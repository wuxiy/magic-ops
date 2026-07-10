package top.cywu.magicops.console.repository.security;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.security.ResourcePermissionEntity;

import java.util.List;

public interface ResourcePermissionRepository extends JpaRepository<ResourcePermissionEntity, Long> {

    List<ResourcePermissionEntity> findByUserIdAndResourceType(Long userId, String resourceType);

    List<ResourcePermissionEntity> findByRoleIdAndResourceType(Long roleId, String resourceType);

    List<ResourcePermissionEntity> findByResourceTypeAndResourceId(String resourceType, String resourceId);
}
