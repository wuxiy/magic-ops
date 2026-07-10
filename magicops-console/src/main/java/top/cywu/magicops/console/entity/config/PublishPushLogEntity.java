package top.cywu.magicops.console.entity.config;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "publish_push_logs")
public class PublishPushLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Column(name = "runtime_url", length = 500)
    private String runtimeUrl;

    @Column(name = "push_status", length = 30)
    private String pushStatus;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "pushed_at")
    private Instant pushedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    public PublishPushLogEntity() {
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getRuntimeUrl() { return runtimeUrl; }
    public void setRuntimeUrl(String runtimeUrl) { this.runtimeUrl = runtimeUrl; }

    public String getPushStatus() { return pushStatus; }
    public void setPushStatus(String pushStatus) { this.pushStatus = pushStatus; }

    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public Instant getPushedAt() { return pushedAt; }
    public void setPushedAt(Instant pushedAt) { this.pushedAt = pushedAt; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
