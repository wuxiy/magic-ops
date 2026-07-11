package top.cywu.magicops.diagnosis.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 诊断中心 WebSocket 配置。
 *
 * <p>注册 {@link DiagnosisWebSocketHandler} 到路径 /api/diagnosis/ws/sessions/**，
 * 并添加 {@link DiagnosisHandshakeInterceptor} 进行握手认证。
 */
@Configuration
@EnableWebSocket
public class DiagnosisWebSocketConfig implements WebSocketConfigurer {

    private final DiagnosisWebSocketHandler handler;

    public DiagnosisWebSocketConfig(DiagnosisWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/diagnosis/ws/sessions/**")
                .addInterceptors(new DiagnosisHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}
