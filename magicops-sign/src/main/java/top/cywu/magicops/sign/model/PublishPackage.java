package top.cywu.magicops.sign.model;

import java.util.List;
import java.util.Map;

/**
 * 发布包。包含 manifest、脚本内容、元数据和签名。
 */
public record PublishPackage(
        PackageManifest manifest,
        Map<String, byte[]> scripts,
        Map<String, Object> metadata,
        Map<String, Object> policy,
        byte[] signature
) {

    /**
     * 转为可序列化的 Map（用于传输和存储）。
     */
    public Map<String, Object> toMap() {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("manifest", manifest.toMap());
        map.put("scripts", scripts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> java.util.Base64.getEncoder().encodeToString(e.getValue()))));
        map.put("metadata", metadata);
        map.put("policy", policy);
        map.put("signature", java.util.Base64.getEncoder().encodeToString(signature));
        return map;
    }

    /**
     * 从 Map 反序列化发布包（与 {@link #toMap()} 对称）。
     *
     * <p>用于接收推送和从持久化存储重载激活包。
     */
    @SuppressWarnings("unchecked")
    public static PublishPackage fromMap(Map<String, Object> body) {
        Map<String, Object> manifestMap = (Map<String, Object>) body.get("manifest");
        Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", Map.of());
        Map<String, Object> policy = (Map<String, Object>) body.getOrDefault("policy", Map.of());

        Map<String, String> scriptsB64 = (Map<String, String>) body.getOrDefault("scripts", Map.of());
        Map<String, byte[]> scripts = new java.util.LinkedHashMap<>();
        for (var entry : scriptsB64.entrySet()) {
            scripts.put(entry.getKey(), java.util.Base64.getDecoder().decode(entry.getValue()));
        }

        byte[] signature = java.util.Base64.getDecoder().decode((String) body.get("signature"));
        return new PublishPackage(PackageManifest.fromMap(manifestMap), scripts, metadata, policy, signature);
    }
}
