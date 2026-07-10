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
}
