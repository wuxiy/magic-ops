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
}
