-- Keep this production-only cleanup after V68 and before the later V69+ migrations.
UPDATE users
SET active = FALSE,
    deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE username IN (
    'admin', 'lab_manager', 'user1',
    'manager01', 'manager02', 'manager03', 'manager04', 'manager05',
    'student01', 'student02', 'student03', 'student04', 'student05'
);
