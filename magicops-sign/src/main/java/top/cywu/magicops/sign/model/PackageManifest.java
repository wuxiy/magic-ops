package top.cywu.magicops.sign.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 发布包 Manifest。第一阶段必需字段。
 *
 * <p>字段规范来自 {@code docs/architecture/release-and-runtime.md}。
 */
public record PackageManifest(
        String projectCode,
        String environment,
        String packageVersion,
        String runtimeVersion,
        String publishedBy,
        Instant publishedAt,
        String keyId,
        List<ScriptEntry> scripts,
        String metadataHash,
        String policyHash,
        String signAlg,
        String signature
) {

    /**
     * 脚本条目。
     */
    public record ScriptEntry(
            String scriptId,
            String path,
            String method,
            String version,
            String scenario,
            String riskLevel,
            String contentHash,
            String metadataHash
    ) {
        public Map<String, Object> toMap() {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("contentHash", contentHash);
            map.put("metadataHash", metadataHash);
            map.put("method", method);
            map.put("path", path);
            map.put("riskLevel", riskLevel);
            map.put("scenario", scenario);
            map.put("scriptId", scriptId);
            map.put("version", version);
            return map;
        }

        /**
         * 从 Map 反序列化脚本条目（与 {@link #toMap()} 对称）。
         */
        public static ScriptEntry fromMap(java.util.Map<String, Object> m) {
            return new ScriptEntry(
                    (String) m.get("scriptId"),
                    (String) m.get("path"),
                    (String) m.get("method"),
                    (String) m.get("version"),
                    (String) m.get("scenario"),
                    (String) m.get("riskLevel"),
                    (String) m.get("contentHash"),
                    (String) m.get("metadataHash")
            );
        }
    }

    /**
     * 转为可序列化的 Map，用于 canonical JSON 生成。
     */
    public Map<String, Object> toMap() {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("projectCode", projectCode);
        map.put("environment", environment);
        map.put("packageVersion", packageVersion);
        map.put("runtimeVersion", runtimeVersion);
        map.put("publishedBy", publishedBy);
        map.put("publishedAt", publishedAt != null ? publishedAt.toString() : null);
        map.put("keyId", keyId);
        map.put("scripts", scripts.stream().map(ScriptEntry::toMap).toList());
        map.put("metadataHash", metadataHash);
        map.put("policyHash", policyHash);
        map.put("signAlg", signAlg);
        map.put("signature", signature);
        return map;
    }

    /**
     * 转为不含签名自身的 Map，用于签名输入。
     */
    public Map<String, Object> toSignableMap() {
        var map = toMap();
        map.remove("signature");
        return map;
    }

    /**
     * 从 Map 反序列化 Manifest（与 {@link #toMap()} 对称）。
     */
    @SuppressWarnings("unchecked")
    public static PackageManifest fromMap(Map<String, Object> map) {
        List<Map<String, Object>> scriptMaps =
                (List<Map<String, Object>>) map.getOrDefault("scripts", List.of());
        List<ScriptEntry> entries = scriptMaps.stream().map(ScriptEntry::fromMap).toList();
        Object publishedAt = map.get("publishedAt");
        return new PackageManifest(
                (String) map.get("projectCode"),
                (String) map.get("environment"),
                (String) map.get("packageVersion"),
                (String) map.get("runtimeVersion"),
                (String) map.get("publishedBy"),
                publishedAt != null ? Instant.parse((String) publishedAt) : null,
                (String) map.get("keyId"),
                entries,
                (String) map.get("metadataHash"),
                (String) map.get("policyHash"),
                (String) map.get("signAlg"),
                (String) map.get("signature")
        );
    }
}
