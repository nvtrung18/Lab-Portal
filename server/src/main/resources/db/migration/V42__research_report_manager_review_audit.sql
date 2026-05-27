ALTER TABLE reports
    ADD COLUMN manager_reviewed_at TIMESTAMP NULL,
    ADD COLUMN manager_comment TEXT NULL;
