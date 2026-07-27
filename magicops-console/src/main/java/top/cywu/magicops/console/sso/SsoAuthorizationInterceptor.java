package top.cywu.magicops.console.sso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.context.MagicUser;
import org.ssssssss.magicapi.core.exception.MagicLoginException;
import org.ssssssss.magicapi.core.interceptor.AuthorizationInterceptor;

/**
 * magic-api 编辑器 SSO 认证拦截器。
 *
 * <p>替换 magic-api 默认的 MD5 固定 Token 认证，改为信任 Spring Security 的
 * Session 认证。只要用户通过 Portal 统一登录（Session 有效），进入 magic-editor
 * 即自动通过认证，无需二次登录。
 *
 * <p>注册为 Bean 后，由于 MagicAPIAutoConfiguration 使用了 @ConditionalOnMissingBean，
 * 默认的 DefaultAuthorizationInterceptor 不会被创建。
 */
@Component
public class SsoAuthorizationInterceptor implements AuthorizationInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthorizationInterceptor.class);

    private final AuthenticationManager authenticationManager;

    public SsoAuthorizationInterceptor(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public boolean requireLogin() {
        return true;
    }

    /**
     * 根据 Token 获取用户。SSO 模式下优先检查 Spring SecurityContext，
     * 只要 Session 中有有效的 Authentication 即返回 MagicUser。
     */
    @Override
    public MagicUser getUserByToken(String token) throws MagicLoginException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            return new MagicUser(username, username, token != null ? token : "sso-session");
        }
        return null;
    }

    /**
     * magic-editor 内置登录表单的 fallback 认证。
     * 委托给 Spring AuthenticationManager（BCrypt + 数据库用户）。
     */
    @Override
    public MagicUser login(String username, String password) throws MagicLoginException {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.info("magic-editor SSO login: {}", username);
            return new MagicUser(username, username, "sso-" + System.currentTimeMillis());
        } catch (Exception e) {
            throw new MagicLoginException("用户名或密码错误");
        }
    }

    @Override
    public void logout(String token) {
        SecurityContextHolder.clearContext();
    }
}
