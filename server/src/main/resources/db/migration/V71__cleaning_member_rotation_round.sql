ALTER TABLE laboratories
    ADD COLUMN cleaning_rotation_round INT NOT NULL DEFAULT 1;

ALTER TABLE cleanings
    ADD COLUMN rotation_round INT NULL;

CREATE INDEX idx_cleaning_rotation_round_status
    ON cleanings (rotation_round, status, staff_id);
