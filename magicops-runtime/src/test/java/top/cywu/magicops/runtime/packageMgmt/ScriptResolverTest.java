package top.cywu.magicops.runtime.packageMgmt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本引用解析器测试（切片 34）。
 *
 * <p>覆盖关闭标准：Runtime 按 scriptId 从激活包解析脚本内容并校验完整性；
 * 未引用脚本或内容 hash 不匹配时拒绝。
 */
class ScriptResolverTest {

    private SigningService signingService;
    private ScriptResolver resolver;

    @BeforeEach
    void setUp() {
        signingService = new SigningService();
        resolver = new ScriptResolver(signingService);
    }

    @Test
    void resolveByScriptId_returnsContent() {
        String sql = "SELECT 1 AS test_value";
        PublishPackage pkg = buildPackage("1", "/api/q1", sql);

        ResolvedScript resolved = resolver.resolve(pkg, "1");

        assertEquals("1", resolved.entry().scriptId());
        assertEquals(sql, resolved.contentAsString());
    }

    @Test
    void unknownScriptId_rejected() {
        PublishPackage pkg = buildPackage("1", "/api/q1", "SELECT 1");
        PackageRejectedException ex = assertThrows(PackageRejectedException.class,
                () -> resolver.resolve(pkg, "no-such-script"));
        assertTrue(ex.getMessage().contains("no-such-script"));
    }

    @Test
    void nullScriptId_rejected() {
        PublishPackage pkg = buildPackage("1", "/api/q1", "SELECT 1");
        assertThrows(PackageRejectedException.class, () -> resolver.resolve(pkg, null));
    }

    @Test
    void contentHashMismatch_rejected() {
        // 构建一个 manifest 声明的 contentHash 与 scripts 实际内容不一致的包
        String realSql = "SELECT 1";
        byte[] normalized = CanonicalJson.normalizeScript(realSql);
        String wrongHash = signingService.sha256Hex(CanonicalJson.normalizeScript("SELECT 2"));

        PackageManifest.ScriptEntry entry = new PackageManifest.ScriptEntry(
                "1", "/api/q1", "GET", "1.0.0", "DYNAMIC_QUERY", "LOW", wrongHash, "");
        PackageManifest manifest = new PackageManifest(
                "test", "development", "1.0.0", "0.1.0", "tester", Instant.now(), "k1",
                List.of(entry), "mh", "ph", "SHA256withRSA", null);
        PublishPackage pkg = new PublishPackage(manifest, Map.of("/api/q1", normalized),
                Map.of(), Map.of(), new byte[0]);

        assertThrows(PackageRejectedException.class, () -> resolver.resolve(pkg, "1"));
    }

    private PublishPackage buildPackage(String scriptId, String path, String sql) {
        byte[] normalized = CanonicalJson.normalizeScript(sql);
        String contentHash = signingService.sha256Hex(normalized);
        PackageManifest.ScriptEntry entry = new PackageManifest.ScriptEntry(
                scriptId, path, "GET", "1.0.0", "DYNAMIC_QUERY", "LOW", contentHash, "");
        PackageManifest manifest = new PackageManifest(
                "test", "development", "1.0.0", "0.1.0", "tester", Instant.now(), "k1",
                List.of(entry), "mh", "ph", "SHA256withRSA", null);
        Map<String, byte[]> scripts = new LinkedHashMap<>();
        scripts.put(path, normalized);
        return new PublishPackage(manifest, scripts, Map.of(), Map.of(), new byte[0]);
    }
}
