package top.cywu.magicops.console.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.entity.ScriptDraftEntity;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.entity.ScriptVersionEntity;
import top.cywu.magicops.console.repository.ApprovalRepository;
import top.cywu.magicops.console.repository.ScriptDraftRepository;
import top.cywu.magicops.console.repository.ScriptRepository;
import top.cywu.magicops.console.repository.ScriptVersionRepository;
import top.cywu.magicops.core.model.ApprovalDecision;
import top.cywu.magicops.core.model.RiskLevel;
import top.cywu.magicops.core.model.ScriptStatus;
import top.cywu.magicops.governance.lifecycle.ScriptStateMachine;

import java.time.Instant;
import java.util.Map;

/**
 * 脚本生命周期服务。管理脚本创建、草稿编辑、版本固化和状态迁移。
 */
@Service
@Transactional
public class ScriptLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ScriptLifecycleService.class);

    private final ScriptRepository scriptRepository;
    private final ScriptDraftRepository draftRepository;
    private final ScriptVersionRepository versionRepository;
    private final ApprovalRepository approvalRepository;
    private final AuditService auditService;

    public ScriptLifecycleService(ScriptRepository scriptRepository,
                                  ScriptDraftRepository draftRepository,
                                  ScriptVersionRepository versionRepository,
                                  ApprovalRepository approvalRepository,
                                  AuditService auditService) {
        this.scriptRepository = scriptRepository;
        this.draftRepository = draftRepository;
        this.versionRepository = versionRepository;
        this.approvalRepository = approvalRepository;
        this.auditService = auditService;
    }

    /**
     * 创建脚本及其草稿。初始状态为 DRAFT。
     */
    public ScriptEntity createScript(String name, String projectCode,
                                     top.cywu.magicops.core.model.ScriptType scriptType,
                                     String operator) {
        ScriptEntity script = new ScriptEntity(name, projectCode, scriptType, operator);
        script = scriptRepository.save(script);

        ScriptDraftEntity draft = new ScriptDraftEntity(script.getId());
        draftRepository.save(draft);

        auditService.write(AuditRecord.of(
                AuditEventType.SCRIPT_CREATED, "Script", String.valueOf(script.getId()),
                operator, Map.of("name", name, "type", scriptType.name())));

        log.info("script_created id={} name={} type={}", script.getId(), name, scriptType);
        return script;
    }

    /**
     * 更新草稿内容。
     */
    public ScriptDraftEntity updateDraft(Long scriptId, String content,
                                         String routePath, String routeMethod) {
        ScriptDraftEntity draft = draftRepository.findByScriptId(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在: scriptId=" + scriptId));
        draft.setContent(content);
        draft.setRoutePath(routePath);
        draft.setRouteMethod(routeMethod);
        return draftRepository.save(draft);
    }

    /**
     * 执行状态迁移。使用状态机校验合法性，写入审计记录。
     */
    public ScriptEntity transition(Long scriptId, ScriptStatus targetStatus, String operator) {
        ScriptEntity script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在: id=" + scriptId));

        ScriptStatus from = script.getStatus();
        ScriptStateMachine.transition(from, targetStatus);
        script.setStatus(targetStatus);
        script = scriptRepository.save(script);

        auditService.write(AuditRecord.of(
                AuditEventType.SCRIPT_STATUS_CHANGED, "Script", String.valueOf(scriptId),
                operator, Map.of("from", from.name(), "to", targetStatus.name())));

        log.info("script_transition id={} {} -> {} by={}", scriptId, from, targetStatus, operator);
        return script;
    }

    /**
     * 从草稿固化出版本。草稿内容被快照为不可变 ScriptVersion。
     */
    public ScriptVersionEntity createVersion(Long scriptId, String version,
                                             RiskLevel riskLevel, String operator) {
        ScriptEntity script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在: id=" + scriptId));
        ScriptDraftEntity draft = draftRepository.findByScriptId(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("草稿不存在: scriptId=" + scriptId));

        ScriptVersionEntity sv = new ScriptVersionEntity();
        sv.setScriptId(scriptId);
        sv.setVersion(version);
        sv.setContent(draft.getContent() != null ? draft.getContent() : "");
        sv.setRoutePath(draft.getRoutePath() != null ? draft.getRoutePath() : "");
        sv.setRouteMethod(draft.getRouteMethod() != null ? draft.getRouteMethod() : "GET");
        sv.setRiskLevel(riskLevel);
        sv.setCreatedAt(Instant.now());
        sv = versionRepository.save(sv);

        script.setCurrentVersionId(sv.getId());
        scriptRepository.save(script);

        log.info("version_created scriptId={} versionId={} version={}", scriptId, sv.getId(), version);
        return sv;
    }

    /**
     * 提交审批。创建审批记录并将脚本状态迁移到 SUBMITTED。
     */
    public ApprovalEntity submitForApproval(Long scriptId, String operator) {
        ScriptEntity script = transition(scriptId, ScriptStatus.DEBUGGED, operator);
        ScriptVersionEntity version = versionRepository.findById(script.getCurrentVersionId())
                .orElseThrow(() -> new IllegalArgumentException("版本不存在"));

        // DEBUGGED → SUBMITTED
        transition(scriptId, ScriptStatus.SUBMITTED, operator);
        // SUBMITTED → REVIEWING
        transition(scriptId, ScriptStatus.REVIEWING, operator);

        ApprovalEntity approval = new ApprovalEntity(version.getId(), operator);
        approval = approvalRepository.save(approval);

        auditService.write(AuditRecord.of(
                AuditEventType.APPROVAL_SUBMITTED, "Approval", String.valueOf(approval.getId()),
                operator, Map.of("scriptId", scriptId, "versionId", version.getId())));

        return approval;
    }

    /**
     * 审批决策。APPROVED 或 REJECTED，审批记录可审计。
     */
    public ApprovalEntity decide(Long scriptId, ApprovalDecision decision,
                                 String reviewer, String comment) {
        ScriptEntity script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在: id=" + scriptId));
        ApprovalEntity approval = approvalRepository.findByScriptVersionId(script.getCurrentVersionId())
                .orElseThrow(() -> new IllegalArgumentException("审批记录不存在"));

        // 切片 31：提交人与审批人分离，禁止自审自批
        if (approval.getSubmittedBy() != null && approval.getSubmittedBy().equals(reviewer)) {
            throw new ApprovalSeparationException(
                    "审批人 " + reviewer + " 同时是该版本的提交人，不允许审批本人提交的版本");
        }

        approval.setDecision(decision);
        approval.setDecidedBy(reviewer);
        approval.setComment(comment);
        approval.setDecidedAt(Instant.now());
        approval = approvalRepository.save(approval);

        if (decision == ApprovalDecision.APPROVED) {
            transition(scriptId, ScriptStatus.APPROVED, reviewer);
        } else {
            transition(scriptId, ScriptStatus.REJECTED, reviewer);
        }

        auditService.write(AuditRecord.critical(
                AuditEventType.APPROVAL_DECIDED, "Approval", String.valueOf(approval.getId()),
                reviewer, Map.of("scriptId", scriptId, "decision", decision.name(),
                        "comment", comment != null ? comment : "")));

        return approval;
    }
}
