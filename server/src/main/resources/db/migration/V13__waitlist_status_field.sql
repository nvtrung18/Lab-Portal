-- V13__waitlist_status_field.sql
-- Day 13: Waitlist Status and Promotion Feature
--
-- FEATURE: Track waitlist entry status for promotion
-- - Add status column to waitlists table (PENDING, PROMOTED, CANCELLED)
-- - Enable soft delete (keep historical records for audit trail)
-- - Create index for efficient promotion queries
--
-- CHANGELOG:
-- - Add status column with default PENDING
-- - Add index on (slot_id, status, position) for findFirst query
--
-- PROMOTION LOGIC:
-- - When booking is cancelled, query waitlists with status=PENDING
-- - Get first user (ORDER BY position ASC LIMIT 1)
-- - Create new booking, update status to PROMOTED
-- - Atomic transaction ensures consistency

ALTER TABLE waitlists ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING' AFTER position;

-- Index for efficient promotion queries: findFirstBySlotIdAndStatusOrderByPositionAsc
ALTER TABLE waitlists ADD INDEX idx_waitlist_slot_status_position (slot_id, status, position);

-- Ensure status is NOT NULL after default applied
ALTER TABLE waitlists MODIFY COLUMN status VARCHAR(20) NOT NULL;

-- Update existing records to PENDING (should already be from DEFAULT)
UPDATE waitlists SET status = 'PENDING' WHERE status IS NULL;

-- Add comment for clarity
ALTER TABLE waitlists MODIFY COLUMN status VARCHAR(20) NOT NULL 
COMMENT 'Status: PENDING (in queue), PROMOTED (moved to booking), CANCELLED (removed)';
