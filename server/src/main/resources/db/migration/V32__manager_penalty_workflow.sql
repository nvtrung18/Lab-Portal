ALTER TABLE penalties
    ADD COLUMN lab_id BIGINT NULL AFTER user_id,
    ADD COLUMN slot_id BIGINT NULL AFTER lab_id,
    ADD COLUMN created_by BIGINT NULL AFTER booking_id,
    ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'OTHER' AFTER created_by,
    ADD COLUMN point INT NOT NULL DEFAULT 0 AFTER type;

UPDATE penalties p
JOIN bookings b ON b.id = p.booking_id
SET p.lab_id = b.lab_id,
    p.slot_id = b.slot_id
WHERE p.lab_id IS NULL OR p.slot_id IS NULL;

ALTER TABLE penalties
    DROP FOREIGN KEY fk_penalty_booking;

ALTER TABLE penalties
    DROP INDEX uk_penalty_booking;

ALTER TABLE penalties
    MODIFY booking_id BIGINT NULL,
    MODIFY lab_id BIGINT NOT NULL,
    MODIFY slot_id BIGINT NOT NULL,
    MODIFY reason VARCHAR(1000) NOT NULL;

ALTER TABLE penalties
    ADD INDEX idx_penalty_lab (lab_id),
    ADD INDEX idx_penalty_slot (slot_id),
    ADD INDEX idx_penalty_booking (booking_id),
    ADD INDEX idx_penalty_created_by (created_by),
    ADD CONSTRAINT uk_penalty_booking_type_status UNIQUE (booking_id, type, status),
    ADD CONSTRAINT fk_penalty_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    ADD CONSTRAINT fk_penalty_slot FOREIGN KEY (slot_id) REFERENCES time_slots (id),
    ADD CONSTRAINT fk_penalty_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    ADD CONSTRAINT fk_penalty_created_by FOREIGN KEY (created_by) REFERENCES users (id);
