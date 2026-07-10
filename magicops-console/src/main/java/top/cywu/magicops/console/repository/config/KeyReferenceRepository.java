package top.cywu.magicops.console.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.config.KeyReferenceEntity;

import java.util.Optional;

public interface KeyReferenceRepository extends JpaRepository<KeyReferenceEntity, Long> {

    Optional<KeyReferenceEntity> findByKeyId(String keyId);

    boolean existsByKeyId(String keyId);
}
