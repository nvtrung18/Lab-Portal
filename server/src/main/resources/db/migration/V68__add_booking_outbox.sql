CREATE TABLE booking_outbox_event (
    event_id CHAR(36) PRIMARY KEY,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    locked_at TIMESTAMP(6) NULL,
    delivered_at TIMESTAMP(6) NULL,
    last_error_code VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,

    CONSTRAINT chk_booking_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
    INDEX idx_booking_outbox_ready (status, next_attempt_at, created_at),
    INDEX idx_booking_outbox_locked (status, locked_at),
    INDEX idx_booking_outbox_delivered (status, delivered_at),
    INDEX idx_booking_outbox_aggregate (aggregate_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
