package top.cywu.magicops.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.entity.AuditRecordEntity;
import top.cywu.magicops.audit.masking.AuditMaskingService;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.repository.AuditRecordRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 审计服务。支持持久化（PostgreSQL）和内存两种存储模式。
 *
 * <p>当 JPA Repository 可用时，审计记录写入数据库。否则回退到内存存储（用于单元测试）。
 * <p>高风险操作的关键审计写入失败时必须阻断执行。
 * <p>敏感数据在写入前经过脱敏处理。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired(required = false)
    private AuditRecordRepository repository;

    @Autowired(required = false)
    private AuditMaskingService maskingService;

    /** 内存回退存储（当 JPA 不可用时使用）。 */
    private final List<AuditRecord> memoryRecords = Collections.synchronizedList(new ArrayList<>());

    /**
     * 写入审计记录。关键审计记录写入失败时抛出异常以阻断执行。
     */
    public void write(AuditRecord record) {
        try {
            if (repository != null) {
                persistRecord(record);
            } else {
                memoryRecords.add(record);
            }
            log.info("audit event={} entity={}:{} operator={} critical={}",
                    record.eventType(), record.entityType(), record.entityId(),
                    record.operator(), record.critical());
        } catch (Exception e) {
            if (record.critical()) {
                throw new AuditWriteException("关键审计写入失败，执行已阻断", e);
            }
            log.error("audit_write_failed event={} entity={}:{}",
                    record.eventType(), record.entityType(), record.entityId(), e);
        }
    }

    /**
     * 查询所有审计记录。
     */
    public List<AuditRecord> findAll() {
        if (repository != null) {
            return repository.findAll().stream().map(this::toRecord).toList();
        }
        return Collections.unmodifiableList(new ArrayList<>(memoryRecords));
    }

    /**
     * 按实体类型和 ID 查询审计记录。
     */
    public List<AuditRecord> findByEntity(String entityType, String entityId) {
        if (repository != null) {
            return repository.findByEntityTypeAndEntityId(entityType, entityId).stream()
                    .map(this::toRecord).toList();
        }
        return memoryRecords.stream()
                .filter(r -> entityType.equals(r.entityType()) && entityId.equals(r.entityId()))
                .toList();
    }

    private void persistRecord(AuditRecord record) {
        AuditRecordEntity entity = new AuditRecordEntity();
        entity.setTraceId(record.traceId());
        entity.setEventType(record.eventType().name());
        entity.setEntityType(record.entityType());
        entity.setEntityId(record.entityId());
        entity.setOperator(record.operator());
        entity.setEventTimestamp(record.timestamp());
        entity.setCritical(record.critical());

        // 脱敏后序列化 details
        Map<String, Object> details = record.details();
        if (maskingService != null && details != null) {
            details = maskingService.maskDetails(details);
        }
        try {
            entity.setDetails(details != null ? MAPPER.writeValueAsString(details) : null);
        } catch (JsonProcessingException e) {
            entity.setDetails("{}");
        }

        repository.save(entity);
    }

    private AuditRecord toRecord(AuditRecordEntity entity) {
        Map<String, Object> details = Map.of();
        if (entity.getDetails() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = MAPPER.readValue(entity.getDetails(), Map.class);
                details = parsed;
            } catch (JsonProcessingException e) {
                details = Map.of("raw", entity.getDetails());
            }
        }
        return new AuditRecord(
                entity.getTraceId(),
                top.cywu.magicops.audit.model.AuditEventType.valueOf(entity.getEventType()),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getOperator(),
                entity.getEventTimestamp(),
                entity.isCritical(),
                details
        );
    }
}
