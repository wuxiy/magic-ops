package top.cywu.magicops.console.repository.security;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.security.UserRoleEntity;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {

    List<UserRoleEntity> findByUserId(Long userId);

    List<UserRoleEntity> findByRoleId(Long roleId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
}
