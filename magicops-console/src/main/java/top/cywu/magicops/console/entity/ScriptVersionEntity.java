package top.cywu.magicops.console.entity;

import jakarta.persistence.*;
import top.cywu.magicops.core.model.RiskLevel;

import java.time.Instant;

/**
 * ScriptVersion JPA 实体。从 Draft 固化出的不可变版本。
 */
@Entity
@Table(name = "script_versions")
public class ScriptVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "route_path", nullable = false, length = 500)
    private String routePath;

    @Column(name = "route_method", nullable = false, length = 10)
    private String routeMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "resource_declaration", columnDefinition = "TEXT")
    private String resourceDeclaration;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ScriptVersionEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }

    public String getRouteMethod() { return routeMethod; }
    public void setRouteMethod(String routeMethod) { this.routeMethod = routeMethod; }

    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }

    public String getResourceDeclaration() { return resourceDeclaration; }
    public void setResourceDeclaration(String resourceDeclaration) { this.resourceDeclaration = resourceDeclaration; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
