package top.cywu.magicops.console.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * ScriptDraft JPA 实体。草稿是 Script 的可编辑版本，只能在 Console 中创建和修改。
 */
@Entity
@Table(name = "script_drafts")
public class ScriptDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "route_path", length = 500)
    private String routePath;

    @Column(name = "route_method", length = 10)
    private String routeMethod;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ScriptDraftEntity() {
    }

    public ScriptDraftEntity(Long scriptId) {
        this.scriptId = scriptId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }

    public String getContent() { return content; }
    public void setContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) {
        this.routePath = routePath;
        this.updatedAt = Instant.now();
    }

    public String getRouteMethod() { return routeMethod; }
    public void setRouteMethod(String routeMethod) {
        this.routeMethod = routeMethod;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
