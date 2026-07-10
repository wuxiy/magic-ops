package top.cywu.magicops.runtime.repair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.sqlguard.SqlGuardService;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;
import top.cywu.magicops.sqlguard.model.SqlType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dry-run 服务。在执行数据修复前，分析 SQL 并估算影响范围。
 *
 * <p>第一版只做 SQL 解析、权限校验、危险语句拦截和影响范围估算。
 */
@Service
public class DryRunService {

    private static final Logger log = LoggerFactory.getLogger(DryRunService.class);

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)(?:UPDATE|INSERT\\s+INTO|DELETE\\s+FROM)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

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

        // 1. SQL 分类
        SqlType sqlType = sqlGuardService.classify(sql);

        // 2. 检查是否为允许的修复类型
        List<String> warnings = new ArrayList<>();
        if (sqlType == SqlType.SELECT) {
            return DryRunReport.failure(scriptId, sql, "数据修复不允许 SELECT 类型");
        }
        if (sqlType == SqlType.TRUNCATE || sqlType == SqlType.DROP
                || sqlType == SqlType.ALTER || sqlType == SqlType.CREATE
                || sqlType == SqlType.GRANT) {
            return DryRunReport.failure(scriptId, sql,
                    "数据修复不允许 " + sqlType + " 类型操作");
        }

        // 3. 提取受影响的表
        List<String> affectedTables = extractTables(sql);

        // 4. 检查 WHERE 条件
        String upperSql = sql.toUpperCase().trim();
        if ((sqlType == SqlType.UPDATE || sqlType == SqlType.DELETE) && !upperSql.contains("WHERE")) {
            return DryRunReport.failure(scriptId, sql,
                    sqlType + " 语句必须包含 WHERE 条件");
        }

        // 5. 检查自由 UPDATE/DELETE 限制
        if (sqlType == SqlType.UPDATE || sqlType == SqlType.DELETE) {
            warnings.add("该操作为 " + sqlType + "，将受 SQL Guard 约束执行");
        }

        // 6. 估算影响行数（第一版基于简单规则）
        int estimatedRows = estimateAffectedRows(sql, sqlType);

        log.info("dry_run scriptId={} type={} tables={} estimatedRows={}",
                scriptId, sqlType, affectedTables, estimatedRows);

        return DryRunReport.success(scriptId, sql, sqlType.name(),
                estimatedRows, affectedTables, warnings);
    }

    /**
     * 从 SQL 中提取受影响的表名。
     */
    private List<String> extractTables(String sql) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
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
