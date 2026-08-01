package top.cywu.magicops.diagnosis.model;

import java.time.Instant;

/**
 * 诊断会话模型。记录一次 Arthas 诊断会话的完整生命周期。
 *
 * <p>{@code agentId} 为目标 JVM 上 Arthas Agent 在 Tunnel Server 注册的标识，
 * TunnelClient 据此经 Tunnel Server 路由到目标 Agent。
 */
public record DiagnosisSession(
        Long id,
        String targetApp,
        String targetHost,
        int targetPort,
        String agentId,
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
