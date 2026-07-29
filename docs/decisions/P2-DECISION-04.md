# P2-DECISION-04 — Canonical Task Status Update API

## Decision

The canonical research-task status operation is exposed as
`PATCH /api/research/tasks/{taskId}/status`. It accepts only the typed
`status` field and an optional `blockedReason`; unknown properties are rejected
locally by the request DTO. The legacy
`PUT /api/tasks/{id}/status` contract remains unchanged.

The use case authorizes the actor and active task before taking a pessimistic
task lock, clears the persistence context, compares scalar scope identifiers,
then re-reads the actor, active project/group scope, exact laboratory ownership,
and active membership with pessimistic current queries before recomputing
cumulative Manager/Leader/assignee/member capabilities. `EntityManager.clear()`
is only a first-level-cache boundary; it does not reset a MySQL/InnoDB
`REPEATABLE_READ` snapshot. The lock order is task, actor, project, group,
laboratory, membership, followed by the applicable report read. The pure
workflow engine remains the sole transition and reason/progress policy authority.

## Boundary and persistence rules

- A non-null blocked reason is limited to 4000 characters and rejects Unicode
  `CONTROL` code points other than TAB, LF, and CR. `FORMAT` code points remain
  transport-valid. Null/blank/all-invisible semantics belong to the workflow
  engine.
- Status/reason/progress mutation and one `UPDATE_RESEARCH_TASK_STATUS` audit
  row share the outer Spring transaction. Audit metadata contains from/to
  status, resolved actor capability, and truthful reason/progress-change flags,
  never reason content.
- Same-status requests are pure no-ops except a valid `BLOCKED` reason refresh,
  which is persisted and audited.
- Every non-null project/group identifier must resolve to an active,
  non-deleted row with a laboratory. When both are present their laboratories
  must agree; fallback is permitted only when an identifier is genuinely null.
- The DONE gate preserves the system-config contract: absent or malformed
  persisted JSON uses the secure default with approved reports required.
  Unexpected null config/research responses fail closed before engine
  evaluation.
- Null milestones are supported; no milestone lookup is required by this use
  case.

P2-DECISION-03 remains authoritative for actor precedence, transition matrix,
blocked-reason semantics, DONE policy, and progress decisions.
