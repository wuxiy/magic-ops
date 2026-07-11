package top.cywu.magicops.diagnosis.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.cywu.magicops.diagnosis.model.CommandTemplate;
import top.cywu.magicops.diagnosis.model.DiagnosisSession;
import top.cywu.magicops.diagnosis.service.CommandTemplateRegistry;
import top.cywu.magicops.diagnosis.service.OutputMaskingService;
import top.cywu.magicops.diagnosis.service.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 诊断中心 WebSocket 消息处理器。
 *
 * <p>客户端通过 WebSocket 连接后发送文本命令，服务端解析命令模板、模拟执行并返回输出。
 *
 * <p>消息协议（纯文本）：
 * <pre>
 *   客户端发送: &lt;templateName&gt; [key1=value1 key2=value2 ...]
 *   服务端返回: 执行结果文本（含脱敏处理）
 * </pre>
 *
 * <p>示例：
 * <pre>
 *   > thread-top10 count=5
 *   &lt; [thread-top10] Resolved: thread -n 5
 *   &lt; (模拟) Arthas 输出: ...
 * </pre>
 */
@Component
public class DiagnosisWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisWebSocketHandler.class);

    private final SessionManager sessionManager;
    private final CommandTemplateRegistry templateRegistry;
    private final OutputMaskingService maskingService;

    public DiagnosisWebSocketHandler(SessionManager sessionManager,
                                     CommandTemplateRegistry templateRegistry,
                                     OutputMaskingService maskingService) {
        this.sessionManager = sessionManager;
        this.templateRegistry = templateRegistry;
        this.maskingService = maskingService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long sessionId = getDiagnosisSessionId(session);
        if (sessionId == null) {
            log.warn("ws_connected_no_session_id wsSessionId={}", session.getId());
            session.sendMessage(new TextMessage("ERROR: 无法识别诊断会话 ID"));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        // 验证诊断会话存在且为 ACTIVE 状态
        var diagnosisSession = sessionManager.getSession(sessionId);
        if (diagnosisSession.isEmpty()) {
            log.warn("ws_connected_session_not_found diagnosisSessionId={}", sessionId);
            session.sendMessage(new TextMessage("ERROR: 诊断会话不存在: " + sessionId));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        DiagnosisSession ds = diagnosisSession.get();
        if (ds.status() != DiagnosisSession.SessionStatus.ACTIVE) {
            log.warn("ws_connected_session_inactive diagnosisSessionId={} status={}", sessionId, ds.status());
            session.sendMessage(new TextMessage("ERROR: 诊断会话已关闭 (状态: " + ds.status() + ")"));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        log.info("ws_connected wsSessionId={} diagnosisSessionId={} target={}:{}",
                session.getId(), sessionId, ds.targetHost(), ds.targetPort());
        session.sendMessage(new TextMessage("已连接到诊断会话 " + sessionId
                + " [" + ds.targetApp() + " @ " + ds.targetHost() + ":" + ds.targetPort() + "]"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) {
            session.sendMessage(new TextMessage("ERROR: 空命令"));
            return;
        }

        Long sessionId = getDiagnosisSessionId(session);
        log.debug("ws_message wsSessionId={} diagnosisSessionId={} payload={}", session.getId(), sessionId, payload);

        try {
            // 解析命令：第一段是模板名，后面是 key=value 参数
            ParsedCommand parsed = parseCommand(payload.trim());

            // 查找模板
            var templateOpt = templateRegistry.findByName(parsed.templateName);
            if (templateOpt.isEmpty()) {
                session.sendMessage(new TextMessage("ERROR: 未知命令模板: " + parsed.templateName));
                return;
            }

            CommandTemplate template = templateOpt.get();

            // 检查审批要求
            if (template.requiresApproval()) {
                session.sendMessage(new TextMessage("REJECTED: 命令 [" + template.name()
                        + "] 风险等级=" + template.riskLevel() + "，需要额外审批"));
                return;
            }

            // 解析命令参数
            String resolvedCommand = templateRegistry.resolve(template.id(), parsed.parameters);

            // 模拟 Arthas 执行输出
            String simulatedOutput = simulateExecution(resolvedCommand, template);

            // 脱敏处理
            String maskedOutput = maskingService.mask(simulatedOutput);

            // 组装响应
            StringBuilder response = new StringBuilder();
            response.append("[").append(template.name()).append("] ")
                    .append("Resolved: ").append(resolvedCommand).append("\n");
            response.append(maskedOutput);

            session.sendMessage(new TextMessage(response.toString()));

        } catch (IllegalArgumentException e) {
            session.sendMessage(new TextMessage("ERROR: " + e.getMessage()));
        } catch (Exception e) {
            log.error("ws_execution_error wsSessionId={}", session.getId(), e);
            session.sendMessage(new TextMessage("ERROR: 命令执行异常: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long sessionId = getDiagnosisSessionId(session);
        log.info("ws_disconnected wsSessionId={} diagnosisSessionId={} status={}",
                session.getId(), sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Long sessionId = getDiagnosisSessionId(session);
        log.error("ws_transport_error wsSessionId={} diagnosisSessionId={}", session.getId(), sessionId, exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // ---- 内部方法 ----

    private Long getDiagnosisSessionId(WebSocketSession session) {
        Object value = session.getAttributes().get(DiagnosisHandshakeInterceptor.ATTR_SESSION_ID);
        return value instanceof Long ? (Long) value : null;
    }

    /**
     * 解析客户端发送的文本命令。
     * 格式: templateName [key1=value1 key2=value2 ...]
     */
    ParsedCommand parseCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String templateName = parts[0];

        Map<String, String> parameters = new HashMap<>();
        if (parts.length > 1) {
            String[] pairs = parts[1].trim().split("\\s+");
            for (String pair : pairs) {
                int eq = pair.indexOf('=');
                if (eq > 0 && eq < pair.length() - 1) {
                    parameters.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }

        return new ParsedCommand(templateName, parameters);
    }

    /**
     * 模拟 Arthas 命令执行输出。
     * 实际场景中会通过 Tunnel Client 下发到目标 JVM 的 Arthas Agent。
     */
    private String simulateExecution(String resolvedCommand, CommandTemplate template) {
        return "(模拟) Arthas 命令已下发到目标 JVM\n"
                + "Command: " + resolvedCommand + "\n"
                + "Risk Level: " + template.riskLevel() + "\n"
                + "Status: EXECUTED\n"
                + "---\n"
                + "[模拟输出] thread 命令结果:\n"
                + "ID     NAME                           GROUP      PRIORITY  STATE\n"
                + "1      main                           main       5         RUNNABLE\n"
                + "2      Reference Handler              system     10        WAITING\n"
                + "3      Finalizer                      system     8         WAITING";
    }

    /**
     * 解析后的命令结构。
     */
    record ParsedCommand(String templateName, Map<String, String> parameters) {}
}
