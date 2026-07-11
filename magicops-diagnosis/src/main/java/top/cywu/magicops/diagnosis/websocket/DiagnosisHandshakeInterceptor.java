package top.cywu.magicops.diagnosis.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 握手拦截器。在握手阶段进行身份认证并提取诊断会话 ID。
 *
 * <p>规则：
 * <ul>
 *   <li>未认证用户被拒绝连接</li>
 *   <li>从 URI 路径中提取 sessionId 并存入 WebSocket 属性</li>
 * </ul>
 */
public class DiagnosisHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisHandshakeInterceptor.class);

    static final String ATTR_SESSION_ID = "diagnosisSessionId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        // 1. 身份认证检查
        Principal principal = request.getPrincipal();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            // 也尝试从 HTTP session 获取（兼容 cookie 认证场景）
            if (request instanceof ServletServerHttpRequest servletRequest) {
                var httpSession = servletRequest.getServletRequest().getSession(false);
                if (httpSession == null) {
                    log.warn("ws_handshake_rejected reason=unauthenticated uri={}", request.getURI());
                    return false;
                }
            } else {
                log.warn("ws_handshake_rejected reason=unauthenticated uri={}", request.getURI());
                return false;
            }
        }

        // 2. 从 URI 路径提取 sessionId
        //    预期格式: /api/diagnosis/ws/sessions/{sessionId}
        Long sessionId = extractSessionId(request);
        if (sessionId == null) {
            log.warn("ws_handshake_rejected reason=invalid_session_id uri={}", request.getURI());
            return false;
        }

        attributes.put(ATTR_SESSION_ID, sessionId);
        log.info("ws_handshake_ok sessionId={} principal={}", sessionId,
                principal != null ? principal.getName() : "http-session");
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理
    }

    /**
     * 从 URI 路径末尾提取诊断会话 ID。
     * 路径格式：/api/diagnosis/ws/sessions/{sessionId}
     */
    private Long extractSessionId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        if (path == null) return null;

        // 取最后一段路径
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) return null;

        String segment = path.substring(lastSlash + 1);
        try {
            return Long.parseLong(segment);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
