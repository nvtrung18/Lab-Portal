CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    target_module VARCHAR(50) NOT NULL,
    target_id BIGINT NULL,
    assistant_key VARCHAR(50) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id),
    CONSTRAINT chk_notifications_target_module CHECK (
        target_module IN ('TASK', 'REPORT', 'PROPOSAL', 'AI', 'FACE')
    ),
    CONSTRAINT chk_notifications_assistant_key CHECK (
        assistant_key IS NULL OR assistant_key IN (
            'ADMIN_ASSISTANT', 'LAB_ASSISTANT', 'RESEARCH_ASSISTANT'
        )
    ),

    INDEX idx_notifications_recipient_read_created (recipient_id, is_read, created_at, id),
    INDEX idx_notifications_recipient_created (recipient_id, created_at, id),
    INDEX idx_notifications_target (target_module, target_id),
    INDEX idx_notifications_assistant_created (assistant_key, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
