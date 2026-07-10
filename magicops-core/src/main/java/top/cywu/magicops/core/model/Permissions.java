package top.cywu.magicops.core.model;

/**
 * 权限常量。集中管理所有权限代码，与数据库 permissions 表的 code 字段保持一致。
 *
 * <p>在 {@code @PreAuthorize} 注解中引用这些常量。
 */
public final class Permissions {

    private Permissions() {}

    // ---- 脚本 ----
    public static final String SCRIPT_CREATE = "script:create";
    public static final String SCRIPT_EDIT = "script:edit";
    public static final String SCRIPT_DEBUG = "script:debug";
    public static final String SCRIPT_SUBMIT = "script:submit";
    public static final String SCRIPT_APPROVE = "script:approve";
    public static final String SCRIPT_PUBLISH = "script:publish";
    public static final String SCRIPT_ROLLBACK = "script:rollback";

    // ---- 审计 ----
    public static final String AUDIT_READ = "audit:read";
    public static final String AUDIT_EXPORT = "audit:export";

    // ---- 数据源 ----
    public static final String DATASOURCE_MANAGE = "datasource:manage";
    public static final String DATASOURCE_QUERY = "datasource:query";

    // ---- HTTP 目标 ----
    public static final String HTTP_TARGET_MANAGE = "http_target:manage";

    // ---- 密钥 ----
    public static final String KEY_MANAGE = "key:manage";

    // ---- 项目 ----
    public static final String PROJECT_MANAGE = "project:manage";

    // ---- 管理 ----
    public static final String USER_MANAGE = "user:manage";
    public static final String ROLE_MANAGE = "role:manage";
    public static final String SYSTEM_CONFIG = "system:config";

    // ---- 角色名（配合 ROLE_ 前缀使用）----
    public static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";
    public static final String ROLE_PROJECT_ADMIN = "ROLE_PROJECT_ADMIN";
    public static final String ROLE_DEVELOPER = "ROLE_DEVELOPER";
    public static final String ROLE_APPROVER = "ROLE_APPROVER";
    public static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    public static final String ROLE_AUDITOR = "ROLE_AUDITOR";
    public static final String ROLE_OBSERVER = "ROLE_OBSERVER";
}
