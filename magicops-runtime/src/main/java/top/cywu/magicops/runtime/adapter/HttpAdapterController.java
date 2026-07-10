package top.cywu.magicops.runtime.adapter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.runtime.packageMgmt.PackageVerificationService;
import top.cywu.magicops.sign.model.PublishPackage;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP 接口适配 API。
 */
@RestController
@RequestMapping("/api/adapter")
public class HttpAdapterController {

    private final HttpAdapterService adapterService;
    private final PackageVerificationService verificationService;

    public HttpAdapterController(HttpAdapterService adapterService,
                                 PackageVerificationService verificationService) {
        this.adapterService = adapterService;
        this.verificationService = verificationService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, String> request) {
        PublishPackage activePackage = verificationService.getActivePackage();
        if (activePackage == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "没有已激活的发布包"));
        }

        String targetId = request.get("targetId");
        String path = request.get("path");
        String method = request.getOrDefault("method", "GET");
        String body = request.get("body");
        String traceId = request.getOrDefault("traceId", UUID.randomUUID().toString());

        if (targetId == null || path == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "targetId 和 path 参数不能为空"));
        }

        AdapterResult result = adapterService.execute(activePackage, targetId, path,
                method, body, traceId);

        return ResponseEntity.ok(result);
    }
}
