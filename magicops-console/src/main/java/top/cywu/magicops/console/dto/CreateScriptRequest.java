package top.cywu.magicops.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.cywu.magicops.core.model.RiskLevel;
import top.cywu.magicops.core.model.ScriptType;

/**
 * 创建脚本请求。
 */
public record CreateScriptRequest(
        @NotBlank String name,
        String projectCode,
        @NotNull ScriptType scriptType
) {}
