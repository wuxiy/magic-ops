package top.cywu.magicops.console.api.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.entity.config.DataSourceEntity;
import top.cywu.magicops.console.entity.config.ProjectEntity;
import top.cywu.magicops.console.repository.config.DataSourceRepository;
import top.cywu.magicops.console.repository.config.ProjectRepository;
import top.cywu.magicops.core.model.Permissions;

import java.time.Instant;
import java.util.List;

/**
 * 数据源管理 REST API（切片 36）。供 Console 管理业务数据源，打通只读灰度前置。
 *
 * <p>安全模型：{@code passwordRef} 引用密钥（不落明文密码），Runtime 侧解析为真实凭据。
 */
@RestController
@RequestMapping("/api/datasources")
@PreAuthorize("hasAuthority('" + Permissions.DATASOURCE_MANAGE + "')")
public class DataSourceController {

    private final DataSourceRepository repository;
    private final ProjectRepository projectRepository;

    public DataSourceController(DataSourceRepository repository, ProjectRepository projectRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public ResponseEntity<List<DataSourceEntity>> list(@RequestParam(required = false) Long projectId) {
        List<DataSourceEntity> sources = projectId != null
                ? repository.findByProjectId(projectId)
                : repository.findAll();
        return ResponseEntity.ok(sources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataSourceEntity> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DataSourceEntity> create(@RequestBody DataSourceEntity request) {
        Instant now = Instant.now();
        request.setId(null);
        if (request.getProjectId() == null) {
            Long defaultProjectId = projectRepository.findByCode("default")
                    .map(ProjectEntity::getId)
                    .orElseThrow(() -> new IllegalArgumentException("默认项目不存在，无法创建数据源"));
            request.setProjectId(defaultProjectId);
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("数据源 name 不能为空");
        }
        if (request.getJdbcUrl() == null || request.getJdbcUrl().isBlank()) {
            throw new IllegalArgumentException("数据源 jdbcUrl 不能为空");
        }
        if (request.getDriverClass() == null || request.getDriverClass().isBlank()) {
            request.setDriverClass(inferDriverClass(request.getJdbcUrl()));
        }
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataSourceEntity> update(@PathVariable Long id, @RequestBody DataSourceEntity request) {
        return repository.findById(id)
                .map(existing -> {
                    if (request.getName() != null) existing.setName(request.getName());
                    if (request.getDisplayName() != null) existing.setDisplayName(request.getDisplayName());
                    if (request.getDriverClass() != null) existing.setDriverClass(request.getDriverClass());
                    if (request.getJdbcUrl() != null) existing.setJdbcUrl(request.getJdbcUrl());
                    if (request.getUsername() != null) existing.setUsername(request.getUsername());
                    if (request.getPasswordRef() != null) existing.setPasswordRef(request.getPasswordRef());
                    existing.setMaxPoolSize(request.getMaxPoolSize());
                    existing.setReadOnly(request.isReadOnly());
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

    /** 按 jdbcUrl 前缀推断驱动类，避免调用方必填。 */
    private String inferDriverClass(String jdbcUrl) {
        if (jdbcUrl == null) return "org.postgresql.Driver";
        if (jdbcUrl.startsWith("jdbc:h2:")) return "org.h2.Driver";
        if (jdbcUrl.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (jdbcUrl.startsWith("jdbc:dm:")) return "dm.jdbc.driver.DmDriver";
        if (jdbcUrl.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (jdbcUrl.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        return "org.postgresql.Driver";
    }
}
