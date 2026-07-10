package top.cywu.magicops.console.entity.security;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 角色-权限关联 JPA 实体。
 */
@Entity
@Table(name = "role_permissions")
public class RolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public RolePermissionEntity() {
    }

    public RolePermissionEntity(Long roleId, Long permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.grantedAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }
}
