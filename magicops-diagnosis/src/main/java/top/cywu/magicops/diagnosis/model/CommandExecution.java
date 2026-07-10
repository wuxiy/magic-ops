package top.cywu.magicops.diagnosis.model;

import java.time.Instant;

/**
 * 命令执行记录。记录单次 Arthas 命令执行的输入、输出和状态。
 */
public record CommandExecution(
        Long id,
        Long sessionId,
        Long templateId,
        String parameters,
        String resolvedCommand,
        String output,
        long durationMs,
        ExecutionStatus status,
        Instant executedAt
) {

    public enum ExecutionStatus {
        SUCCESS, FAILED, TIMEOUT, REJECTED
    }
}
