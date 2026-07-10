package top.cywu.magicops.runtime.adapter;

import java.time.Instant;

/**
 * HTTP 适配执行结果。
 */
public record AdapterResult(
        String traceId,
        String targetId,
        String path,
        boolean success,
        int statusCode,
        String responseBody,
        long durationMs,
        String errorMessage,
        Instant executedAt
) {

    public static AdapterResult success(String traceId, String targetId, String path,
                                        int statusCode, String responseBody, long durationMs) {
        return new AdapterResult(traceId, targetId, path, true, statusCode,
                responseBody, durationMs, null, Instant.now());
    }

    public static AdapterResult failure(String traceId, String targetId, String path,
                                        String errorMessage) {
        return new AdapterResult(traceId, targetId, path, false, 0,
                null, 0, errorMessage, Instant.now());
    }
}
