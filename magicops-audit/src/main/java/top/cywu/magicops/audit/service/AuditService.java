package top.cywu.magicops.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.cywu.magicops.audit.model.AuditRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 审计服务。第一阶段最小实现：内存存储 + 日志输出。
 *
 * <p>后续切片将接入持久化存储。高风险操作的关键审计写入失败时必须阻断执行。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final List<AuditRecord> records = Collections.synchronizedList(new ArrayList<>());

    /**
     * 写入审计记录。关键审计记录写入失败时抛出异常以阻断执行。
     *
     * @param record 审计记录
     * @throws AuditWriteException 关键审计写入失败时抛出
     */
    public void write(AuditRecord record) {
        try {
            records.add(record);
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
     * 查询所有审计记录（第一阶段最小实现）。
     */
    public List<AuditRecord> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /**
     * 按实体类型和 ID 查询审计记录。
     */
    public List<AuditRecord> findByEntity(String entityType, String entityId) {
        return records.stream()
                .filter(r -> entityType.equals(r.entityType()) && entityId.equals(r.entityId()))
                .toList();
    }
}
