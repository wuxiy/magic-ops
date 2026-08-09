package top.cywu.magicops.runtime.packageMgmt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 激活发布包仓储（切片 33）。
 */
public interface ActivePackageRepository extends JpaRepository<ActivePackageEntity, Long> {

    /** 最近一条指定状态的激活记录（按激活时间倒序）。 */
    Optional<ActivePackageEntity> findFirstByStatusOrderByActivatedAtDesc(String status);

    /** 全部指定状态的记录。 */
    List<ActivePackageEntity> findByStatus(String status);
}
