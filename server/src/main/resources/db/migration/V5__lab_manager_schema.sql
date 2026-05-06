-- ============================================================
-- V5__lab_manager_schema.sql
-- Add manager assignment to laboratories table
-- ============================================================

ALTER TABLE laboratories
ADD COLUMN manager_id BIGINT NULL,
ADD CONSTRAINT fk_lab_manager FOREIGN KEY (manager_id) REFERENCES users (id),
ADD INDEX idx_lab_manager (manager_id);
