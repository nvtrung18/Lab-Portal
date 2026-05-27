-- UC14 report upload versions are scoped to the assigned task and submitter.
ALTER TABLE reports
    DROP INDEX uk_report_submission_version,
    ADD CONSTRAINT uk_report_task_submitter_version UNIQUE (task_id, submitted_by_id, version);
