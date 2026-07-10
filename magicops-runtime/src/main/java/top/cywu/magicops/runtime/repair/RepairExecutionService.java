package top.cywu.magicops.runtime.repair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.audit.service.AuditWriteException;
import top.cywu.magicops.runtime.datasource.DynamicDataSourceManager;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.sqlguard.SqlGuardService;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;
import top.cywu.magicops.sqlguard.model.SqlType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 数据修复执行服务。
 *
 * <p>执行流程：
 * <ol>
 *   <li>验证 dry-run 已完成</li>
 *   <li>验证审批已通过</li>
 *   <li>SQL Guard 校验（写操作约束）</li>
 *   <li>执行修复 SQL</li>
 *   <li>记录审计（含回滚意图）</li>
 * </ol>
 */
@Service
public class RepairExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RepairExecutionService.class);
    private static final int MAX_AFFECTED_ROWS = 100;

    private final SqlGuardService sqlGuardService;
    private final DryRunService dryRunService;
    private final AuditService auditService;
    private final DynamicDataSourceManager dataSourceManager;

    public RepairExecutionService(SqlGuardService sqlGuardService,
                                  DryRunService dryRunService,
                                  AuditService auditService,
                                  DynamicDataSourceManager dataSourceManager) {
        this.sqlGuardService = sqlGuardService;
        this.dryRunService = dryRunService;
        this.auditService = auditService;
        this.dataSourceManager = dataSourceManager;
    }

    /**
     * 执行数据修复。
     *
     * @param pkg              已验签的发布包
     * @param sql              修复 SQL
     * @param dryRunReport     先前的 dry-run 报告（必须存在）
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

        // 1. 验证 dry-run 已完成
        if (dryRunReport == null || !dryRunReport.safe()) {
            String errorMsg = "无有效 dry-run 报告，不允许执行修复";
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), errorMsg, dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            return result;
        }

        // 2. SQL Guard 校验（写操作约束）
        SqlGuardResult guardResult = sqlGuardService.validateForRepair(sql);
        if (!guardResult.allowed()) {
            String errorMsg = "SQL Guard 拒绝修复: " + guardResult.reason();
            RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                    elapsed(startTime), errorMsg, dryRunReport);
            writeRepairAudit(pkg, result, repairDeclaration);
            return result;
        }

        try {
            // 3. 执行修复 SQL (UPDATE/INSERT/DELETE)
            DynamicDataSourceManager.QueryResult queryResult =
                    dataSourceManager.executeUpdate("default", sql, null);

            if (!queryResult.success()) {
                RepairResult result = RepairResult.failure(traceId, scriptId, scriptVersion,
                        elapsed(startTime), queryResult.errorMessage(), dryRunReport);
                writeRepairAudit(pkg, result, repairDeclaration);
                return result;
            }

            RepairResult result = RepairResult.success(traceId, scriptId, scriptVersion,
                    queryResult.rowCount(), elapsed(startTime),
                    dryRunReport, repairDeclaration.rollbackStrategy());

            // 4. 写入修复审计（关键审计，失败时阻断）
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
