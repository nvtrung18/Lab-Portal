-- V10__booking_consistency.sql
-- Ensures data consistency by preventing duplicate bookings (double booking)
-- and optimizes capacity counting with status filtering

-- Add UNIQUE CONSTRAINT on (user_id, slot_id) to prevent one user from booking same slot twice
-- This constraint only applies to non-deleted bookings
ALTER TABLE bookings 
ADD CONSTRAINT uk_booking_user_slot UNIQUE (user_id, slot_id, deleted);

-- Create index to support status-based capacity counting
-- Allows fast queries like: COUNT(*) WHERE slot_id = X AND status = 'CONFIRMED'
CREATE INDEX idx_booking_slot_status ON bookings(slot_id, status, deleted) 
WHERE deleted = false;

-- Composite index for user cancellation queries
CREATE INDEX idx_booking_user_status ON bookings(user_id, status, deleted)
WHERE deleted = false;

-- Index for slot availability checks (used in capacity validation)
CREATE INDEX idx_booking_slot_confirmed ON bookings(slot_id, deleted)
WHERE deleted = false AND status = 'CONFIRMED';

COMMIT;
