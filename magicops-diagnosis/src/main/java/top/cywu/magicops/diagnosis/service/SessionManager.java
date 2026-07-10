package top.cywu.magicops.diagnosis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.diagnosis.model.DiagnosisSession;
import top.cywu.magicops.diagnosis.model.DiagnosisSession.SessionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 诊断会话管理器。管理会话生命周期、超时和并发限制。
 *
 * <p>规则：
 * <ul>
 *   <li>每个目标实例最多 1 个活跃会话</li>
 *   <li>会话超时后自动关闭（默认 30 分钟）</li>
 *   <li>会话结束后输出归档</li>
 * </ul>
 */
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    private final Map<Long, DiagnosisSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * 创建诊断会话。
     *
     * @throws IllegalStateException 如果目标实例已有活跃会话
     */
    public DiagnosisSession createSession(String targetApp, String targetHost, int targetPort,
                                          Long operatorId) {
        // 检查并发限制
        String targetKey = targetHost + ":" + targetPort;
        boolean hasActive = sessions.values().stream()
                .anyMatch(s -> (s.targetHost() + ":" + s.targetPort()).equals(targetKey)
                        && s.status() == SessionStatus.ACTIVE);
        if (hasActive) {
            throw new IllegalStateException("目标实例已有活跃诊断会话: " + targetKey);
        }

        long id = idGenerator.getAndIncrement();
        DiagnosisSession session = new DiagnosisSession(
                id, targetApp, targetHost, targetPort, operatorId,
                SessionStatus.ACTIVE, Instant.now(), null, DEFAULT_TIMEOUT_MINUTES);
        sessions.put(id, session);

        log.info("session_created id={} target={}:{} operator={}", id, targetHost, targetPort, operatorId);
        return session;
    }

    /**
     * 关闭会话。
     */
    public DiagnosisSession closeSession(Long sessionId) {
        DiagnosisSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在: " + sessionId);
        }
        if (session.status() != SessionStatus.ACTIVE) {
            throw new IllegalStateException("会话已关闭: " + sessionId);
        }

        DiagnosisSession closed = new DiagnosisSession(
                session.id(), session.targetApp(), session.targetHost(), session.targetPort(),
                session.operatorId(), SessionStatus.CLOSED, session.createdAt(), Instant.now(),
                session.timeoutMinutes());
        sessions.put(sessionId, closed);

        log.info("session_closed id={} durationMs={}", sessionId,
                Duration.between(session.createdAt(), Instant.now()).toMillis());
        return closed;
    }

    /**
     * 获取会话。
     */
    public Optional<DiagnosisSession> getSession(Long sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 获取所有会话。
     */
    public List<DiagnosisSession> getAllSessions() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * 清理超时会话。
     */
    public int cleanupExpiredSessions() {
        int count = 0;
        for (DiagnosisSession session : sessions.values()) {
            if (session.status() == SessionStatus.ACTIVE) {
                Duration elapsed = Duration.between(session.createdAt(), Instant.now());
                if (elapsed.toMinutes() >= session.timeoutMinutes()) {
                    DiagnosisSession timedOut = new DiagnosisSession(
                            session.id(), session.targetApp(), session.targetHost(), session.targetPort(),
                            session.operatorId(), SessionStatus.TIMEOUT, session.createdAt(), Instant.now(),
                            session.timeoutMinutes());
                    sessions.put(session.id(), timedOut);
                    count++;
                    log.info("session_timeout id={}", session.id());
                }
            }
        }
        return count;
    }
}
