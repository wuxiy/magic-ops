package top.cywu.magicops.runtime.query;

import java.time.Instant;

/**
 * 查询执行结果。
 */
public record QueryExecutionResult(
        String traceId,
        String scriptId,
        String scriptVersion,
        String sqlSummary,
        int resultSize,
        long durationMs,
        boolean success,
        String errorMessage,
        Instant executedAt
) {

    public static QueryExecutionResult success(String traceId, String scriptId, String scriptVersion,
                                               String sqlSummary, int resultSize, long durationMs) {
        return new QueryExecutionResult(traceId, scriptId, scriptVersion,
                sqlSummary, resultSize, durationMs, true, null, Instant.now());
    }

    public static QueryExecutionResult failure(String traceId, String scriptId, String scriptVersion,
                                               String sqlSummary, long durationMs, String errorMessage) {
        return new QueryExecutionResult(traceId, scriptId, scriptVersion,
                sqlSummary, 0, durationMs, false, errorMessage, Instant.now());
    }
}
