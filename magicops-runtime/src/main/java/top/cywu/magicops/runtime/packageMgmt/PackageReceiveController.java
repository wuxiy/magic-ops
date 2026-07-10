package top.cywu.magicops.runtime.packageMgmt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.sign.canonical.CanonicalJson;
import top.cywu.magicops.sign.model.PackageManifest;
import top.cywu.magicops.sign.model.PublishPackage;

import java.time.Instant;
import java.util.*;

/**
 * Runtime 发布包接收 API。
 *
 * <p>Runtime 不暴露编辑端点。此 API 仅用于接收 Console 推送的已签名发布包。
 */
@RestController
@RequestMapping("/api/packages")
public class PackageReceiveController {

    private static final Logger log = LoggerFactory.getLogger(PackageReceiveController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PackageVerificationService verificationService;

    public PackageReceiveController(PackageVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody Map<String, Object> body) {
        try {
            PublishPackage pkg = deserializePackage(body);
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

    @SuppressWarnings("unchecked")
    private PublishPackage deserializePackage(Map<String, Object> body) {
        Map<String, Object> manifestMap = (Map<String, Object>) body.get("manifest");
        Map<String, Object> metadataMap = (Map<String, Object>) body.getOrDefault("metadata", Map.of());
        Map<String, Object> policyMap = (Map<String, Object>) body.getOrDefault("policy", Map.of());

        // 解析 scripts
        Map<String, String> scriptsB64 = (Map<String, String>) body.getOrDefault("scripts", Map.of());
        Map<String, byte[]> scripts = new LinkedHashMap<>();
        for (var entry : scriptsB64.entrySet()) {
            scripts.put(entry.getKey(), Base64.getDecoder().decode(entry.getValue()));
        }

        // 解析 signature
        byte[] signature = Base64.getDecoder().decode((String) body.get("signature"));

        // 解析 manifest scripts
        List<Map<String, Object>> scriptMaps = (List<Map<String, Object>>) manifestMap.getOrDefault("scripts", List.of());
        List<PackageManifest.ScriptEntry> entries = scriptMaps.stream()
                .map(m -> new PackageManifest.ScriptEntry(
                        (String) m.get("scriptId"),
                        (String) m.get("path"),
                        (String) m.get("method"),
                        (String) m.get("version"),
                        (String) m.get("scenario"),
                        (String) m.get("riskLevel"),
                        (String) m.get("contentHash"),
                        (String) m.get("metadataHash")
                ))
                .toList();

        PackageManifest manifest = new PackageManifest(
                (String) manifestMap.get("projectCode"),
                (String) manifestMap.get("environment"),
                (String) manifestMap.get("packageVersion"),
                (String) manifestMap.get("runtimeVersion"),
                (String) manifestMap.get("publishedBy"),
                Instant.parse((String) manifestMap.get("publishedAt")),
                (String) manifestMap.get("keyId"),
                entries,
                (String) manifestMap.get("metadataHash"),
                (String) manifestMap.get("policyHash"),
                (String) manifestMap.get("signAlg"),
                (String) manifestMap.get("signature")
        );

        return new PublishPackage(manifest, scripts, metadataMap, policyMap, signature);
    }
}
