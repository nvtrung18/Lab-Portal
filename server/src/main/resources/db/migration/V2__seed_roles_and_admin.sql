-- ============================================================
-- V2__seed_roles_and_admin.sql
-- Seed initial roles and admin user
-- ============================================================

-- -----------------------------------------------------------
-- 1. Seed roles: ADMIN, LAB_MANAGER, STUDENT
-- -----------------------------------------------------------
INSERT INTO roles (name, description, active, deleted, created_at, updated_at) VALUES
    ('ADMIN',       'System administrator with full access',                TRUE, FALSE, NOW(6), NOW(6)),
    ('LAB_MANAGER', 'Laboratory manager — manages labs and approves bookings', TRUE, FALSE, NOW(6), NOW(6)),
    ('STUDENT',     'Student — can book labs and join research projects',    TRUE, FALSE, NOW(6), NOW(6));

-- -----------------------------------------------------------
-- 2. Seed a default admin user
--    Password: "admin123" hashed with BCrypt (strength 12)
--    $2a$12$LJ3m4ysWnOKRDjey0vKO4.OGaqJDvATqhMJYVRuBjRaC.3mKHaOFa
-- -----------------------------------------------------------
INSERT INTO users (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at) VALUES
    ('admin@labportal.com', 'admin',
     '$2a$12$LJ3m4ysWnOKRDjey0vKO4.OGaqJDvATqhMJYVRuBjRaC.3mKHaOFa',
     'System Administrator', NULL, 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

-- -----------------------------------------------------------
-- 3. Assign ADMIN role to the admin user
-- -----------------------------------------------------------
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN';
