package top.cywu.magicops.console.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import top.cywu.magicops.core.model.RiskLevel;

/**
 * 创建版本请求。从草稿固化出不可变版本。
 */
public record CreateVersionRequest(
        @NotBlank String version,
        @NotNull RiskLevel riskLevel
) {}
