package top.cywu.magicops.console.entity.config;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "http_targets")
public class HttpTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "target_id", length = 100)
    private String targetId;

    @Column(length = 100)
    private String name;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "allowed_paths", length = 1000)
    private String allowedPaths;

    @Column(name = "auth_type", length = 50)
    private String authType;

    @Column(name = "auth_config_ref", length = 200)
    private String authConfigRef;

    @Column(name = "require_encryption", nullable = false)
    private boolean requireEncryption = false;

    @Column(name = "crypto_key_id", length = 100)
    private String cryptoKeyId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public HttpTargetEntity() {
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getAllowedPaths() { return allowedPaths; }
    public void setAllowedPaths(String allowedPaths) { this.allowedPaths = allowedPaths; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getAuthConfigRef() { return authConfigRef; }
    public void setAuthConfigRef(String authConfigRef) { this.authConfigRef = authConfigRef; }

    public boolean isRequireEncryption() { return requireEncryption; }
    public void setRequireEncryption(boolean requireEncryption) { this.requireEncryption = requireEncryption; }

    public String getCryptoKeyId() { return cryptoKeyId; }
    public void setCryptoKeyId(String cryptoKeyId) { this.cryptoKeyId = cryptoKeyId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
