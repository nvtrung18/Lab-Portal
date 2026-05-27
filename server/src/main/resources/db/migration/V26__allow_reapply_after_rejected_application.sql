-- Allow students to submit a new application after their latest one is REJECTED.
-- Duplicate protection is now enforced by service logic for PENDING applications
-- and ACTIVE memberships instead of a hard unique key across all statuses.

ALTER TABLE applications DROP INDEX uk_app_user_lab;

CREATE INDEX idx_app_user_lab_status_deleted
    ON applications (user_id, lab_id, status, deleted);
