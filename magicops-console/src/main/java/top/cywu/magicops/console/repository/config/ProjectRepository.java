package top.cywu.magicops.console.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.config.ProjectEntity;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    Optional<ProjectEntity> findByCode(String code);

    boolean existsByCode(String code);
}
