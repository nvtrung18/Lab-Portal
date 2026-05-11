-- ============================================================
-- V9__booking_timeslot_relation.sql
-- Add slot_id to bookings table for TimeSlot integration
-- ============================================================

-- Add slot_id column and foreign key to bookings table
ALTER TABLE bookings 
ADD COLUMN slot_id BIGINT AFTER lab_id;

-- Add foreign key constraint
ALTER TABLE bookings
ADD CONSTRAINT fk_booking_slot FOREIGN KEY (slot_id) REFERENCES time_slots (id) ON DELETE CASCADE;

-- Add index for slot_id for efficient queries
CREATE INDEX idx_booking_slot ON bookings (slot_id);

-- Add composite index for slot and user (common query pattern)
CREATE INDEX idx_booking_slot_user ON bookings (slot_id, user_id);
