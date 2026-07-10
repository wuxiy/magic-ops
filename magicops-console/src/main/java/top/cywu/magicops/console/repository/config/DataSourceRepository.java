package top.cywu.magicops.console.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.config.DataSourceEntity;

import java.util.List;
import java.util.Optional;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, Long> {

    List<DataSourceEntity> findByProjectId(Long projectId);

    Optional<DataSourceEntity> findByProjectIdAndName(Long projectId, String name);
}
