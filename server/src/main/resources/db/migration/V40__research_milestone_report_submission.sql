-- ============================================================
-- V40__research_milestone_report_submission.sql
-- Detailed milestone/task progress reports with uploaded evidence
-- ============================================================

ALTER TABLE reports
    MODIFY COLUMN task_id BIGINT NULL,
    ADD COLUMN project_id BIGINT NULL AFTER id,
    ADD COLUMN group_id BIGINT NULL AFTER project_id,
    ADD COLUMN milestone_id BIGINT NULL AFTER group_id,
    ADD COLUMN submitted_by_id BIGINT NULL AFTER task_id,
    ADD COLUMN title VARCHAR(200) NULL AFTER version,
    ADD COLUMN content_done TEXT NULL AFTER title,
    ADD COLUMN result TEXT NULL AFTER content_done,
    ADD COLUMN difficulty TEXT NULL AFTER result,
    ADD COLUMN next_plan TEXT NULL AFTER difficulty,
    ADD COLUMN self_assessment TEXT NULL AFTER next_plan,
    ADD COLUMN file_name VARCHAR(255) NULL AFTER file_url,
    ADD COLUMN file_type VARCHAR(100) NULL AFTER file_name,
    ADD COLUMN file_size BIGINT NULL AFTER file_type,
    ADD COLUMN evidence_link VARCHAR(1000) NULL AFTER file_size,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED' AFTER evidence_link,
    ADD COLUMN submission_scope VARCHAR(120) NULL AFTER status;

UPDATE reports r
JOIN tasks t ON t.id = r.task_id
JOIN milestones m ON m.id = t.milestone_id
SET r.milestone_id = m.id,
    r.project_id = m.project_id,
    r.submitted_by_id = t.assignee_id;

UPDATE reports r
JOIN projects p ON p.id = r.project_id
LEFT JOIN research_groups g ON g.project_id = p.id
SET r.group_id = COALESCE(p.group_id, g.id)
WHERE r.group_id IS NULL;

UPDATE reports
SET submission_scope = CONCAT(
    'M:', COALESCE(CAST(milestone_id AS CHAR), '_'),
    ':T:', COALESCE(CAST(task_id AS CHAR), '_'),
    ':U:', COALESCE(CAST(submitted_by_id AS CHAR), '_')
),
    title = CONCAT('Báo cáo phiên bản ', version),
    content_done = 'Dữ liệu từ báo cáo đã nộp trước khi nâng cấp biểu mẫu.',
    result = 'Xem tài liệu đính kèm.',
    difficulty = 'Chưa cập nhật.',
    next_plan = 'Chưa cập nhật.',
    self_assessment = 'Chưa cập nhật.',
    file_name = 'Tài liệu báo cáo';

ALTER TABLE reports
    DROP INDEX uk_report_task_version,
    MODIFY COLUMN milestone_id BIGINT NOT NULL,
    MODIFY COLUMN submission_scope VARCHAR(120) NOT NULL,
    MODIFY COLUMN title VARCHAR(200) NOT NULL,
    MODIFY COLUMN content_done TEXT NOT NULL,
    MODIFY COLUMN result TEXT NOT NULL,
    MODIFY COLUMN difficulty TEXT NOT NULL,
    MODIFY COLUMN next_plan TEXT NOT NULL,
    MODIFY COLUMN self_assessment TEXT NOT NULL,
    MODIFY COLUMN file_name VARCHAR(255) NOT NULL,
    ADD CONSTRAINT fk_report_project FOREIGN KEY (project_id) REFERENCES projects (id),
    ADD CONSTRAINT fk_report_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    ADD CONSTRAINT fk_report_milestone FOREIGN KEY (milestone_id) REFERENCES milestones (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_report_submitter FOREIGN KEY (submitted_by_id) REFERENCES users (id),
    ADD CONSTRAINT uk_report_submission_version UNIQUE (submission_scope, version),
    ADD INDEX idx_reports_milestone_created (milestone_id, created_at),
    ADD INDEX idx_reports_submitter (submitted_by_id);
