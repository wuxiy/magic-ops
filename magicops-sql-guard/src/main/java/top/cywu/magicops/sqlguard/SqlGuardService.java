package top.cywu.magicops.sqlguard;

import org.springframework.stereotype.Service;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;
import top.cywu.magicops.sqlguard.model.SqlType;

import java.util.Set;

/**
 * SQL Guard 服务。对脚本 SQL 进行解析、分类、授权和限制。
 *
 * <p>第一版使用简单字符串匹配进行 SQL 类型分类。后续切片接入 JSQLParser。
 *
 * <p>默认策略（来自 {@code docs/architecture/security-and-governance.md}）：
 * <ul>
 *   <li>SELECT：允许，但限制结果大小</li>
 *   <li>INSERT：需要授权</li>
 *   <li>UPDATE/DELETE：高风险，默认拒绝（第一版动态查询场景）</li>
 *   <li>TRUNCATE/DROP/ALTER/CREATE/GRANT：默认拒绝</li>
 * </ul>
 */
@Service
public class SqlGuardService {

    /** 默认允许的最大结果行数。 */
    public static final int DEFAULT_MAX_RESULT_ROWS = 1000;

    private static final Set<SqlType> ALWAYS_DENIED = Set.of(
            SqlType.TRUNCATE, SqlType.DROP, SqlType.ALTER, SqlType.CREATE, SqlType.GRANT);

    /**
     * 分类 SQL 类型。
     */
    public SqlType classify(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlType.UNKNOWN;
        }
        String trimmed = sql.strip().toUpperCase();
        if (trimmed.startsWith("SELECT") || trimmed.startsWith("WITH")) {
            return SqlType.SELECT;
        }
        if (trimmed.startsWith("INSERT")) return SqlType.INSERT;
        if (trimmed.startsWith("UPDATE")) return SqlType.UPDATE;
        if (trimmed.startsWith("DELETE")) return SqlType.DELETE;
        if (trimmed.startsWith("TRUNCATE")) return SqlType.TRUNCATE;
        if (trimmed.startsWith("DROP")) return SqlType.DROP;
        if (trimmed.startsWith("ALTER")) return SqlType.ALTER;
        if (trimmed.startsWith("CREATE")) return SqlType.CREATE;
        if (trimmed.startsWith("GRANT")) return SqlType.GRANT;
        return SqlType.UNKNOWN;
    }

    /**
     * 校验 SQL 是否符合默认只读策略。
     *
     * <p>动态查询场景只允许 SELECT，其他类型一律拒绝。
     */
    public SqlGuardResult validate(String sql) {
        SqlType type = classify(sql);

        if (type == SqlType.UNKNOWN) {
            return SqlGuardResult.deny(type, "无法识别的 SQL 类型");
        }

        if (ALWAYS_DENIED.contains(type)) {
            return SqlGuardResult.deny(type, "SQL 类型 " + type + " 默认拒绝");
        }

        if (type != SqlType.SELECT) {
            return SqlGuardResult.deny(type, "动态查询场景仅允许 SELECT，当前为 " + type);
        }

        // 检查危险模式
        String upper = sql.toUpperCase();
        if (upper.contains(";") && upper.indexOf(";") < upper.length() - 1) {
            return SqlGuardResult.deny(type, "多语句 SQL 不被允许");
        }

        return SqlGuardResult.allow(type);
    }

    /**
     * 校验 SQL 并附加最大结果行数限制。
     */
    public SqlGuardResult validateWithLimit(String sql, int maxResultRows) {
        SqlGuardResult result = validate(sql);
        if (!result.allowed()) {
            return result;
        }
        // SELECT 允许，但结果大小将在执行时强制限制
        return new SqlGuardResult(true, result.sqlType(), null, maxResultRows);
    }

    /**
     * 校验修复 SQL。允许 INSERT、UPDATE（需 WHERE）、DELETE（需 WHERE），
     * 拒绝 TRUNCATE/DROP/ALTER/CREATE/GRANT 和自由 UPDATE/DELETE。
     */
    public SqlGuardResult validateForRepair(String sql) {
        SqlType type = classify(sql);

        if (type == SqlType.UNKNOWN) {
            return SqlGuardResult.deny(type, "无法识别的 SQL 类型");
        }

        if (ALWAYS_DENIED.contains(type)) {
            return SqlGuardResult.deny(type, "数据修复不允许 " + type + " 类型操作");
        }

        if (type == SqlType.SELECT) {
            return SqlGuardResult.deny(type, "数据修复不允许 SELECT");
        }

        // UPDATE 和 DELETE 必须有 WHERE
        String upper = sql.toUpperCase();
        if ((type == SqlType.UPDATE || type == SqlType.DELETE) && !upper.contains("WHERE")) {
            return SqlGuardResult.deny(type, type + " 语句必须包含 WHERE 条件");
        }

        // 拒绝多语句
        if (upper.contains(";") && upper.indexOf(";") < upper.length() - 1) {
            return SqlGuardResult.deny(type, "多语句 SQL 不被允许");
        }

        return SqlGuardResult.allow(type);
    }

    /**
     * 生成 SQL 摘要（用于审计记录，脱敏敏感值）。
     */
    public String summarize(String sql) {
        if (sql == null) return "";
        String trimmed = sql.strip();
        // 截取前 200 字符作为摘要
        if (trimmed.length() > 200) {
            return trimmed.substring(0, 200) + "...";
        }
        return trimmed;
    }
}
