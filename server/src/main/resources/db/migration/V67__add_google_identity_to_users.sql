ALTER TABLE users
    ADD COLUMN google_subject VARCHAR(255) NULL AFTER password,
    ADD CONSTRAINT uk_user_google_subject UNIQUE (google_subject);
