package top.cywu.magicops.core.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * magic-api 脚本引擎集成测试。验证脚本执行、表达式和错误处理。
 */
class ScriptEngineFacadeTest {

    private ScriptEngineFacade engine;

    @BeforeEach
    void setUp() {
        engine = new ScriptEngineFacade();
    }

    @Test
    void execute_returnExpression() {
        var result = engine.execute("return 1 + 2;", Map.of());
        assertTrue(result.success());
        assertEquals(3, ((Number) result.result()).intValue());
    }

    @Test
    void execute_withVariables() {
        var result = engine.execute("return x + y;", Map.of("x", 10, "y", 20));
        assertTrue(result.success());
        assertEquals(30, ((Number) result.result()).intValue());
    }

    @Test
    void execute_stringResult() {
        var result = engine.execute("return 'hello magic-api';", Map.of());
        assertTrue(result.success());
        assertEquals("hello magic-api", result.result());
    }

    @Test
    void execute_mapResult() {
        var result = engine.execute("return {name: 'test', value: 42};", Map.of());
        assertTrue(result.success());
        assertNotNull(result.result());
    }

    @Test
    void execute_durationRecorded() {
        var result = engine.execute("return 1;", Map.of());
        assertTrue(result.success());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void execute_complexScript() {
        String script = """
                var list = [1, 2, 3, 4, 5];
                var sum = 0;
                for (item in list) {
                    sum = sum + item;
                }
                return sum;
                """;
        var result = engine.execute(script, Map.of());
        assertTrue(result.success());
        assertEquals(15, ((Number) result.result()).intValue());
    }

    @Test
    void executeExpression_simple() {
        var result = engine.executeExpression("1 + 2", Map.of());
        assertTrue(result.success());
        assertEquals(3, ((Number) result.result()).intValue());
    }

    @Test
    void executeExpression_withVariables() {
        var result = engine.executeExpression("name + ' is ' + age",
                Map.of("name", "Alice", "age", 30));
        assertTrue(result.success());
        assertEquals("Alice is 30", result.result());
    }

    @Test
    void execute_runtimeError() {
        var result = engine.execute("var x = null; return x.property;", Map.of());
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void validateSyntax_valid() {
        // Valid script should execute without error
        var result = engine.execute("return 1 + 2;", Map.of());
        assertTrue(result.success());
    }

    @Test
    void validateSyntax_invalid() {
        // Invalid script should fail
        var result = engine.execute("@#$ invalid syntax !!!", Map.of());
        assertFalse(result.success());
    }
}
