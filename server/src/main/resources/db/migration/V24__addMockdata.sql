INSERT INTO users (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at) VALUES
    ('lab_manager@labportal.com', 'lab_manager',
     '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm',
     'Lab Manager', NULL, 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

-- -----------------------------------------------------------
-- 3. Assign LAB_MANAGER role to the lab manager user
--    Login password: manager123
-- -----------------------------------------------------------
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'lab_manager' AND r.name = 'LAB_MANAGER';


INSERT INTO users (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at) VALUES
    ('user1@labportal.com', 'user1',
     '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y',
     'User', NULL, 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

-- -----------------------------------------------------------
-- 4. Assign STUDENT role to the default user
--    Login password: user123
-- -----------------------------------------------------------
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'user1' AND r.name = 'STUDENT';
