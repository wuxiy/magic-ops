package top.cywu.magicops.console.security.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import top.cywu.magicops.console.security.annotation.ResourcePermission;
import top.cywu.magicops.console.service.security.ResourcePermissionService;

import java.lang.reflect.Method;

/**
 * 资源级权限 AOP 切面。拦截 {@link ResourcePermission} 注解的方法，
 * 在执行前检查调用者对目标资源的权限。
 */
@Aspect
@Component
public class ResourcePermissionAspect {

    private final ResourcePermissionService permissionService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public ResourcePermissionAspect(ResourcePermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(resourcePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                  ResourcePermission resourcePermission) throws Throwable {
        String resourceType = resourcePermission.resourceType();
        String permission = resourcePermission.permission();

        // 使用 SpEL 解析资源 ID
        String resourceId = resolveResourceId(joinPoint, resourcePermission.resourceId());

        if (!permissionService.hasPermission(resourceType, resourceId, permission)) {
            throw new AccessDeniedException(
                    String.format("资源权限不足: type=%s, id=%s, permission=%s",
                            resourceType, resourceId, permission));
        }

        return joinPoint.proceed();
    }

    private String resolveResourceId(ProceedingJoinPoint joinPoint, String expression) {
        if (!expression.startsWith("#")) {
            return expression;
        }

        try {
            Method method = getMethod(joinPoint);
            String[] paramNames = nameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();

            EvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ((StandardEvaluationContext) context).setVariable(paramNames[i], args[i]);
                }
            }

            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : "null";
        } catch (Exception e) {
            return expression;
        }
    }

    private Method getMethod(ProceedingJoinPoint joinPoint) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Class<?> clazz = joinPoint.getTarget().getClass();
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
