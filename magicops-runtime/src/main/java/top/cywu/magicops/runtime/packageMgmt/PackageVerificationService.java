package top.cywu.magicops.runtime.packageMgmt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 发布包验证与激活服务。
 *
 * <p>Runtime 加载流程：
 * <pre>
 * receive -> verify environment -> verify keyId -> verify signature -> verify hashes -> persist -> activate -> audit
 * </pre>
 *
 * <p>切片 33 起，激活包落库（{@link ActivePackageRepository}），重启后由 {@link #reloadActivePackage()}
 * 重新验签并加载，消除内存态重启即丢的风险。仓储为可选依赖：不可用时回退到纯内存缓存（单元测试场景）。
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
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SigningService signingService;
    private final KeyProvider keyProvider;
    private final AuditService auditService;

    /** 当前激活的发布包（内存缓存，真正的激活事实源是 active_packages 表）。 */
    private final AtomicReference<PublishPackage> activePackage = new AtomicReference<>();

    /** 可选：激活包持久化仓储。单元测试无 Spring 时不注入，走内存缓存。 */
    @Autowired(required = false)
    private ActivePackageRepository packageRepository;

    public PackageVerificationService(SigningService signingService,
                                      KeyProvider keyProvider,
                                      AuditService auditService) {
        this.signingService = signingService;
        this.keyProvider = keyProvider;
        this.auditService = auditService;
    }

    /**
     * 启动时从数据库重载激活包并重新验签。
     *
     * <p>重载失败（payload 损坏、验签不通过、环境不匹配）时将该行置为 INACTIVE 并落审计，
     * 不激活，保证重启后不会加载被篡改的发布包。
     */
    @PostConstruct
    public void reloadActivePackage() {
        if (packageRepository == null) {
            return;
        }
        activePackage.set(null);
        var row = packageRepository.findFirstByStatusOrderByActivatedAtDesc(ActivePackageEntity.STATUS_ACTIVE);
        if (row.isEmpty()) {
            log.info("package_reload none active on startup");
            return;
        }
        ActivePackageEntity entity = row.get();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = MAPPER.readValue(entity.getPayload(), Map.class);
            PublishPackage pkg = PublishPackage.fromMap(payload);
            verify(pkg);
            activePackage.set(pkg);
            log.info("package_reloaded version={} env={}",
                    pkg.manifest().packageVersion(), pkg.manifest().environment());
        } catch (PackageRejectedException e) {
            deactivateAndAudit(entity, "重载验签拒绝: " + e.getMessage());
        } catch (Exception e) {
            deactivateAndAudit(entity, "重载反序列化失败: " + e.getMessage());
        }
    }

    /**
     * 验证并激活发布包。验证失败时抛出 {@link PackageRejectedException}。
     */
    public void verifyAndActivate(PublishPackage pkg) {
        verify(pkg);
        persistActive(pkg);
        activePackage.set(pkg);
        writeLoadAudit(pkg.manifest(), AuditEventType.PACKAGE_LOADED);
        log.info("package_activated version={} env={} scripts={}",
                pkg.manifest().packageVersion(), pkg.manifest().environment(),
                pkg.manifest().scripts().size());
    }

    /**
     * 获取当前激活的发布包。
     */
    public PublishPackage getActivePackage() {
        return activePackage.get();
    }

    /**
     * 下线当前激活的发布包（切片 33-d）。
     *
     * <p>下线是一次受控的停用动作：将 active_packages 表中的 ACTIVE 行置 INACTIVE，
     * 清空内存缓存，并写入关键审计。下线后 {@link #getActivePackage()} 返回 null，
     * 查询/修复/适配端点据此拒绝执行（"没有已激活的发布包"）。
     *
     * <p>回滚不走此路径：回滚是重新推送上一个已签名发布包，经 {@link #verifyAndActivate(PublishPackage)}
     * 验签后激活，表现为一次已签名发布动作。
     *
     * @param operator 操作人（Console 登录用户）
     * @param reason   下线原因
     * @return 被下线的包版本；无激活包时返回 null
     */
    public String deactivate(String operator, String reason) {
        PublishPackage current = activePackage.get();
        String version = current != null ? current.manifest().packageVersion() : null;

        if (packageRepository != null) {
            for (var e : packageRepository.findByStatus(ActivePackageEntity.STATUS_ACTIVE)) {
                e.setStatus(ActivePackageEntity.STATUS_INACTIVE);
                packageRepository.save(e);
            }
        }
        activePackage.set(null);

        auditService.write(AuditRecord.critical(
                AuditEventType.PACKAGE_DEACTIVATED, "PublishPackage",
                version != null ? version : "none", operator,
                Map.of("reason", reason != null ? reason : "未提供",
                        "stage", "deactivate")));
        log.info("package_deactivated version={} operator={} reason={}", version, operator, reason);
        return version;
    }

    /**
     * 校验签名、keyId、环境与 hash。验证失败抛 {@link PackageRejectedException}。
     */
    private void verify(PublishPackage pkg) {
        PackageManifest manifest = pkg.manifest();

        if (pkg.signature() == null || pkg.signature().length == 0) {
            reject("未签名发布包", manifest);
        }
        if (!keyProvider.isTrustedKeyId(manifest.keyId())) {
            reject("未知 key ID: " + manifest.keyId(), manifest);
        }
        String expectedEnv = resolveExpectedEnvironment();
        if (!expectedEnv.equals(manifest.environment())) {
            reject("环境不匹配: expected=" + expectedEnv + " actual=" + manifest.environment(), manifest);
        }
        if (!signingService.verifyPackage(pkg, keyProvider.getPublicKey())) {
            reject("签名或 hash 验证失败", manifest);
        }
    }

    /**
     * 落库激活包：将既有 ACTIVE 行置 INACTIVE，插入新 ACTIVE 行。
     */
    private void persistActive(PublishPackage pkg) {
        if (packageRepository == null) {
            return;
        }
        PackageManifest manifest = pkg.manifest();
        List<ActivePackageEntity> current = packageRepository.findByStatus(ActivePackageEntity.STATUS_ACTIVE);
        for (var e : current) {
            e.setStatus(ActivePackageEntity.STATUS_INACTIVE);
            packageRepository.save(e);
        }

        ActivePackageEntity entity = new ActivePackageEntity();
        entity.setPackageVersion(manifest.packageVersion());
        entity.setEnvironment(manifest.environment());
        entity.setKeyId(manifest.keyId());
        entity.setStatus(ActivePackageEntity.STATUS_ACTIVE);
        entity.setActivatedAt(Instant.now());
        entity.setActivatedBy(manifest.publishedBy());
        try {
            entity.setPayload(MAPPER.writeValueAsString(pkg.toMap()));
        } catch (JsonProcessingException e) {
            throw new PackageRejectedException("激活包序列化失败: " + e.getMessage());
        }
        packageRepository.save(entity);
    }

    private void deactivateAndAudit(ActivePackageEntity entity, String reason) {
        entity.setStatus(ActivePackageEntity.STATUS_INACTIVE);
        packageRepository.save(entity);
        auditService.write(AuditRecord.critical(
                AuditEventType.PACKAGE_REJECTED, "ActivePackage",
                entity.getPackageVersion(), "system",
                Map.of("reason", reason, "stage", "reload")));
        log.warn("package_reload_deactivated version={} reason={}", entity.getPackageVersion(), reason);
    }

    private void writeLoadAudit(PackageManifest manifest, AuditEventType event) {
        auditService.write(AuditRecord.critical(
                event, "PublishPackage",
                manifest.packageVersion(),
                manifest.publishedBy(),
                Map.of(
                        "environment", manifest.environment(),
                        "keyId", manifest.keyId(),
                        "scriptCount", manifest.scripts().size(),
                        "packageVersion", manifest.packageVersion()
                )
        ));
    }

    private String resolveExpectedEnvironment() {
        String env = System.getenv("MAGICOPS_ENVIRONMENT");
        return env != null ? env : "development";
    }

    private void reject(String reason, PackageManifest manifest) {
        log.warn("package_rejected reason={} version={}", reason, manifest.packageVersion());
        throw new PackageRejectedException(reason);
    }
}
