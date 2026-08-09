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
        // 遗留直连路径：default 数据源，不做脚本路由与数据源权限校验（scriptId=null）。
        // 生产入口 QueryController 走下方 5 参重载（routeByScript=true）。
        return executeQueryInternal(pkg, sql, traceId, DEFAULT_DATASOURCE, null);
    }

    /**
     * 按脚本声明的数据源路由执行查询（切片 37，生产入口）。
     *
     * <p>从发布包 metadata 的 {@code scriptDatasource} 解析该脚本声明的数据源，
     * 校验该数据源在 {@code datasourcePermissions} 授权范围内后路由执行（fail-closed）。
     * 无 {@code scriptDatasource} 元数据时回退 default，但仍须经权限校验。
     *
     * @param pkg      已验签的发布包
     * @param sql      要执行的 SQL
     * @param traceId  追踪 ID
     * @param scriptId 脚本 ID（解析声明数据源）
     * @return 执行结果
     */
    public QueryExecutionResult executeQuery(PublishPackage pkg, String sql, String traceId, String scriptId,
                                             boolean routeByScript) {
        String datasource = resolveScriptDatasource(pkg, scriptId);
        return executeQueryInternal(pkg, sql, traceId, datasource, scriptId);
    }

    private QueryExecutionResult executeQueryInternal(PublishPackage pkg, String sql, String traceId,
                                                     String dataSourceName, String scriptId) {
        long startTime = System.currentTimeMillis();

        // 从发布包中查找脚本信息
        var scriptEntry = (scriptId != null)
                ? pkg.manifest().scripts().stream()
                        .filter(s -> scriptId.equals(s.scriptId())).findFirst().orElse(null)
                : pkg.manifest().scripts().stream()
                        .filter(s -> s.path() != null).findFirst().orElse(null);

        String resolvedScriptId = scriptEntry != null ? scriptEntry.scriptId() : "unknown";
        String scriptVersion = scriptEntry != null ? scriptEntry.version() : "unknown";
        String sqlSummary = sqlGuardService.summarize(sql);

        // 切片 37：校验数据源在发布包授权范围内（按脚本路由时）
        if (scriptId != null) {
            String dsViolation = checkDatasourcePermission(pkg, dataSourceName);
            if (dsViolation != null) {
                QueryExecutionResult result = QueryExecutionResult.failure(
                        traceId, resolvedScriptId, scriptVersion, sqlSummary, elapsed(startTime), dsViolation);
                writeExecutionAudit(pkg, result);
                return result;
            }
        }

        try {
            // 1. SQL Guard 校验
            SqlGuardResult guardResult = sqlGuardService.validateWithLimit(sql, MAX_RESULT_ROWS);
            if (!guardResult.allowed()) {
                String errorMsg = "SQL Guard 拒绝: " + guardResult.reason();
                QueryExecutionResult result = QueryExecutionResult.failure(
                        traceId, resolvedScriptId, scriptVersion, sqlSummary, elapsed(startTime), errorMsg);
                writeExecutionAudit(pkg, result);
                return result;
            }

            // 2. 表级白名单校验（切片 30/37）：SQL 引用的表必须在当前数据源授权范围内
            String tableViolation = checkTablePermissions(pkg, sql, dataSourceName);
            if (tableViolation != null) {
                QueryExecutionResult result = QueryExecutionResult.failure(
                        traceId, resolvedScriptId, scriptVersion, sqlSummary, elapsed(startTime), tableViolation);
                writeExecutionAudit(pkg, result);
                return result;
            }

            // 3. 通过 DynamicDataSourceManager 执行查询
            DynamicDataSourceManager.QueryResult queryResult =
                    dataSourceManager.executeQuery(dataSourceName, sql, null, MAX_RESULT_ROWS);

            if (!queryResult.success()) {
                QueryExecutionResult result = QueryExecutionResult.failure(
                        traceId, resolvedScriptId, scriptVersion, sqlSummary, elapsed(startTime), queryResult.errorMessage());
                writeExecutionAudit(pkg, result);
                return result;
            }

            // 4. 结果大小已由 DynamicDataSourceManager 限制
            int resultSize = queryResult.rowCount();

            QueryExecutionResult result = QueryExecutionResult.success(
                    traceId, resolvedScriptId, scriptVersion, sqlSummary, resultSize, elapsed(startTime));

            // 5. 写入执行审计
            writeExecutionAudit(pkg, result);

            log.info("query_executed traceId={} scriptId={} datasource={} resultSize={} durationMs={}",
                    traceId, resolvedScriptId, dataSourceName, resultSize, result.durationMs());
            return result;

        } catch (Exception e) {
            QueryExecutionResult result = QueryExecutionResult.failure(
                    traceId, resolvedScriptId, scriptVersion, sqlSummary, elapsed(startTime), e.getMessage());
            writeExecutionAudit(pkg, result);
            throw e;
        }
    }

    /**
     * 从发布包 metadata 解析脚本声明的数据源（切片 37）。
     *
     * <p>无 {@code scriptDatasource} 元数据时回退 {@code default}（遗留包路由兼容）。
     * 注意：回退 {@code default} 后仍须经 {@link #checkDatasourcePermission} 校验--
     * 切片 38 起 {@code datasourcePermissions} 缺失即 fail-closed，故遗留包若无任何
     * 授权声明会被拒绝，不会静默放行。
     */
    private String resolveScriptDatasource(PublishPackage pkg, String scriptId) {
        if (scriptId == null) {
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
     * 校验数据源在发布包 {@code datasourcePermissions} 授权范围内（切片 37，切片 38 收紧为 fail-closed）。
     *
     * <p>无授权声明（{@code datasourcePermissions} 缺失）时拒绝，不再静默放行。
     *
     * @return 违规说明；通过时返回 null
     */
    private String checkDatasourcePermission(PublishPackage pkg, String datasource) {
        Object permsObj = pkg.metadata() != null
                ? pkg.metadata().get("datasourcePermissions") : null;
        if (!(permsObj instanceof List<?> perms)) {
            return "发布包缺少 datasourcePermissions 授权声明，拒绝执行（fail-closed）";
        }
        for (Object item : perms) {
            if (item instanceof Map<?, ?> perm
                    && datasource.equals(String.valueOf(perm.get("datasource")))) {
                return null; // 命中授权
            }
        }
        return "数据源 " + datasource + " 不在发布包授权范围内";
    }

    /**
     * 校验 SQL 引用的表在指定数据源授权范围内（fail-closed，切片 38 收紧）。
     *
     * <p>metadata 无 {@code datasourcePermissions} 时拒绝（不再告警放行）；
     * 存在授权声明时严格校验。
     *
     * @param pkg        发布包
     * @param sql        待校验 SQL
     * @param datasource 目标数据源（切片 37：按数据源取授权表清单）
     * @return 违规说明；通过时返回 null
     */
    private String checkTablePermissions(PublishPackage pkg, String sql, String datasource) {
        Object permsObj = pkg.metadata() != null
                ? pkg.metadata().get("datasourcePermissions") : null;
        if (!(permsObj instanceof List<?> perms)) {
            return "发布包缺少 datasourcePermissions 授权声明，拒绝执行（fail-closed）";
        }

        List<String> allowed = List.of();
        for (Object item : perms) {
            if (item instanceof Map<?, ?> perm
                    && datasource.equals(String.valueOf(perm.get("datasource")))
                    && perm.get("tables") instanceof List<?> tableList) {
                allowed = tableList.stream().map(String::valueOf).toList();
                break;
            }
        }

        for (String table : sqlGuardService.tablesIn(sql)) {
            boolean matched = false;
            for (String a : allowed) {
                if (a.equalsIgnoreCase(table)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return "表 " + table + " 不在发布包授权范围内（授权表: " + allowed + "）";
            }
        }
        return null;
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
