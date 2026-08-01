package top.cywu.magicops.diagnosis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.cywu.magicops.diagnosis.config.TunnelProperties;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于 Spring {@link StandardWebSocketClient} 的 Arthas Tunnel 客户端实现。
 *
 * <p>连接到 Arthas Tunnel Server 的客户端接入点
 * （{@code <serverUrl>?method=connectArthas&agentId=...&clientName=...}），由 Tunnel Server
 * 桥接到目标 Agent，随后下发命令并以文本帧流式接收输出，逐帧回调 {@code outputConsumer}。
 *
 * <p>未启用（{@code enabled=false} 或未配置 {@code serverUrl}）或连接失败时，按
 * {@link TunnelProperties#isSimulateFallback()} 回退到模拟输出，保证开发/演示环境可用。
 *
 * <p><b>协议说明</b>：命令完成检测基于 Arthas 输出中的提示符标记（{@value #DEFAULT_COMPLETION_MARKER}）
 * 或连接关闭/超时。接入真实 Tunnel Server 时，若其采用结构化帧协议（CommandRequest/CommandResponse），
 * 需在此处按实际帧格式校准下发与完成判定逻辑。
 */
public class ArthasTunnelClient implements TunnelClient {

    private static final Logger log = LoggerFactory.getLogger(ArthasTunnelClient.class);

    /** Arthas 命令输出结束的提示符标记（用于检测命令完成）。 */
    private static final String DEFAULT_COMPLETION_MARKER = "[arthas@";

    private final TunnelProperties properties;
    private final StandardWebSocketClient webSocketClient;

    public ArthasTunnelClient(TunnelProperties properties) {
        this.properties = properties;
        this.webSocketClient = new StandardWebSocketClient();
    }

    @Override
    public boolean isRealTunnelEnabled() {
        return properties.isEnabled()
                && properties.getServerUrl() != null
                && !properties.getServerUrl().isBlank();
    }

    @Override
    public TunnelResult execute(String agentId, String command, Consumer<String> outputConsumer) {
        if (!isRealTunnelEnabled()) {
            return simulate(agentId, command, outputConsumer, "Tunnel 未配置");
        }
        try {
            return doExecute(agentId, command, outputConsumer);
        } catch (Exception e) {
            log.error("tunnel_execute_failed agentId={} command={}", agentId, command, e);
            if (properties.isSimulateFallback()) {
                return simulate(agentId, command, outputConsumer, "Tunnel 连接失败: " + e.getMessage());
            }
            return TunnelResult.ofFailure("Tunnel 连接失败: " + e.getMessage());
        }
    }

    private TunnelResult doExecute(String agentId, String command, Consumer<String> outputConsumer) throws Exception {
        URI uri = buildTunnelUri(agentId);
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.info("tunnel_connected agentId={}", agentId);
                session.sendMessage(new TextMessage(command));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                String payload = message.getPayload();
                outputConsumer.accept(payload);
                if (isCommandComplete(payload)) {
                    completion.countDown();
                    closeQuietly(session);
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) {
                error.set(exception);
                completion.countDown();
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                completion.countDown();
            }
        };

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (properties.getAuthToken() != null && !properties.getAuthToken().isBlank()) {
            headers.add("Authorization", "Bearer " + properties.getAuthToken());
        }

        WebSocketSession session = webSocketClient.execute(handler, headers, uri)
                .get(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);

        try {
            boolean finished = completion.await(properties.getCommandTimeoutMs(), TimeUnit.MILLISECONDS);
            if (error.get() != null) {
                throw new IllegalStateException("Tunnel 传输错误: " + error.get().getMessage(), error.get());
            }
            if (!finished) {
                outputConsumer.accept("\n[命令执行超时 " + properties.getCommandTimeoutMs() + "ms，已返回已接收输出]");
            }
            return TunnelResult.ofReal();
        } finally {
            closeQuietly(session);
        }
    }

    private URI buildTunnelUri(String agentId) {
        String base = properties.getServerUrl();
        String separator = base.contains("?") ? "&" : "?";
        String url = base + separator
                + "method=connectArthas"
                + "&agentId=" + URLEncoder.encode(agentId, StandardCharsets.UTF_8)
                + "&clientName=" + URLEncoder.encode(properties.getClientName(), StandardCharsets.UTF_8);
        return URI.create(url);
    }

    private boolean isCommandComplete(String payload) {
        return payload != null && payload.contains(DEFAULT_COMPLETION_MARKER);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
            // 关闭失败不影响主流程
        }
    }

    private TunnelResult simulate(String agentId, String command, Consumer<String> outputConsumer, String reason) {
        log.info("tunnel_simulated agentId={} command={} reason={}", agentId, command, reason);
        outputConsumer.accept("(模拟降级: " + reason + ")\n");
        outputConsumer.accept("Arthas 命令已模拟下发到目标 Agent [" + agentId + "]\n");
        outputConsumer.accept("Command: " + command + "\n");
        outputConsumer.accept("---\n");
        outputConsumer.accept("[模拟输出] thread 命令结果:\n");
        outputConsumer.accept("ID     NAME                           GROUP      PRIORITY  STATE\n");
        outputConsumer.accept("1      main                           main       5         RUNNABLE\n");
        outputConsumer.accept("2      Reference Handler              system     10        WAITING\n");
        outputConsumer.accept("3      Finalizer                      system     8         WAITING\n");
        return TunnelResult.ofSimulated();
    }
}
