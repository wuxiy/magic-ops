package top.cywu.magicops.core.model;

/**
 * 脚本类型。动态查询、数据修复和 HTTP 接口适配共用生命周期。
 */
public enum ScriptType {

    /** 动态查询 API，SQL 默认只读。 */
    DYNAMIC_QUERY,

    /** 受控数据修复，需要 dry-run 和审批。 */
    DATA_REPAIR,

    /** HTTP 接口适配，调用已注册目标系统。 */
    HTTP_ADAPTER
}
