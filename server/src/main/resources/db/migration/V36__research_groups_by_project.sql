-- ============================================================
-- V36__research_groups_by_project.sql
-- Research groups attached directly to manager-created projects
-- ============================================================

ALTER TABLE research_groups
    ADD COLUMN project_id BIGINT NULL AFTER topic_id,
    ADD INDEX idx_research_group_project (project_id),
    ADD CONSTRAINT fk_research_group_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;
