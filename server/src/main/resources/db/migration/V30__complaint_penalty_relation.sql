ALTER TABLE complaints
    ADD COLUMN penalty_id BIGINT NULL AFTER user_id;

ALTER TABLE complaints
    ADD CONSTRAINT uk_complaint_penalty UNIQUE (penalty_id);

ALTER TABLE complaints
    ADD CONSTRAINT fk_complaint_penalty FOREIGN KEY (penalty_id) REFERENCES penalties (id);
