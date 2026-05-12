-- V12__booking_waitlist.sql
-- Day 12: Waitlist Feature Implementation (UC06)
--
-- FEATURE: Dedicated Waitlist Table
-- - Stores user waitlist positions when time slots are full
-- - Position is auto-calculated (MAX + 1) with pessimistic locking
-- - UNIQUE constraint prevents duplicate entries per (slot, user)
-- - Race condition safe via database constraints + application-level pessimistic locking
--
-- CHANGELOG:
-- - Create waitlists table with columns: id, slot_id, user_id, position, created_at, updated_at
-- - Add UNIQUE constraint on (slot_id, user_id) to prevent user from joining waitlist twice
-- - Add foreign keys to time_slots and users for referential integrity
-- - Add index on (slot_id, position) for efficient position queries
--
-- CONCURRENCY STRATEGY:
-- 1. Application layer uses @Lock(PESSIMISTIC_WRITE) when querying MAX(position)
-- 2. Database enforces UNIQUE(slot_id, user_id) for duplicate prevention
-- 3. Position assignment is atomic: read max + increment + insert in single transaction
-- 4. Timeout: 3 seconds (matches BookingCoreService pattern from Day 11)

CREATE TABLE IF NOT EXISTS waitlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    position INT NOT NULL COMMENT 'Queue position (1-based indexing)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Prevent user from joining same slot waitlist multiple times
    CONSTRAINT uk_waitlist_slot_user UNIQUE KEY (slot_id, user_id),
    
    -- Foreign key constraints
    CONSTRAINT fk_waitlist_slot FOREIGN KEY (slot_id) REFERENCES time_slots(id) ON DELETE CASCADE,
    CONSTRAINT fk_waitlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- Index for efficient position queries and ordering
    INDEX idx_waitlist_slot_position (slot_id, position),
    
    -- Index for user-specific queries
    INDEX idx_waitlist_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Waitlist entries when time slots are full';

-- Insert historical data hint (for documentation)
-- When a user joins a slot's waitlist:
-- 1. SELECT MAX(position) FROM waitlists WHERE slot_id = ? WITH (PESSIMISTIC_WRITE lock in app)
-- 2. Calculate newPosition = MAX + 1 (or 1 if no entries)
-- 3. INSERT INTO waitlists (slot_id, user_id, position) VALUES (?, ?, newPosition)
-- 4. Return WaitlistResponse with position info
