package top.cywu.magicops.diagnosis.model;

/**
 * 诊断命令模板。约束可执行的 Arthas 命令及其参数。
 */
public record CommandTemplate(
        Long id,
        String name,
        String command,
        String parameterConstraints,
        RiskLevel riskLevel,
        boolean requiresApproval,
        String description
) {

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
