# P2-DECISION-02: Research Task Metadata Patch Contract

## Status

APPROVED

## Context

Research task metadata must be editable without allowing the metadata endpoint to become a status transition API, a project transfer API, or a replacement for task activity history.

PATCH requests must distinguish an omitted property from an explicitly null property. The distinction is required because several optional relations and values can be cleared, while required metadata such as title, priority, and type cannot be cleared.

## Decision

The canonical metadata endpoint is `PATCH /api/research/tasks/{taskId}` and returns the existing `Response<TaskResponse>` wrapper with `200 OK` for both actual updates and valid no-op requests.

The request is presence-aware. Each recognized JSON property records presence independently from its typed value. An omitted property preserves the current value. An explicitly null property has the semantics defined below.

| Field | Manager | Leader | Explicit null |
| --- | --- | --- | --- |
| `groupId` | Allowed | Forbidden, including the current value or null | Clears the group when transition rules allow it |
| `milestoneId` | Allowed | Allowed | Clears the milestone without changing status |
| `parentTaskId` | Allowed | Allowed | Clears the parent |
| `title` | Allowed | Allowed | Rejected |
| `description` | Allowed | Allowed | Clears the description |
| `assigneeId` | Allowed | Allowed | Clears the assignee |
| `priority` | Allowed | Allowed | Rejected |
| `type` | Allowed | Allowed | Rejected |
| `dueDate` | Allowed | Allowed | Clears both `dueDate` and legacy `deadline` |

- Managers may patch tasks only in the exact active, non-deleted laboratory they manage.
- Leaders may patch only group-level tasks for which they have active, non-deleted `LEADER` membership in the task's current group.
- A Leader cannot include `groupId` in the request, even when the value is unchanged or null. The final group remains the group the Leader is authorized to lead.
- Ordinary Members and Students have no metadata patch permission. Being assigned to a task does not grant metadata permission.
- Unknown properties are rejected with `400 Bad Request`, including requests that also contain recognized properties.
- An empty JSON object is rejected with `400 Bad Request` before task lookup.
- Explicit null `title`, `priority`, or `type` is rejected. A provided title is trimmed and must remain nonblank.
- Clearing or assigning a milestone does not change task status and does not invoke transition logic.
- Clearing a group is a special transition only when the current group is non-null and the final group is null. Only a Manager may perform it, the task must currently be `BACKLOG`, and the final assignee, parent, and milestone must be project-level or null.
- Sending `groupId: null` for an already project-level task is an ordinary no-op and does not require `BACKLOG` status.
- The candidate final state is validated before the task entity is mutated. Group, milestone, parent, and assignee references must be active, non-deleted, and compatible with the unchanged project and final group scope.
- A final assignee requires a final group and active, non-deleted membership in that group. No additional system-role check is required.
- A parent must share the task project and final group using null-safe ID comparison. Self-parenting, a proposed cycle, an existing ancestor cycle, and unusable or scope-invalid ancestors are rejected.
- A valid no-op returns `200 OK` after authorization and final-state validation, without saving, auditing, or intentionally changing `updatedAt`.
- An actual update is saved once and audited once with `UPDATE_RESEARCH_TASK`, module `RESEARCH`, target type `RESEARCH_TASK`, and metadata containing `changedFields`.
- Changed field names use this stable order: `groupId`, `milestoneId`, `parentTaskId`, `title`, `description`, `assigneeId`, `priority`, `type`, `dueDate`.
- `dueDate` is the only audit field name for changes that synchronize canonical `dueDate` and legacy `deadline`.
- Task update and audit participate in one production transaction. An audit runtime failure rolls back the task update.

## Consequences

- Clients can safely clear optional metadata without ambiguity and cannot patch server-owned or lifecycle fields through this endpoint.
- Managers can reorganize task scope while retained references are checked against the final group.
- Leaders can maintain metadata within their current group but cannot transfer tasks between groups.
- Concurrent authorized updates acquire a pessimistic write lock after the initial authorization check, then revalidate authorization and scope against the locked task.
- Audit records describe only the fields that actually changed and do not store full old or new values.

## Out of Scope

- Project transfer.
- Status transitions, blocked-reason changes, and progress changes.
- Task activity history.
- Task proposal, notification, AI, comments, attachments, and frontend work.
- The canonical transition engine and status endpoint planned for P2-T6 and P2-T7.
