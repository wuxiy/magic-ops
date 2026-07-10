package top.cywu.magicops.console.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.sign.model.PublishPackage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * 发布包推送服务。Console 通过 REST 推送已签名发布包到 Runtime。
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 推送发布包到 Runtime。
     *
     * @param runtimeUrl Runtime 地址（如 http://localhost:8081）
     * @param packageJson 发布包的 JSON 序列化
     * @return 推送是否成功
     */
    public boolean push(String runtimeUrl, String packageJson) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(runtimeUrl + "/api/packages"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(packageJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
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
