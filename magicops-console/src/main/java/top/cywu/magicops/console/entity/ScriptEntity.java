package top.cywu.magicops.console.entity;

import jakarta.persistence.*;
import top.cywu.magicops.core.model.ScriptStatus;
import top.cywu.magicops.core.model.ScriptType;

import java.time.Instant;

/**
 * Script JPA 实体。脚本是平台的核心对象，动态 API、查询、修复和适配都以 Script 为基础。
 */
@Entity
@Table(name = "scripts")
public class ScriptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "project_code", length = 50)
    private String projectCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScriptType scriptType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScriptStatus status;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public ScriptEntity() {
    }

    public ScriptEntity(String name, String projectCode, ScriptType scriptType, String createdBy) {
        this.name = name;
        this.projectCode = projectCode;
        this.scriptType = scriptType;
        this.status = ScriptStatus.DRAFT;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }

    public ScriptType getScriptType() { return scriptType; }
    public void setScriptType(ScriptType scriptType) { this.scriptType = scriptType; }

    public ScriptStatus getStatus() { return status; }
    public void setStatus(ScriptStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
