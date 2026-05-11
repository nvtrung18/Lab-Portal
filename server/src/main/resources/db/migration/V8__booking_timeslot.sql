-- ============================================================
-- V8__booking_timeslot.sql
-- Create time_slots table for booking slot management
-- ============================================================

CREATE TABLE time_slots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lab_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Foreign keys
    CONSTRAINT fk_time_slot_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id) ON DELETE CASCADE,

    -- Indexes for query performance
    INDEX idx_time_slots_lab_id (lab_id),
    INDEX idx_time_slots_status (status),
    INDEX idx_time_slots_time_range (start_time, end_time),
    INDEX idx_time_slots_deleted (deleted),
    INDEX idx_time_slots_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add composite index for efficient querying by lab and time
CREATE INDEX idx_time_slots_lab_time ON time_slots (lab_id, start_time, end_time);
