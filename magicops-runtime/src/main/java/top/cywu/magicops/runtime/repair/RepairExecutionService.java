package top.cywu.magicops.runtime.repair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.audit.service.AuditWriteException;
import top.cywu.magicops.runtime.datasource.DynamicDataSourceManager;
import top.cywu.magicops.sign.SigningService;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.sqlguard.SqlGuardService;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据修复执行服务。
 *
 * <p>执行流程（切片 30 起全部为代码强制）：
 * <ol>
 *   <li>验证 dry-run 已完成</li>
 *   <li>SQL Guard 校验（AST 级写操作约束）</li>
 *   <li>验证审批凭据：发布包 metadata 必须携带该脚本的 APPROVED 审批记录（签名保护）</li>
 *   <li>内容绑定：执行 SQL 必须与包内经审批的脚本内容一致（contentHash 比对）</li>
 *   <li>表级白名单：SQL 引用的表必须在发布包授权的 datasourcePermissions 内</li>
 *   <li>事务化执行，影响行数超过 {@link #MAX_AFFECTED_ROWS} 即回滚</li>
 *   <li>记录审计（含回滚意图）</li>
 * </ol>
 */
@Service
public class RepairExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RepairExecutionService.class);

    /** 单次修复允许的最大影响行数，超过即回滚。 */
    public static final int MAX_AFFECTED_ROWS = 100;

    private static final String METADATA_APPROVALS = "approvals";
    private static final String METADATA_DATASOURCE_PERMISSIONS = "datasourcePermissions";
    private static final String DEFAULT_DATASOURCE = "default";

    private final SqlGuardService sqlGuardService;
    private final DryRunService dryRunService;
    private final AuditService auditService;
    private final DynamicDataSourceManager dataSourceManager;
    private final SigningService signingService;

    public RepairExecutionService(SqlGuardService sqlGuardService,
                                  DryRunService dryRunService,
                                  AuditService auditService,
                                  DynamicDataSourceManager dataSourceManager,
                                  SigningService signingService) {
        this.sqlGuardService = sqlGuardService;
        this.dryRunService = dryRunService;
        this.auditService = auditService;
        this.dataSourceManager = dataSourceManager;
        this.signingService = signingService;
    }

    /**
     * 执行数据修复。
     *
     * @param pkg               已验签的发布包
     * @param sql               修复 SQL
     * @param dryRunReport      先前的 dry-run 报告（必须存在）
     * @param repairDeclaration 修复风险声明
     * @return 修复执行结果
     */
    public RepairResult execute(PublishPackage pkg, String sql,
                                DryRunReport dryRunReport,
                                RepairDeclaration repairDeclaration) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();

        var scriptEntry = pkg.manifest().scripts().stream().findFirst().orElse(null);
        String scriptId = scriptEntry != null ? scriptEntry.scriptId() : "unknown";
        String scriptVersion = scriptEntry != null ? scriptEntry.version() : "unknown";

        // 切片 37：按脚本声明的数据源路由（无声明时回退 default）
        String datasource = resolveScriptDatasource(pkg, scriptId);

        // 1. 验证 dry-run 已完成
        if (dryRunReport == null || !dryRunReport.safe()) {
            String errorMsg = "无有效 dry-run 报告，不允许执行修复";
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), errorMsg, dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            return result;
        }

        // 2. SQL Guard 校验（AST 级写操作约束）
        SqlGuardResult guardResult = sqlGuardService.validateForRepair(sql);
        if (!guardResult.allowed()) {
            String errorMsg = "SQL Guard 拒绝修复: " + guardResult.reason();
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), errorMsg, dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            return result;
        }

        // 3. 验证审批凭据（发布包 metadata 中的 APPROVED 审批记录）
        Map<String, Object> approvalProof = findApprovalProof(pkg, scriptId, scriptVersion);
        if (approvalProof == null) {
            String errorMsg = "修复未获得审批：发布包缺少脚本 " + scriptId + "@" + scriptVersion
                    + " 的 APPROVED 审批凭据";
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), errorMsg, dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            return result;
        }

        // 4. 内容绑定：执行 SQL 必须与包内经审批的脚本内容一致
        if (scriptEntry != null && !contentMatchesPackage(sql, scriptEntry, pkg)) {
            String errorMsg = "执行内容与已审批脚本不一致（contentHash 不匹配），拒绝执行";
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), errorMsg, dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            return result;
        }

        // 5. 表级白名单：SQL 引用的表必须在当前数据源授权范围内
        String tableViolation = checkTablePermissions(pkg, sql, datasource);
        if (tableViolation != null) {
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), tableViolation, dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            return result;
        }

        try {
            // 6. 事务化执行修复 SQL，影响行数超限自动回滚
            DynamicDataSourceManager.QueryResult queryResult =
                    dataSourceManager.executeUpdateInTransaction(
                            datasource, sql, null, MAX_AFFECTED_ROWS);

            if (!queryResult.success()) {
                RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                        elapsed(startTime), queryResult.errorMessage(), dryRunReport);
                writeRepairAudit(pkg, result, repairDeclaration);
                return result;
            }

            RepairResult result = RepairResult.success(traceId, scriptId, scriptVersion,
                    queryResult.rowCount(), elapsed(startTime),
                    dryRunReport, repairDeclaration.rollbackStrategy());

            // 7. 写入修复审计（关键审计，失败时阻断）
            writeRepairAudit(pkg, result, repairDeclaration);

            log.info("repair_executed traceId={} scriptId={} affectedRows={} durationMs={}",
                    traceId, scriptId, queryResult.rowCount(), result.durationMs());
            return result;

        } catch (AuditWriteException e) {
            // 关键审计写入失败，阻断执行
            throw e;
        } catch (Exception e) {
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), e.getMessage(), dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            throw e;
        }
    }

    /**
     * 从发布包 metadata 中查找脚本对应的 APPROVED 审批凭据。
     *
     * <p>metadata 随包签名，凭据不可伪造。未找到或决定非 APPROVED 返回 null（fail-closed）。
     */
    private Map<String, Object> findApprovalProof(PublishPackage pkg, String scriptId, String version) {
        Object approvalsObj = pkg.metadata() != null ? pkg.metadata().get(METADATA_APPROVALS) : null;
        if (!(approvalsObj instanceof List<?> approvals)) {
            return null;
        }
        for (Object item : approvals) {
            if (!(item instanceof Map<?, ?> proof)) {
                continue;
            }
            boolean scriptMatches = String.valueOf(proof.get("scriptId")).equals(scriptId)
                    && String.valueOf(proof.get("version")).equals(version);
            boolean approved = "APPROVED".equals(String.valueOf(proof.get("decision")));
            if (scriptMatches && approved) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) proof;
                return typed;
            }
        }
        return null;
    }

    /**
     * 校验执行 SQL 与包内脚本内容一致（规范化后 contentHash 比对）。
     */
    private boolean contentMatchesPackage(String sql, PackageManifest.ScriptEntry entry,
                                          PublishPackage pkg) {
        byte[] packaged = pkg.scripts() != null ? pkg.scripts().get(entry.path()) : null;
        if (packaged == null) {
            return false;
        }
        String actualHash = signingService.sha256Hex(CanonicalJson.normalizeScript(sql));
        return actualHash.equals(entry.contentHash());
    }

    /**
     * 从发布包 metadata 解析脚本声明的数据源（切片 37）。
     * 无 {@code scriptDatasource} 元数据时回退 default（遗留包兼容）。
     */
    private String resolveScriptDatasource(PublishPackage pkg, String scriptId) {
        if (scriptId == null || "unknown".equals(scriptId)) {
            return DEFAULT_DATASOURCE;
        }
        Object mapping = pkg.metadata() != null ? pkg.metadata().get("scriptDatasource") : null;
        if (mapping instanceof Map<?, ?> m) {
            Object ds = m.get(scriptId);
            if (ds != null && !String.valueOf(ds).isBlank()) {
                return String.valueOf(ds);
            }
        }
        return DEFAULT_DATASOURCE;
    }

    /**
     * 校验 SQL 引用的表在指定数据源授权范围内。
     *
     * <p>metadata 无 {@code datasourcePermissions} 时按遗留包放行并告警；
     * 存在授权声明时严格校验（fail-closed）。
     *
     * @return 违规说明；通过时返回 null
     */
    private String checkTablePermissions(PublishPackage pkg, String sql, String datasource) {
        Object permsObj = pkg.metadata() != null
                ? pkg.metadata().get(METADATA_DATASOURCE_PERMISSIONS) : null;
        if (!(permsObj instanceof List<?> perms)) {
            log.warn("package_missing_datasource_permissions version={} legacy_mode=allow",
                    pkg.manifest().packageVersion());
            return null;
        }

        List<String> allowed = allowedTables(perms, datasource);
        List<String> referenced = sqlGuardService.tablesIn(sql);
        for (String table : referenced) {
            if (!containsIgnoreCase(allowed, table)) {
                return "表 " + table + " 不在发布包授权范围内（授权表: " + allowed + "）";
            }
        }
        return null;
    }

    private List<String> allowedTables(List<?> perms, String datasource) {
        for (Object item : perms) {
            if (item instanceof Map<?, ?> perm
                    && datasource.equals(String.valueOf(perm.get("datasource")))) {
                Object tables = perm.get("tables");
                if (tables instanceof List<?> tableList) {
                    return tableList.stream().map(String::valueOf).toList();
                }
            }
        }
        return List.of();
    }

    private boolean containsIgnoreCase(List<String> allowed, String table) {
        for (String a : allowed) {
            if (a.equalsIgnoreCase(table)) {
                return true;
            }
        }
        return false;
    }

    private void writeRepairAudit(PublishPackage pkg, RepairResult result,
                                  RepairDeclaration declaration) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("traceId", result.traceId());
        details.put("scriptId", result.scriptId());
        details.put("scriptVersion", result.scriptVersion());
        details.put("success", result.success());
        details.put("affectedRows", result.affectedRows());
        details.put("durationMs", result.durationMs());
        if (result.errorMessage() != null) {
            details.put("errorMessage", result.errorMessage());
        }
        if (result.rollbackIntent() != null) {
            details.put("rollbackIntent", result.rollbackIntent());
        }
        if (declaration != null) {
            details.put("riskLevel", declaration.riskLevel().name());
            details.put("targetTable", declaration.targetTable());
        }
        details.put("environment", pkg.manifest().environment());
        details.put("packageVersion", pkg.manifest().packageVersion());

        auditService.write(AuditRecord.critical(
                AuditEventType.SCRIPT_EXECUTED,
                "RepairExecution",
                result.scriptId(),
                pkg.manifest().publishedBy(),
                details
        ));
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
