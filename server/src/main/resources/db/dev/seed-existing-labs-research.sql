-- Manual, additive research-plan fixture for the five existing D35 demo labs.
-- Development MySQL 8 only; run with DEV_SEED_ENABLED=false. Do not run the
-- automatic D35 rollback: it deletes and recreates older demo rows.
-- All plans below are synthetic. Nothing is submitted, completed or reviewed.
-- A D35 project is eligible only while it has no research activity. Projects
-- with milestones, tasks, reports, products, evaluations or logs are untouched.
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS d35_initial_projects;
DROP TEMPORARY TABLE IF EXISTS d35_seed_members;
DROP TEMPORARY TABLE IF EXISTS d35_seed_milestones;
DROP TEMPORARY TABLE IF EXISTS d35_seed_tasks;
DROP TEMPORARY TABLE IF EXISTS d35_empty_groups;
DROP TEMPORARY TABLE IF EXISTS d35_seed_guard;

CREATE TEMPORARY TABLE d35_initial_projects (project_id BIGINT PRIMARY KEY) ENGINE=InnoDB;
CREATE TEMPORARY TABLE d35_seed_members (
    project_code VARCHAR(32) NOT NULL,
    leader_username VARCHAR(64) NOT NULL,
    member_username VARCHAR(64) NOT NULL,
    PRIMARY KEY (project_code, leader_username, member_username)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE d35_seed_milestones (
    project_code VARCHAR(32) NOT NULL,
    phase TINYINT NOT NULL,
    leader_username VARCHAR(64) NOT NULL,
    assignee_username VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    PRIMARY KEY (project_code, phase)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE d35_seed_tasks (
    project_code VARCHAR(32) NOT NULL,
    phase TINYINT NOT NULL,
    sequence_no TINYINT NOT NULL,
    assignee_username VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    PRIMARY KEY (project_code, phase, sequence_no)
) ENGINE=InnoDB;
CREATE TEMPORARY TABLE d35_empty_groups (group_id BIGINT PRIMARY KEY) ENGINE=InnoDB;
CREATE TEMPORARY TABLE d35_seed_guard (
    ok TINYINT NOT NULL,
    CONSTRAINT chk_d35_seed_guard CHECK (ok = 1)
) ENGINE=InnoDB;

INSERT INTO d35_seed_members (project_code, leader_username, member_username) VALUES
('D35-AI-FACE', 'student06', 'student06'),
('D35-AI-FACE', 'student06', 'student07'),
('D35-AI-FACE', 'student06', 'student08'),
('D35-AI-FACE', 'student06', 'student09'),
('D35-AI-FACE', 'student11', 'student11'),
('D35-AI-FACE', 'student11', 'student12'),
('D35-AI-FACE', 'student11', 'student13'),
('D35-AI-FACE', 'student11', 'student14'),
('D35-ROBOT', 'student01', 'student01'),
('D35-ROBOT', 'student01', 'student16'),
('D35-ROBOT', 'student01', 'student17'),
('D35-ROBOT', 'student01', 'student18'),
('D35-DS-ANALYTICS', 'student02', 'student02'),
('D35-DS-ANALYTICS', 'student02', 'student03'),
('D35-DS-ANALYTICS', 'student02', 'student04'),
('D35-DS-ANALYTICS', 'student02', 'student05'),
('D35-CYBER-IDS', 'student03', 'student03'),
('D35-CYBER-IDS', 'student03', 'student04'),
('D35-CYBER-IDS', 'student03', 'student05'),
('D35-CYBER-IDS', 'student03', 'student06'),
('D35-IoT-ENV', 'student04', 'student04'),
('D35-IoT-ENV', 'student04', 'student05'),
('D35-IoT-ENV', 'student04', 'student06'),
('D35-IoT-ENV', 'student04', 'student07');

-- Four planned milestones for each existing project. The AI project has two
-- groups: its final verification phase belongs to the second group.
INSERT INTO d35_seed_milestones
    (project_code, phase, leader_username, assignee_username, title, description) VALUES
('D35-AI-FACE', 1, 'student06', 'student06', 'Khảo sát phương pháp nhận diện khuôn mặt',
 'Xác định bài toán, nguồn tài liệu, phương pháp nhận diện và giới hạn dữ liệu được phép sử dụng.'),
('D35-AI-FACE', 2, 'student06', 'student07', 'Thiết kế dữ liệu và quy trình đánh giá',
 'Định nghĩa dữ liệu mẫu, cách chia tập và chỉ số đánh giá độ chính xác, thời gian xử lý.'),
('D35-AI-FACE', 3, 'student06', 'student08', 'Thiết kế nguyên mẫu nhận diện',
 'Lập cấu trúc nguyên mẫu và hợp đồng đầu vào, đầu ra để kiểm chứng phương pháp nhận diện.'),
('D35-AI-FACE', 4, 'student11', 'student11', 'Lập kế hoạch kiểm chứng và báo cáo',
 'Soạn kịch bản kiểm tra và dàn ý báo cáo; chưa thực hiện kiểm chứng hay nộp kết quả.'),
('D35-ROBOT', 1, 'student01', 'student01', 'Khảo sát yêu cầu điều hướng robot',
 'Xác định môi trường giả lập, kích thước vật cản, ràng buộc chuyển động và nguồn tài liệu.'),
('D35-ROBOT', 2, 'student01', 'student16', 'Thiết kế mô hình cơ khí và điều khiển',
 'Lập sơ đồ phần cứng, tín hiệu cảm biến và điều kiện an toàn cho nguyên mẫu.'),
('D35-ROBOT', 3, 'student01', 'student17', 'Thiết kế thuật toán tránh vật cản',
 'Mô tả luồng dữ liệu cảm biến và các tình huống cần xử lý trong bài toán điều hướng.'),
('D35-ROBOT', 4, 'student01', 'student18', 'Lập kế hoạch thử nghiệm robot',
 'Định nghĩa kịch bản mô phỏng, tiêu chí đo và dàn ý tổng kết; chưa chạy thử nghiệm.'),
('D35-DS-ANALYTICS', 1, 'student02', 'student02', 'Xác định câu hỏi nghiên cứu và nguồn dữ liệu',
 'Khoanh vùng câu hỏi phân tích, nguồn dữ liệu mẫu và điều kiện sử dụng dữ liệu.'),
('D35-DS-ANALYTICS', 2, 'student02', 'student03', 'Thiết kế quy trình làm sạch dữ liệu',
 'Đề xuất cấu trúc bảng, quy tắc xử lý bản ghi thiếu và cách ghi lại nguồn gốc.'),
('D35-DS-ANALYTICS', 3, 'student02', 'student04', 'Thiết kế mô hình phân tích và trực quan',
 'Định nghĩa biến đầu vào, cách so sánh phương pháp và bố cục bảng chỉ báo.'),
('D35-DS-ANALYTICS', 4, 'student02', 'student05', 'Lập kế hoạch đánh giá và báo cáo dữ liệu',
 'Chuẩn bị tiêu chí đánh giá, biểu mẫu kết quả và dàn ý báo cáo; chưa tính kết quả.'),
('D35-CYBER-IDS', 1, 'student03', 'student03', 'Khảo sát tín hiệu truy cập bất thường',
 'Định nghĩa phạm vi nghiên cứu, loại sự kiện và nguồn dữ liệu mạng mô phỏng.'),
('D35-CYBER-IDS', 2, 'student03', 'student04', 'Thiết kế bộ dữ liệu kiểm tra an toàn',
 'Mô tả đặc trưng, cách gắn nhãn và điều kiện dùng lưu lượng mạng mẫu được phép.'),
('D35-CYBER-IDS', 3, 'student03', 'student05', 'Thiết kế quy tắc phát hiện bất thường',
 'Đề xuất chỉ báo phát hiện và cách phân biệt cảnh báo thật, cảnh báo giả.'),
('D35-CYBER-IDS', 4, 'student03', 'student06', 'Lập kế hoạch đánh giá mô hình phát hiện',
 'Xây dựng kịch bản kiểm tra, tiêu chí precision và recall, dàn ý báo cáo; chưa chạy mô hình.'),
('D35-IoT-ENV', 1, 'student04', 'student04', 'Khảo sát thông số môi trường cần theo dõi',
 'Xác định biến đo, dải đo dự kiến, nguồn tài liệu và ràng buộc cảm biến.'),
('D35-IoT-ENV', 2, 'student04', 'student05', 'Thiết kế mô hình cảm biến và truyền dữ liệu',
 'Lập sơ đồ cảm biến, định dạng bản tin và nguyên tắc ghi nhận thời điểm đo.'),
('D35-IoT-ENV', 3, 'student04', 'student06', 'Thiết kế xử lý và hiển thị số liệu',
 'Định nghĩa kiểm tra dữ liệu thiếu, chỉ báo và màn hình biểu diễn dữ liệu mẫu.'),
('D35-IoT-ENV', 4, 'student04', 'student07', 'Lập kế hoạch hiệu chuẩn và tổng kết',
 'Đề xuất cách đối chiếu thiết bị, tiêu chí sai số và dàn ý báo cáo; chưa đo đạc thực tế.');

-- Three concrete TODO tasks under each milestone. These describe planned work,
-- not research results. Each assignee belongs to the milestone's own group.
INSERT INTO d35_seed_tasks
    (project_code, phase, sequence_no, assignee_username, title, description, priority) VALUES
('D35-AI-FACE', 1, 1, 'student06', 'Lập danh mục tài liệu về nhận diện khuôn mặt',
 'Ghi nguồn, phạm vi sử dụng và phương pháp được trình bày trong từng tài liệu.', 'HIGH'),
('D35-AI-FACE', 1, 2, 'student07', 'So sánh tiêu chí của các phương pháp nhận diện',
 'Lập bảng so sánh dữ liệu đầu vào, độ chính xác và chi phí xử lý cần đánh giá.', 'MEDIUM'),
('D35-AI-FACE', 1, 3, 'student08', 'Xác định ràng buộc quyền riêng tư dữ liệu ảnh',
 'Ghi điều kiện dùng ảnh mẫu và phương án tránh đưa ảnh cá nhân vào dữ liệu trình diễn.', 'MEDIUM'),
('D35-AI-FACE', 2, 1, 'student07', 'Thiết kế từ điển dữ liệu ảnh mẫu',
 'Định nghĩa mã bản ghi, nhãn, điều kiện ánh sáng và nguồn của ảnh được phép dùng.', 'HIGH'),
('D35-AI-FACE', 2, 2, 'student09', 'Lập quy tắc chia tập dữ liệu',
 'Mô tả cách tách dữ liệu huấn luyện và kiểm tra, tránh trùng ảnh giữa các tập.', 'MEDIUM'),
('D35-AI-FACE', 2, 3, 'student06', 'Định nghĩa chỉ số đánh giá nhận diện',
 'Chuẩn bị công thức độ chính xác, tỷ lệ nhận sai và thời gian phản hồi để đo sau này.', 'MEDIUM'),
('D35-AI-FACE', 3, 1, 'student08', 'Phác thảo luồng xử lý của nguyên mẫu',
 'Mô tả bước nhận ảnh, trích xuất đặc trưng và so khớp kết quả dự kiến.', 'HIGH'),
('D35-AI-FACE', 3, 2, 'student06', 'Định nghĩa hợp đồng dữ liệu nguyên mẫu',
 'Liệt kê trường đầu vào, đầu ra, lỗi và dữ liệu giả lập cho giao diện nghiên cứu.', 'MEDIUM'),
('D35-AI-FACE', 3, 3, 'student09', 'Chuẩn bị tình huống kiểm tra nguyên mẫu',
 'Soạn đầu vào mẫu cho trường hợp ảnh hợp lệ, ảnh mờ và không tìm được khuôn mặt.', 'MEDIUM'),
('D35-AI-FACE', 4, 1, 'student11', 'Lập kịch bản kiểm chứng nhận diện',
 'Đề xuất bộ tình huống và kết quả mong đợi; chưa ghi nhận kết quả chạy.', 'HIGH'),
('D35-AI-FACE', 4, 2, 'student12', 'Thiết kế biểu mẫu tổng hợp chỉ số',
 'Chuẩn bị bảng ghi độ chính xác và thời gian xử lý cho đợt đo sau này.', 'MEDIUM'),
('D35-AI-FACE', 4, 3, 'student13', 'Lập dàn ý báo cáo phương pháp và giới hạn',
 'Đề xuất phần phương pháp, dữ liệu, đánh giá, rủi ro và giới hạn nghiên cứu.', 'MEDIUM'),

('D35-ROBOT', 1, 1, 'student01', 'Liệt kê tình huống điều hướng cần nghiên cứu',
 'Mô tả đường thẳng, góc cua, giao lộ và vật cản trong môi trường mô phỏng.', 'HIGH'),
('D35-ROBOT', 1, 2, 'student16', 'Khảo sát nguồn tài liệu cảm biến khoảng cách',
 'Ghi đặc tính, giới hạn và điều kiện sử dụng tài liệu về Lidar, siêu âm.', 'MEDIUM'),
('D35-ROBOT', 1, 3, 'student17', 'Xác định ràng buộc an toàn chuyển động',
 'Đề xuất giới hạn tốc độ, khoảng cách dừng và tình huống mất tín hiệu.', 'MEDIUM'),
('D35-ROBOT', 2, 1, 'student16', 'Phác thảo sơ đồ khối phần cứng robot',
 'Liệt kê bộ điều khiển, cảm biến, động cơ và đường truyền tín hiệu dự kiến.', 'HIGH'),
('D35-ROBOT', 2, 2, 'student18', 'Xác định giao diện tín hiệu cảm biến',
 'Mô tả đơn vị đo, tần suất lấy mẫu và dữ liệu lỗi cần ghi nhận.', 'MEDIUM'),
('D35-ROBOT', 2, 3, 'student01', 'Lập phương án dừng khẩn và giới hạn hành trình',
 'Đề xuất điều kiện dừng và cách kiểm tra an toàn trước khi thử nghiệm.', 'MEDIUM'),
('D35-ROBOT', 3, 1, 'student17', 'Mô tả thuật toán lập đường đi dự kiến',
 'Viết giả mã nhận vị trí, xác định mục tiêu và chọn hướng di chuyển.', 'HIGH'),
('D35-ROBOT', 3, 2, 'student01', 'Định nghĩa quy tắc tránh vật cản',
 'Mô tả ngưỡng khoảng cách và hành động khi cảm biến thấy vật cản.', 'MEDIUM'),
('D35-ROBOT', 3, 3, 'student18', 'Chuẩn bị bản đồ tình huống mô phỏng',
 'Soạn các bố cục vật cản dùng để kiểm tra thuật toán sau này.', 'MEDIUM'),
('D35-ROBOT', 4, 1, 'student18', 'Thiết kế kịch bản thử nghiệm điều hướng',
 'Đề xuất các tình huống thành công, dừng khẩn và mất dữ liệu cảm biến.', 'HIGH'),
('D35-ROBOT', 4, 2, 'student16', 'Lập bảng chỉ số đánh giá chuyển động',
 'Chuẩn bị biểu mẫu quãng đường, thời gian, số lần tránh vật cản và lỗi.', 'MEDIUM'),
('D35-ROBOT', 4, 3, 'student01', 'Lập dàn ý báo cáo robot tự hành',
 'Đề xuất mục thiết kế, thuật toán, mô phỏng, đánh giá và giới hạn.', 'MEDIUM'),

('D35-DS-ANALYTICS', 1, 1, 'student02', 'Viết câu hỏi nghiên cứu dữ liệu',
 'Xác định biến cần phân tích và giả thuyết cần kiểm tra trên dữ liệu mẫu.', 'HIGH'),
('D35-DS-ANALYTICS', 1, 2, 'student03', 'Liệt kê nguồn dữ liệu được phép sử dụng',
 'Ghi nguồn, giấy phép và phạm vi dùng của dữ liệu mẫu trước khi thu thập.', 'MEDIUM'),
('D35-DS-ANALYTICS', 1, 3, 'student04', 'Xác định tiêu chí bảo vệ dữ liệu cá nhân',
 'Mô tả trường cần ẩn danh và cách loại thông tin định danh khỏi bản trình diễn.', 'MEDIUM'),
('D35-DS-ANALYTICS', 2, 1, 'student03', 'Thiết kế từ điển trường dữ liệu',
 'Định nghĩa kiểu, ý nghĩa, đơn vị đo và giá trị thiếu của từng trường.', 'HIGH'),
('D35-DS-ANALYTICS', 2, 2, 'student05', 'Đề xuất quy tắc phát hiện bản ghi bất thường',
 'Mô tả điều kiện trùng, sai định dạng và giá trị ngoài khoảng hợp lệ.', 'MEDIUM'),
('D35-DS-ANALYTICS', 2, 3, 'student02', 'Lập quy trình ghi nguồn gốc dữ liệu',
 'Đề xuất cách lưu nguồn, ngày nhận và thao tác biến đổi cho bản ghi.', 'MEDIUM'),
('D35-DS-ANALYTICS', 3, 1, 'student04', 'Chọn biến đầu vào của mô hình phân tích',
 'Liệt kê biến dự kiến, lý do chọn và nguy cơ rò rỉ thông tin.', 'HIGH'),
('D35-DS-ANALYTICS', 3, 2, 'student03', 'Thiết kế phép so sánh phương pháp phân tích',
 'Đề xuất đường cơ sở, chỉ số và cách chia dữ liệu cho đánh giá.', 'MEDIUM'),
('D35-DS-ANALYTICS', 3, 3, 'student05', 'Phác thảo bảng trực quan chỉ báo',
 'Định nghĩa biểu đồ, bộ lọc và cách hiển thị khi chưa có dữ liệu.', 'MEDIUM'),
('D35-DS-ANALYTICS', 4, 1, 'student05', 'Lập tiêu chí đánh giá mô hình dữ liệu',
 'Chuẩn bị biểu mẫu đo chất lượng và điều kiện diễn giải sai lệch.', 'HIGH'),
('D35-DS-ANALYTICS', 4, 2, 'student04', 'Chuẩn bị tình huống kiểm tra dữ liệu thiếu',
 'Soạn đầu vào và kết quả mong đợi cho dữ liệu rỗng, thiếu và trùng.', 'MEDIUM'),
('D35-DS-ANALYTICS', 4, 3, 'student02', 'Lập dàn ý báo cáo khoa học dữ liệu',
 'Đề xuất mục nguồn dữ liệu, làm sạch, phương pháp, đánh giá và giới hạn.', 'MEDIUM'),

('D35-CYBER-IDS', 1, 1, 'student03', 'Phân loại tình huống truy cập bất thường',
 'Liệt kê kiểu quét cổng và biến động lưu lượng chỉ dùng trong dữ liệu mô phỏng.', 'HIGH'),
('D35-CYBER-IDS', 1, 2, 'student04', 'Khảo sát nguồn dữ liệu mạng hợp lệ',
 'Ghi điều kiện cấp phép, trường dữ liệu và giới hạn của tập mẫu.', 'MEDIUM'),
('D35-CYBER-IDS', 1, 3, 'student05', 'Định nghĩa ranh giới nghiên cứu an toàn',
 'Mô tả phạm vi môi trường giả lập và dữ liệu không chứa thông tin mạng thật.', 'MEDIUM'),
('D35-CYBER-IDS', 2, 1, 'student04', 'Thiết kế cấu trúc bản ghi sự kiện mạng',
 'Định nghĩa thời điểm, loại sự kiện, đặc trưng và nhãn dự kiến.', 'HIGH'),
('D35-CYBER-IDS', 2, 2, 'student06', 'Đề xuất quy tắc gắn nhãn tình huống',
 'Phân biệt lưu lượng bình thường, nghi vấn và dữ liệu thiếu căn cứ.', 'MEDIUM'),
('D35-CYBER-IDS', 2, 3, 'student03', 'Lập cách chia dữ liệu kiểm tra',
 'Đề xuất tách dữ liệu theo thời gian để tránh rò rỉ giữa hai tập.', 'MEDIUM'),
('D35-CYBER-IDS', 3, 1, 'student05', 'Định nghĩa chỉ báo phát hiện bất thường',
 'Mô tả tần suất sự kiện, sai lệch và điều kiện sinh cảnh báo dự kiến.', 'HIGH'),
('D35-CYBER-IDS', 3, 2, 'student04', 'Thiết kế cách ghi lý do cảnh báo',
 'Liệt kê bằng chứng và trường dữ liệu cần hiển thị để giải thích cảnh báo.', 'MEDIUM'),
('D35-CYBER-IDS', 3, 3, 'student06', 'Chuẩn bị tình huống giảm cảnh báo giả',
 'Soạn ví dụ lưu lượng hợp lệ dễ bị nhầm và kết quả mong đợi.', 'MEDIUM'),
('D35-CYBER-IDS', 4, 1, 'student06', 'Lập kịch bản đánh giá phát hiện',
 'Định nghĩa dữ liệu đầu vào và cách đo precision, recall, false positive.', 'HIGH'),
('D35-CYBER-IDS', 4, 2, 'student05', 'Thiết kế bảng tổng hợp kết quả mô phỏng',
 'Chuẩn bị biểu mẫu phân loại cảnh báo và sai lệch, chưa có kết quả.', 'MEDIUM'),
('D35-CYBER-IDS', 4, 3, 'student03', 'Lập dàn ý báo cáo an toàn thông tin',
 'Đề xuất mục dữ liệu, mô hình phát hiện, đánh giá, rủi ro và giới hạn.', 'MEDIUM'),

('D35-IoT-ENV', 1, 1, 'student04', 'Liệt kê chỉ số môi trường dự kiến đo',
 'Xác định nhiệt độ, độ ẩm, đơn vị và giới hạn đo từ tài liệu cảm biến.', 'HIGH'),
('D35-IoT-ENV', 1, 2, 'student05', 'Khảo sát đặc tính cảm biến phù hợp',
 'Ghi độ phân giải, sai số, tần suất đọc và điều kiện vận hành.', 'MEDIUM'),
('D35-IoT-ENV', 1, 3, 'student06', 'Xác định quy tắc an toàn dữ liệu cảm biến',
 'Mô tả việc dùng dữ liệu mô phỏng và tránh gắn số liệu chưa đo là kết quả thật.', 'MEDIUM'),
('D35-IoT-ENV', 2, 1, 'student05', 'Phác thảo sơ đồ nút cảm biến',
 'Liệt kê cảm biến, bộ điều khiển, nguồn điện và liên kết tín hiệu.', 'HIGH'),
('D35-IoT-ENV', 2, 2, 'student07', 'Định nghĩa cấu trúc bản tin đo lường',
 'Mô tả mã thiết bị, thời điểm, đơn vị, giá trị và dấu hiệu dữ liệu thiếu.', 'MEDIUM'),
('D35-IoT-ENV', 2, 3, 'student04', 'Đề xuất chu kỳ lấy mẫu và gửi dữ liệu',
 'Nêu giả định về tần suất, độ trễ và tình huống mất kết nối để kiểm tra.', 'MEDIUM'),
('D35-IoT-ENV', 3, 1, 'student06', 'Thiết kế quy tắc kiểm tra dữ liệu đo',
 'Mô tả giá trị ngoài dải, bản tin trùng và mất thời điểm đo.', 'HIGH'),
('D35-IoT-ENV', 3, 2, 'student05', 'Phác thảo chỉ báo và biểu đồ môi trường',
 'Định nghĩa trục thời gian, đơn vị và cách hiển thị khi chưa có số liệu.', 'MEDIUM'),
('D35-IoT-ENV', 3, 3, 'student07', 'Chuẩn bị dữ liệu mô phỏng để kiểm tra giao diện',
 'Soạn bản ghi giả lập, đánh dấu rõ là dữ liệu kiểm tra, không là kết quả đo.', 'MEDIUM'),
('D35-IoT-ENV', 4, 1, 'student07', 'Lập phương án hiệu chuẩn cảm biến',
 'Đề xuất thiết bị tham chiếu, khoảng đo và cách ghi nhận sai số sau này.', 'HIGH'),
('D35-IoT-ENV', 4, 2, 'student06', 'Thiết kế bảng đối chiếu số liệu',
 'Chuẩn bị biểu mẫu giá trị đo, tham chiếu, độ lệch; chưa ghi kết quả.', 'MEDIUM'),
('D35-IoT-ENV', 4, 3, 'student04', 'Lập dàn ý báo cáo nghiên cứu IoT',
 'Đề xuất mục thiết kế, giao thức, hiệu chuẩn, đánh giá và giới hạn.', 'MEDIUM');

START TRANSACTION;

-- Fail before changing permanent rows when the old fixture has been replaced or
-- a project code points at the wrong lab. A rich D35 fixture still passes this
-- structural guard, but contributes no rows to d35_initial_projects below.
-- MySQL cannot reopen one temporary table in multiple subqueries of a single
-- statement, so evaluate the three planned-data joins separately.
SET @d35_seed_member_coverage := (
    SELECT COUNT(*) FROM d35_seed_members seed
    JOIN projects p ON p.code = seed.project_code
    JOIN users leader ON leader.username = seed.leader_username
    JOIN research_groups rg ON rg.project_id = p.id AND rg.leader_id = leader.id
    JOIN users member ON member.username = seed.member_username
    JOIN memberships membership ON membership.lab_id = p.lab_id
                               AND membership.user_id = member.id
                               AND membership.active = TRUE AND membership.deleted = FALSE
);
SET @d35_seed_milestone_coverage := (
    SELECT COUNT(*) FROM d35_seed_milestones milestone
    JOIN d35_seed_members member ON member.project_code = milestone.project_code
                                AND member.leader_username = milestone.leader_username
                                AND member.member_username = milestone.assignee_username
);
SET @d35_seed_task_coverage := (
    SELECT COUNT(*) FROM d35_seed_tasks task
    JOIN d35_seed_milestones milestone ON milestone.project_code = task.project_code
                                      AND milestone.phase = task.phase
    JOIN d35_seed_members member ON member.project_code = task.project_code
                                AND member.leader_username = milestone.leader_username
                                AND member.member_username = task.assignee_username
);
SET @d35_seed_milestone_count := (SELECT COUNT(*) FROM d35_seed_milestones);
SET @d35_seed_task_count := (SELECT COUNT(*) FROM d35_seed_tasks);

INSERT INTO d35_seed_guard (ok)
SELECT CASE WHEN
    (SELECT COUNT(*) FROM projects p JOIN laboratories l ON l.id = p.lab_id
     WHERE (p.code = 'D35-AI-FACE' AND l.lab_name = 'AI Research Lab')
        OR (p.code = 'D35-ROBOT' AND l.lab_name = 'Robotics Lab')
        OR (p.code = 'D35-DS-ANALYTICS' AND l.lab_name = 'Data Science Lab')
        OR (p.code = 'D35-CYBER-IDS' AND l.lab_name = 'Cybersecurity Lab')
        OR (p.code = 'D35-IoT-ENV' AND l.lab_name = 'IoT Innovation Lab')) = 5
    AND (SELECT COUNT(*) FROM research_groups rg JOIN projects p ON p.id = rg.project_id
         JOIN users leader ON leader.id = rg.leader_id
         WHERE p.code IN ('D35-AI-FACE', 'D35-ROBOT', 'D35-DS-ANALYTICS',
                          'D35-CYBER-IDS', 'D35-IoT-ENV')
           AND rg.active = TRUE AND rg.deleted = FALSE
           AND (leader.username = 'student06' AND p.code = 'D35-AI-FACE'
             OR leader.username = 'student11' AND p.code = 'D35-AI-FACE'
             OR leader.username = 'student01' AND p.code = 'D35-ROBOT'
             OR leader.username = 'student02' AND p.code = 'D35-DS-ANALYTICS'
             OR leader.username = 'student03' AND p.code = 'D35-CYBER-IDS'
             OR leader.username = 'student04' AND p.code = 'D35-IoT-ENV')) = 6
    AND @d35_seed_member_coverage = 24
    AND @d35_seed_milestone_count = 20
    AND @d35_seed_milestone_coverage = 20
    AND @d35_seed_task_count = 60
    AND @d35_seed_task_coverage = 60
    THEN 1 ELSE 0 END;

INSERT INTO d35_initial_projects (project_id)
SELECT p.id FROM projects p JOIN laboratories l ON l.id = p.lab_id
JOIN d35_seed_guard guard ON guard.ok = 1
WHERE p.active = TRUE AND p.deleted = FALSE
  AND l.active = TRUE AND l.deleted = FALSE
  AND ((p.code = 'D35-AI-FACE' AND l.lab_name = 'AI Research Lab')
    OR (p.code = 'D35-ROBOT' AND l.lab_name = 'Robotics Lab')
    OR (p.code = 'D35-DS-ANALYTICS' AND l.lab_name = 'Data Science Lab')
    OR (p.code = 'D35-CYBER-IDS' AND l.lab_name = 'Cybersecurity Lab')
    OR (p.code = 'D35-IoT-ENV' AND l.lab_name = 'IoT Innovation Lab'))
  AND NOT EXISTS (SELECT 1 FROM milestones m WHERE m.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM reports r WHERE r.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM research_task_proposal rtp WHERE rtp.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM products pr WHERE pr.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM research_logs rl WHERE rl.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM evaluations e WHERE e.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM project_logs pl WHERE pl.project_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM group_members gm JOIN research_groups rg ON rg.id = gm.group_id
                  WHERE rg.project_id = p.id)
  AND (p.topic_id IS NULL OR EXISTS (SELECT 1 FROM research_topics rt
                                    WHERE rt.id = p.topic_id AND rt.lab_id = p.lab_id
                                      AND rt.active = TRUE AND rt.deleted = FALSE))
  AND NOT EXISTS (SELECT 1 FROM research_groups rg WHERE rg.project_id = p.id
                  AND rg.topic_id IS NOT NULL
                  AND (p.topic_id IS NULL OR rg.topic_id <> p.topic_id));

-- The original D35 projects were incorrectly dated to a single day in July.
-- Correct only that exact stale metadata on eligible, activity-free demo rows.
UPDATE projects p JOIN d35_initial_projects fresh ON fresh.project_id = p.id
SET p.status = 'DRAFT', p.start_date = '2026-09-28', p.end_date = '2026-12-31',
    p.updated_at = NOW(6)
WHERE p.start_date = '2026-07-02' AND p.end_date = '2026-07-02'
  AND p.status IN ('ONGOING', 'PLANNED');

-- Give the five old projects a research-topic row, while retaining any topic
-- that may already be linked on another development database.
INSERT INTO research_topics (lab_id, name, description, requirements, reference_links,
                             manager_id, created_by, status, active, deleted, created_at, updated_at)
SELECT p.lab_id, CONCAT('Đề tài 2026: ', p.title), p.objective,
       'Dữ liệu mẫu được phép sử dụng; bốn mốc và mười hai nhiệm vụ ở trạng thái kế hoạch.',
       NULL, p.manager_id, p.manager_id, 'RECRUITING', TRUE, FALSE, NOW(6), NOW(6)
FROM projects p JOIN d35_initial_projects fresh ON fresh.project_id = p.id
WHERE p.topic_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM research_topics rt
                  WHERE rt.lab_id = p.lab_id AND rt.name = CONCAT('Đề tài 2026: ', p.title));

UPDATE projects p JOIN d35_initial_projects fresh ON fresh.project_id = p.id
JOIN research_topics rt ON rt.lab_id = p.lab_id
                       AND rt.name = CONCAT('Đề tài 2026: ', p.title)
                       AND rt.active = TRUE AND rt.deleted = FALSE
SET p.topic_id = rt.id, p.updated_at = NOW(6)
WHERE p.topic_id IS NULL;

UPDATE research_groups rg JOIN d35_initial_projects fresh ON fresh.project_id = rg.project_id
JOIN projects p ON p.id = rg.project_id
SET rg.topic_id = p.topic_id, rg.updated_at = NOW(6)
WHERE rg.topic_id IS NULL AND p.topic_id IS NOT NULL;

-- Existing group shells have zero members in the current local DB. Fill only
-- wholly empty groups; an independently populated group is never expanded.
INSERT INTO d35_empty_groups (group_id)
SELECT rg.id FROM research_groups rg
JOIN d35_initial_projects fresh ON fresh.project_id = rg.project_id
WHERE rg.active = TRUE AND rg.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM group_members gm WHERE gm.group_id = rg.id);

INSERT INTO group_members (group_id, user_id, role, joined_at, active, deleted, created_at, updated_at)
SELECT rg.id, member.id,
       CASE WHEN member.id = rg.leader_id THEN 'LEADER' ELSE 'MEMBER' END,
       NOW(6), TRUE, FALSE, NOW(6), NOW(6)
FROM d35_seed_members seed
JOIN projects p ON p.code = seed.project_code
JOIN d35_initial_projects fresh ON fresh.project_id = p.id
JOIN users leader ON leader.username = seed.leader_username
JOIN research_groups rg ON rg.project_id = p.id AND rg.leader_id = leader.id
JOIN users member ON member.username = seed.member_username
JOIN memberships membership ON membership.lab_id = p.lab_id AND membership.user_id = member.id
                           AND membership.active = TRUE AND membership.deleted = FALSE
JOIN d35_empty_groups empty_group ON empty_group.group_id = rg.id;

-- Lab appointments are scoped to a membership; group LEADER is independent.
-- Keep any existing deputy or secretary selected by a real manager.
UPDATE memberships appointment
JOIN laboratories lab ON lab.id = appointment.lab_id
JOIN users member ON member.id = appointment.user_id
JOIN projects p ON p.lab_id = lab.id
JOIN d35_initial_projects fresh ON fresh.project_id = p.id
LEFT JOIN memberships already_deputy ON already_deputy.lab_id = lab.id
    AND already_deputy.role = 'DEPUTY' AND already_deputy.active = TRUE
    AND already_deputy.deleted = FALSE
SET appointment.role = 'DEPUTY', appointment.updated_at = NOW()
WHERE appointment.role = 'MEMBER' AND appointment.active = TRUE
  AND appointment.deleted = FALSE AND already_deputy.id IS NULL
  AND ((lab.lab_name = 'AI Research Lab' AND member.username = 'student07' AND p.code = 'D35-AI-FACE')
    OR (lab.lab_name = 'Robotics Lab' AND member.username = 'student16' AND p.code = 'D35-ROBOT')
    OR (lab.lab_name = 'Data Science Lab' AND member.username = 'student03' AND p.code = 'D35-DS-ANALYTICS')
    OR (lab.lab_name = 'Cybersecurity Lab' AND member.username = 'student04' AND p.code = 'D35-CYBER-IDS')
    OR (lab.lab_name = 'IoT Innovation Lab' AND member.username = 'student05' AND p.code = 'D35-IoT-ENV'));

UPDATE memberships appointment
JOIN laboratories lab ON lab.id = appointment.lab_id
JOIN users member ON member.id = appointment.user_id
JOIN projects p ON p.lab_id = lab.id
JOIN d35_initial_projects fresh ON fresh.project_id = p.id
LEFT JOIN memberships already_secretary ON already_secretary.lab_id = lab.id
    AND already_secretary.role = 'SECRETARY' AND already_secretary.active = TRUE
    AND already_secretary.deleted = FALSE
SET appointment.role = 'SECRETARY', appointment.updated_at = NOW()
WHERE appointment.role = 'MEMBER' AND appointment.active = TRUE
  AND appointment.deleted = FALSE AND already_secretary.id IS NULL
  AND ((lab.lab_name = 'AI Research Lab' AND member.username = 'student08' AND p.code = 'D35-AI-FACE')
    OR (lab.lab_name = 'Robotics Lab' AND member.username = 'student17' AND p.code = 'D35-ROBOT')
    OR (lab.lab_name = 'Data Science Lab' AND member.username = 'student04' AND p.code = 'D35-DS-ANALYTICS')
    OR (lab.lab_name = 'Cybersecurity Lab' AND member.username = 'student05' AND p.code = 'D35-CYBER-IDS')
    OR (lab.lab_name = 'IoT Innovation Lab' AND member.username = 'student06' AND p.code = 'D35-IoT-ENV'));

INSERT INTO milestones (project_id, group_id, name, title, description,
                        start_date, end_date, deadline, status, progress_percent,
                        created_by, assigned_to_student_id, active, deleted, created_at, updated_at)
SELECT p.id, rg.id, seed.title, seed.title, seed.description,
       CASE seed.phase WHEN 1 THEN '2026-09-28' WHEN 2 THEN '2026-10-19'
                       WHEN 3 THEN '2026-11-09' ELSE '2026-11-30' END,
       CASE seed.phase WHEN 1 THEN '2026-10-16' WHEN 2 THEN '2026-11-06'
                       WHEN 3 THEN '2026-11-27' ELSE '2026-12-18' END,
       CASE seed.phase WHEN 1 THEN '2026-10-16' WHEN 2 THEN '2026-11-06'
                       WHEN 3 THEN '2026-11-27' ELSE '2026-12-18' END,
       'NOT_STARTED', 0, p.manager_id, assignee.id, TRUE, FALSE, NOW(6), NOW(6)
FROM d35_seed_milestones seed
JOIN projects p ON p.code = seed.project_code
JOIN d35_initial_projects fresh ON fresh.project_id = p.id
JOIN users leader ON leader.username = seed.leader_username
JOIN research_groups rg ON rg.project_id = p.id AND rg.leader_id = leader.id
JOIN users assignee ON assignee.username = seed.assignee_username
JOIN group_members gm ON gm.group_id = rg.id AND gm.user_id = assignee.id
                     AND gm.active = TRUE AND gm.deleted = FALSE
WHERE NOT EXISTS (SELECT 1 FROM milestones m WHERE m.project_id = p.id AND m.title = seed.title);

INSERT INTO tasks (project_id, group_id, milestone_id, assignee_id, title, description,
                   deadline, due_date, status, priority, type, progress_percent, created_by,
                   active, deleted, created_at, updated_at)
SELECT p.id, m.group_id, m.id, assignee.id, seed.title, seed.description,
       CASE seed.phase
           WHEN 1 THEN CASE seed.sequence_no WHEN 1 THEN '2026-10-05' WHEN 2 THEN '2026-10-09' ELSE '2026-10-15' END
           WHEN 2 THEN CASE seed.sequence_no WHEN 1 THEN '2026-10-26' WHEN 2 THEN '2026-11-02' ELSE '2026-11-05' END
           WHEN 3 THEN CASE seed.sequence_no WHEN 1 THEN '2026-11-16' WHEN 2 THEN '2026-11-23' ELSE '2026-11-26' END
           ELSE CASE seed.sequence_no WHEN 1 THEN '2026-12-04' WHEN 2 THEN '2026-12-11' ELSE '2026-12-17' END
       END,
       CASE seed.phase
           WHEN 1 THEN CASE seed.sequence_no WHEN 1 THEN '2026-10-05' WHEN 2 THEN '2026-10-09' ELSE '2026-10-15' END
           WHEN 2 THEN CASE seed.sequence_no WHEN 1 THEN '2026-10-26' WHEN 2 THEN '2026-11-02' ELSE '2026-11-05' END
           WHEN 3 THEN CASE seed.sequence_no WHEN 1 THEN '2026-11-16' WHEN 2 THEN '2026-11-23' ELSE '2026-11-26' END
           ELSE CASE seed.sequence_no WHEN 1 THEN '2026-12-04' WHEN 2 THEN '2026-12-11' ELSE '2026-12-17' END
       END,
       'TODO', seed.priority, 'TASK', 0, p.manager_id, TRUE, FALSE, NOW(6), NOW(6)
FROM d35_seed_tasks seed
JOIN d35_seed_milestones phase ON phase.project_code = seed.project_code
                               AND phase.phase = seed.phase
JOIN projects p ON p.code = seed.project_code
JOIN d35_initial_projects fresh ON fresh.project_id = p.id
JOIN milestones m ON m.project_id = p.id AND m.title = phase.title
                  AND m.active = TRUE AND m.deleted = FALSE
JOIN users assignee ON assignee.username = seed.assignee_username
JOIN group_members gm ON gm.group_id = m.group_id AND gm.user_id = assignee.id
                     AND gm.active = TRUE AND gm.deleted = FALSE
WHERE NOT EXISTS (SELECT 1 FROM tasks t WHERE t.milestone_id = m.id AND t.title = seed.title);

-- A failed CHECK aborts a normal mysql client before COMMIT, rolling back all
-- permanent changes. Do not use mysql --force when loading this fixture.
INSERT INTO d35_seed_guard (ok)
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM d35_initial_projects fresh JOIN projects p ON p.id = fresh.project_id
    WHERE p.topic_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM research_topics rt
                      WHERE rt.id = p.topic_id AND rt.lab_id = p.lab_id
                        AND rt.active = TRUE AND rt.deleted = FALSE)
       OR (SELECT COUNT(*) FROM milestones m WHERE m.project_id = p.id
           AND m.active = TRUE AND m.deleted = FALSE) <> 4
       OR (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id
           AND t.active = TRUE AND t.deleted = FALSE) <> 12
       OR EXISTS (SELECT 1 FROM milestones m WHERE m.project_id = p.id
                  AND (m.status <> 'NOT_STARTED' OR m.progress_percent <> 0
                    OR m.group_id IS NULL OR m.assigned_to_student_id IS NULL))
       OR EXISTS (SELECT 1 FROM tasks t WHERE t.project_id = p.id
                  AND (t.status <> 'TODO' OR t.progress_percent <> 0
                    OR t.milestone_id IS NULL OR t.group_id IS NULL OR t.assignee_id IS NULL))
       OR EXISTS (SELECT 1 FROM research_groups rg WHERE rg.project_id = p.id
                  AND (rg.topic_id IS NULL OR rg.topic_id <> p.topic_id
                    OR (SELECT COUNT(*) FROM group_members gm WHERE gm.group_id = rg.id
                        AND gm.active = TRUE AND gm.deleted = FALSE) <> 4))
       OR EXISTS (SELECT 1 FROM tasks t LEFT JOIN milestones m ON m.id = t.milestone_id
                  LEFT JOIN group_members gm ON gm.group_id = t.group_id AND gm.user_id = t.assignee_id
                                             AND gm.active = TRUE AND gm.deleted = FALSE
                  WHERE t.project_id = p.id
                    AND (m.id IS NULL OR m.project_id <> p.id OR m.group_id <> t.group_id
                      OR gm.id IS NULL))
) THEN 1 ELSE 0 END;

COMMIT;

-- Read-only summary: freshly populated projects should each show 4/12 and all
-- TODO/NOT_STARTED. A previously progressed D35 project is intentionally shown
-- with its original counts and was not modified by this script.
SELECT p.code, p.status, p.start_date, p.end_date,
       (SELECT COUNT(*) FROM research_groups rg WHERE rg.project_id = p.id AND rg.active AND NOT rg.deleted) AS groups_count,
       (SELECT COUNT(*) FROM group_members gm JOIN research_groups rg ON rg.id = gm.group_id
        WHERE rg.project_id = p.id AND gm.active AND NOT gm.deleted) AS group_members_count,
       (SELECT COUNT(*) FROM milestones m WHERE m.project_id = p.id AND m.active AND NOT m.deleted) AS milestones_count,
       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.active AND NOT t.deleted) AS tasks_count,
       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.status <> 'TODO' AND t.active AND NOT t.deleted) AS non_todo_tasks
FROM projects p
WHERE p.code IN ('D35-AI-FACE', 'D35-ROBOT', 'D35-DS-ANALYTICS', 'D35-CYBER-IDS', 'D35-IoT-ENV')
ORDER BY p.code;
