package top.cywu.magicops.runtime.packageMgmt;

import jakarta.persistence.*;
import top.cywu.magicops.sign.model.PublishPackage;

import java.time.Instant;

/**
 * 激活发布包持久化实体（切片 33）。
 *
 * <p>Runtime 将验签通过的发布包以 {@link PublishPackage#toMap()} 序列化为 JSON 落库，
 * 重启后读取 status=ACTIVE 的行，反序列化并重新验签后重新激活。
 * 新包激活时，旧行置为 INACTIVE。
 */
@Entity
@Table(name = "active_packages")
public class ActivePackageEntity {

    /** 激活中状态。同一时刻至多一行 ACTIVE。 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 已被新包取代或重载验签失败而停用。 */
    public static final String STATUS_INACTIVE = "INACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_version", nullable = false, length = 100)
    private String packageVersion;

    @Column(nullable = false, length = 50)
    private String environment;

    @Column(name = "key_id", nullable = false, length = 100)
    private String keyId;

    @Column(nullable = false, length = 30)
    private String status;

    /** 发布包完整 JSON（PublishPackage.toMap()）。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "activated_by", length = 100)
    private String activatedBy;

    public ActivePackageEntity() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPackageVersion() { return packageVersion; }
    public void setPackageVersion(String packageVersion) { this.packageVersion = packageVersion; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }

    public String getActivatedBy() { return activatedBy; }
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }
}
