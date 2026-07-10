package top.cywu.magicops.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ssssssss.magicapi.utils.ScriptManager;
import org.ssssssss.script.MagicScriptContext;

import java.util.Map;

/**
 * Magic-API 脚本引擎封装。为 MagicOps 提供统一的脚本执行接口。
 *
 * <p>封装 magic-api 核心引擎（{@link ScriptManager}），屏蔽上游 API 细节。
 * MagicOps Runtime 通过此类执行动态 API 脚本。
 */
public class ScriptEngineFacade {

    private static final Logger log = LoggerFactory.getLogger(ScriptEngineFacade.class);

    /**
     * 执行 magic-script 脚本代码（多语句）。
     *
     * @param scriptContent 脚本源代码
     * @param variables     注入变量
     * @return 执行结果
     */
    public ScriptExecutionResult execute(String scriptContent, Map<String, Object> variables) {
        long startTime = System.currentTimeMillis();
        try {
            MagicScriptContext context = new MagicScriptContext(variables);
            Object result = ScriptManager.executeScript(scriptContent, context);
            long duration = System.currentTimeMillis() - startTime;
            log.info("script_executed durationMs={} resultType={}",
                    duration, result != null ? result.getClass().getSimpleName() : "null");
            return ScriptExecutionResult.success(result, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("script_execution_failed durationMs={}", duration, e);
            return ScriptExecutionResult.failure(e.getMessage(), duration);
        }
    }

    /**
     * 执行 magic-script 表达式（自动添加 return）。
     *
     * @param expression 表达式
     * @param variables  注入变量
     * @return 执行结果
     */
    public ScriptExecutionResult executeExpression(String expression, Map<String, Object> variables) {
        long startTime = System.currentTimeMillis();
        try {
            Object result = ScriptManager.executeExpression(expression, variables);
            long duration = System.currentTimeMillis() - startTime;
            return ScriptExecutionResult.success(result, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return ScriptExecutionResult.failure(e.getMessage(), duration);
        }
    }

    /**
     * 验证脚本语法。
     *
     * @param scriptContent 脚本源代码
     * @return 语法是否合法
     */
    public boolean validateSyntax(String scriptContent) {
        try {
            MagicScriptContext context = new MagicScriptContext();
            ScriptManager.executeScript("var __syntax_check = function(){" + scriptContent + "}", context);
            return true;
        } catch (Exception e) {
            log.debug("syntax_validation_failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 脚本执行结果。
     */
    public record ScriptExecutionResult(
            boolean success,
            Object result,
            String errorMessage,
            long durationMs
    ) {
        public static ScriptExecutionResult success(Object result, long durationMs) {
            return new ScriptExecutionResult(true, result, null, durationMs);
        }

        public static ScriptExecutionResult failure(String errorMessage, long durationMs) {
            return new ScriptExecutionResult(false, null, errorMessage, durationMs);
        }
    }
}
