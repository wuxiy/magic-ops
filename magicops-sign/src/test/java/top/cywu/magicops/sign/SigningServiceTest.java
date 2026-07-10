package top.cywu.magicops.sign;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 签名与验签测试。覆盖签名生成、验签、canonical JSON 稳定性和 hash 一致性。
 */
class SigningServiceTest {

    private SigningService signingService;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        signingService = new SigningService();
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
    }

    @Test
    void signAndVerify_roundTrip() {
        byte[] data = "hello magicops".getBytes();
        byte[] signature = signingService.sign(data, keyPair.getPrivate());
        assertTrue(signingService.verify(data, signature, keyPair.getPublic()));
    }

    @Test
    void verify_rejectsWrongKey() throws Exception {
        byte[] data = "test data".getBytes();
        byte[] signature = signingService.sign(data, keyPair.getPrivate());

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair otherKey = gen.generateKeyPair();

        assertFalse(signingService.verify(data, signature, otherKey.getPublic()));
    }

    @Test
    void verify_rejectsTamperedData() {
        byte[] data = "original".getBytes();
        byte[] signature = signingService.sign(data, keyPair.getPrivate());

        byte[] tampered = "tampered".getBytes();
        assertFalse(signingService.verify(tampered, signature, keyPair.getPublic()));
    }

    @Test
    void sha256Hex_stableOutput() {
        String hash1 = signingService.sha256Hex("test".getBytes());
        String hash2 = signingService.sha256Hex("test".getBytes());
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 = 32 bytes = 64 hex chars
    }

    @Test
    void canonicalJson_fieldOrderStable() {
        Map<String, Object> json1 = Map.of("z", 1, "a", 2, "m", 3);
        Map<String, Object> json2 = Map.of("a", 2, "m", 3, "z", 1);

        byte[] bytes1 = CanonicalJson.toCanonicalBytes(json1);
        byte[] bytes2 = CanonicalJson.toCanonicalBytes(json2);

        assertArrayEquals(bytes1, bytes2, "Canonical JSON 应当与字段输入顺序无关");
    }

    @Test
    void canonicalJson_scriptNormalization() {
        String crlf = "line1\r\nline2\r\n";
        String lf = "line1\nline2\n";
        assertArrayEquals(
                CanonicalJson.normalizeScript(lf),
                CanonicalJson.normalizeScript(crlf),
                "CRLF 应当被规范化为 LF"
        );
    }

    @Test
    void verifyPackage_fullRoundTrip() {
        // 构建一个完整的发布包
        String scriptContent = "return db.select('SELECT 1')";
        byte[] normalizedScript = CanonicalJson.normalizeScript(scriptContent);
        String contentHash = signingService.sha256Hex(normalizedScript);

        Map<String, Object> metadata = Map.of(
                "datasourcePermissions", List.of(),
                "routeMapping", List.of()
        );
        String metadataHash = signingService.hashMetadata(metadata);
        Map<String, Object> policy = Map.of("readOnly", true);

        PackageManifest manifest = new PackageManifest(
                "test-project", "development", "1.0.0", "0.1.0",
                "tester", Instant.now(), "test-key",
                List.of(new PackageManifest.ScriptEntry(
                        "1", "/api/test", "GET", "1.0.0",
                        "DYNAMIC_QUERY", "LOW", contentHash, ""
                )),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/test", normalizedScript);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());

        PublishPackage pkg = new PublishPackage(manifest, scripts, metadata, policy, signature);

        // 验签应通过
        assertTrue(signingService.verifyPackage(pkg, keyPair.getPublic()));
    }

    @Test
    void verifyPackage_rejectsHashMismatch() {
        String content = "original content";
        byte[] normalized = CanonicalJson.normalizeScript(content);
        String correctHash = signingService.sha256Hex(normalized);

        Map<String, Object> metadata = Map.of("routeMapping", List.of());
        String metadataHash = signingService.hashMetadata(metadata);

        // 故意使用错误的 content hash
        PackageManifest manifest = new PackageManifest(
                "test", "development", "1.0.0", "0.1.0",
                "tester", Instant.now(), "test-key",
                List.of(new PackageManifest.ScriptEntry(
                        "1", "/api/test", "GET", "1.0.0",
                        "DYNAMIC_QUERY", "LOW", "wrong-hash", ""
                )),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(Map.of())),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/test", normalized);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());

        PublishPackage pkg = new PublishPackage(manifest, scripts, metadata, Map.of(), signature);

        // hash 不匹配应拒绝
        assertFalse(signingService.verifyPackage(pkg, keyPair.getPublic()));
    }
}
