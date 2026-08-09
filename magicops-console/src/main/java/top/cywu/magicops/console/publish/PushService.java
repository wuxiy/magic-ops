package top.cywu.magicops.console.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.cywu.magicops.core.constants.PushProtocol;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * 发布包推送服务。Console 通过 REST 推送已签名发布包到 Runtime。
 *
 * <p>切片 32 起：配置了 {@code magicops.runtime.push-secret}（环境变量
 * {@code MAGICOPS_RUNTIME_SHARED_SECRET}）时，推送请求携带共享密钥头，
 * 由 Runtime 侧过滤器校验。
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String pushSecret;

    public PushService(@Value("${magicops.runtime.push-secret:}") String pushSecret) {
        this.pushSecret = pushSecret == null ? "" : pushSecret.trim();
    }

    /**
     * 推送发布包到 Runtime。
     *
     * @param runtimeUrl Runtime 地址（如 http://localhost:8081）
     * @param packageJson 发布包的 JSON 序列化
     * @return 推送是否成功
     */
    public boolean push(String runtimeUrl, String packageJson) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(runtimeUrl + PushProtocol.PACKAGE_RECEIVE_PATH))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(packageJson, StandardCharsets.UTF_8));
            if (!pushSecret.isEmpty()) {
                builder.header(PushProtocol.PUSH_SECRET_HEADER, pushSecret);
            } else {
                log.warn("package_push_without_secret 未配置推送共享密钥（NOT FOR PRODUCTION）");
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            log.info("package_push runtime={} status={} success={}", runtimeUrl, response.statusCode(), success);
            return success;
        } catch (Exception e) {
            log.error("package_push_failed runtime={}", runtimeUrl, e);
            return false;
        }
    }
}
