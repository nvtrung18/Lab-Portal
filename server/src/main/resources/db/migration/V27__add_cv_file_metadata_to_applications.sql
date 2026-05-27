-- Support applications with either a CV URL, an uploaded CV file, or both.

ALTER TABLE applications
    MODIFY cv_url VARCHAR(500) NULL,
    ADD COLUMN cv_file_url VARCHAR(500) NULL AFTER cv_url,
    ADD COLUMN cv_file_name VARCHAR(255) NULL AFTER cv_file_url,
    ADD COLUMN cv_content_type VARCHAR(100) NULL AFTER cv_file_name,
    ADD COLUMN cv_size BIGINT NULL AFTER cv_content_type;
