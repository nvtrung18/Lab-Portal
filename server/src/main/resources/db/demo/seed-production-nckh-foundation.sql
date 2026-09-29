-- Manual, opt-in NCKH fixture for a migrated production MySQL 8 database.
-- Run only after production Flyway V72 succeeds and after a verified backup.
-- This file is not a Flyway migration and is not loaded at application startup.
-- All accounts and research plans are synthetic. Do not treat them as real people
-- or completed research. The production import script combines this foundation
-- with the D35 and thesis research-plan fixtures in one transaction.
--
-- The operator must set @demo_manager_hash and @demo_student_hash to two fresh,
-- different BCrypt hashes in THIS MySQL session before sourcing this file.
-- No password or reusable hash is stored here. Use mysql batch mode without
-- --force: any failed guard must stop the client and roll back the transaction.
-- The second, research-plan fixture adds group members and TODO work later.

SET NAMES utf8mb4;

-- A CHECK violation is intentional when any precondition or postcondition fails.
-- Temporary-table DDL does not change the durable application schema.
CREATE TEMPORARY TABLE nckh_prod_foundation_guard (
    ok TINYINT NOT NULL CHECK (ok = 1)
);

-- Refuse a partial rerun, an identity collision, missing permissions, missing
-- credential inputs, or an installation that has not removed the old V25 demo.
INSERT INTO nckh_prod_foundation_guard (ok)
SELECT CASE WHEN
    (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '72' AND success = 1) = 1
    AND (SELECT COUNT(*) FROM roles WHERE name IN ('LAB_MANAGER', 'STUDENT')
         AND active = TRUE AND deleted = FALSE) = 2
    AND @demo_manager_hash IS NOT NULL
    AND @demo_student_hash IS NOT NULL
    AND CHAR_LENGTH(@demo_manager_hash) = 60
    AND CHAR_LENGTH(@demo_student_hash) = 60
    AND LEFT(@demo_manager_hash, 4) IN ('$2a$', '$2b$', '$2y$')
    AND LEFT(@demo_student_hash, 4) IN ('$2a$', '$2b$', '$2y$')
    AND @demo_manager_hash <> @demo_student_hash
    AND (SELECT COUNT(*) FROM users
         WHERE username REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20))$'
            OR email REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20))@example[.]invalid$') = 0
    AND (SELECT COUNT(*) FROM laboratories WHERE lab_name IN
         ('AI Research Lab', 'Robotics Lab', 'Data Science Lab',
          'Cybersecurity Lab', 'IoT Innovation Lab')) = 0
    AND (SELECT COUNT(*) FROM projects WHERE code IN
         ('D35-AI-FACE', 'D35-ROBOT', 'D35-DS-ANALYTICS',
          'D35-CYBER-IDS', 'D35-IoT-ENV')) = 0
    THEN 1 ELSE 0 END;

START TRANSACTION;

-- V25/D35 account identities, with credentials supplied privately at import.
INSERT INTO users
    (email, username, password, full_name, phone, status, active, deleted, created_at, updated_at)
VALUES
    ('manager01@example.invalid', 'manager01', @demo_manager_hash, 'Manager 01', '0900000001', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager02@example.invalid', 'manager02', @demo_manager_hash, 'Manager 02', '0900000002', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager03@example.invalid', 'manager03', @demo_manager_hash, 'Manager 03', '0900000003', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager04@example.invalid', 'manager04', @demo_manager_hash, 'Manager 04', '0900000004', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('manager05@example.invalid', 'manager05', @demo_manager_hash, 'Manager 05', '0900000005', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student01@example.invalid', 'student01', @demo_student_hash, 'Sinh viên 01', '0910000001', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student02@example.invalid', 'student02', @demo_student_hash, 'Sinh viên 02', '0910000002', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student03@example.invalid', 'student03', @demo_student_hash, 'Sinh viên 03', '0910000003', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student04@example.invalid', 'student04', @demo_student_hash, 'Sinh viên 04', '0910000004', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student05@example.invalid', 'student05', @demo_student_hash, 'Sinh viên 05', '0910000005', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student06@example.invalid', 'student06', @demo_student_hash, 'Sinh viên 06', '0910000006', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student07@example.invalid', 'student07', @demo_student_hash, 'Sinh viên 07', '0910000007', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student08@example.invalid', 'student08', @demo_student_hash, 'Sinh viên 08', '0910000008', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student09@example.invalid', 'student09', @demo_student_hash, 'Sinh viên 09', '0910000009', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student10@example.invalid', 'student10', @demo_student_hash, 'Sinh viên 10', '0910000010', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student11@example.invalid', 'student11', @demo_student_hash, 'Sinh viên 11', '0910000011', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student12@example.invalid', 'student12', @demo_student_hash, 'Sinh viên 12', '0910000012', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student13@example.invalid', 'student13', @demo_student_hash, 'Sinh viên 13', '0910000013', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student14@example.invalid', 'student14', @demo_student_hash, 'Sinh viên 14', '0910000014', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student15@example.invalid', 'student15', @demo_student_hash, 'Sinh viên 15', '0910000015', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student16@example.invalid', 'student16', @demo_student_hash, 'Sinh viên 16', '0910000016', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student17@example.invalid', 'student17', @demo_student_hash, 'Sinh viên 17', '0910000017', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student18@example.invalid', 'student18', @demo_student_hash, 'Sinh viên 18', '0910000018', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student19@example.invalid', 'student19', @demo_student_hash, 'Sinh viên 19', '0910000019', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)),
    ('student20@example.invalid', 'student20', @demo_student_hash, 'Sinh viên 20', '0910000020', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6));

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'LAB_MANAGER'
WHERE u.username IN ('manager01', 'manager02', 'manager03', 'manager04', 'manager05');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'STUDENT'
WHERE u.username REGEXP '^student(0[1-9]|1[0-9]|20)$';

-- The V25 labs are needed for NCKH ownership and lab-scoped authorization.
-- This fixture creates no application, booking, check-in or lab-use history.
INSERT INTO laboratories
    (lab_name, description, location, capacity, department, status,
     manager_id, active, deleted, created_at, updated_at)
SELECT 'AI Research Lab', 'Simple test lab for artificial intelligence and machine learning.',
       'Building A - Room 101', 30, 'Computer Science', 'AVAILABLE', u.id,
       TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'manager01';

INSERT INTO laboratories
    (lab_name, description, location, capacity, department, status,
     manager_id, active, deleted, created_at, updated_at)
SELECT 'Robotics Lab', 'Simple test lab for robotics, embedded systems, and automation.',
       'Building B - Room 202', 24, 'Mechatronics', 'AVAILABLE', u.id,
       TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'manager02';

INSERT INTO laboratories
    (lab_name, description, location, capacity, department, status,
     manager_id, active, deleted, created_at, updated_at)
SELECT 'Data Science Lab', 'Simple test lab for analytics, visualization, and data engineering.',
       'Building C - Room 303', 28, 'Information Systems', 'AVAILABLE', u.id,
       TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'manager03';

INSERT INTO laboratories
    (lab_name, description, location, capacity, department, status,
     manager_id, active, deleted, created_at, updated_at)
SELECT 'Cybersecurity Lab', 'Simple test lab for security testing and network defense.',
       'Building D - Room 404', 20, 'Cybersecurity', 'AVAILABLE', u.id,
       TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'manager04';

INSERT INTO laboratories
    (lab_name, description, location, capacity, department, status,
     manager_id, active, deleted, created_at, updated_at)
SELECT 'IoT Innovation Lab', 'Simple test lab for IoT devices, sensors, and cloud prototypes.',
       'Building E - Room 505', 26, 'Computer Engineering', 'AVAILABLE', u.id,
       TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'manager05';

SET @ai_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'AI Research Lab');
SET @robotics_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'Robotics Lab');
SET @datascience_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'Data Science Lab');
SET @cybersecurity_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'Cybersecurity Lab');
SET @iot_lab_id := (SELECT id FROM laboratories WHERE lab_name = 'IoT Innovation Lab');

SET @manager01_id := (SELECT id FROM users WHERE username = 'manager01');
SET @manager02_id := (SELECT id FROM users WHERE username = 'manager02');
SET @manager03_id := (SELECT id FROM users WHERE username = 'manager03');
SET @manager04_id := (SELECT id FROM users WHERE username = 'manager04');
SET @manager05_id := (SELECT id FROM users WHERE username = 'manager05');

-- Preserve the D35 lab-membership mapping. These rows grant no research-group
-- participation until the separate research-plan seed is imported.
INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @ai_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username REGEXP '^student(0[1-9]|1[0-5])$';

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @robotics_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username IN
    ('student01', 'student16', 'student17', 'student18', 'student19', 'student20');

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @datascience_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username IN ('student02', 'student03', 'student04', 'student05');

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @cybersecurity_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username IN ('student03', 'student04', 'student05', 'student06');

INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @iot_lab_id, 'MEMBER', TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username IN ('student04', 'student05', 'student06', 'student07');

-- Five D35 project definitions. Status/dates follow the existing planned-state
-- research fixture, rather than the obsolete single-day July dev progress seed.
INSERT INTO projects
    (lab_id, code, title, research_direction, description, objective, status,
     start_date, end_date, priority, required_products, evaluation_criteria,
     manager_id, created_by, active, deleted, created_at, updated_at)
VALUES
    (@ai_lab_id, 'D35-AI-FACE', 'Hệ thống điểm danh sinh viên bằng khuôn mặt',
     'Thị giác máy tính',
     '[D35] Đề tài chính phục vụ kiểm thử giao diện NCKH, có bảng tiến độ Kanban và dữ liệu lớn.',
     'Xây dựng luồng nhận dạng khuôn mặt qua webcam và tích hợp API check-in tự động.',
     'DRAFT', '2026-09-28', '2026-12-31', 'HIGH',
     'Prototype nhận diện qua camera; API gateway check-in',
     'Tỷ lệ chính xác của mô hình (accuracy); thời gian phản hồi (latency); độ mượt mà của giao diện.',
     @manager01_id, @manager01_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@robotics_lab_id, 'D35-ROBOT', 'Robot hỗ trợ vận chuyển trong phòng thí nghiệm',
     'Robotics', '[D35] Dự án robot tự hành hỗ trợ vận chuyển vật tư và thiết bị kiểm thử.',
     'Phát triển hệ thống tự hành điều hướng và tránh vật cản.',
     'DRAFT', '2026-09-28', '2026-12-31', 'MEDIUM',
     'Mô hình robot tự hành thực tế; thuật toán tránh vật cản',
     'Độ ổn định khi di chuyển; tốc độ xử lý vật cản.',
     @manager02_id, @manager02_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@datascience_lab_id, 'D35-DS-ANALYTICS', 'Phân tích dữ liệu sử dụng phòng thí nghiệm',
     'Khoa học dữ liệu',
     '[D35] Đề tài phân tích hành vi và tần suất sử dụng thiết bị phòng Lab của sinh viên.',
     'Xây dựng mô hình dự báo tần suất và lập kế hoạch bảo trì thiết bị.',
     'DRAFT', '2026-09-28', '2026-12-31', 'MEDIUM',
     'Mô hình dự đoán nhu cầu; Dashboard báo cáo trực quan',
     'Độ tin cậy của thuật toán dự báo; tính khả dụng của Dashboard.',
     @manager03_id, @manager03_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@cybersecurity_lab_id, 'D35-CYBER-IDS', 'Hệ thống phát hiện truy cập bất thường',
     'An toàn thông tin',
     '[D35] Đề tài giám sát và phân tích gói tin mạng để phát hiện xâm nhập trái phép nội bộ.',
     'Xây dựng hệ thống phát hiện hành vi truy cập mạng bất thường theo thời gian thực.',
     'DRAFT', '2026-09-28', '2026-12-31', 'HIGH',
     'Module IDS nhận diện gói tin xấu; Tài liệu báo cáo phân tích mạng',
     'Tỷ lệ phát hiện xâm nhập; số lượng cảnh báo giả.',
     @manager04_id, @manager04_id, TRUE, FALSE, NOW(6), NOW(6)),
    (@iot_lab_id, 'D35-IoT-ENV', 'Giám sát môi trường phòng thí nghiệm bằng IoT',
     'Mạng cảm biến',
     '[D35] Dự án đo đạc nhiệt độ, độ ẩm và các chỉ số không khí bảo vệ thiết bị phòng máy.',
     'Phát triển trạm đo tích hợp cảm biến thông minh gửi dữ liệu qua giao thức MQTT.',
     'DRAFT', '2026-09-28', '2026-12-31', 'LOW',
     'Mạch cảm biến IoT; Dashboard Web giám sát môi trường',
     'Độ chính xác đo đạc; tính ổn định khi truyền dữ liệu không dây.',
     @manager05_id, @manager05_id, TRUE, FALSE, NOW(6), NOW(6));

SET @face_project_id := (SELECT id FROM projects WHERE code = 'D35-AI-FACE');
SET @robot_project_id := (SELECT id FROM projects WHERE code = 'D35-ROBOT');
SET @ds_project_id := (SELECT id FROM projects WHERE code = 'D35-DS-ANALYTICS');
SET @cyber_project_id := (SELECT id FROM projects WHERE code = 'D35-CYBER-IDS');
SET @iot_project_id := (SELECT id FROM projects WHERE code = 'D35-IoT-ENV');

-- Group shells from D35 section 4. leader_id identifies each intended leader;
-- group_members remain empty until the separate research-plan seed is imported.
INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status,
     leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @face_project_id, 'Nhóm nhận diện khuôn mặt',
       'Nghiên cứu mô hình so khớp khuôn mặt sâu và tối ưu hóa latency.',
       'Đạt độ chính xác so khớp trên 99% trong môi trường lab.',
       'Khảo sát mô hình, lập tập dữ liệu, viết API và tích hợp UI.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student06';

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status,
     leader_id, active, deleted, created_at, updated_at)
SELECT @ai_lab_id, @face_project_id, 'Nhóm kiểm thử và báo cáo',
       'Thực hiện kiểm thử tích hợp giao diện webcam và lập kịch bản sự cố.',
       'Đảm bảo UI hoạt động trơn tru trên mọi cấu hình thiết bị.',
       'Viết kịch bản kiểm thử, chạy thử nghiệm tải và tối ưu hiệu năng.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student11';

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status,
     leader_id, active, deleted, created_at, updated_at)
SELECT @robotics_lab_id, @robot_project_id, 'Nhóm điều hướng robot',
       'Lập trình điều khiển tự động và thuật toán dò đường cho robot.',
       'Robot tự hành di chuyển mượt mà tránh chướng ngại vật.',
       'Thiết kế mạch điều khiển, lập trình tránh vật cản Lidar, chạy thử nghiệm.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student01';

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status,
     leader_id, active, deleted, created_at, updated_at)
SELECT @datascience_lab_id, @ds_project_id, 'Nhóm phân tích hành vi sử dụng PTN',
       'Tổng hợp và làm sạch dữ liệu hoạt động phòng máy để huấn luyện mô hình.',
       'Xây dựng các mô hình phân tích hành vi sinh viên.',
       'Thu thập log máy tính, tiền xử lý dữ liệu, phân tích hồi quy.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student02';

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status,
     leader_id, active, deleted, created_at, updated_at)
SELECT @cybersecurity_lab_id, @cyber_project_id, 'Nhóm giám sát an toàn hệ thống',
       'Xây dựng hệ thống phân tích luồng traffic mạng phát hiện port scan và DDoS.',
       'Ngăn ngừa các đợt tấn công thử nghiệm nội bộ.',
       'Bắt gói tin, phân tích chữ ký số độc hại, tối ưu hóa thời gian cảnh báo.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student03';

INSERT INTO research_groups
    (lab_id, project_id, name, description, objective, plan, status,
     leader_id, active, deleted, created_at, updated_at)
SELECT @iot_lab_id, @iot_project_id, 'Nhóm cảm biến môi trường',
       'Nghiên cứu thiết bị cảm biến không dây ESP32 và truyền thông MQTT.',
       'Xây dựng hệ sinh thái giám sát nhiệt độ và khí thải thông minh.',
       'Thiết kế phần cứng cảm biến, cấu hình MQTT broker, dựng Dashboard theo dõi.',
       'ACTIVE', u.id, TRUE, FALSE, NOW(6), NOW(6)
FROM users u WHERE u.username = 'student04';

-- All counts must match before the transaction can commit. No existing row is
-- updated or deleted, and this fixture does not insert research activity.
INSERT INTO nckh_prod_foundation_guard (ok)
SELECT CASE WHEN
    (SELECT COUNT(*) FROM users
     WHERE username REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20))$'
       AND email = CONCAT(username, '@example.invalid')
       AND active = TRUE AND deleted = FALSE) = 25
    AND (SELECT COUNT(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id
         WHERE u.username REGEXP '^(manager0[1-5]|student(0[1-9]|1[0-9]|20))$') = 25
    AND (SELECT COUNT(*) FROM laboratories l JOIN users u ON u.id = l.manager_id
         WHERE l.lab_name IN ('AI Research Lab', 'Robotics Lab', 'Data Science Lab',
                              'Cybersecurity Lab', 'IoT Innovation Lab')
           AND u.username REGEXP '^manager0[1-5]$'
           AND l.active = TRUE AND l.deleted = FALSE) = 5
    AND (SELECT COUNT(*) FROM memberships m JOIN laboratories l ON l.id = m.lab_id
         WHERE l.lab_name IN ('AI Research Lab', 'Robotics Lab', 'Data Science Lab',
                              'Cybersecurity Lab', 'IoT Innovation Lab')
           AND m.active = TRUE AND m.deleted = FALSE) = 33
    AND (SELECT COUNT(*) FROM projects p JOIN laboratories l ON l.id = p.lab_id
         WHERE p.code IN ('D35-AI-FACE', 'D35-ROBOT', 'D35-DS-ANALYTICS',
                          'D35-CYBER-IDS', 'D35-IoT-ENV')
           AND p.status = 'DRAFT' AND p.start_date = '2026-09-28'
           AND p.end_date = '2026-12-31'
           AND p.manager_id = l.manager_id AND p.created_by = l.manager_id
           AND p.active = TRUE AND p.deleted = FALSE) = 5
    AND (SELECT COUNT(*) FROM research_groups rg JOIN projects p ON p.id = rg.project_id
         WHERE p.code IN ('D35-AI-FACE', 'D35-ROBOT', 'D35-DS-ANALYTICS',
                          'D35-CYBER-IDS', 'D35-IoT-ENV')
           AND rg.lab_id = p.lab_id AND rg.active = TRUE AND rg.deleted = FALSE) = 6
    AND (SELECT COUNT(*) FROM group_members gm JOIN research_groups rg ON rg.id = gm.group_id
         JOIN projects p ON p.id = rg.project_id
         WHERE p.code IN ('D35-AI-FACE', 'D35-ROBOT', 'D35-DS-ANALYTICS',
                          'D35-CYBER-IDS', 'D35-IoT-ENV')) = 0
    THEN 1 ELSE 0 END;

COMMIT;
DROP TEMPORARY TABLE nckh_prod_foundation_guard;
