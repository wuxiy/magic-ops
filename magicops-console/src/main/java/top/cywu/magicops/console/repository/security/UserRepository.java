package top.cywu.magicops.console.repository.security;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.security.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
