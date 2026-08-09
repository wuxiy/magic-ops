package top.cywu.magicops.runtime.packageMgmt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import top.cywu.magicops.core.constants.PushProtocol;

import java.io.IOException;

/**
 * 发布包推送共享密钥认证过滤器（切片 32）。
 *
 * <p>基于 Spring Security 过滤器链的标准扩展点（{@link OncePerRequestFilter}），
 * 仅作用于 {@code POST /api/packages} 收包端点：
 *
 * <ul>
 *   <li>Runtime 配置了 {@code magicops.runtime.push-secret} 时，请求必须携带匹配的
 *       {@code X-MagicOps-Push-Secret} 头，否则返回 401；</li>
 *   <li>未配置共享密钥且为 prod profile 时 fail-closed，一律 401；</li>
 *   <li>未配置共享密钥且为非 prod 环境时放行并告警（开发/测试便利）。</li>
 * </ul>
 */
public class PushSecretAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PushSecretAuthenticationFilter.class);

    private final String expectedSecret;
    private final boolean prodProfile;

    public PushSecretAuthenticationFilter(String expectedSecret, boolean prodProfile) {
        this.expectedSecret = expectedSecret == null ? "" : expectedSecret.trim();
        this.prodProfile = prodProfile;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isPushReceiveRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!expectedSecret.isEmpty()) {
            String provided = request.getHeader(PushProtocol.PUSH_SECRET_HEADER);
            if (expectedSecret.equals(provided == null ? "" : provided.trim())) {
                filterChain.doFilter(request, response);
                return;
            }
            log.warn("package_push_rejected reason=shared_secret_mismatch remote={}", request.getRemoteAddr());
            sendUnauthorized(response, "共享密钥缺失或不匹配");
            return;
        }

        if (prodProfile) {
            log.error("package_push_rejected reason=push_secret_not_configured profile=prod");
            sendUnauthorized(response, "生产环境未配置推送共享密钥（MAGICOPS_RUNTIME_SHARED_SECRET）");
            return;
        }

        log.warn("push_endpoint_unauthenticated 未配置推送共享密钥，非生产环境放行（NOT FOR PRODUCTION）");
        filterChain.doFilter(request, response);
    }

    private boolean isPushReceiveRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && PushProtocol.PACKAGE_RECEIVE_PATH.equals(request.getRequestURI());
    }

    private void sendUnauthorized(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":\"unauthorized\",\"reason\":\"" + reason + "\"}");
    }
}
