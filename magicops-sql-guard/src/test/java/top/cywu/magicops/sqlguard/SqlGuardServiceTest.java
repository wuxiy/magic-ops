package top.cywu.magicops.sqlguard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;
import top.cywu.magicops.sqlguard.model.SqlType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL Guard 测试。覆盖分类、只读策略、危险语句拦截和结果大小限制。
 */
class SqlGuardServiceTest {

    private SqlGuardService sqlGuardService;

    @BeforeEach
    void setUp() {
        sqlGuardService = new SqlGuardService();
    }

    // ---- SQL 分类测试 ----

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM users",
            "select count(*) from orders",
            "  SELECT id FROM patients WHERE id = 1",
            "WITH cte AS (SELECT 1) SELECT * FROM cte"
    })
    void classify_select(String sql) {
        assertEquals(SqlType.SELECT, sqlGuardService.classify(sql));
    }

    @Test
    void classify_insert() {
        assertEquals(SqlType.INSERT, sqlGuardService.classify("INSERT INTO users VALUES (1, 'test')"));
    }

    @Test
    void classify_update() {
        assertEquals(SqlType.UPDATE, sqlGuardService.classify("UPDATE users SET name = 'test' WHERE id = 1"));
    }

    @Test
    void classify_delete() {
        assertEquals(SqlType.DELETE, sqlGuardService.classify("DELETE FROM users WHERE id = 1"));
    }

    @Test
    void classify_truncate() {
        assertEquals(SqlType.TRUNCATE, sqlGuardService.classify("TRUNCATE TABLE users"));
    }

    @Test
    void classify_drop() {
        assertEquals(SqlType.DROP, sqlGuardService.classify("DROP TABLE users"));
    }

    @Test
    void classify_alter() {
        assertEquals(SqlType.ALTER, sqlGuardService.classify("ALTER TABLE users ADD COLUMN age INT"));
    }

    @Test
    void classify_create() {
        assertEquals(SqlType.CREATE, sqlGuardService.classify("CREATE TABLE users (id INT)"));
    }

    @Test
    void classify_grant() {
        assertEquals(SqlType.GRANT, sqlGuardService.classify("GRANT SELECT ON users TO role1"));
    }

    @Test
    void classify_emptyOrNull() {
        assertEquals(SqlType.UNKNOWN, sqlGuardService.classify(null));
        assertEquals(SqlType.UNKNOWN, sqlGuardService.classify(""));
        assertEquals(SqlType.UNKNOWN, sqlGuardService.classify("   "));
    }

    // ---- 只读策略测试 ----

    @Test
    void validate_allowsSelect() {
        SqlGuardResult result = sqlGuardService.validate("SELECT * FROM users");
        assertTrue(result.allowed());
        assertEquals(SqlType.SELECT, result.sqlType());
    }

    @Test
    void validate_allowsWithCte() {
        SqlGuardResult result = sqlGuardService.validate("WITH cte AS (SELECT 1) SELECT * FROM cte");
        assertTrue(result.allowed());
        assertEquals(SqlType.SELECT, result.sqlType());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO users VALUES (1, 'test')",
            "UPDATE users SET name = 'test' WHERE id = 1",
            "DELETE FROM users WHERE id = 1",
            "TRUNCATE TABLE users",
            "DROP TABLE users",
            "ALTER TABLE users ADD COLUMN age INT",
            "CREATE TABLE test (id INT)",
            "GRANT SELECT ON users TO role1"
    })
    void validate_rejectsNonSelect(String sql) {
        SqlGuardResult result = sqlGuardService.validate(sql);
        assertFalse(result.allowed(), "应拒绝非 SELECT 语句: " + sql);
    }

    @Test
    void validate_rejectsMultiStatement() {
        SqlGuardResult result = sqlGuardService.validate("SELECT 1; DROP TABLE users");
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("多语句"));
    }

    @Test
    void validate_rejectsUnknown() {
        SqlGuardResult result = sqlGuardService.validate("WEIRD SQL HERE");
        assertFalse(result.allowed());
    }

    // ---- 结果大小限制 ----

    @Test
    void validateWithLimit_setsMaxRows() {
        SqlGuardResult result = sqlGuardService.validateWithLimit("SELECT * FROM users", 500);
        assertTrue(result.allowed());
        assertEquals(500, result.estimatedAffectedRows());
    }

    @Test
    void validateWithLimit_rejectsInvalidSql() {
        SqlGuardResult result = sqlGuardService.validateWithLimit("DELETE FROM users", 500);
        assertFalse(result.allowed());
    }

    // ---- SQL 摘要 ----

    @Test
    void summarize_truncatesLongSql() {
        String longSql = "SELECT " + "a".repeat(300) + " FROM users";
        String summary = sqlGuardService.summarize(longSql);
        assertTrue(summary.length() <= 204); // 200 + "..."
        assertTrue(summary.endsWith("..."));
    }

    @Test
    void summarize_shortSqlUnchanged() {
        String sql = "SELECT 1";
        assertEquals(sql, sqlGuardService.summarize(sql));
    }

    // ---- AST 绕过抵抗（切片 30 加固）----

    @ParameterizedTest
    @ValueSource(strings = {
            "/* comment */ DROP TABLE users",
            "DROP/**/TABLE users",
            "   DROP TABLE users   ",
            "drop table users"
    })
    void validate_rejectsObfuscatedDrop(String sql) {
        SqlGuardResult result = sqlGuardService.validate(sql);
        assertFalse(result.allowed(), "注释/空白变形不应绕过拦截: " + sql);
    }

    @Test
    void validate_rejectsInlineCommentWrite() {
        SqlGuardResult result = sqlGuardService.validate("SELECT 1; /* hide */ DELETE FROM users");
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("多语句"));
    }

    @Test
    void validate_allowsPlaceholderParam() {
        // magic-api 占位符归一化后应为合法 SELECT
        SqlGuardResult result = sqlGuardService.validate(
                "SELECT id, name FROM patients WHERE id = #{id}");
        assertTrue(result.allowed());
        assertEquals(SqlType.SELECT, result.sqlType());
    }

    @Test
    void validateForRepair_updateWithCommentWhere() {
        SqlGuardResult result = sqlGuardService.validateForRepair(
                "UPDATE /* c */ orders SET status='X' WHERE id = 1");
        assertTrue(result.allowed());
    }

    @Test
    void validateForRepair_rejectsCommentHiddenNoWhere() {
        // WHERE 检查基于 AST，注释不能伪造 WHERE
        SqlGuardResult result = sqlGuardService.validateForRepair(
                "UPDATE orders SET status='X' /* WHERE id=1 */");
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("WHERE"));
    }

    @Test
    void classify_placeholderNormalized() {
        assertEquals(SqlType.SELECT,
                sqlGuardService.classify("SELECT * FROM t WHERE a = #{a} AND b = ${b}"));
    }

    // ---- 表提取（切片 30，表白名单基础）----

    @Test
    void tablesIn_simpleSelect() {
        assertEquals(List.of("patients"),
                sqlGuardService.tablesIn("SELECT * FROM patients WHERE id = 1"));
    }

    @Test
    void tablesIn_joinAndSubquery() {
        List<String> tables = sqlGuardService.tablesIn(
                "SELECT a.id FROM orders a JOIN users b ON a.uid = b.id "
                        + "WHERE a.id IN (SELECT oid FROM audit_log)");
        assertEquals(List.of("audit_log", "orders", "users"), tables);
    }

    @Test
    void tablesIn_cteExcluded() {
        // CTE 名称不应被当作物理表
        List<String> tables = sqlGuardService.tablesIn(
                "WITH cte AS (SELECT id FROM patients) SELECT * FROM cte");
        assertEquals(List.of("patients"), tables);
    }

    @Test
    void tablesIn_unparseable_returnsEmpty() {
        assertEquals(List.of(), sqlGuardService.tablesIn("NOT VALID SQL"));
    }

    @Test
    void targetTables_update() {
        assertEquals(List.of("orders"),
                sqlGuardService.targetTables("UPDATE orders SET s='X' WHERE id = 1"));
    }

    @Test
    void targetTables_deleteAndInsert() {
        assertEquals(List.of("orders"),
                sqlGuardService.targetTables("DELETE FROM orders WHERE id = 1"));
        assertEquals(List.of("orders"),
                sqlGuardService.targetTables("INSERT INTO orders (id) VALUES (9)"));
    }

    @Test
    void targetTables_select_returnsEmpty() {
        assertEquals(List.of(), sqlGuardService.targetTables("SELECT * FROM orders"));
    }

    @Test
    void parseSingleStatement_rejectsMultiStatement() {
        assertThrows(IllegalArgumentException.class,
                () -> sqlGuardService.parseSingleStatement("SELECT 1; DROP TABLE users"));
    }
}
