package top.cywu.magicops.console.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import top.cywu.magicops.console.entity.security.PermissionEntity;
import top.cywu.magicops.console.entity.security.RoleEntity;
import top.cywu.magicops.console.entity.security.UserEntity;
import top.cywu.magicops.console.repository.security.PermissionRepository;
import top.cywu.magicops.console.repository.security.RolePermissionRepository;
import top.cywu.magicops.console.repository.security.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetailsService 实现。
 * 从数据库加载用户、角色和权限，供 Spring Security 认证和授权使用。
 */
@Service
public class MagicOpsUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(MagicOpsUserDetailsService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public MagicOpsUserDetailsService(UserRepository userRepository,
                                      UserService userService,
                                      RolePermissionRepository rolePermissionRepository,
                                      PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("用户已禁用: " + username);
        }

        // 加载角色权限：ROLE_XXX + permission codes
        List<GrantedAuthority> authorities = new ArrayList<>();

        List<RoleEntity> roles = userService.getUserRoles(user.getId());
        for (RoleEntity role : roles) {
            // 添加 ROLE_ 前缀的角色名（Spring Security 约定）
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));

            // 添加角色关联的细粒度权限
            List<Long> permissionIds = rolePermissionRepository.findByRoleId(role.getId())
                    .stream().map(rp -> rp.getPermissionId()).collect(Collectors.toList());
            for (Long permId : permissionIds) {
                permissionRepository.findById(permId)
                        .ifPresent(perm -> authorities.add(
                                new SimpleGrantedAuthority(perm.getCode())));
            }
        }

        log.debug("user_loaded username={} roles={} authorities={}",
                username, roles.size(), authorities.size());

        return new User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled(),
                true,   // accountNonExpired
                true,   // credentialsNonExpired
                !user.isLocked(),  // accountNonLocked
                authorities
        );
    }
}
