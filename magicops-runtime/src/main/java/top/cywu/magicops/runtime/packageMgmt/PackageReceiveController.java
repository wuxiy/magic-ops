package top.cywu.magicops.runtime.packageMgmt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.sign.model.PublishPackage;

import java.time.Instant;
import java.util.Map;

/**
 * Runtime 发布包接收 API。
 *
 * <p>Runtime 不暴露编辑端点。此 API 仅用于接收 Console 推送的已签名发布包。
 */
@RestController
@RequestMapping("/api/packages")
public class PackageReceiveController {

    private static final Logger log = LoggerFactory.getLogger(PackageReceiveController.class);

    private final PackageVerificationService verificationService;

    public PackageReceiveController(PackageVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(@RequestBody Map<String, Object> body) {
        try {
            PublishPackage pkg = PublishPackage.fromMap(body);
            verificationService.verifyAndActivate(pkg);

            return ResponseEntity.ok(Map.of(
                    "status", "accepted",
                    "version", pkg.manifest().packageVersion(),
                    "timestamp", Instant.now().toString()
            ));
        } catch (PackageRejectedException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("status", "rejected", "reason", e.getMessage()));
        } catch (Exception e) {
            log.error("package_receive_error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "内部错误"));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> active() {
        PublishPackage pkg = verificationService.getActivePackage();
        if (pkg == null) {
            return ResponseEntity.ok(Map.of("status", "none"));
        }
        return ResponseEntity.ok(Map.of(
                "status", "active",
                "version", pkg.manifest().packageVersion(),
                "environment", pkg.manifest().environment(),
                "scriptCount", pkg.manifest().scripts().size()
        ));
    }

    /**
     * 下线当前激活的发布包（切片 33-d）。
     *
     * <p>由 Console 推送，受共享密钥认证保护。下线后 Runtime 拒绝脚本执行。
     */
    @PostMapping("/deactivate")
    public ResponseEntity<Map<String, Object>> deactivate(@RequestBody Map<String, Object> body) {
        String operator = body.getOrDefault("operator", "unknown").toString();
        String reason = body.getOrDefault("reason", "未提供").toString();
        String version = verificationService.deactivate(operator, reason);
        return ResponseEntity.ok(Map.of(
                "status", "deactivated",
                "version", version != null ? version : "none",
                "timestamp", Instant.now().toString()
        ));
    }
}
