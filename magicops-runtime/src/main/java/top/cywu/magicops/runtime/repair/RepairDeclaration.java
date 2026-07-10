package top.cywu.magicops.runtime.repair;

import top.cywu.magicops.core.model.RiskLevel;

/**
 * 数据修复风险声明。每个修复脚本必须声明风险等级和影响范围。
 */
public record RepairDeclaration(
        String scriptId,
        RiskLevel riskLevel,
        String targetTable,
        String operationType,
        boolean dryRunRequired,
        String rollbackStrategy
) {

    /**
     * 验证风险声明完整性。高风险操作需要完整的回滚策略。
     */
    public void validate() {
        if (scriptId == null || scriptId.isBlank()) {
            throw new IllegalArgumentException("scriptId 不能为空");
        }
        if (riskLevel == null) {
            throw new IllegalArgumentException("riskLevel 不能为空");
        }
        if (targetTable == null || targetTable.isBlank()) {
            throw new IllegalArgumentException("targetTable 不能为空");
        }
        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
            if (rollbackStrategy == null || rollbackStrategy.isBlank()) {
                throw new IllegalArgumentException("高风险操作必须提供回滚策略");
            }
        }
    }
}
