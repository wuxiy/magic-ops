package top.cywu.magicops.console.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.cywu.magicops.console.entity.security.RoleEntity;
import top.cywu.magicops.console.entity.security.UserEntity;
import top.cywu.magicops.console.entity.security.UserRoleEntity;
import top.cywu.magicops.console.repository.security.RoleRepository;
import top.cywu.magicops.console.repository.security.UserRepository;
import top.cywu.magicops.console.repository.security.UserRoleRepository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务。处理用户 CRUD、密码加密和角色分配。
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 创建用户。密码使用 BCrypt 加密存储。
     */
    public UserEntity createUser(String username, String rawPassword,
                                 String displayName, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        UserEntity user = new UserEntity(username, passwordEncoder.encode(rawPassword), displayName);
        user.setEmail(email);
        user = userRepository.save(user);

        log.info("user_created username={} displayName={}", username, displayName);
        return user;
    }

    /**
     * 为用户分配角色。
     */
    public UserRoleEntity assignRole(Long userId, String roleName, String grantedBy) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        RoleEntity role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleName));

        if (userRoleRepository.existsByUserIdAndRoleId(userId, role.getId())) {
            throw new IllegalArgumentException("用户已拥有角色: " + roleName);
        }

        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRole.setGrantedAt(Instant.now());
        userRole.setGrantedBy(grantedBy);
        userRole = userRoleRepository.save(userRole);

        log.info("role_assigned userId={} role={} by={}", userId, roleName, grantedBy);
        return userRole;
    }

    /**
     * 获取用户的所有角色名称。
     */
    public List<String> getUserRoleNames(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.getRoleId())
                        .map(RoleEntity::getName)
                        .orElse("UNKNOWN"))
                .collect(Collectors.toList());
    }

    /**
     * 获取用户的所有角色实体。
     */
    public List<RoleEntity> getUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> roleRepository.findById(ur.getRoleId()).orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    /**
     * 查找用户。
     */
    public UserEntity findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
    }

    /**
     * 列出所有用户。
     */
    public List<UserEntity> findAll() {
        return userRepository.findAll();
    }

    /**
     * 修改密码。
     */
    public void changePassword(Long userId, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("password_changed userId={}", userId);
    }

    /**
     * 启用/禁用用户。
     */
    public void setEnabled(Long userId, boolean enabled) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        user.setEnabled(enabled);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("user_{} userId={}", enabled ? "enabled" : "disabled", userId);
    }
}
