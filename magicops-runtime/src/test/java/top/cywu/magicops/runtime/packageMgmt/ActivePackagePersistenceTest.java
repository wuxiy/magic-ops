package top.cywu.magicops.runtime.packageMgmt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.cywu.magicops.audit.repository.AuditRecordRepository;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 激活包持久化与启动重载测试（切片 33-b）。
 *
 * <p>覆盖关闭标准：
 * <ul>
 *   <li>激活包落库，Runtime 重启后自动重载并通过验签与环境校验；</li>
 *   <li>重载验签失败（payload 被篡改）时该行置 INACTIVE，不激活。</li>
 * </ul>
 *
 * <p>依赖 prod 拓扑下 Runtime 与 Console 共享 PostgreSQL；本测试在 H2 上验证落库与重载逻辑，
 * Docker E2E on PostgreSQL 由切片 33 收口阶段覆盖。
 */
@SpringBootTest(properties = "MAGICOPS_ENVIRONMENT=development")
class ActivePackagePersistenceTest {

    @Autowired
    private PackageVerificationService verificationService;

    @Autowired
    private ActivePackageRepository activePackageRepository;

    @Autowired
    private SigningService signingService;

    @Autowired
    private KeyProvider keyProvider;

    @Autowired
    private AuditRecordRepository auditRepository;

    @Test
    void activatedPackage_persistedAndReloadedAfterRestart() throws Exception {
        activePackageRepository.deleteAll();
        PublishPackage pkg = buildSignedPackage(keyProvider.getKeyId(), keyProvider.getKeyPair());

        verificationService.verifyAndActivate(pkg);

        // 落库：存在一条 ACTIVE 行
        var activeRow = activePackageRepository
                .findFirstByStatusOrderByActivatedAtDesc(ActivePackageEntity.STATUS_ACTIVE);
        assertTrue(activeRow.isPresent(), "激活包应已落库");
        assertEquals(pkg.manifest().packageVersion(), activeRow.get().getPackageVersion());

        // 模拟重启：清内存缓存，重新从 DB 重载
        invokeReload();

        PublishPackage reloaded = verificationService.getActivePackage();
        assertNotNull(reloaded, "重启后应重载激活包");
        assertEquals(pkg.manifest().packageVersion(), reloaded.manifest().packageVersion());
        assertEquals(pkg.manifest().environment(), reloaded.manifest().environment());
    }

    @Test
    void reloadTamperedPackage_deactivated_notActivated() throws Exception {
        activePackageRepository.deleteAll();
        PublishPackage pkg = buildSignedPackage(keyProvider.getKeyId(), keyProvider.getKeyPair());
        verificationService.verifyAndActivate(pkg);

        // 篡改落库 payload（破坏验签）
        var row = activePackageRepository
                .findFirstByStatusOrderByActivatedAtDesc(ActivePackageEntity.STATUS_ACTIVE).orElseThrow();
        row.setPayload("{\"manifest\":{}}");
        activePackageRepository.save(row);

        invokeReload();

        assertNull(verificationService.getActivePackage(), "篡改包重载验签失败后不应激活");
        var stillActive = activePackageRepository
                .findFirstByStatusOrderByActivatedAtDesc(ActivePackageEntity.STATUS_ACTIVE);
        assertTrue(stillActive.isEmpty(), "篡改行应已置为 INACTIVE");
    }

    /** 反射调用 @PostConstruct reloadActivePackage 模拟重启。 */
    private void invokeReload() throws Exception {
        var m = PackageVerificationService.class.getDeclaredMethod("reloadActivePackage");
        m.setAccessible(true);
        m.invoke(verificationService);
    }

    private PublishPackage buildSignedPackage(String keyId, KeyPair keyPair) throws Exception {
        String content = "return db.select('SELECT 1')";
        byte[] normalized = CanonicalJson.normalizeScript(content);
        String contentHash = signingService.sha256Hex(normalized);

        Map<String, Object> metadata = Map.of("routeMapping", List.of());
        String metadataHash = signingService.hashMetadata(metadata);
        Map<String, Object> policy = Map.of();

        PackageManifest manifest = new PackageManifest(
                "test", "development", "1.0.0", "0.1.0",
                "tester", Instant.now(), keyId,
                List.of(new PackageManifest.ScriptEntry(
                        "1", "/api/test", "GET", "1.0.0",
                        "DYNAMIC_QUERY", "LOW", contentHash, "")),
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA", null
        );

        Map<String, byte[]> scripts = Map.of("/api/test", normalized);
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyPair.getPrivate());
        return new PublishPackage(manifest, scripts, metadata, policy, signature);
    }
}
