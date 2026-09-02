ALTER TABLE notifications
    DROP CHECK chk_notifications_target_module;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_target_module CHECK (
        target_module IN ('TASK', 'REPORT', 'PROPOSAL', 'AI', 'FACE', 'BOOKING')
    );
