package top.cywu.magicops.runtime.packageMgmt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime 验签测试。覆盖所有关闭标准：
 * <ul>
 *   <li>Runtime 接受合法签名包</li>
 *   <li>Runtime 拒绝 hash 不匹配包</li>
 *   <li>Runtime 拒绝环境不匹配包</li>
 *   <li>Runtime 拒绝未知 key ID 包</li>
 *   <li>Runtime 加载审计成功写入</li>
 * </ul>
 */
class PackageVerificationServiceTest {

    private PackageVerificationService verificationService;
    private SigningService signingService;
    private AuditService auditService;
    private KeyPair keyPair;
    private static final String KEY_ID = "test-key-001";
    private static final String ENVIRONMENT = "development";

    @BeforeEach
    void setUp() throws Exception {
        signingService = new SigningService();
        auditService = new AuditService();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        // 使用反射或直接构造一个自定义 KeyProvider
        KeyProvider keyProvider = new TestKeyProvider(keyPair, KEY_ID);
        verificationService = new PackageVerificationService(signingService, keyProvider, auditService);
    }

    @Test
    void acceptsValidSignedPackage() {
        PublishPackage pkg = buildValidPackage();
        assertDoesNotThrow(() -> verificationService.verifyAndActivate(pkg));
        assertNotNull(verificationService.getActivePackage());
    }

    @Test
    void rejectsHashMismatch() {
        // 构建一个脚本内容被篡改的包
        String originalContent = "SELECT 1";
        byte[] normalized = CanonicalJson.normalizeScript(originalContent);
        String wrongHash = signingService.sha256Hex("wrong content".getBytes());

        Map<String, Object> metadata = Map.of("routeMapping", List.of());
        String metadataHash = signingService.hashMetadata(metadata);

        PackageManifest manifest = new PackageManifest(
                "test", ENVIRONMENT, "1.0.0", "0.1.0",
                "tester", Instant.now(), KEY_ID,
                List.of(new PackageManifest.ScriptEntry(
                        "1", "/api/test", "GET", "1.0.0",
                        "DYNAMIC_QUERY", "LOW", wrongHash, ""
                )),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(Map.of())),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/test", normalized);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());

        PublishPackage pkg = new PublishPackage(manifest, scripts, metadata, Map.of(), signature);

        assertThrows(PackageRejectedException.class,
                () -> verificationService.verifyAndActivate(pkg));
    }

    @Test
    void rejectsEnvironmentMismatch() {
        // 使用不匹配的环境
        PublishPackage pkg = buildPackageWithEnvironment("production");
        assertThrows(PackageRejectedException.class,
                () -> verificationService.verifyAndActivate(pkg));
    }

    @Test
    void rejectsUnknownKeyId() {
        // 使用未知的 key ID
        PublishPackage pkg = buildPackageWithKeyId("unknown-key-999");
        assertThrows(PackageRejectedException.class,
                () -> verificationService.verifyAndActivate(pkg));
    }

    @Test
    void loadAuditWritten() {
        PublishPackage pkg = buildValidPackage();
        verificationService.verifyAndActivate(pkg);

        List<AuditRecord> records = auditService.findAll();
        assertFalse(records.isEmpty(), "加载后应有审计记录");

        boolean hasLoadAudit = records.stream()
                .anyMatch(r -> r.eventType().name().equals("PACKAGE_LOADED"));
        assertTrue(hasLoadAudit, "应有 PACKAGE_LOADED 审计事件");

        boolean hasCriticalAudit = records.stream()
                .anyMatch(r -> r.eventType().name().equals("PACKAGE_LOADED") && r.critical());
        assertTrue(hasCriticalAudit, "加载审计应为关键审计");
    }

    private PublishPackage buildValidPackage() {
        return buildPackageWithEnvironment(ENVIRONMENT);
    }

    private PublishPackage buildPackageWithEnvironment(String environment) {
        return buildPackageWith(environment, KEY_ID);
    }

    private PublishPackage buildPackageWithKeyId(String keyId) {
        return buildPackageWith(ENVIRONMENT, keyId);
    }

    private PublishPackage buildPackageWith(String environment, String keyId) {
        String content = "return db.select('SELECT 1')";
        byte[] normalized = CanonicalJson.normalizeScript(content);
        String contentHash = signingService.sha256Hex(normalized);

        Map<String, Object> metadata = Map.of("routeMapping", List.of());
        String metadataHash = signingService.hashMetadata(metadata);
        Map<String, Object> policy = Map.of();

        PackageManifest manifest = new PackageManifest(
                "test", environment, "1.0.0", "0.1.0",
                "tester", Instant.now(), keyId,
                List.of(new PackageManifest.ScriptEntry(
                        "1", "/api/test", "GET", "1.0.0",
                        "DYNAMIC_QUERY", "LOW", contentHash, ""
                )),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/test", normalized);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());

        return new PublishPackage(manifest, scripts, metadata, policy, signature);
    }

    /**
     * 测试用 KeyProvider，使用指定的密钥对和 key ID。
     */
    static class TestKeyProvider extends KeyProvider {
        private final KeyPair testKeyPair;
        private final String testKeyId;

        TestKeyProvider(KeyPair keyPair, String keyId) {
            // 调用父类构造函数（会生成临时密钥对，但我们不用它）
            super();
            this.testKeyPair = keyPair;
            this.testKeyId = keyId;
        }

        @Override
        public KeyPair getKeyPair() { return testKeyPair; }

        @Override
        public java.security.PrivateKey getPrivateKey() { return testKeyPair.getPrivate(); }

        @Override
        public java.security.PublicKey getPublicKey() { return testKeyPair.getPublic(); }

        @Override
        public String getKeyId() { return testKeyId; }

        @Override
        public boolean isTrustedKeyId(String keyId) { return testKeyId.equals(keyId); }
    }
}
