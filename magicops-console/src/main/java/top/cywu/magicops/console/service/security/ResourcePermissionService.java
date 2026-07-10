package top.cywu.magicops.console.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import top.cywu.magicops.console.entity.security.ResourcePermissionEntity;
import top.cywu.magicops.console.entity.security.RoleEntity;
import top.cywu.magicops.console.entity.security.UserEntity;
import top.cywu.magicops.console.repository.security.ResourcePermissionRepository;
import top.cywu.magicops.console.repository.security.UserRepository;
import top.cywu.magicops.core.model.Permissions;

import java.util.List;

/**
 * 资源级权限服务。检查用户（及其角色）对特定资源的权限。
 *
 * <p>检查顺序：
 * <ol>
 *   <li>PLATFORM_ADMIN 角色直接放行</li>
 *   <li>检查用户直接拥有的资源权限</li>
 *   <li>检查用户角色拥有的资源权限</li>
 * </ol>
 */
@Service
public class ResourcePermissionService {

    private static final Logger log = LoggerFactory.getLogger(ResourcePermissionService.class);

    private final ResourcePermissionRepository resourcePermissionRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public ResourcePermissionService(ResourcePermissionRepository resourcePermissionRepository,
                                     UserRepository userRepository,
                                     UserService userService) {
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /**
     * 检查当前认证用户是否对指定资源拥有权限。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param permission   权限代码
     * @return true 表示有权限
     */
    public boolean hasPermission(String resourceType, String resourceId, String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        // PLATFORM_ADMIN 直接放行
        if (auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Permissions.ROLE_PLATFORM_ADMIN))) {
            return true;
        }

        String username = auth.getName();
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return false;
        }

        // 检查用户直接拥有的资源权限
        List<ResourcePermissionEntity> userPerms =
                resourcePermissionRepository.findByUserIdAndResourceType(user.getId(), resourceType);
        for (ResourcePermissionEntity perm : userPerms) {
            if (perm.getResourceId().equals(resourceId) && perm.getPermissionCode().equals(permission)) {
                return true;
            }
        }

        // 检查用户角色拥有的资源权限
        List<RoleEntity> roles = userService.getUserRoles(user.getId());
        for (RoleEntity role : roles) {
            List<ResourcePermissionEntity> rolePerms =
                    resourcePermissionRepository.findByRoleIdAndResourceType(role.getId(), resourceType);
            for (ResourcePermissionEntity perm : rolePerms) {
                if (perm.getResourceId().equals(resourceId) && perm.getPermissionCode().equals(permission)) {
                    return true;
                }
            }
        }

        log.debug("resource_permission_denied user={} type={} id={} permission={}",
                username, resourceType, resourceId, permission);
        return false;
    }
}
