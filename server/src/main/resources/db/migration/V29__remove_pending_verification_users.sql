-- Redis OTP registration no longer creates pending users.
DELETE FROM user_roles
WHERE user_id IN (
    SELECT id FROM users WHERE status = 'PENDING_VERIFICATION'
);

DELETE FROM users
WHERE status = 'PENDING_VERIFICATION';
