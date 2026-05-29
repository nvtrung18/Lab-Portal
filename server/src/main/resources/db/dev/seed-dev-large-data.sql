-- ============================================================
-- FE checkpoint day 35 - simplified development dataset
-- MySQL 8.x, runs automatically on backend startup if app.dev-seed.enabled=true.
--
-- Each lab has exactly 1 high-quality, realistic research project.
-- Main test project (AI Face Attendance) has 2 groups and complete mockup data
-- to support clean testing for all role views and tabs as requested.
-- ============================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ------------------------------------------------------------
-- 1. Accounts and roles (student01 to student20, manager01 to manager05)
-- ------------------------------------------------------------
INSERT IGNORE INTO users
    (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at)
VALUES
    ('manager01@labportal.com', 'manager01', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 01', '0900000001', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager02@labportal.com', 'manager02', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 02', '0900000002', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager03@labportal.com', 'manager03', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 03', '0900000003', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager04@labportal.com', 'manager04', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 04', '0900000004', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager05@labportal.com', 'manager05', '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm', 'Manager 05', '0900000005', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

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
WHERE u.username IN ('manager01', 'manager02', 'manager03', 'manager04', 'manager05');

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'STUDENT'
WHERE u.username REGEXP '^student(0[1-9]|1[0-9]|20)$';

-- ------------------------------------------------------------
-- 2. Laboratories and memberships mapping
-- ------------------------------------------------------------
SET @ai_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'AI Research Lab' AND deleted = FALSE LIMIT 1);
SET @robotics_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'Robotics Lab' AND deleted = FALSE LIMIT 1);
SET @datascience_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'Data Science Lab' AND deleted = FALSE LIMIT 1);
SET @cybersecurity_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'Cybersecurity Lab' AND deleted = FALSE LIMIT 1);
SET @iot_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'IoT Innovation Lab' AND deleted = FALSE LIMIT 1);

SET @manager01_id := (SELECT id FROM users WHERE username = 'manager01' LIMIT 1);
SET @manager02_id := (SELECT id FROM users WHERE username = 'manager02' LIMIT 1);
SET @manager03_id := (SELECT id FROM users WHERE username = 'manager03' LIMIT 1);
SET @manager04_id := (SELECT id FROM users WHERE username = 'manager04' LIMIT 1);
SET @manager05_id := (SELECT id FROM users WHERE username = 'manager05' LIMIT 1);

-- Standardize memberships (Students belong to their respective Labs to satisfy DB rules)
INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @ai_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username REGEXP '^student(0[1-9]|1[0-5])$'
ON DUPLICATE KEY UPDATE role = VALUES(role), active = TRUE, updated_at = NOW(6);

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @robotics_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('student01', 'student16', 'student17', 'student18', 'student19', 'student20')
ON DUPLICATE KEY UPDATE role = VALUES(role), active = TRUE, updated_at = NOW(6);

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @datascience_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('student02', 'student03', 'student04', 'student05')
ON DUPLICATE KEY UPDATE role = VALUES(role), active = TRUE, updated_at = NOW(6);

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @cybersecurity_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('student03', 'student04', 'student05', 'student06')
ON DUPLICATE KEY UPDATE role = VALUES(role), active = TRUE, updated_at = NOW(6);

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @iot_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('student04', 'student05', 'student06', 'student07')
ON DUPLICATE KEY UPDATE role = VALUES(role), active = TRUE, updated_at = NOW(6);

-- ------------------------------------------------------------
-- 3. Exactly 1 high-quality Research Project per Lab
-- ------------------------------------------------------------

-- AI Research Lab: Hệ thống điểm danh sinh viên bằng khuôn mặt
INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
VALUES
    (@ai_lab_id, 'D35-AI-FACE', 'Hệ thống điểm danh sinh viên bằng khuôn mặt', 'Thị giác máy tính', 
     '[D35] Đề tài chính phục vụ kiểm thử giao diện NCKH, có bảng tiến độ Kanban và dữ liệu lớn.', 
     'Xây dựng luồng nhận dạng khuôn mặt qua webcam và tích hợp API check-in tự động.', 'ONGOING', 
     '2026-05-01', '2026-08-31', 'HIGH', 'Prototype nhận diện qua camera; API gateway check-in', 
     'Tỷ lệ chính xác của mô hình (accuracy); thời gian phản hồi (latency); độ mượt mà của giao diện.', 
     @manager01_id, @manager01_id, TRUE, FALSE, NOW(6), NOW(6));

-- Robotics Lab: Robot hỗ trợ vận chuyển trong phòng thí nghiệm
INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
VALUES
    (@robotics_lab_id, 'D35-ROBOT', 'Robot hỗ trợ vận chuyển trong phòng thí nghiệm', 'Robotics', 
     '[D35] Dự án robot tự hành hỗ trợ vận chuyển vật tư và thiết bị kiểm thử.', 
     'Phát triển hệ thống tự hành điều hướng và tránh vật cản.', 'ONGOING', 
     '2026-05-15', '2026-10-31', 'MEDIUM', 'Mô hình robot tự hành thực tế; thuật toán tránh vật cản', 
     'Độ ổn định khi di chuyển; tốc độ xử lý vật cản.', 
     @manager02_id, @manager02_id, TRUE, FALSE, NOW(6), NOW(6));

-- Data Science Lab: Phân tích dữ liệu sử dụng phòng thí nghiệm
INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
VALUES
    (@datascience_lab_id, 'D35-DS-ANALYTICS', 'Phân tích dữ liệu sử dụng phòng thí nghiệm', 'Khoa học dữ liệu', 
     '[D35] Đề tài phân tích hành vi và tần suất sử dụng thiết bị phòng Lab của sinh viên.', 
     'Xây dựng mô hình dự báo tần suất và lập kế hoạch bảo trì thiết bị.', 'PLANNED', 
     '2026-06-01', '2026-09-30', 'MEDIUM', 'Mô hình dự đoán nhu cầu; Dashboard báo cáo trực quan', 
     'Độ tin cậy của thuật toán dự báo; tính khả dụng của Dashboard.', 
     @manager03_id, @manager03_id, TRUE, FALSE, NOW(6), NOW(6));

-- Cybersecurity Lab: Hệ thống phát hiện truy cập bất thường
INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
VALUES
    (@cybersecurity_lab_id, 'D35-CYBER-IDS', 'Hệ thống phát hiện truy cập bất thường', 'An toàn thông tin', 
     '[D35] Đề tài giám sát và phân tích gói tin mạng để phát hiện xâm nhập trái phép nội bộ.', 
     'Xây dựng hệ thống phát hiện hành vi truy cập mạng bất thường theo thời gian thực.', 'ONGOING', 
     '2026-05-01', '2026-11-30', 'HIGH', 'Module IDS nhận diện gói tin xấu; Tài liệu báo cáo phân tích mạng', 
     'Tỷ lệ phát hiện xâm nhập; số lượng cảnh báo giả.', 
     @manager04_id, @manager04_id, TRUE, FALSE, NOW(6), NOW(6));

-- IoT Innovation Lab: Giám sát môi trường phòng thí nghiệm bằng IoT
INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status, start_date, end_date,
     priority, required_products, evaluation_criteria, manager_id, created_by, active, deleted, created_at, updated_at)
VALUES
    (@iot_lab_id, 'D35-IoT-ENV', 'Giám sát môi trường phòng thí nghiệm bằng IoT', 'Mạng cảm biến', 
     '[D35] Dự án đo đạc nhiệt độ, độ ẩm và các chỉ số không khí bảo vệ thiết bị phòng máy.', 
     'Phát triển trạm đo tích hợp cảm biến thông minh gửi dữ liệu qua giao thức MQTT.', 'ONGOING', 
     '2026-05-15', '2026-08-31', 'LOW', 'Mạch cảm biến IoT; Dashboard Web giám sát môi trường', 
     'Độ chính xác đo đạc; tính ổn định khi truyền dữ liệu không dây.', 
     @manager05_id, @manager05_id, TRUE, FALSE, NOW(6), NOW(6));

-- Retrieve created project IDs
SET @face_project_id := (SELECT id FROM projects WHERE code = 'D35-AI-FACE' LIMIT 1);
SET @robot_project_id := (SELECT id FROM projects WHERE code = 'D35-ROBOT' LIMIT 1);
SET @ds_project_id := (SELECT id FROM projects WHERE code = 'D35-DS-ANALYTICS' LIMIT 1);
SET @cyber_project_id := (SELECT id FROM projects WHERE code = 'D35-CYBER-IDS' LIMIT 1);
SET @iot_project_id := (SELECT id FROM projects WHERE code = 'D35-IoT-ENV' LIMIT 1);

-- ------------------------------------------------------------
-- 4. Seed Research Groups (AI Face Project has 2 groups, others have 1)
-- ------------------------------------------------------------

-- AI Face project groups
INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @face_project_id, 'Nhóm nhận diện khuôn mặt', 
       'Nghiên cứu mô hình so khớp khuôn mặt sâu và tối ưu hóa latency.', 
       'Đạt độ chính xác so khớp trên 99% trong môi trường lab.', 
       'Khảo sát mô hình, lập tập dữ liệu, viết API và tích hợp UI.', 
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student06';

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @face_project_id, 'Nhóm kiểm thử và báo cáo', 
       'Thực hiện kiểm thử tích hợp giao diện webcam và lập kịch bản sự cố.', 
       'Đảm bảo UI hoạt động trơn tru trên mọi cấu hình thiết bị.', 
       'Viết kịch bản kiểm thử, chạy thử nghiệm tải và tối ưu hiệu năng.', 
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student11';

-- Robotics project group
INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @robotics_lab_id, @robot_project_id, 'Nhóm điều hướng robot', 
       'Lập trình điều khiển tự động và thuật toán dò đường cho robot.', 
       'Robot tự hành di chuyển mượt mà tránh chướng ngại vật.', 
       'Thiết kế mạch điều khiển, lập trình tránh vật cản Lidar, chạy thử nghiệm.', 
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student01';

-- Data Science project group
INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @datascience_lab_id, @ds_project_id, 'Nhóm phân tích hành vi sử dụng PTN', 
       'Tổng hợp và làm sạch dữ liệu hoạt động phòng máy để huấn luyện mô hình.', 
       'Xây dựng các mô hình phân tích hành vi sinh viên.', 
       'Thu thập log máy tính, tiền xử lý dữ liệu, phân tích hồi quy.', 
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student02';

-- Cybersecurity project group
INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @cybersecurity_lab_id, @cyber_project_id, 'Nhóm giám sát an toàn hệ thống', 
       'Xây dựng hệ thống phân tích luồng traffic mạng phát hiện port scan và DDoS.', 
       'Ngăn ngừa các đợt tấn công thử nghiệm nội bộ.', 
       'Bắt gói tin, phân tích chữ ký số độc hại, tối ưu hóa thời gian cảnh báo.', 
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student03';

-- IoT project group
INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status, leader_id, active, deleted, created_at, updated_at)
SELECT @iot_lab_id, @iot_project_id, 'Nhóm cảm biến môi trường', 
       'Nghiên cứu thiết bị cảm biến không dây ESP32 và truyền thông MQTT.', 
       'Xây dựng hệ sinh thái giám sát nhiệt độ và khí thải thông minh.', 
       'Thiết kế phần cứng cảm biến, cấu hình MQTT broker, dựng Dashboard theo dõi.', 
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student04';

-- Retrieve Group IDs
SET @face_group_id := (SELECT id FROM research_groups WHERE project_id = @face_project_id AND name = 'Nhóm nhận diện khuôn mặt' LIMIT 1);
SET @qa_group_id := (SELECT id FROM research_groups WHERE project_id = @face_project_id AND name = 'Nhóm kiểm thử và báo cáo' LIMIT 1);
SET @robot_group_id := (SELECT id FROM research_groups WHERE project_id = @robot_project_id AND name = 'Nhóm điều hướng robot' LIMIT 1);
SET @ds_group_id := (SELECT id FROM research_groups WHERE project_id = @ds_project_id AND name = 'Nhóm phân tích hành vi sử dụng PTN' LIMIT 1);
SET @cyber_group_id := (SELECT id FROM research_groups WHERE project_id = @cyber_project_id AND name = 'Nhóm giám sát an toàn hệ thống' LIMIT 1);
SET @iot_group_id := (SELECT id FROM research_groups WHERE project_id = @iot_project_id AND name = 'Nhóm cảm biến môi trường' LIMIT 1);

-- ------------------------------------------------------------
-- 5. Map Students to Groups (Group Memberships)
-- ------------------------------------------------------------

-- Nhóm nhận diện khuôn mặt (Leader: student06, Members: student07 to student10)
INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @face_group_id, id, IF(username = 'student06', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users WHERE username IN ('student06', 'student07', 'student08', 'student09', 'student10');

-- Nhóm kiểm thử và báo cáo (Leader: student11, Members: student12, student13)
INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @qa_group_id, id, IF(username = 'student11', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users WHERE username IN ('student11', 'student12', 'student13');

-- Nhóm điều khiển robot (Leader: student01, Members: student16 to student20)
INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @robot_group_id, id, IF(username = 'student01', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users WHERE username IN ('student01', 'student16', 'student17', 'student18', 'student19', 'student20');

-- Nhóm phân tích dữ liệu (Leader: student02, Members: student03, student04, student05)
INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @ds_group_id, id, IF(username = 'student02', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users WHERE username IN ('student02', 'student03', 'student04', 'student05');

-- Nhóm bảo mật mạng (Leader: student03, Members: student04, student05, student06)
INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @cyber_group_id, id, IF(username = 'student03', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users WHERE username IN ('student03', 'student04', 'student05', 'student06');

-- Nhóm giám sát IoT (Leader: student04, Members: student05, student06, student07)
INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @iot_group_id, id, IF(username = 'student04', 'LEADER', 'MEMBER'), NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM users WHERE username IN ('student04', 'student05', 'student06', 'student07');

-- ------------------------------------------------------------
-- 6. Milestones (5 distinct milestones for the Face Attendance project as specified)
-- ------------------------------------------------------------
SET @student06_id := (SELECT id FROM users WHERE username = 'student06' LIMIT 1);
SET @student07_id := (SELECT id FROM users WHERE username = 'student07' LIMIT 1);
SET @student08_id := (SELECT id FROM users WHERE username = 'student08' LIMIT 1);
SET @student09_id := (SELECT id FROM users WHERE username = 'student09' LIMIT 1);
SET @student10_id := (SELECT id FROM users WHERE username = 'student10' LIMIT 1);
SET @student11_id := (SELECT id FROM users WHERE username = 'student11' LIMIT 1);
SET @student01_id := (SELECT id FROM users WHERE username = 'student01' LIMIT 1);
SET @student02_id := (SELECT id FROM users WHERE username = 'student02' LIMIT 1);
SET @student03_id := (SELECT id FROM users WHERE username = 'student03' LIMIT 1);
SET @student04_id := (SELECT id FROM users WHERE username = 'student04' LIMIT 1);
SET @student05_id := (SELECT id FROM users WHERE username = 'student05' LIMIT 1);
SET @student16_id := (SELECT id FROM users WHERE username = 'student16' LIMIT 1);
SET @student17_id := (SELECT id FROM users WHERE username = 'student17' LIMIT 1);
SET @student18_id := (SELECT id FROM users WHERE username = 'student18' LIMIT 1);

INSERT INTO milestones 
    (project_id, group_id, name, title, description, start_date, end_date, deadline, status, progress_percent,
     created_by, assigned_to_student_id, active, deleted, created_at, updated_at)
VALUES
    (@face_project_id, @face_group_id, 'Khảo sát mô hình nhận diện', 
     'Khảo sát mô hình nhận diện', 'Nghiên cứu cấu trúc triplet loss của FaceNet và ArcFace, so sánh metrics.', 
     '2026-05-01', '2026-05-15', '2026-05-15', 'IN_PROGRESS', 60.00, @manager01_id, @student07_id, TRUE, FALSE, NOW(6), NOW(6)),
     
    (@face_project_id, @face_group_id, 'Xây dựng tập dữ liệu thử nghiệm', 
     'Xây dựng tập dữ liệu thử nghiệm', 'Chụp ảnh mẫu học viên thực tế tại Lab và tiền xử lý, chuẩn hóa kích thước.', 
     '2026-05-16', '2026-05-25', '2026-05-25', 'IN_PROGRESS', 40.00, @manager01_id, @student09_id, TRUE, FALSE, NOW(6), NOW(6)),
     
    (@face_project_id, @face_group_id, 'Phát triển API nhận diện khuôn mặt', 
     'Phát triển API nhận diện khuôn mặt', 'Thiết kế cơ sở dữ liệu lưu trữ vector đặc trưng và viết API đối khớp base64.', 
     '2026-05-26', '2026-06-02', '2026-06-02', 'IN_PROGRESS', 50.00, @manager01_id, @student06_id, TRUE, FALSE, NOW(6), NOW(6)),
     
    (@face_project_id, @face_group_id, 'Tích hợp giao diện check-in', 
     'Tích hợp giao diện check-in', 'Lấy luồng camera từ React UI, chụp và gửi ảnh định kỳ lên API gateway.', 
     '2026-06-03', '2026-06-10', '2026-06-10', 'NOT_STARTED', 0.00, @manager01_id, @student10_id, TRUE, FALSE, NOW(6), NOW(6)),

    (@face_project_id, @face_group_id, 'Kiểm thử và hoàn thiện báo cáo', 
     'Kiểm thử và hoàn thiện báo cáo', 'Tiến hành kiểm thử hệ thống tích hợp, đánh giá độ chính xác và viết báo cáo nghiệm thu.', 
     '2026-06-11', '2026-06-18', '2026-06-18', 'NOT_STARTED', 0.00, @manager01_id, @student08_id, TRUE, FALSE, NOW(6), NOW(6));

-- Retrieve Milestone IDs
SET @ms1_id := (SELECT id FROM milestones WHERE project_id = @face_project_id AND name = 'Khảo sát mô hình nhận diện' LIMIT 1);
SET @ms2_id := (SELECT id FROM milestones WHERE project_id = @face_project_id AND name = 'Xây dựng tập dữ liệu thử nghiệm' LIMIT 1);
SET @ms3_id := (SELECT id FROM milestones WHERE project_id = @face_project_id AND name = 'Phát triển API nhận diện khuôn mặt' LIMIT 1);
SET @ms4_id := (SELECT id FROM milestones WHERE project_id = @face_project_id AND name = 'Tích hợp giao diện check-in' LIMIT 1);
SET @ms5_id := (SELECT id FROM milestones WHERE project_id = @face_project_id AND name = 'Kiểm thử và hoàn thiện báo cáo' LIMIT 1);

-- ------------------------------------------------------------
-- 7. Tasks (Kanban Tasks assigned to students within the AI Face project)
-- ------------------------------------------------------------
INSERT INTO tasks 
    (milestone_id, assignee_id, title, description, deadline, status, progress_percent, active, deleted, created_at, updated_at)
VALUES
    -- Mốc 1: Khảo sát mô hình nhận diện
    (@ms1_id, @student07_id, 'Tìm hiểu FaceNet', 'Đọc tài liệu về FaceNet, hiểu cấu trúc triplet loss.', '2026-05-10', 'DONE', 100, TRUE, FALSE, NOW(6), NOW(6)),
    (@ms1_id, @student08_id, 'So sánh FaceNet và ArcFace', 'Chạy thử nghiệm so sánh hiệu năng và độ chính xác của FaceNet và ArcFace trên LFW.', '2026-05-14', 'WAITING_REVIEW', 90, TRUE, FALSE, NOW(6), NOW(6)),
    (@ms1_id, @student06_id, 'Tổng hợp tài liệu nhận diện khuôn mặt', 'Tập hợp các tài liệu khảo sát và viết báo cáo tổng quan.', '2026-05-15', 'DOING', 50, TRUE, FALSE, NOW(6), NOW(6)),
    
    -- Mốc 2: Xây dựng tập dữ liệu thử nghiệm
    (@ms2_id, @student09_id, 'Thu thập ảnh mẫu', 'Chụp ảnh chân dung của các thành viên Lab ở nhiều góc độ và điều kiện ánh sáng.', '2026-05-22', 'DOING', 40, TRUE, FALSE, NOW(6), NOW(6)),
    (@ms2_id, @student10_id, 'Chuẩn hóa ảnh đầu vào', 'Viết code tiền xử lý, căn chỉnh (align) và crop khuôn mặt về kích thước 160x160.', '2026-05-24', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6)),
    (@ms2_id, @student09_id, 'Gắn nhãn dữ liệu thử nghiệm', 'Gắn nhãn ID tương ứng cho từng tập ảnh chân dung đã chụp.', '2026-05-25', 'NEEDS_REVISION', 80, TRUE, FALSE, NOW(6), NOW(6)),
    
    -- Mốc 3: Phát triển API nhận diện khuôn mặt
    (@ms3_id, @student06_id, 'Thiết kế API nhận diện', 'Thiết kế các endpoint nhận diện và cấu trúc DB lưu trữ vector đặc trưng.', '2026-06-01', 'DOING', 30, TRUE, FALSE, NOW(6), NOW(6)),
    (@ms3_id, @student07_id, 'Tích hợp model vào backend', 'Triển khai inference model FaceNet/ArcFace trên backend Spring Boot.', '2026-06-02', 'WAITING_REVIEW', 90, TRUE, FALSE, NOW(6), NOW(6)),
    
    -- Mốc 4: Tích hợp giao diện check-in
    (@ms4_id, @student10_id, 'Thiết kế màn hình check-in', 'Thiết kế giao diện check-in bằng camera trên React.', '2026-06-07', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6)),
    (@ms4_id, @student08_id, 'Hiển thị kết quả nhận diện', 'Xử lý hiển thị thông báo check-in thành công/thất bại dựa trên kết quả API.', '2026-06-09', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6)),
    
    -- Mốc 5: Kiểm thử và hoàn thiện báo cáo
    (@ms5_id, @student08_id, 'Viết báo cáo tổng kết', 'Tổng kết lại toàn bộ số liệu thử nghiệm và viết báo cáo khoa học.', '2026-06-15', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6)),
    (@ms5_id, @student06_id, 'Chuẩn bị slide demo', 'Chuẩn bị slide và video quay lại quá trình hoạt động thực tế để thuyết trình.', '2026-06-17', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6));

-- Retrieve Task IDs
SET @task_facenet_id := (SELECT id FROM tasks WHERE milestone_id = @ms1_id AND title = 'Tìm hiểu FaceNet' LIMIT 1);
SET @task_compare_id := (SELECT id FROM tasks WHERE milestone_id = @ms1_id AND title = 'So sánh FaceNet và ArcFace' LIMIT 1);
SET @task_doc_id := (SELECT id FROM tasks WHERE milestone_id = @ms1_id AND title = 'Tổng hợp tài liệu nhận diện khuôn mặt' LIMIT 1);
SET @task_dataset_id := (SELECT id FROM tasks WHERE milestone_id = @ms2_id AND title = 'Thu thập ảnh mẫu' LIMIT 1);
SET @task_chuanhoa_id := (SELECT id FROM tasks WHERE milestone_id = @ms2_id AND title = 'Chuẩn hóa ảnh đầu vào' LIMIT 1);
SET @task_label_id := (SELECT id FROM tasks WHERE milestone_id = @ms2_id AND title = 'Gắn nhãn dữ liệu thử nghiệm' LIMIT 1);
SET @task_design_api_id := (SELECT id FROM tasks WHERE milestone_id = @ms3_id AND title = 'Thiết kế API nhận diện' LIMIT 1);
SET @task_backend_id := (SELECT id FROM tasks WHERE milestone_id = @ms3_id AND title = 'Tích hợp model vào backend' LIMIT 1);
SET @task_ui_design_id := (SELECT id FROM tasks WHERE milestone_id = @ms4_id AND title = 'Thiết kế màn hình check-in' LIMIT 1);
SET @task_ui_result_id := (SELECT id FROM tasks WHERE milestone_id = @ms4_id AND title = 'Hiển thị kết quả nhận diện' LIMIT 1);
SET @task_report_id := (SELECT id FROM tasks WHERE milestone_id = @ms5_id AND title = 'Viết báo cáo tổng kết' LIMIT 1);
SET @task_slide_id := (SELECT id FROM tasks WHERE milestone_id = @ms5_id AND title = 'Chuẩn bị slide demo' LIMIT 1);

-- Map old/shared variables to prevent breaking subsequent references
SET @task_api_id := @task_design_api_id;
SET @task_ui_id := @task_backend_id;

-- ------------------------------------------------------------
-- 8. Research Reports (At least 7 reports as specified)
-- ------------------------------------------------------------
INSERT INTO reports 
    (project_id, group_id, milestone_id, task_id, submitted_by_id, version, title, content_done, result, difficulty, next_plan, self_assessment, file_url, file_name, file_type, file_size, evidence_link, status, submission_scope, leader_reviewed_at, leader_comment, manager_reviewed_at, manager_comment, active, deleted, created_at, updated_at)
VALUES
    -- Student 07: Tìm hiểu FaceNet v1
    (@face_project_id, @face_group_id, @ms1_id, @task_facenet_id, @student07_id, 1, 'Báo cáo khảo sát FaceNet v1', 
     'Đã đọc tài liệu, tổng hợp kiến trúc mô hình và thử nghiệm bước đầu.', 
     'Hoàn thành phần khảo sát và có bảng so sánh kết quả thử nghiệm.', 
     'Dữ liệu ảnh chưa đồng đều về ánh sáng và góc chụp.', 
     'Bổ sung dữ liệu thử nghiệm và tối ưu pipeline nhận diện.', 
     'Hoàn thành khoảng 80% khối lượng được giao.', 
     '/storage/reports/task-facenet/v1.pdf', 'bao-cao-facenet-v1.pdf', 'application/pdf', 850000, 
     'https://github.com/lab-portal/face-attendance-demo', 'NEEDS_REVISION', CONCAT('M:', @ms1_id, ':T:', @task_facenet_id, ':U:', @student07_id), NOW(6), 'Cần bổ sung bảng so sánh với ArcFace và mô tả rõ tập dữ liệu thử nghiệm.', NULL, NULL, TRUE, FALSE, NOW(6), NOW(6)),

    -- Student 07: Tìm hiểu FaceNet v2
    (@face_project_id, @face_group_id, @ms1_id, @task_facenet_id, @student07_id, 2, 'Báo cáo khảo sát FaceNet v2', 
     'Đã bổ sung thêm bảng so sánh các metrics lý thuyết giữa FaceNet và ArcFace.', 
     'Hoàn thành phần so sánh và tổng hợp tài liệu.', 
     'Không gặp khó khăn lớn.', 
     'Phối hợp thu thập ảnh mẫu trong Lab.', 
     'Hoàn thành 100% khối lượng được giao.', 
     '/storage/reports/task-facenet/v2.pdf', 'bao-cao-facenet-v2.pdf', 'application/pdf', 1250000, 
     'https://github.com/lab-portal/face-attendance-demo', 'APPROVED', CONCAT('M:', @ms1_id, ':T:', @task_facenet_id, ':U:', @student07_id), NOW(6), 'Báo cáo đạt yêu cầu, duyệt chuyển manager.', NOW(6), 'Báo cáo đã đạt yêu cầu, có thể chuyển sang bước tích hợp thử nghiệm.', TRUE, FALSE, NOW(6), NOW(6)),

    -- Student 08: So sánh FaceNet và ArcFace v1
    (@face_project_id, @face_group_id, @ms1_id, @task_compare_id, @student08_id, 1, 'Báo cáo so sánh FaceNet và ArcFace', 
     'Đã tiến hành benchmark FaceNet và ArcFace trên bộ dữ liệu nhỏ LFW.', 
     'Bảng tổng hợp độ chính xác (FaceNet: 99.2%, ArcFace: 99.6%).', 
     'Latency suy diễn của ArcFace lớn hơn FaceNet trên CPU phòng máy.', 
     'Tối ưu hóa latency mô hình hoặc cấu hình tăng tốc.', 
     'Hoàn thành 90% khối lượng được giao.', 
     '/storage/reports/task-compare/v1.docx', 'so-sanh-facenet-arcface-v1.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 2300000, 
     'https://github.com/lab-portal/face-attendance-demo', 'LEADER_REVIEWED', CONCAT('M:', @ms1_id, ':T:', @task_compare_id, ':U:', @student08_id), NOW(6), 'Cần làm rõ tiêu chí đánh giá giữa hai mô hình.', NULL, NULL, TRUE, FALSE, NOW(6), NOW(6)),

    -- Student 09: Thu thập ảnh mẫu v1
    (@face_project_id, @face_group_id, @ms2_id, @task_dataset_id, @student09_id, 1, 'Báo cáo thu thập ảnh mẫu v1', 
     'Đã chụp ảnh chân dung của 15 sinh viên tại Lab, mỗi người 5 góc độ khác nhau.', 
     'Tập dữ liệu thô gồm 75 hình ảnh góc chụp đa dạng.', 
     'Ảnh chụp góc nghiêng bị mờ nhẹ do chất lượng camera.', 
     'Lọc ảnh mờ và chuẩn hóa kích thước.', 
     'Hoàn thành 85% tiến độ.', 
     '/storage/reports/task-dataset/v1.pdf', 'thu-thap-anh-mau-v1.pdf', 'application/pdf', 1500000, 
     'https://github.com/lab-portal/face-attendance-demo', 'SUBMITTED', CONCAT('M:', @ms2_id, ':T:', @task_dataset_id, ':U:', @student09_id), NULL, NULL, NULL, NULL, TRUE, FALSE, NOW(6), NOW(6)),

    -- Student 10: Chuẩn hóa ảnh đầu vào v1
    (@face_project_id, @face_group_id, @ms2_id, @task_chuanhoa_id, @student10_id, 1, 'Báo cáo chuẩn hóa ảnh đầu vào v1', 
     'Viết script crop ảnh chân dung, căn chỉnh mắt tự động bằng OpenCV.', 
     'Tập ảnh mẫu chuẩn 160x160 pixels, loại bỏ các vùng thừa.', 
     'OpenCV không nhận diện chính xác mắt trong điều kiện thiếu sáng.', 
     'Bổ sung ngưỡng lọc sáng trước khi xử lý.', 
     'Hoàn thành 80% công việc.', 
     '/storage/reports/task-chuanhoa/v1.pdf', 'chuan-hoa-anh-v1.pdf', 'application/pdf', 980000, 
     'https://github.com/lab-portal/face-attendance-demo', 'REJECTED', CONCAT('M:', @ms2_id, ':T:', @task_chuanhoa_id, ':U:', @student10_id), NOW(6), 'Cần bổ sung ảnh minh chứng và mô tả quy trình tiền xử lý.', NULL, NULL, TRUE, FALSE, NOW(6), NOW(6)),

    -- Student 10: Chuẩn hóa ảnh đầu vào v2
    (@face_project_id, @face_group_id, @ms2_id, @task_chuanhoa_id, @student10_id, 2, 'Báo cáo chuẩn hóa ảnh đầu vào v2', 
     'Đã bổ sung các ảnh minh chứng crop mặt và tối ưu ngưỡng lọc sáng.', 
     'Tập ảnh 75 ảnh chuẩn hóa chất lượng tốt, OpenCV chạy ổn định hơn.', 
     'Không gặp khó khăn đáng kể.', 
     'Giao dữ liệu ảnh cho nhóm lưu trữ.', 
     'Hoàn thành 100% công việc.', 
     '/storage/reports/task-chuanhoa/v2.pdf', 'chuan-hoa-anh-v2.pdf', 'application/pdf', 1050000, 
     'https://github.com/lab-portal/face-attendance-demo', 'SUBMITTED', CONCAT('M:', @ms2_id, ':T:', @task_chuanhoa_id, ':U:', @student10_id), NULL, NULL, NULL, NULL, TRUE, FALSE, NOW(6), NOW(6)),

    -- Student 06 (Leader): Tổng hợp tài liệu nhận diện khuôn mặt v1
    (@face_project_id, @face_group_id, @ms1_id, @task_doc_id, @student06_id, 1, 'Báo cáo tổng hợp tài liệu nhận diện khuôn mặt v1', 
     'Định nghĩa tài liệu đặc tả yêu cầu và tổng hợp các nghiên cứu FaceNet, ArcFace.', 
     'Tài liệu đặc tả PDF dài 15 trang đầy đủ cấu trúc hệ thống.', 
     'Tài liệu tham khảo tiếng Việt về ArcFace còn hạn chế.', 
     'Dịch thêm tài liệu tiếng Anh từ tác giả mô hình.', 
     'Hoàn thành 90% khối lượng.', 
     '/storage/reports/task-doc/v1.pdf', 'tong-hop-tai-lieu-v1.pdf', 'application/pdf', 1150000, 
     'https://github.com/lab-portal/face-attendance-demo', 'SUBMITTED', CONCAT('M:', @ms1_id, ':T:', @task_doc_id, ':U:', @student06_id), NULL, NULL, NULL, NULL, TRUE, FALSE, NOW(6), NOW(6));

-- ------------------------------------------------------------
-- 9. Report Comments (At least 8 comments as specified)
-- ------------------------------------------------------------
SET @rep_facenet_v1_id := (SELECT id FROM reports WHERE task_id = @task_facenet_id AND version = 1 LIMIT 1);
SET @rep_facenet_v2_id := (SELECT id FROM reports WHERE task_id = @task_facenet_id AND version = 2 LIMIT 1);
SET @rep_compare_v1_id := (SELECT id FROM reports WHERE task_id = @task_compare_id AND version = 1 LIMIT 1);
SET @rep_chuanhoa_v1_id := (SELECT id FROM reports WHERE task_id = @task_chuanhoa_id AND version = 1 LIMIT 1);
SET @rep_chuanhoa_v2_id := (SELECT id FROM reports WHERE task_id = @task_chuanhoa_id AND version = 2 LIMIT 1);
SET @rep_doc_v1_id := (SELECT id FROM reports WHERE task_id = @task_doc_id AND version = 1 LIMIT 1);

INSERT INTO comments (report_id, author_id, content, active, deleted, created_at, updated_at) VALUES
-- v1 facenet
(@rep_facenet_v1_id, @student06_id, 'Cần bổ sung bảng so sánh với ArcFace và mô tả rõ tập dữ liệu thử nghiệm.', TRUE, FALSE, NOW(6), NOW(6)),
(@rep_facenet_v1_id, @student07_id, 'Em đã cập nhật phần so sánh và nộp lại ở phiên bản v2.', TRUE, FALSE, NOW(6), NOW(6)),

-- v2 facenet
(@rep_facenet_v2_id, @manager01_id, 'Báo cáo đã đạt yêu cầu, có thể chuyển sang bước tích hợp thử nghiệm.', TRUE, FALSE, NOW(6), NOW(6)),

-- v1 compare
(@rep_compare_v1_id, @student06_id, 'Cần làm rõ tiêu chí đánh giá giữa hai mô hình.', TRUE, FALSE, NOW(6), NOW(6)),
(@rep_compare_v1_id, @student08_id, 'Dạ em đã bổ sung thêm phần so sánh tham số ở phụ lục.', TRUE, FALSE, NOW(6), NOW(6)),

-- v1 chuanhoa
(@rep_chuanhoa_v1_id, @student06_id, 'Cần bổ sung ảnh minh chứng và mô tả quy trình tiền xử lý.', TRUE, FALSE, NOW(6), NOW(6)),
(@rep_chuanhoa_v1_id, @student10_id, 'Em đã chụp thêm ảnh màn hình kết quả crop của OpenCV.', TRUE, FALSE, NOW(6), NOW(6)),

-- v2 chuanhoa
(@rep_chuanhoa_v2_id, @student06_id, 'Báo cáo v2 tốt hơn nhiều, anh duyệt chuyển quản lý.', TRUE, FALSE, NOW(6), NOW(6));

-- ------------------------------------------------------------
-- 10. Research Products (At least 5 products as specified)
-- ------------------------------------------------------------
INSERT INTO products 
    (project_id, group_id, name, submitted_by_id, product_type, title, description, file_url, file_name, file_type, file_size, external_link, version, status, submitted_at, created_at, updated_at, active, deleted)
VALUES
    -- 1. Báo cáo tổng kết
    (@face_project_id, @face_group_id, 'Báo cáo tổng kết nhận diện khuôn mặt', @student06_id, 'FINAL_REPORT', 'Báo cáo tổng kết nhận diện khuôn mặt', 
     'Tài liệu hoàn chỉnh tổng kết quá trình xây dựng hệ thống check-in phòng máy.', 
     '/storage/products/bao-cao-tong-ket.pdf', 'bao-cao-tong-ket.pdf', 'application/pdf', 4500000, 
     NULL, 1, 'SUBMITTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    -- 2. Slide thuyết trình
    (@face_project_id, @face_group_id, 'Slide thuyết trình demo', @student06_id, 'SLIDE', 'Slide thuyết trình demo', 
     'Bản slide báo cáo tóm tắt cấu trúc FaceNet, ArcFace và kết quả.', 
     '/storage/products/slide-demo.pptx', 'slide-demo.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 2800000, 
     NULL, 1, 'ACCEPTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    -- 3. Source code demo
    (@face_project_id, @face_group_id, 'Source code demo', @student07_id, 'SOURCE_CODE', 'Source code demo', 
     'Mã nguồn hoàn chỉnh module React và API so khớp vector.', 
     NULL, NULL, NULL, NULL, 
     'https://github.com/lab-portal/face-attendance-demo', 1, 'SUBMITTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    -- 4. Bộ dữ liệu ảnh thử nghiệm
    (@face_project_id, @face_group_id, 'Bộ dữ liệu ảnh thử nghiệm', @student09_id, 'DATASET', 'Bộ dữ liệu ảnh thử nghiệm', 
     'Tập ảnh mẫu khuôn mặt của 15 học viên Lab đã chuẩn hóa.', 
     '/storage/products/face-dataset.zip', 'face-dataset.zip', 'application/zip', 12500000, 
     NULL, 1, 'SUBMITTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    -- 5. Video demo check-in
    (@face_project_id, @face_group_id, 'Video demo check-in', @student07_id, 'DEMO_VIDEO', 'Video demo check-in', 
     'Video quay lại luồng sinh viên đứng trước camera và hệ thống nhận diện check-in tự động.', 
     '/storage/products/demo-checkin.mp4', 'demo-checkin.mp4', 'video/mp4', 35000000, 
     NULL, 1, 'NEEDS_REVISION', NOW(6), NOW(6), NOW(6), TRUE, FALSE);

-- ------------------------------------------------------------
-- 11. Student evaluations (No attendance, pure student-level evaluation score)
-- ------------------------------------------------------------
INSERT INTO evaluations 
    (project_id, group_id, student_id, reviewer_id, contribution_score, task_score, report_score, product_score, attitude_score, score, comments, created_at, updated_at, active, deleted)
VALUES
    -- Sinh viên 07: score = 8.40
    (@face_project_id, @face_group_id, @student07_id, @manager01_id, 8.50, 8.50, 8.00, 8.00, 9.00, 8.40, 
     'Hoàn thành tốt nhiệm vụ, cần cải thiện cách trình bày báo cáo rõ ràng hơn.', 
     NOW(6), NOW(6), TRUE, FALSE),
     
    -- Sinh viên 08: score = 8.00
    (@face_project_id, @face_group_id, @student08_id, @manager01_id, 8.00, 8.00, 8.00, 8.00, 8.00, 8.00, 
     'Tích cực đóng góp ý kiến, hoàn thành tốt bảng so sánh metrics.', 
     NOW(6), NOW(6), TRUE, FALSE),

    -- Sinh viên 09: score = 7.50
    (@face_project_id, @face_group_id, @student09_id, @manager01_id, 7.50, 7.50, 7.50, 7.50, 7.50, 7.50, 
     'Nỗ lực thu thập tập dữ liệu khuôn mặt trong Lab dưới điều kiện sáng yếu.', 
     NOW(6), NOW(6), TRUE, FALSE),

    -- Sinh viên 10: score = 7.00
    (@face_project_id, @face_group_id, @student10_id, @manager01_id, 7.00, 7.00, 7.00, 7.00, 7.00, 7.00, 
     'Cần tích cực và chủ động hơn nữa trong việc phối hợp viết API so khớp.', 
     NOW(6), NOW(6), TRUE, FALSE);

-- ------------------------------------------------------------
-- 12. Research Logs (At least 8 research logs as specified)
-- ------------------------------------------------------------
INSERT INTO research_logs 
    (project_id, group_id, milestone_id, task_id, author_id, author_name, log_type, work_date, duration_minutes, content, result, problem, next_plan, evidence_link, visibility, created_at, updated_at, active, deleted)
VALUES
    -- Log 1 (Manual - student07)
    (@face_project_id, @face_group_id, @ms1_id, @task_facenet_id, @student07_id, 'Sinh viên 07', 'MANUAL', '2026-05-10', 180, 
     'Thử nghiệm FaceNet trên tập ảnh mẫu và ghi nhận các trường hợp nhận diện sai.', 
     'Tìm ra 3 trường hợp bị nhận diện nhầm do góc nghiêng lớn.', 
     'Triplet loss chưa tối ưu cho góc xoay mặt quá 45 độ.', 
     'Thử nghiệm so khớp với ArcFace.', 
     'https://github.com/lab-portal/face-attendance-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),
     
    -- Log 2 (Manual - student08)
    (@face_project_id, @face_group_id, @ms1_id, @task_compare_id, @student08_id, 'Sinh viên 08', 'MANUAL', '2026-05-14', 120, 
     'So sánh kết quả FaceNet và ArcFace trên cùng tập dữ liệu.', 
     'ArcFace đạt độ ổn định cao hơn FaceNet 12% trong điều kiện thiếu sáng.', 
     'Latency suy diễn của ArcFace lớn hơn.', 
     'Nghiên cứu tối ưu hóa thời gian inference.', 
     'https://github.com/lab-portal/face-attendance-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Log 3 (Manual - student09)
    (@face_project_id, @face_group_id, @ms2_id, @task_dataset_id, @student09_id, 'Sinh viên 09', 'MANUAL', '2026-05-20', 150, 
     'Chuẩn bị dữ liệu ảnh, phân loại theo điều kiện ánh sáng.', 
     'Hoàn thành phân nhóm 75 hình ảnh thô thành các thư mục ánh sáng yếu, trung bình và tốt.', 
     'Thiếu thiết bị đo cường độ sáng chuẩn.', 
     'Căn chỉnh mắt tự động bằng OpenCV.', 
     'https://github.com/lab-portal/face-attendance-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Log 4 (Manual - student10)
    (@face_project_id, @face_group_id, @ms2_id, @task_chuanhoa_id, @student10_id, 'Sinh viên 10', 'MANUAL', '2026-05-25', 180, 
     'Xây dựng khung API và triển khai chạy thử nghiệm CorsFilter trên gateway local.', 
     'CorsFilter chạy thành công, không còn lỗi chặn origin.', 
     'Độ phân giải ảnh base64 gửi lên quá lớn gây chậm mạng.', 
     'Cấu hình nén ảnh phía React client.', 
     'https://github.com/lab-portal/face-attendance-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Log 5 (System - student07)
    (@face_project_id, @face_group_id, @ms1_id, @task_facenet_id, @student07_id, 'Sinh viên 07', 'SYSTEM', '2026-05-12', 0, 
     'Hệ thống tự động: Sinh viên 07 đã nộp báo cáo v2 cho nhiệm vụ Tìm hiểu FaceNet.', 
     'Phiên bản báo cáo mới nhất được tải lên thành công.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Log 6 (System - manager01)
    (@face_project_id, @face_group_id, @ms1_id, @task_facenet_id, @manager01_id, 'Manager 01', 'SYSTEM', '2026-05-13', 0, 
     'Hệ thống tự động: Manager 01 đã duyệt báo cáo của Sinh viên 07.', 
     'Trạng thái báo cáo chuyển thành APPROVED.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Log 7 (System - student06)
    (@face_project_id, @face_group_id, @ms1_id, @task_compare_id, @student06_id, 'Sinh viên 06', 'SYSTEM', '2026-05-15', 0, 
     'Hệ thống tự động: Sinh viên 06 đã kiểm tra báo cáo của Sinh viên 08.', 
     'Trạng thái báo cáo chuyển thành LEADER_REVIEWED.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Log 8 (System - student06)
    (@face_project_id, @face_group_id, @ms4_id, @task_ui_id, @student06_id, 'Sinh viên 06', 'SYSTEM', '2026-05-22', 0, 
     'Hệ thống tự động: Trưởng nhóm Sinh viên 06 đã giao nhiệm vụ Tích hợp giao diện check-in cho Sinh viên 07.', 
     'Nhiệm vụ mới được cấu hình thành công trên Kanban board.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE);

-- ------------------------------------------------------------
-- 12a. Seed Minimal Milestones for remaining 4 labs
-- ------------------------------------------------------------
INSERT INTO milestones 
    (project_id, group_id, name, title, description, start_date, end_date, deadline, status, progress_percent,
     created_by, assigned_to_student_id, active, deleted, created_at, updated_at)
VALUES
    -- Robotics Lab
    (@robot_project_id, @robot_group_id, 'Khảo sát thuật toán điều hướng', 'Khảo sát thuật toán điều hướng', 'Tìm hiểu lý thuyết các thuật toán A* và Dijkstra.', '2026-05-15', '2026-05-30', '2026-05-30', 'COMPLETED', 100.00, @manager02_id, @student16_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@robot_project_id, @robot_group_id, 'Thử nghiệm tránh vật cản Lidar', 'Thử nghiệm tránh vật cản Lidar', 'Chạy thử nghiệm điều hướng tránh vật cản trên robot mô hình.', '2026-06-01', '2026-06-15', '2026-06-15', 'IN_PROGRESS', 50.00, @manager02_id, @student17_id, TRUE, FALSE, NOW(6), NOW(6)),

    -- Data Science Lab
    (@ds_project_id, @ds_group_id, 'Thu thập và làm sạch log hệ thống', 'Thu thập và làm sạch log hệ thống', 'Khai thác dữ liệu log máy tính thô và tiền xử lý làm sạch.', '2026-06-01', '2026-06-15', '2026-06-15', 'IN_PROGRESS', 50.00, @manager03_id, @student03_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@ds_project_id, @ds_group_id, 'Xây dựng mô hình phân tích hành vi', 'Xây dựng mô hình phân tích hành vi', 'Sử dụng mô hình phân cụm K-Means phân tích tần suất hành vi sinh viên.', '2026-06-16', '2026-06-30', '2026-06-30', 'NOT_STARTED', 0.00, @manager03_id, @student05_id, TRUE, FALSE, NOW(6), NOW(6)),

    -- Cybersecurity Lab
    (@cyber_project_id, @cyber_group_id, 'Thiết lập môi trường giả lập tấn công', 'Thiết lập môi trường giả lập tấn công', 'Cấu hình Snort IDS và môi trường mạng ảo tấn công thử nghiệm.', '2026-05-01', '2026-05-15', '2026-05-15', 'COMPLETED', 100.00, @manager04_id, @student04_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@cyber_project_id, @cyber_group_id, 'Phát hiện xâm nhập cổng mạng', 'Phát hiện xâm nhập cổng mạng', 'Viết luật Snort phát hiện hành vi port scan trên gateway mạng.', '2026-05-16', '2026-05-30', '2026-05-30', 'IN_PROGRESS', 50.00, @manager04_id, @student06_id, TRUE, FALSE, NOW(6), NOW(6)),

    -- IoT Innovation Lab
    (@iot_project_id, @iot_group_id, 'Thiết kế phần cứng cảm biến', 'Thiết kế phần cứng cảm biến', 'Thiết kế sơ đồ nguyên lý mạch cảm biến ESP32 đọc DHT22.', '2026-05-15', '2026-05-30', '2026-05-30', 'COMPLETED', 100.00, @manager05_id, @student05_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@iot_project_id, @iot_group_id, 'Kết nối MQTT gửi dữ liệu lên Broker', 'Kết nối MQTT gửi dữ liệu lên Broker', 'Lập trình ESP32 kết nối Wifi và gửi dữ liệu cảm biến qua MQTT.', '2026-06-01', '2026-06-15', '2026-06-15', 'IN_PROGRESS', 30.00, @manager05_id, @student07_id, TRUE, FALSE, NOW(6), NOW(6));

-- Retrieve Milestone IDs for other labs
SET @robot_ms1_id := (SELECT id FROM milestones WHERE project_id = @robot_project_id AND name = 'Khảo sát thuật toán điều hướng' LIMIT 1);
SET @robot_ms2_id := (SELECT id FROM milestones WHERE project_id = @robot_project_id AND name = 'Thử nghiệm tránh vật cản Lidar' LIMIT 1);

SET @ds_ms1_id := (SELECT id FROM milestones WHERE project_id = @ds_project_id AND name = 'Thu thập và làm sạch log hệ thống' LIMIT 1);
SET @ds_ms2_id := (SELECT id FROM milestones WHERE project_id = @ds_project_id AND name = 'Xây dựng mô hình phân tích hành vi' LIMIT 1);

SET @cyber_ms1_id := (SELECT id FROM milestones WHERE project_id = @cyber_project_id AND name = 'Thiết lập môi trường giả lập tấn công' LIMIT 1);
SET @cyber_ms2_id := (SELECT id FROM milestones WHERE project_id = @cyber_project_id AND name = 'Phát hiện xâm nhập cổng mạng' LIMIT 1);

SET @iot_ms1_id := (SELECT id FROM milestones WHERE project_id = @iot_project_id AND name = 'Thiết kế phần cứng cảm biến' LIMIT 1);
SET @iot_ms2_id := (SELECT id FROM milestones WHERE project_id = @iot_project_id AND name = 'Kết nối MQTT gửi dữ liệu lên Broker' LIMIT 1);

-- ------------------------------------------------------------
-- 12b. Seed Minimal Tasks for remaining 4 labs (3 tasks per lab)
-- ------------------------------------------------------------
INSERT INTO tasks 
    (milestone_id, assignee_id, title, description, deadline, status, progress_percent, active, deleted, created_at, updated_at)
VALUES
    -- Robotics Lab Tasks
    (@robot_ms1_id, @student16_id, 'Tìm hiểu thuật toán A*', 'Nghiên cứu cấu trúc và nguyên lý thuật toán A* trên đồ thị.', '2026-05-25', 'DONE', 100, TRUE, FALSE, NOW(6), NOW(6)),
    (@robot_ms2_id, @student17_id, 'Thu thập dữ liệu cảm biến Lidar', 'Kết nối cảm biến Lidar và đo đạc khoảng cách vật cản.', '2026-06-10', 'DOING', 50, TRUE, FALSE, NOW(6), NOW(6)),
    (@robot_ms2_id, @student18_id, 'Lập trình thuật toán tránh vật cản', 'Viết hàm điều khiển hướng robot dựa trên khoảng cách Lidar.', '2026-06-14', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6)),

    -- Data Science Lab Tasks
    (@ds_ms1_id, @student03_id, 'Trích xuất log sử dụng máy tính', 'Viết script trích xuất lịch sử log sử dụng máy tính phòng lab.', '2026-06-08', 'DONE', 100, TRUE, FALSE, NOW(6), NOW(6)),
    (@ds_ms1_id, @student04_id, 'Làm sạch dữ liệu thô', 'Lọc bỏ dữ liệu null, chuẩn hóa các trường thông tin đăng nhập.', '2026-06-12', 'DOING', 40, TRUE, FALSE, NOW(6), NOW(6)),
    (@ds_ms2_id, @student05_id, 'Huấn luyện mô hình phân cụm K-Means', 'Xây dựng mô hình phân cụm tìm ra nhóm người dùng tích cực.', '2026-06-25', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6)),

    -- Cybersecurity Lab Tasks
    (@cyber_ms1_id, @student04_id, 'Cấu hình Snort IDS trên Gateway', 'Cài đặt và cấu hình Snort IDS lắng nghe trên cổng mạng gateway.', '2026-05-10', 'DONE', 100, TRUE, FALSE, NOW(6), NOW(6)),
    (@cyber_ms1_id, @student05_id, 'Giả lập tấn công Port Scan', 'Sử dụng Nmap để quét cổng và tạo lưu lượng traffic tấn công.', '2026-05-14', 'DOING', 60, TRUE, FALSE, NOW(6), NOW(6)),
    (@cyber_ms2_id, @student06_id, 'Viết luật phát hiện Port Scan', 'Định nghĩa rule Snort cảnh báo khi có nhiều kết nối TCP SYN trong 1s.', '2026-05-25', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6)),

    -- IoT Innovation Lab Tasks
    (@iot_ms1_id, @student05_id, 'Sơ đồ nguyên lý ESP32 và DHT22', 'Vẽ sơ đồ nguyên lý kết nối vi điều khiển ESP32 và cảm biến DHT22.', '2026-05-20', 'DONE', 100, TRUE, FALSE, NOW(6), NOW(6)),
    (@iot_ms1_id, @student06_id, 'Lập trình đọc dữ liệu cảm biến', 'Viết code đọc nhiệt độ và độ ẩm từ DHT22 gửi qua cổng Serial.', '2026-05-28', 'DOING', 50, TRUE, FALSE, NOW(6), NOW(6)),
    (@iot_ms2_id, @student07_id, 'Cấu hình MQTT Broker và Publish', 'Lập trình gửi dữ liệu nhiệt độ độ ẩm lên MQTT broker công cộng.', '2026-06-10', 'TODO', 0, TRUE, FALSE, NOW(6), NOW(6));

-- Retrieve Task IDs for other labs
SET @robot_task1_id := (SELECT id FROM tasks WHERE milestone_id = @robot_ms1_id AND title = 'Tìm hiểu thuật toán A*' LIMIT 1);
SET @robot_task2_id := (SELECT id FROM tasks WHERE milestone_id = @robot_ms2_id AND title = 'Thu thập dữ liệu cảm biến Lidar' LIMIT 1);
SET @robot_task3_id := (SELECT id FROM tasks WHERE milestone_id = @robot_ms2_id AND title = 'Lập trình thuật toán tránh vật cản' LIMIT 1);

SET @ds_task1_id := (SELECT id FROM tasks WHERE milestone_id = @ds_ms1_id AND title = 'Trích xuất log sử dụng máy tính' LIMIT 1);
SET @ds_task2_id := (SELECT id FROM tasks WHERE milestone_id = @ds_ms1_id AND title = 'Làm sạch dữ liệu thô' LIMIT 1);
SET @ds_task3_id := (SELECT id FROM tasks WHERE milestone_id = @ds_ms2_id AND title = 'Huấn luyện mô hình phân cụm K-Means' LIMIT 1);

SET @cyber_task1_id := (SELECT id FROM tasks WHERE milestone_id = @cyber_ms1_id AND title = 'Cấu hình Snort IDS trên Gateway' LIMIT 1);
SET @cyber_task2_id := (SELECT id FROM tasks WHERE milestone_id = @cyber_ms1_id AND title = 'Giả lập tấn công Port Scan' LIMIT 1);
SET @cyber_task3_id := (SELECT id FROM tasks WHERE milestone_id = @cyber_ms2_id AND title = 'Viết luật phát hiện Port Scan' LIMIT 1);

SET @iot_task1_id := (SELECT id FROM tasks WHERE milestone_id = @iot_ms1_id AND title = 'Sơ đồ nguyên lý ESP32 và DHT22' LIMIT 1);
SET @iot_task2_id := (SELECT id FROM tasks WHERE milestone_id = @iot_ms1_id AND title = 'Lập trình đọc dữ liệu cảm biến' LIMIT 1);
SET @iot_task3_id := (SELECT id FROM tasks WHERE milestone_id = @iot_ms2_id AND title = 'Cấu hình MQTT Broker và Publish' LIMIT 1);

-- ------------------------------------------------------------
-- 12c. Seed Minimal Reports for remaining 4 labs (3 reports per lab)
-- ------------------------------------------------------------
INSERT INTO reports 
    (project_id, group_id, milestone_id, task_id, submitted_by_id, version, title, content_done, result, difficulty, next_plan, self_assessment, file_url, file_name, file_type, file_size, evidence_link, status, submission_scope, active, deleted, created_at, updated_at)
VALUES
    -- Robotics Lab Reports
    (@robot_project_id, @robot_group_id, @robot_ms1_id, @robot_task1_id, @student16_id, 1, 'Báo cáo khảo sát thuật toán A* v1', 
     'Đã hoàn thành tìm hiểu lý thuyết thuật toán A* và Dijkstra.', 'Có tài liệu so sánh chi tiết ưu nhược điểm của 2 thuật toán.', 
     'Không gặp khó khăn lớn.', 'Tiến hành cài đặt thuật toán trên mô phỏng.', 'Hoàn thành tốt nhiệm vụ.', 
     '/storage/reports/robot-task1/v1.pdf', 'khao-sat-thuat-toan-a.pdf', 'application/pdf', 900000, 
     'https://github.com/lab-portal/robotics-demo', 'APPROVED', CONCAT('M:', @robot_ms1_id, ':T:', @robot_task1_id, ':U:', @student16_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@robot_project_id, @robot_group_id, @robot_ms2_id, @robot_task2_id, @student17_id, 1, 'Báo cáo cảm biến Lidar v1', 
     'Đã kết nối thành công Lidar và vẽ sơ đồ khoảng cách xung quanh.', 'Thu thập được tập dữ liệu quét khoảng cách góc 360 độ.', 
     'Cảm biến Lidar thỉnh thoảng bị nhiễu do ánh sáng ngoài trời.', 'Thiết kế bộ lọc trung bình để giảm nhiễu.', 'Hoàn thành khoảng 80% tiến độ.', 
     '/storage/reports/robot-task2/v1.pdf', 'bao-cao-lidar-v1.pdf', 'application/pdf', 1200000, 
     'https://github.com/lab-portal/robotics-demo', 'SUBMITTED', CONCAT('M:', @robot_ms2_id, ':T:', @robot_task2_id, ':U:', @student17_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@robot_project_id, @robot_group_id, @robot_ms1_id, @robot_task1_id, @student01_id, 1, 'Báo cáo thiết kế khung gầm robot v1', 
     'Thiết kế và in 3D bộ khung gầm robot dạng 2 bánh chủ động.', 'Hoàn thành in 3D và lắp ráp phần cơ khí cơ bản.', 
     'Sai số in 3D khiến các khớp nối hơi chật.', 'Mài giũa lại các chi tiết nhựa và lắp ráp động cơ.', 'Đạt 90% yêu cầu.', 
     '/storage/reports/robot-leader/v1.pdf', 'thiet-ke-khung-gam.pdf', 'application/pdf', 1800000, 
     'https://github.com/lab-portal/robotics-demo', 'APPROVED', CONCAT('M:', @robot_ms1_id, ':T:', @robot_task1_id, ':U:', @student01_id), TRUE, FALSE, NOW(6), NOW(6)),

    -- Data Science Lab Reports
    (@ds_project_id, @ds_group_id, @ds_ms1_id, @ds_task1_id, @student03_id, 1, 'Báo cáo trích xuất log v1', 
     'Viết script Python trích xuất log đăng nhập trên hệ điều hành.', 'Tập dữ liệu log gồm 10,000 dòng ghi nhận chi tiết thời gian đăng nhập.', 
     'Định dạng log giữa các máy tính phòng Lab chưa đồng nhất.', 'Xây dựng parser để chuẩn hóa log.', 'Hoàn thành tốt nhiệm vụ.', 
     '/storage/reports/ds-task1/v1.pdf', 'trich-xuat-log.pdf', 'application/pdf', 950000, 
     'https://github.com/lab-portal/ds-demo', 'APPROVED', CONCAT('M:', @ds_ms1_id, ':T:', @ds_task1_id, ':U:', @student03_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@ds_project_id, @ds_group_id, @ds_ms1_id, @ds_task2_id, @student04_id, 1, 'Báo cáo tiền xử lý log v1', 
     'Đã hoàn thành lọc bỏ log rác và điền các trường bị thiếu.', 'Dữ liệu log sau làm sạch sẵn sàng để đưa vào mô hình.', 
     'Số lượng log null chiếm tỷ lệ khá cao (20%).', 'Sử dụng phương pháp nội suy để điền dữ liệu thiếu.', 'Hoàn thành 85% tiến độ.', 
     '/storage/reports/ds-task2/v1.pdf', 'lam-sach-log.pdf', 'application/pdf', 1050000, 
     'https://github.com/lab-portal/ds-demo', 'SUBMITTED', CONCAT('M:', @ds_ms1_id, ':T:', @ds_task2_id, ':U:', @student04_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@ds_project_id, @ds_group_id, @ds_ms2_id, @ds_task3_id, @student05_id, 1, 'Báo cáo khảo sát phương pháp phân cụm v1', 
     'Khảo sát mô hình phân cụm K-Means và phân tích phân phối ElBow.', 'Tài liệu lý thuyết và code thử nghiệm phân cụm trên Google Colab.', 
     'Chưa tìm được số cụm tối ưu K.', 'Thử nghiệm phương pháp Silhouette để tìm K tối ưu.', 'Cần chỉnh sửa lại.', 
     '/storage/reports/ds-task3/v1.pdf', 'phap-phan-cum.pdf', 'application/pdf', 1100000, 
     'https://github.com/lab-portal/ds-demo', 'NEEDS_REVISION', CONCAT('M:', @ds_ms2_id, ':T:', @ds_task3_id, ':U:', @student05_id), TRUE, FALSE, NOW(6), NOW(6)),

    -- Cybersecurity Lab Reports
    (@cyber_project_id, @cyber_group_id, @cyber_ms1_id, @cyber_task1_id, @student04_id, 1, 'Báo cáo cấu hình Snort IDS v1', 
     'Đã cài đặt Snort và thiết lập card mạng promiscuous mode.', 'Snort IDS hoạt động ổn định và bắt được gói tin ICMP.', 
     'Card mạng không nhận promiscuous mode trên môi trường ảo hóa VirtualBox.', 'Cấu hình lại driver mạng VirtualBox.', 'Hoàn thành 100% nhiệm vụ.', 
     '/storage/reports/cyber-task1/v1.pdf', 'cauhinh-snort.pdf', 'application/pdf', 1300000, 
     'https://github.com/lab-portal/cyber-demo', 'APPROVED', CONCAT('M:', @cyber_ms1_id, ':T:', @cyber_task1_id, ':U:', @student04_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@cyber_project_id, @cyber_group_id, @cyber_ms1_id, @cyber_task2_id, @student05_id, 1, 'Báo cáo kịch bản tấn công scan v1', 
     'Xây dựng các kịch bản quét cổng TCP Connect và TCP SYN.', 'Ghi nhận dấu hiệu các gói tin quét cổng từ WireShark.', 
     'Tốc độ quét cổng nhanh gây tràn bộ đệm ghi log.', 'Cấu hình giới hạn rate-limit trên card mạng.', 'Đạt 85% tiến độ.', 
     '/storage/reports/cyber-task2/v1.pdf', 'kichban-portscan.pdf', 'application/pdf', 1400000, 
     'https://github.com/lab-portal/cyber-demo', 'SUBMITTED', CONCAT('M:', @cyber_ms1_id, ':T:', @cyber_task2_id, ':U:', @student05_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@cyber_project_id, @cyber_group_id, @cyber_ms2_id, @cyber_task3_id, @student06_id, 1, 'Báo cáo thiết kế luật Snort v1', 
     'Viết các rule Snort cơ bản để phát hiện IP quét cổng liên tục.', 'Tệp cấu hình rule Snort chạy thử nghiệm nội bộ.', 
     'Rule Snort thỉnh thoảng cảnh báo nhầm đối với request hợp lệ từ trình duyệt.', 'Tối ưu lại ngưỡng kết nối cảnh báo.', 'Cần cập nhật luật.', 
     '/storage/reports/cyber-task3/v1.pdf', 'luat-snort-scan.pdf', 'application/pdf', 1250000, 
     'https://github.com/lab-portal/cyber-demo', 'NEEDS_REVISION', CONCAT('M:', @cyber_ms2_id, ':T:', @cyber_task3_id, ':U:', @student06_id), TRUE, FALSE, NOW(6), NOW(6)),

    -- IoT Innovation Lab Reports
    (@iot_project_id, @iot_group_id, @iot_ms1_id, @iot_task1_id, @student05_id, 1, 'Báo cáo thiết kế mạch cảm biến v1', 
     'Vẽ sơ đồ nguyên lý kết nối chân ESP32 với cảm biến nhiệt độ DHT22.', 'Hoàn thành file sơ đồ nguyên lý SCH và Layout PCB.', 
     'Linh kiện DHT22 chưa có sẵn thư viện chân Altium.', 'Tự vẽ thư viện chân DHT22 từ datasheet.', 'Hoàn thành xuất sắc mạch in.', 
     '/storage/reports/iot-task1/v1.pdf', 'mach-in-sensor.pdf', 'application/pdf', 1500000, 
     'https://github.com/lab-portal/iot-demo', 'APPROVED', CONCAT('M:', @iot_ms1_id, ':T:', @iot_task1_id, ':U:', @student05_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@iot_project_id, @iot_group_id, @iot_ms1_id, @iot_task2_id, @student06_id, 1, 'Báo cáo đọc dữ liệu DHT22 v1', 
     'Lập trình ESP32 sử dụng thư viện DHT để lấy nhiệt độ độ ẩm mỗi 2 giây.', 'Đọc và in dữ liệu ra màn hình terminal Serial Monitor.', 
     'Cảm biến DHT22 giá rẻ thỉnh thoảng trả về giá trị NaN.', 'Thêm cơ chế kiểm tra giá trị hợp lệ trước khi xử lý.', 'Đạt 90% khối lượng.', 
     '/storage/reports/iot-task2/v1.pdf', 'doc-dht22-esp32.pdf', 'application/pdf', 1100000, 
     'https://github.com/lab-portal/iot-demo', 'SUBMITTED', CONCAT('M:', @iot_ms1_id, ':T:', @iot_task2_id, ':U:', @student06_id), TRUE, FALSE, NOW(6), NOW(6)),

    (@iot_project_id, @iot_group_id, @iot_ms2_id, @iot_task3_id, @student07_id, 1, 'Báo cáo kết nối MQTT v1', 
     'Tìm hiểu cấu trúc bản tin MQTT gửi dữ liệu lên Broker.', 'Viết mã nguồn kết nối Wifi và cấu hình PubSubClient.', 
     'Wifi phòng thí nghiệm có bảo mật Enterprise không cho ESP32 kết nối trực tiếp.', 'Phát Wifi hotspot từ điện thoại để test.', 'Cần nghiên cứu thêm kết nối Enterprise.', 
     '/storage/reports/iot-task3/v1.pdf', 'mqtt-connect-v1.pdf', 'application/pdf', 980000, 
     'https://github.com/lab-portal/iot-demo', 'NEEDS_REVISION', CONCAT('M:', @iot_ms2_id, ':T:', @iot_task3_id, ':U:', @student07_id), TRUE, FALSE, NOW(6), NOW(6));

-- ------------------------------------------------------------
-- 12d. Seed Minimal Products for remaining 4 labs (2 products per lab)
-- ------------------------------------------------------------
INSERT INTO products 
    (project_id, group_id, name, submitted_by_id, product_type, title, description, file_url, file_name, file_type, file_size, external_link, version, status, submitted_at, created_at, updated_at, active, deleted)
VALUES
    -- Robotics Lab Products
    (@robot_project_id, @robot_group_id, 'Tài liệu thiết kế thuật toán điều hướng', @student01_id, 'SLIDE', 'Tài liệu thiết kế thuật toán điều hướng', 
     'Slide thuyết trình mô tả thuật toán đường đi ngắn nhất A* tích hợp robot tự hành.', 
     '/storage/products/robot-slide.pptx', 'robot-slide.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 1500000, 
     NULL, 1, 'ACCEPTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    (@robot_project_id, @robot_group_id, 'Mã nguồn điều khiển động cơ', @student16_id, 'SOURCE_CODE', 'Mã nguồn điều khiển động cơ', 
     'Mã nguồn C++ điều khiển driver động cơ servo qua board STM32.', 
     NULL, NULL, NULL, NULL, 
     'https://github.com/lab-portal/robotics-demo', 1, 'SUBMITTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    -- Data Science Lab Products
    (@ds_project_id, @ds_group_id, 'Slide phân tích hành vi người dùng', @student02_id, 'SLIDE', 'Slide phân tích hành vi người dùng', 
     'Slide trình bày kết quả phân cụm hành vi đăng nhập phòng Lab.', 
     '/storage/products/ds-slide.pptx', 'ds-slide.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 1600000, 
     NULL, 1, 'ACCEPTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    (@ds_project_id, @ds_group_id, 'Bộ dữ liệu log làm sạch', @student03_id, 'DATASET', 'Bộ dữ liệu log làm sạch', 
     'Tập log hoạt động máy tính phòng máy dạng CSV đã được làm sạch và chuẩn hóa.', 
     '/storage/products/ds-clean-log.zip', 'ds-clean-log.zip', 'application/zip', 5400000, 
     NULL, 1, 'SUBMITTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    -- Cybersecurity Lab Products
    (@cyber_project_id, @cyber_group_id, 'Slide kiến trúc Snort IDS', @student03_id, 'SLIDE', 'Slide kiến trúc Snort IDS', 
     'Slide giới thiệu cơ chế phát hiện và cảnh báo của Snort IDS trên Gateway.', 
     '/storage/products/cyber-slide.pptx', 'cyber-slide.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 1700000, 
     NULL, 1, 'ACCEPTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    (@cyber_project_id, @cyber_group_id, 'Tệp cấu hình rule Snort', @student04_id, 'SOURCE_CODE', 'Tệp cấu hình rule Snort', 
     'Tệp tin cấu hình Snort rules phục vụ phát hiện tấn công DDoS và scan port.', 
     NULL, NULL, NULL, NULL, 
     'https://github.com/lab-portal/cyber-demo', 1, 'SUBMITTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    -- IoT Innovation Lab Products
    (@iot_project_id, @iot_group_id, 'Bản vẽ PCB mạch cảm biến', @student04_id, 'SLIDE', 'Bản vẽ PCB mạch cảm biến', 
     'Tài liệu thiết kế PCB Altium của mạch tích hợp vi điều khiển và cảm biến dht22.', 
     '/storage/products/iot-pcb.pdf', 'iot-pcb.pdf', 'application/pdf', 2200000, 
     NULL, 1, 'ACCEPTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE),

    (@iot_project_id, @iot_group_id, 'Mã nguồn ESP32 đọc cảm biến', @student05_id, 'SOURCE_CODE', 'Mã nguồn ESP32 đọc cảm biến', 
     'Mã nguồn Arduino đọc cảm biến DHT22 và publish dữ liệu qua MQTT broker.', 
     NULL, NULL, NULL, NULL, 
     'https://github.com/lab-portal/iot-demo', 1, 'SUBMITTED', NOW(6), NOW(6), NOW(6), TRUE, FALSE);

-- ------------------------------------------------------------
-- 12e. Seed Minimal Evaluations for remaining 4 labs (2 evaluations per lab)
-- ------------------------------------------------------------
INSERT INTO evaluations 
    (project_id, group_id, student_id, reviewer_id, contribution_score, task_score, report_score, product_score, attitude_score, score, comments, created_at, updated_at, active, deleted)
VALUES
    -- Robotics Lab Evaluations
    (@robot_project_id, @robot_group_id, @student16_id, @manager02_id, 8.00, 8.00, 8.00, 8.00, 8.00, 8.00, 'Tìm hiểu thuật toán A* rất nhanh và áp dụng tốt.', NOW(6), NOW(6), TRUE, FALSE),
    (@robot_project_id, @robot_group_id, @student17_id, @manager02_id, 7.50, 7.50, 7.50, 7.50, 7.50, 7.50, 'Hoàn thành kết nối Lidar, cần cải thiện tốc độ code.', NOW(6), NOW(6), TRUE, FALSE),

    -- Data Science Lab Evaluations
    (@ds_project_id, @ds_group_id, @student03_id, @manager03_id, 8.20, 8.20, 8.20, 8.20, 8.20, 8.20, 'Trích xuất dữ liệu log sạch sẽ, đúng hạn.', NOW(6), NOW(6), TRUE, FALSE),
    (@ds_project_id, @ds_group_id, @student04_id, @manager03_id, 7.80, 7.80, 7.80, 7.80, 7.80, 7.80, 'Có cố gắng làm sạch dữ liệu, cần chủ động hơn.', NOW(6), NOW(6), TRUE, FALSE),

    -- Cybersecurity Lab Evaluations
    (@cyber_project_id, @cyber_group_id, @student04_id, @manager04_id, 8.50, 8.50, 8.50, 8.50, 8.50, 8.50, 'Cấu hình Snort IDS rất tốt, đúng thiết kế.', NOW(6), NOW(6), TRUE, FALSE),
    (@cyber_project_id, @cyber_group_id, @student05_id, @manager04_id, 7.90, 7.90, 7.90, 7.90, 7.90, 7.90, 'Giả lập các hình thức port scan đầy đủ.', NOW(6), NOW(6), TRUE, FALSE),

    -- IoT Innovation Lab Evaluations
    (@iot_project_id, @iot_group_id, @student05_id, @manager05_id, 8.00, 8.00, 8.00, 8.00, 8.00, 8.00, 'Thiết kế mạch in PCB đẹp, không lỗi chân linh kiện.', NOW(6), NOW(6), TRUE, FALSE),
    (@iot_project_id, @iot_group_id, @student06_id, @manager05_id, 7.70, 7.70, 7.70, 7.70, 7.70, 7.70, 'Đọc thành công dữ liệu cảm biến DHT22.', NOW(6), NOW(6), TRUE, FALSE);

-- ------------------------------------------------------------
-- 12f. Seed Minimal Research Logs for remaining 4 labs (3 logs per lab)
-- ------------------------------------------------------------
INSERT INTO research_logs 
    (project_id, group_id, milestone_id, task_id, author_id, author_name, log_type, work_date, duration_minutes, content, result, problem, next_plan, evidence_link, visibility, created_at, updated_at, active, deleted)
VALUES
    -- Robotics Lab Research Logs
    (@robot_project_id, @robot_group_id, @robot_ms1_id, @robot_task1_id, @student16_id, 'Sinh viên 16', 'MANUAL', '2026-05-20', 120, 
     'Thử nghiệm thuật toán A* trên mô phỏng đồ thị 2D.', 'Tìm ra đường đi tránh được chướng ngại vật tĩnh.', 
     'Chưa test với chướng ngại vật động.', 'Nghiên cứu thêm thuật toán D* Lite.', 
     'https://github.com/lab-portal/robotics-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),
     
    (@robot_project_id, @robot_group_id, @robot_ms2_id, @robot_task2_id, @student17_id, 'Sinh viên 17', 'MANUAL', '2026-06-08', 180, 
     'Kết nối cảm biến Lidar với board STM32 qua chuẩn giao tiếp UART.', 'Nhận được bản tin khoảng cách 360 độ từ Lidar.', 
     'Tốc độ nhận bản tin UART thỉnh thoảng bị drop byte.', 'Cấu hình DMA để tăng tốc độ nhận UART.', 
     'https://github.com/lab-portal/robotics-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    (@robot_project_id, @robot_group_id, @robot_ms1_id, @robot_task1_id, @student01_id, 'Sinh viên 01', 'SYSTEM', '2026-05-22', 0, 
     'Hệ thống tự động: Sinh viên 16 đã nộp báo cáo v1 cho nhiệm vụ Tìm hiểu thuật toán A*.', 
     'Phiên bản báo cáo mới nhất được tải lên thành công.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Data Science Lab Research Logs
    (@ds_project_id, @ds_group_id, @ds_ms1_id, @ds_task1_id, @student03_id, 'Sinh viên 03', 'MANUAL', '2026-06-05', 150, 
     'Viết script SQL để kết xuất log truy cập phòng lab từ CSDL chính.', 'Xuất ra file CSV thô chứa 10,000 bản ghi log.', 
     'Có nhiều dòng log bị trùng lặp do lỗi ghi log trùng lặp.', 'Viết câu lệnh SELECT DISTINCT để loại bỏ trùng.', 
     'https://github.com/lab-portal/ds-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),
     
    (@ds_project_id, @ds_group_id, @ds_ms1_id, @ds_task2_id, @student04_id, 'Sinh viên 04', 'MANUAL', '2026-06-10', 120, 
     'Viết script Python Pandas để lọc các dòng log null.', 'Loại bỏ thành công 2,000 dòng log lỗi.', 
     'Mất nhiều thời gian xử lý các trường dữ liệu định dạng ngày tháng.', 'Sử dụng hàm pd.to_datetime để chuẩn hóa.', 
     'https://github.com/lab-portal/ds-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    (@ds_project_id, @ds_group_id, @ds_ms1_id, @ds_task1_id, @student02_id, 'Sinh viên 02', 'SYSTEM', '2026-06-06', 0, 
     'Hệ thống tự động: Sinh viên 03 đã nộp báo cáo v1 cho nhiệm vụ Trích xuất log sử dụng máy tính.', 
     'Phiên bản báo cáo mới nhất được tải lên thành công.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- Cybersecurity Lab Research Logs
    (@cyber_project_id, @cyber_group_id, @cyber_ms1_id, @cyber_task1_id, @student04_id, 'Sinh viên 04', 'MANUAL', '2026-05-08', 240, 
     'Cài đặt Snort IDS trên máy ảo Ubuntu Server làm Gateway.', 'IDS khởi chạy thành công và nhận diện card mạng.', 
     'Gặp lỗi compile mã nguồn Snort từ source code.', 'Chuyển sang cài đặt qua gói apt pre-built.', 
     'https://github.com/lab-portal/cyber-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),
     
    (@cyber_project_id, @cyber_group_id, @cyber_ms1_id, @cyber_task2_id, @student05_id, 'Sinh viên 05', 'MANUAL', '2026-05-12', 150, 
     'Thực hiện quét cổng bằng Nmap từ máy Kali Linux sang máy Gateway.', 'Các gói tin SYN scan bị IDS ghi nhận thành công.', 
     'IDS chưa sinh ra log cảnh báo Alert.', 'Kiểm tra lại cấu hình file snort.conf.', 
     'https://github.com/lab-portal/cyber-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    (@cyber_project_id, @cyber_group_id, @cyber_ms1_id, @cyber_task1_id, @student03_id, 'Sinh viên 03', 'SYSTEM', '2026-05-09', 0, 
     'Hệ thống tự động: Sinh viên 04 đã nộp báo cáo v1 cho nhiệm vụ Cấu hình Snort IDS trên Gateway.', 
     'Phiên bản báo cáo mới nhất được tải lên thành công.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    -- IoT Innovation Lab Research Logs
    (@iot_project_id, @iot_group_id, @iot_ms1_id, @iot_task1_id, @student05_id, 'Sinh viên 05', 'MANUAL', '2026-05-18', 180, 
     'Thiết kế sơ đồ nguyên lý mạch ESP32 DHT22 trên Altium.', 'Lựa chọn và bố trí các chân cắm cho cảm biến và nguồn ổn áp 3.3V.', 
     'Thiếu linh kiện ổn áp LM1117 trong thư viện mặc định.', 'Tải thư viện bổ sung từ trang Component Search Engine.', 
     'https://github.com/lab-portal/iot-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),
     
    (@iot_project_id, @iot_group_id, @iot_ms1_id, @iot_task2_id, @student06_id, 'Sinh viên 06', 'MANUAL', '2026-05-25', 120, 
     'Lập trình ESP32 đọc dữ liệu nhiệt độ và độ ẩm qua thư viện DHT.h.', 'Giá trị nhiệt độ độ ẩm xuất ra Serial Monitor chính xác.', 
     'DHT22 thỉnh thoảng phản hồi chậm gây treo chip.', 'Thêm hàm non-blocking millis() thay thế delay().', 
     'https://github.com/lab-portal/iot-demo', 'GROUP', NOW(6), NOW(6), TRUE, FALSE),

    (@iot_project_id, @iot_group_id, @iot_ms1_id, @iot_task1_id, @student04_id, 'Sinh viên 04', 'SYSTEM', '2026-05-19', 0, 
     'Hệ thống tự động: Sinh viên 05 đã nộp báo cáo v1 cho nhiệm vụ Sơ đồ nguyên lý ESP32 và DHT22.', 
     'Phiên bản báo cáo mới nhất được tải lên thành công.', 
     NULL, NULL, NULL, 'GROUP', NOW(6), NOW(6), TRUE, FALSE);

-- ------------------------------------------------------------
-- 13. Bookings Added for NCKH testing
-- ------------------------------------------------------------
INSERT INTO bookings
    (user_id, lab_id, slot_id, start_time, end_time, status, purpose, participants_count, active, deleted, created_at, updated_at)
SELECT u.id, @ai_lab_id, s.id, s.start_time, s.end_time, 'CHECKED_IN', '[D35] Chụp ảnh dữ liệu khuôn mặt và kiểm thử camera', 1, TRUE, FALSE, NOW(6), NOW(6)
FROM users u
JOIN time_slots s ON s.lab_id = @ai_lab_id AND s.start_time = TIMESTAMP('2026-06-01 08:00:00') AND s.deleted = FALSE
WHERE u.username = 'student06'
  AND NOT EXISTS (SELECT 1 FROM bookings WHERE user_id = u.id AND slot_id = s.id AND deleted = FALSE);

COMMIT;

-- Final Verification count query
SELECT 'D35 projects' AS item, COUNT(*) AS total FROM projects WHERE code LIKE 'D35-%' AND deleted = FALSE
UNION ALL SELECT 'D35 groups', COUNT(*) FROM research_groups WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id) AND deleted = FALSE
UNION ALL SELECT 'D35 milestones', COUNT(*) FROM milestones WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id) AND deleted = FALSE
UNION ALL SELECT 'D35 tasks', COUNT(*) FROM tasks WHERE milestone_id IN (SELECT id FROM milestones WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id)) AND deleted = FALSE
UNION ALL SELECT 'D35 reports', COUNT(*) FROM reports WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id) AND deleted = FALSE
UNION ALL SELECT 'D35 comments', COUNT(*) FROM comments WHERE report_id IN (SELECT id FROM reports WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id)) AND deleted = FALSE
UNION ALL SELECT 'D35 products', COUNT(*) FROM products WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id) AND deleted = FALSE
UNION ALL SELECT 'D35 evaluations', COUNT(*) FROM evaluations WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id) AND deleted = FALSE
UNION ALL SELECT 'D35 research_logs', COUNT(*) FROM research_logs WHERE project_id IN (@face_project_id, @robot_project_id, @ds_project_id, @cyber_project_id, @iot_project_id) AND deleted = FALSE;
