package top.cywu.magicops.console.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.console.entity.security.RoleEntity;
import top.cywu.magicops.console.entity.security.UserEntity;
import top.cywu.magicops.console.repository.security.RoleRepository;
import top.cywu.magicops.console.service.security.UserService;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 切片 31：身份与追责集成测试。
 *
 * <p>覆盖关闭标准：
 * <ul>
 *   <li>提交/审批的审计记录 actor 为实际登录用户</li>
 *   <li>审批本人提交的版本返回 403</li>
 *   <li>角色回收端点生效且 RBAC 覆盖（越权拒绝）</li>
 *   <li>角色授予/回收落关键审计且 actor 为登录用户</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class GovernanceActorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditService auditService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASS = "magicops-admin";
    private static final String DEVELOPER = "developer";
    private static final String DEV_PASS = "dev123";
    private static final String APPROVER = "approver";
    private static final String APPR_PASS = "appr123";

    private long createDraftedScript(String name, String user, String pass) throws Exception {
        MvcResult create = mockMvc.perform(post("/api/scripts")
                        .with(httpBasic(user, pass))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"projectCode\":\"hospital-a\","
                                + "\"scriptType\":\"DYNAMIC_QUERY\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long scriptId = Long.parseLong(
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(create.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(put("/api/scripts/" + scriptId + "/draft")
                        .with(httpBasic(user, pass))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"SELECT 1\",\"routePath\":\"/api/gov-" + scriptId
                                + "\",\"routeMethod\":\"GET\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/scripts/" + scriptId + "/versions")
                        .with(httpBasic(user, pass))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\",\"riskLevel\":\"LOW\"}"))
                .andExpect(status().isCreated());
        return scriptId;
    }

    @Test
    void createScript_auditActorIsLoginUser() throws Exception {
        long scriptId = createDraftedScript("gov-actor-create", DEVELOPER, DEV_PASS);

        var records = auditService.findByEntity("Script", String.valueOf(scriptId));
        AuditRecord created = records.stream()
                .filter(r -> r.eventType().name().equals("SCRIPT_CREATED"))
                .findFirst().orElseThrow();
        assertEquals(DEVELOPER, created.operator(), "创建审计的 actor 应为实际登录用户");
        assertNotEquals("system", created.operator());
    }

    @Test
    void submit_auditActorIsLoginUser() throws Exception {
        long scriptId = createDraftedScript("gov-actor-submit", DEVELOPER, DEV_PASS);

        mockMvc.perform(post("/api/scripts/" + scriptId + "/submit")
                        .with(httpBasic(DEVELOPER, DEV_PASS)))
                .andExpect(status().isOk());

        var records = auditService.findByEntity("Script", String.valueOf(scriptId));
        AuditRecord changed = records.stream()
                .filter(r -> r.eventType().name().equals("SCRIPT_STATUS_CHANGED"))
                .reduce((a, b) -> b)
                .orElseThrow();
        assertEquals(DEVELOPER, changed.operator(), "状态迁移审计的 actor 应为提交人");
    }

    @Test
    void selfApproval_rejectedWith403() throws Exception {
        // admin 自己创建并提交，再自己审批：必须被拒绝
        long scriptId = createDraftedScript("gov-self-approve", ADMIN, ADMIN_PASS);
        mockMvc.perform(post("/api/scripts/" + scriptId + "/submit")
                        .with(httpBasic(ADMIN, ADMIN_PASS)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/scripts/" + scriptId + "/approve")
                        .with(httpBasic(ADMIN, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"self approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvalByDifferentUser_recordedWithLoginActor() throws Exception {
        long scriptId = createDraftedScript("gov-cross-approve", ADMIN, ADMIN_PASS);
        mockMvc.perform(post("/api/scripts/" + scriptId + "/submit")
                        .with(httpBasic(ADMIN, ADMIN_PASS)))
                .andExpect(status().isOk());

        // 提交人为 admin，approver 审批应通过
        mockMvc.perform(post("/api/scripts/" + scriptId + "/approve")
                        .with(httpBasic(APPROVER, APPR_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"cross approve\"}"))
                .andExpect(status().isOk());

        // 审批决策审计 actor 为实际审批人，且为关键审计
        var approvals = auditService.findAll().stream()
                .filter(r -> r.eventType().name().equals("APPROVAL_DECIDED")
                        && APPROVER.equals(r.operator()))
                .toList();
        assertFalse(approvals.isEmpty(), "应存在 approver 的审批决策审计");
        AuditRecord decided = approvals.get(approvals.size() - 1);
        assertEquals(APPROVER, decided.operator());
        assertTrue(decided.critical());
        assertEquals(String.valueOf(scriptId), String.valueOf(decided.details().get("scriptId")));
    }

    @Test
    void developer_cannotApprove_returns403() throws Exception {
        long scriptId = createDraftedScript("gov-dev-approve", ADMIN, ADMIN_PASS);
        mockMvc.perform(post("/api/scripts/" + scriptId + "/submit")
                        .with(httpBasic(ADMIN, ADMIN_PASS)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/scripts/" + scriptId + "/approve")
                        .with(httpBasic(DEVELOPER, DEV_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"dev approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleAssignAndRevoke_actorAuditedAndRbacEnforced() throws Exception {
        // admin 创建受害者用户
        MvcResult created = mockMvc.perform(post("/api/users")
                        .with(httpBasic(ADMIN, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"gov-victim\",\"password\":\"pass123\","
                                + "\"displayName\":\"Gov Victim\",\"email\":\"victim@gov.local\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = Long.parseLong(
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(created.getResponse().getContentAsString()).get("id").asText());

        // admin 分配角色：ROLE_ASSIGNED 关键审计 actor=admin
        mockMvc.perform(post("/api/users/" + userId + "/roles")
                        .with(httpBasic(ADMIN, ADMIN_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"DEVELOPER\"}"))
                .andExpect(status().isOk());

        var assigned = auditService.findByEntity("User", String.valueOf(userId)).stream()
                .filter(r -> r.eventType().name().equals("ROLE_ASSIGNED"))
                .findFirst().orElseThrow(() -> new AssertionError("缺少 ROLE_ASSIGNED 审计"));
        assertEquals(ADMIN, assigned.operator(), "角色分配审计的 actor 应为登录用户而非 system");
        assertTrue(assigned.critical());

        RoleEntity devRole = roleRepository.findByName("DEVELOPER").orElseThrow();

        // developer 越权回收角色：403
        mockMvc.perform(delete("/api/users/" + userId + "/roles/" + devRole.getId())
                        .with(httpBasic(DEVELOPER, DEV_PASS)))
                .andExpect(status().isForbidden());

        // admin 回收角色：成功且落 ROLE_REVOKED 关键审计
        mockMvc.perform(delete("/api/users/" + userId + "/roles/" + devRole.getId())
                        .with(httpBasic(ADMIN, ADMIN_PASS)))
                .andExpect(status().isOk());

        UserEntity victim = userService.findByUsername("gov-victim");
        assertTrue(userService.getUserRoleNames(victim.getId()).isEmpty(), "角色应已被回收");

        var revoked = auditService.findByEntity("User", String.valueOf(userId)).stream()
                .filter(r -> r.eventType().name().equals("ROLE_REVOKED"))
                .findFirst().orElseThrow(() -> new AssertionError("缺少 ROLE_REVOKED 审计"));
        assertEquals(ADMIN, revoked.operator());
        assertTrue(revoked.critical());

        // 重复回收：绑定不存在，404
        mockMvc.perform(delete("/api/users/" + userId + "/roles/" + devRole.getId())
                        .with(httpBasic(ADMIN, ADMIN_PASS)))
                .andExpect(status().isNotFound());
    }
}
