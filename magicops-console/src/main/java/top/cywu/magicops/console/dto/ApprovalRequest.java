package top.cywu.magicops.console.dto;

import jakarta.validation.constraints.NotNull;
import top.cywu.magicops.core.model.ApprovalDecision;

/**
 * 审批决策请求。
 */
public record ApprovalRequest(
        @NotNull ApprovalDecision decision,
        String comment
) {}
