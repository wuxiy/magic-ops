package top.cywu.magicops.diagnosis.model;

import java.time.Instant;

/**
 * 诊断会话模型。记录一次 Arthas 诊断会话的完整生命周期。
 */
public record DiagnosisSession(
        Long id,
        String targetApp,
        String targetHost,
        int targetPort,
        Long operatorId,
        SessionStatus status,
        Instant createdAt,
        Instant closedAt,
        int timeoutMinutes
) {

    public enum SessionStatus {
        CREATED, ACTIVE, CLOSED, TIMEOUT
    }
}
