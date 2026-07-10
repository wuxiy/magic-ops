package top.cywu.magicops.console.entity.config;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "environments")
public class EnvironmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "environment_type", length = 30)
    private String environmentType;

    @Column(name = "runtime_url", length = 500)
    private String runtimeUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public EnvironmentEntity() {
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEnvironmentType() { return environmentType; }
    public void setEnvironmentType(String environmentType) { this.environmentType = environmentType; }

    public String getRuntimeUrl() { return runtimeUrl; }
    public void setRuntimeUrl(String runtimeUrl) { this.runtimeUrl = runtimeUrl; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
