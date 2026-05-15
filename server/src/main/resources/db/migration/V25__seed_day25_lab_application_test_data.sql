-- ============================================================
-- V25__seed_day25_lab_application_test_data.sql
-- Day 25: simple test data for Lab & Application flows
--
-- Login accounts:
--   manager01@labportal.com / manager123
--   manager02@labportal.com / manager123
--   manager03@labportal.com / manager123
--   manager04@labportal.com / manager123
--   manager05@labportal.com / manager123
--
--   student01@labportal.com / user123
--   student02@labportal.com / user123
--   student03@labportal.com / user123
--   student04@labportal.com / user123
--   student05@labportal.com / user123
-- ============================================================

-- BCrypt hashes reused from existing seed data:
--   manager123: $2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm
--   user123:    $2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y

-- ------------------------------------------------------------
-- 1. Seed 5 lab managers
-- ------------------------------------------------------------
INSERT IGNORE INTO users (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at) VALUES
    ('manager01@labportal.com', 'manager01', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 01', '0900000001', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager02@labportal.com', 'manager02', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 02', '0900000002', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager03@labportal.com', 'manager03', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 03', '0900000003', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager04@labportal.com', 'manager04', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 04', '0900000004', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager05@labportal.com', 'manager05', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 05', '0900000005', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

-- Admin has effectively assigned LAB_MANAGER permission to these users.
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'LAB_MANAGER'
WHERE u.username IN ('manager01', 'manager02', 'manager03', 'manager04', 'manager05');

-- ------------------------------------------------------------
-- 2. Seed 5 students for application testing
-- ------------------------------------------------------------
INSERT IGNORE INTO users (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at) VALUES
    ('student01@labportal.com', 'student01', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Student 01', '0910000001', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student02@labportal.com', 'student02', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Student 02', '0910000002', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student03@labportal.com', 'student03', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Student 03', '0910000003', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student04@labportal.com', 'student04', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Student 04', '0910000004', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student05@labportal.com', 'student05', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Student 05', '0910000005', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'STUDENT'
WHERE u.username IN ('student01', 'student02', 'student03', 'student04', 'student05');

-- ------------------------------------------------------------
-- 3. Seed 5 labs and assign each lab to one manager
-- ------------------------------------------------------------
INSERT INTO laboratories (lab_name, description, location, capacity, department, status, manager_id, active, deleted, created_at, updated_at)
SELECT 'AI Research Lab', 'Simple test lab for artificial intelligence and machine learning.', 'Building A - Room 101', 30, 'Computer Science', 'AVAILABLE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username = 'manager01'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    location = VALUES(location),
    capacity = VALUES(capacity),
    department = VALUES(department),
    status = VALUES(status),
    manager_id = VALUES(manager_id),
    active = TRUE,
    deleted = FALSE,
    updated_at = NOW(6);

INSERT INTO laboratories (lab_name, description, location, capacity, department, status, manager_id, active, deleted, created_at, updated_at)
SELECT 'Robotics Lab', 'Simple test lab for robotics, embedded systems, and automation.', 'Building B - Room 202', 24, 'Mechatronics', 'AVAILABLE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username = 'manager02'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    location = VALUES(location),
    capacity = VALUES(capacity),
    department = VALUES(department),
    status = VALUES(status),
    manager_id = VALUES(manager_id),
    active = TRUE,
    deleted = FALSE,
    updated_at = NOW(6);

INSERT INTO laboratories (lab_name, description, location, capacity, department, status, manager_id, active, deleted, created_at, updated_at)
SELECT 'Data Science Lab', 'Simple test lab for analytics, visualization, and data engineering.', 'Building C - Room 303', 28, 'Information Systems', 'AVAILABLE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username = 'manager03'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    location = VALUES(location),
    capacity = VALUES(capacity),
    department = VALUES(department),
    status = VALUES(status),
    manager_id = VALUES(manager_id),
    active = TRUE,
    deleted = FALSE,
    updated_at = NOW(6);

INSERT INTO laboratories (lab_name, description, location, capacity, department, status, manager_id, active, deleted, created_at, updated_at)
SELECT 'Cybersecurity Lab', 'Simple test lab for security testing and network defense.', 'Building D - Room 404', 20, 'Cybersecurity', 'AVAILABLE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username = 'manager04'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    location = VALUES(location),
    capacity = VALUES(capacity),
    department = VALUES(department),
    status = VALUES(status),
    manager_id = VALUES(manager_id),
    active = TRUE,
    deleted = FALSE,
    updated_at = NOW(6);

INSERT INTO laboratories (lab_name, description, location, capacity, department, status, manager_id, active, deleted, created_at, updated_at)
SELECT 'IoT Innovation Lab', 'Simple test lab for IoT devices, sensors, and cloud prototypes.', 'Building E - Room 505', 26, 'Computer Engineering', 'AVAILABLE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username = 'manager05'
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    location = VALUES(location),
    capacity = VALUES(capacity),
    department = VALUES(department),
    status = VALUES(status),
    manager_id = VALUES(manager_id),
    active = TRUE,
    deleted = FALSE,
    updated_at = NOW(6);

-- ------------------------------------------------------------
-- 4. Seed applications for manager review screens
-- ------------------------------------------------------------
INSERT IGNORE INTO applications (user_id, lab_id, cv_url, status, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'https://example.com/cv/student01-ai.pdf', 'PENDING', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'AI Research Lab'
WHERE s.username = 'student01';

INSERT IGNORE INTO applications (user_id, lab_id, cv_url, status, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'https://example.com/cv/student02-ai.pdf', 'PENDING', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'AI Research Lab'
WHERE s.username = 'student02';

INSERT IGNORE INTO applications (user_id, lab_id, cv_url, status, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'https://example.com/cv/student03-robotics.pdf', 'PENDING', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'Robotics Lab'
WHERE s.username = 'student03';

INSERT IGNORE INTO applications (user_id, lab_id, cv_url, status, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'https://example.com/cv/student04-data.pdf', 'PENDING', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'Data Science Lab'
WHERE s.username = 'student04';

INSERT IGNORE INTO applications (user_id, lab_id, cv_url, status, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'https://example.com/cv/student05-cybersecurity.pdf', 'PENDING', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'Cybersecurity Lab'
WHERE s.username = 'student05';

INSERT IGNORE INTO applications (user_id, lab_id, cv_url, status, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'https://example.com/cv/student01-iot.pdf', 'APPROVED', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'IoT Innovation Lab'
WHERE s.username = 'student01';

INSERT IGNORE INTO applications (user_id, lab_id, cv_url, status, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'https://example.com/cv/student02-robotics.pdf', 'REJECTED', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'Robotics Lab'
WHERE s.username = 'student02';

-- Approved seed application should also have a matching membership.
INSERT IGNORE INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT s.id, l.id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users s
JOIN laboratories l ON l.lab_name = 'IoT Innovation Lab'
WHERE s.username = 'student01';
