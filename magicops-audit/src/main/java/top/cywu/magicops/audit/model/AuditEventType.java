package top.cywu.magicops.audit.model;

import java.time.Instant;

/**
 * 审计事件类型。覆盖操作审计、执行审计、发布审计和审批审计。
 */
public enum AuditEventType {

    /** 脚本创建。 */
    SCRIPT_CREATED,

    /** 脚本状态变更。 */
    SCRIPT_STATUS_CHANGED,

    /** 审批提交。 */
    APPROVAL_SUBMITTED,

    /** 审批决策。 */
    APPROVAL_DECIDED,

    /** 发布包签名。 */
    PACKAGE_SIGNED,

    /** 发布包推送。 */
    PACKAGE_PUSHED,

    /** 发布包加载。 */
    PACKAGE_LOADED,

    /** 脚本执行。 */
    SCRIPT_EXECUTED,

    /** 角色分配（切片 31）。 */
    ROLE_ASSIGNED,

    /** 角色回收（切片 31）。 */
    ROLE_REVOKED,

    /** 审计写入失败。 */
    AUDIT_WRITE_FAILED
}
