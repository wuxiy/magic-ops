package top.cywu.magicops.console.api.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.entity.config.HttpTargetEntity;
import top.cywu.magicops.console.repository.config.HttpTargetRepository;
import top.cywu.magicops.console.repository.config.ProjectRepository;
import top.cywu.magicops.core.model.Permissions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP 目标管理 REST API。供 Console 及 magic-editor 插件管理 HTTP 适配目标。
 */
@RestController
@RequestMapping("/api/httptargets")
@PreAuthorize("hasAuthority('" + Permissions.HTTP_TARGET_MANAGE + "')")
public class HttpTargetController {

    private final HttpTargetRepository repository;
    private final ProjectRepository projectRepository;

    public HttpTargetController(HttpTargetRepository repository, ProjectRepository projectRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public ResponseEntity<List<HttpTargetEntity>> list(@RequestParam(required = false) Long projectId) {
        List<HttpTargetEntity> targets = projectId != null
                ? repository.findByProjectId(projectId)
                : repository.findAll();
        return ResponseEntity.ok(targets);
    }

    @PostMapping
    public ResponseEntity<HttpTargetEntity> create(@RequestBody HttpTargetEntity request) {
        Instant now = Instant.now();
        request.setId(null);
        if (request.getProjectId() == null) {
            Long defaultProjectId = projectRepository.findByCode("default")
                    .map(p -> p.getId())
                    .orElseThrow(() -> new IllegalArgumentException("默认项目不存在，无法创建 HTTP 目标"));
            request.setProjectId(defaultProjectId);
        }
        if (request.getTargetId() == null || request.getTargetId().isBlank()) {
            request.setTargetId("ht-" + UUID.randomUUID().toString().substring(0, 8));
        }
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HttpTargetEntity> update(@PathVariable Long id, @RequestBody HttpTargetEntity request) {
        return repository.findById(id)
                .map(existing -> {
                    if (request.getName() != null) existing.setName(request.getName());
                    if (request.getBaseUrl() != null) existing.setBaseUrl(request.getBaseUrl());
                    if (request.getAllowedPaths() != null) existing.setAllowedPaths(request.getAllowedPaths());
                    if (request.getAuthType() != null) existing.setAuthType(request.getAuthType());
                    if (request.getTargetId() != null) existing.setTargetId(request.getTargetId());
                    existing.setRequireEncryption(request.isRequireEncryption());
                    existing.setEnabled(request.isEnabled());
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(repository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
