-- Optional detail metadata described by the research milestone workflow.
-- Update and upload flows are intentionally outside this migration's scope.

ALTER TABLE milestones
    ADD COLUMN assigned_to_student_id BIGINT NULL AFTER created_by,
    ADD COLUMN evidence_url VARCHAR(1000) NULL AFTER assigned_to_student_id,
    ADD COLUMN manager_comment TEXT NULL AFTER evidence_url,
    ADD CONSTRAINT fk_milestone_assigned_student
        FOREIGN KEY (assigned_to_student_id) REFERENCES users (id);
