package top.cywu.magicops.console.api.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.repository.ApprovalRepository;

/**
 * 审批管理 REST API。提供分页查询。
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalRepository approvalRepository;

    public ApprovalController(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('script:approve', 'audit:read', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<Page<ApprovalEntity>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String decision) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("submittedAt").descending());

        Page<ApprovalEntity> result;
        if (decision != null && !decision.isBlank()) {
            result = approvalRepository.findByDecision(decision, pageRequest);
        } else {
            result = approvalRepository.findAll(pageRequest);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('script:approve', 'audit:read', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApprovalEntity> get(@PathVariable Long id) {
        return approvalRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
