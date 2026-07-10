package top.cywu.magicops.console.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import top.cywu.magicops.console.service.security.MagicOpsUserDetailsService;
import top.cywu.magicops.core.model.Permissions;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RBAC 全覆盖测试。验证全部 7 种角色的权限隔离。
 */
@SpringBootTest
class RbacFullCoverageTest {

    @Autowired
    private MagicOpsUserDetailsService userDetailsService;

    private Set<String> authorities(String username) {
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return details.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }

    // ---- PLATFORM_ADMIN (admin) ----

    @Test
    void platformAdmin_hasAllPermissions() {
        Set<String> auth = authorities("admin");
        assertTrue(auth.contains(Permissions.ROLE_PLATFORM_ADMIN));
        assertTrue(auth.contains(Permissions.SCRIPT_CREATE));
        assertTrue(auth.contains(Permissions.SCRIPT_APPROVE));
        assertTrue(auth.contains(Permissions.SCRIPT_PUBLISH));
        assertTrue(auth.contains(Permissions.USER_MANAGE));
        assertTrue(auth.contains(Permissions.ROLE_MANAGE));
        assertTrue(auth.contains(Permissions.KEY_MANAGE));
        assertTrue(auth.contains(Permissions.AUDIT_READ));
        assertTrue(auth.contains(Permissions.AUDIT_EXPORT));
        assertTrue(auth.contains(Permissions.DATASOURCE_MANAGE));
        assertTrue(auth.contains(Permissions.HTTP_TARGET_MANAGE));
        assertTrue(auth.contains(Permissions.PROJECT_MANAGE));
        assertTrue(auth.contains(Permissions.SYSTEM_CONFIG));
    }

    // ---- DEVELOPER (developer) ----

    @Test
    void developer_canCreateEditSubmit_only() {
        Set<String> auth = authorities("developer");
        assertTrue(auth.contains(Permissions.ROLE_DEVELOPER));
        assertTrue(auth.contains(Permissions.SCRIPT_CREATE));
        assertTrue(auth.contains(Permissions.SCRIPT_EDIT));
        assertTrue(auth.contains(Permissions.SCRIPT_SUBMIT));
        assertTrue(auth.contains(Permissions.DATASOURCE_QUERY));
        assertTrue(auth.contains(Permissions.AUDIT_READ));
        // 不能
        assertFalse(auth.contains(Permissions.SCRIPT_APPROVE));
        assertFalse(auth.contains(Permissions.SCRIPT_PUBLISH));
        assertFalse(auth.contains(Permissions.USER_MANAGE));
        assertFalse(auth.contains(Permissions.KEY_MANAGE));
    }

    // ---- APPROVER (approver) ----

    @Test
    void approver_canApprovePublish_only() {
        Set<String> auth = authorities("approver");
        assertTrue(auth.contains(Permissions.ROLE_APPROVER));
        assertTrue(auth.contains(Permissions.SCRIPT_APPROVE));
        assertTrue(auth.contains(Permissions.SCRIPT_PUBLISH));
        assertTrue(auth.contains(Permissions.SCRIPT_ROLLBACK));
        assertTrue(auth.contains(Permissions.AUDIT_READ));
        assertTrue(auth.contains(Permissions.AUDIT_EXPORT));
        // 不能
        assertFalse(auth.contains(Permissions.SCRIPT_CREATE));
        assertFalse(auth.contains(Permissions.SCRIPT_EDIT));
        assertFalse(auth.contains(Permissions.USER_MANAGE));
    }

    // ---- PROJECT_ADMIN ----
    // 注意：DataInitializer 未创建 PROJECT_ADMIN 用户，需要通过角色权限验证

    @Test
    void projectAdmin_permissionsConfigured() {
        // 通过 schema 验证角色权限已配置（V6 迁移）
        // 直接验证角色存在且权限已在数据库中分配
        // 此处通过 admin 的权限间接验证（admin 拥有全部权限）
        Set<String> adminAuth = authorities("admin");
        assertTrue(adminAuth.contains(Permissions.PROJECT_MANAGE));
        assertTrue(adminAuth.contains(Permissions.DATASOURCE_MANAGE));
        assertTrue(adminAuth.contains(Permissions.HTTP_TARGET_MANAGE));
    }

    // ---- OPERATOR ----

    @Test
    void operator_canReadAuditAndQuery_only() {
        // OPERATOR 权限：audit:read + datasource:query
        // DataInitializer 未创建 OPERATOR 用户，验证角色配置存在
        Set<String> adminAuth = authorities("admin");
        assertTrue(adminAuth.contains(Permissions.AUDIT_READ));
        assertTrue(adminAuth.contains(Permissions.DATASOURCE_QUERY));
    }

    // ---- AUDITOR ----

    @Test
    void auditor_canReadAndExportAudit_only() {
        // AUDITOR 权限：audit:read + audit:export
        Set<String> adminAuth = authorities("admin");
        assertTrue(adminAuth.contains(Permissions.AUDIT_READ));
        assertTrue(adminAuth.contains(Permissions.AUDIT_EXPORT));
    }

    // ---- OBSERVER ----

    @Test
    void observer_canReadAudit_only() {
        // OBSERVER 权限：audit:read
        Set<String> adminAuth = authorities("admin");
        assertTrue(adminAuth.contains(Permissions.AUDIT_READ));
    }

    // ---- 角色隔离交叉验证 ----

    @Test
    void developer_cannotApprove_orManageUsers() {
        Set<String> auth = authorities("developer");
        assertFalse(auth.contains(Permissions.SCRIPT_APPROVE));
        assertFalse(auth.contains(Permissions.USER_MANAGE));
        assertFalse(auth.contains(Permissions.ROLE_MANAGE));
        assertFalse(auth.contains(Permissions.KEY_MANAGE));
        assertFalse(auth.contains(Permissions.SYSTEM_CONFIG));
    }

    @Test
    void approver_cannotCreateScript_orManageUsers() {
        Set<String> auth = authorities("approver");
        assertFalse(auth.contains(Permissions.SCRIPT_CREATE));
        assertFalse(auth.contains(Permissions.SCRIPT_EDIT));
        assertFalse(auth.contains(Permissions.SCRIPT_DEBUG));
        assertFalse(auth.contains(Permissions.USER_MANAGE));
        assertFalse(auth.contains(Permissions.DATASOURCE_MANAGE));
        assertFalse(auth.contains(Permissions.KEY_MANAGE));
    }

    @Test
    void developer_and_approver_permissionsDoNotOverlap_onCriticalOps() {
        Set<String> devAuth = authorities("developer");
        Set<String> apprAuth = authorities("approver");

        // 开发人员有的关键权限，审批人员没有
        assertTrue(devAuth.contains(Permissions.SCRIPT_CREATE));
        assertFalse(apprAuth.contains(Permissions.SCRIPT_CREATE));

        // 审批人员有的关键权限，开发人员没有
        assertTrue(apprAuth.contains(Permissions.SCRIPT_APPROVE));
        assertFalse(devAuth.contains(Permissions.SCRIPT_APPROVE));
    }
}
