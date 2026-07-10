package top.cywu.magicops.governance.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import top.cywu.magicops.core.model.ScriptStatus;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脚本状态机测试。覆盖合法路径和非法拒绝。
 */
class ScriptStateMachineTest {

    @Test
    void happyPath_draftToApproved() {
        // DRAFT → DEBUGGED → SUBMITTED → REVIEWING → APPROVED
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.DRAFT, ScriptStatus.DEBUGGED));
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.DEBUGGED, ScriptStatus.SUBMITTED));
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.SUBMITTED, ScriptStatus.REVIEWING));
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.REVIEWING, ScriptStatus.APPROVED));
    }

    @Test
    void happyPath_approvedToPublished() {
        // APPROVED → SIGNED → PUSHING → PUBLISHED
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.APPROVED, ScriptStatus.SIGNED));
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.SIGNED, ScriptStatus.PUSHING));
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.PUSHING, ScriptStatus.PUBLISHED));
    }

    @Test
    void rejectionPath_reviewingToRejected() {
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.REVIEWING, ScriptStatus.REJECTED));
    }

    @Test
    void publishFailedPath() {
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.PUSHING, ScriptStatus.PUBLISH_FAILED));
    }

    @Test
    void reEditAfterRejection() {
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.REJECTED, ScriptStatus.DRAFT));
    }

    @Test
    void retryAfterPublishFailed() {
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.PUBLISH_FAILED, ScriptStatus.SIGNED));
    }

    @Test
    void disableAndRollback() {
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.PUBLISHED, ScriptStatus.DISABLED));
        assertTrue(ScriptStateMachine.canTransition(ScriptStatus.PUBLISHED, ScriptStatus.ROLLED_BACK));
    }

    // 非法迁移拒绝

    @Test
    void illegal_draftToApproved() {
        assertFalse(ScriptStateMachine.canTransition(ScriptStatus.DRAFT, ScriptStatus.APPROVED));
    }

    @Test
    void illegal_draftToPublished() {
        assertFalse(ScriptStateMachine.canTransition(ScriptStatus.DRAFT, ScriptStatus.PUBLISHED));
    }

    @Test
    void illegal_approvedToDraft() {
        assertFalse(ScriptStateMachine.canTransition(ScriptStatus.APPROVED, ScriptStatus.DRAFT));
    }

    @Test
    void illegal_publishedToDraft() {
        assertFalse(ScriptStateMachine.canTransition(ScriptStatus.PUBLISHED, ScriptStatus.DRAFT));
    }

    @Test
    void illegal_terminalStates() {
        // DISABLED 和 ROLLED_BACK 是终态
        assertFalse(ScriptStateMachine.canTransition(ScriptStatus.DISABLED, ScriptStatus.DRAFT));
        assertFalse(ScriptStateMachine.canTransition(ScriptStatus.ROLLED_BACK, ScriptStatus.DRAFT));
    }

    @Test
    void illegal_nullStatus() {
        assertFalse(ScriptStateMachine.canTransition(null, ScriptStatus.DRAFT));
        assertFalse(ScriptStateMachine.canTransition(ScriptStatus.DRAFT, null));
    }

    @Test
    void transition_throwsOnIllegal() {
        assertThrows(IllegalStateTransitionException.class,
                () -> ScriptStateMachine.transition(ScriptStatus.DRAFT, ScriptStatus.APPROVED));
    }

    @Test
    void transition_returnsTargetOnLegal() {
        assertEquals(ScriptStatus.DEBUGGED,
                ScriptStateMachine.transition(ScriptStatus.DRAFT, ScriptStatus.DEBUGGED));
    }

    @ParameterizedTest
    @MethodSource("allIllegalTransitions")
    void rejectsIllegalTransition(ScriptStatus from, ScriptStatus to) {
        assertFalse(ScriptStateMachine.canTransition(from, to),
                "应当拒绝 " + from + " → " + to);
    }

    static Stream<Arguments> allIllegalTransitions() {
        return Stream.of(
                // 不能跳过中间状态
                Arguments.of(ScriptStatus.DRAFT, ScriptStatus.SUBMITTED),
                Arguments.of(ScriptStatus.DRAFT, ScriptStatus.REVIEWING),
                Arguments.of(ScriptStatus.DRAFT, ScriptStatus.APPROVED),
                Arguments.of(ScriptStatus.DRAFT, ScriptStatus.SIGNED),
                Arguments.of(ScriptStatus.DRAFT, ScriptStatus.PUBLISHED),
                // 不能从已审批回到草稿
                Arguments.of(ScriptStatus.APPROVED, ScriptStatus.DRAFT),
                Arguments.of(ScriptStatus.SIGNED, ScriptStatus.DRAFT),
                // 终态不可迁移
                Arguments.of(ScriptStatus.DISABLED, ScriptStatus.DRAFT),
                Arguments.of(ScriptStatus.DISABLED, ScriptStatus.PUBLISHED),
                Arguments.of(ScriptStatus.ROLLED_BACK, ScriptStatus.DRAFT),
                // 自迁移不允许
                Arguments.of(ScriptStatus.DRAFT, ScriptStatus.DRAFT),
                Arguments.of(ScriptStatus.APPROVED, ScriptStatus.APPROVED)
        );
    }

    @Test
    void allowedTransitions_returnsCorrectSet() {
        Set<ScriptStatus> fromDraft = ScriptStateMachine.allowedTransitions(ScriptStatus.DRAFT);
        assertEquals(Set.of(ScriptStatus.DEBUGGED), fromDraft);

        Set<ScriptStatus> fromReviewing = ScriptStateMachine.allowedTransitions(ScriptStatus.REVIEWING);
        assertEquals(Set.of(ScriptStatus.APPROVED, ScriptStatus.REJECTED), fromReviewing);

        Set<ScriptStatus> fromTerminal = ScriptStateMachine.allowedTransitions(ScriptStatus.DISABLED);
        assertTrue(fromTerminal.isEmpty());
    }
}
