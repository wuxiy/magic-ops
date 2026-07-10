package top.cywu.magicops.runtime.repair;

import java.time.Instant;

/**
 * 数据修复执行结果。
 */
public record RepairResult(
        String traceId,
        String scriptId,
        String scriptVersion,
        boolean success,
        int affectedRows,
        long durationMs,
        String errorMessage,
        DryRunReport dryRunReport,
        String rollbackIntent,
        Instant executedAt
) {

    public static RepairResult success(String traceId, String scriptId, String scriptVersion,
                                       int affectedRows, long durationMs,
                                       DryRunReport dryRunReport, String rollbackIntent) {
        return new RepairResult(traceId, scriptId, scriptVersion, true,
                affectedRows, durationMs, null, dryRunReport, rollbackIntent, Instant.now());
    }

    public static RepairResult failure(String traceId, String scriptId, String scriptVersion,
                                       long durationMs, String errorMessage,
                                       DryRunReport dryRunReport) {
        return new RepairResult(traceId, scriptId, scriptVersion, false,
                0, durationMs, errorMessage, dryRunReport, null, Instant.now());
    }
}
