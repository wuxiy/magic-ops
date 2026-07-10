package top.cywu.magicops.runtime.repair;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dry-run 报告。在修复执行前生成影响范围评估。
 */
public record DryRunReport(
        String scriptId,
        String sql,
        String sqlType,
        int estimatedAffectedRows,
        List<String> affectedTables,
        List<String> warnings,
        boolean safe,
        String reason,
        Instant generatedAt
) {

    public static DryRunReport success(String scriptId, String sql, String sqlType,
                                       int estimatedAffectedRows, List<String> affectedTables,
                                       List<String> warnings) {
        return new DryRunReport(scriptId, sql, sqlType, estimatedAffectedRows,
                affectedTables, warnings, true, null, Instant.now());
    }

    public static DryRunReport failure(String scriptId, String sql, String reason) {
        return new DryRunReport(scriptId, sql, "UNKNOWN", 0,
                List.of(), List.of(), false, reason, Instant.now());
    }

    /**
     * 转为 Map 便于序列化和审计。
     */
    public Map<String, Object> toMap() {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("scriptId", scriptId);
        map.put("sql", sql);
        map.put("sqlType", sqlType);
        map.put("estimatedAffectedRows", estimatedAffectedRows);
        map.put("affectedTables", affectedTables);
        map.put("warnings", warnings);
        map.put("safe", safe);
        if (reason != null) map.put("reason", reason);
        map.put("generatedAt", generatedAt.toString());
        return map;
    }
}
