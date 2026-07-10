package top.cywu.magicops.console.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.dto.*;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.entity.ScriptVersionEntity;
import top.cywu.magicops.console.repository.ScriptRepository;
import top.cywu.magicops.console.service.ScriptLifecycleService;

import java.util.List;

/**
 * 脚本管理 REST API。最小 Console API，用于驱动脚本生命周期闭环。
 */
@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptLifecycleService lifecycleService;
    private final ScriptRepository scriptRepository;

    public ScriptController(ScriptLifecycleService lifecycleService,
                            ScriptRepository scriptRepository) {
        this.lifecycleService = lifecycleService;
        this.scriptRepository = scriptRepository;
    }

    @PostMapping
    public ResponseEntity<ScriptResponse> create(@Valid @RequestBody CreateScriptRequest request) {
        ScriptEntity script = lifecycleService.createScript(
                request.name(), request.projectCode(), request.scriptType(), "system");
        return ResponseEntity.status(HttpStatus.CREATED).body(ScriptResponse.from(script));
    }

    @GetMapping
    public ResponseEntity<List<ScriptResponse>> list() {
        List<ScriptResponse> scripts = scriptRepository.findAll().stream()
                .map(ScriptResponse::from)
                .toList();
        return ResponseEntity.ok(scripts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScriptResponse> get(@PathVariable Long id) {
        ScriptEntity script = scriptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("脚本不存在: id=" + id));
        return ResponseEntity.ok(ScriptResponse.from(script));
    }

    @PutMapping("/{id}/draft")
    public ResponseEntity<Void> updateDraft(@PathVariable Long id,
                                            @RequestBody UpdateDraftRequest request) {
        lifecycleService.updateDraft(id, request.content(), request.routePath(), request.routeMethod());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<Void> createVersion(@PathVariable Long id,
                                              @Valid @RequestBody CreateVersionRequest request) {
        lifecycleService.createVersion(id, request.version(), request.riskLevel(), "system");
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<Void> submit(@PathVariable Long id) {
        lifecycleService.submitForApproval(id, "system");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalEntity> approve(@PathVariable Long id,
                                                  @Valid @RequestBody ApprovalRequest request) {
        ApprovalEntity approval = lifecycleService.decide(
                id, request.decision(), "reviewer", request.comment());
        return ResponseEntity.ok(approval);
    }
}
