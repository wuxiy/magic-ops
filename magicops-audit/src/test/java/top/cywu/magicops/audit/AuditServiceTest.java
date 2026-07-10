package top.cywu.magicops.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.cywu.magicops.audit.masking.AuditMaskingService;
import top.cywu.magicops.audit.model.AuditEventType;
import top.cywu.magicops.audit.model.AuditRecord;
import top.cywu.magicops.audit.service.AuditService;
import top.cywu.magicops.audit.service.AuditWriteException;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审计服务测试。覆盖写入、查询、脱敏和关键审计阻断。
 */
class AuditServiceTest {

    private AuditService auditService;
    private AuditMaskingService maskingService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService();
        maskingService = new AuditMaskingService();
    }

    @Test
    void write_andFindAll() {
        AuditRecord record = AuditRecord.of(
                AuditEventType.SCRIPT_CREATED, "Script", "1", "admin", Map.of("name", "test"));
        auditService.write(record);

        var all = auditService.findAll();
        assertEquals(1, all.size());
        assertEquals("SCRIPT_CREATED", all.get(0).eventType().name());
    }

    @Test
    void write_criticalRecord() {
        AuditRecord record = AuditRecord.critical(
                AuditEventType.APPROVAL_DECIDED, "Approval", "1", "reviewer",
                Map.of("decision", "APPROVED"));
        auditService.write(record);

        var all = auditService.findAll();
        assertEquals(1, all.size());
        assertTrue(all.get(0).critical());
    }

    @Test
    void findByEntity_filtersCorrectly() {
        auditService.write(AuditRecord.of(AuditEventType.SCRIPT_CREATED, "Script", "1", "admin", Map.of()));
        auditService.write(AuditRecord.of(AuditEventType.SCRIPT_EXECUTED, "ScriptExecution", "2", "runtime", Map.of()));
        auditService.write(AuditRecord.of(AuditEventType.SCRIPT_STATUS_CHANGED, "Script", "1", "admin", Map.of()));

        var scriptRecords = auditService.findByEntity("Script", "1");
        assertEquals(2, scriptRecords.size());

        var execRecords = auditService.findByEntity("ScriptExecution", "2");
        assertEquals(1, execRecords.size());
    }

    // ---- 脱敏测试 ----

    @Test
    void mask_phoneNumber() {
        assertEquals("138****1234", maskingService.mask("13812341234"));
    }

    @Test
    void mask_idCard() {
        assertEquals("110101********1234", maskingService.mask("110101199001011234"));
    }

    @Test
    void mask_dbUrl() {
        String url = "jdbc:postgresql://localhost/db?user=admin&password=secret123&ssl=true";
        String masked = maskingService.mask(url);
        assertFalse(masked.contains("secret123"), "密码不应出现在脱敏结果中");
        assertTrue(masked.contains("password=***"));
    }

    @Test
    void maskDetails_sensitiveKeys() {
        Map<String, Object> details = Map.of(
                "scriptId", "1",
                "password", "my-secret",
                "token", "abc123",
                "sqlSummary", "SELECT 1"
        );

        Map<String, Object> masked = maskingService.maskDetails(details);
        assertEquals("***", masked.get("password"));
        assertEquals("***", masked.get("token"));
        assertEquals("1", masked.get("scriptId"));
        assertEquals("SELECT 1", masked.get("sqlSummary"));
    }

    @Test
    void maskDetails_nullInput() {
        assertNull(maskingService.maskDetails(null));
        assertNull(maskingService.mask(null));
    }
}
