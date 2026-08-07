package top.cywu.magicops.console.init;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import top.cywu.magicops.console.entity.security.UserEntity;
import top.cywu.magicops.console.repository.config.ProjectRepository;
import top.cywu.magicops.console.repository.security.UserRepository;
import top.cywu.magicops.console.service.security.UserService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 切片 31：DataInitializer profile 约束测试。
 *
 * <p>覆盖关闭标准：prod profile 下不产生 developer/approver 测试账号；
 * prod 未显式提供管理员密码时不初始化任何用户。
 */
class DataInitializerTest {

    private UserService userService;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private Environment environment;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        environment = mock(Environment.class);
        when(projectRepository.existsByCode("default")).thenReturn(true);
        when(userRepository.count()).thenReturn(0L);
    }

    private UserEntity stubUser(String username) {
        UserEntity u = new UserEntity();
        u.setId((long) (100 + username.length()));
        when(userService.createUser(eq(username), anyString(), anyString(), anyString()))
                .thenReturn(u);
        return u;
    }

    @Test
    void devProfile_createsAdminDeveloperApprover() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        stubUser("admin");
        stubUser("developer");
        stubUser("approver");

        new DataInitializer(userService, userRepository, projectRepository, environment, "")
                .run(null);

        verify(userService).createUser(eq("admin"), eq("magicops-admin"), anyString(), anyString());
        verify(userService).createUser(eq("developer"), eq("dev123"), anyString(), anyString());
        verify(userService).createUser(eq("approver"), eq("appr123"), anyString(), anyString());
    }

    @Test
    void prodProfile_withPassword_createsOnlyAdmin() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        stubUser("admin");

        new DataInitializer(userService, userRepository, projectRepository, environment,
                "strong-prod-pass")
                .run(null);

        verify(userService).createUser("admin", "strong-prod-pass", "系统管理员", "admin@magicops.local");
        verify(userService).assignRole(anyLong(), eq("PLATFORM_ADMIN"), anyString());
        // 永不创建测试账号
        verify(userService, never()).createUser(eq("developer"), anyString(), anyString(), anyString());
        verify(userService, never()).createUser(eq("approver"), anyString(), anyString(), anyString());
    }

    @Test
    void prodProfile_withoutPassword_createsNoUsers() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        new DataInitializer(userService, userRepository, projectRepository, environment, "")
                .run(null);

        verify(userService, never()).createUser(anyString(), anyString(), anyString(), anyString());
        verify(userService, never()).assignRole(anyLong(), anyString(), anyString());
    }

    @Test
    void existingUsers_skipInitialization() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(userRepository.count()).thenReturn(3L);

        new DataInitializer(userService, userRepository, projectRepository, environment, "")
                .run(null);

        verify(userService, never()).createUser(anyString(), anyString(), anyString(), anyString());
    }
}
