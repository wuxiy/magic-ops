package top.cywu.magicops.console.repository.config;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.console.entity.config.PublishPackageEntity;

import java.util.List;

public interface PublishPackageRepository extends JpaRepository<PublishPackageEntity, Long> {

    List<PublishPackageEntity> findByProjectCodeAndEnvironment(String projectCode, String environment);

    Page<PublishPackageEntity> findByProjectCodeAndEnvironment(String projectCode, String environment, Pageable pageable);

    Page<PublishPackageEntity> findByProjectCode(String projectCode, Pageable pageable);

    List<PublishPackageEntity> findByStatus(String status);
}
