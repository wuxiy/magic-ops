package top.cywu.magicops.core.model;

/**
 * 脚本生命周期状态。
 *
 * <p>状态迁移路径见 {@code docs/design/flow-overview.md}。
 */
public enum ScriptStatus {

    /** 草稿，可编辑。 */
    DRAFT,

    /** 已调试，在受控环境中验证过。 */
    DEBUGGED,

    /** 已提交审批。 */
    SUBMITTED,

    /** 审批中。 */
    REVIEWING,

    /** 已审批通过。 */
    APPROVED,

    /** 已签名。 */
    SIGNED,

    /** 推送中。 */
    PUSHING,

    /** 已发布到 Runtime。 */
    PUBLISHED,

    /** 已禁用。 */
    DISABLED,

    /** 已回滚。 */
    ROLLED_BACK,

    /** 审批被拒绝。 */
    REJECTED,

    /** 发布失败。 */
    PUBLISH_FAILED;

    /**
     * 判断当前状态是否为终态（不可再迁移，除非显式重置）。
     */
    public boolean isTerminal() {
        return this == PUBLISHED || this == DISABLED || this == ROLLED_BACK;
    }

    /**
     * 判断当前状态是否为失败态。
     */
    public boolean isFailed() {
        return this == REJECTED || this == PUBLISH_FAILED;
    }
}
