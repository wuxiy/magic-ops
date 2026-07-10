package top.cywu.magicops.runtime.repair;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.core.model.RiskLevel;
import top.cywu.magicops.runtime.datasource.DynamicDataSourceManager;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.sqlguard.SqlGuardService;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据修复闭环测试。覆盖 dry-run、SQL Guard 约束、修复执行和审计。
 */
class RepairExecutionServiceTest {

    private RepairExecutionService repairService;
    private DryRunService dryRunService;
    private AuditService auditService;
    private DynamicDataSourceManager dataSourceManager;
    private SigningService signingService;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        SqlGuardService sqlGuardService = new SqlGuardService();
        auditService = new AuditService();
        dataSourceManager = new DynamicDataSourceManager();
        dryRunService = new DryRunService(sqlGuardService);
        repairService = new RepairExecutionService(sqlGuardService, dryRunService,
                auditService, dataSourceManager);
        signingService = new SigningService();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        // Setup test data
        try (Connection conn = dataSourceManager.getDefaultDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, status VARCHAR(20), amount DECIMAL(10,2))");
            stmt.execute("MERGE INTO orders VALUES (1, 'PENDING', 100.00)");
            stmt.execute("MERGE INTO orders VALUES (2, 'PENDING', 200.00)");
            stmt.execute("MERGE INTO orders VALUES (3, 'DONE', 300.00)");
        }
    }

    @Test
    void dryRun_updateWithWhere_succeeds() {
        DryRunReport report = dryRunService.analyze("script-001",
                "UPDATE orders SET status = 'FIXED' WHERE id = 1");

        assertTrue(report.safe());
        assertEquals("UPDATE", report.sqlType());
        assertEquals(List.of("orders"), report.affectedTables());
        assertTrue(report.estimatedAffectedRows() > 0);
    }

    @Test
    void dryRun_updateWithoutWhere_rejected() {
        DryRunReport report = dryRunService.analyze("script-001",
                "UPDATE orders SET status = 'FIXED'");

        assertFalse(report.safe());
        assertTrue(report.reason().contains("WHERE"));
    }

    @Test
    void dryRun_dropTable_rejected() {
        DryRunReport report = dryRunService.analyze("script-001", "DROP TABLE orders");

        assertFalse(report.safe());
        assertTrue(report.reason().contains("DROP"));
    }

    @Test
    void dryRun_selectRejected_forRepair() {
        DryRunReport report = dryRunService.analyze("script-001", "SELECT * FROM orders");

        assertFalse(report.safe());
        assertTrue(report.reason().contains("SELECT"));
    }

    @Test
    void repair_noDryRun_rejected() {
        PublishPackage pkg = buildPackage("UPDATE orders SET status = 'FIXED' WHERE id = 1");
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "orders", "UPDATE", true, "manual rollback");

        RepairResult result = repairService.execute(pkg,
                "UPDATE orders SET status = 'FIXED' WHERE id = 1", null, declaration);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("dry-run"));
    }

    @Test
    void repair_freeUpdateWithoutWhere_rejected() {
        DryRunReport report = dryRunService.analyze("script-001",
                "UPDATE orders SET status = 'FIXED' WHERE id = 1");

        PublishPackage pkg = buildPackage("UPDATE orders SET status = 'FIXED' WHERE id = 1");
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "orders", "UPDATE", true, "manual rollback");

        // 尝试执行不带 WHERE 的自由 UPDATE
        RepairResult result = repairService.execute(pkg,
                "UPDATE orders SET status = 'FIXED'", report, declaration);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("WHERE"));
    }

    @Test
    void repair_successfulExecution_auditWritten() {
        String sql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        assertTrue(report.safe());

        PublishPackage pkg = buildPackage(sql);
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.HIGH, "orders", "UPDATE", true, "backup table");

        RepairResult result = repairService.execute(pkg, sql, report, declaration);

        assertTrue(result.success());
        assertNotNull(result.rollbackIntent());
        assertEquals("backup table", result.rollbackIntent());

        // 验证审计记录
        var records = auditService.findAll();
        assertFalse(records.isEmpty());
        var lastAudit = records.get(records.size() - 1);
        assertEquals("SCRIPT_EXECUTED", lastAudit.eventType().name());
        assertTrue(lastAudit.critical());
        assertEquals("HIGH", lastAudit.details().get("riskLevel"));
    }

    @Test
    void repair_auditFailure_blocksExecution() {
        // 使用一个会抛出异常的 AuditService 来模拟审计写入失败
        AuditService failingAuditService = new AuditService() {
            @Override
            public void write(top.cywu.magicops.audit.model.AuditRecord record) {
                if (record.critical()) {
                    throw new top.cywu.magicops.audit.service.AuditWriteException(
                            "模拟审计写入失败", new RuntimeException("disk full"));
                }
            }
        };

        RepairExecutionService failRepairService = new RepairExecutionService(
                new SqlGuardService(), dryRunService, failingAuditService, dataSourceManager);

        String sql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        PublishPackage pkg = buildPackage(sql);
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.CRITICAL, "orders", "UPDATE", true, "backup");

        assertThrows(top.cywu.magicops.audit.service.AuditWriteException.class,
                () -> failRepairService.execute(pkg, sql, report, declaration));
    }

    private PublishPackage buildPackage(String sql) {
        byte[] normalizedScript = CanonicalJson.normalizeScript(sql);
        String contentHash = signingService.sha256Hex(normalizedScript);

        Map<String, Object> metadata = Map.of("routeMapping", List.of());
        String metadataHash = signingService.hashMetadata(metadata);
        Map<String, Object> policy = Map.of();

        PackageManifest manifest = new PackageManifest(
                "test-project", "development", "1.0.0", "0.1.0",
                "tester", Instant.now(), "test-key",
                List.of(new PackageManifest.ScriptEntry(
                        "script-001", "/api/repair", "POST", "1.0.0",
                        "DATA_REPAIR", "HIGH", contentHash, ""
                )),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/repair", normalizedScript);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());

        return new PublishPackage(manifest, scripts, metadata, policy, signature);
    }
}
