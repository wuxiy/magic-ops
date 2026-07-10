package top.cywu.magicops.console.repository.security;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.security.RoleEntity;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    Optional<RoleEntity> findByName(String name);

    boolean existsByName(String name);
}
