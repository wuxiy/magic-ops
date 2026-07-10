package top.cywu.magicops.console.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.console.dto.*;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.publish.PackageBuildService;
import top.cywu.magicops.console.publish.PushService;
import top.cywu.magicops.console.repository.ScriptRepository;
import top.cywu.magicops.console.service.ScriptLifecycleService;
import top.cywu.magicops.sign.model.PublishPackage;

import java.util.List;
import java.util.Map;

/**
 * 脚本管理 REST API。最小 Console API，用于驱动脚本生命周期闭环。
 */
@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptLifecycleService lifecycleService;
    private final ScriptRepository scriptRepository;
    private final PackageBuildService packageBuildService;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    @Value("${magicops.runtime.url:http://localhost:8081}")
    private String runtimeUrl;

    public ScriptController(ScriptLifecycleService lifecycleService,
                            ScriptRepository scriptRepository,
                            PackageBuildService packageBuildService,
                            PushService pushService) {
        this.lifecycleService = lifecycleService;
        this.scriptRepository = scriptRepository;
        this.packageBuildService = packageBuildService;
        this.pushService = pushService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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

    /**
     * 构建、签名并推送发布包到 Runtime。
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> scriptIdNumbers = (List<Number>) request.get("scriptIds");
            List<Long> scriptIds = scriptIdNumbers.stream().map(Number::longValue).toList();
            String environment = (String) request.getOrDefault("environment", "development");

            PublishPackage pkg = packageBuildService.buildAndSign(scriptIds, environment, "publisher");
            String packageJson = objectMapper.writeValueAsString(pkg.toMap());
            boolean pushed = pushService.push(runtimeUrl, packageJson);

            if (pushed) {
                return ResponseEntity.ok(Map.of(
                        "status", "published",
                        "scriptIds", scriptIds,
                        "environment", environment,
                        "runtimeUrl", runtimeUrl));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "status", "push_failed",
                        "runtimeUrl", runtimeUrl));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error", "message", e.getMessage()));
        }
    }
}
