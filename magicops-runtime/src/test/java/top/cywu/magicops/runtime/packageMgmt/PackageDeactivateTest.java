package top.cywu.magicops.runtime.packageMgmt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.core.constants.PushProtocol;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 发布包下线端点测试（切片 33-d）。
 *
 * <p>覆盖关闭标准：
 * <ul>
 *   <li>下线后 Runtime 拒绝对应脚本执行（getActivePackage 返回 null）；</li>
 *   <li>下线端点受共享密钥认证保护，未携带密钥返回 401；</li>
 *   <li>下线写入关键审计（PACKAGE_DEACTIVATED）。</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "magicops.runtime.push-secret=deact-secret",
        "MAGICOPS_ENVIRONMENT=development"
})
@AutoConfigureMockMvc
class PackageDeactivateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PackageVerificationService verificationService;

    @Autowired
    private SigningService signingService;

    @Autowired
    private KeyProvider keyProvider;

    @Autowired
    private AuditService auditService;

    @Test
    void deactivate_clearsActivePackage_thenExecutionRejected() throws Exception {
        verificationService.verifyAndActivate(buildSignedPackage());

        mockMvc.perform(post("/api/packages/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PushProtocol.PUSH_SECRET_HEADER, "deact-secret")
                        .content("{\"operator\":\"admin\",\"reason\":\"运维下线\"}"))
                .andExpect(status().isOk());

        assertNull(verificationService.getActivePackage(), "下线后不应有激活包");
        boolean hasDeactivateAudit = auditService.findAll().stream()
                .anyMatch(r -> r.eventType() == AuditEventType.PACKAGE_DEACTIVATED && r.critical());
        assertTrue(hasDeactivateAudit, "应写入 PACKAGE_DEACTIVATED 关键审计");
    }

    @Test
    void deactivateWithoutSecret_rejectedWith401() throws Exception {
        mockMvc.perform(post("/api/packages/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"admin\"}"))
                .andExpect(status().isUnauthorized());
    }

    private PublishPackage buildSignedPackage() throws Exception {
        KeyPair kp = keyProvider.getKeyPair();
        String content = "return db.select('SELECT 1')";
        byte[] normalized = CanonicalJson.normalizeScript(content);
        String contentHash = signingService.sha256Hex(normalized);

        Map<String, Object> metadata = Map.of("routeMapping", List.of());
        String metadataHash = signingService.hashMetadata(metadata);
        Map<String, Object> policy = Map.of();

        PackageManifest manifest = new PackageManifest(
                "test", "development", "1.0.0", "0.1.0",
                "tester", Instant.now(), keyProvider.getKeyId(),
                List.of(new PackageManifest.ScriptEntry(
                        "1", "/api/test", "GET", "1.0.0",
                        "DYNAMIC_QUERY", "LOW", contentHash, "")),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA", null);

        Map<String, byte[]> scripts = Map.of("/api/test", normalized);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, kp.getPrivate());
        return new PublishPackage(manifest, scripts, metadata, policy, signature);
    }
}
