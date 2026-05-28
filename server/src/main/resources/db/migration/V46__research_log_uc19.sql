-- ============================================================
-- UC19 Research Log timeline
-- ============================================================

CREATE TABLE research_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    group_id BIGINT NULL,
    milestone_id BIGINT NULL,
    task_id BIGINT NULL,
    author_id BIGINT NOT NULL,
    author_name VARCHAR(150) NULL,
    log_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    work_date DATE NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    result TEXT NULL,
    problem TEXT NULL,
    next_plan TEXT NULL,
    evidence_link VARCHAR(1000) NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'GROUP',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_research_log_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_research_log_group FOREIGN KEY (group_id) REFERENCES research_groups (id) ON DELETE SET NULL,
    CONSTRAINT fk_research_log_milestone FOREIGN KEY (milestone_id) REFERENCES milestones (id) ON DELETE SET NULL,
    CONSTRAINT fk_research_log_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT fk_research_log_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT chk_research_log_type CHECK (log_type IN ('MANUAL', 'SYSTEM')),
    CONSTRAINT chk_research_log_visibility CHECK (visibility IN ('PRIVATE', 'GROUP', 'PROJECT')),
    CONSTRAINT chk_research_log_duration CHECK (duration_minutes >= 0),

    INDEX idx_research_logs_project_created (project_id, created_at),
    INDEX idx_research_logs_group (group_id),
    INDEX idx_research_logs_author (author_id),
    INDEX idx_research_logs_type (log_type),
    INDEX idx_research_logs_milestone (milestone_id),
    INDEX idx_research_logs_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
