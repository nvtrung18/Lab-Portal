# P2-DECISION-01: Nullable Milestone for Research Tasks

## Status

APPROVED

## Context

The official task creation API must support project-level and group-level backlog tasks before they are assigned to a milestone.

The existing `tasks.milestone_id` column was non-nullable, which prevented creation of backlog tasks that had not yet been planned into a milestone.

Some earlier Phase 2 execution notes also contained conflicting rules for initial task status and assignee eligibility.

## Decision

- `tasks.milestone_id` is nullable.
- A task without a milestone starts in `BACKLOG`.
- A task with a milestone starts in `TODO`.
- Group-level backlog tasks are supported.
- A project-level task with no group cannot have an assignee.
- If an assignee is provided, a group is required.
- The assignee must be an active, non-deleted member of the selected research group.
- No additional `STUDENT` system-role check is required for an assignee with valid active research-group membership.
- The client cannot provide the initial task status through the official task creation request.

## Consequences

- The database and JPA mapping must allow `milestone_id` to be null.
- Board and backlog queries must support tasks without milestones while preserving project, group, assignee and ownership visibility rules.
- Legacy operations that require a milestone must reject null-milestone tasks with a clear business error rather than fail with a null-pointer error.
- Moving and validating task status through the canonical transition engine remains part of P2-T6 and P2-T7.

## Superseded Rules

This decision supersedes only the conflicting Phase 2 execution-plan rules that:

- derived initial task status from `groupId` instead of `milestoneId`; and
- required an assignee to additionally hold the `STUDENT` system role despite having valid active research-group membership.

All non-conflicting parts of the Phase 2 execution plan remain applicable.
