package top.cywu.magicops.console.api.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.cywu.magicops.core.model.Permissions;
import top.cywu.magicops.sign.key.KeyRotationService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 密钥管理 REST API。查看/轮换/移除密钥。
 */
@RestController
@RequestMapping("/api/keys")
@PreAuthorize("hasAuthority('" + Permissions.KEY_MANAGE + "')")
public class KeyManagementController {

    private final KeyRotationService keyRotationService;

    public KeyManagementController(KeyRotationService keyRotationService) {
        this.keyRotationService = keyRotationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listKeys() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeKeyId", keyRotationService.getActiveKeyId());
        result.put("allKeyIds", keyRotationService.getAllKeyIds());
        result.put("deprecatedKeyIds", keyRotationService.getDeprecatedKeyIds());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rotate")
    public ResponseEntity<Map<String, String>> rotate() {
        String newKeyId = keyRotationService.rotate();
        return ResponseEntity.ok(Map.of(
                "newKeyId", newKeyId,
                "status", "rotated"));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Map<String, String>> remove(@PathVariable String keyId) {
        keyRotationService.removeDeprecated(keyId);
        return ResponseEntity.ok(Map.of(
                "keyId", keyId,
                "status", "removed"));
    }

    @GetMapping("/{keyId}/status")
    public ResponseEntity<Map<String, String>> status(@PathVariable String keyId) {
        KeyRotationService.KeyStatus status = keyRotationService.getKeyStatus(keyId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "keyId", keyId,
                "status", status.name()));
    }
}
