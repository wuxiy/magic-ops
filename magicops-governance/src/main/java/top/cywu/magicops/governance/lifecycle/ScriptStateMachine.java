package top.cywu.magicops.governance.lifecycle;

import top.cywu.magicops.core.model.ScriptStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 脚本状态机。定义合法的状态迁移路径，拒绝非法迁移。
 *
 * <p>迁移规则来自 {@code docs/design/flow-overview.md}：
 * <pre>
 * DRAFT → DEBUGGED → SUBMITTED → REVIEWING → APPROVED → SIGNED → PUSHING → PUBLISHED
 * REVIEWING → REJECTED
 * PUSHING → PUBLISH_FAILED
 * PUBLISHED → DISABLED
 * PUBLISHED → ROLLED_BACK
 * REJECTED → DRAFT（重新编辑）
 * PUBLISH_FAILED → SIGNED（重新推送）
 * </pre>
 */
public final class ScriptStateMachine {

    private static final Map<ScriptStatus, Set<ScriptStatus>> TRANSITIONS;

    static {
        TRANSITIONS = new EnumMap<>(ScriptStatus.class);

        TRANSITIONS.put(ScriptStatus.DRAFT, EnumSet.of(
                ScriptStatus.DEBUGGED));

        TRANSITIONS.put(ScriptStatus.DEBUGGED, EnumSet.of(
                ScriptStatus.SUBMITTED, ScriptStatus.DRAFT));

        TRANSITIONS.put(ScriptStatus.SUBMITTED, EnumSet.of(
                ScriptStatus.REVIEWING));

        TRANSITIONS.put(ScriptStatus.REVIEWING, EnumSet.of(
                ScriptStatus.APPROVED, ScriptStatus.REJECTED));

        TRANSITIONS.put(ScriptStatus.APPROVED, EnumSet.of(
                ScriptStatus.SIGNED));

        TRANSITIONS.put(ScriptStatus.SIGNED, EnumSet.of(
                ScriptStatus.PUSHING));

        TRANSITIONS.put(ScriptStatus.PUSHING, EnumSet.of(
                ScriptStatus.PUBLISHED, ScriptStatus.PUBLISH_FAILED));

        TRANSITIONS.put(ScriptStatus.PUBLISHED, EnumSet.of(
                ScriptStatus.DISABLED, ScriptStatus.ROLLED_BACK));

        TRANSITIONS.put(ScriptStatus.DISABLED, EnumSet.noneOf(ScriptStatus.class));
        TRANSITIONS.put(ScriptStatus.ROLLED_BACK, EnumSet.noneOf(ScriptStatus.class));

        TRANSITIONS.put(ScriptStatus.REJECTED, EnumSet.of(
                ScriptStatus.DRAFT));

        TRANSITIONS.put(ScriptStatus.PUBLISH_FAILED, EnumSet.of(
                ScriptStatus.SIGNED));
    }

    private ScriptStateMachine() {
    }

    /**
     * 判断从 {@code from} 到 {@code to} 的迁移是否合法。
     */
    public static boolean canTransition(ScriptStatus from, ScriptStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<ScriptStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 执行迁移，如果不合法则抛出 {@link IllegalStateTransitionException}。
     */
    public static ScriptStatus transition(ScriptStatus from, ScriptStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
        return to;
    }

    /**
     * 获取从指定状态可以迁移到的所有目标状态。
     */
    public static Set<ScriptStatus> allowedTransitions(ScriptStatus from) {
        if (from == null) {
            return EnumSet.noneOf(ScriptStatus.class);
        }
        Set<ScriptStatus> allowed = TRANSITIONS.get(from);
        return allowed != null ? EnumSet.copyOf(allowed) : EnumSet.noneOf(ScriptStatus.class);
    }
}
