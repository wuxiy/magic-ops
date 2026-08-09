package top.cywu.magicops.console.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.core.model.Permissions;
import top.cywu.magicops.console.dto.*;
import top.cywu.magicops.console.entity.ApprovalEntity;
import top.cywu.magicops.console.entity.ScriptEntity;
import top.cywu.magicops.console.publish.PackageBuildService;
import top.cywu.magicops.console.publish.PushService;
import top.cywu.magicops.console.repository.ScriptRepository;
import top.cywu.magicops.console.security.CurrentActor;
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
    @PreAuthorize("hasAuthority('" + Permissions.SCRIPT_CREATE + "')")
    public ResponseEntity<ScriptResponse> create(@Valid @RequestBody CreateScriptRequest request) {
        ScriptEntity script = lifecycleService.createScript(
                request.name(), request.projectCode(), request.scriptType(), CurrentActor.username());
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
    @PreAuthorize("hasAuthority('" + Permissions.SCRIPT_EDIT + "')")
    public ResponseEntity<Void> updateDraft(@PathVariable Long id,
                                            @RequestBody UpdateDraftRequest request) {
        lifecycleService.updateDraft(id, request.content(), request.routePath(), request.routeMethod());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('" + Permissions.SCRIPT_SUBMIT + "')")
    public ResponseEntity<Void> createVersion(@PathVariable Long id,
                                              @Valid @RequestBody CreateVersionRequest request) {
        lifecycleService.createVersion(id, request.version(), request.riskLevel(), CurrentActor.username());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('" + Permissions.SCRIPT_SUBMIT + "')")
    public ResponseEntity<Void> submit(@PathVariable Long id) {
        lifecycleService.submitForApproval(id, CurrentActor.username());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('" + Permissions.SCRIPT_APPROVE + "')")
    public ResponseEntity<ApprovalEntity> approve(@PathVariable Long id,
                                                  @Valid @RequestBody ApprovalRequest request) {
        ApprovalEntity approval = lifecycleService.decide(
                id, request.decision(), CurrentActor.username(), request.comment());
        return ResponseEntity.ok(approval);
    }

    /**
     * 构建、签名并推送发布包到 Runtime。
     */
    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('" + Permissions.SCRIPT_PUBLISH + "')")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> scriptIdNumbers = (List<Number>) request.get("scriptIds");
            List<Long> scriptIds = scriptIdNumbers.stream().map(Number::longValue).toList();
            String environment = (String) request.getOrDefault("environment", "development");

            PublishPackage pkg = packageBuildService.buildAndSign(scriptIds, environment, CurrentActor.username());
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

    /**
     * 下线当前激活的发布包（切片 38）。向 Runtime 推送下线指令，
     * Runtime 清空激活包并拒绝脚本执行。回滚不走此端点--回滚是重新发布上一个已签名包。
     */
    @PostMapping("/deactivate")
    @PreAuthorize("hasAuthority('" + Permissions.SCRIPT_PUBLISH + "')")
    public ResponseEntity<Map<String, Object>> deactivate(@RequestBody Map<String, Object> request) {
        try {
            String operator = CurrentActor.username();
            String reason = (String) request.getOrDefault("reason", "未提供");

            boolean deactivated = pushService.deactivate(runtimeUrl, operator, reason);

            if (deactivated) {
                return ResponseEntity.ok(Map.of(
                        "status", "deactivated",
                        "operator", operator,
                        "reason", reason,
                        "runtimeUrl", runtimeUrl));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "status", "deactivate_failed",
                        "runtimeUrl", runtimeUrl));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error", "message", e.getMessage()));
        }
    }
}
