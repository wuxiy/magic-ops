package top.cywu.magicops.runtime.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.runtime.datasource.DynamicDataSourceManager;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.sqlguard.SqlGuardService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源声明路由测试（切片 37）。
 *
 * <p>覆盖关闭标准：Query 按脚本声明的数据源路由；声明数据源未在发布包授权范围内时拒绝。
 */
class QueryDatasourceRoutingTest {

    private QueryExecutionService queryService;
    private DynamicDataSourceManager dataSourceManager;

    @BeforeEach
    void setUp() throws Exception {
        SqlGuardService sqlGuardService = new SqlGuardService();
        AuditService auditService = new AuditService();
        SigningService signingService = new SigningService();
        dataSourceManager = new DynamicDataSourceManager();
        queryService = new QueryExecutionService(sqlGuardService, auditService, dataSourceManager);

        // 注册一个业务数据源（模拟 DataSourceSyncService 同步）
        dataSourceManager.registerDataSource("business-his",
                "jdbc:h2:mem:his_routing_test;DB_CLOSE_DELAY=-1", "sa", "");
        try (var conn = dataSourceManager.getDataSource("business-his").getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS his_orders (id INT PRIMARY KEY, amount INT)");
            stmt.execute("MERGE INTO his_orders VALUES (1, 100)");
        }
    }

    @Test
    void queryRoutesToDeclaredDatasource() {
        // 脚本声明 datasource=business-his，datasourcePermissions 含该数据源
        PublishPackage pkg = buildPackage("1", "SELECT * FROM his_orders",
                "business-his", Map.of(
                        "datasourcePermissions", List.of(
                                Map.of("datasource", "business-his", "tables", List.of("HIS_ORDERS"))),
                        "scriptDatasource", Map.of("1", "business-his")));

        var result = queryService.executeQuery(pkg, "SELECT * FROM his_orders", "trace-1", "1", true);

        assertTrue(result.success(), "应按声明数据源路由执行成功: " + result.errorMessage());
        assertEquals(1, result.resultSize());
    }

    @Test
    void undeclaredDatasource_rejected() {
        // 脚本声明 datasource=business-his，但发布包未授权该数据源
        PublishPackage pkg = buildPackage("1", "SELECT 1",
                "business-his", Map.of(
                        "datasourcePermissions", List.of(
                                Map.of("datasource", "default", "tables", List.of())),
                        "scriptDatasource", Map.of("1", "business-his")));

        var result = queryService.executeQuery(pkg, "SELECT 1", "trace-2", "1", true);

        assertFalse(result.success(), "声明数据源未授权应被拒绝");
        assertTrue(result.errorMessage().contains("数据源") || result.errorMessage().contains("授权"));
    }

    @Test
    void tableNotInDeclaredDatasource_rejected() {
        // 声明 business-his 授权，但查询表不在其授权表清单内
        PublishPackage pkg = buildPackage("1", "SELECT * FROM secret_table",
                "business-his", Map.of(
                        "datasourcePermissions", List.of(
                                Map.of("datasource", "business-his", "tables", List.of("HIS_ORDERS"))),
                        "scriptDatasource", Map.of("1", "business-his")));

        var result = queryService.executeQuery(pkg, "SELECT * FROM secret_table", "trace-3", "1", true);

        assertFalse(result.success(), "表不在声明数据源授权范围应被拒绝");
    }

    private PublishPackage buildPackage(String scriptId, String sql, String datasource,
                                        Map<String, Object> metadata) {
        byte[] normalized = CanonicalJson.normalizeScript(sql);
        SigningService signingService = new SigningService();
        String contentHash = signingService.sha256Hex(normalized);
        PackageManifest.ScriptEntry entry = new PackageManifest.ScriptEntry(
                scriptId, "/api/test", "GET", "1.0.0", "DYNAMIC_QUERY", "LOW", contentHash, "");
        PackageManifest manifest = new PackageManifest(
                "test", "development", "1.0.0", "0.1.0", "tester", Instant.now(), "k1",
                List.of(entry), "mh", "ph", "SHA256withRSA", null);
        Map<String, byte[]> scripts = Map.of("/api/test", normalized);
        return new PublishPackage(manifest, scripts, metadata, Map.of(), new byte[0]);
    }
}
