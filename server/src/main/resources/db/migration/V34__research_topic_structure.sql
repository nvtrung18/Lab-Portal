-- ============================================================
-- V34__research_topic_structure.sql
-- Research topic and document-aligned group/project fields
-- ============================================================

CREATE TABLE research_topics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lab_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    requirements TEXT NULL,
    reference_links TEXT NULL,
    manager_id BIGINT NULL,
    created_by BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECRUITING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_research_topic_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    CONSTRAINT fk_research_topic_manager FOREIGN KEY (manager_id) REFERENCES users (id),
    CONSTRAINT fk_research_topic_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    INDEX idx_research_topic_lab (lab_id),
    INDEX idx_research_topic_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE research_groups
    ADD COLUMN topic_id BIGINT NULL AFTER lab_id,
    ADD COLUMN objective TEXT NULL AFTER description,
    ADD COLUMN plan TEXT NULL AFTER objective,
    ADD INDEX idx_research_group_topic (topic_id),
    ADD CONSTRAINT fk_research_group_topic FOREIGN KEY (topic_id) REFERENCES research_topics (id);

ALTER TABLE projects
    ADD COLUMN topic_id BIGINT NULL AFTER group_id,
    ADD COLUMN code VARCHAR(50) NULL AFTER topic_id,
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' AFTER end_date,
    ADD COLUMN required_products TEXT NULL AFTER priority,
    ADD COLUMN evaluation_criteria TEXT NULL AFTER required_products,
    ADD COLUMN manager_id BIGINT NULL AFTER evaluation_criteria,
    ADD INDEX idx_project_topic (topic_id),
    ADD INDEX idx_project_manager (manager_id),
    ADD CONSTRAINT fk_project_topic FOREIGN KEY (topic_id) REFERENCES research_topics (id),
    ADD CONSTRAINT fk_project_manager FOREIGN KEY (manager_id) REFERENCES users (id);
