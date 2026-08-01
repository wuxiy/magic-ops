package top.cywu.magicops.diagnosis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.diagnosis.model.CommandTemplate;
import top.cywu.magicops.diagnosis.model.CommandTemplate.RiskLevel;
import top.cywu.magicops.diagnosis.model.DiagnosisSession;
import top.cywu.magicops.diagnosis.service.CommandTemplateRegistry;
import top.cywu.magicops.diagnosis.service.OutputMaskingService;
import top.cywu.magicops.diagnosis.service.SessionManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 诊断中心核心测试。覆盖会话管理、命令模板和输出脱敏。
 */
class DiagnosisServiceTest {

    private SessionManager sessionManager;
    private CommandTemplateRegistry templateRegistry;
    private OutputMaskingService maskingService;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        templateRegistry = new CommandTemplateRegistry();
        maskingService = new OutputMaskingService();
    }

    // ---- 会话管理测试 ----

    @Test
    void createSession_succeeds() {
        DiagnosisSession session = sessionManager.createSession("my-app", "10.0.0.1", 8080, "agent-1", 1L);
        assertNotNull(session);
        assertEquals(DiagnosisSession.SessionStatus.ACTIVE, session.status());
        assertEquals("my-app", session.targetApp());
        assertEquals(30, session.timeoutMinutes());
    }

    @Test
    void createSession_duplicateTarget_rejected() {
        sessionManager.createSession("app1", "10.0.0.1", 8080, "agent-1", 1L);
        assertThrows(IllegalStateException.class,
                () -> sessionManager.createSession("app2", "10.0.0.1", 8080, "agent-2", 2L));
    }

    @Test
    void closeSession_succeeds() {
        DiagnosisSession session = sessionManager.createSession("app1", "10.0.0.1", 8080, "agent-1", 1L);
        DiagnosisSession closed = sessionManager.closeSession(session.id());
        assertEquals(DiagnosisSession.SessionStatus.CLOSED, closed.status());
        assertNotNull(closed.closedAt());
    }

    @Test
    void closeSession_alreadyClosed_throws() {
        DiagnosisSession session = sessionManager.createSession("app1", "10.0.0.1", 8080, "agent-1", 1L);
        sessionManager.closeSession(session.id());
        assertThrows(IllegalStateException.class,
                () -> sessionManager.closeSession(session.id()));
    }

    @Test
    void closeSession_allowsNewSession_sameTarget() {
        DiagnosisSession session = sessionManager.createSession("app1", "10.0.0.1", 8080, "agent-1", 1L);
        sessionManager.closeSession(session.id());
        // 关闭后应允许在同一目标创建新会话
        DiagnosisSession newSession = sessionManager.createSession("app1", "10.0.0.1", 8080, "agent-1", 1L);
        assertNotNull(newSession);
    }

    // ---- 命令模板测试 ----

    @Test
    void registerTemplate_succeeds() {
        CommandTemplate template = templateRegistry.register(
                "thread-top10", "thread -n {count}", "\\d+",
                RiskLevel.LOW, false, "查看最忙线程");
        assertNotNull(template);
        assertEquals("thread-top10", template.name());
        assertEquals(RiskLevel.LOW, template.riskLevel());
    }

    @Test
    void resolveCommand_withParameters() {
        CommandTemplate template = templateRegistry.register(
                "trace-method", "trace {class} {method}", null,
                RiskLevel.MEDIUM, false, "方法追踪");

        String resolved = templateRegistry.resolve(template.id(),
                Map.of("class", "com.example.Service", "method", "doWork"));
        assertEquals("trace com.example.Service doWork", resolved);
    }

    @Test
    void resolveCommand_constraintViolation_rejected() {
        CommandTemplate template = templateRegistry.register(
                "thread-topN", "thread -n {count}", "\\d+",
                RiskLevel.LOW, false, "查看线程");

        assertThrows(IllegalArgumentException.class,
                () -> templateRegistry.resolve(template.id(),
                        Map.of("count", "not-a-number")));
    }

    @Test
    void resolveCommand_unknownTemplate_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> templateRegistry.resolve(999L, Map.of()));
    }

    @Test
    void requiresApproval_flagged() {
        CommandTemplate template = templateRegistry.register(
                "retransform", "retransform {path}", null,
                RiskLevel.CRITICAL, true, "重加载类");
        assertTrue(template.requiresApproval());
    }

    // ---- 输出脱敏测试 ----

    @Test
    void mask_passwordInOutput() {
        String output = "Connection: jdbc:postgresql://localhost/db?user=admin&password=secret123";
        String masked = maskingService.mask(output);
        assertFalse(masked.contains("secret123"));
        assertTrue(masked.contains("password=***"));
    }

    @Test
    void mask_tokenInOutput() {
        String output = "Authorization: token=abc123xyz789";
        String masked = maskingService.mask(output);
        assertFalse(masked.contains("abc123xyz789"));
    }

    @Test
    void mask_phoneNumber() {
        String output = "Patient phone: 13812345678";
        String masked = maskingService.mask(output);
        assertTrue(masked.contains("138****5678"));
    }

    @Test
    void mask_idCard() {
        String output = "ID: 110101199001011234";
        String masked = maskingService.mask(output);
        assertTrue(masked.contains("110101********1234"));
    }

    @Test
    void mask_nullOrBlank() {
        assertNull(maskingService.mask(null));
        assertEquals("", maskingService.mask(""));
    }
}
