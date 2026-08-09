package top.cywu.magicops.console.publish;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.core.constants.PushProtocol;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PushService 推送认证测试（切片 32）。使用 JDK 内置 HttpServer 捕获请求头，不引入额外依赖。
 */
class PushServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void pushWithSecret_sendsSharedSecretHeader() throws Exception {
        AtomicReference<String> capturedHeader = new AtomicReference<>();
        String url = startServer(capturedHeader, 200);

        PushService pushService = new PushService("push-secret-123");
        boolean result = pushService.push(url, "{\"manifest\":{}}");

        assertTrue(result, "200 响应应视为推送成功");
        assertEquals("push-secret-123", capturedHeader.get(), "应携带共享密钥头");
    }

    @Test
    void pushWithoutSecret_omitsHeader() throws Exception {
        AtomicReference<String> capturedHeader = new AtomicReference<>("sentinel");
        String url = startServer(capturedHeader, 200);

        PushService pushService = new PushService("");
        boolean result = pushService.push(url, "{\"manifest\":{}}");

        assertTrue(result);
        assertNull(capturedHeader.get(), "未配置密钥时不应携带认证头");
    }

    @Test
    void pushRejectedWith401_reportedAsFailure() throws Exception {
        String url = startServer(new AtomicReference<>(), 401);

        PushService pushService = new PushService("wrong-secret");
        boolean result = pushService.push(url, "{\"manifest\":{}}");

        assertFalse(result, "Runtime 以 401 拒绝时应报告推送失败");
    }

    private String startServer(AtomicReference<String> headerSink, int responseStatus) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PushProtocol.PACKAGE_RECEIVE_PATH, exchange -> {
            headerSink.set(exchange.getRequestHeaders().getFirst(PushProtocol.PUSH_SECRET_HEADER));
            byte[] body = "{\"status\":\"accepted\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
