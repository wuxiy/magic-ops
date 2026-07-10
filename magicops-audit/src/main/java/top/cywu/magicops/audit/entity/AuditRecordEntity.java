package top.cywu.magicops.audit.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 审计记录 JPA 实体。映射到 audit_records 表。
 */
@Entity
@Table(name = "audit_records")
public class AuditRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(length = 100)
    private String operator;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false)
    private boolean critical;

    @Column(columnDefinition = "TEXT")
    private String details;

    public AuditRecordEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Instant getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }

    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
