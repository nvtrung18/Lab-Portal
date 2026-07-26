# P2-DECISION-03: Canonical Task Status Transition Engine

## Status

APPROVED

## Context

Before P2-T6 the research task status logic was scattered across `TaskServiceImpl`:
`MANAGER_STATUS_TRANSITIONS` (`:56-63`), `STUDENT_STATUS_TRANSITIONS` (`:64-69`), `updateStatus`
(`:363-399`), `assertCanUpdateTaskStatus` (`:401-416`), `applyStatusProgress` (`:418-429`) and
`assertApprovedReportForCompletion` (`:431-435`).

Four problems followed from that layout.

1. **Leader has no identity of its own.** `TaskServiceImpl:385-387` selects the transition table with
   `currentUser.hasRole("LAB_MANAGER") ? MANAGER_STATUS_TRANSITIONS : STUDENT_STATUS_TRANSITIONS`, so a
   group Leader and an ordinary assignee receive exactly the same rights.
2. **Three incompatible definitions of "an assignee may update status" coexist.**
   `TaskPermissionHelper.canUpdateTaskStatus:93-100` delegates to `isScopedTaskAssignee:152-154`, which
   only checks `task.getGroupId() != null && userId.equals(task.getAssigneeId())` and therefore returns
   `true` for an assignee who has already left the group or been deactivated.
   `TaskServiceImpl.assertCanUpdateTaskStatus:401-416` instead resolves an active role at **PROJECT** scope
   through `resolveStudentProjectRole:459-465` and additionally requires a milestone. The new engine uses a
   third, stricter definition.
3. **`blockedReason` is never validated.** A task can reach `BLOCKED` with no reason, and a stale reason
   survives after the task leaves `BLOCKED`.
4. **The DONE policy flag is ignored.** `TaskServiceImpl:392-394` requires an approved report
   unconditionally and never consults
   `SystemConfigService...requireApprovedReportBeforeTaskDone`.

P2-T6 introduces one canonical policy component. It deliberately introduces **no endpoint** and changes
**no existing production file**, so runtime behaviour of `PUT /api/research/tasks/{id}/status` is unchanged
by this decision. The engine becomes a live security boundary only when P2-T7 wires it to the new
`PATCH /api/research/tasks/{taskId}/status` endpoint.

## Decision

### 1. Actor capability model

The engine recognises exactly three actor categories, `MANAGER`, `LEADER` and `MEMBER`, resolved with the
fixed precedence `MANAGER > LEADER > MEMBER`. Resolution uses **only four booleans** supplied by the
caller; the engine never inspects roles, memberships or the security context itself.

| Boolean | Caller must resolve it with |
| --- | --- |
| `managerInScope` | `TaskPermissionHelper.isManagerOfTaskProjectOrLab(userId, task)` (`:118-150`) |
| `leaderInScope` | `TaskPermissionHelper.isLeaderInTaskGroup(userId, task)` (`:109-111`) |
| `assignee` | `task.getGroupId() != null && TaskPermissionHelper.isTaskAssignee(userId, task)` (`:102-107`) |
| `activeGroupMember` | `TaskPermissionHelper.isMemberInTaskGroup(userId, task)` (`:113-116`), as an independent call |

The precedence matches the `||` order in `TaskPermissionHelper.canUpdateTaskStatus:97-99` and
`TaskMetadataPatchService.resolveActorType:132-140`. Because
`MEMBER_ALLOWED ⊂ LEADER_ALLOWED ⊂ MANAGER_ALLOWED`, precedence can never narrow an actor's rights.

### 2. GROUP scope, fail-closed

`LEADER` and `MEMBER` both require `activeGroupMember = true`. `MANAGER` does not, because
`isManagerOfTaskProjectOrLab` verifies authority at project/lab level and a manager is normally not a member
of the task group; requiring group membership from a manager would block legitimate supervision.

All sixteen combinations are specified, and five of them are denied with `403`.

| # | `managerInScope` | `leaderInScope` | `assignee` | `activeGroupMember` | Result | Reason |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | F | F | F | F | **403** | No capability at all |
| 2 | F | F | F | T | **403** | Active member but neither assignee, leader nor manager |
| 3 | F | F | T | F | **403** | Assignee who left the group or was deactivated |
| 4 | F | F | T | T | `MEMBER` | Valid assignee with active membership |
| 5 | F | T | F | F | **403** | Leader in scope but not an active member |
| 6 | F | T | F | T | `LEADER` | Valid leader |
| 7 | F | T | T | F | **403** | Leader and assignee, but membership is not active |
| 8 | F | T | T | T | `LEADER` | Precedence `LEADER > MEMBER` |
| 9 | T | F | F | F | `MANAGER` | A manager needs no group membership |
| 10 | T | F | F | T | `MANAGER` | |
| 11 | T | F | T | F | `MANAGER` | Precedence `MANAGER > MEMBER` |
| 12 | T | F | T | T | `MANAGER` | |
| 13 | T | T | F | F | `MANAGER` | Precedence `MANAGER > LEADER` |
| 14 | T | T | F | T | `MANAGER` | |
| 15 | T | T | T | F | `MANAGER` | |
| 16 | T | T | T | T | `MANAGER` | Full precedence |

Distribution: 8 `MANAGER`, 2 `LEADER`, 1 `MEMBER`, 5 denied. Actor resolution runs before every other check,
so a denied combination can never reach the matrix, the blocked-reason rule or the DONE gate.

### 3. Binding constraints on P2-T7

1. **Do not use `TaskPermissionHelper.canUpdateTaskStatus` as the only gate.** Through
   `isScopedTaskAssignee:152-154` it returns `true` for an assignee who has already left the group.
2. **Resolve `activeGroupMember` with an independent call to
   `TaskPermissionHelper.isMemberInTaskGroup(userId, task)`.** It must never be inferred.
3. **Add dedicated authorization tests** proving all four booleans resolve correctly, including the negative
   cases: an assignee who left the group, a leader whose role was removed, a manager of a different lab.
4. **Load the task with `findByIdForUpdate`.** Neither `TaskEntity` nor `BaseEntity` declares `@Version`, so
   the pessimistic write lock is the only protection against a lost update.
5. **Check `blockedReasonChanged()` independently of `statusUnchanged()`** before skipping a save or an audit
   entry.
6. **Classify P2-T7 as `HIGH`** and implement it through the `implement-high` gate.
7. **Read `hasApprovedReport`/`requireApprovedReport` inside the same transaction/lock scope used to load and
   persist the task.** Lock the task first with `findByIdForUpdate` (point 4), then read the approved-report
   status, then evaluate and persist — do not read the report status before acquiring the lock, and do not
   evaluate against a value read outside that scope. Otherwise a report can be approved or revoked between the
   check and the write (time-of-check-time-of-use), and a task can be marked `DONE` on a report status that is
   already stale. This is mandatory for P2-T7, not an optional hardening step; P2-T6 itself has no I/O and
   cannot enforce it.

### 4. Literal transition matrix

The matrix is literal, transcribed from the locked source contract, and is never derived or inferred.
`A` marks an allowed transition, `N` marks a same-status request, `—` marks a rejection with `400`.

**MANAGER — 19 allowed pairs.** Source `PHASE_2_EXECUTION_PLAN.md:474-479`; identical to
`TaskServiceImpl.MANAGER_STATUS_TRANSITIONS:56-63`.

| from \ to | BACKLOG | TODO | IN_PROGRESS | IN_REVIEW | NEEDS_REVISION | DONE | BLOCKED | CANCELLED |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **BACKLOG** | N | A | A | — | — | — | — | A |
| **TODO** | — | N | A | — | — | — | A | A |
| **IN_PROGRESS** | — | — | N | A | — | — | A | A |
| **IN_REVIEW** | — | — | — | N | A | A | A | A |
| **NEEDS_REVISION** | — | — | A | — | N | — | A | A |
| **DONE** | — | — | — | — | — | N | — | — |
| **BLOCKED** | — | A | A | — | — | — | N | A |
| **CANCELLED** | — | — | — | — | — | — | — | N |

**LEADER — 9 allowed pairs.** Source `PHASE_2_EXECUTION_PLAN.md:463-471`, header `:462`.

| from \ to | BACKLOG | TODO | IN_PROGRESS | IN_REVIEW | NEEDS_REVISION | DONE | BLOCKED | CANCELLED |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **BACKLOG** | N | — | — | — | — | — | — | — |
| **TODO** | — | N | A | — | — | — | A | — |
| **IN_PROGRESS** | — | — | N | A | — | — | A | — |
| **IN_REVIEW** | — | — | — | N | A | **—** | A | — |
| **NEEDS_REVISION** | — | — | A | — | N | — | A | — |
| **DONE** | — | — | — | — | — | N | — | — |
| **BLOCKED** | — | — | A | — | — | — | N | — |
| **CANCELLED** | — | — | — | — | — | — | — | N |

**MEMBER — 5 allowed pairs.** Source `PHASE_2_EXECUTION_PLAN.md:461`; identical to
`TaskServiceImpl.STUDENT_STATUS_TRANSITIONS:64-69`.

| from \ to | BACKLOG | TODO | IN_PROGRESS | IN_REVIEW | NEEDS_REVISION | DONE | BLOCKED | CANCELLED |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **BACKLOG** | N | — | — | — | — | — | — | — |
| **TODO** | — | N | A | — | — | — | — | — |
| **IN_PROGRESS** | — | — | N | A | — | — | A | — |
| **IN_REVIEW** | — | — | — | N | — | — | — | — |
| **NEEDS_REVISION** | — | — | A | — | N | — | — | — |
| **DONE** | — | — | — | — | — | N | — | — |
| **BLOCKED** | — | — | A | — | — | — | N | — |
| **CANCELLED** | — | — | — | — | — | — | — | N |

Invariants that hold across the three tables:

- `MEMBER_ALLOWED ⊂ LEADER_ALLOWED ⊂ MANAGER_ALLOWED`.
- No actor may transition **into** `BACKLOG`; that status is reachable only at creation time
  (`P2-DECISION-01`).
- No actor may transition **out of** `DONE` or `CANCELLED` (`:480`, `:484`).
- Only `MANAGER` may transition to `CANCELLED`, to `DONE`, out of `BACKLOG`, or `BLOCKED -> TODO`.
- Every actor has exactly 64 `(from, to)` pairs: 19 / 9 / 5 allowed, 8 same-status, and the remainder
  rejected.

Relative to the legacy tables the Manager matrix is unchanged (19 pairs) and the assignee matrix is
unchanged in shape (5 pairs) but stricter in scope. Leader gains four pairs it never had as a distinct
category: `TODO -> BLOCKED`, `IN_REVIEW -> NEEDS_REVISION`, `IN_REVIEW -> BLOCKED` and
`NEEDS_REVISION -> BLOCKED`.

### 5. Leader `IN_REVIEW -> DONE` is denied

`PHASE_2_EXECUTION_PLAN.md:472` states:

> "Leader `IN_REVIEW -> DONE` is denied unless its independent policy is explicitly approved. Unless a
> locked source contract explicitly allows otherwise, Leader must not transition from `BACKLOG`, to
> `CANCELLED`, from `DONE`, from `CANCELLED`, `BLOCKED -> TODO`, or through any source/target pair not
> listed above."

The denial is unconditional. A Leader is refused even when `hasApprovedReport = true` and
`requireApprovedReport = false`, because the matrix is evaluated before the DONE gate. The resulting error
is therefore the matrix message `"... is not allowed"`, not the approved-report message. The same applies to
`MEMBER`.

### 6. `DONE` and `CANCELLED` are absolutely terminal

Neither status has any outgoing transition for any actor (`:480`, `:484`). There is no reopen (`:449`) and
no restore, not even for a manager. `DONE` is reachable only by a `MANAGER` and only from `IN_REVIEW`
(`:477`). `CANCELLED` is reachable only by a `MANAGER`, from six sources.

The single exception is the same-status request: `DONE -> DONE` and `CANCELLED -> CANCELLED` succeed as
pure no-ops, preserving the behaviour of `TaskServiceImpl:381-383`.

### 7. `blockedReason` policy

| # | Case | `blockedReasonChanged` | `resolvedBlockedReason` | Note |
| --- | --- | --- | --- | --- |
| B1 | Entering `BLOCKED` (from ≠ BLOCKED) with a reason that is nonblank after trimming | `true` | the trimmed value | Applies to all three actors (`:481` makes no distinction) |
| B2 | Entering `BLOCKED` (from ≠ BLOCKED) with `null`, `""` or whitespace | — | — | `IllegalArgumentException`, `400` |
| B3 | `BLOCKED -> BLOCKED` with a reason that is nonblank after trimming | `true` | the trimmed value | Status unchanged, progress unchanged, reason refreshed |
| B4 | `BLOCKED -> BLOCKED` with `null` or a blank reason | `false` | `null` | Pure no-op; **does not throw** |
| B5 | Leaving `BLOCKED` (to ≠ BLOCKED, allowed) | `true` | `null` | Always cleared; no resolution note is created (`:487`) |
| B6 | Neither side is `BLOCKED` but the caller still supplies a reason | `false` | `null` | Ignored, not an error (`:481`) |
| B7 | `-> CANCELLED` from a status other than `BLOCKED` | `false` | `null` | `:482` |
| B8 | `BLOCKED -> CANCELLED` | `true` | `null` | Falls under B5: cleared, never given a new reason |
| B9 | Same-status on any status other than `BLOCKED` | `false` | `null` | Pure no-op |

`blockedReasonChanged` is the instruction to write; `resolvedBlockedReason` is the value to write.
`changed == false` means the caller must not touch `task.blockedReason` at all. `changed == true` with a
`null` value means the caller must clear the column.

> **Note on the meaning of "writes".** `PHASE_2_EXECUTION_PLAN.md:482` says "`CANCELLED` never requires or
> writes blocked reason". Here "writes" means *storing a non-null, nonblank value* into `blockedReason`.
> **Clearing** the reason to `null` when a task leaves any status, including leaving `BLOCKED` for
> `CANCELLED` (rule B8), is **not** a "write" in the sense of the original sentence: it is cleanup of stale
> state, not the assignment of a new blocking cause. `CANCELLED` still **never requires** and **never
> receives a new (non-null) `blockedReason`**.
>
> This is a clarification of wording, **not** a supersede. No `S8` entry exists.

No maximum length is enforced by the engine. The column is MySQL `TEXT`
(`V54__research_task_board_upgrade.sql:14`) and `TaskEntity.java:74-75` declares no `@Size`, `length` or
`@NotBlank`. Input-shape limits belong to the P2-T7 request DTO, not to a pure policy component.

### 8. Semantics of `statusUnchanged`

A same-status request is no longer an absolute no-op. `statusUnchanged = true` means exactly one thing:
*the status did not change*. It does **not** mean *nothing happened*.

```java
/**
 * True when {@code fromStatus == toStatus}.
 * WARNING: this does NOT mean "no side effect". A BLOCKED -> BLOCKED evaluation
 * with a valid new reason returns statusUnchanged=true AND blockedReasonChanged=true.
 * Callers MUST check blockedReasonChanged() independently before skipping persist/audit.
 */
```

The field is named `statusUnchanged` rather than `noOp` for this reason: a `noOp = true` decision carrying
`blockedReasonChanged = true` is a serious reading trap, and a caller that writes
`if (decision.statusUnchanged()) return;` would silently discard a legitimate reason update without raising
any exception.

The three same-status branches are:

- **N1** — `BLOCKED -> BLOCKED` with a nonblank trimmed reason: `statusUnchanged = true`,
  `blockedReasonChanged = true`, `resolvedBlockedReason` = the trimmed reason,
  `resolvedProgressPercent = null`. Available to all three actor categories that passed authorization.
- **N2** — `BLOCKED -> BLOCKED` with a null or blank reason: pure no-op, no exception.
- **N3** — same status other than `BLOCKED`: pure no-op; any supplied reason is ignored.

Branch N1 is handled before the matrix lookup, so it is not subject to the transition tables.

### 9. Purity boundary

`TaskWorkflowService` is a `@Component` with an empty constructor and **zero instance fields**. All state is
`private static final` and immutable. It injects nothing, performs no I/O, never reads
`SecurityContextHolder`, never mutates a task, never audits, declares no `@Transactional`, and uses no
nondeterministic source such as `Instant.now`, `System.currentTimeMillis`, `Random` or `UUID.randomUUID`.

The only legitimate imports are `TaskStatus`, `org.springframework.security.access.AccessDeniedException`,
`java.util.*` and `org.springframework.stereotype.Component`. Forbidden tokens include every entity type
(`TaskEntity` above all), every repository, `EntityManager`, `JdbcTemplate`, `TaskPermissionHelper`,
`SystemConfigService`, `AuditLogService`, `@Transactional`, every HTTP DTO and every DI annotation.

Because `TaskTransitionContext` carries no entity reference, mutating a task from inside the engine is
impossible at the type level: purity is guaranteed by the compiler, not by convention. The primary evidence
is reflective:

```java
assertThat(TaskWorkflowService.class.getDeclaredFields())
        .allMatch(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()));

assertThat(TaskWorkflowService.class.getDeclaredConstructors()).hasSize(1);
assertThat(TaskWorkflowService.class.getDeclaredConstructors()[0].getParameterCount()).isZero();
```

Zero instance fields plus a zero-argument constructor is direct proof of zero dependencies. The forbidden
token list is a supplementary check performed during `code-review`.

### 10. The DONE gate is driven by two booleans

```java
if (targetStatus == TaskStatus.DONE && context.requireApprovedReport() && !context.hasApprovedReport()) {
    throw new IllegalArgumentException(APPROVED_REPORT_REQUIRED);
}
```

The engine never calls `ReportRepository.existsLatestApprovedByTaskId` (`ReportRepository.java:139`) or
`SystemConfigService.getConfig()`. P2-T7 reads both and passes the results in. The default configuration is
`new SystemConfigResponse.ResearchConfig(10, true, true, true)`
(`SystemConfigServiceImpl.java:208`), so `requireApprovedReportBeforeTaskDone` defaults to `true`.

The gate runs after the matrix check, which is why a Leader is stopped by the matrix rather than by this
rule. P2-T7 should query the report repository only when `requireApprovedReport == true` and the target is
`DONE`.

### 11. Progress mapping

The engine preserves the semantics of `TaskServiceImpl.applyStatusProgress:418-429`, which
`PHASE_2_EXECUTION_PLAN.md:502` locks as a regression contract.

| Target status | `resolvedProgressPercent` |
| --- | --- |
| `IN_PROGRESS` | `max(currentProgressPercent, 10)`; `null` current is treated as below 10, so the result is `10` |
| `IN_REVIEW` | **`90`, a hard overwrite — not a maximum** |
| `DONE` | `100` |
| `BACKLOG`, `TODO`, `NEEDS_REVISION`, `BLOCKED`, `CANCELLED` | `null` (leave unchanged) |
| Every same-status branch (N1, N2, N3) | `null` (leave unchanged) |

Two properties are load-bearing.

1. **The type is `Integer`, never `int`.** A primitive cannot express "leave unchanged", which would force a
   sentinel: `0` is a legitimate progress value and `-1` would violate the `nullable = false` column
   (`TaskEntity.java:80-82`) if it ever reached the database. `null` means "do not touch"; the caller applies
   the value only when it is non-null.
2. **`IN_REVIEW` overwrites downwards.** A task at 95 moving to `IN_REVIEW` is pulled down to 90, because
   `TaskServiceImpl:423-425` has no `max()` and no guard, unlike the `IN_PROGRESS` branch at `:419-422`.
   This asymmetry is deliberate legacy behaviour and is preserved verbatim.

`NEEDS_REVISION` mapping to `null` mirrors the behaviour locked by the legacy regression test
`updateStatus_managerRequestsRevisionWithoutChangingProgress` (`TaskServiceImplTest.java:586`).

### 12. Error semantics and message format

The engine creates no new exception type. It uses `IllegalArgumentException`, mapped to `400` by
`GlobalExceptionHandler:121`, and `org.springframework.security.access.AccessDeniedException`, mapped to
`403` by `GlobalExceptionHandler:109-115`. It never throws `ResourceNotFoundException` and never produces
`409`; there is no `@Version` anywhere on the task entity and `PHASE_2_EXECUTION_PLAN.md:489` specifies "no
409 by default".

| Condition | Exception | HTTP |
| --- | --- | --- |
| `context == null` | `IllegalArgumentException` | 400 |
| `currentStatus == null` or `targetStatus == null` | `IllegalArgumentException` | 400 |
| Boolean combination in one of the five denied rows | `AccessDeniedException` | **403** |
| `(actor, from, to)` not present in the matrix | `IllegalArgumentException` | 400 |
| Entering `BLOCKED` (from ≠ BLOCKED) with a blank reason (B2) | `IllegalArgumentException` | 400 |
| `-> DONE` with `requireApprovedReport && !hasApprovedReport` | `IllegalArgumentException` | 400 |

The rejection message is:

```
"Task " + taskId + ": status transition from " + currentStatus + " to " + targetStatus + " is not allowed"
```

for example `Task 4271: status transition from IN_REVIEW to DONE is not allowed`. The legacy message at
`TaskServiceImpl:389-390` carries no task id. Including it here is deliberate: the engine logs nothing, so
the message is the only channel through which P2-T7 and operations can trace a rejected transition. The
legacy message is untouched, so no existing regression assertion is affected.

`actorUserId` is carried on the context but is used in no message and in no decision. It is reserved for
logging and tracing at the caller layer, so that a caller logging a rejected transition does not have to
re-join two sources.

## Supersedes

The P2-T6 section of `PHASE_2_EXECUTION_PLAN.md` spans lines `444-507`. The following points of that
section are superseded by this decision. Line numbers were re-read directly from the file.

| # | Superseded statement | Replacement | Status |
| --- | --- | --- | --- |
| S1 | `:448` "workflow service, transition evaluation, **report/config rule integration**" | The engine reads neither reports nor configuration; the caller passes two booleans | SUPERSEDED |
| S2 | `:452` "use `ReportRepository.existsLatestApprovedByTaskId`; workflow ... loads with `findByIdForUpdate`" | The engine injects no repository, loads nothing and locks nothing; loading and locking stay in exactly one place, the caller | SUPERSEDED |
| S3 | `:458` "**Apply** status, blockedReason and progress consistently; return **mutated entity**/response to caller" | The engine mutates no entity; it returns an immutable `TaskTransitionDecision` | SUPERSEDED |
| S4 | `:459` "use `TaskPermissionHelper.canUpdateTaskStatus`" | The engine injects and calls no helper; the caller resolves four booleans. `canUpdateTaskStatus` is additionally judged **too weak** to be the only gate, because `isScopedTaskAssignee:152-154` accepts an assignee who left the group | SUPERSEDED and STRENGTHENED |
| S5 | `:486` "DONE checks approved report only when `requireApprovedReportBeforeTaskDone=true`" | The rule is unchanged, but the engine receives it as two booleans; **reading** the configuration and the report moves to P2-T7 | PARTIALLY SUPERSEDED |
| S6 | `:451` "Expected files to modify: **`TaskServiceImpl`**"; `:490` "old PUT may delegate to the engine"; `:501` "Same-status requests are idempotent no-ops for both endpoint forms" | `TaskServiceImpl` is not modified and does not delegate; the duplication is temporary and deliberate | SUPERSEDED |
| S7 | `:483` "A same-status transition is an **idempotent no-op**" | `BLOCKED -> BLOCKED` with a valid new reason **has a side effect**: the reason is refreshed. "No-op" now means only "the status did not change" | SUPERSEDED |

### Explicitly not superseded

- `:446` — the goal of centralising canonical role-based transitions in `TaskWorkflowService`, with no
  endpoint.
- `:450` — the file name and location, `research/service/TaskWorkflowService.java`, as a concrete service.
- `:474-479` — the Manager matrix, 19 pairs.
- `:463-471` — the literal Leader matrix, 9 pairs; and `:472`, Leader `IN_REVIEW -> DONE` denied.
- `:461` — the Member matrix, 5 pairs.
- `:480` — `DONE` and `CANCELLED` have no outgoing transitions; `:484` — both are terminal.
- `:481` — `BLOCKED` requires a trimmed nonblank `blockedReason`, and a non-BLOCKED target must not be
  interpreted as blocked; `:482` — `CANCELLED` never requires or writes a blocked reason.
- `:485` — `CANCELLED` is distinct from `BLOCKED`.
- `:487` — no resolution-note field is invented when a task leaves `BLOCKED`.
- `:488` — no independent nested transaction.
- `:489` — the error contract of 400/403/404 with no 409 by default.
- `:490` — legacy status values are not reintroduced into the Java enum.
- `:491-502` — the test requirements, parameterized and exhaustive.
- `:449` — the forbidden scope: no endpoint, no frontend alias, no reopen, no notification, no activity
  schema, no audit framework redesign.

The note on the meaning of "writes" in Decision 7 is a clarification of wording and is **not** a supersede;
there is no `S8`.

### Recorded finding, not acted on

After P2-T6, `PHASE_2_EXECUTION_PLAN.md:444-507` contradicts the code in the seven points above. A
cross-reference note in that file pointing to this decision is recommended, but editing the execution plan
is outside the scope of P2-T6 and was not done.

## Deferred debt

Two behavioural divergences from the legacy path are accepted deliberately. Neither has any production
effect at P2-T6, because no caller invokes the engine and `TaskServiceImpl` is untouched.

1. **DONE gate.** `TaskServiceImpl:392-394` requires an approved report unconditionally; the engine requires
   one only when `requireApprovedReport` is true (`:486`). With the default configuration of `true` the two
   agree. They diverge visibly once an administrator turns the flag off, at which point P2-T7 will allow a
   completion that the legacy endpoint refuses.
2. **Scope of MEMBER / assignee.** The engine uses GROUP scope, fail-closed. The legacy
   `assertCanUpdateTaskStatus:401-416` uses PROJECT scope through `resolveStudentProjectRole:459-465` and
   does not fail closed when the user has left the group. There is no corresponding divergence for Leader,
   because the legacy code has no separate Leader concept at all.

No cascade of status to `parentTaskId` or `TaskType.SUBTASK` exists anywhere in the codebase, and P2-T6 adds
none.

## Alternatives considered

- **An orchestrating service, as described by the original spec.** Rejected. Every matrix test would need a
  mocked repository and configuration, an `apply(TaskEntity, ...)` method would reopen the door to side
  effects, and, decisively, the fail-closed group-membership rule would be impossible to implement because
  the engine would never see `activeGroupMember`.
- **Having the caller pass an already-resolved actor enum.** Rejected. Precedence and fail-closed handling
  would escape the tested surface, and different callers could resolve the category differently, which is
  precisely the matrix-drift risk named at `:504`.
- **Delegating the legacy `TaskServiceImpl.updateStatus` to the new engine immediately.** Rejected. It would
  change the behaviour of a live endpoint before the engine has an independent review, and it would couple
  the P2-T6 risk profile to production traffic.
- **PROJECT scope for the assignee, matching `resolveStudentProjectRole`.** Rejected. GROUP scope was chosen
  explicitly, because a task belongs to a group and project-level membership is too broad a claim to update
  a group task.

## Consequences

Positive:

- The policy is testable with no mocks, no Spring context and no database. The suite runs 314 cases in
  well under a second.
- Risk at P2-T6 is effectively zero: no production file changed, no endpoint changed, no migration, no
  schema drift, and the change is reversible by removing three new files.
- The fail-closed rule closes the assignee-left-the-group hole that `canUpdateTaskStatus` leaves open,
  before that hole can be inherited by a new endpoint.
- Leader becomes a first-class actor category with an explicit, reviewable set of rights.

Negative:

- Two parallel sources of truth exist temporarily: `TaskServiceImpl:56-69` serves the legacy `PUT` endpoint
  and `TaskWorkflowService` serves P2-T7. They can drift until the legacy path is retired.
- The engine trusts the four booleans absolutely. Real authorization now depends on the caller resolving
  them correctly, which is why constraints 1 to 3 in Decision 3 are binding.
- The engine is unreachable code until P2-T7 wires it up.
- `statusUnchanged = true` combined with `blockedReasonChanged = true` is a genuine reading trap for a
  careless caller.

## Open follow-ups

- An independent policy for Leader `IN_REVIEW -> DONE`, if the product ever wants it.
- An audit action for status changes. `AuditAction` has no `UPDATE_RESEARCH_TASK_STATUS`, and
  `research/enums/TaskAuditAction.java` declares `TASK_STATUS_CHANGED`, `TASK_BLOCKED`, `TASK_COMPLETED`,
  `TASK_CANCELLED`, `TASK_METADATA_UPDATED`, `TASK_CREATED` and `TASK_ASSIGNED` but is entirely unused. Note
  that branch N1 changes data while leaving the status alone, so it must be audited rather than skipped.
- Consolidating the legacy transition tables with the engine once P2-T7 is live.
- **P2-T7 must apply length-limit and content-sanitization validation on `blockedReason`, at the DTO layer
  (`@Size`) before the raw value is ever passed into `TaskTransitionContext`.** This is a mandatory
  precondition, not an optional hardening step: P2-T6's `trimToNull` only catches the case where the *entire*
  reason is invisible/format code points (zero-width space, BOM, etc.); it enforces no maximum length and does
  not strip invisible characters that appear in the middle of an otherwise valid reason.
- Tightening `TaskPermissionHelper.isScopedTaskAssignee:152-154` so it checks active membership itself. This
  affects six other callers of `canUpdateTaskStatus` and needs its own plan.
- A reopen policy for `DONE`, currently forbidden.
- A cross-reference note in `PHASE_2_EXECUTION_PLAN.md:444-507` pointing to this decision.

## Relations

Extends `P2-DECISION-01` (task creation status: `BACKLOG` without a milestone, `TODO` with one, never
client-supplied) and `P2-DECISION-02` (metadata patch contract, which explicitly excludes status
transitions, blocked-reason changes and progress changes).

Binds P2-T7, which must implement `PATCH /api/research/tasks/{taskId}/status` under the seven constraints in
Decision 3.
