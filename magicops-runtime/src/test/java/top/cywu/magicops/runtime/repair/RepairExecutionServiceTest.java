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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据修复闭环测试。覆盖 dry-run、SQL Guard 约束、审批凭据校验、
 * 内容绑定、表级白名单、行数上限回滚、修复执行和审计（切片 30）。
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
        signingService = new SigningService();
        repairService = new RepairExecutionService(sqlGuardService, dryRunService,
                auditService, dataSourceManager, signingService);

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
    void dryRun_multiStatement_rejected() {
        DryRunReport report = dryRunService.analyze("script-001",
                "UPDATE orders SET status = 'A' WHERE id = 1; "
                        + "UPDATE orders SET status = 'B' WHERE id = 2");

        assertFalse(report.safe());
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

    // ---- 切片 30：审批凭据强制 ----

    @Test
    void repair_withoutApprovalProof_rejected() {
        String sql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        // 覆盖默认凭据为空列表，模拟无 approvals 的发布包
        PublishPackage pkg = buildPackage(sql, Map.of("approvals", List.of()));
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "orders", "UPDATE", true, "manual");

        RepairResult result = repairService.execute(pkg, sql, report, declaration);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("审批"),
                "缺少审批凭据应被拒绝: " + result.errorMessage());
    }

    @Test
    void repair_rejectedApprovalDecision_rejected() {
        String sql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        PublishPackage pkg = buildPackage(sql, Map.of("approvals", List.of(
                approvalProof("script-001", "1.0.0", "REJECTED"))));
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "orders", "UPDATE", true, "manual");

        RepairResult result = repairService.execute(pkg, sql, report, declaration);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("审批"));
    }

    @Test
    void repair_contentNotMatchingPackage_rejected() {
        // 包内审批的 SQL 与实际执行的 SQL 不一致 → 拒绝（内容绑定）
        String approvedSql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", approvedSql);
        PublishPackage pkg = buildPackage(approvedSql);
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "orders", "UPDATE", true, "manual");

        String differentSql = "UPDATE orders SET status = 'HACKED' WHERE id = 2";
        RepairResult result = repairService.execute(pkg, differentSql, report, declaration);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("不一致"),
                "内容绑定应拒绝与审批内容不同的 SQL: " + result.errorMessage());
    }

    // ---- 切片 30：表级白名单 ----

    @Test
    void repair_tableNotInAllowlist_rejected() {
        String sql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        // 授权表不含 orders
        PublishPackage pkg = buildPackage(sql, Map.of(
                "datasourcePermissions", List.of(
                        Map.of("datasource", "default", "tables", List.of("patients")))));
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "orders", "UPDATE", true, "manual");

        RepairResult result = repairService.execute(pkg, sql, report, declaration);

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("授权范围"),
                "表白名单应拒绝未授权表: " + result.errorMessage());
    }

    @Test
    void repair_tableInAllowlist_allowed() {
        String sql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        PublishPackage pkg = buildPackage(sql, Map.of(
                "datasourcePermissions", List.of(
                        Map.of("datasource", "default", "tables", List.of("ORDERS")))));
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "orders", "UPDATE", true, "manual");

        RepairResult result = repairService.execute(pkg, sql, report, declaration);

        assertTrue(result.success(), "大小写不同的授权表名应匹配: " + result.errorMessage());
        assertEquals(1, result.affectedRows());
    }

    // ---- 切片 30：影响行数上限与回滚 ----

    @Test
    void repair_affectedRowsExceeded_rolledBack() throws Exception {
        // 准备 150 行数据
        try (Connection conn = dataSourceManager.getDefaultDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS bulk_orders (id INT PRIMARY KEY, status VARCHAR(20))");
            for (int i = 1; i <= 150; i++) {
                stmt.execute("MERGE INTO bulk_orders VALUES (" + i + ", 'OLD')");
            }
        }

        String sql = "UPDATE bulk_orders SET status = 'NEW' WHERE id > 0";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        PublishPackage pkg = buildPackage(sql);
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.HIGH, "bulk_orders", "UPDATE", true, "manual");

        RepairResult result = repairService.execute(pkg, sql, report, declaration);

        assertFalse(result.success(), "影响 150 行超过上限 100，必须拒绝");
        assertTrue(result.errorMessage().contains("回滚"),
                "应报告事务已回滚: " + result.errorMessage());

        // 验证数据未被修改（回滚生效）
        try (Connection conn = dataSourceManager.getDefaultDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM bulk_orders WHERE status = 'NEW'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "超限修复必须完全回滚，不允许部分生效");
        }

        // 阻断审计已记录
        var records = auditService.findAll();
        assertFalse(records.isEmpty());
        var last = records.get(records.size() - 1);
        assertEquals(false, last.details().get("success"));
    }

    @Test
    void repair_withinRowLimit_committed() throws Exception {
        // 99 行在上限内，应成功提交
        try (Connection conn = dataSourceManager.getDefaultDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS limit_orders (id INT PRIMARY KEY, status VARCHAR(20))");
            for (int i = 1; i <= 99; i++) {
                stmt.execute("MERGE INTO limit_orders VALUES (" + i + ", 'OLD')");
            }
        }

        String sql = "UPDATE limit_orders SET status = 'NEW' WHERE id > 0";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        PublishPackage pkg = buildPackage(sql);
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.MEDIUM, "limit_orders", "UPDATE", true, "manual");

        RepairResult result = repairService.execute(pkg, sql, report, declaration);

        assertTrue(result.success(), "99 行未超限应成功: " + result.errorMessage());
        assertEquals(99, result.affectedRows());
    }

    // ---- 既有用例 ----

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
                new SqlGuardService(), dryRunService, failingAuditService,
                dataSourceManager, signingService);

        String sql = "UPDATE orders SET status = 'FIXED' WHERE id = 1";
        DryRunReport report = dryRunService.analyze("script-001", sql);
        PublishPackage pkg = buildPackage(sql);
        RepairDeclaration declaration = new RepairDeclaration(
                "script-001", RiskLevel.CRITICAL, "orders", "UPDATE", true, "backup");

        assertThrows(top.cywu.magicops.audit.service.AuditWriteException.class,
                () -> failRepairService.execute(pkg, sql, report, declaration));
    }

    // ---- 测试辅助 ----

    private Map<String, Object> approvalProof(String scriptId, String version, String decision) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("scriptId", scriptId);
        proof.put("version", version);
        proof.put("approvalId", "9001");
        proof.put("decision", decision);
        proof.put("submittedBy", "developer");
        proof.put("decidedBy", "approver");
        proof.put("decidedAt", Instant.now().toString());
        return proof;
    }

    /**
     * 构建带默认审批凭据的发布包（无额外 metadata）。
     */
    private PublishPackage buildPackage(String sql) {
        return buildPackage(sql, Map.of());
    }

    /**
     * 构建发布包：自动附加 script-001 的 APPROVED 审批凭据，并合并额外 metadata。
     */
    private PublishPackage buildPackage(String sql, Map<String, Object> extraMetadata) {
        byte[] normalizedScript = CanonicalJson.normalizeScript(sql);
        String contentHash = signingService.sha256Hex(normalizedScript);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("routeMapping", List.of());
        metadata.put("approvals", List.of(approvalProof("script-001", "1.0.0", "APPROVED")));
        metadata.putAll(extraMetadata);

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
