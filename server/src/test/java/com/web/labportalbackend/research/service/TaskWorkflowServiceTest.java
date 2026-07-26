package com.web.labportalbackend.research.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.web.labportalbackend.research.enums.TaskStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;

/**
 * Exhaustive specification for {@link TaskWorkflowService}.
 *
 * <p><strong>Anti-tautology rule (AC19):</strong> this file must never read the transition matrix from
 * production code. {@link #EXPECTED_ALLOWED_TRANSITIONS} below is an independent, hand-written table
 * transcribed from the approved plan. If it were generated from the production constants the suite would
 * pass against any matrix, including a completely wrong one.
 *
 * <p>No Mockito, no Spring context, no database: the engine is pure, so the suite is pure.
 */
class TaskWorkflowServiceTest {

    private static final long TASK_ID = 4271L;
    private static final long ACTOR_USER_ID = 9001L;

    private final TaskWorkflowService service = new TaskWorkflowService();

    // ------------------------------------------------------------------------------------------------
    // Independent, hand-written expectation table (F7 / AC19). Transcribed from the approved plan §8.
    // ------------------------------------------------------------------------------------------------

    private static final Map<TaskWorkflowService.TaskWorkflowActor, Map<TaskStatus, Set<TaskStatus>>>
            EXPECTED_ALLOWED_TRANSITIONS = expectedAllowedTransitions();

    private static Map<TaskWorkflowService.TaskWorkflowActor, Map<TaskStatus, Set<TaskStatus>>>
            expectedAllowedTransitions() {
        Map<TaskWorkflowService.TaskWorkflowActor, Map<TaskStatus, Set<TaskStatus>>> expected =
                new EnumMap<>(TaskWorkflowService.TaskWorkflowActor.class);

        // MANAGER — 19 allowed pairs.
        Map<TaskStatus, Set<TaskStatus>> manager = new EnumMap<>(TaskStatus.class);
        manager.put(TaskStatus.BACKLOG,
                EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED));
        manager.put(TaskStatus.TODO,
                EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED));
        manager.put(TaskStatus.IN_PROGRESS,
                EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED, TaskStatus.CANCELLED));
        manager.put(TaskStatus.IN_REVIEW,
                EnumSet.of(TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.BLOCKED,
                        TaskStatus.CANCELLED));
        manager.put(TaskStatus.NEEDS_REVISION,
                EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED));
        manager.put(TaskStatus.DONE, EnumSet.noneOf(TaskStatus.class));
        manager.put(TaskStatus.BLOCKED,
                EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED));
        manager.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
        expected.put(TaskWorkflowService.TaskWorkflowActor.MANAGER, manager);

        // LEADER — 9 allowed pairs. IN_REVIEW -> DONE is deliberately absent.
        Map<TaskStatus, Set<TaskStatus>> leader = new EnumMap<>(TaskStatus.class);
        leader.put(TaskStatus.BACKLOG, EnumSet.noneOf(TaskStatus.class));
        leader.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        leader.put(TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED));
        leader.put(TaskStatus.IN_REVIEW, EnumSet.of(TaskStatus.NEEDS_REVISION, TaskStatus.BLOCKED));
        leader.put(TaskStatus.NEEDS_REVISION, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        leader.put(TaskStatus.DONE, EnumSet.noneOf(TaskStatus.class));
        leader.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.IN_PROGRESS));
        leader.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
        expected.put(TaskWorkflowService.TaskWorkflowActor.LEADER, leader);

        // MEMBER — 5 allowed pairs.
        Map<TaskStatus, Set<TaskStatus>> member = new EnumMap<>(TaskStatus.class);
        member.put(TaskStatus.BACKLOG, EnumSet.noneOf(TaskStatus.class));
        member.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS));
        member.put(TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED));
        member.put(TaskStatus.IN_REVIEW, EnumSet.noneOf(TaskStatus.class));
        member.put(TaskStatus.NEEDS_REVISION, EnumSet.of(TaskStatus.IN_PROGRESS));
        member.put(TaskStatus.DONE, EnumSet.noneOf(TaskStatus.class));
        member.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.IN_PROGRESS));
        member.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
        expected.put(TaskWorkflowService.TaskWorkflowActor.MEMBER, member);

        return expected;
    }

    private static boolean isExpectedAllowed(TaskWorkflowService.TaskWorkflowActor actor,
                                             TaskStatus from,
                                             TaskStatus to) {
        return EXPECTED_ALLOWED_TRANSITIONS.get(actor).get(from).contains(to);
    }

    // ------------------------------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------------------------------

    private static TaskWorkflowService.TaskTransitionContext context(
            TaskWorkflowService.TaskWorkflowActor actor,
            TaskStatus from,
            TaskStatus to,
            String rawBlockedReason,
            Integer currentProgressPercent,
            boolean hasApprovedReport,
            boolean requireApprovedReport) {
        boolean manager = actor == TaskWorkflowService.TaskWorkflowActor.MANAGER;
        boolean leader = actor == TaskWorkflowService.TaskWorkflowActor.LEADER;
        boolean assignee = actor == TaskWorkflowService.TaskWorkflowActor.MEMBER;
        boolean activeGroupMember = leader || assignee;
        return new TaskWorkflowService.TaskTransitionContext(
                TASK_ID,
                ACTOR_USER_ID,
                from,
                to,
                currentProgressPercent,
                rawBlockedReason,
                manager,
                leader,
                assignee,
                activeGroupMember,
                hasApprovedReport,
                requireApprovedReport);
    }

    /** Context that satisfies every gate other than the transition matrix itself. */
    private static TaskWorkflowService.TaskTransitionContext satisfiedContext(
            TaskWorkflowService.TaskWorkflowActor actor, TaskStatus from, TaskStatus to) {
        return context(actor, from, to, "blocking cause", 50, true, true);
    }

    private static String notAllowedMessage(TaskStatus from, TaskStatus to) {
        return "Task " + TASK_ID + ": status transition from " + from + " to " + to + " is not allowed";
    }

    private static Stream<TaskWorkflowService.TaskWorkflowActor> actors() {
        return Stream.of(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskWorkflowService.TaskWorkflowActor.LEADER,
                TaskWorkflowService.TaskWorkflowActor.MEMBER);
    }

    private static Stream<Arguments> allDistinctTriples() {
        List<Arguments> triples = new ArrayList<>();
        actors().forEach(actor -> {
            for (TaskStatus from : TaskStatus.values()) {
                for (TaskStatus to : TaskStatus.values()) {
                    if (from != to) {
                        triples.add(Arguments.of(actor, from, to));
                    }
                }
            }
        });
        return triples.stream();
    }

    // ------------------------------------------------------------------------------------------------
    // T1 — exhaustive allowed pairs (33)
    // ------------------------------------------------------------------------------------------------

    static Stream<Arguments> expectedAllowedTriples() {
        return allDistinctTriples().filter(arguments -> {
            Object[] values = arguments.get();
            return isExpectedAllowed((TaskWorkflowService.TaskWorkflowActor) values[0],
                    (TaskStatus) values[1], (TaskStatus) values[2]);
        });
    }

    @ParameterizedTest(name = "T1 {0}: {1} -> {2} is allowed")
    @MethodSource("expectedAllowedTriples")
    void t1_allowedTransitionsAreAccepted(TaskWorkflowService.TaskWorkflowActor actor,
                                          TaskStatus from,
                                          TaskStatus to) {
        var decision = service.evaluate(satisfiedContext(actor, from, to));

        assertThat(decision.actor()).isEqualTo(actor);
        assertThat(decision.fromStatus()).isEqualTo(from);
        assertThat(decision.toStatus()).isEqualTo(to);
        assertThat(decision.statusUnchanged()).isFalse();
    }

    // ------------------------------------------------------------------------------------------------
    // T2 — exhaustive rejected pairs (135)
    // ------------------------------------------------------------------------------------------------

    static Stream<Arguments> expectedRejectedTriples() {
        return allDistinctTriples().filter(arguments -> {
            Object[] values = arguments.get();
            return !isExpectedAllowed((TaskWorkflowService.TaskWorkflowActor) values[0],
                    (TaskStatus) values[1], (TaskStatus) values[2]);
        });
    }

    @ParameterizedTest(name = "T2 {0}: {1} -> {2} is rejected")
    @MethodSource("expectedRejectedTriples")
    void t2_rejectedTransitionsRaiseBadRequestWithTaskId(TaskWorkflowService.TaskWorkflowActor actor,
                                                         TaskStatus from,
                                                         TaskStatus to) {
        assertThatThrownBy(() -> service.evaluate(satisfiedContext(actor, from, to)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(notAllowedMessage(from, to));
    }

    // ------------------------------------------------------------------------------------------------
    // T3 — same-status semantics (54)
    // ------------------------------------------------------------------------------------------------

    private static Stream<Arguments> nonBlockedSameStatusPairs() {
        List<Arguments> pairs = new ArrayList<>();
        actors().forEach(actor -> {
            for (TaskStatus status : TaskStatus.values()) {
                if (status != TaskStatus.BLOCKED) {
                    pairs.add(Arguments.of(actor, status));
                }
            }
        });
        return pairs.stream();
    }

    static Stream<Arguments> t3aArguments() {
        return nonBlockedSameStatusPairs();
    }

    @ParameterizedTest(name = "T3a {0}: {1} -> {1} without reason is a pure no-op")
    @MethodSource("t3aArguments")
    void t3a_sameStatusWithoutReasonIsPureNoOp(TaskWorkflowService.TaskWorkflowActor actor,
                                               TaskStatus status) {
        var decision = service.evaluate(context(actor, status, status, null, 50, true, true));

        assertThat(decision.actor()).isEqualTo(actor);
        assertThat(decision.statusUnchanged()).isTrue();
        assertThat(decision.blockedReasonChanged()).isFalse();
        assertThat(decision.resolvedBlockedReason()).isNull();
        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    static Stream<Arguments> t3bArguments() {
        return nonBlockedSameStatusPairs();
    }

    @ParameterizedTest(name = "T3b {0}: {1} -> {1} ignores a supplied reason")
    @MethodSource("t3bArguments")
    void t3b_sameStatusIgnoresSuppliedReason(TaskWorkflowService.TaskWorkflowActor actor,
                                             TaskStatus status) {
        var decision = service.evaluate(context(actor, status, status, "  ignored cause  ", 50, true, true));

        assertThat(decision.statusUnchanged()).isTrue();
        assertThat(decision.blockedReasonChanged()).isFalse();
        assertThat(decision.resolvedBlockedReason()).isNull();
        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    static Stream<Arguments> t3cArguments() {
        List<Arguments> arguments = new ArrayList<>();
        actors().forEach(actor -> {
            arguments.add(Arguments.of(actor, (String) null));
            arguments.add(Arguments.of(actor, ""));
            arguments.add(Arguments.of(actor, "   "));
        });
        return arguments.stream();
    }

    @ParameterizedTest(name = "T3c {0}: BLOCKED -> BLOCKED with blank reason is a no-op")
    @MethodSource("t3cArguments")
    void t3c_blockedToBlockedWithBlankReasonIsNoOpAndDoesNotThrow(
            TaskWorkflowService.TaskWorkflowActor actor, String rawReason) {
        var decision = service.evaluate(
                context(actor, TaskStatus.BLOCKED, TaskStatus.BLOCKED, rawReason, 40, true, true));

        assertThat(decision.statusUnchanged()).isTrue();
        assertThat(decision.blockedReasonChanged()).isFalse();
        assertThat(decision.resolvedBlockedReason()).isNull();
        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    static Stream<TaskWorkflowService.TaskWorkflowActor> t3dArguments() {
        return actors();
    }

    @ParameterizedTest(name = "T3d {0}: BLOCKED -> BLOCKED refreshes the reason")
    @MethodSource("t3dArguments")
    void t3d_blockedToBlockedWithValidReasonRefreshesReason(
            TaskWorkflowService.TaskWorkflowActor actor) {
        var decision = service.evaluate(
                context(actor, TaskStatus.BLOCKED, TaskStatus.BLOCKED, "  new cause  ", 40, true, true));

        assertThat(decision.statusUnchanged()).isTrue();
        assertThat(decision.blockedReasonChanged()).isTrue();
        assertThat(decision.resolvedBlockedReason()).isEqualTo("new cause");
        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    // ------------------------------------------------------------------------------------------------
    // T4 — enum identity guard and matrix cardinality (4)
    // ------------------------------------------------------------------------------------------------

    @Test
    void t4a_taskStatusEnumContractIsFrozen() {
        assertThat(TaskStatus.values()).containsExactly(
                TaskStatus.BACKLOG,
                TaskStatus.TODO,
                TaskStatus.IN_PROGRESS,
                TaskStatus.IN_REVIEW,
                TaskStatus.NEEDS_REVISION,
                TaskStatus.DONE,
                TaskStatus.BLOCKED,
                TaskStatus.CANCELLED);
    }

    static Stream<Arguments> t4bArguments() {
        return Stream.of(
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.MANAGER, 19),
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.LEADER, 9),
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.MEMBER, 5));
    }

    // Self-consistency check of the hand-written EXPECTED_ALLOWED_TRANSITIONS table above, not a check
    // against TaskWorkflowService production code.
    @ParameterizedTest(name = "T4b {0} has {1} allowed pairs out of 64")
    @MethodSource("t4bArguments")
    void t4b_expectedTransitionTableCardinalityPerActorIs64(TaskWorkflowService.TaskWorkflowActor actor,
                                           int expectedAllowedCount) {
        int allowed = 0;
        int sameStatus = 0;
        int rejected = 0;
        for (TaskStatus from : TaskStatus.values()) {
            for (TaskStatus to : TaskStatus.values()) {
                if (from == to) {
                    sameStatus++;
                } else if (isExpectedAllowed(actor, from, to)) {
                    allowed++;
                } else {
                    rejected++;
                }
            }
        }

        assertThat(sameStatus).isEqualTo(8);
        assertThat(allowed).isEqualTo(expectedAllowedCount);
        assertThat(allowed + sameStatus + rejected).isEqualTo(64);
    }

    // ------------------------------------------------------------------------------------------------
    // T5 — actor resolution over all 16 boolean combinations (16)
    // ------------------------------------------------------------------------------------------------

    static Stream<Arguments> t5Arguments() {
        return Stream.of(
                Arguments.of(1, false, false, false, false, null),
                Arguments.of(2, false, false, false, true, null),
                Arguments.of(3, false, false, true, false, null),
                Arguments.of(4, false, false, true, true, TaskWorkflowService.TaskWorkflowActor.MEMBER),
                Arguments.of(5, false, true, false, false, null),
                Arguments.of(6, false, true, false, true, TaskWorkflowService.TaskWorkflowActor.LEADER),
                Arguments.of(7, false, true, true, false, null),
                Arguments.of(8, false, true, true, true, TaskWorkflowService.TaskWorkflowActor.LEADER),
                Arguments.of(9, true, false, false, false, TaskWorkflowService.TaskWorkflowActor.MANAGER),
                Arguments.of(10, true, false, false, true, TaskWorkflowService.TaskWorkflowActor.MANAGER),
                Arguments.of(11, true, false, true, false, TaskWorkflowService.TaskWorkflowActor.MANAGER),
                Arguments.of(12, true, false, true, true, TaskWorkflowService.TaskWorkflowActor.MANAGER),
                Arguments.of(13, true, true, false, false, TaskWorkflowService.TaskWorkflowActor.MANAGER),
                Arguments.of(14, true, true, false, true, TaskWorkflowService.TaskWorkflowActor.MANAGER),
                Arguments.of(15, true, true, true, false, TaskWorkflowService.TaskWorkflowActor.MANAGER),
                Arguments.of(16, true, true, true, true, TaskWorkflowService.TaskWorkflowActor.MANAGER));
    }

    @ParameterizedTest(name = "T5 row {0}: M={1} L={2} A={3} G={4} -> {5}")
    @MethodSource("t5Arguments")
    void t5_actorResolutionIsFailClosed(int row,
                                        boolean managerInScope,
                                        boolean leaderInScope,
                                        boolean assignee,
                                        boolean activeGroupMember,
                                        TaskWorkflowService.TaskWorkflowActor expectedActor) {
        TaskWorkflowService.TaskTransitionContext context =
                new TaskWorkflowService.TaskTransitionContext(
                        TASK_ID,
                        ACTOR_USER_ID,
                        TaskStatus.TODO,
                        TaskStatus.IN_PROGRESS,
                        50,
                        null,
                        managerInScope,
                        leaderInScope,
                        assignee,
                        activeGroupMember,
                        true,
                        true);

        if (expectedActor == null) {
            assertThatThrownBy(() -> service.evaluate(context))
                    .as("row %d must be denied", row)
                    .isInstanceOf(AccessDeniedException.class);
        } else {
            assertThat(service.evaluate(context).actor())
                    .as("row %d", row)
                    .isEqualTo(expectedActor);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // T6 — blockedReason input space, rules B1, B2, B5, B6, B7, B8 (24)
    // ------------------------------------------------------------------------------------------------

    static Stream<TaskWorkflowService.TaskWorkflowActor> t6b1Arguments() {
        return actors();
    }

    @ParameterizedTest(name = "T6/B1 {0}: entering BLOCKED trims and stores the reason")
    @MethodSource("t6b1Arguments")
    void t6b1_enteringBlockedStoresTrimmedReason(TaskWorkflowService.TaskWorkflowActor actor) {
        var decision = service.evaluate(
                context(actor, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, "  fix db  ", 50, true, true));

        assertThat(decision.blockedReasonChanged()).isTrue();
        assertThat(decision.resolvedBlockedReason()).isEqualTo("fix db");
        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    static Stream<Arguments> t6b2Arguments() {
        List<Arguments> arguments = new ArrayList<>();
        actors().forEach(actor -> {
            arguments.add(Arguments.of(actor, (String) null));
            arguments.add(Arguments.of(actor, ""));
            arguments.add(Arguments.of(actor, "   "));
            arguments.add(Arguments.of(actor, "\t\n "));
        });
        return arguments.stream();
    }

    @ParameterizedTest(name = "T6/B2 {0}: entering BLOCKED with a blank reason is rejected")
    @MethodSource("t6b2Arguments")
    void t6b2_enteringBlockedWithBlankReasonIsRejected(TaskWorkflowService.TaskWorkflowActor actor,
                                                       String rawReason) {
        assertThatThrownBy(() -> service.evaluate(
                context(actor, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, rawReason, 50, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lý do");
    }

    static Stream<Arguments> t6b5Arguments() {
        return Stream.of(
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.MANAGER, TaskStatus.TODO),
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.MANAGER, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.LEADER, TaskStatus.IN_PROGRESS),
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.MEMBER, TaskStatus.IN_PROGRESS));
    }

    @ParameterizedTest(name = "T6/B5 {0}: BLOCKED -> {1} always clears the reason")
    @MethodSource("t6b5Arguments")
    void t6b5_leavingBlockedAlwaysClearsReason(TaskWorkflowService.TaskWorkflowActor actor,
                                               TaskStatus to) {
        var decision = service.evaluate(
                context(actor, TaskStatus.BLOCKED, to, "stale cause", 50, true, true));

        assertThat(decision.blockedReasonChanged()).isTrue();
        assertThat(decision.resolvedBlockedReason()).isNull();
    }

    static Stream<TaskWorkflowService.TaskWorkflowActor> t6b6Arguments() {
        return actors();
    }

    @ParameterizedTest(name = "T6/B6 {0}: a reason is ignored when neither side is BLOCKED")
    @MethodSource("t6b6Arguments")
    void t6b6_reasonIgnoredWhenNeitherSideIsBlocked(TaskWorkflowService.TaskWorkflowActor actor) {
        var decision = service.evaluate(
                context(actor, TaskStatus.TODO, TaskStatus.IN_PROGRESS, "irrelevant", 50, true, true));

        assertThat(decision.blockedReasonChanged()).isFalse();
        assertThat(decision.resolvedBlockedReason()).isNull();
    }

    @Test
    void t6b7_cancellingFromNonBlockedNeverWritesReason() {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED, "irrelevant", 50, true, true));

        assertThat(decision.blockedReasonChanged()).isFalse();
        assertThat(decision.resolvedBlockedReason()).isNull();
    }

    @Test
    void t6b8_cancellingFromBlockedClearsReasonWithoutWritingANewOne() {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.BLOCKED, TaskStatus.CANCELLED, "stale cause", 50, true, true));

        assertThat(decision.blockedReasonChanged()).isTrue();
        assertThat(decision.resolvedBlockedReason()).isNull();
    }

    @Test
    void t6b9_reasonMadeSolelyOfZeroWidthSpacesIsRejectedAsBlank() {
        // U+200B (ZERO WIDTH SPACE) is Character.FORMAT, not Character.isWhitespace: a naive
        // strip().isEmpty() check would treat this as a valid, non-blank reason.
        String invisibleOnlyReason = "​​​";

        assertThatThrownBy(() -> service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, invisibleOnlyReason, 50, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lý do");
    }

    // ------------------------------------------------------------------------------------------------
    // T7 — non-manager IN_REVIEW -> DONE is a matrix rejection, not a report-gate rejection (3)
    // ------------------------------------------------------------------------------------------------

    static Stream<Arguments> t7Arguments() {
        return Stream.of(
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.LEADER, true, false),
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.LEADER, true, true),
                Arguments.of(TaskWorkflowService.TaskWorkflowActor.MEMBER, true, true));
    }

    @ParameterizedTest(name = "T7 {0}: IN_REVIEW -> DONE denied by the matrix (has={1}, require={2})")
    @MethodSource("t7Arguments")
    void t7_nonManagerCannotCompleteEvenWithAnApprovedReport(
            TaskWorkflowService.TaskWorkflowActor actor,
            boolean hasApprovedReport,
            boolean requireApprovedReport) {
        assertThatThrownBy(() -> service.evaluate(context(actor, TaskStatus.IN_REVIEW, TaskStatus.DONE,
                null, 90, hasApprovedReport, requireApprovedReport)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(notAllowedMessage(TaskStatus.IN_REVIEW, TaskStatus.DONE));
    }

    // ------------------------------------------------------------------------------------------------
    // T8 — DONE gate driven only by the two booleans (4)
    // ------------------------------------------------------------------------------------------------

    static Stream<Arguments> t8Arguments() {
        return Stream.of(
                Arguments.of(true, false, true),
                Arguments.of(true, true, false),
                Arguments.of(false, false, false),
                Arguments.of(false, true, false));
    }

    @ParameterizedTest(name = "T8 require={0} has={1} -> rejected={2}")
    @MethodSource("t8Arguments")
    void t8_doneGateUsesOnlyTheTwoBooleans(boolean requireApprovedReport,
                                           boolean hasApprovedReport,
                                           boolean expectRejection) {
        TaskWorkflowService.TaskTransitionContext context =
                context(TaskWorkflowService.TaskWorkflowActor.MANAGER, TaskStatus.IN_REVIEW,
                        TaskStatus.DONE, null, 90, hasApprovedReport, requireApprovedReport);

        if (expectRejection) {
            assertThatThrownBy(() -> service.evaluate(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("báo cáo");
        } else {
            assertThat(service.evaluate(context).resolvedProgressPercent()).isEqualTo(100);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // T9 — decision output correctness, progress mapping (15)
    // ------------------------------------------------------------------------------------------------

    static Stream<Arguments> t9InProgressArguments() {
        return Stream.of(
                Arguments.of(null, 10),
                Arguments.of(0, 10),
                Arguments.of(5, 10),
                Arguments.of(9, 10),
                Arguments.of(10, 10),
                Arguments.of(50, 50));
    }

    @ParameterizedTest(name = "T9 IN_PROGRESS current={0} -> {1}")
    @MethodSource("t9InProgressArguments")
    void t9_inProgressRaisesProgressToAtLeastTen(Integer currentProgress, Integer expectedProgress) {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.TODO, TaskStatus.IN_PROGRESS, null, currentProgress, true, true));

        assertThat(decision.resolvedProgressPercent()).isEqualTo(expectedProgress);
    }

    static Stream<Arguments> t9InReviewArguments() {
        return Stream.of(Arguments.of(0), Arguments.of(90), Arguments.of(95));
    }

    @ParameterizedTest(name = "T9 IN_REVIEW current={0} -> 90 (hard overwrite)")
    @MethodSource("t9InReviewArguments")
    void t9_inReviewOverwritesProgressToNinetyEvenDownwards(Integer currentProgress) {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, null, currentProgress, true, true));

        assertThat(decision.resolvedProgressPercent()).isEqualTo(90);
    }

    @Test
    void t9_doneSetsProgressToOneHundred() {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.IN_REVIEW, TaskStatus.DONE, null, 90, true, true));

        assertThat(decision.resolvedProgressPercent()).isEqualTo(100);
    }

    static Stream<Arguments> t9NullProgressArguments() {
        return Stream.of(
                Arguments.of(TaskStatus.BLOCKED, TaskStatus.TODO, null),
                Arguments.of(TaskStatus.IN_REVIEW, TaskStatus.NEEDS_REVISION, null),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, "cause"),
                Arguments.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED, null));
    }

    @ParameterizedTest(name = "T9 {0} -> {1} leaves progress untouched")
    @MethodSource("t9NullProgressArguments")
    void t9_otherTargetsLeaveProgressUntouched(TaskStatus from, TaskStatus to, String rawReason) {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                from, to, rawReason, 42, true, true));

        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    @Test
    void t9_sameStatusLeavesProgressUntouched() {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS, null, 42, true, true));

        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    // ------------------------------------------------------------------------------------------------
    // T10 — CANCELLED is not BLOCKED (12)
    // ------------------------------------------------------------------------------------------------

    static Stream<TaskStatus> t10CancelSourceArguments() {
        return Stream.of(TaskStatus.BACKLOG, TaskStatus.TODO, TaskStatus.IN_PROGRESS,
                TaskStatus.IN_REVIEW, TaskStatus.NEEDS_REVISION, TaskStatus.BLOCKED);
    }

    @ParameterizedTest(name = "T10 {0} -> CANCELLED needs no reason")
    @MethodSource("t10CancelSourceArguments")
    void t10_cancellingNeverRequiresAReason(TaskStatus from) {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                from, TaskStatus.CANCELLED, null, 50, true, true));

        assertThat(decision.toStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(decision.resolvedBlockedReason()).isNull();
    }

    static Stream<TaskWorkflowService.TaskWorkflowActor> t10ActorArguments() {
        return actors();
    }

    @ParameterizedTest(name = "T10 {0}: CANCELLED has no outgoing transition")
    @MethodSource("t10ActorArguments")
    void t10_cancelledHasNoOutgoingTransition(TaskWorkflowService.TaskWorkflowActor actor) {
        for (TaskStatus to : TaskStatus.values()) {
            if (to == TaskStatus.CANCELLED) {
                continue;
            }
            assertThatThrownBy(() -> service.evaluate(
                    satisfiedContext(actor, TaskStatus.CANCELLED, to)))
                    .as("%s: CANCELLED -> %s", actor, to)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest(name = "T10 {0}: BLOCKED has an outgoing transition")
    @MethodSource("t10ActorArguments")
    void t10_blockedHasAnOutgoingTransition(TaskWorkflowService.TaskWorkflowActor actor) {
        var decision = service.evaluate(
                satisfiedContext(actor, TaskStatus.BLOCKED, TaskStatus.IN_PROGRESS));

        assertThat(decision.toStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    // ------------------------------------------------------------------------------------------------
    // T11 — purity (5)
    // ------------------------------------------------------------------------------------------------

    @Test
    void t11a_engineHasNoInstanceState() {
        // Whitelisted precisely: the three EnumMap-backed transition matrices, the String message/validation
        // constants and the int progress constants. A field of any other type — e.g. a stray
        // `private static final Random R = new Random();` — must fail this assertion immediately, even
        // though it would still be `static final`.
        Set<Class<?>> allowedFieldTypes = Set.of(Map.class, String.class, int.class);

        for (Field field : TaskWorkflowService.class.getDeclaredFields()) {
            assertThat(Modifier.isStatic(field.getModifiers()))
                    .as("field %s must be static", field.getName())
                    .isTrue();
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .as("field %s must be final", field.getName())
                    .isTrue();
            assertThat(allowedFieldTypes)
                    .as("field %s has disallowed type %s", field.getName(), field.getType().getName())
                    .contains(field.getType());
        }
    }

    @Test
    void t11b_engineHasExactlyOneNoArgumentConstructor() {
        Constructor<?>[] constructors = TaskWorkflowService.class.getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterCount()).isZero();
    }

    @Test
    void t11c_engineExposesNoApplyMethod() {
        assertThat(TaskWorkflowService.class.getDeclaredMethods())
                .filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .extracting(Method::getName)
                .doesNotContain("apply");
    }

    @Test
    void t11d_evaluateDoesNotMutateTheContext() {
        TaskWorkflowService.TaskTransitionContext context = context(
                TaskWorkflowService.TaskWorkflowActor.MANAGER, TaskStatus.IN_PROGRESS,
                TaskStatus.BLOCKED, "  cause  ", 42, true, true);
        TaskWorkflowService.TaskTransitionContext untouched = context(
                TaskWorkflowService.TaskWorkflowActor.MANAGER, TaskStatus.IN_PROGRESS,
                TaskStatus.BLOCKED, "  cause  ", 42, true, true);

        service.evaluate(context);

        assertThat(context).isEqualTo(untouched);
    }

    @Test
    void t11e_evaluateIsDeterministic() {
        TaskWorkflowService.TaskTransitionContext context = context(
                TaskWorkflowService.TaskWorkflowActor.MANAGER, TaskStatus.IN_PROGRESS,
                TaskStatus.IN_REVIEW, null, 42, true, true);

        assertThat(service.evaluate(context)).isEqualTo(service.evaluate(context));
    }

    // ------------------------------------------------------------------------------------------------
    // T12 — entity-free contract and null-milestone support (3)
    // ------------------------------------------------------------------------------------------------

    @Test
    void t12a_contextCarriesOnlyPlainValues() {
        Set<Class<?>> allowedTypes = Set.of(Long.class, TaskStatus.class, Integer.class, String.class,
                boolean.class);

        for (RecordComponent component :
                TaskWorkflowService.TaskTransitionContext.class.getRecordComponents()) {
            assertThat(allowedTypes)
                    .as("component %s of type %s", component.getName(), component.getType().getName())
                    .contains(component.getType());
        }
    }

    @Test
    void t12b_engineExposesNoApiTakingAnEntityOrRepository() {
        for (Method method : TaskWorkflowService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertThat(parameterType.getName())
                        .as("public method %s", method.getName())
                        .doesNotContain("Entity")
                        .doesNotContain("Repository");
            }
        }
        for (Field field : TaskWorkflowService.class.getDeclaredFields()) {
            assertThat(field.getType().getName())
                    .as("field %s", field.getName())
                    .doesNotContain("Entity")
                    .doesNotContain("Repository");
        }
    }

    @Test
    void t12c_backlogTransitionWorksWithoutAnyMilestoneInformation() {
        var decision = service.evaluate(context(TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.BACKLOG, TaskStatus.TODO, null, null, true, true));

        assertThat(decision.toStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(decision.resolvedProgressPercent()).isNull();
    }

    // ------------------------------------------------------------------------------------------------
    // T13 — input shape validation (3)
    // ------------------------------------------------------------------------------------------------

    @Test
    void t13a_nullContextIsRejected() {
        assertThatThrownBy(() -> service.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void t13b_nullCurrentStatusIsRejected() {
        TaskWorkflowService.TaskTransitionContext context =
                new TaskWorkflowService.TaskTransitionContext(TASK_ID, ACTOR_USER_ID, null,
                        TaskStatus.IN_PROGRESS, 10, null, false, false, false, false, false, false);

        assertThatThrownBy(() -> service.evaluate(context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void t13c_nullTargetStatusIsRejected() {
        TaskWorkflowService.TaskTransitionContext context =
                new TaskWorkflowService.TaskTransitionContext(TASK_ID, ACTOR_USER_ID, TaskStatus.TODO,
                        null, 10, null, false, false, false, false, false, false);

        assertThatThrownBy(() -> service.evaluate(context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------------------------------------
    // T14 — authorization precedence: authz must run before same-status/N1 and before the DONE gate (2)
    // ------------------------------------------------------------------------------------------------

    @Test
    void t14a_unauthorizedActorIsDeniedBeforeSameStatusNoOpShortCircuits() {
        // All four capability booleans false: resolveActor() must fail-closed before the same-status branch
        // (step 3) ever gets a chance to return a "harmless" no-op decision. If a future change swapped the
        // order, this same-status request would silently succeed instead of throwing.
        TaskWorkflowService.TaskTransitionContext context = new TaskWorkflowService.TaskTransitionContext(
                TASK_ID, ACTOR_USER_ID, TaskStatus.TODO, TaskStatus.TODO, 50, null,
                false, false, false, false, true, true);

        assertThatThrownBy(() -> service.evaluate(context))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void t14b_unauthorizedActorIsDeniedBeforeReachingTheDoneGate() {
        // All four capability booleans false, but the DONE gate inputs are set up so that, if authorization
        // were skipped or evaluated after the DONE gate, the request would sail through as approved. Actor
        // resolution (step 2) must still deny it first.
        TaskWorkflowService.TaskTransitionContext context = new TaskWorkflowService.TaskTransitionContext(
                TASK_ID, ACTOR_USER_ID, TaskStatus.IN_REVIEW, TaskStatus.DONE, 90, null,
                false, false, false, false, true, true);

        assertThatThrownBy(() -> service.evaluate(context))
                .isInstanceOf(AccessDeniedException.class);
    }
}
