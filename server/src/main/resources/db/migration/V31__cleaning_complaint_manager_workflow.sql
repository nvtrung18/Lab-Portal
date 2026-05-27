ALTER TABLE cleanings
    DROP FOREIGN KEY fk_cleaning_slot;

ALTER TABLE cleanings
    DROP INDEX uk_cleaning_slot;

ALTER TABLE cleanings
    ADD CONSTRAINT uk_cleaning_slot_staff UNIQUE (slot_id, staff_id);

ALTER TABLE cleanings
    ADD CONSTRAINT fk_cleaning_slot FOREIGN KEY (slot_id) REFERENCES time_slots (id);

ALTER TABLE complaints
    ADD COLUMN resolved_at TIMESTAMP(6) NULL AFTER status,
    ADD COLUMN resolution_note TEXT NULL AFTER resolved_at;
