-- ============================================================
-- V33__research_day31_group_project_fields.sql
-- UC11/UC12 group and project fields
-- ============================================================

ALTER TABLE research_groups
    ADD COLUMN description TEXT NULL AFTER name,
    ADD COLUMN research_direction VARCHAR(200) NULL AFTER description,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER research_direction;

ALTER TABLE projects
    ADD COLUMN objective TEXT NULL AFTER description,
    ADD COLUMN created_by BIGINT NULL AFTER end_date,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    MODIFY COLUMN start_date DATE NULL,
    ADD INDEX idx_project_created_by (created_by),
    ADD CONSTRAINT fk_project_created_by FOREIGN KEY (created_by) REFERENCES users (id);
