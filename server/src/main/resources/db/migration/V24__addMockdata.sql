INSERT INTO users (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at) VALUES
    ('lab_manager@labportal.com', 'lab_manager',
     '$2a$12$LJ3m4ysWnOKRDjey0vKO4.OGaqJDvATqhMJYVRuBjRaC.3mKHaOFa',
     'Lab Manager', NULL, 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

-- -----------------------------------------------------------
-- 3. Assign ADMIN role to the admin user
-- -----------------------------------------------------------
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'lab_manager' AND r.name = 'LAB_MANAGER';


INSERT INTO users (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at) VALUES
    ('user1@labportal.com', 'user1',
     '$2a$12$LJ3m4ysWnOKRDjey0vKO4.OGaqJDvATqhMJYVRuBjRaC.3mKHaOFa',
     'User', NULL, 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

-- -----------------------------------------------------------
-- 3. Assign ADMIN role to the admin user
-- -----------------------------------------------------------
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'user1' AND r.name = 'STUDENT';