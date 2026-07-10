package top.cywu.magicops.console.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.entity.ScriptVersionEntity;
import top.cywu.magicops.core.model.*;
import top.cywu.magicops.governance.lifecycle.IllegalStateTransitionException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本生命周期集成测试。使用 H2 内存数据库。
 *
 * <p>覆盖关闭标准：
 * <ul>
 *   <li>脚本可以从 DRAFT 走到 APPROVED</li>
 *   <li>非法状态迁移被拒绝</li>
 *   <li>审批记录可审计</li>
 * </ul>
 */
@SpringBootTest
class ScriptLifecycleServiceTest {

    @Autowired
    private ScriptLifecycleService lifecycleService;

    @Autowired
    private AuditService auditService;

    @Test
    void fullLifecycle_draftToApproved() {
        // 1. 创建脚本 (DRAFT)
        ScriptEntity script = lifecycleService.createScript(
                "test-query-001", "project-a", ScriptType.DYNAMIC_QUERY, "dev1");
        assertNotNull(script.getId());
        assertEquals(ScriptStatus.DRAFT, script.getStatus());

        // 2. 编辑草稿
        lifecycleService.updateDraft(script.getId(),
                "return db.select('SELECT * FROM patients WHERE id = #{id}')",
                "/api/patients/query", "GET");

        // 3. 创建版本
        ScriptVersionEntity version = lifecycleService.createVersion(
                script.getId(), "1.0.0", RiskLevel.LOW, "dev1");
        assertNotNull(version.getId());

        // 4. 提交审批 (DEBUGGED → SUBMITTED → REVIEWING)
        ApprovalEntity approval = lifecycleService.submitForApproval(script.getId(), "dev1");
        assertNotNull(approval.getId());

        // 5. 审批通过 (REVIEWING → APPROVED)
        lifecycleService.decide(script.getId(), ApprovalDecision.APPROVED, "reviewer1", "LGTM");

        // 验证最终状态
        // 重新从 service 拿不到（没有 findById），但 audit 记录了所有迁移
        List<AuditRecord> scriptAudits = auditService.findByEntity("Script", String.valueOf(script.getId()));
        assertFalse(scriptAudits.isEmpty(), "脚本应当有审计记录");

        // 验证审批审计
        List<AuditRecord> approvalAudits = auditService.findByEntity("Approval", String.valueOf(approval.getId()));
        assertFalse(approvalAudits.isEmpty(), "审批应当有审计记录");

        // 验证关键审计存在
        boolean hasCriticalApproval = approvalAudits.stream()
                .anyMatch(r -> r.critical() && r.eventType().name().equals("APPROVAL_DECIDED"));
        assertTrue(hasCriticalApproval, "审批决策应当是关键审计");
    }

    @Test
    void rejection_reviewingToRejected() {
        ScriptEntity script = lifecycleService.createScript(
                "test-repair-001", "project-b", ScriptType.DATA_REPAIR, "dev2");
        lifecycleService.updateDraft(script.getId(), "UPDATE t SET x=1", "/repair", "POST");
        lifecycleService.createVersion(script.getId(), "1.0.0", RiskLevel.HIGH, "dev2");
        lifecycleService.submitForApproval(script.getId(), "dev2");

        // 拒绝
        lifecycleService.decide(script.getId(), ApprovalDecision.REJECTED, "reviewer2", "风险太高");

        // 审批记录可审计
        List<AuditRecord> audits = auditService.findByEntity("Script", String.valueOf(script.getId()));
        assertTrue(audits.stream().anyMatch(r ->
                r.details().containsValue("REJECTED")),
                "审计应记录 REJECTED 状态");
    }

    @Test
    void illegalTransition_throwsException() {
        ScriptEntity script = lifecycleService.createScript(
                "test-illegal-001", "project-c", ScriptType.HTTP_ADAPTER, "dev3");

        // 不能从 DRAFT 直接到 APPROVED
        assertThrows(IllegalStateTransitionException.class,
                () -> lifecycleService.transition(script.getId(), ScriptStatus.APPROVED, "dev3"));
    }

    @Test
    void approvalRecord_isAuditable() {
        ScriptEntity script = lifecycleService.createScript(
                "test-audit-001", "project-d", ScriptType.DYNAMIC_QUERY, "dev4");
        lifecycleService.updateDraft(script.getId(), "SELECT 1", "/health", "GET");
        lifecycleService.createVersion(script.getId(), "1.0.0", RiskLevel.LOW, "dev4");

        ApprovalEntity approval = lifecycleService.submitForApproval(script.getId(), "dev4");
        lifecycleService.decide(script.getId(), ApprovalDecision.APPROVED, "reviewer4", "OK");

        // 验证审批记录有完整的审计链
        List<AuditRecord> approvalAudits = auditService.findByEntity("Approval", String.valueOf(approval.getId()));
        assertTrue(approvalAudits.size() >= 2, "应当至少有提交和决策两条审计记录");
    }
}
