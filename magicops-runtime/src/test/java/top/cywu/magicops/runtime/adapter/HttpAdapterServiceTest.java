package top.cywu.magicops.runtime.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.crypto.service.CryptoService;
import top.cywu.magicops.http.HttpClientService;
import top.cywu.magicops.http.model.HttpTarget;
import top.cywu.magicops.http.registry.HttpTargetRegistry;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP 接口适配闭环测试。覆盖目标注册、allowlist 校验、审计记录。
 */
class HttpAdapterServiceTest {

    private HttpAdapterService adapterService;
    private HttpTargetRegistry targetRegistry;
    private CryptoService cryptoService;
    private AuditService auditService;
    private SigningService signingService;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        targetRegistry = new HttpTargetRegistry();
        HttpClientService httpClientService = new HttpClientService(targetRegistry);
        cryptoService = new CryptoService();
        auditService = new AuditService();
        signingService = new SigningService();
        adapterService = new HttpAdapterService(targetRegistry, httpClientService,
                cryptoService, auditService);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        // Register a test HTTP target
        HttpTarget target = new HttpTarget(
                "his-system", "HIS System",
                "http://localhost:9999",
                List.of("/api/patients", "/api/orders"),
                "basic", Map.of(), false, null
        );
        targetRegistry.register(target);
    }

    @Test
    void unregisteredTarget_rejected() {
        PublishPackage pkg = buildPackage();
        AdapterResult result = adapterService.execute(pkg, "unknown-system",
                "/api/test", "GET", null, "trace-001");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("未注册"));
    }

    @Test
    void nonAllowlistPath_rejected() {
        PublishPackage pkg = buildPackage();
        AdapterResult result = adapterService.execute(pkg, "his-system",
                "/api/admin/delete", "POST", "{}", "trace-002");

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("allowlist"));
    }

    @Test
    void allowlistPath_accepted() {
        PublishPackage pkg = buildPackage();
        // This will fail at the HTTP level (no server running) but should pass validation
        AdapterResult result = adapterService.execute(pkg, "his-system",
                "/api/patients/123", "GET", null, "trace-003");

        // HTTP request fails but audit should still be written
        var records = auditService.findByEntity("HttpAdapter", "his-system");
        assertFalse(records.isEmpty());
    }

    @Test
    void traceId_propagated() {
        PublishPackage pkg = buildPackage();
        String traceId = "custom-trace-id-123";

        AdapterResult result = adapterService.execute(pkg, "his-system",
                "/api/patients", "GET", null, traceId);

        assertEquals(traceId, result.traceId());
    }

    @Test
    void writeOperationAudit_isCritical() {
        PublishPackage pkg = buildPackage();
        adapterService.execute(pkg, "his-system",
                "/api/orders", "POST", "{\"id\": 1}", "trace-write");

        var records = auditService.findByEntity("HttpAdapter", "his-system");
        assertFalse(records.isEmpty());
        // POST is a write operation, audit should be critical
        var lastRecord = records.get(records.size() - 1);
        assertTrue(lastRecord.critical());
    }

    @Test
    void cryptoService_encryptDecrypt_roundTrip() {
        byte[] key = new byte[32]; // AES-256
        new java.security.SecureRandom().nextBytes(key);
        cryptoService.registerKey("test-key", key);

        String plaintext = "sensitive patient data";
        String encrypted = cryptoService.encrypt("test-key", plaintext);
        String decrypted = cryptoService.decrypt("test-key", encrypted);

        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted);
    }

    @Test
    void cryptoService_hmacSign_verify() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        cryptoService.registerKey("hmac-key", key);

        String data = "request payload";
        String hmac = cryptoService.hmacSha256("hmac-key", data);

        assertTrue(cryptoService.verifyHmac("hmac-key", data, hmac));
        assertFalse(cryptoService.verifyHmac("hmac-key", "tampered", hmac));
    }

    @Test
    void cryptoService_maskSensitiveData() {
        assertEquals("138****1234", cryptoService.mask("13812341234"));
        assertEquals("110101********1234", cryptoService.mask("110101199001011234"));
    }

    @Test
    void targetRegistry_registerAndQuery() {
        assertTrue(targetRegistry.isRegistered("his-system"));
        assertFalse(targetRegistry.isRegistered("unknown"));

        assertTrue(targetRegistry.isUrlAllowed("his-system", "/api/patients"));
        assertTrue(targetRegistry.isUrlAllowed("his-system", "/api/orders/123"));
        assertFalse(targetRegistry.isUrlAllowed("his-system", "/api/admin"));
    }

    private PublishPackage buildPackage() {
        byte[] normalizedScript = CanonicalJson.normalizeScript("http.call('his-system', '/api/patients')");
        String contentHash = signingService.sha256Hex(normalizedScript);

        Map<String, Object> metadata = Map.of("routeMapping", List.of());
        String metadataHash = signingService.hashMetadata(metadata);
        Map<String, Object> policy = Map.of();

        PackageManifest manifest = new PackageManifest(
                "test-project", "development", "1.0.0", "0.1.0",
                "tester", Instant.now(), "test-key",
                List.of(new PackageManifest.ScriptEntry(
                        "script-001", "/api/adapter", "POST", "1.0.0",
                        "HTTP_ADAPTER", "LOW", contentHash, ""
                )),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/adapter", normalizedScript);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());

        return new PublishPackage(manifest, scripts, metadata, policy, signature);
    }
}
