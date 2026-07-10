package top.cywu.magicops.console.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.config.EnvironmentEntity;

import java.util.List;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, Long> {

    List<EnvironmentEntity> findByProjectId(Long projectId);

    List<EnvironmentEntity> findByProjectIdAndEnabled(Long projectId, boolean enabled);
}
