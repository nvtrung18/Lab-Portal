-- ============================================================
-- V35__manager_research_projects_by_lab.sql
-- Manager-created research projects scoped directly by laboratory
-- ============================================================

ALTER TABLE projects
    ADD COLUMN lab_id BIGINT NULL AFTER id,
    ADD COLUMN research_direction VARCHAR(200) NULL AFTER title,
    MODIFY COLUMN group_id BIGINT NULL;

UPDATE projects p
JOIN research_groups g ON g.id = p.group_id
SET p.lab_id = g.lab_id
WHERE p.lab_id IS NULL;

ALTER TABLE projects
    MODIFY COLUMN lab_id BIGINT NOT NULL,
    ADD INDEX idx_project_lab (lab_id),
    ADD CONSTRAINT fk_project_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id);
