package top.cywu.magicops.console.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.entity.ScriptVersionEntity;
import top.cywu.magicops.console.repository.ApprovalRepository;
import top.cywu.magicops.console.repository.ScriptRepository;
import top.cywu.magicops.console.repository.ScriptVersionRepository;
import top.cywu.magicops.core.model.ApprovalDecision;
import top.cywu.magicops.core.model.ScriptStatus;
import top.cywu.magicops.core.model.ScriptType;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.key.KeyProvider;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.sqlguard.SqlGuardService;

import java.time.Instant;
import java.util.*;

/**
 * 发布包构建与签名服务。从已审批的脚本版本生成 canonical 发布包并签名。
 *
 * <p>切片 30 起，metadata 额外携带两类治理凭据（随包签名，Runtime fail-closed 校验）：
 * <ul>
 *   <li>{@code approvals}：每个脚本的 APPROVED 审批凭据（ID、决定、审批人、时间）</li>
 *   <li>{@code datasourcePermissions}：脚本 SQL 引用的表清单（表级白名单）</li>
 * </ul>
 */
@Service
public class PackageBuildService {

    private static final Logger log = LoggerFactory.getLogger(PackageBuildService.class);
    private static final String DEFAULT_DATASOURCE = "default";

    private final ScriptRepository scriptRepository;
    private final ScriptVersionRepository versionRepository;
    private final ApprovalRepository approvalRepository;
    private final SigningService signingService;
    private final KeyProvider keyProvider;
    private final SqlGuardService sqlGuardService;

    public PackageBuildService(ScriptRepository scriptRepository,
                               ScriptVersionRepository versionRepository,
                               ApprovalRepository approvalRepository,
                               SigningService signingService,
                               KeyProvider keyProvider,
                               SqlGuardService sqlGuardService) {
        this.scriptRepository = scriptRepository;
        this.versionRepository = versionRepository;
        this.approvalRepository = approvalRepository;
        this.signingService = signingService;
        this.keyProvider = keyProvider;
        this.sqlGuardService = sqlGuardService;
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
        List<Map<String, Object>> approvalProofs = new ArrayList<>();
        Set<String> referencedTables = new TreeSet<>();

        for (Long scriptId : scriptIds) {
            ScriptEntity script = scriptRepository.findById(scriptId)
                    .orElseThrow(() -> new IllegalArgumentException("脚本不存在: " + scriptId));
            if (script.getStatus() != ScriptStatus.APPROVED) {
                throw new IllegalStateException("脚本状态不允许发布: " + script.getStatus());
            }
            ScriptVersionEntity version = versionRepository.findById(script.getCurrentVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("版本不存在"));

            // 审批凭据（fail-closed）：无 APPROVED 审批记录不允许打包
            approvalProofs.add(buildApprovalProof(script, version));

            // 表级授权：从脚本 SQL 提取引用表（仅 SQL 类脚本）
            if (script.getScriptType() == ScriptType.DYNAMIC_QUERY
                    || script.getScriptType() == ScriptType.DATA_REPAIR) {
                referencedTables.addAll(extractDeclaredTables(script, version));
            }

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

        // 构建 metadata（治理凭据随包签名）
        metadata.put("datasourcePermissions", List.of(
                Map.of("datasource", DEFAULT_DATASOURCE, "tables", new ArrayList<>(referencedTables))));
        metadata.put("httpTargetPermissions", List.of());
        metadata.put("keyRefPermissions", List.of());
        metadata.put("approvals", approvalProofs);
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

    /**
     * 构建脚本的审批凭据。无审批记录或决定非 APPROVED 时抛出异常（fail-closed）。
     */
    private Map<String, Object> buildApprovalProof(ScriptEntity script, ScriptVersionEntity version) {
        ApprovalEntity approval = approvalRepository.findByScriptVersionId(version.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "脚本 " + script.getId() + " 版本 " + version.getVersion() + " 缺少审批记录，不允许打包"));
        if (approval.getDecision() != ApprovalDecision.APPROVED) {
            throw new IllegalStateException(
                    "脚本 " + script.getId() + " 审批决定为 " + approval.getDecision() + "，不允许打包");
        }

        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("scriptId", String.valueOf(script.getId()));
        proof.put("version", version.getVersion());
        proof.put("approvalId", String.valueOf(approval.getId()));
        proof.put("decision", approval.getDecision().name());
        proof.put("submittedBy", approval.getSubmittedBy());
        proof.put("decidedBy", approval.getDecidedBy());
        proof.put("decidedAt", approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null);
        return proof;
    }

    /**
     * 从脚本 SQL 提取引用表（表级白名单来源）。
     *
     * <p>严格模式：SQL 无法解析时拒绝打包，保证授权清单完整可信。
     */
    private List<String> extractDeclaredTables(ScriptEntity script, ScriptVersionEntity version) {
        try {
            var statement = sqlGuardService.parseSingleStatement(version.getContent());
            return sqlGuardService.tablesIn(statement);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("脚本 " + script.getId() + " 的 SQL 无法解析，"
                    + "无法确定表授权范围，不允许打包: " + e.getMessage(), e);
        }
    }
}
