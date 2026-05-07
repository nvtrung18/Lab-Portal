-- ============================================================
-- V6__application.sql
-- Create applications table for CV submissions to laboratories
-- ============================================================

CREATE TABLE applications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    lab_id BIGINT NOT NULL,
    cv_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Foreign keys
    CONSTRAINT fk_app_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_app_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id) ON DELETE CASCADE,

    -- Unique constraint to prevent duplicate applications from same user to same lab
    UNIQUE KEY uk_app_user_lab (user_id, lab_id, deleted),

    -- Indexes for query performance
    INDEX idx_app_user (user_id),
    INDEX idx_app_lab (lab_id),
    INDEX idx_app_status (status),
    INDEX idx_app_created (created_at),
    INDEX idx_app_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
