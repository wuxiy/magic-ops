package top.cywu.magicops.sqlguard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import top.cywu.magicops.sqlguard.model.SqlGuardResult;
import top.cywu.magicops.sqlguard.model.SqlType;

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
}
