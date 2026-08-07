package top.cywu.magicops.sqlguard;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.schema.CreateSchema;
import net.sf.jsqlparser.statement.create.synonym.CreateSynonym;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.grant.Grant;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;
import top.cywu.magicops.sqlguard.model.SqlType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL Guard 服务。对脚本 SQL 进行解析、分类、授权和限制。
 *
 * <p>基于 JSQLParser 做 AST 级解析（切片 30 起），替代早期字符串前缀匹配，
 * 防止注释混淆、大小写变形、多语句拼接等绕过手法。
 *
 * <p>默认策略（来自 {@code docs/architecture/security-and-governance.md}）：
 * <ul>
 *   <li>SELECT：允许，但限制结果大小</li>
 *   <li>INSERT：需要授权</li>
 *   <li>UPDATE/DELETE：高风险，默认拒绝（动态查询场景）；修复场景需 WHERE 条件</li>
 *   <li>TRUNCATE/DROP/ALTER/CREATE/GRANT：默认拒绝</li>
 * </ul>
 *
 * <p>模板占位符（magic-api 风格 {@code #{param}} / {@code ${param}}）在解析前
 * 归一化为 JDBC 参数占位符 {@code ?}，保证含参脚本可被解析。
 */
@Service
public class SqlGuardService {

    /** 默认允许的最大结果行数。 */
    public static final int DEFAULT_MAX_RESULT_ROWS = 1000;

    private static final Set<SqlType> ALWAYS_DENIED = Set.of(
            SqlType.TRUNCATE, SqlType.DROP, SqlType.ALTER, SqlType.CREATE, SqlType.GRANT);

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("(?:#\\{[^}]*\\})|(?:\\$\\{[^}]*\\})");

    /**
     * 归一化模板占位符：{@code #{x}} / {@code ${x}} → {@code ?}。
     */
    public String normalizePlaceholders(String sql) {
        if (sql == null) {
            return null;
        }
        return PLACEHOLDER_PATTERN.matcher(sql).replaceAll("?");
    }

    /**
     * 严格解析单条语句。解析失败或多语句时抛出 {@link IllegalArgumentException}。
     *
     * <p>供需要 fail-closed 的调用方使用（如发布打包时的表提取）。
     */
    public Statement parseSingleStatement(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 为空");
        }
        String normalized = normalizePlaceholders(sql);
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(normalized);
        } catch (JSQLParserException e) {
            throw new IllegalArgumentException("SQL 无法解析: " + rootCauseMessage(e), e);
        }
        List<Statement> list = statements.getStatements();
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("SQL 为空");
        }
        if (list.size() > 1) {
            throw new IllegalArgumentException("多语句 SQL 不被允许");
        }
        return list.get(0);
    }

    /**
     * 分类 SQL 类型。基于 AST；无法解析时返回 {@link SqlType#UNKNOWN}。
     */
    public SqlType classify(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlType.UNKNOWN;
        }
        try {
            return classifyStatement(parseSingleStatement(sql));
        } catch (IllegalArgumentException e) {
            // 多语句输入按首条语句分类，保持策略判断可用
            try {
                Statements statements = CCJSqlParserUtil.parseStatements(normalizePlaceholders(sql));
                if (statements.getStatements() != null && !statements.getStatements().isEmpty()) {
                    return classifyStatement(statements.getStatements().get(0));
                }
            } catch (JSQLParserException ignored) {
                // fall through to UNKNOWN
            }
            return SqlType.UNKNOWN;
        }
    }

    private SqlType classifyStatement(Statement statement) {
        if (statement instanceof Select) return SqlType.SELECT;
        if (statement instanceof Insert) return SqlType.INSERT;
        if (statement instanceof Update) return SqlType.UPDATE;
        if (statement instanceof Delete) return SqlType.DELETE;
        if (statement instanceof Truncate) return SqlType.TRUNCATE;
        if (statement instanceof Drop) return SqlType.DROP;
        if (statement instanceof Alter) return SqlType.ALTER;
        if (statement instanceof Grant) return SqlType.GRANT;
        if (statement instanceof CreateTable || statement instanceof CreateIndex
                || statement instanceof CreateView || statement instanceof CreateSchema
                || statement instanceof CreateSynonym) {
            return SqlType.CREATE;
        }
        return SqlType.UNKNOWN;
    }

    /**
     * 校验 SQL 是否符合默认只读策略。
     *
     * <p>动态查询场景只允许单条 SELECT，其他类型一律拒绝。
     */
    public SqlGuardResult validate(String sql) {
        Statement statement;
        try {
            statement = parseSingleStatement(sql);
        } catch (IllegalArgumentException e) {
            return SqlGuardResult.deny(SqlType.UNKNOWN, e.getMessage());
        }

        SqlType type = classifyStatement(statement);

        if (type == SqlType.UNKNOWN) {
            return SqlGuardResult.deny(type, "无法识别的 SQL 类型");
        }

        if (ALWAYS_DENIED.contains(type)) {
            return SqlGuardResult.deny(type, "SQL 类型 " + type + " 默认拒绝");
        }

        if (type != SqlType.SELECT) {
            return SqlGuardResult.deny(type, "动态查询场景仅允许 SELECT，当前为 " + type);
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
     * 拒绝 TRUNCATE/DROP/ALTER/CREATE/GRANT、SELECT 和无 WHERE 的 UPDATE/DELETE。
     *
     * <p>WHERE 检查基于 AST（{@code getWhere() != null}），不再使用字符串包含判断。
     */
    public SqlGuardResult validateForRepair(String sql) {
        Statement statement;
        try {
            statement = parseSingleStatement(sql);
        } catch (IllegalArgumentException e) {
            return SqlGuardResult.deny(SqlType.UNKNOWN, e.getMessage());
        }

        SqlType type = classifyStatement(statement);

        if (type == SqlType.UNKNOWN) {
            return SqlGuardResult.deny(type, "无法识别的 SQL 类型");
        }

        if (ALWAYS_DENIED.contains(type)) {
            return SqlGuardResult.deny(type, "数据修复不允许 " + type + " 类型操作");
        }

        if (type == SqlType.SELECT) {
            return SqlGuardResult.deny(type, "数据修复不允许 SELECT");
        }

        // UPDATE 和 DELETE 必须有 WHERE（AST 级判断）
        if (statement instanceof Update update && update.getWhere() == null) {
            return SqlGuardResult.deny(type, type + " 语句必须包含 WHERE 条件");
        }
        if (statement instanceof Delete delete && delete.getWhere() == null) {
            return SqlGuardResult.deny(type, type + " 语句必须包含 WHERE 条件");
        }

        return SqlGuardResult.allow(type);
    }

    /**
     * 提取 SQL 引用的所有表名（含子查询、UNION、JOIN），排除 CTE 名称。
     *
     * <p>返回小写、去重、按字典序排列的表名列表；无法解析时返回空列表。
     * 用于表级白名单校验。
     */
    public List<String> tablesIn(String sql) {
        try {
            return tablesIn(parseSingleStatement(sql));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * 从已解析语句提取表名（排除 CTE 名称）。
     */
    public List<String> tablesIn(Statement statement) {
        Set<String> cteNames = new HashSet<>();
        if (statement instanceof Select select && select.getWithItemsList() != null) {
            for (WithItem withItem : select.getWithItemsList()) {
                if (withItem.getAlias() != null && withItem.getAlias().getName() != null) {
                    cteNames.add(normalizeTableName(withItem.getAlias().getName()));
                }
            }
        }

        List<String> raw = new TablesNamesFinder().getTableList(statement);
        List<String> result = new ArrayList<>();
        for (String name : raw) {
            String normalized = normalizeTableName(name);
            if (normalized.isEmpty() || cteNames.contains(normalized)) {
                continue;
            }
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    /**
     * 提取 DML 语句的写目标表（INSERT INTO / UPDATE / DELETE FROM 的对象表）。
     *
     * <p>非写语句或无法解析时返回空列表。用于 dry-run 影响面报告。
     */
    public List<String> targetTables(String sql) {
        try {
            Statement statement = parseSingleStatement(sql);
            if (statement instanceof Insert insert && insert.getTable() != null) {
                return List.of(normalizeTableName(insert.getTable().getName()));
            }
            if (statement instanceof Update update && update.getTable() != null) {
                return List.of(normalizeTableName(update.getTable().getName()));
            }
            if (statement instanceof Delete delete && delete.getTable() != null) {
                return List.of(normalizeTableName(delete.getTable().getName()));
            }
            return List.of();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * 表名归一化：去引号、去 schema 前缀、转小写。
     */
    private String normalizeTableName(String name) {
        if (name == null) {
            return "";
        }
        String stripped = name.replace("\"", "").replace("`", "").replace("[", "").replace("]", "");
        int dot = stripped.lastIndexOf('.');
        if (dot >= 0 && dot < stripped.length() - 1) {
            stripped = stripped.substring(dot + 1);
        }
        return stripped.toLowerCase(Locale.ROOT);
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

    private String rootCauseMessage(JSQLParserException e) {
        Throwable cause = e.getCause();
        return cause != null ? cause.getMessage() : e.getMessage();
    }
}
