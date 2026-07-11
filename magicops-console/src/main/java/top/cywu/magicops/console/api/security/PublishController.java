package top.cywu.magicops.console.api.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.entity.config.PublishPackageEntity;
import top.cywu.magicops.console.repository.config.PublishPackageRepository;

/**
 * 发布历史 REST API。提供分页查询。
 */
@RestController
@RequestMapping("/api/publish")
public class PublishController {

    private final PublishPackageRepository publishPackageRepository;

    public PublishController(PublishPackageRepository publishPackageRepository) {
        this.publishPackageRepository = publishPackageRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('script:publish', 'audit:read', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<Page<PublishPackageEntity>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String environment) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("publishedAt").descending());

        Page<PublishPackageEntity> result;
        if (projectCode != null && environment != null) {
            result = publishPackageRepository.findByProjectCodeAndEnvironment(
                    projectCode, environment, pageRequest);
        } else if (projectCode != null) {
            result = publishPackageRepository.findByProjectCode(projectCode, pageRequest);
        } else {
            result = publishPackageRepository.findAll(pageRequest);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('script:publish', 'audit:read', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<PublishPackageEntity> get(@PathVariable Long id) {
        return publishPackageRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
