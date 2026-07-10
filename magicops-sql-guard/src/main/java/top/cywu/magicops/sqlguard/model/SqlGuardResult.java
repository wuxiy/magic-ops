package top.cywu.magicops.sqlguard.model;

/**
 * SQL Guard 校验结果。
 */
public record SqlGuardResult(
        boolean allowed,
        SqlType sqlType,
        String reason,
        int estimatedAffectedRows
) {

    public static SqlGuardResult allow(SqlType sqlType) {
        return new SqlGuardResult(true, sqlType, null, 0);
    }

    public static SqlGuardResult deny(SqlType sqlType, String reason) {
        return new SqlGuardResult(false, sqlType, reason, 0);
    }
}
