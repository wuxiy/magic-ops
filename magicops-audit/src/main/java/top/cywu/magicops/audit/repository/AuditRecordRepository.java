package top.cywu.magicops.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.cywu.magicops.audit.entity.AuditRecordEntity;

import java.time.Instant;
import java.util.List;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, Long> {

    Page<AuditRecordEntity> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);

    Page<AuditRecordEntity> findByEventType(String eventType, Pageable pageable);

    Page<AuditRecordEntity> findByEventTimestampBetween(Instant from, Instant to, Pageable pageable);

    Page<AuditRecordEntity> findByOperator(String operator, Pageable pageable);

    List<AuditRecordEntity> findByEntityTypeAndEntityId(String entityType, String entityId);

    Page<AuditRecordEntity> findByCriticalTrue(Pageable pageable);
}
