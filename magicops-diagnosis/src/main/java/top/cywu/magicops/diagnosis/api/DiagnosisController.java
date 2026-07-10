package top.cywu.magicops.diagnosis.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.diagnosis.model.CommandTemplate;
import top.cywu.magicops.diagnosis.model.CommandTemplate.RiskLevel;
import top.cywu.magicops.diagnosis.model.DiagnosisSession;
import top.cywu.magicops.diagnosis.service.CommandTemplateRegistry;
import top.cywu.magicops.diagnosis.service.SessionManager;

import java.util.List;
import java.util.Map;

/**
 * 诊断中心 REST API。
 */
@RestController
@RequestMapping("/api/diagnosis")
public class DiagnosisController {

    private final SessionManager sessionManager;
    private final CommandTemplateRegistry templateRegistry;

    public DiagnosisController(SessionManager sessionManager,
                               CommandTemplateRegistry templateRegistry) {
        this.sessionManager = sessionManager;
        this.templateRegistry = templateRegistry;
    }

    // ---- 会话管理 ----

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('diagnosis:session:create')")
    public ResponseEntity<DiagnosisSession> createSession(@RequestBody Map<String, Object> request) {
        String targetApp = (String) request.get("targetApp");
        String targetHost = (String) request.get("targetHost");
        int targetPort = ((Number) request.getOrDefault("targetPort", 8080)).intValue();
        Long operatorId = request.get("operatorId") != null
                ? ((Number) request.get("operatorId")).longValue() : null;

        DiagnosisSession session = sessionManager.createSession(targetApp, targetHost, targetPort, operatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('diagnosis:session:create')")
    public ResponseEntity<List<DiagnosisSession>> listSessions() {
        return ResponseEntity.ok(sessionManager.getAllSessions());
    }

    @PostMapping("/sessions/{id}/close")
    @PreAuthorize("hasAuthority('diagnosis:session:create')")
    public ResponseEntity<DiagnosisSession> closeSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionManager.closeSession(id));
    }

    // ---- 命令模板 ----

    @GetMapping("/templates")
    public ResponseEntity<List<CommandTemplate>> listTemplates() {
        return ResponseEntity.ok(templateRegistry.getAll());
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('diagnosis:template:manage')")
    public ResponseEntity<CommandTemplate> registerTemplate(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String command = (String) request.get("command");
        String constraints = (String) request.get("parameterConstraints");
        RiskLevel riskLevel = RiskLevel.valueOf((String) request.getOrDefault("riskLevel", "LOW"));
        boolean requiresApproval = Boolean.TRUE.equals(request.get("requiresApproval"));
        String description = (String) request.get("description");

        CommandTemplate template = templateRegistry.register(name, command, constraints,
                riskLevel, requiresApproval, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }

    // ---- 命令执行 ----

    @PostMapping("/sessions/{sessionId}/execute")
    @PreAuthorize("hasAuthority('diagnosis:session:create')")
    public ResponseEntity<Map<String, Object>> executeCommand(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> request) {
        Long templateId = ((Number) request.get("templateId")).longValue();
        @SuppressWarnings("unchecked")
        Map<String, String> parameters = (Map<String, String>) request.get("parameters");

        // 验证会话存在且活跃
        DiagnosisSession session = sessionManager.getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (session.status() != DiagnosisSession.SessionStatus.ACTIVE) {
            return ResponseEntity.badRequest().body(Map.of("error", "会话已关闭"));
        }

        // 解析命令
        String resolvedCommand = templateRegistry.resolve(templateId, parameters);

        // 检查是否需要审批
        CommandTemplate template = templateRegistry.getTemplate(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在"));
        if (template.requiresApproval()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "此命令需要额外审批",
                    "command", resolvedCommand,
                    "riskLevel", template.riskLevel().name()));
        }

        // 模拟执行（实际会通过 Tunnel Client 发送到 Arthas Agent）
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "templateId", templateId,
                "resolvedCommand", resolvedCommand,
                "status", "EXECUTED",
                "output", "（模拟输出）Arthas 命令已下发"));
    }
}
