package top.cywu.magicops.runtime.packageMgmt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PublishPackage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 发布包验证与激活服务。
 *
 * <p>Runtime 加载流程：
 * <pre>
 * receive → verify environment → verify keyId → verify signature → verify hashes → activate → audit
 * </pre>
 *
 * <p>Runtime 拒绝规则：
 * <ul>
 *   <li>未签名发布包</li>
 *   <li>使用未知 key ID 签名的发布包</li>
 *   <li>属于其他环境的发布包</li>
 *   <li>content、metadata 或 policy hash 不匹配的发布包</li>
 * </ul>
 */
@Service
public class PackageVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PackageVerificationService.class);

    private final SigningService signingService;
    private final KeyProvider keyProvider;
    private final AuditService auditService;

    /**
     * 当前激活的发布包（原子引用，支持原子激活）。
     */
    private final AtomicReference<PublishPackage> activePackage = new AtomicReference<>();

    public PackageVerificationService(SigningService signingService,
                                      KeyProvider keyProvider,
                                      AuditService auditService) {
        this.signingService = signingService;
        this.keyProvider = keyProvider;
        this.auditService = auditService;
    }

    /**
     * 验证并激活发布包。验证失败时抛出异常。
     */
    public void verifyAndActivate(PublishPackage pkg) {
        var manifest = pkg.manifest();

        // 1. 验证签名存在
        if (pkg.signature() == null || pkg.signature().length == 0) {
            reject("未签名发布包", manifest);
        }

        // 2. 验证 keyId
        if (!keyProvider.isTrustedKeyId(manifest.keyId())) {
            reject("未知 key ID: " + manifest.keyId(), manifest);
        }

        // 3. 验证环境
        String expectedEnv = resolveExpectedEnvironment();
        if (!expectedEnv.equals(manifest.environment())) {
            reject("环境不匹配: expected=" + expectedEnv + " actual=" + manifest.environment(), manifest);
        }

        // 4. 验证签名和 hash
        if (!signingService.verifyPackage(pkg, keyProvider.getPublicKey())) {
            reject("签名或 hash 验证失败", manifest);
        }

        // 5. 原子激活
        activePackage.set(pkg);

        // 6. 写入加载审计
        auditService.write(AuditRecord.critical(
                AuditEventType.PACKAGE_LOADED,
                "PublishPackage",
                manifest.packageVersion(),
                manifest.publishedBy(),
                Map.of(
                        "environment", manifest.environment(),
                        "keyId", manifest.keyId(),
                        "scriptCount", manifest.scripts().size(),
                        "packageVersion", manifest.packageVersion()
                )
        ));

        log.info("package_activated version={} env={} scripts={}",
                manifest.packageVersion(), manifest.environment(), manifest.scripts().size());
    }

    /**
     * 获取当前激活的发布包。
     */
    public PublishPackage getActivePackage() {
        return activePackage.get();
    }

    private String resolveExpectedEnvironment() {
        String env = System.getenv("MAGICOPS_ENVIRONMENT");
        return env != null ? env : "development";
    }

    private void reject(String reason, top.cywu.magicops.sign.model.PackageManifest manifest) {
        log.warn("package_rejected reason={} version={}", reason, manifest.packageVersion());
        throw new PackageRejectedException(reason);
    }
}
