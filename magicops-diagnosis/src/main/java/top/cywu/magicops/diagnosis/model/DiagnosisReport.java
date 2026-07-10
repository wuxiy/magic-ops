package top.cywu.magicops.diagnosis.model;

import java.time.Instant;
import java.util.List;

/**
 * 诊断报告。汇总一次诊断会话的完整记录。
 */
public record DiagnosisReport(
        Long sessionId,
        String targetApp,
        String targetHost,
        int targetPort,
        Long operatorId,
        String status,
        Instant createdAt,
        Instant closedAt,
        long durationMs,
        List<CommandExecution> executions,
        String summary
) {}
