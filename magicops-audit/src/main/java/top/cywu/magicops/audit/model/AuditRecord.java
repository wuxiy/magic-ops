package top.cywu.magicops.audit.model;

import java.time.Instant;
import java.util.Map;

/**
 * 审计记录值对象。所有审计事件的统一载体。
 *
 * <p>高风险操作的关键审计写入失败必须阻断执行。
 */
public record AuditRecord(
        String traceId,
        AuditEventType eventType,
        String entityType,
        String entityId,
        String operator,
        Instant timestamp,
        boolean critical,
        Map<String, Object> details
) {

    /**
     * 创建一条审计记录。
     */
    public static AuditRecord of(AuditEventType eventType, String entityType, String entityId,
                                 String operator, Map<String, Object> details) {
        return new AuditRecord(
                null,
                eventType,
                entityType,
                entityId,
                operator,
                Instant.now(),
                false,
                details
        );
    }

    /**
     * 创建一条关键审计记录。写入失败时必须阻断执行。
     */
    public static AuditRecord critical(AuditEventType eventType, String entityType, String entityId,
                                       String operator, Map<String, Object> details) {
        return new AuditRecord(
                null,
                eventType,
                entityType,
                entityId,
                operator,
                Instant.now(),
                true,
                details
        );
    }
}
