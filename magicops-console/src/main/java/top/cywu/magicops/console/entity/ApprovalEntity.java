package top.cywu.magicops.console.entity;

import jakarta.persistence.*;
import top.cywu.magicops.core.model.ApprovalDecision;

import java.time.Instant;

/**
 * Approval JPA 实体。审批记录，必须可审计。
 */
@Entity
@Table(name = "approvals")
public class ApprovalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_version_id", nullable = false)
    private Long scriptVersionId;

    @Column(name = "submitted_by", nullable = false, length = 100)
    private String submittedBy;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApprovalDecision decision;

    @Column(length = 2000)
    private String comment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public ApprovalEntity() {
    }

    public ApprovalEntity(Long scriptVersionId, String submittedBy) {
        this.scriptVersionId = scriptVersionId;
        this.submittedBy = submittedBy;
        this.submittedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScriptVersionId() { return scriptVersionId; }
    public void setScriptVersionId(Long scriptVersionId) { this.scriptVersionId = scriptVersionId; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }

    public ApprovalDecision getDecision() { return decision; }
    public void setDecision(ApprovalDecision decision) { this.decision = decision; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
