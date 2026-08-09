package top.cywu.magicops.console.pipeline;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流过滤器（Console 侧，切片 35）。
 *
 * <p>基于固定窗口计数，按客户端 IP 限制请求频率：每分钟 60 次/IP，超出返回 429。
 * 与 Runtime 侧 {@code top.cywu.magicops.runtime.pipeline.RateLimitFilter} 行为一致。
 * 单实例限流；多实例部署后应迁移到 Redis 共享计数。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, ClientBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String clientIp = request.getRemoteAddr();
        ClientBucket bucket = buckets.compute(clientIp, (key, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.windowStart() > WINDOW_MS) {
                return new ClientBucket(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (bucket.count().get() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("rate_limit_exceeded ip={} count={}", clientIp, bucket.count().get());
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.setStatus(429);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private record ClientBucket(long windowStart, AtomicInteger count) {}
}
