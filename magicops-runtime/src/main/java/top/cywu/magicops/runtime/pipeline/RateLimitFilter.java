package top.cywu.magicops.runtime.pipeline;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流过滤器。基于令牌桶算法，按客户端 IP 限制请求频率。
 *
 * <p>默认限制：每分钟 60 次请求/IP。超出时返回 429 Too Many Requests。
 */
@Component
@Order(2)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** 每 IP 每分钟最大请求数。 */
    private static final int MAX_REQUESTS_PER_MINUTE = 60;

    /** 窗口重置时间（毫秒）。 */
    private static final long WINDOW_MS = 60_000L;

    private final Map<String, ClientBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String clientIp = request.getRemoteAddr();
        ClientBucket bucket = buckets.compute(clientIp, (key, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                return new ClientBucket(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (bucket.count.get() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("rate_limit_exceeded ip={} count={}", clientIp, bucket.count.get());
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
