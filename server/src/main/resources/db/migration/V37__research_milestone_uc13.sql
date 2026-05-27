-- ============================================================
-- V37__research_milestone_uc13.sql
-- UC13 milestone contract and authorization-ready metadata
-- ============================================================

ALTER TABLE milestones
    ADD COLUMN title VARCHAR(200) NULL AFTER project_id,
    ADD COLUMN description TEXT NULL AFTER title,
    ADD COLUMN deadline DATE NULL AFTER description,
    ADD COLUMN progress_percent INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN created_by BIGINT NULL AFTER progress_percent,
    MODIFY COLUMN name VARCHAR(200) NULL,
    MODIFY COLUMN start_date DATE NULL,
    MODIFY COLUMN end_date DATE NULL;

UPDATE milestones
SET title = name,
    deadline = end_date,
    status = CASE
        WHEN status = 'PLANNED' THEN 'NOT_STARTED'
        WHEN status = 'DELAYED' THEN 'OVERDUE'
        ELSE status
    END
WHERE title IS NULL;

ALTER TABLE milestones
    MODIFY COLUMN title VARCHAR(200) NOT NULL,
    ADD INDEX idx_milestone_project_deadline (project_id, deadline),
    ADD CONSTRAINT fk_milestone_created_by FOREIGN KEY (created_by) REFERENCES users (id);
