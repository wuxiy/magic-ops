package top.cywu.magicops.console.entity.config;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "publish_packages")
public class PublishPackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", length = 50)
    private String projectCode;

    @Column(length = 50)
    private String environment;

    @Column(name = "package_version", length = 50)
    private String packageVersion;

    @Column(name = "runtime_version", length = 50)
    private String runtimeVersion;

    @Column(name = "published_by", length = 100)
    private String publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "key_id", length = 100)
    private String keyId;

    @Column(name = "sign_alg", length = 50)
    private String signAlg;

    @Column(length = 500)
    private String signature;

    @Column(name = "metadata_hash", length = 200)
    private String metadataHash;

    @Column(name = "policy_hash", length = 200)
    private String policyHash;

    @Column(length = 30)
    private String status;

    @Column(name = "runtime_response", columnDefinition = "TEXT")
    private String runtimeResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PublishPackageEntity() {
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getPackageVersion() { return packageVersion; }
    public void setPackageVersion(String packageVersion) { this.packageVersion = packageVersion; }

    public String getRuntimeVersion() { return runtimeVersion; }
    public void setRuntimeVersion(String runtimeVersion) { this.runtimeVersion = runtimeVersion; }

    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getSignAlg() { return signAlg; }
    public void setSignAlg(String signAlg) { this.signAlg = signAlg; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getMetadataHash() { return metadataHash; }
    public void setMetadataHash(String metadataHash) { this.metadataHash = metadataHash; }

    public String getPolicyHash() { return policyHash; }
    public void setPolicyHash(String policyHash) { this.policyHash = policyHash; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRuntimeResponse() { return runtimeResponse; }
    public void setRuntimeResponse(String runtimeResponse) { this.runtimeResponse = runtimeResponse; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
