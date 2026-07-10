package top.cywu.magicops.console.repository.config;

import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.config.PublishPackageEntity;

import java.util.List;

public interface PublishPackageRepository extends JpaRepository<PublishPackageEntity, Long> {

    List<PublishPackageEntity> findByProjectCodeAndEnvironment(String projectCode, String environment);

    List<PublishPackageEntity> findByStatus(String status);
}
