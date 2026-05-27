-- ============================================================
-- FE checkpoint day 35 - large development dataset
-- MySQL 8.x, run manually after Flyway has migrated through V39.
--
-- This file is intentionally outside db/migration so it is never
-- executed automatically in production.
--
-- Test passwords reused from existing development seeds:
--   manager01@labportal.com / manager123
--   manager02@labportal.com / manager123
--   student01@labportal.com / user123
--   student02@labportal.com / user123
--   ...
--   student20@labportal.com / user123
-- ============================================================

START TRANSACTION;

-- ------------------------------------------------------------
-- 1. Accounts and roles
-- ------------------------------------------------------------
INSERT IGNORE INTO users
    (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at)
VALUES
    ('manager01@labportal.com', 'manager01', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Quan ly AI Research Lab', '0900000001', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager02@labportal.com', 'manager02', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Quan ly Robotics Lab', '0900000002', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

INSERT IGNORE INTO users
    (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at)
VALUES
    ('student01@labportal.com', 'student01', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 01', '0910000001', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student02@labportal.com', 'student02', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 02', '0910000002', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student03@labportal.com', 'student03', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 03', '0910000003', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student04@labportal.com', 'student04', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 04', '0910000004', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student05@labportal.com', 'student05', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 05', '0910000005', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student06@labportal.com', 'student06', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 06', '0910000006', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student07@labportal.com', 'student07', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 07', '0910000007', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student08@labportal.com', 'student08', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 08', '0910000008', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student09@labportal.com', 'student09', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 09', '0910000009', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student10@labportal.com', 'student10', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 10', '0910000010', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student11@labportal.com', 'student11', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 11', '0910000011', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student12@labportal.com', 'student12', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 12', '0910000012', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student13@labportal.com', 'student13', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 13', '0910000013', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student14@labportal.com', 'student14', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 14', '0910000014', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student15@labportal.com', 'student15', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 15', '0910000015', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student16@labportal.com', 'student16', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 16', '0910000016', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student17@labportal.com', 'student17', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 17', '0910000017', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student18@labportal.com', 'student18', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 18', '0910000018', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student19@labportal.com', 'student19', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 19', '0910000019', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student20@labportal.com', 'student20', '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y', 'Sinh viên 20', '0910000020', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'LAB_MANAGER'
WHERE u.username IN ('manager01', 'manager02');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'STUDENT'
WHERE u.username REGEXP '^student(0[1-9]|1[0-9]|20)$';

-- ------------------------------------------------------------
-- 2. Laboratories and memberships
-- ------------------------------------------------------------
INSERT INTO laboratories
    (lab_name, description, location, capacity, department, status, manager_id, active, deleted, created_at, updated_at)
SELECT 'AI Research Lab', '[D35] Dữ liệu lớn kiểm thử frontend NCKH và booking.', 'Tòa A - Phòng 101', 40,
       'Khoa học máy tính', 'AVAILABLE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username = 'manager01'
  AND NOT EXISTS (SELECT 1 FROM laboratories WHERE lab_name = 'AI Research Lab');

INSERT INTO laboratories
    (lab_name, description, location, capacity, department, status, manager_id, active, deleted, created_at, updated_at)
SELECT 'Robotics Lab', '[D35] Dữ liệu kiểm thử phân tách PTN và selector sinh viên.', 'Tòa B - Phòng 202', 30,
       'Cơ điện tử', 'AVAILABLE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username = 'manager02'
  AND NOT EXISTS (SELECT 1 FROM laboratories WHERE lab_name = 'Robotics Lab');

SET @ai_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'AI Research Lab' AND deleted = FALSE LIMIT 1);
SET @robotics_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'Robotics Lab' AND deleted = FALSE LIMIT 1);
SET @manager01_id := (SELECT id FROM users WHERE username = 'manager01' LIMIT 1);
SET @manager02_id := (SELECT id FROM users WHERE username = 'manager02' LIMIT 1);

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @ai_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username REGEXP '^student(0[1-9]|1[0-5])$'
ON DUPLICATE KEY UPDATE role = VALUES(role), active = TRUE, updated_at = NOW(6);

-- student01 participates in both labs to verify the student lab selector.
INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @robotics_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('student01', 'student16', 'student17', 'student18', 'student19', 'student20')
ON DUPLICATE KEY UPDATE role = VALUES(role), active = TRUE, updated_at = NOW(6);

-- ------------------------------------------------------------
-- 3. Research projects and groups
-- ------------------------------------------------------------
INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, 'D35-AI-FACE', 'Hệ thống điểm danh sinh viên bằng khuôn mặt',
       'Thị giác máy tính', '[D35] Đề tài chính có Task Board dữ liệu lớn.',
       'Xây dựng luồng nhận diện và check-in thử nghiệm.', 'ONGOING', '2026-05-01', '2026-08-31',
       'HIGH', 'Prototype nhận diện; API check-in', 'Độ chính xác; độ ổn định UI',
       @manager01_id, @manager01_id, TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE code = 'D35-AI-FACE' AND deleted = FALSE);

INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, 'D35-AI-LAB', 'Ứng dụng AI trong quản lý phòng lab',
       'Phân tích dữ liệu', '[D35] Đề tài thứ hai cho danh sách và phân quyền manager.',
       'Phân tích lịch sử sử dụng PTN.', 'PLANNED', '2026-06-01', '2026-09-30',
       'MEDIUM', 'Mô hình dự báo', 'Tính khả dụng',
       @manager01_id, @manager01_id, TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE code = 'D35-AI-LAB' AND deleted = FALSE);

INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
SELECT @robotics_lab_id, 'D35-ROBOT', 'Điều khiển robot kiểm tra thiết bị PTN',
       'Robotics', '[D35] Dữ liệu đối chứng không được lẫn vào AI Research Lab.',
       'Kiểm tra phân tách dữ liệu theo PTN.', 'ONGOING', '2026-05-15', '2026-10-31',
       'MEDIUM', 'Robot demo', 'Khả năng điều khiển',
       @manager02_id, @manager02_id, TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE code = 'D35-ROBOT' AND deleted = FALSE);

SET @face_project_id := (SELECT id FROM projects WHERE code = 'D35-AI-FACE' AND deleted = FALSE ORDER BY id DESC LIMIT 1);
SET @lab_project_id := (SELECT id FROM projects WHERE code = 'D35-AI-LAB' AND deleted = FALSE ORDER BY id DESC LIMIT 1);
SET @robot_project_id := (SELECT id FROM projects WHERE code = 'D35-ROBOT' AND deleted = FALSE ORDER BY id DESC LIMIT 1);

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @face_project_id, '[D35] Nhóm nhận diện khuôn mặt', 'Huấn luyện và đánh giá mô hình.',
       'Hoàn thành pipeline nhận diện.', 'Nghiên cứu, thử nghiệm và tích hợp.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student01'
  AND NOT EXISTS (SELECT 1 FROM research_groups WHERE project_id = @face_project_id AND name = '[D35] Nhóm nhận diện khuôn mặt' AND deleted = FALSE);

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @face_project_id, '[D35] Nhóm cơ sở dữ liệu và API', 'Thiết kế dữ liệu và API task.',
       'Đảm bảo API ổn định.', 'Thiết kế, triển khai và test.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student09'
  AND NOT EXISTS (SELECT 1 FROM research_groups WHERE project_id = @face_project_id AND name = '[D35] Nhóm cơ sở dữ liệu và API' AND deleted = FALSE);

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @lab_project_id, '[D35] Nhóm giao diện check-in', 'Giao diện booking và check-in.',
       'Kiểm tra responsive data list.', 'Dựng UI và kiểm thử.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student02'
  AND NOT EXISTS (SELECT 1 FROM research_groups WHERE project_id = @lab_project_id AND name = '[D35] Nhóm giao diện check-in' AND deleted = FALSE);

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @lab_project_id, '[D35] Nhóm kiểm thử và báo cáo', 'Kiểm thử dữ liệu lớn.',
       'Đánh giá UI checkpoint.', 'Lập testcase và ghi lỗi.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student06'
  AND NOT EXISTS (SELECT 1 FROM research_groups WHERE project_id = @lab_project_id AND name = '[D35] Nhóm kiểm thử và báo cáo' AND deleted = FALSE);

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @robotics_lab_id, @robot_project_id, '[D35] Nhóm điều khiển robot', 'Nhóm thuộc Robotics Lab.',
       'Kiểm thử scope PTN.', 'Theo dõi thiết bị.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student01'
  AND NOT EXISTS (SELECT 1 FROM research_groups WHERE project_id = @robot_project_id AND name = '[D35] Nhóm điều khiển robot' AND deleted = FALSE);

SET @face_group_id := (SELECT id FROM research_groups WHERE project_id = @face_project_id AND name = '[D35] Nhóm nhận diện khuôn mặt' AND deleted = FALSE LIMIT 1);
SET @api_group_id := (SELECT id FROM research_groups WHERE project_id = @face_project_id AND name = '[D35] Nhóm cơ sở dữ liệu và API' AND deleted = FALSE LIMIT 1);
SET @ui_group_id := (SELECT id FROM research_groups WHERE project_id = @lab_project_id AND name = '[D35] Nhóm giao diện check-in' AND deleted = FALSE LIMIT 1);
SET @qa_group_id := (SELECT id FROM research_groups WHERE project_id = @lab_project_id AND name = '[D35] Nhóm kiểm thử và báo cáo' AND deleted = FALSE LIMIT 1);
SET @robot_group_id := (SELECT id FROM research_groups WHERE project_id = @robot_project_id AND name = '[D35] Nhóm điều khiển robot' AND deleted = FALSE LIMIT 1);

INSERT IGNORE INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @face_group_id, u.id, IF(u.username = 'student01', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username REGEXP '^student0[1-8]$';

INSERT IGNORE INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @api_group_id, u.id, IF(u.username = 'student09', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username REGEXP '^student(09|1[0-5])$';

INSERT IGNORE INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @ui_group_id, u.id, IF(u.username = 'student02', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username REGEXP '^student0[1-5]$';

INSERT IGNORE INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @qa_group_id, u.id, IF(u.username = 'student06', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username REGEXP '^student(0[6-9]|10)$';

INSERT IGNORE INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @robot_group_id, u.id, IF(u.username = 'student01', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username IN ('student01', 'student16', 'student17', 'student18', 'student19', 'student20');

-- ------------------------------------------------------------
-- 4. Five milestones for the face attendance project
-- ------------------------------------------------------------
INSERT INTO milestones
    (project_id, name, title, description, start_date, end_date, deadline, status, progress_percent,
     created_by, assigned_to_student_id, active, deleted, created_at, updated_at)
SELECT @face_project_id, seed.title, seed.title, seed.description, seed.start_date, seed.deadline, seed.deadline,
       seed.status, seed.progress, @manager01_id, u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu' AS title, 'Tổng hợp tài liệu nền tảng.' AS description, '2026-05-01' AS start_date, '2026-05-20' AS deadline, 'COMPLETED' AS status, 100 AS progress, 'student01' AS owner
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', 'Khảo sát FaceNet và metric.', '2026-05-15', '2026-06-05', 'IN_PROGRESS', 75, 'student02'
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', 'Thiết kế schema và API.', '2026-05-20', '2026-06-15', 'IN_PROGRESS', 45, 'student09'
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', 'Tích hợp pipeline nhận diện.', '2026-06-01', '2026-07-05', 'IN_PROGRESS', 30, 'student04'
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', 'Kiểm thử end-to-end.', '2026-07-01', '2026-08-15', 'NOT_STARTED', 0, 'student06'
) seed
JOIN users u ON u.username = seed.owner
WHERE NOT EXISTS (
    SELECT 1 FROM milestones m WHERE m.project_id = @face_project_id AND m.title = seed.title AND m.deleted = FALSE
);

-- ------------------------------------------------------------
-- 5. Fifty Kanban tasks, exactly:
--    TODO 15, DOING 12, WAITING_REVIEW 8,
--    NEEDS_REVISION 5, DONE 8, OVERDUE 2.
-- ------------------------------------------------------------
INSERT INTO tasks
    (milestone_id, assignee_id, title, description, deadline, status, progress_percent,
     active, deleted, created_at, updated_at)
SELECT m.id, u.id, seed.title, seed.description, seed.deadline, seed.status, seed.progress,
       TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu' AS milestone, '[D35] M1-01 Tổng hợp yêu cầu điểm danh' AS title, 'Phân tích yêu cầu và phạm vi.' AS description, 'student01' AS assignee, '2026-05-08' AS deadline, 'DONE' AS status, 100 AS progress
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-02 Đọc bài báo FaceNet', 'Tóm tắt phương pháp FaceNet.', 'student02', '2026-05-09', 'DONE', 100
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-03 Khảo sát ArcFace', 'So sánh ArcFace và FaceNet.', 'student03', '2026-05-10', 'DONE', 100
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-04 Tổng hợp bộ dữ liệu', 'Ghi nhận nguồn dữ liệu thử nghiệm.', 'student04', '2026-05-11', 'DONE', 100
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-05 Đánh giá rủi ro riêng tư', 'Liệt kê rủi ro dữ liệu khuôn mặt.', 'student05', '2026-05-12', 'DONE', 100
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-06 Thiết kế checklist nghiên cứu', 'Tạo checklist khảo sát.', 'student06', '2026-05-13', 'DONE', 100
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-07 Tổng hợp tiêu chí đánh giá', 'Xác định accuracy và latency.', 'student07', '2026-05-14', 'DONE', 100
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-08 Trình bày hướng tiếp cận', 'Chuẩn bị buổi trình bày.', 'student08', '2026-05-15', 'DONE', 100
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-09 Bổ sung nguồn tham khảo', 'Bổ sung tài liệu còn thiếu.', 'student01', '2026-05-18', 'OVERDUE', 35
    UNION ALL SELECT '[D35] Mốc 1: Tìm hiểu bài toán, đọc tài liệu', '[D35] M1-10 Hoàn thiện biên bản khảo sát', 'Chốt biên bản đọc tài liệu.', 'student02', '2026-05-20', 'OVERDUE', 55
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-01 Benchmark FaceNet', 'Chạy benchmark ban đầu.', 'student01', '2026-06-01', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-02 Benchmark ArcFace', 'Chạy benchmark đối chứng.', 'student02', '2026-06-01', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-03 Thiết lập tiền xử lý ảnh', 'Chuẩn hóa crop ảnh.', 'student03', '2026-06-02', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-04 Kiểm thử điều kiện ánh sáng', 'Đo ảnh hưởng ánh sáng.', 'student04', '2026-06-02', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-05 Kiểm thử góc mặt', 'Đo ảnh hưởng góc quay.', 'student05', '2026-06-03', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-06 Lập bảng kết quả mô hình', 'Tổng hợp metric.', 'student06', '2026-06-03', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-07 Viết nhận xét kết quả', 'Nhận xét lựa chọn mô hình.', 'student07', '2026-06-04', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-08 Chuẩn bị review kỹ thuật', 'Chuẩn bị demo kết quả.', 'student08', '2026-06-05', 'WAITING_REVIEW', 90
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-09 Chỉnh sửa bảng metric', 'Sửa theo góp ý.', 'student01', '2026-06-06', 'NEEDS_REVISION', 70
    UNION ALL SELECT '[D35] Mốc 2: Khảo sát mô hình nhận diện', '[D35] M2-10 Chỉnh sửa slide mô hình', 'Bổ sung minh họa.', 'student02', '2026-06-07', 'NEEDS_REVISION', 60
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-01 Thiết kế bảng sinh viên', 'Xây dựng entity sinh viên.', 'student09', '2026-06-08', 'DOING', 35
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-02 Thiết kế bảng lượt điểm danh', 'Xây dựng lịch sử điểm danh.', 'student10', '2026-06-09', 'DOING', 30
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-03 Thiết kế API khuôn mặt', 'Định nghĩa DTO request.', 'student11', '2026-06-10', 'DOING', 45
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-04 Thiết kế API check-in', 'Định nghĩa response.', 'student12', '2026-06-11', 'DOING', 50
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-05 Tạo sequence diagram', 'Luồng nhận diện.', 'student13', '2026-06-12', 'DOING', 25
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-06 Viết test repository', 'Kiểm tra truy vấn chính.', 'student14', '2026-06-13', 'DOING', 20
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-07 Tối ưu index', 'Rà soát truy vấn danh sách.', 'student15', '2026-06-14', 'DOING', 40
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-08 Chuẩn hóa response API', 'Đồng bộ contract FE.', 'student09', '2026-06-15', 'DOING', 30
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-09 Tạo fixture API', 'Tạo payload test.', 'student10', '2026-06-16', 'DOING', 20
    UNION ALL SELECT '[D35] Mốc 3: Thiết kế cơ sở dữ liệu', '[D35] M3-10 Rà soát bảo mật dữ liệu', 'Kiểm tra scope truy cập.', 'student11', '2026-06-17', 'DOING', 25
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-01 Tích hợp service embedding', 'Tạo service xử lý embedding.', 'student03', '2026-06-20', 'DOING', 30
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-02 Tích hợp endpoint so khớp', 'Kết nối API nhận diện.', 'student04', '2026-06-21', 'DOING', 40
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-03 Sửa threshold nhận diện', 'Điều chỉnh theo review.', 'student05', '2026-06-22', 'NEEDS_REVISION', 65
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-04 Sửa luồng lỗi camera', 'Bổ sung xử lý lỗi.', 'student06', '2026-06-23', 'NEEDS_REVISION', 55
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-05 Sửa log theo dõi', 'Chỉnh cấu trúc log.', 'student07', '2026-06-24', 'NEEDS_REVISION', 50
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-06 Xây dựng màn hình camera', 'Khởi tạo giao diện.', 'student01', '2026-06-25', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-07 Kết nối webcam', 'Kết nối trình duyệt.', 'student02', '2026-06-26', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-08 Hiển thị kết quả', 'Render kết quả nhận diện.', 'student03', '2026-06-27', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-09 Tối ưu latency UI', 'Đo hiệu năng thao tác.', 'student04', '2026-06-28', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 4: Xây dựng module nhận diện', '[D35] M4-10 Tạo bản demo module', 'Chuẩn bị demo.', 'student05', '2026-06-29', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-01 Viết test E2E đăng nhập', 'Kiểm tra auth.', 'student06', '2026-07-05', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-02 Viết test E2E booking', 'Kiểm tra đặt lịch.', 'student07', '2026-07-06', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-03 Viết test E2E check-in', 'Kiểm tra xác nhận.', 'student08', '2026-07-07', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-04 Kiểm thử Task Board', 'Kiểm tra kéo thả.', 'student09', '2026-07-08', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-05 Kiểm thử responsive', 'Kiểm tra màn hình nhỏ.', 'student10', '2026-07-09', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-06 Kiểm thử role manager', 'Kiểm tra scope PTN.', 'student11', '2026-07-10', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-07 Kiểm thử role student', 'Kiểm tra scope nhóm.', 'student12', '2026-07-11', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-08 Ghi nhận lỗi tích hợp', 'Tổng hợp lỗi.', 'student13', '2026-07-12', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-09 Chuẩn bị demo cuối', 'Chuẩn bị dữ liệu.', 'student14', '2026-07-13', 'TODO', 0
    UNION ALL SELECT '[D35] Mốc 5: Tích hợp và kiểm thử', '[D35] M5-10 Tổng kết checkpoint', 'Tổng kết kết quả.', 'student15', '2026-07-14', 'TODO', 0
) seed
JOIN milestones m ON m.project_id = @face_project_id AND m.title = seed.milestone AND m.deleted = FALSE
JOIN users u ON u.username = seed.assignee
LEFT JOIN tasks existing ON existing.milestone_id = m.id AND existing.title = seed.title AND existing.deleted = FALSE
WHERE existing.id IS NULL;

-- ------------------------------------------------------------
-- 6. Slots and bookings for Auth -> Booking -> Research tests
-- ------------------------------------------------------------
INSERT INTO time_slots (lab_id, start_time, end_time, capacity, status, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, seed.start_time, seed.end_time, 20, 'AVAILABLE', TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT TIMESTAMP('2026-06-01 08:00:00') AS start_time, TIMESTAMP('2026-06-01 10:00:00') AS end_time
    UNION ALL SELECT TIMESTAMP('2026-06-02 08:00:00'), TIMESTAMP('2026-06-02 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-03 08:00:00'), TIMESTAMP('2026-06-03 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-04 08:00:00'), TIMESTAMP('2026-06-04 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-05 08:00:00'), TIMESTAMP('2026-06-05 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-06 08:00:00'), TIMESTAMP('2026-06-06 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-07 08:00:00'), TIMESTAMP('2026-06-07 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-08 08:00:00'), TIMESTAMP('2026-06-08 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-09 08:00:00'), TIMESTAMP('2026-06-09 10:00:00')
    UNION ALL SELECT TIMESTAMP('2026-06-10 08:00:00'), TIMESTAMP('2026-06-10 10:00:00')
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM time_slots t
    WHERE t.lab_id = @ai_lab_id AND t.start_time = seed.start_time AND t.end_time = seed.end_time AND t.deleted = FALSE
);

INSERT INTO time_slots (lab_id, start_time, end_time, capacity, status, active, deleted, created_at, updated_at)
SELECT @robotics_lab_id, seed.start_time, seed.end_time, 15, 'AVAILABLE', TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT TIMESTAMP('2026-06-01 13:00:00') AS start_time, TIMESTAMP('2026-06-01 15:00:00') AS end_time
    UNION ALL SELECT TIMESTAMP('2026-06-02 13:00:00'), TIMESTAMP('2026-06-02 15:00:00')
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM time_slots t
    WHERE t.lab_id = @robotics_lab_id AND t.start_time = seed.start_time AND t.end_time = seed.end_time AND t.deleted = FALSE
);

INSERT INTO bookings
    (user_id, lab_id, slot_id, start_time, end_time, status, purpose, participants_count,
     active, deleted, created_at, updated_at)
SELECT u.id, @ai_lab_id, s.id, s.start_time, s.end_time, seed.status, seed.purpose, 1,
       TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT 'student01' AS username, TIMESTAMP('2026-06-01 08:00:00') AS start_time, 'APPROVED' AS status, '[D35] Thử nghiệm camera' AS purpose
    UNION ALL SELECT 'student02', TIMESTAMP('2026-06-01 08:00:00'), 'CHECKED_IN', '[D35] Kiểm thử check-in'
    UNION ALL SELECT 'student03', TIMESTAMP('2026-06-02 08:00:00'), 'PENDING_APPROVAL', '[D35] Thu thập dữ liệu'
    UNION ALL SELECT 'student04', TIMESTAMP('2026-06-03 08:00:00'), 'APPROVED', '[D35] Đánh giá mô hình'
    UNION ALL SELECT 'student05', TIMESTAMP('2026-06-04 08:00:00'), 'CANCELLED_BY_STUDENT', '[D35] Hủy thử lịch'
    UNION ALL SELECT 'student06', TIMESTAMP('2026-06-05 08:00:00'), 'CHECKED_IN', '[D35] Kiểm tra API'
    UNION ALL SELECT 'student07', TIMESTAMP('2026-06-06 08:00:00'), 'APPROVED', '[D35] Kiểm thử responsive'
    UNION ALL SELECT 'student08', TIMESTAMP('2026-06-07 08:00:00'), 'PENDING_APPROVAL', '[D35] Demo Task Board'
    UNION ALL SELECT 'student09', TIMESTAMP('2026-06-08 08:00:00'), 'APPROVED', '[D35] Thiết kế cơ sở dữ liệu'
    UNION ALL SELECT 'student10', TIMESTAMP('2026-06-09 08:00:00'), 'CHECKED_IN', '[D35] Tích hợp kiểm thử'
    UNION ALL SELECT 'student11', TIMESTAMP('2026-06-10 08:00:00'), 'PENDING_APPROVAL', '[D35] Kiểm thử danh sách'
    UNION ALL SELECT 'student12', TIMESTAMP('2026-06-10 08:00:00'), 'APPROVED', '[D35] Test dữ liệu lớn'
) seed
JOIN users u ON u.username = seed.username
JOIN time_slots s ON s.lab_id = @ai_lab_id AND s.start_time = seed.start_time AND s.deleted = FALSE
LEFT JOIN bookings b ON b.user_id = u.id AND b.slot_id = s.id AND b.deleted = FALSE
WHERE b.id IS NULL;

COMMIT;

-- Verification summary after execution.
SELECT 'D35 projects' AS item, COUNT(*) AS total FROM projects WHERE code LIKE 'D35-%' AND deleted = FALSE
UNION ALL SELECT 'D35 groups', COUNT(*) FROM research_groups WHERE name LIKE '[D35]%' AND deleted = FALSE
UNION ALL SELECT 'D35 milestones', COUNT(*) FROM milestones WHERE title LIKE '[D35]%' AND deleted = FALSE
UNION ALL SELECT 'D35 tasks', COUNT(*) FROM tasks WHERE title LIKE '[D35]%' AND deleted = FALSE
UNION ALL SELECT 'AI active members', COUNT(*) FROM memberships WHERE lab_id = @ai_lab_id AND active = TRUE AND deleted = FALSE
UNION ALL SELECT 'D35 bookings', COUNT(*) FROM bookings WHERE purpose LIKE '[D35]%' AND deleted = FALSE;
