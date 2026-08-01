package top.cywu.magicops.diagnosis.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.diagnosis.model.CommandTemplate;
import top.cywu.magicops.diagnosis.model.CommandTemplate.RiskLevel;
import top.cywu.magicops.diagnosis.config.TunnelProperties;
import top.cywu.magicops.diagnosis.service.ArthasTunnelClient;
import top.cywu.magicops.diagnosis.service.CommandTemplateRegistry;
import top.cywu.magicops.diagnosis.service.OutputMaskingService;
import top.cywu.magicops.diagnosis.service.SessionManager;
import top.cywu.magicops.diagnosis.service.TunnelClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 处理器单元测试。测试命令解析、模板解析和输出脱敏管道。
 */
class DiagnosisWebSocketTest {

    private DiagnosisWebSocketHandler handler;
    private SessionManager sessionManager;
    private CommandTemplateRegistry templateRegistry;
    private OutputMaskingService maskingService;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        templateRegistry = new CommandTemplateRegistry();
        maskingService = new OutputMaskingService();
        TunnelClient tunnelClient = new ArthasTunnelClient(new TunnelProperties());
        handler = new DiagnosisWebSocketHandler(sessionManager, templateRegistry, maskingService, tunnelClient);

        // 注册测试模板
        templateRegistry.register("dashboard", "dashboard", null, RiskLevel.LOW, false, "面板");
        templateRegistry.register("thread-top", "thread -n {count}", "\\d+", RiskLevel.LOW, false, "线程");
        templateRegistry.register("retransform", "retransform {path}", null, RiskLevel.CRITICAL, true, "重加载");
    }

    @Test
    void parseCommand_simple() {
        var parsed = handler.parseCommand("dashboard");
        assertEquals("dashboard", parsed.templateName());
        assertTrue(parsed.parameters().isEmpty());
    }

    @Test
    void parseCommand_withParameters() {
        var parsed = handler.parseCommand("thread-top count=5");
        assertEquals("thread-top", parsed.templateName());
        assertEquals("5", parsed.parameters().get("count"));
    }

    @Test
    void parseCommand_multipleParameters() {
        var parsed = handler.parseCommand("trace-method class=com.example.Service method=doWork");
        assertEquals("trace-method", parsed.templateName());
        assertEquals("com.example.Service", parsed.parameters().get("class"));
        assertEquals("doWork", parsed.parameters().get("method"));
    }

    @Test
    void templateLookup_known() {
        var result = templateRegistry.findByName("dashboard");
        assertTrue(result.isPresent());
        assertEquals("dashboard", result.get().name());
    }

    @Test
    void templateLookup_unknown() {
        var result = templateRegistry.findByName("nonexistent");
        assertFalse(result.isPresent());
    }

    @Test
    void templateResolve_withValidParameters() {
        var template = templateRegistry.findByName("thread-top").orElseThrow();
        String resolved = templateRegistry.resolve(template.id(), Map.of("count", "5"));
        assertEquals("thread -n 5", resolved);
    }

    @Test
    void templateResolve_constraintViolation() {
        var template = templateRegistry.findByName("thread-top").orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> templateRegistry.resolve(template.id(), Map.of("count", "not-a-number")));
    }

    @Test
    void requiresApproval_criticalTemplate() {
        var template = templateRegistry.findByName("retransform").orElseThrow();
        assertTrue(template.requiresApproval());
        assertEquals(RiskLevel.CRITICAL, template.riskLevel());
    }

    @Test
    void requiresApproval_lowTemplate() {
        var template = templateRegistry.findByName("dashboard").orElseThrow();
        assertFalse(template.requiresApproval());
    }

    @Test
    void outputMasking_passwordMasked() {
        String output = "Connection: password=secret123&user=admin";
        String masked = maskingService.mask(output);
        assertFalse(masked.contains("secret123"));
    }

    @Test
    void outputMasking_phoneMasked() {
        String output = "Patient phone: 13812345678";
        String masked = maskingService.mask(output);
        assertTrue(masked.contains("138****5678"));
    }

    @Test
    void sessionManager_createAndRetrieve() {
        var session = sessionManager.createSession("app1", "10.0.0.1", 8080, "agent-1", 1L);
        assertTrue(sessionManager.getSession(session.id()).isPresent());
    }

    @Test
    void sessionManager_duplicateTargetRejected() {
        sessionManager.createSession("app1", "10.0.0.1", 8080, "agent-1", 1L);
        assertThrows(IllegalStateException.class,
                () -> sessionManager.createSession("app2", "10.0.0.1", 8080, "agent-2", 2L));
    }
}
