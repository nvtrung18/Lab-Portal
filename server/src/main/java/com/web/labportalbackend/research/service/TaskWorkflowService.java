package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.enums.TaskStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Canonical, pure policy engine for research task status transitions (P2-T6).
 *
 * <p>Architecture (P2-DECISION-03, Option B): this component has <strong>zero dependencies</strong>,
 * performs <strong>zero I/O</strong>, never reads the security context, never mutates a task entity,
 * never audits, never opens a transaction, and uses no nondeterministic source. It answers exactly one
 * question, from plain input values only:
 *
 * <blockquote>Given four already-resolved capability booleans, a current status, a target status, a raw
 * blocked reason, a current progress value and two DONE-policy booleans: is this transition allowed, and
 * if so, what must {@code blockedReason} and {@code progressPercent} become?</blockquote>
 *
 * <p>The caller (P2-T7) owns loading and locking the task, resolving the four capability booleans, reading
 * the approved-report and system-config flags, applying the returned decision, saving and auditing.
 *
 * <p>Because {@link TaskTransitionContext} carries no entity reference, entity mutation from inside this
 * engine is impossible by construction: purity is compiler-guaranteed, not merely conventional.
 */
@Component
public class TaskWorkflowService {

    /**
     * Message used when the DONE gate rejects a completion because no approved report exists.
     *
     * <p>Intentionally re-declared here rather than imported from {@code TaskServiceImpl}: the legacy
     * service is frozen for P2-T6 (decision D2) and this engine must not depend on it.
     */
    private static final String APPROVED_REPORT_REQUIRED =
            "Cần có báo cáo được duyệt trước khi hoàn thành nhiệm vụ/mốc nghiên cứu.";

    private static final String BLOCKED_REASON_REQUIRED =
            "Cần nêu lý do khi chuyển nhiệm vụ sang trạng thái BLOCKED.";

    private static final String CONTEXT_REQUIRED = "Transition context is required";
    private static final String CURRENT_STATUS_REQUIRED = "Current task status is required";
    private static final String TARGET_STATUS_REQUIRED = "Target task status is required";

    private static final int IN_PROGRESS_MINIMUM_PROGRESS = 10;
    private static final int IN_REVIEW_PROGRESS = 90;
    private static final int DONE_PROGRESS = 100;

    /** Manager transition matrix — 19 allowed pairs. Source: PHASE_2_EXECUTION_PLAN.md:474-479. */
    private static final Map<TaskStatus, Set<TaskStatus>> MANAGER_TRANSITIONS = managerTransitions();

    /** Leader transition matrix — 9 allowed pairs. Source: PHASE_2_EXECUTION_PLAN.md:463-471 (literal). */
    private static final Map<TaskStatus, Set<TaskStatus>> LEADER_TRANSITIONS = leaderTransitions();

    /** Member (assignee) transition matrix — 5 allowed pairs. Source: PHASE_2_EXECUTION_PLAN.md:461. */
    private static final Map<TaskStatus, Set<TaskStatus>> MEMBER_TRANSITIONS = memberTransitions();

    /**
     * Actor categories recognised by the engine, in precedence order
     * {@code MANAGER > LEADER > MEMBER}.
     */
    public enum TaskWorkflowActor {
        MANAGER,
        LEADER,
        MEMBER
    }

    /**
     * Pure input for {@link #evaluate(TaskTransitionContext)}. Deliberately contains no entity, no
     * repository and no service reference.
     *
     * @param taskId used verbatim in the rejection message so a denied transition can be traced by the
     *               caller and by operations without the engine logging anything itself
     * @param actorUserId reserved for logging/tracing at the caller layer (P2-T7). The engine does
     *                    <strong>not</strong> use this field in any message or in any decision. It is kept
     *                    because the context should carry enough actor information for every downstream
     *                    debugging purpose — the caller should not have to re-join two sources when it logs
     *                    a rejected transition.
     * @param currentStatus current persisted status; must not be null
     * @param targetStatus requested status; must not be null
     * @param currentProgressPercent current progress; {@code Integer} and nullable on purpose, mirroring the
     *                               defensive null check the legacy service still performs
     * @param rawBlockedReason untrimmed, possibly null blocked reason supplied by the caller
     * @param managerInScope resolved from {@code TaskPermissionHelper.isManagerOfTaskProjectOrLab}
     * @param leaderInScope resolved from {@code TaskPermissionHelper.isLeaderInTaskGroup}
     * @param assignee resolved from {@code task.getGroupId() != null && TaskPermissionHelper.isTaskAssignee}
     * @param activeGroupMember <strong>must</strong> be resolved by an independent call to
     *                          {@code TaskPermissionHelper.isMemberInTaskGroup}. It must never be inferred
     *                          from {@code canUpdateTaskStatus}, which returns true for an assignee that has
     *                          already left the group.
     * @param hasApprovedReport resolved from {@code ReportRepository.existsLatestApprovedByTaskId}
     * @param requireApprovedReport resolved from
     *                              {@code SystemConfigService...requireApprovedReportBeforeTaskDone}
     */
    public record TaskTransitionContext(
            Long taskId,
            Long actorUserId,
            TaskStatus currentStatus,
            TaskStatus targetStatus,
            Integer currentProgressPercent,
            String rawBlockedReason,
            boolean managerInScope,
            boolean leaderInScope,
            boolean assignee,
            boolean activeGroupMember,
            boolean hasApprovedReport,
            boolean requireApprovedReport
    ) {
    }

    /**
     * Immutable result of a successful evaluation. Holds no entity reference and has no side-effecting
     * method.
     *
     * @param actor the resolved actor category
     * @param fromStatus the unchanged current status
     * @param toStatus the requested target status
     * @param statusUnchanged True when {@code fromStatus == toStatus}.
     *                        WARNING: this does NOT mean "no side effect". A BLOCKED -&gt; BLOCKED evaluation
     *                        with a valid new reason returns statusUnchanged=true AND
     *                        blockedReasonChanged=true. Callers MUST check blockedReasonChanged()
     *                        independently before skipping persist/audit.
     * @param blockedReasonChanged true when the caller must write {@code resolvedBlockedReason} onto the
     *                             task, including when that value is null (clear)
     * @param resolvedBlockedReason trimmed reason, or null when the reason must be cleared
     * @param resolvedProgressPercent {@code Integer} and nullable on purpose: null means "leave the current
     *                                progress untouched". It is never a sentinel.
     */
    public record TaskTransitionDecision(
            TaskWorkflowActor actor,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            boolean statusUnchanged,
            boolean blockedReasonChanged,
            String resolvedBlockedReason,
            Integer resolvedProgressPercent
    ) {
    }

    /**
     * Evaluates a requested status transition and returns what the caller must apply.
     *
     * <p>Evaluation order is part of the contract:
     * <ol>
     *   <li>input shape validation</li>
     *   <li>actor resolution, fail-closed</li>
     *   <li>same-status handling, including the BLOCKED -&gt; BLOCKED reason refresh</li>
     *   <li>matrix lookup for the resolved actor</li>
     *   <li>blocked-reason validation when entering BLOCKED</li>
     *   <li>DONE gate</li>
     *   <li>resolution of blocked reason and progress</li>
     * </ol>
     *
     * @throws IllegalArgumentException mapped to 400 by the global handler
     * @throws AccessDeniedException mapped to 403 by the global handler
     */
    public TaskTransitionDecision evaluate(TaskTransitionContext context) {
        // 1. input shape
        if (context == null) {
            throw new IllegalArgumentException(CONTEXT_REQUIRED);
        }
        if (context.currentStatus() == null) {
            throw new IllegalArgumentException(CURRENT_STATUS_REQUIRED);
        }
        if (context.targetStatus() == null) {
            throw new IllegalArgumentException(TARGET_STATUS_REQUIRED);
        }

        // 2. actor resolution, fail-closed — must precede everything else
        TaskWorkflowActor actor = resolveActor(context);

        TaskStatus from = context.currentStatus();
        TaskStatus to = context.targetStatus();

        // 3. same-status branches
        if (from == to) {
            String trimmedReason = trimToNull(context.rawBlockedReason());
            if (to == TaskStatus.BLOCKED && trimmedReason != null) {
                // N1 — refresh the blocked reason while the task stays BLOCKED.
                return new TaskTransitionDecision(actor, from, to, true, true, trimmedReason, null);
            }
            // N2 / N3 — pure no-op; any supplied reason is ignored and nothing is thrown.
            return new TaskTransitionDecision(actor, from, to, true, false, null, null);
        }

        // 4. matrix lookup
        if (!allowedTargets(actor, from).contains(to)) {
            throw new IllegalArgumentException(notAllowedMessage(context));
        }

        // 5. blocked reason validation when entering BLOCKED from another status
        String trimmedReason = trimToNull(context.rawBlockedReason());
        if (to == TaskStatus.BLOCKED && trimmedReason == null) {
            throw new IllegalArgumentException(BLOCKED_REASON_REQUIRED);
        }

        // 6. DONE gate, from the two booleans only
        if (to == TaskStatus.DONE && context.requireApprovedReport() && !context.hasApprovedReport()) {
            throw new IllegalArgumentException(APPROVED_REPORT_REQUIRED);
        }

        // 7. resolve blocked reason and progress
        boolean blockedReasonChanged;
        String resolvedBlockedReason;
        if (to == TaskStatus.BLOCKED) {
            blockedReasonChanged = true;
            resolvedBlockedReason = trimmedReason;
        } else if (from == TaskStatus.BLOCKED) {
            // Leaving BLOCKED always clears the reason, including when the target is CANCELLED.
            blockedReasonChanged = true;
            resolvedBlockedReason = null;
        } else {
            blockedReasonChanged = false;
            resolvedBlockedReason = null;
        }

        Integer resolvedProgressPercent = resolveProgressPercent(to, context.currentProgressPercent());

        // 8. decision
        return new TaskTransitionDecision(
                actor, from, to, false, blockedReasonChanged, resolvedBlockedReason, resolvedProgressPercent);
    }

    /**
     * Resolves the actor category from the four capability booleans, fail-closed.
     *
     * <p>A LEADER and a MEMBER both require an active membership in the task group. A MANAGER does not:
     * manager scope is verified at project/lab level, and a manager is normally not a member of the group.
     */
    private static TaskWorkflowActor resolveActor(TaskTransitionContext context) {
        if (context.managerInScope()) {
            return TaskWorkflowActor.MANAGER;
        }
        if (context.leaderInScope()) {
            if (!context.activeGroupMember()) {
                throw new AccessDeniedException("Leader is not an active member of the task group");
            }
            return TaskWorkflowActor.LEADER;
        }
        if (context.assignee()) {
            if (!context.activeGroupMember()) {
                throw new AccessDeniedException("Assignee is not an active member of the task group");
            }
            return TaskWorkflowActor.MEMBER;
        }
        throw new AccessDeniedException("Actor may not update this task status");
    }

    private static Integer resolveProgressPercent(TaskStatus targetStatus, Integer currentProgressPercent) {
        if (targetStatus == TaskStatus.IN_PROGRESS) {
            if (currentProgressPercent == null || currentProgressPercent < IN_PROGRESS_MINIMUM_PROGRESS) {
                return IN_PROGRESS_MINIMUM_PROGRESS;
            }
            return currentProgressPercent;
        }
        if (targetStatus == TaskStatus.IN_REVIEW) {
            // Hard overwrite, never a maximum: a task at 95 is pulled down to 90.
            return IN_REVIEW_PROGRESS;
        }
        if (targetStatus == TaskStatus.DONE) {
            return DONE_PROGRESS;
        }
        return null;
    }

    private static Set<TaskStatus> allowedTargets(TaskWorkflowActor actor, TaskStatus from) {
        Map<TaskStatus, Set<TaskStatus>> matrix = switch (actor) {
            case MANAGER -> MANAGER_TRANSITIONS;
            case LEADER -> LEADER_TRANSITIONS;
            case MEMBER -> MEMBER_TRANSITIONS;
        };
        return matrix.getOrDefault(from, Collections.emptySet());
    }

    private static String notAllowedMessage(TaskTransitionContext context) {
        return "Task " + context.taskId()
                + ": status transition from " + context.currentStatus()
                + " to " + context.targetStatus()
                + " is not allowed";
    }

    /**
     * Trims {@code value} and treats it as blank when nothing "visible" remains.
     *
     * <p>{@link String#isBlank()} / {@link String#strip()} only recognise
     * {@link Character#isWhitespace(int)}. That leaves invisible-but-not-whitespace code points — zero-width
     * space (U+200B), zero-width non-joiner/joiner (U+200C/U+200D), the byte-order mark (U+FEFF), NUL
     * (U+0000), RTL override (U+202E) and other {@link Character#FORMAT}/{@link Character#CONTROL} code
     * points — able to slip through as a "non-blank" reason while being semantically empty. A reason
     * consisting solely of such code points must be rejected exactly like a whitespace-only reason.
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        boolean hasVisibleContent = trimmed.codePoints()
                .anyMatch(cp -> !Character.isWhitespace(cp)
                        && Character.getType(cp) != Character.FORMAT
                        && Character.getType(cp) != Character.CONTROL);
        return hasVisibleContent ? trimmed : null;
    }

    private static Map<TaskStatus, Set<TaskStatus>> managerTransitions() {
        Map<TaskStatus, Set<TaskStatus>> matrix = new EnumMap<>(TaskStatus.class);
        matrix.put(TaskStatus.BACKLOG,
                EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED));
        matrix.put(TaskStatus.TODO,
                EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED));
        matrix.put(TaskStatus.IN_PROGRESS,
                EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED, TaskStatus.CANCELLED));
        matrix.put(TaskStatus.IN_REVIEW,
                EnumSet.of(TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.BLOCKED, TaskStatus.CANCELLED));
        matrix.put(TaskStatus.NEEDS_REVISION,
                EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED));
        matrix.put(TaskStatus.BLOCKED,
                EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED));
        return unmodifiable(matrix);
    }

    private static Map<TaskStatus, Set<TaskStatus>> leaderTransitions() {
        Map<TaskStatus, Set<TaskStatus>> matrix = new EnumMap<>(TaskStatus.class);
        matrix.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        matrix.put(TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED));
        matrix.put(TaskStatus.IN_REVIEW, EnumSet.of(TaskStatus.NEEDS_REVISION, TaskStatus.BLOCKED));
        matrix.put(TaskStatus.NEEDS_REVISION, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        matrix.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.IN_PROGRESS));
        return unmodifiable(matrix);
    }

    private static Map<TaskStatus, Set<TaskStatus>> memberTransitions() {
        Map<TaskStatus, Set<TaskStatus>> matrix = new EnumMap<>(TaskStatus.class);
        matrix.put(TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS));
        matrix.put(TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED));
        matrix.put(TaskStatus.NEEDS_REVISION, EnumSet.of(TaskStatus.IN_PROGRESS));
        matrix.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.IN_PROGRESS));
        return unmodifiable(matrix);
    }

    private static Map<TaskStatus, Set<TaskStatus>> unmodifiable(Map<TaskStatus, Set<TaskStatus>> source) {
        Map<TaskStatus, Set<TaskStatus>> copy = new EnumMap<>(TaskStatus.class);
        source.forEach((from, targets) -> copy.put(from, Collections.unmodifiableSet(targets)));
        return Collections.unmodifiableMap(copy);
    }
}
