package top.cywu.magicops.console.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 资源级权限注解。标注在 Controller 方法上，要求调用者对指定资源拥有权限。
 *
 * <p>使用 SpEL 表达式从方法参数中提取资源 ID。
 *
 * <p>示例：
 * <pre>
 * {@code @ResourcePermission(resourceType = "project", resourceId = "#projectId", permission = "script:create")}
 * public ResponseEntity<?> createScript(@PathVariable Long projectId, ...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourcePermission {

    /** 资源类型（project / datasource / http_target / script）。 */
    String resourceType();

    /** SpEL 表达式，从方法参数中提取资源 ID。 */
    String resourceId();

    /** 所需权限代码（如 script:create）。 */
    String permission();
}
