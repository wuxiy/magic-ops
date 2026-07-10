package top.cywu.magicops.console.entity.security;

import jakarta.persistence.*;

/**
 * 权限 JPA 实体。
 */
@Entity
@Table(name = "permissions")
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(length = 50)
    private String category;

    public PermissionEntity() {
    }

    public PermissionEntity(String code, String displayName, String resourceType, String category) {
        this.code = code;
        this.displayName = displayName;
        this.resourceType = resourceType;
        this.category = category;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
