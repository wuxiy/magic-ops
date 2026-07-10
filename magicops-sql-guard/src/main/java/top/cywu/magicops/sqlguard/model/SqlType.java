package top.cywu.magicops.sqlguard.model;

/**
 * SQL 类型分类。用于 SQL Guard 策略执行。
 */
public enum SqlType {

    /** 查询，默认允许但限制结果大小。 */
    SELECT,

    /** 插入，需要授权。 */
    INSERT,

    /** 更新，需要授权、WHERE、影响行数限制。 */
    UPDATE,

    /** 删除，高风险，需要审批，必须有 WHERE。 */
    DELETE,

    /** 清空表，默认拒绝。 */
    TRUNCATE,

    /** 删除表，默认拒绝。 */
    DROP,

    /** 修改表结构，默认拒绝。 */
    ALTER,

    /** 创建对象，默认拒绝。 */
    CREATE,

    /** 授权，默认拒绝。 */
    GRANT,

    /** 无法分类。 */
    UNKNOWN
}
