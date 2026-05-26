-- ============================================================
-- V39__research_task_board_read.sql
-- Kanban-ready task metadata and public task status values
-- ============================================================

ALTER TABLE tasks
    ADD COLUMN description TEXT NULL AFTER title,
    ADD COLUMN deadline DATE NULL AFTER description,
    ADD COLUMN progress_percent INT NOT NULL DEFAULT 0 AFTER status,
    ADD INDEX idx_tasks_milestone_deadline (milestone_id, deadline);

UPDATE tasks
SET status = CASE
    WHEN status = 'IN_PROGRESS' THEN 'DOING'
    WHEN status = 'REVIEW' THEN 'WAITING_REVIEW'
    ELSE status
END;
