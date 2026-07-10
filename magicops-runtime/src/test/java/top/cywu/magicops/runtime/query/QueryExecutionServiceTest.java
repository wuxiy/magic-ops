package top.cywu.magicops.runtime.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.audit.service.AuditService;
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
 * 动态查询闭环测试。覆盖完整查询执行流程、SQL Guard 拒绝和审计记录验证。
 * 使用 H2 内存数据库执行真实 JDBC 查询。
 */
class QueryExecutionServiceTest {

    private QueryExecutionService queryService;
    private SigningService signingService;
    private AuditService auditService;
    private DynamicDataSourceManager dataSourceManager;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        SqlGuardService sqlGuardService = new SqlGuardService();
        auditService = new AuditService();
        signingService = new SigningService();
        dataSourceManager = new DynamicDataSourceManager();
        queryService = new QueryExecutionService(sqlGuardService, auditService, dataSourceManager);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        // Setup test data in H2
        try (Connection conn = dataSourceManager.getDefaultDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS patients (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
            stmt.execute("MERGE INTO patients VALUES (1, 'Alice', 30)");
            stmt.execute("MERGE INTO patients VALUES (2, 'Bob', 25)");
            stmt.execute("MERGE INTO patients VALUES (3, 'Charlie', 35)");
        }
    }

    @Test
    void executeQuery_selectSucceeds() {
        PublishPackage pkg = buildPackage("SELECT * FROM patients WHERE id = 1");
        String traceId = UUID.randomUUID().toString();

        QueryExecutionResult result = queryService.executeQuery(pkg,
                "SELECT * FROM patients WHERE id = 1", traceId);

        assertTrue(result.success());
        assertEquals(traceId, result.traceId());
        assertEquals(1, result.resultSize());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void executeQuery_selectAllReturnsMultipleRows() {
        PublishPackage pkg = buildPackage("SELECT * FROM patients");
        String traceId = UUID.randomUUID().toString();

        QueryExecutionResult result = queryService.executeQuery(pkg,
                "SELECT * FROM patients", traceId);

        assertTrue(result.success());
        assertEquals(3, result.resultSize());
    }

    @Test
    void executeQuery_nonSelectRejected() {
        PublishPackage pkg = buildPackage("SELECT 1");
        String traceId = UUID.randomUUID().toString();

        // 尝试执行 UPDATE —— 应被 SQL Guard 拒绝
        QueryExecutionResult result = queryService.executeQuery(pkg,
                "UPDATE patients SET name = 'hacked' WHERE id = 1", traceId);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("SQL Guard"));
    }

    @Test
    void executeQuery_auditRecordContainsRequiredFields() {
        PublishPackage pkg = buildPackage("SELECT count(*) FROM patients");
        String traceId = "trace-audit-test";

        queryService.executeQuery(pkg, "SELECT count(*) FROM patients", traceId);

        var records = auditService.findAll();
        assertFalse(records.isEmpty());

        var auditRecord = records.get(records.size() - 1);
        assertEquals("SCRIPT_EXECUTED", auditRecord.eventType().name());
        assertTrue(auditRecord.critical(), "执行审计应为关键审计");

        Map<String, Object> details = auditRecord.details();
        assertEquals(traceId, details.get("traceId"));
        assertNotNull(details.get("scriptId"));
        assertNotNull(details.get("scriptVersion"));
        assertNotNull(details.get("sqlSummary"));
        assertNotNull(details.get("resultSize"));
        assertNotNull(details.get("durationMs"));
        assertEquals(true, details.get("success"));
    }

    @Test
    void executeQuery_failedQueryAuditRecorded() {
        PublishPackage pkg = buildPackage("SELECT 1");
        String traceId = "trace-fail-test";

        // DROP TABLE 应被拒绝
        QueryExecutionResult result = queryService.executeQuery(pkg,
                "DROP TABLE patients", traceId);

        assertFalse(result.success());

        var records = auditService.findByEntity("ScriptExecution",
                result.scriptId());
        assertFalse(records.isEmpty());
        var failAudit = records.get(records.size() - 1);
        assertEquals(false, failAudit.details().get("success"));
        assertNotNull(failAudit.details().get("errorMessage"));
    }

    @Test
    void executeQuery_resultSizeLimited() {
        PublishPackage pkg = buildPackage("SELECT * FROM patients");
        String traceId = UUID.randomUUID().toString();

        QueryExecutionResult result = queryService.executeQuery(pkg,
                "SELECT * FROM patients", traceId);

        assertTrue(result.success());
        assertTrue(result.resultSize() <= 1000);
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
                        "script-001", "/api/test", "GET", "1.0.0",
                        "DYNAMIC_QUERY", "LOW", contentHash, ""
                )),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/test", normalizedScript);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());

        return new PublishPackage(manifest, scripts, metadata, policy, signature);
    }
}
