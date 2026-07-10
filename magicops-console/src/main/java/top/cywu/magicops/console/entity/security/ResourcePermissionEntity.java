package top.cywu.magicops.console.entity.security;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 资源级权限 JPA 实体。支持按具体资源实例控制访问。
 */
@Entity
@Table(name = "resource_permissions")
public class ResourcePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private String resourceId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by", length = 100)
    private String grantedBy;

    public ResourcePermissionEntity() {
    }

    public ResourcePermissionEntity(Long userId, Long roleId, String resourceType,
                                    String resourceId, String permissionCode, String grantedBy) {
        this.userId = userId;
        this.roleId = roleId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.permissionCode = permissionCode;
        this.grantedBy = grantedBy;
        this.grantedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }

    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
}
