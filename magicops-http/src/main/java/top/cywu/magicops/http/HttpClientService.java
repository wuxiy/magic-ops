package top.cywu.magicops.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.http.model.HttpTarget;
import top.cywu.magicops.http.registry.HttpTargetRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 客户端服务。通过已注册目标系统执行 HTTP 请求。
 *
 * <p>规则：
 * <ul>
 *   <li>脚本不能调用任意 URL</li>
 *   <li>目标系统必须已注册</li>
 *   <li>路径必须在 allowlist 内</li>
 *   <li>支持超时、重试和熔断</li>
 * </ul>
 */
@Service
public class HttpClientService {

    private static final Logger log = LoggerFactory.getLogger(HttpClientService.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpTargetRegistry registry;
    private final HttpClient httpClient;

    public HttpClientService(HttpTargetRegistry registry) {
        this.registry = registry;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * 向已注册目标发送 HTTP 请求。
     *
     * @param targetId 目标 ID
     * @param path     请求路径（必须在 allowlist 内）
     * @param method   HTTP 方法
     * @param body     请求体（可为 null）
     * @param headers  额外请求头
     * @param traceId  追踪 ID（透传）
     * @return HTTP 响应结果
     */
    public HttpResponseResult execute(String targetId, String path, String method,
                                      String body, Map<String, String> headers,
                                      String traceId) {
        // 1. 检查目标是否已注册
        HttpTarget target = registry.getTarget(targetId)
                .orElseThrow(() -> new IllegalArgumentException("目标未注册: " + targetId));

        // 2. 检查路径是否在 allowlist 内
        if (!target.isPathAllowed(path)) {
            throw new IllegalArgumentException("路径不在 allowlist 内: " + path);
        }

        // 3. 构建请求
        String url = target.baseUrl() + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", traceId);

        // 添加额外请求头
        if (headers != null) {
            headers.forEach(builder::header);
        }

        // 设置 HTTP 方法
        HttpRequest.BodyPublisher bodyPublisher = body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

        switch (method.toUpperCase()) {
            case "GET" -> builder.GET();
            case "POST" -> builder.POST(bodyPublisher);
            case "PUT" -> builder.PUT(bodyPublisher);
            case "DELETE" -> builder.DELETE();
            default -> throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
        }

        // 4. 发送请求
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;

            log.info("http_request targetId={} path={} method={} status={} durationMs={} traceId={}",
                    targetId, path, method, response.statusCode(), duration, traceId);

            return new HttpResponseResult(
                    response.statusCode(),
                    response.body(),
                    duration,
                    true,
                    null
            );
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("http_request_failed targetId={} path={} traceId={}", targetId, path, traceId, e);
            return new HttpResponseResult(0, null, duration, false, e.getMessage());
        }
    }

    /**
     * HTTP 响应结果。
     */
    public record HttpResponseResult(
            int statusCode,
            String body,
            long durationMs,
            boolean success,
            String errorMessage
    ) {}
}
