package top.cywu.magicops.runtime.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.runtime.packageMgmt.PackageRejectedException;
import top.cywu.magicops.runtime.packageMgmt.PackageVerificationService;
import top.cywu.magicops.runtime.packageMgmt.ResolvedScript;
import top.cywu.magicops.runtime.packageMgmt.ScriptResolver;
import top.cywu.magicops.sign.model.PublishPackage;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP 接口适配 API。
 *
 * <p>切片 34 起改为脚本引用模式：请求携带 {@code scriptId}，目标与路径来自激活包中
 * 已签名的适配脚本（内容为 JSON {@code {targetId,path,method}}），请求只提供 body。
 * 拒绝裸目标调用。
 */
@RestController
@RequestMapping("/api/adapter")
public class HttpAdapterController {

    private static final Logger log = LoggerFactory.getLogger(HttpAdapterController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpAdapterService adapterService;
    private final PackageVerificationService verificationService;
    private final ScriptResolver scriptResolver;

    public HttpAdapterController(HttpAdapterService adapterService,
                                 PackageVerificationService verificationService,
                                 ScriptResolver scriptResolver) {
        this.adapterService = adapterService;
        this.verificationService = verificationService;
        this.scriptResolver = scriptResolver;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, String> request) {
        PublishPackage activePackage = verificationService.getActivePackage();
        if (activePackage == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "没有已激活的发布包"));
        }

        String scriptId = request.get("scriptId");
        if (scriptId == null || scriptId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "scriptId 参数不能为空（切片 34 起拒绝裸目标调用）"));
        }

        ResolvedScript resolved;
        try {
            resolved = scriptResolver.resolve(activePackage, scriptId);
        } catch (PackageRejectedException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        // 适配脚本内容为 JSON：声明目标、路径、方法（随包签名）
        String targetId;
        String path;
        String method;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> def = MAPPER.readValue(resolved.contentAsString(), Map.class);
            targetId = def.get("targetId");
            path = def.get("path");
            method = def.getOrDefault("method", "GET");
        } catch (Exception e) {
            log.warn("adapter_binding_parse_failed scriptId={} reason={}", scriptId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "适配脚本定义解析失败"));
        }

        if (targetId == null || path == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "适配脚本缺少 targetId/path"));
        }

        String traceId = request.getOrDefault("traceId", UUID.randomUUID().toString());
        String body = request.get("body");

        AdapterResult result = adapterService.execute(activePackage, targetId, path, method, body, traceId);
        return ResponseEntity.ok(result);
    }
}
