package top.cywu.magicops.console.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前操作人上下文。从 Spring Security 上下文读取已认证用户名。
 *
 * <p>切片 31 起，所有写入审计的操作人（actor）必须来自登录身份，
 * 禁止在 Controller/Service 中硬编码 {@code "system"/"reviewer"} 等占位符。
 * 未认证或匿名场景回退为 {@code "system"}（仅限内部调用路径，
 * API 请求在安全链中已被强制认证）。
 */
public final class CurrentActor {

    /** 未认证场景的回退标识。 */
    public static final String SYSTEM = "system";

    private CurrentActor() {
    }

    /**
     * 返回当前已认证用户名；匿名或未认证时返回 {@link #SYSTEM}。
     */
    public static String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return SYSTEM;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return SYSTEM;
        }
        return name;
    }
}
