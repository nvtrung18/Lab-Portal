-- Manual, additive NCKH fixture for a migrated MySQL 8 development database.
-- Run with DEV_SEED_ENABLED=false. Never run the D35 rollback for this fixture.
-- All people and email addresses below are synthetic. Password hashes match the
-- existing development-only manager123 / user123 credentials in V25.
-- Planned research state: topics, projects, groups, people, milestones and TODO tasks.
-- No completed work, report submission, evaluation, product, booking, check-in
-- or lab usage is fabricated.
-- If the older progress-rich fixture was already imported, run the separate
-- reconcile-nckh-thesis-initial.sql once before rerunning this additive seed.
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO users (email, username, password, full_name, status, active, deleted, created_at, updated_at)
SELECT 'nckh.manager@example.invalid', 'nckh_manager',
       '$2b$12$NcgrOVryzefwVh/wB1fytOBzAeE4VDyHqyTh9dtoivIPxCK07B3wm',
       'Nguyễn Minh Anh (dữ liệu mẫu)', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'nckh_manager');
INSERT INTO users (email, username, password, full_name, status, active, deleted, created_at, updated_at)
SELECT 'nckh.leader@example.invalid', 'nckh_leader',
       '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y',
       'Trần Gia Hân (dữ liệu mẫu)', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'nckh_leader');
INSERT INTO users (email, username, password, full_name, status, active, deleted, created_at, updated_at)
SELECT 'nckh.member1@example.invalid', 'nckh_member1',
       '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y',
       'Lê Quang Huy (dữ liệu mẫu)', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'nckh_member1');
INSERT INTO users (email, username, password, full_name, status, active, deleted, created_at, updated_at)
SELECT 'nckh.member2@example.invalid', 'nckh_member2',
       '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y',
       'Phạm Bảo Ngọc (dữ liệu mẫu)', 'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'nckh_member2');

-- Additional synthetic students: four per group, including each group leader.
SET @nckh_student_hash := '$2b$12$0vik4je4VznEWttFBRD4euDzv5IJtR8uTfVHUHrsgNNPPrDik.z1y';
INSERT INTO users (email, username, password, full_name, status, active, deleted, created_at, updated_at)
SELECT demo.email, demo.username, @nckh_student_hash, demo.full_name,
       'ACTIVE', TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT 'nckh.member3@example.invalid' AS email, 'nckh_member3' AS username,
           'Võ Thảo Vy (dữ liệu mẫu)' AS full_name
    UNION ALL SELECT 'nckh.g2.leader@example.invalid', 'nckh_g2_leader', 'Bùi Khánh Linh (dữ liệu mẫu)'
    UNION ALL SELECT 'nckh.g2.member1@example.invalid', 'nckh_g2_member1', 'Nguyễn Tuấn Kiệt (dữ liệu mẫu)'
    UNION ALL SELECT 'nckh.g2.member2@example.invalid', 'nckh_g2_member2', 'Trương Mỹ Duyên (dữ liệu mẫu)'
    UNION ALL SELECT 'nckh.g2.member3@example.invalid', 'nckh_g2_member3', 'Hồ Nhật Nam (dữ liệu mẫu)'
    UNION ALL SELECT 'nckh.g3.leader@example.invalid', 'nckh_g3_leader', 'Đặng Minh Khoa (dữ liệu mẫu)'
    UNION ALL SELECT 'nckh.g3.member1@example.invalid', 'nckh_g3_member1', 'Đỗ Ngọc Hà (dữ liệu mẫu)'
    UNION ALL SELECT 'nckh.g3.member2@example.invalid', 'nckh_g3_member2', 'Vũ Quốc Bảo (dữ liệu mẫu)'
    UNION ALL SELECT 'nckh.g3.member3@example.invalid', 'nckh_g3_member3', 'Phan Thu Trang (dữ liệu mẫu)'
) AS demo
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.username = demo.username);

SET @nckh_manager := (SELECT id FROM users WHERE username = 'nckh_manager');
SET @nckh_leader := (SELECT id FROM users WHERE username = 'nckh_leader');
SET @nckh_member1 := (SELECT id FROM users WHERE username = 'nckh_member1');
SET @nckh_member2 := (SELECT id FROM users WHERE username = 'nckh_member2');
SET @nckh_member3 := (SELECT id FROM users WHERE username = 'nckh_member3');
SET @nckh_g2_leader := (SELECT id FROM users WHERE username = 'nckh_g2_leader');
SET @nckh_g2_member1 := (SELECT id FROM users WHERE username = 'nckh_g2_member1');
SET @nckh_g2_member2 := (SELECT id FROM users WHERE username = 'nckh_g2_member2');
SET @nckh_g2_member3 := (SELECT id FROM users WHERE username = 'nckh_g2_member3');
SET @nckh_g3_leader := (SELECT id FROM users WHERE username = 'nckh_g3_leader');
SET @nckh_g3_member1 := (SELECT id FROM users WHERE username = 'nckh_g3_member1');
SET @nckh_g3_member2 := (SELECT id FROM users WHERE username = 'nckh_g3_member2');
SET @nckh_g3_member3 := (SELECT id FROM users WHERE username = 'nckh_g3_member3');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'LAB_MANAGER'
WHERE u.username = 'nckh_manager'
  AND NOT EXISTS (SELECT 1 FROM user_roles x WHERE x.user_id = u.id AND x.role_id = r.id);
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'STUDENT'
WHERE u.username IN ('nckh_leader', 'nckh_member1', 'nckh_member2', 'nckh_member3',
                     'nckh_g2_leader', 'nckh_g2_member1', 'nckh_g2_member2', 'nckh_g2_member3',
                     'nckh_g3_leader', 'nckh_g3_member1', 'nckh_g3_member2', 'nckh_g3_member3')
  AND NOT EXISTS (SELECT 1 FROM user_roles x WHERE x.user_id = u.id AND x.role_id = r.id);

-- The lab row is required only for NCKH ownership and authorization.
INSERT INTO laboratories (lab_name, description, location, capacity, department, status,
                          manager_id, active, deleted, created_at, updated_at)
SELECT 'NCKH Thesis Demo 2026', 'Không gian dữ liệu mẫu cho nghiên cứu khoa học',
       'Cơ sở dữ liệu mẫu', 30, 'Nghiên cứu khoa học', 'AVAILABLE',
       @nckh_manager, TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM laboratories WHERE lab_name = 'NCKH Thesis Demo 2026');
SET @nckh_lab := (SELECT id FROM laboratories WHERE lab_name = 'NCKH Thesis Demo 2026');

-- These two students also hold lab-scoped appointments, independently of their
-- MEMBER roles inside the research group.
INSERT INTO memberships (user_id, lab_id, role, active, deleted, created_at, updated_at)
SELECT u.id, @nckh_lab,
       CASE u.username
           WHEN 'nckh_member1' THEN 'DEPUTY'
           WHEN 'nckh_member2' THEN 'SECRETARY'
           ELSE 'MEMBER'
       END,
       TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('nckh_leader', 'nckh_member1', 'nckh_member2', 'nckh_member3',
                     'nckh_g2_leader', 'nckh_g2_member1', 'nckh_g2_member2', 'nckh_g2_member3',
                     'nckh_g3_leader', 'nckh_g3_member1', 'nckh_g3_member2', 'nckh_g3_member3')
  AND NOT EXISTS (SELECT 1 FROM memberships m WHERE m.user_id = u.id AND m.lab_id = @nckh_lab AND m.deleted = FALSE);

INSERT INTO research_topics (lab_id, name, description, requirements, reference_links,
                             manager_id, created_by, status, active, deleted, created_at, updated_at)
SELECT @nckh_lab, 'NCKH-2026: Phân tích tài liệu nghiên cứu',
       'Nghiên cứu quản lý tiến độ, phân loại và truy xuất tài liệu học thuật.',
       'Có bộ dữ liệu được phép sử dụng, quy trình đánh giá và nhật ký tiến độ.',
       NULL, @nckh_manager, @nckh_manager, 'RECRUITING',
       TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM research_topics
                  WHERE lab_id = @nckh_lab AND name = 'NCKH-2026: Phân tích tài liệu nghiên cứu');
SET @nckh_topic := (SELECT id FROM research_topics
                    WHERE lab_id = @nckh_lab AND name = 'NCKH-2026: Phân tích tài liệu nghiên cứu' LIMIT 1);

INSERT INTO projects (lab_id, topic_id, code, title, research_direction, description, objective,
                      status, start_date, end_date, priority, required_products, evaluation_criteria,
                      manager_id, created_by, active, deleted, created_at, updated_at)
SELECT @nckh_lab, @nckh_topic, 'THESIS-NCKH-2026',
       'Hệ thống theo dõi tiến độ và tổng hợp tài liệu nghiên cứu',
       'Hệ thống thông tin nghiên cứu',
       'Đề tài mẫu ở giai đoạn khởi tạo, chưa có hoạt động thực hiện hoặc báo cáo.',
       'Theo dõi nhiệm vụ theo nhóm và tổng hợp minh chứng nghiên cứu có kiểm soát quyền.',
       'DRAFT', '2026-02-01', '2026-12-15', 'HIGH',
       'Báo cáo tổng kết; mã nguồn; bộ dữ liệu có quyền sử dụng',
       'Tính đúng đắn; tiến độ; chất lượng báo cáo; khả năng tái lập',
       @nckh_manager, @nckh_manager, TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE code = 'THESIS-NCKH-2026');
SET @nckh_project := (SELECT id FROM projects WHERE code = 'THESIS-NCKH-2026' LIMIT 1);

INSERT INTO research_groups (lab_id, topic_id, project_id, name, description, objective, plan,
                             research_direction, status, leader_id, active, deleted, created_at, updated_at)
SELECT @nckh_lab, @nckh_topic, @nckh_project, 'Nhóm Tổng hợp tài liệu',
       'Nhóm thực hiện khảo sát và phân loại tài liệu nghiên cứu.',
       'Xây dựng tiêu chí phân loại tài liệu và bộ dữ liệu được thẩm định.',
       'Khảo sát nguồn; thiết kế tiêu chí; kiểm tra chất lượng; báo cáo kết quả.',
       'Phân tích tài liệu', 'ACTIVE', @nckh_leader,
       TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM research_groups
                  WHERE project_id = @nckh_project AND name = 'Nhóm Tổng hợp tài liệu');
SET @nckh_group := (SELECT id FROM research_groups
                    WHERE project_id = @nckh_project AND name = 'Nhóm Tổng hợp tài liệu' LIMIT 1);

INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @nckh_group, u.id,
       CASE WHEN u.id = @nckh_leader THEN 'LEADER' ELSE 'MEMBER' END,
       '2026-02-05 09:00:00', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('nckh_leader', 'nckh_member1', 'nckh_member2', 'nckh_member3')
  AND NOT EXISTS (SELECT 1 FROM group_members gm WHERE gm.group_id = @nckh_group AND gm.user_id = u.id);

-- Topic/project/group 2: synthetic academic metadata research.
INSERT INTO research_topics (lab_id, name, description, requirements, reference_links,
                             manager_id, created_by, status, active, deleted, created_at, updated_at)
SELECT @nckh_lab, 'NCKH-2026: Khai phá dữ liệu học thuật',
       'Đề tài mẫu về chuẩn hóa và đối chiếu metadata của tài liệu học thuật.',
       'Chỉ dùng dữ liệu mẫu hoặc dữ liệu được phép sử dụng; ghi rõ tiêu chí đối chiếu.',
       NULL, @nckh_manager, @nckh_manager, 'RECRUITING',
       TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM research_topics
                  WHERE lab_id = @nckh_lab AND name = 'NCKH-2026: Khai phá dữ liệu học thuật');
SET @nckh_topic2 := (SELECT id FROM research_topics
                     WHERE lab_id = @nckh_lab AND name = 'NCKH-2026: Khai phá dữ liệu học thuật' LIMIT 1);

INSERT INTO projects (lab_id, topic_id, code, title, research_direction, description, objective,
                      status, start_date, end_date, priority, required_products, evaluation_criteria,
                      manager_id, created_by, active, deleted, created_at, updated_at)
SELECT @nckh_lab, @nckh_topic2, 'THESIS-NCKH-2026-B',
       'Chuẩn hóa và đối chiếu metadata học thuật', 'Khai phá dữ liệu học thuật',
       'Dự án mẫu xây dựng quy trình chuẩn hóa metadata và kiểm tra sai lệch.',
       'Lập bộ tiêu chí rõ ràng và đánh giá kết quả đối chiếu trên dữ liệu mẫu.',
       'DRAFT', '2026-02-15', '2026-12-15', 'MEDIUM',
       'Báo cáo phương pháp; bộ metadata mẫu có quyền sử dụng',
       'Chất lượng dữ liệu; khả năng tái lập; mức độ hoàn thành nhiệm vụ',
       @nckh_manager, @nckh_manager, TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE code = 'THESIS-NCKH-2026-B');
SET @nckh_project2 := (SELECT id FROM projects WHERE code = 'THESIS-NCKH-2026-B' LIMIT 1);

INSERT INTO research_groups (lab_id, topic_id, project_id, name, description, objective, plan,
                             research_direction, status, leader_id, active, deleted, created_at, updated_at)
SELECT @nckh_lab, @nckh_topic2, @nckh_project2, 'Nhóm Khai phá dữ liệu',
       'Nhóm mẫu phụ trách metadata học thuật.',
       'Chuẩn hóa trường dữ liệu và đối chiếu các bản ghi mẫu.',
       'Xác định trường; làm sạch dữ liệu; đối chiếu; rà soát sai lệch.',
       'Khai phá dữ liệu học thuật', 'ACTIVE', @nckh_g2_leader,
       TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM research_groups
                  WHERE project_id = @nckh_project2 AND name = 'Nhóm Khai phá dữ liệu');
SET @nckh_group2 := (SELECT id FROM research_groups
                     WHERE project_id = @nckh_project2 AND name = 'Nhóm Khai phá dữ liệu' LIMIT 1);

INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @nckh_group2, u.id,
       CASE WHEN u.id = @nckh_g2_leader THEN 'LEADER' ELSE 'MEMBER' END,
       '2026-02-20 09:00:00', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('nckh_g2_leader', 'nckh_g2_member1', 'nckh_g2_member2', 'nckh_g2_member3')
  AND NOT EXISTS (SELECT 1 FROM group_members gm WHERE gm.group_id = @nckh_group2 AND gm.user_id = u.id);

-- Topic/project/group 3: synthetic research progress analysis.
INSERT INTO research_topics (lab_id, name, description, requirements, reference_links,
                             manager_id, created_by, status, active, deleted, created_at, updated_at)
SELECT @nckh_lab, 'NCKH-2026: Phân tích tiến độ đề tài',
       'Đề tài mẫu về chỉ báo và cảnh báo tiến độ nghiên cứu.',
       'Dùng trạng thái nhiệm vụ và mốc thời gian mẫu; không suy diễn kết quả thực tế.',
       NULL, @nckh_manager, @nckh_manager, 'RECRUITING',
       TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM research_topics
                  WHERE lab_id = @nckh_lab AND name = 'NCKH-2026: Phân tích tiến độ đề tài');
SET @nckh_topic3 := (SELECT id FROM research_topics
                     WHERE lab_id = @nckh_lab AND name = 'NCKH-2026: Phân tích tiến độ đề tài' LIMIT 1);

INSERT INTO projects (lab_id, topic_id, code, title, research_direction, description, objective,
                      status, start_date, end_date, priority, required_products, evaluation_criteria,
                      manager_id, created_by, active, deleted, created_at, updated_at)
SELECT @nckh_lab, @nckh_topic3, 'THESIS-NCKH-2026-C',
       'Bảng phân tích và cảnh báo tiến độ đề tài NCKH', 'Phân tích tiến độ nghiên cứu',
       'Dự án mẫu tổng hợp tiến độ theo nhiệm vụ và mốc thời gian.',
       'Xác định chỉ báo tiến độ và mô tả tình huống cần rà soát.',
       'DRAFT', '2026-03-01', '2026-12-20', 'MEDIUM',
       'Báo cáo phương pháp; bảng chỉ báo tiến độ mẫu',
       'Tính rõ ràng của chỉ báo; khả năng kiểm tra; tiến độ thực hiện',
       @nckh_manager, @nckh_manager, TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM projects WHERE code = 'THESIS-NCKH-2026-C');
SET @nckh_project3 := (SELECT id FROM projects WHERE code = 'THESIS-NCKH-2026-C' LIMIT 1);

INSERT INTO research_groups (lab_id, topic_id, project_id, name, description, objective, plan,
                             research_direction, status, leader_id, active, deleted, created_at, updated_at)
SELECT @nckh_lab, @nckh_topic3, @nckh_project3, 'Nhóm Phân tích tiến độ',
       'Nhóm mẫu nghiên cứu chỉ báo tiến độ đề tài.',
       'Tổng hợp dữ liệu nhiệm vụ và thiết kế quy tắc cảnh báo dễ kiểm tra.',
       'Xác định chỉ số; chuẩn hóa trạng thái; thiết kế cảnh báo; rà soát tình huống.',
       'Phân tích tiến độ nghiên cứu', 'ACTIVE', @nckh_g3_leader,
       TRUE, FALSE, NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM research_groups
                  WHERE project_id = @nckh_project3 AND name = 'Nhóm Phân tích tiến độ');
SET @nckh_group3 := (SELECT id FROM research_groups
                     WHERE project_id = @nckh_project3 AND name = 'Nhóm Phân tích tiến độ' LIMIT 1);

INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT @nckh_group3, u.id,
       CASE WHEN u.id = @nckh_g3_leader THEN 'LEADER' ELSE 'MEMBER' END,
       '2026-03-05 09:00:00', TRUE, FALSE, NOW(6), NOW(6)
FROM users u
WHERE u.username IN ('nckh_g3_leader', 'nckh_g3_member1', 'nckh_g3_member2', 'nckh_g3_member3')
  AND NOT EXISTS (SELECT 1 FROM group_members gm WHERE gm.group_id = @nckh_group3 AND gm.user_id = u.id);

-- Future-dated plans only. Keep milestone and task progress at zero and leave
-- report, activity, review and product tables untouched.
-- Match on project and title so rerunning this seed does not duplicate plans.
INSERT INTO milestones (project_id, group_id, name, title, description, start_date, end_date,
                        deadline, status, progress_percent, created_by, assigned_to_student_id,
                        active, deleted, created_at, updated_at)
SELECT seed.project_id, seed.group_id, seed.title, seed.title, seed.description,
       seed.start_date, seed.deadline, seed.deadline, 'NOT_STARTED', 0,
       @nckh_manager, seed.assignee_id, TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT @nckh_project AS project_id, @nckh_group AS group_id, 'Khảo sát yêu cầu và nguồn tài liệu' AS title,
           'Lập danh mục nguồn học thuật được phép sử dụng, tiêu chí chọn lọc và cấu trúc metadata đầu vào.' AS description, '2026-09-28' AS start_date,
           '2026-10-16' AS deadline, @nckh_leader AS assignee_id
    UNION ALL SELECT @nckh_project, @nckh_group, 'Thiết kế quy trình phân loại tài liệu',
           'Xây dựng bộ nhãn, quy tắc gắn nhãn và tiêu chí kiểm tra tính nhất quán trước khi xử lý dữ liệu.', '2026-10-19',
           '2026-11-06', @nckh_member1
    UNION ALL SELECT @nckh_project, @nckh_group, 'Xây dựng nguyên mẫu tìm kiếm tài liệu',
           'Thiết kế dữ liệu lưu trữ, bộ lọc tra cứu và các tình huống dùng để kiểm tra kết quả tìm kiếm.', '2026-11-09',
           '2026-11-27', @nckh_member2
    UNION ALL SELECT @nckh_project, @nckh_group, 'Đánh giá và chuẩn bị báo cáo tổng kết',
           'Lập kế hoạch đánh giá truy xuất và dàn ý báo cáo; chưa thực hiện đánh giá hay nộp báo cáo.', '2026-11-30',
           '2026-12-15', @nckh_member3
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Khảo sát cấu trúc metadata học thuật',
           'Xác định nguồn dữ liệu mẫu, các trường metadata cần thiết và điều kiện sử dụng dữ liệu.', '2026-09-28',
           '2026-10-16', @nckh_g2_leader
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế quy tắc chuẩn hóa metadata',
           'Định nghĩa cách chuẩn hóa tên tác giả, ngày tháng, mã định danh và xử lý giá trị thiếu.', '2026-10-19',
           '2026-11-06', @nckh_g2_member1
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế đối chiếu và phát hiện sai lệch',
           'Xây dựng tiêu chí so khớp bản ghi và phân loại các tình huống trùng, thiếu hoặc mâu thuẫn.', '2026-11-09',
           '2026-11-27', @nckh_g2_member2
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Đánh giá dữ liệu mẫu và chuẩn bị báo cáo',
           'Lập kế hoạch đo chất lượng metadata và dàn ý báo cáo; chưa thực hiện hay nộp kết quả.', '2026-11-30',
           '2026-12-15', @nckh_g2_member3
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Xác định chỉ báo theo dõi tiến độ',
           'Định nghĩa chỉ báo dựa trên kế hoạch mốc, nhiệm vụ và trạng thái có sẵn, không suy diễn kết quả thực hiện.', '2026-09-30',
           '2026-10-20', @nckh_g3_leader
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Chuẩn hóa dữ liệu mốc và nhiệm vụ',
           'Lập quy tắc đọc trạng thái, ngày hạn và quan hệ giữa đề tài, nhóm, mốc và nhiệm vụ.', '2026-10-21',
           '2026-11-10', @nckh_g3_member1
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Thiết kế bảng phân tích và cảnh báo',
           'Phác thảo bảng tổng quan và điều kiện cảnh báo dựa trên hạn dự kiến, không ghi nhận cảnh báo thực tế.', '2026-11-11',
           '2026-12-01', @nckh_g3_member2
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Kiểm tra tình huống và chuẩn bị báo cáo',
           'Lập kế hoạch kiểm tra các kịch bản tiến độ và dàn ý báo cáo; chưa chạy đánh giá hay nộp báo cáo.', '2026-12-02',
           '2026-12-20', @nckh_g3_member3
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM milestones m WHERE m.project_id = seed.project_id AND m.title = seed.title
);

INSERT INTO tasks (project_id, group_id, milestone_id, assignee_id, title, description,
                   deadline, due_date, status, priority, type, progress_percent, created_by,
                   active, deleted, created_at, updated_at)
SELECT seed.project_id, seed.group_id, m.id, seed.assignee_id, seed.title, seed.description,
       seed.due_date, seed.due_date, 'TODO', seed.priority, 'TASK', 0, @nckh_manager,
       TRUE, FALSE, NOW(6), NOW(6)
FROM (
    SELECT @nckh_project AS project_id, @nckh_group AS group_id, 'Khảo sát yêu cầu và nguồn tài liệu' AS milestone_title,
           @nckh_leader AS assignee_id, 'Liệt kê nguồn tài liệu được phép sử dụng' AS title,
           'Lập danh mục tạp chí, kho mở và tài liệu được phép sử dụng; ghi nguồn và điều kiện truy cập.' AS description, '2026-10-05' AS due_date, 'HIGH' AS priority
    UNION ALL SELECT @nckh_project, @nckh_group, 'Khảo sát yêu cầu và nguồn tài liệu',
           @nckh_member1, 'Xác định tiêu chí chọn và loại tài liệu',
           'Đề xuất tiêu chí về chủ đề, thời gian, tính đầy đủ và lý do loại tài liệu khỏi bộ khảo sát.', '2026-10-09', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Khảo sát yêu cầu và nguồn tài liệu',
           @nckh_member2, 'Thiết kế biểu mẫu metadata tài liệu',
           'Xác định các trường tiêu đề, tác giả, năm, nguồn, từ khóa và giấy phép cho từng tài liệu.', '2026-10-14', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Thiết kế quy trình phân loại tài liệu',
           @nckh_member1, 'Xây dựng bộ nhãn chủ đề',
           'Đề xuất hệ nhãn theo hướng nghiên cứu và định nghĩa ngắn gọn cho từng nhãn.', '2026-10-26', 'HIGH'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Thiết kế quy trình phân loại tài liệu',
           @nckh_member3, 'Quy định cách gắn nhãn và xử lý trùng lặp',
           'Viết hướng dẫn gắn nhãn, trường hợp nhiều nhãn và quy tắc nhận diện bản ghi trùng.', '2026-11-02', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Thiết kế quy trình phân loại tài liệu',
           @nckh_leader, 'Định nghĩa tiêu chí kiểm tra nhãn',
           'Lập cách chọn mẫu rà soát và tiêu chí ghi nhận bất đồng giữa người gắn nhãn.', '2026-11-05', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Xây dựng nguyên mẫu tìm kiếm tài liệu',
           @nckh_member2, 'Thiết kế cấu trúc lưu trữ tài liệu',
           'Phác thảo bảng tài liệu, nhãn và liên kết nguồn; xác định khóa để tránh bản ghi trùng.', '2026-11-16', 'HIGH'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Xây dựng nguyên mẫu tìm kiếm tài liệu',
           @nckh_member3, 'Xây dựng bộ lọc theo chủ đề và năm',
           'Định nghĩa cách kết hợp điều kiện chủ đề, năm xuất bản và từ khóa trong màn tra cứu.', '2026-11-23', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Xây dựng nguyên mẫu tìm kiếm tài liệu',
           @nckh_member1, 'Chuẩn bị kịch bản kiểm tra kết quả tìm kiếm',
           'Soạn các truy vấn mẫu và kết quả mong đợi để kiểm tra độ chính xác của bộ lọc.', '2026-11-26', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Đánh giá và chuẩn bị báo cáo tổng kết',
           @nckh_member3, 'Lập bộ tiêu chí đánh giá truy xuất',
           'Đề xuất độ đúng, độ đầy đủ và khả năng truy vết nguồn cho các truy vấn mẫu.', '2026-12-04', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Đánh giá và chuẩn bị báo cáo tổng kết',
           @nckh_leader, 'Thiết kế bảng đối chiếu kết quả và tiêu chí',
           'Chuẩn bị biểu mẫu ghi kết quả truy vấn, nhận xét và sai lệch khi đánh giá sau này.', '2026-12-09', 'MEDIUM'
    UNION ALL SELECT @nckh_project, @nckh_group, 'Đánh giá và chuẩn bị báo cáo tổng kết',
           @nckh_member2, 'Lập dàn ý báo cáo tổng kết',
           'Đề xuất cấu trúc phần dữ liệu, phương pháp, kết quả và giới hạn nghiên cứu.', '2026-12-14', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Khảo sát cấu trúc metadata học thuật',
           @nckh_g2_leader, 'Liệt kê nguồn metadata mẫu',
           'Lập danh mục bộ dữ liệu học thuật mở và ghi giấy phép, phạm vi sử dụng dự kiến.', '2026-10-05', 'HIGH'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Khảo sát cấu trúc metadata học thuật',
           @nckh_g2_member1, 'Lập từ điển trường metadata',
           'Mô tả định dạng và ý nghĩa của tiêu đề, tác giả, DOI, năm, nguồn xuất bản và từ khóa.', '2026-10-09', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Khảo sát cấu trúc metadata học thuật',
           @nckh_g2_member2, 'Xác định trường bắt buộc và trường tùy chọn',
           'Đề xuất quy tắc nhận biết bản ghi thiếu dữ liệu để chuẩn bị cho bước làm sạch.', '2026-10-14', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế quy tắc chuẩn hóa metadata',
           @nckh_g2_member1, 'Quy định định dạng tên tác giả và nguồn',
           'Soạn quy tắc khoảng trắng, chữ hoa và thứ tự thành phần tên để đối chiếu nhất quán.', '2026-10-26', 'HIGH'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế quy tắc chuẩn hóa metadata',
           @nckh_g2_member3, 'Thiết kế cách chuẩn hóa DOI và ngày xuất bản',
           'Xác định cách kiểm tra cú pháp DOI và chuyển ngày, năm về định dạng dùng chung.', '2026-11-02', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế quy tắc chuẩn hóa metadata',
           @nckh_g2_leader, 'Định nghĩa xử lý giá trị thiếu',
           'Lập quy tắc đánh dấu dữ liệu vắng mặt và giữ lại giá trị gốc để truy vết.', '2026-11-05', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế đối chiếu và phát hiện sai lệch',
           @nckh_g2_member2, 'Thiết kế khóa đối chiếu bản ghi',
           'Đề xuất thứ tự so khớp theo DOI, tiêu đề chuẩn hóa, tác giả và năm xuất bản.', '2026-11-16', 'HIGH'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế đối chiếu và phát hiện sai lệch',
           @nckh_g2_member3, 'Lập danh mục loại sai lệch',
           'Phân biệt bản ghi trùng, thiếu trường, sai định dạng và thông tin mâu thuẫn.', '2026-11-23', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Thiết kế đối chiếu và phát hiện sai lệch',
           @nckh_g2_member1, 'Chuẩn bị bộ tình huống kiểm tra',
           'Soạn dữ liệu đầu vào và kết quả mong đợi cho từng loại sai lệch.', '2026-11-26', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Đánh giá dữ liệu mẫu và chuẩn bị báo cáo',
           @nckh_g2_member3, 'Định nghĩa chỉ số chất lượng metadata',
           'Đề xuất tỷ lệ đủ trường, tỷ lệ trùng và tỷ lệ bản ghi cần rà soát thủ công.', '2026-12-04', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Đánh giá dữ liệu mẫu và chuẩn bị báo cáo',
           @nckh_g2_leader, 'Thiết kế bảng tổng hợp sai lệch',
           'Chuẩn bị biểu mẫu đếm và mô tả các loại sai lệch theo nguồn dữ liệu.', '2026-12-09', 'MEDIUM'
    UNION ALL SELECT @nckh_project2, @nckh_group2, 'Đánh giá dữ liệu mẫu và chuẩn bị báo cáo',
           @nckh_g2_member2, 'Lập dàn ý báo cáo phương pháp',
           'Đề xuất các mục nguồn dữ liệu, chuẩn hóa, đối chiếu, đánh giá và giới hạn.', '2026-12-14', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Xác định chỉ báo theo dõi tiến độ',
           @nckh_g3_leader, 'Liệt kê chỉ báo cho mốc và nhiệm vụ',
           'Đề xuất số lượng nhiệm vụ theo trạng thái, hạn dự kiến và tỷ lệ đúng hạn cần quan sát.', '2026-10-07', 'HIGH'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Xác định chỉ báo theo dõi tiến độ',
           @nckh_g3_member1, 'Định nghĩa nguồn dữ liệu cho từng chỉ báo',
           'Chỉ rõ bảng, trường và bộ lọc dự kiến dùng để tính từng chỉ báo.', '2026-10-13', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Xác định chỉ báo theo dõi tiến độ',
           @nckh_g3_member2, 'Xác định quy tắc hiển thị khi thiếu dữ liệu',
           'Mô tả cách ghi nhận trường hợp chưa có mốc, nhiệm vụ hoặc hạn dự kiến.', '2026-10-19', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Chuẩn hóa dữ liệu mốc và nhiệm vụ',
           @nckh_g3_member1, 'Lập sơ đồ liên kết đề tài và công việc',
           'Mô tả khóa liên kết giữa đề tài, nhóm, mốc, nhiệm vụ và người phụ trách.', '2026-10-28', 'HIGH'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Chuẩn hóa dữ liệu mốc và nhiệm vụ',
           @nckh_g3_member3, 'Quy định cách xử lý trạng thái và hạn',
           'Định nghĩa đầu vào hợp lệ cho trạng thái, ngày hạn và nhiệm vụ chưa được phân công.', '2026-11-04', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Chuẩn hóa dữ liệu mốc và nhiệm vụ',
           @nckh_g3_leader, 'Chuẩn bị dữ liệu mẫu cho các tình huống',
           'Soạn bản ghi minh họa cho công việc chưa bắt đầu, gần hạn và thiếu hạn.', '2026-11-09', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Thiết kế bảng phân tích và cảnh báo',
           @nckh_g3_member2, 'Thiết kế bố cục bảng chỉ báo',
           'Phác thảo số liệu tổng quan, bộ lọc theo nhóm và danh sách nhiệm vụ cần theo dõi.', '2026-11-18', 'HIGH'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Thiết kế bảng phân tích và cảnh báo',
           @nckh_g3_member3, 'Định nghĩa điều kiện cảnh báo gần hạn',
           'Đề xuất ngưỡng minh họa và cách tránh lặp cảnh báo cho cùng một nhiệm vụ.', '2026-11-25', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Thiết kế bảng phân tích và cảnh báo',
           @nckh_g3_member1, 'Mô tả dữ liệu chi tiết khi mở cảnh báo',
           'Xác định thông tin mốc, nhiệm vụ, người phụ trách và hạn cần hiển thị.', '2026-11-30', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Kiểm tra tình huống và chuẩn bị báo cáo',
           @nckh_g3_member3, 'Soạn kịch bản kiểm tra chỉ báo',
           'Chuẩn bị đầu vào và kết quả mong đợi cho tình huống bình thường, gần hạn và thiếu dữ liệu.', '2026-12-08', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Kiểm tra tình huống và chuẩn bị báo cáo',
           @nckh_g3_leader, 'Thiết kế bảng đối chiếu cảnh báo',
           'Lập biểu mẫu ghi ngưỡng, nguyên nhân cảnh báo và kết quả kiểm tra sau này.', '2026-12-14', 'MEDIUM'
    UNION ALL SELECT @nckh_project3, @nckh_group3, 'Kiểm tra tình huống và chuẩn bị báo cáo',
           @nckh_g3_member2, 'Lập dàn ý báo cáo phân tích tiến độ',
           'Đề xuất mục phương pháp, chỉ báo, kiểm tra tình huống và giới hạn dữ liệu.', '2026-12-19', 'MEDIUM'
) AS seed
JOIN milestones m ON m.project_id = seed.project_id AND m.group_id = seed.group_id
                 AND m.title = seed.milestone_title AND m.active = TRUE AND m.deleted = FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM tasks t WHERE t.milestone_id = m.id AND t.title = seed.title
);

COMMIT;

-- Read-only acceptance checks for a fresh fixture or a reconciled old fixture.
-- Expected: 13 users (one manager, 12 students), one lab, three topics,
-- three draft projects, three active groups, 12 group members and lab memberships,
-- four NOT_STARTED milestones and 12 TODO tasks per project (all progress = 0).
-- The deputy and secretary are already among the 12 group members.
-- Submission, activity and review tables below must remain empty for these projects.
SELECT 'users' AS item, COUNT(*) AS total FROM users
WHERE username IN ('nckh_manager', 'nckh_leader', 'nckh_member1', 'nckh_member2', 'nckh_member3',
                   'nckh_g2_leader', 'nckh_g2_member1', 'nckh_g2_member2', 'nckh_g2_member3',
                   'nckh_g3_leader', 'nckh_g3_member1', 'nckh_g3_member2', 'nckh_g3_member3')
UNION ALL SELECT 'laboratories', COUNT(*) FROM laboratories WHERE id = @nckh_lab
UNION ALL SELECT 'lab_memberships', COUNT(*) FROM memberships
          WHERE lab_id = @nckh_lab AND active = TRUE AND deleted = FALSE
UNION ALL SELECT 'topics', COUNT(*) FROM research_topics
          WHERE id IN (@nckh_topic, @nckh_topic2, @nckh_topic3)
UNION ALL SELECT 'projects', COUNT(*) FROM projects
          WHERE id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'groups', COUNT(*) FROM research_groups
          WHERE id IN (@nckh_group, @nckh_group2, @nckh_group3)
UNION ALL SELECT 'group_members', COUNT(*) FROM group_members
          WHERE group_id IN (@nckh_group, @nckh_group2, @nckh_group3)
            AND active = TRUE AND deleted = FALSE
UNION ALL SELECT 'milestones', COUNT(*) FROM milestones
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'tasks', COUNT(*) FROM tasks
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'reports', COUNT(*) FROM reports
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'proposals', COUNT(*) FROM research_task_proposal
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'research_logs', COUNT(*) FROM research_logs
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'evaluations', COUNT(*) FROM evaluations
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'products', COUNT(*) FROM products
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'project_logs', COUNT(*) FROM project_logs
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
UNION ALL SELECT 'task_comments', COUNT(*) FROM task_comments
          WHERE task_id IN (SELECT id FROM tasks
                            WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3))
UNION ALL SELECT 'task_activity', COUNT(*) FROM research_task_activity
          WHERE task_id IN (SELECT id FROM tasks
                            WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3));

SELECT p.code AS project_code,
       (SELECT COUNT(*) FROM milestones m WHERE m.project_id = p.id AND m.active = TRUE AND m.deleted = FALSE) AS planned_milestones,
       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.active = TRUE AND t.deleted = FALSE) AS todo_tasks
FROM projects p
WHERE p.id IN (@nckh_project, @nckh_project2, @nckh_project3)
ORDER BY p.code;

SELECT g.name AS group_name, COUNT(gm.id) AS active_members,
       SUM(CASE WHEN gm.role = 'LEADER' THEN 1 ELSE 0 END) AS leaders,
       SUM(CASE WHEN gm.role = 'LEADER' AND gm.user_id = g.leader_id THEN 1 ELSE 0 END) AS matching_leaders
FROM research_groups g
LEFT JOIN group_members gm ON gm.group_id = g.id AND gm.active = TRUE AND gm.deleted = FALSE
WHERE g.id IN (@nckh_group, @nckh_group2, @nckh_group3)
GROUP BY g.id, g.name, g.leader_id;

SELECT 'invalid_topic_status' AS check_name, COUNT(*) AS failures
FROM research_topics WHERE id IN (@nckh_topic, @nckh_topic2, @nckh_topic3) AND status <> 'RECRUITING'
UNION ALL SELECT 'invalid_project_status', COUNT(*) FROM projects
          WHERE id IN (@nckh_project, @nckh_project2, @nckh_project3) AND status <> 'DRAFT'
UNION ALL SELECT 'invalid_group_status', COUNT(*) FROM research_groups
          WHERE id IN (@nckh_group, @nckh_group2, @nckh_group3) AND status <> 'ACTIVE'
UNION ALL SELECT 'invalid_milestone_status_or_progress', COUNT(*) FROM milestones
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
            AND (status <> 'NOT_STARTED' OR progress_percent <> 0 OR evidence_url IS NOT NULL)
UNION ALL SELECT 'invalid_task_status_or_progress', COUNT(*) FROM tasks
          WHERE project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
            AND (status <> 'TODO' OR progress_percent <> 0)
UNION ALL SELECT 'invalid_task_links_or_assignee', COUNT(*) FROM tasks t
          LEFT JOIN milestones m ON m.id = t.milestone_id
          LEFT JOIN group_members gm ON gm.group_id = t.group_id AND gm.user_id = t.assignee_id
                                     AND gm.active = TRUE AND gm.deleted = FALSE
          WHERE t.project_id IN (@nckh_project, @nckh_project2, @nckh_project3)
            AND (m.id IS NULL OR m.project_id <> t.project_id OR m.group_id <> t.group_id
                 OR gm.id IS NULL OR t.due_date > m.deadline)
UNION ALL SELECT 'invalid_lab_appointments', 2 - COUNT(*) FROM memberships m
          JOIN users u ON u.id = m.user_id
          WHERE m.lab_id = @nckh_lab AND m.active = TRUE AND m.deleted = FALSE
            AND ((u.username = 'nckh_member1' AND m.role = 'DEPUTY')
              OR (u.username = 'nckh_member2' AND m.role = 'SECRETARY'));
