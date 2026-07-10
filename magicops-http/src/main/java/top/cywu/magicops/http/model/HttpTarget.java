package top.cywu.magicops.http.model;

import java.util.List;
import java.util.Map;

/**
 * HTTP 目标系统。脚本只能调用已注册的 HTTP 目标。
 */
public record HttpTarget(
        String id,
        String name,
        String baseUrl,
        List<String> allowedPaths,
        String authType,
        Map<String, String> authConfig,
        boolean requireEncryption,
        String cryptoKeyId
) {

    /**
     * 检查给定路径是否在该目标的 allowlist 内。
     */
    public boolean isPathAllowed(String path) {
        if (path == null) return false;
        if (allowedPaths == null || allowedPaths.isEmpty()) return false;
        for (String allowed : allowedPaths) {
            if (path.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }
}
