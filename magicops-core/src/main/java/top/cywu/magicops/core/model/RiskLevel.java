package top.cywu.magicops.core.model;

/**
 * 风险等级。用于决定审批策略和执行控制。
 */
public enum RiskLevel {

    /** 低风险，标准审批流程。 */
    LOW,

    /** 中风险，需要额外复核。 */
    MEDIUM,

    /** 高风险，需要强制审批，审计失败时阻断执行。 */
    HIGH,

    /** 关键风险，需要多人审批，审计失败时立即阻断。 */
    CRITICAL
}
