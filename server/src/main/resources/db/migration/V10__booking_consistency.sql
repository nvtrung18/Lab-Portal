-- V10__booking_consistency.sql
-- Ensures data consistency by preventing duplicate bookings (double booking)
-- and optimizes capacity counting with status filtering.
--
-- MySQL does not support partial indexes with a WHERE clause, so these indexes
-- include the filtering columns and let queries filter by deleted/status.
-- The guards below also make the migration safe if a previous run applied the
-- first DDL statement before failing later in the file.

SET @constraint_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'bookings'
      AND CONSTRAINT_NAME = 'uk_booking_user_slot'
);

SET @sql := IF(
    @constraint_exists = 0,
    'ALTER TABLE bookings ADD CONSTRAINT uk_booking_user_slot UNIQUE (user_id, slot_id, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'bookings'
      AND INDEX_NAME = 'idx_booking_slot_status'
);

SET @sql := IF(
    @index_exists = 0,
    'CREATE INDEX idx_booking_slot_status ON bookings(slot_id, status, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'bookings'
      AND INDEX_NAME = 'idx_booking_user_status'
);

SET @sql := IF(
    @index_exists = 0,
    'CREATE INDEX idx_booking_user_status ON bookings(user_id, status, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'bookings'
      AND INDEX_NAME = 'idx_booking_slot_confirmed'
);

SET @sql := IF(
    @index_exists = 0,
    'CREATE INDEX idx_booking_slot_confirmed ON bookings(slot_id, deleted, status)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
