package top.cywu.magicops.runtime.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.repository.AuditRecordRepository;
import top.cywu.magicops.audit.service.AuditService;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Runtime 审计持久化测试（切片 33-a）。
 *
 * <p>覆盖关闭标准：Runtime 装配 JPA 后审计落库，而非内存回退。
 * 重启后关键执行审计仍可从数据库查询。
 */
@SpringBootTest
class RuntimeAuditPersistenceTest {

    @Autowired
    private AuditService auditService;

    @Autowired(required = false)
    private AuditRecordRepository repository;

    @Test
    void auditRepositoryBean_wired_notNull() {
        assertNotNull(repository, "AuditRecordRepository 必须装配，审计不得走内存回退");
    }

    @Test
    void criticalAuditRecord_persistedToDatabase_retrievable() {
        assertNotNull(repository, "前置：仓储已装配");
        String entityId = "persist-" + UUID.randomUUID();
        long before = repository.count();

        auditService.write(AuditRecord.critical(
                AuditEventType.PACKAGE_LOADED, "PersistTest", entityId, "tester",
                Map.of("reason", "slice-33-a")));

        assertEquals(before + 1, repository.count(), "关键审计记录应已落库");
        var found = auditService.findByEntity("PersistTest", entityId);
        assertFalse(found::isEmpty, "落库后应可按实体类型与 ID 查回");
        assertEquals("tester", found.get(0).operator());
    }
}
