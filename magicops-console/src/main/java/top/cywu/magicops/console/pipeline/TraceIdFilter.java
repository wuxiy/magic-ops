package top.cywu.magicops.console.pipeline;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Trace ID 过滤器（Console 侧，切片 35）。
 *
 * <p>为每个请求注入唯一 trace ID，透传到日志（MDC）与响应头。
 * 请求已携带 {@code X-Trace-Id} 头则沿用，否则生成新 UUID。
 * 与 Runtime 侧 {@code top.cywu.magicops.runtime.pipeline.TraceIdFilter} 行为一致。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String traceId = httpReq.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_TRACE_ID, traceId);
        httpResp.setHeader(TRACE_ID_HEADER, traceId);

        long startTime = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("request method={} uri={} status={} durationMs={} traceId={}",
                    httpReq.getMethod(), httpReq.getRequestURI(),
                    httpResp.getStatus(), duration, traceId);
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
