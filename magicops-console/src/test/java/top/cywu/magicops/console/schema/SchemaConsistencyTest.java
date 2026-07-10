package top.cywu.magicops.console.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.cywu.magicops.console.repository.config.*;
import top.cywu.magicops.console.repository.security.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema 一致性测试。验证 Flyway 迁移成功执行且所有表/Repository 可正常访问。
 */
@SpringBootTest
class SchemaConsistencyTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private ResourcePermissionRepository resourcePermissionRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EnvironmentRepository environmentRepository;
    @Autowired private DataSourceRepository dataSourceRepository;
    @Autowired private KeyReferenceRepository keyReferenceRepository;
    @Autowired private HttpTargetRepository httpTargetRepository;
    @Autowired private PublishPackageRepository publishPackageRepository;

    @Test
    void allRepositoriesInjected() {
        assertNotNull(userRepository, "UserRepository 应被注入");
        assertNotNull(roleRepository, "RoleRepository 应被注入");
        assertNotNull(userRoleRepository, "UserRoleRepository 应被注入");
        assertNotNull(permissionRepository, "PermissionRepository 应被注入");
        assertNotNull(rolePermissionRepository, "RolePermissionRepository 应被注入");
        assertNotNull(resourcePermissionRepository, "ResourcePermissionRepository 应被注入");
        assertNotNull(projectRepository, "ProjectRepository 应被注入");
        assertNotNull(environmentRepository, "EnvironmentRepository 应被注入");
        assertNotNull(dataSourceRepository, "DataSourceRepository 应被注入");
        assertNotNull(keyReferenceRepository, "KeyReferenceRepository 应被注入");
        assertNotNull(httpTargetRepository, "HttpTargetRepository 应被注入");
        assertNotNull(publishPackageRepository, "PublishPackageRepository 应被注入");
    }

    @Test
    void predefinedRolesExist() {
        long roleCount = roleRepository.count();
        assertEquals(7, roleCount, "应预置 7 种角色");

        assertTrue(roleRepository.existsByName("PLATFORM_ADMIN"), "平台管理员角色应存在");
        assertTrue(roleRepository.existsByName("DEVELOPER"), "开发人员角色应存在");
        assertTrue(roleRepository.existsByName("APPROVER"), "审批人员角色应存在");
        assertTrue(roleRepository.existsByName("AUDITOR"), "安全审计员角色应存在");
    }

    @Test
    void predefinedPermissionsExist() {
        long permissionCount = permissionRepository.count();
        assertEquals(17, permissionCount, "应预置 17 种权限");

        assertTrue(permissionRepository.findByCode("script:create").isPresent(), "script:create 权限应存在");
        assertTrue(permissionRepository.findByCode("script:approve").isPresent(), "script:approve 权限应存在");
        assertTrue(permissionRepository.findByCode("audit:read").isPresent(), "audit:read 权限应存在");
    }

    @Test
    void canQueryAllTables() {
        // 验证所有表可查询（不应抛异常）
        assertDoesNotThrow(() -> userRepository.findAll());
        assertDoesNotThrow(() -> roleRepository.findAll());
        assertDoesNotThrow(() -> userRoleRepository.findAll());
        assertDoesNotThrow(() -> permissionRepository.findAll());
        assertDoesNotThrow(() -> rolePermissionRepository.findAll());
        assertDoesNotThrow(() -> resourcePermissionRepository.findAll());
        assertDoesNotThrow(() -> projectRepository.findAll());
        assertDoesNotThrow(() -> environmentRepository.findAll());
        assertDoesNotThrow(() -> dataSourceRepository.findAll());
        assertDoesNotThrow(() -> keyReferenceRepository.findAll());
        assertDoesNotThrow(() -> httpTargetRepository.findAll());
        assertDoesNotThrow(() -> publishPackageRepository.findAll());
    }
}
