package top.cywu.magicops.console.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import top.cywu.magicops.console.entity.security.UserEntity;
import top.cywu.magicops.console.service.security.MagicOpsUserDetailsService;
import top.cywu.magicops.console.service.security.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证与用户管理测试。验证数据库用户认证、角色分配和 UserDetailsService 集成。
 */
@SpringBootTest
class AuthenticationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MagicOpsUserDetailsService userDetailsService;

    @Test
    void defaultAdminUser_exists() {
        UserEntity admin = userService.findByUsername("admin");
        assertNotNull(admin);
        assertTrue(admin.isEnabled());

        List<String> roles = userService.getUserRoleNames(admin.getId());
        assertTrue(roles.contains("PLATFORM_ADMIN"), "admin 应拥有 PLATFORM_ADMIN 角色");
    }

    @Test
    void defaultDeveloperUser_exists() {
        UserEntity dev = userService.findByUsername("developer");
        assertNotNull(dev);

        List<String> roles = userService.getUserRoleNames(dev.getId());
        assertTrue(roles.contains("DEVELOPER"), "developer 应拥有 DEVELOPER 角色");
    }

    @Test
    void createUser_withBcryptPassword() {
        UserEntity user = userService.createUser(
                "test-user-auth", "securePass123", "Test User", "test@auth.local");
        assertNotNull(user.getId());
        // 密码应被 BCrypt 加密（以 $2a$ 开头）
        assertTrue(user.getPasswordHash().startsWith("$2a$"),
                "密码应使用 BCrypt 加密");
        assertNotEquals("securePass123", user.getPasswordHash(),
                "存储的密码不应是明文");
    }

    @Test
    void userDetailsService_loadsUser() {
        UserDetails details = userDetailsService.loadUserByUsername("admin");
        assertNotNull(details);
        assertEquals("admin", details.getUsername());
        assertTrue(details.isEnabled());

        // admin 应有 ROLE_PLATFORM_ADMIN 和相关权限
        var authorities = details.getAuthorities();
        assertTrue(authorities.stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN")),
                "应包含 ROLE_PLATFORM_ADMIN");
    }

    @Test
    void userDetailsService_rejectsUnknownUser() {
        assertThrows(
                org.springframework.security.core.userdetails.UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("nonexistent-user")
        );
    }

    @Test
    void assignRole_duplicateRejected() {
        UserEntity user = userService.createUser(
                "test-dup-role", "pass123", "Dup Role Test", "dup@test.local");
        userService.assignRole(user.getId(), "DEVELOPER", "system");

        assertThrows(IllegalArgumentException.class,
                () -> userService.assignRole(user.getId(), "DEVELOPER", "system"),
                "重复分配角色应被拒绝");
    }

    @Test
    void createUser_duplicateUsernameRejected() {
        userService.createUser("unique-user-001", "pass", "User 1", "u1@test.local");

        assertThrows(IllegalArgumentException.class,
                () -> userService.createUser("unique-user-001", "pass2", "User 2", "u2@test.local"),
                "重复用户名应被拒绝");
    }

    @Test
    void changePassword_updatesHash() {
        UserEntity user = userService.createUser(
                "test-chg-pass", "oldPass", "Change Pass Test", "chg@test.local");
        String oldHash = user.getPasswordHash();

        userService.changePassword(user.getId(), "newPass");
        UserEntity updated = userService.findByUsername("test-chg-pass");

        assertNotEquals(oldHash, updated.getPasswordHash(),
                "密码变更后 hash 应不同");
    }
}
