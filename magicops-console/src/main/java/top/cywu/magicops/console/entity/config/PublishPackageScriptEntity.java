package top.cywu.magicops.console.entity.config;

import jakarta.persistence.*;

@Entity
@Table(name = "publish_package_scripts")
public class PublishPackageScriptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "script_id", length = 100)
    private String scriptId;

    @Column(name = "script_version", length = 50)
    private String scriptVersion;

    @Column(length = 200)
    private String path;

    @Column(length = 20)
    private String method;

    @Column(length = 50)
    private String scenario;

    @Column(name = "risk_level", length = 30)
    private String riskLevel;

    @Column(name = "content_hash", length = 200)
    private String contentHash;

    public PublishPackageScriptEntity() {
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }

    public String getScriptVersion() { return scriptVersion; }
    public void setScriptVersion(String scriptVersion) { this.scriptVersion = scriptVersion; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
}
