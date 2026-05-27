ALTER TABLE reports
    ADD COLUMN leader_reviewed_at TIMESTAMP NULL,
    ADD COLUMN leader_comment TEXT NULL;
