package top.cywu.magicops.runtime.query;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.cywu.magicops.runtime.packageMgmt.PackageVerificationService;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 查询执行端点脚本引用契约测试（切片 34）。
 *
 * <p>覆盖关闭标准：脚本引用执行成功，裸 SQL 请求被拒绝。
 */
@SpringBootTest(properties = "MAGICOPS_ENVIRONMENT=development")
@AutoConfigureMockMvc
class QueryExecutionContractTest {

    private static final String BASIC = "Basic "
            + java.util.Base64.getEncoder().encodeToString("runtime:runtime".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PackageVerificationService verificationService;

    @Autowired
    private SigningService signingService;

    @Autowired
    private KeyProvider keyProvider;

    @Test
    void queryByScriptId_succeeds() throws Exception {
        verificationService.verifyAndActivate(buildQueryPackage("1", "SELECT 1 AS test_value"));

        mockMvc.perform(post("/api/query")
                        .header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scriptId\":\"1\",\"traceId\":\"contract-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void bareSqlRequest_rejected() throws Exception {
        verificationService.verifyAndActivate(buildQueryPackage("1", "SELECT 1 AS test_value"));

        // 裸 SQL：未携带 scriptId，应被拒绝
        mockMvc.perform(post("/api/query")
                        .header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"SELECT 1\",\"traceId\":\"bare-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("scriptId")));
    }

    @Test
    void unknownScriptId_rejected() throws Exception {
        verificationService.verifyAndActivate(buildQueryPackage("1", "SELECT 1 AS test_value"));

        mockMvc.perform(post("/api/query")
                        .header("Authorization", BASIC)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scriptId\":\"no-such\",\"traceId\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("不存在")));
    }

    private PublishPackage buildQueryPackage(String scriptId, String sql) throws Exception {
        KeyPair kp = keyProvider.getKeyPair();
        byte[] normalized = CanonicalJson.normalizeScript(sql);
        String contentHash = signingService.sha256Hex(normalized);

        Map<String, Object> metadata = Map.of(
                "routeMapping", List.of(),
                "datasourcePermissions", List.of(
                        Map.of("datasource", "default", "tables", List.of())),
                "scriptDatasource", Map.of(scriptId, "default"));
        String metadataHash = signingService.hashMetadata(metadata);
        Map<String, Object> policy = Map.of();

        PackageManifest manifest = new PackageManifest(
                "test", "development", "1.0.0", "0.1.0",
                "tester", Instant.now(), keyProvider.getKeyId(),
                List.of(new PackageManifest.ScriptEntry(
                        scriptId, "/api/test", "GET", "1.0.0",
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
