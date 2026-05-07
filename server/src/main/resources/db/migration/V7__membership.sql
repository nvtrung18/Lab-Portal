-- ============================================================
-- V7__membership.sql
-- Create memberships table to track lab members
-- ============================================================

CREATE TABLE memberships (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    lab_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Foreign keys
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_membership_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id) ON DELETE CASCADE,

    -- Unique constraint to prevent duplicate memberships for same user in same lab
    UNIQUE KEY uk_membership_user_lab (user_id, lab_id, deleted),

    -- Indexes for query performance
    INDEX idx_membership_user (user_id),
    INDEX idx_membership_lab (lab_id),
    INDEX idx_membership_role (role),
    INDEX idx_membership_created (created_at),
    INDEX idx_membership_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
