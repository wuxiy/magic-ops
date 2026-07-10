package top.cywu.magicops.governance.lifecycle;

import top.cywu.magicops.core.model.ScriptStatus;

/**
 * 非法状态迁移异常。
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final ScriptStatus from;
    private final ScriptStatus to;

    public IllegalStateTransitionException(ScriptStatus from, ScriptStatus to) {
        super(String.format("非法状态迁移：%s → %s", from, to));
        this.from = from;
        this.to = to;
    }

    public ScriptStatus getFrom() {
        return from;
    }

    public ScriptStatus getTo() {
        return to;
    }
}
