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
        return postWithSecret(runtimeUrl + PushProtocol.PACKAGE_RECEIVE_PATH, packageJson, "package_push");
    }

    /**
     * 向 Runtime 推送下线指令（切片 38）。Runtime 收包端点受共享密钥保护，
     * 下线后 Runtime 清空激活包并拒绝脚本执行。
     *
     * @param runtimeUrl Runtime 地址（如 http://localhost:8081）
     * @param operator   操作人（Console 登录用户）
     * @param reason     下线原因
     * @return 下线是否成功
     */
    public boolean deactivate(String runtimeUrl, String operator, String reason) {
        try {
            String body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(java.util.Map.of(
                            "operator", operator != null ? operator : "unknown",
                            "reason", reason != null ? reason : "未提供"));
            return postWithSecret(runtimeUrl + PushProtocol.PACKAGE_DEACTIVATE_PATH, body, "package_deactivate");
        } catch (Exception e) {
            log.error("package_deactivate_serialize_failed runtime={}", runtimeUrl, e);
            return false;
        }
    }

    /**
     * 携带共享密钥向 Runtime 发送 POST 请求。
     *
     * @param fullPath 完整 URL（含路径）
     * @param body     请求体
     * @param logTag   日志标签
     * @return 2xx 响应视为成功
     */
    private boolean postWithSecret(String fullPath, String body, String logTag) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(fullPath))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (!pushSecret.isEmpty()) {
                builder.header(PushProtocol.PUSH_SECRET_HEADER, pushSecret);
            } else {
                log.warn("{}_without_secret 未配置推送共享密钥（NOT FOR PRODUCTION）", logTag);
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            log.info("{} status={} success={}", logTag, response.statusCode(), success);
            return success;
        } catch (Exception e) {
            log.error("{}_failed", logTag, e);
            return false;
        }
    }
}
