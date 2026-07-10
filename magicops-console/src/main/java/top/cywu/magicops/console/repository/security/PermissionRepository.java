package top.cywu.magicops.console.repository.security;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.security.PermissionEntity;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {

    Optional<PermissionEntity> findByCode(String code);

    List<PermissionEntity> findByCategory(String category);

    List<PermissionEntity> findByResourceType(String resourceType);
}
