-- ============================================================
-- V54__research_task_board_upgrade.sql
-- Phase 1 P1-T1: upgrade existing tasks table for Research Board
-- ============================================================

ALTER TABLE tasks
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD COLUMN group_id BIGINT NULL AFTER project_id,
    ADD COLUMN parent_task_id BIGINT NULL AFTER assignee_id,
    ADD COLUMN epic_id BIGINT NULL AFTER parent_task_id,
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' AFTER status,
    ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'TASK' AFTER priority,
    ADD COLUMN due_date DATE NULL AFTER deadline,
    ADD COLUMN blocked_reason TEXT NULL AFTER due_date,
    ADD COLUMN created_by BIGINT NULL AFTER blocked_reason;

UPDATE tasks t
JOIN milestones m ON m.id = t.milestone_id
LEFT JOIN projects p ON p.id = m.project_id
SET t.project_id = m.project_id,
    t.group_id = COALESCE(m.group_id, p.group_id),
    t.due_date = t.deadline
WHERE t.project_id IS NULL
   OR t.group_id IS NULL
   OR (t.due_date IS NULL AND t.deadline IS NOT NULL);

UPDATE tasks
SET status = CASE
    WHEN status = 'DOING' THEN 'IN_PROGRESS'
    WHEN status = 'WAITING_REVIEW' THEN 'IN_REVIEW'
    WHEN status = 'REVIEW' THEN 'IN_REVIEW'
    WHEN status = 'IN_PROGRESS' THEN 'IN_PROGRESS'
    WHEN status = 'OVERDUE' THEN 'IN_PROGRESS'
    WHEN status = 'CANCELLED' THEN 'CANCELLED'
    WHEN status = 'TODO' THEN 'TODO'
    WHEN status = 'NEEDS_REVISION' THEN 'NEEDS_REVISION'
    WHEN status = 'DONE' THEN 'DONE'
    WHEN status = 'BACKLOG' THEN 'BACKLOG'
    WHEN status = 'BLOCKED' THEN 'BLOCKED'
    WHEN status = 'IN_REVIEW' THEN 'IN_REVIEW'
    ELSE status
END;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id),
    ADD CONSTRAINT fk_tasks_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    ADD CONSTRAINT fk_tasks_parent_task FOREIGN KEY (parent_task_id) REFERENCES tasks (id),
    ADD CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    ADD INDEX idx_tasks_project_status_due_date (project_id, status, due_date),
    ADD INDEX idx_tasks_group_status_due_date (group_id, status, due_date),
    ADD INDEX idx_tasks_assignee_status_due_date (assignee_id, status, due_date);
