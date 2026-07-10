package top.cywu.magicops.audit.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.audit.entity.AuditRecordEntity;
import top.cywu.magicops.audit.repository.AuditRecordRepository;

import java.time.Instant;

/**
 * 审计查询 REST API。支持分页和按实体/事件/时间过滤。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditRecordRepository repository;

    public AuditController(AuditRecordRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<Page<AuditRecordEntity>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(repository.findAll(
                PageRequest.of(page, size, Sort.by("eventTimestamp").descending())));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<Page<AuditRecordEntity>> findByEntity(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(repository.findByEntityTypeAndEntityId(
                entityType, entityId,
                PageRequest.of(page, size, Sort.by("eventTimestamp").descending())));
    }

    @GetMapping("/event/{eventType}")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<Page<AuditRecordEntity>> findByEventType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(repository.findByEventType(
                eventType,
                PageRequest.of(page, size, Sort.by("eventTimestamp").descending())));
    }

    @GetMapping("/time")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<Page<AuditRecordEntity>> findByTimeRange(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(repository.findByEventTimestampBetween(
                Instant.parse(from), Instant.parse(to),
                PageRequest.of(page, size, Sort.by("eventTimestamp").descending())));
    }

    @GetMapping("/critical")
    @PreAuthorize("hasAuthority('audit:read')")
    public ResponseEntity<Page<AuditRecordEntity>> findCritical(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(repository.findByCriticalTrue(
                PageRequest.of(page, size, Sort.by("eventTimestamp").descending())));
    }
}
