package top.cywu.magicops.console.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.entity.ScriptVersionEntity;
import top.cywu.magicops.console.repository.ScriptRepository;
import top.cywu.magicops.console.repository.ScriptVersionRepository;
import top.cywu.magicops.core.model.ScriptStatus;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.time.Instant;
import java.util.*;

/**
 * 发布包构建与签名服务。从已审批的脚本版本生成 canonical 发布包并签名。
 */
@Service
public class PackageBuildService {

    private static final Logger log = LoggerFactory.getLogger(PackageBuildService.class);

    private final ScriptRepository scriptRepository;
    private final ScriptVersionRepository versionRepository;
    private final SigningService signingService;
    private final KeyProvider keyProvider;

    public PackageBuildService(ScriptRepository scriptRepository,
                               ScriptVersionRepository versionRepository,
                               SigningService signingService,
                               KeyProvider keyProvider) {
        this.scriptRepository = scriptRepository;
        this.versionRepository = versionRepository;
        this.signingService = signingService;
        this.keyProvider = keyProvider;
    }

    /**
     * 从已审批的脚本版本构建并签名发布包。
     *
     * @param scriptIds   要打包的脚本 ID 列表
     * @param environment 目标环境
     * @param operator    操作人
     * @return 已签名的发布包
     */
    public PublishPackage buildAndSign(List<Long> scriptIds, String environment, String operator) {
        List<PackageManifest.ScriptEntry> scriptEntries = new ArrayList<>();
        Map<String, byte[]> scripts = new LinkedHashMap<>();
        Map<String, Object> metadata = new LinkedHashMap<>();

        for (Long scriptId : scriptIds) {
            ScriptEntity script = scriptRepository.findById(scriptId)
                    .orElseThrow(() -> new IllegalArgumentException("脚本不存在: " + scriptId));
            if (script.getStatus() != ScriptStatus.APPROVED) {
                throw new IllegalStateException("脚本状态不允许发布: " + script.getStatus());
            }
            ScriptVersionEntity version = versionRepository.findById(script.getCurrentVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("版本不存在"));

            String path = version.getRoutePath();
            byte[] normalizedContent = CanonicalJson.normalizeScript(version.getContent());
            String contentHash = signingService.sha256Hex(normalizedContent);

            scriptEntries.add(new PackageManifest.ScriptEntry(
                    String.valueOf(script.getId()),
                    path,
                    version.getRouteMethod(),
                    version.getVersion(),
                    script.getScriptType().name(),
                    version.getRiskLevel().name(),
                    contentHash,
                    ""
            ));
            scripts.put(path, normalizedContent);
        }

        // 构建 metadata
        metadata.put("datasourcePermissions", List.of());
        metadata.put("httpTargetPermissions", List.of());
        metadata.put("keyRefPermissions", List.of());
        metadata.put("routeMapping", scriptEntries.stream()
                .map(e -> Map.of("path", e.path(), "method", e.method(), "scriptId", e.scriptId()))
                .toList());

        String metadataHash = signingService.hashMetadata(metadata);

        // 构建 policy（最小化）
        Map<String, Object> policy = Map.of("readOnly", true);

        // 构建 manifest（不含签名）
        PackageManifest manifest = new PackageManifest(
                "default",
                environment,
                "1.0.0",
                "0.1.0",
                operator,
                Instant.now(),
                keyProvider.getKeyId(),
                scriptEntries,
                metadataHash,
                signingService.sha256Hex(CanonicalJson.toCanonicalBytes(policy)),
                "SHA256withRSA",
                null
        );

        // 构建签名输入并签名
        byte[] signingInput = signingService.buildSigningInput(manifest, metadata, scripts);
        byte[] signature = signingService.sign(signingInput, keyProvider.getPrivateKey());

        // 创建带签名的最终 manifest
        PackageManifest signedManifest = new PackageManifest(
                manifest.projectCode(),
                manifest.environment(),
                manifest.packageVersion(),
                manifest.runtimeVersion(),
                manifest.publishedBy(),
                manifest.publishedAt(),
                manifest.keyId(),
                manifest.scripts(),
                manifest.metadataHash(),
                manifest.policyHash(),
                manifest.signAlg(),
                Base64.getEncoder().encodeToString(signature)
        );

        log.info("package_built scripts={} environment={} operator={}", scriptIds.size(), environment, operator);
        return new PublishPackage(signedManifest, scripts, metadata, policy, signature);
    }
}
