package top.cywu.magicops.runtime.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.runtime.datasource.DynamicDataSourceManager;
import top.cywu.magicops.sign.model.PublishPackage;
import top.cywu.magicops.sqlguard.SqlGuardService;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;

import java.util.*;

/**
 * 查询执行服务。在 Runtime 端接收已验证的发布包，执行其中的查询脚本并记录审计。
 *
 * <p>执行流程：
 * <ol>
 *   <li>从发布包中获取脚本和 SQL</li>
 *   <li>通过 SQL Guard 校验（只读检查）</li>
 *   <li>通过 DynamicDataSourceManager 执行 SQL</li>
 *   <li>限制结果大小</li>
 *   <li>写入执行审计</li>
 * </ol>
 */
@Service
public class QueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutionService.class);

    /** 默认最大结果行数。 */
    private static final int MAX_RESULT_ROWS = 1000;
    private static final String DEFAULT_DATASOURCE = "default";

    private final SqlGuardService sqlGuardService;
    private final AuditService auditService;
    private final DynamicDataSourceManager dataSourceManager;

    public QueryExecutionService(SqlGuardService sqlGuardService,
                                 AuditService auditService,
                                 DynamicDataSourceManager dataSourceManager) {
        this.sqlGuardService = sqlGuardService;
        this.auditService = auditService;
        this.dataSourceManager = dataSourceManager;
    }

    /**
     * 执行发布包中的查询脚本（使用默认数据源）。
     *
     * @param pkg     已验签的发布包
     * @param sql     要执行的 SQL（从脚本内容中提取）
     * @param traceId 追踪 ID
     * @return 执行结果
     */
    public QueryExecutionResult executeQuery(PublishPackage pkg, String sql, String traceId) {
        return executeQuery(pkg, sql, traceId, DEFAULT_DATASOURCE);
    }

    /**
     * 执行发布包中的查询脚本。
     *
     * @param pkg            已验签的发布包
     * @param sql            要执行的 SQL（从脚本内容中提取）
     * @param traceId        追踪 ID
     * @param dataSourceName 数据源名称
     * @return 执行结果
     */
    public QueryExecutionResult executeQuery(PublishPackage pkg, String sql, String traceId, String dataSourceName) {
        long startTime = System.currentTimeMillis();

        // 从发布包中查找脚本信息
        var scriptEntry = pkg.manifest().scripts().stream()
                .filter(s -> s.path() != null)
                .findFirst()
                .orElse(null);

        String scriptId = scriptEntry != null ? scriptEntry.scriptId() : "unknown";
        String scriptVersion = scriptEntry != null ? scriptEntry.version() : "unknown";
        String sqlSummary = sqlGuardService.summarize(sql);

        try {
            // 1. SQL Guard 校验
            SqlGuardResult guardResult = sqlGuardService.validateWithLimit(sql, MAX_RESULT_ROWS);
            if (!guardResult.allowed()) {
                String errorMsg = "SQL Guard 拒绝: " + guardResult.reason();
                QueryExecutionResult result = QueryExecutionResult.failure(
                        traceId, scriptId, scriptVersion, sqlSummary, elapsed(startTime), errorMsg);
                writeExecutionAudit(pkg, result);
                return result;
            }

            // 2. 通过 DynamicDataSourceManager 执行查询
            DynamicDataSourceManager.QueryResult queryResult =
                    dataSourceManager.executeQuery(dataSourceName, sql, null, MAX_RESULT_ROWS);

            if (!queryResult.success()) {
                QueryExecutionResult result = QueryExecutionResult.failure(
                        traceId, scriptId, scriptVersion, sqlSummary, elapsed(startTime), queryResult.errorMessage());
                writeExecutionAudit(pkg, result);
                return result;
            }

            // 3. 结果大小已由 DynamicDataSourceManager 限制
            int resultSize = queryResult.rowCount();

            QueryExecutionResult result = QueryExecutionResult.success(
                    traceId, scriptId, scriptVersion, sqlSummary, resultSize, elapsed(startTime));

            // 4. 写入执行审计
            writeExecutionAudit(pkg, result);

            log.info("query_executed traceId={} scriptId={} resultSize={} durationMs={}",
                    traceId, scriptId, resultSize, result.durationMs());
            return result;

        } catch (Exception e) {
            QueryExecutionResult result = QueryExecutionResult.failure(
                    traceId, scriptId, scriptVersion, sqlSummary, elapsed(startTime), e.getMessage());
            writeExecutionAudit(pkg, result);
            throw e;
        }
    }

    private void writeExecutionAudit(PublishPackage pkg, QueryExecutionResult result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("traceId", result.traceId());
        details.put("scriptId", result.scriptId());
        details.put("scriptVersion", result.scriptVersion());
        details.put("sqlSummary", result.sqlSummary());
        details.put("resultSize", result.resultSize());
        details.put("durationMs", result.durationMs());
        details.put("success", result.success());
        if (result.errorMessage() != null) {
            details.put("errorMessage", result.errorMessage());
        }
        details.put("environment", pkg.manifest().environment());
        details.put("packageVersion", pkg.manifest().packageVersion());

        auditService.write(AuditRecord.critical(
                AuditEventType.SCRIPT_EXECUTED,
                "ScriptExecution",
                result.scriptId(),
                pkg.manifest().publishedBy(),
                details
        ));
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
