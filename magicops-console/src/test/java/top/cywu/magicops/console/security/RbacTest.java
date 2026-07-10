package top.cywu.magicops.console.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import top.cywu.magicops.console.service.security.MagicOpsUserDetailsService;
import top.cywu.magicops.core.model.Permissions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RBAC 权限隔离测试。验证不同角色拥有正确的权限集合。
 */
@SpringBootTest
class RbacTest {

    @Autowired
    private MagicOpsUserDetailsService userDetailsService;

    @Test
    void platformAdmin_hasAllPermissions() {
        UserDetails admin = userDetailsService.loadUserByUsername("admin");
        var authorities = admin.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();

        assertTrue(authorities.contains("ROLE_PLATFORM_ADMIN"));
        // 平台管理员应有所有权限
        assertTrue(authorities.contains(Permissions.SCRIPT_CREATE));
        assertTrue(authorities.contains(Permissions.SCRIPT_APPROVE));
        assertTrue(authorities.contains(Permissions.SCRIPT_PUBLISH));
        assertTrue(authorities.contains(Permissions.USER_MANAGE));
        assertTrue(authorities.contains(Permissions.ROLE_MANAGE));
        assertTrue(authorities.contains(Permissions.AUDIT_READ));
        assertTrue(authorities.contains(Permissions.KEY_MANAGE));
    }

    @Test
    void developer_hasScriptEditPermissions_only() {
        UserDetails dev = userDetailsService.loadUserByUsername("developer");
        var authorities = dev.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();

        assertTrue(authorities.contains("ROLE_DEVELOPER"));
        // 开发人员可以创建/编辑/提交脚本
        assertTrue(authorities.contains(Permissions.SCRIPT_CREATE));
        assertTrue(authorities.contains(Permissions.SCRIPT_EDIT));
        assertTrue(authorities.contains(Permissions.SCRIPT_SUBMIT));
        // 开发人员不能审批/发布
        assertFalse(authorities.contains(Permissions.SCRIPT_APPROVE));
        assertFalse(authorities.contains(Permissions.SCRIPT_PUBLISH));
        // 开发人员不能管理用户
        assertFalse(authorities.contains(Permissions.USER_MANAGE));
    }

    @Test
    void approver_hasApprovePermissions_only() {
        UserDetails approver = userDetailsService.loadUserByUsername("approver");
        var authorities = approver.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();

        assertTrue(authorities.contains("ROLE_APPROVER"));
        // 审批人员可以审批/发布
        assertTrue(authorities.contains(Permissions.SCRIPT_APPROVE));
        assertTrue(authorities.contains(Permissions.SCRIPT_PUBLISH));
        // 审批人员不能创建/编辑脚本
        assertFalse(authorities.contains(Permissions.SCRIPT_CREATE));
        assertFalse(authorities.contains(Permissions.SCRIPT_EDIT));
        // 审批人员不能管理用户
        assertFalse(authorities.contains(Permissions.USER_MANAGE));
    }

    @Test
    void developer_cannotApprove() {
        UserDetails dev = userDetailsService.loadUserByUsername("developer");
        var authorities = dev.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();

        assertFalse(authorities.contains(Permissions.SCRIPT_APPROVE),
                "开发人员不应有审批权限");
    }

    @Test
    void approver_cannotCreateScript() {
        UserDetails approver = userDetailsService.loadUserByUsername("approver");
        var authorities = approver.getAuthorities().stream()
                .map(a -> a.getAuthority()).toList();

        assertFalse(authorities.contains(Permissions.SCRIPT_CREATE),
                "审批人员不应有创建脚本权限");
    }
}
