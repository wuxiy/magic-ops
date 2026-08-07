package top.cywu.magicops.runtime.repair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.sqlguard.SqlGuardService;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;
import top.cywu.magicops.sqlguard.model.SqlType;

import java.util.ArrayList;
import java.util.List;

/**
 * Dry-run 服务。在执行数据修复前，分析 SQL 并估算影响范围。
 *
 * <p>切片 30 起：语句合法性与 WHERE 约束复用 {@link SqlGuardService#validateForRepair}
 * （AST 级判断），表提取使用 {@link SqlGuardService#targetTables}，
 * 不再使用正则与字符串包含检查。
 */
@Service
public class DryRunService {

    private static final Logger log = LoggerFactory.getLogger(DryRunService.class);

    private final SqlGuardService sqlGuardService;

    public DryRunService(SqlGuardService sqlGuardService) {
        this.sqlGuardService = sqlGuardService;
    }

    /**
     * 执行 dry-run 分析，生成影响范围报告。
     *
     * @param scriptId 脚本 ID
     * @param sql      待分析的 SQL
     * @return dry-run 报告
     */
    public DryRunReport analyze(String scriptId, String sql) {
        if (sql == null || sql.isBlank()) {
            return DryRunReport.failure(scriptId, sql, "SQL 不能为空");
        }

        // 1. SQL 分类（AST）
        SqlType sqlType = sqlGuardService.classify(sql);

        // 2. 复用 SQL Guard 的修复约束校验（类型 + WHERE + 单语句）
        SqlGuardResult guardResult = sqlGuardService.validateForRepair(sql);
        if (!guardResult.allowed()) {
            return DryRunReport.failure(scriptId, sql, guardResult.reason());
        }

        // 3. 提取受影响的表（AST）
        List<String> affectedTables = sqlGuardService.targetTables(sql);

        // 4. 风险告警
        List<String> warnings = new ArrayList<>();
        if (sqlType == SqlType.UPDATE || sqlType == SqlType.DELETE) {
            warnings.add("该操作为 " + sqlType + "，将受 SQL Guard 约束执行");
        }

        // 5. 估算影响行数（第一版基于简单规则）
        int estimatedRows = estimateAffectedRows(sql, sqlType);

        log.info("dry_run scriptId={} type={} tables={} estimatedRows={}",
                scriptId, sqlType, affectedTables, estimatedRows);

        return DryRunReport.success(scriptId, sql, sqlType.name(),
                estimatedRows, affectedTables, warnings);
    }

    /**
     * 估算影响行数。第一版使用简单规则。
     */
    private int estimateAffectedRows(String sql, SqlType sqlType) {
        String upperSql = sql.toUpperCase();
        // 如果包含主键条件，估算影响 1 行
        if (upperSql.contains("WHERE") && (upperSql.contains("ID =") || upperSql.contains("ID="))) {
            return 1;
        }
        // 如果有 WHERE 条件但非主键，估算影响 10 行
        if (upperSql.contains("WHERE")) {
            return 10;
        }
        // 无 WHERE 条件（INSERT 除外），估算影响较大
        if (sqlType == SqlType.INSERT) {
            return 1;
        }
        return 100; // 无 WHERE 的 UPDATE/DELETE 影响范围较大
    }
}
