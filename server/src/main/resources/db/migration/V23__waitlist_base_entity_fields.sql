-- V23__waitlist_base_entity_fields.sql
-- Align waitlists table with WaitlistEntity, which extends BaseEntity.
-- Existing local databases created by V12 may be missing active/deleted.

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'waitlists'
      AND COLUMN_NAME = 'active'
);

SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE waitlists ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER updated_at',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'waitlists'
      AND COLUMN_NAME = 'deleted'
);

SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE waitlists ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER active',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
