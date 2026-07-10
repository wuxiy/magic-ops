package top.cywu.magicops.console.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.config.HttpTargetEntity;

import java.util.List;
import java.util.Optional;

public interface HttpTargetRepository extends JpaRepository<HttpTargetEntity, Long> {

    List<HttpTargetEntity> findByProjectId(Long projectId);

    Optional<HttpTargetEntity> findByProjectIdAndTargetId(Long projectId, String targetId);
}
