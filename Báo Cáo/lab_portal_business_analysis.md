# Phân tích Nghiệp vụ Hệ thống Lab Portal

> **Phạm vi:** Chương 2 — Chương 3 báo cáo môn Dự án Công nghệ
> **Đối tượng đọc:** Giảng viên hướng dẫn, hội đồng chấm báo cáo
> **Nguyên tắc:** Mô tả nghiệp vụ từ góc nhìn người dùng, không đi sâu vào chi tiết kỹ thuật

---

## 1. Bối cảnh Nghiệp vụ

### 1.1. Vì sao cần hệ thống Lab Portal?

Phòng thí nghiệm (PTN) là môi trường thiết yếu cho hoạt động thực hành và nghiên cứu khoa học (NCKH) trong các trường đại học. Tuy nhiên, việc quản lý PTN và tổ chức hoạt động NCKH sinh viên tại nhiều cơ sở đào tạo vẫn được thực hiện theo phương pháp thủ công hoặc bán thủ công, dẫn đến nhiều bất cập trong vận hành hàng ngày.

Hệ thống **Lab Portal** ra đời nhằm giải quyết ba nhóm vấn đề cốt lõi:

1. **Quản lý vận hành PTN:** Tập trung hóa các quy trình đăng ký sử dụng, xác nhận có mặt, phân công vệ sinh và xử lý vi phạm.
2. **Quản lý thành viên:** Chuẩn hóa luồng ứng tuyển, duyệt hồ sơ và theo dõi tư cách thành viên PTN.
3. **Quản lý hoạt động NCKH:** Số hóa toàn bộ quy trình từ lập đề tài, tổ chức nhóm nghiên cứu, phân công nhiệm vụ đến nộp — duyệt báo cáo và đánh giá kết quả.

### 1.2. Quản lý PTN thủ công gặp vấn đề gì?

| Vấn đề | Mô tả |
|--------|-------|
| **Đăng ký sử dụng rời rạc** | Sinh viên đăng ký qua biểu mẫu giấy hoặc email, quản lý không có cái nhìn tổng thể về tình trạng sử dụng từng ca. |
| **Không kiểm soát được số lượng** | Khi đông sinh viên đăng ký cùng lúc, không có cơ chế giới hạn sức chứa hoặc xếp hàng đợi. |
| **Xác nhận có mặt thủ công** | Quản lý điểm danh bằng danh sách giấy, dễ sai sót, khó truy xuất sau này. |
| **Phân công vệ sinh không minh bạch** | Không có hệ thống theo dõi ai được phân công, trạng thái hoàn thành ra sao. |
| **Vi phạm — khiếu nại không có lịch sử** | Khi xảy ra sự cố, không có hồ sơ lưu trữ vi phạm và kết quả xử lý khiếu nại. |
| **Khó thống kê báo cáo** | Muốn biết tần suất sử dụng, tỷ lệ vắng mặt, số vi phạm… phải tổng hợp thủ công. |

### 1.3. Vì sao cần quản lý NCKH theo nhóm?

Hoạt động NCKH sinh viên tại các trường đại học thường được tổ chức theo mô hình nhóm nghiên cứu, trong đó:

- Một **đề tài nghiên cứu** có thể được chia cho nhiều nhóm tiếp cận theo các hướng khác nhau.
- Mỗi nhóm có **trưởng nhóm (Leader)** phụ trách điều phối và **thành viên (Member)** thực hiện các nhiệm vụ cụ thể.
- Quá trình nghiên cứu diễn ra theo các **mốc tiến độ (milestone)** với nhiều **nhiệm vụ (task)** được phân công cho từng cá nhân.
- Kết quả công việc được ghi nhận qua **báo cáo tiến độ** — trải qua quy trình duyệt hai cấp trước khi được công nhận.
- Cuối cùng, giảng viên phụ trách **đánh giá từng sinh viên** theo nhiều tiêu chí.

Nếu không có hệ thống, toàn bộ quy trình trên phải quản lý bằng file Excel, email hoặc tin nhắn nhóm — dẫn đến:
- **Mất dữ liệu** khi file bị xóa hoặc ghi đè.
- **Thiếu minh bạch** trong phân công và đánh giá.
- **Không có lịch sử phiên bản** khi báo cáo được yêu cầu chỉnh sửa nhiều lần.
- **Giảng viên tốn nhiều thời gian** tổng hợp kết quả đánh giá.

---

## 2. Tác nhân Hệ thống

Hệ thống Lab Portal phục vụ ba nhóm tác nhân chính. Trong đó, tác nhân **Sinh viên (STUDENT)** được phân thêm thành hai vai trò trong bối cảnh nhóm nghiên cứu.

| Tác nhân | Mô tả | Trách nhiệm chính |
|----------|-------|--------------------|
| **Quản trị viên (ADMIN)** | Người quản lý cấp cao nhất của hệ thống. Thường là quản trị viên CNTT hoặc phòng đào tạo. | Quản lý tài khoản người dùng; tạo và quản lý PTN; gán quản lý cho từng PTN; cấu hình hệ thống; giám sát nhật ký hoạt động. |
| **Quản lý PTN (LAB_MANAGER)** | Giảng viên hoặc nhân viên được gán phụ trách một PTN cụ thể. | Vận hành PTN hàng ngày: duyệt ứng tuyển, tạo khung giờ, duyệt đăng ký sử dụng, xác nhận có mặt, phân công vệ sinh, xử lý vi phạm, tổ chức và giám sát hoạt động NCKH. |
| **Sinh viên — Thành viên (STUDENT · MEMBER)** | Sinh viên tham gia sử dụng PTN và là thành viên thông thường trong nhóm nghiên cứu. | Đăng ký sử dụng PTN, check-in, thực hiện nhiệm vụ NCKH được giao, nộp báo cáo tiến độ, nộp sản phẩm nghiên cứu. |
| **Sinh viên — Trưởng nhóm (STUDENT · LEADER)** | Sinh viên được chỉ định làm trưởng nhóm trong một nhóm nghiên cứu cụ thể. | Kế thừa toàn bộ quyền của Member; thêm khả năng theo dõi tiến độ toàn nhóm, duyệt báo cáo cấp nhóm (cấp 1), quản lý sản phẩm và nhật ký nhóm. |

> **Lưu ý quan trọng:** Vai trò **LEADER** và **MEMBER** không phải là role cấp hệ thống — chúng là vai trò **trong phạm vi nhóm nghiên cứu cụ thể**. Một sinh viên có thể là Leader ở nhóm A nhưng là Member ở nhóm B.

---

## 3. Nghiệp vụ ADMIN

Quản trị viên (ADMIN) là tác nhân duy nhất có toàn quyền quản lý ở cấp hệ thống. Khi đăng nhập, ADMIN được chuyển đến giao diện riêng (Admin Portal) với các chức năng sau:

### 3.1. Quản lý Người dùng

ADMIN xem danh sách toàn bộ tài khoản người dùng trong hệ thống (trừ tài khoản ADMIN khác). Với mỗi tài khoản, ADMIN có thể:

- **Xem thông tin chi tiết:** Họ tên, email, vai trò hiện tại, trạng thái (hoạt động/bị khóa).
- **Khóa tài khoản (Ban):** Vô hiệu hóa tài khoản khi phát hiện vi phạm nghiêm trọng. Người dùng bị khóa không thể đăng nhập.
- **Mở khóa tài khoản (Unban):** Khôi phục quyền đăng nhập cho tài khoản đã bị khóa.

### 3.2. Đổi Vai trò Người dùng

ADMIN có thể thay đổi vai trò của một tài khoản giữa **STUDENT** và **LAB_MANAGER**. Nghiệp vụ này phục vụ hai tình huống:

- **Nâng cấp sinh viên thành Quản lý PTN:** Khi một giảng viên hoặc trợ giảng cần được gán vai trò quản lý.
- **Hạ vai trò:** Khi một Quản lý PTN không còn phụ trách PTN nào.

### 3.3. Gán Quản lý cho PTN

Sau khi tạo PTN, ADMIN cần gán một người dùng có vai trò LAB_MANAGER để phụ trách vận hành. Hệ thống cung cấp danh sách các tài khoản LAB_MANAGER đủ điều kiện (chưa quản lý PTN nào hoặc có thể thay thế). ADMIN chọn người phụ trách và xác nhận gán.

### 3.4. Quản lý Phòng thí nghiệm

ADMIN thực hiện các thao tác vòng đời của PTN:

| Thao tác | Mô tả |
|----------|-------|
| **Tạo PTN** | Nhập tên, vị trí, sức chứa, mô tả. Trạng thái mặc định: **Sẵn sàng (AVAILABLE)**. |
| **Cập nhật trạng thái** | Chuyển đổi giữa: **Sẵn sàng** → **Đang bảo trì (UNDER_MAINTENANCE)** → **Đóng cửa (CLOSED)**. |
| **Gán Manager** | Liên kết PTN với một LAB_MANAGER cụ thể. |

### 3.5. Cấu hình Hệ thống (System Config)

ADMIN quản lý các tham số cấu hình toàn hệ thống theo mô hình **key–value**. Ví dụ:
- Mức phạt mặc định khi vắng mặt không phép.
- Thời gian gia hạn trước khi đánh dấu vắng (grace period).
- Các tham số tùy chỉnh khác.

Mọi thay đổi cấu hình đều được ghi nhật ký thay đổi (audit trail) với giá trị cũ, giá trị mới và người thực hiện.

### 3.6. Nhật ký Kiểm toán (Audit Logs)

ADMIN có thể tra cứu nhật ký kiểm toán hệ thống, ghi lại các hoạt động quan trọng như:
- Thay đổi vai trò người dùng.
- Khóa/mở khóa tài khoản.
- Tạo/cập nhật PTN.
- Thay đổi cấu hình hệ thống.

Mỗi bản ghi bao gồm: người thực hiện, hành động, module liên quan, chi tiết và thời điểm.

### 3.7. Dashboard Quản trị

ADMIN xem tổng quan toàn hệ thống qua dashboard thống kê, bao gồm:
- Tổng số người dùng (phân theo vai trò).
- Tổng số PTN (phân theo trạng thái).
- Các chỉ số hoạt động chung.

---

## 4. Nghiệp vụ LAB_MANAGER

Quản lý PTN (LAB_MANAGER) là tác nhân trung tâm trong hoạt động vận hành hàng ngày. Mỗi LAB_MANAGER được gán phụ trách **đúng một PTN** và chỉ có quyền thao tác trên PTN đó.

> **Điều kiện tiên quyết:** LAB_MANAGER phải được ADMIN gán cho một PTN trước khi có thể sử dụng các chức năng bên dưới. Nếu chưa được gán, hệ thống hiển thị thông báo *"Bạn chưa được phân công quản lý phòng thí nghiệm nào"*.

### 4.1. Duyệt Hồ sơ Ứng tuyển

Khi sinh viên nộp đơn ứng tuyển vào PTN, hồ sơ được gửi đến LAB_MANAGER ở trạng thái **Chờ duyệt (PENDING)**. LAB_MANAGER xem xét thông tin sinh viên và CV đính kèm (file hoặc liên kết), sau đó:

- **Duyệt (APPROVED):** Hệ thống tự động tạo tư cách thành viên (Membership) cho sinh viên trong PTN. Sinh viên được truy cập các tính năng đầy đủ.
- **Từ chối (REJECTED):** Sinh viên được phép nộp lại đơn mới sau khi bị từ chối.

Danh sách hồ sơ hỗ trợ phân trang và sắp xếp theo thời gian nộp.

### 4.2. Quản lý Thành viên PTN

LAB_MANAGER xem danh sách thành viên đang hoạt động trong PTN mình quản lý, bao gồm: họ tên, email, vai trò, trạng thái và ngày gia nhập.

- **Xóa thành viên:** LAB_MANAGER có thể loại bỏ một thành viên khỏi PTN (vô hiệu hóa tư cách thành viên). Thao tác này không xóa tài khoản người dùng.
- **Ràng buộc:** LAB_MANAGER không thể tự loại bỏ chính mình.

### 4.3. Tạo Khung giờ Sử dụng (Time Slot)

LAB_MANAGER tạo các khung giờ sử dụng PTN, mỗi khung giờ bao gồm:

| Thông tin | Mô tả |
|-----------|-------|
| Ngày | Ngày diễn ra ca sử dụng |
| Giờ bắt đầu — kết thúc | Khoảng thời gian cụ thể |
| Sức chứa (capacity) | Số lượng sinh viên tối đa được đăng ký |

Khung giờ được tạo ở trạng thái **Mở đăng ký (AVAILABLE)** và tự động chuyển trạng thái khi đủ người hoặc khi đã qua thời gian.

### 4.4. Duyệt Đăng ký Sử dụng (Booking Review)

Khi sinh viên đăng ký sử dụng một khung giờ, yêu cầu được gửi ở trạng thái **Chờ duyệt (PENDING_APPROVAL)**. LAB_MANAGER xem danh sách đăng ký theo từng khung giờ và thực hiện:

- **Duyệt (APPROVED):** Sinh viên được xác nhận quyền sử dụng PTN trong khung giờ đó.
- **Từ chối (REJECTED):** Sinh viên nhận thông báo và có thể đăng ký khung giờ khác.

### 4.5. Hủy Ca Sử dụng

Trong trường hợp cần hủy khung giờ (bảo trì PTN, sự cố…), LAB_MANAGER có thể:
- **Hủy khung giờ:** Tất cả đăng ký thuộc khung giờ đó bị hủy theo, trạng thái chuyển thành CANCELLED_BY_MANAGER. Hệ thống gửi email thông báo đến các sinh viên bị ảnh hưởng.

### 4.6. Xác nhận Có mặt (Check-in)

Luồng xác nhận có mặt sử dụng cơ chế **QR code hai chiều**:

1. Sinh viên có đăng ký đã được duyệt → tạo mã QR trên thiết bị cá nhân.
2. LAB_MANAGER quét mã QR bằng chức năng "Xác nhận có mặt" trên hệ thống.
3. Hệ thống xác minh tính hợp lệ (đúng sinh viên, đúng khung giờ, trong thời gian cho phép) và cập nhật trạng thái đăng ký thành **Đã check-in (CHECKED_IN)**.

### 4.7. Phân công Vệ sinh PTN

Sau khi một ca sử dụng kết thúc, LAB_MANAGER có thể phân công nhiệm vụ vệ sinh:

1. Chọn khung giờ đã kết thúc.
2. Xem danh sách sinh viên đủ điều kiện (đã check-in trong ca đó).
3. Chọn một hoặc nhiều sinh viên và tạo nhiệm vụ vệ sinh.
4. Nhiệm vụ ở trạng thái **Chờ thực hiện (PENDING)** cho đến khi sinh viên xác nhận hoàn thành.
5. LAB_MANAGER có thể hủy nhiệm vụ vệ sinh nếu cần.

### 4.8. Tạo Vi phạm (Penalty)

LAB_MANAGER ghi nhận vi phạm cho sinh viên trong phạm vi PTN mình quản lý. Mỗi vi phạm bao gồm:

| Thông tin | Mô tả |
|-----------|-------|
| Sinh viên vi phạm | Chọn từ danh sách thành viên |
| Khung giờ liên quan | Ca sử dụng phát sinh vi phạm |
| Loại vi phạm | Vắng mặt, trễ check-in, làm hư thiết bị, gây ồn, hoặc khác |
| Điểm phạt | Mức độ nghiêm trọng |
| Lý do chi tiết | Mô tả cụ thể tình huống vi phạm |

### 4.9. Xử lý Khiếu nại (Complaint)

Khi sinh viên gửi khiếu nại về một vi phạm, LAB_MANAGER xem danh sách khiếu nại thuộc PTN mình quản lý, xem xét nội dung và đưa ra quyết định:

- **Chấp nhận (RESOLVED):** Ghi nhận biện pháp giải quyết. Vi phạm có thể được gỡ bỏ hoặc giảm mức phạt.
- **Từ chối (REJECTED):** Giữ nguyên vi phạm, ghi lý do từ chối.

### 4.10. Quản lý Nghiên cứu Khoa học (NCKH)

Đây là nhóm chức năng phức tạp nhất của LAB_MANAGER, bao gồm nhiều bước tuần tự:

#### 4.10.1. Tạo Đề tài (Research Topic)

LAB_MANAGER tạo đề tài nghiên cứu thuộc PTN, bao gồm: tên đề tài, mô tả chi tiết, yêu cầu, tài liệu tham khảo. Mỗi đề tài có trạng thái:
- **Đang tuyển (RECRUITING):** Đề tài đang tìm nhóm tham gia.
- **Đang hoạt động (ACTIVE):** Đề tài đã có nhóm và đang triển khai.
- **Đã đóng (CLOSED):** Đề tài kết thúc.

#### 4.10.2. Tạo Nhóm Nghiên cứu (Research Group)

LAB_MANAGER tạo nhóm nghiên cứu gắn với đề tài hoặc dự án. Khi tạo nhóm, LAB_MANAGER:
- Đặt tên nhóm.
- Chọn sinh viên từ danh sách thành viên PTN để thêm vào nhóm.
- Chỉ định một sinh viên làm **Leader**.
- Các sinh viên còn lại là **Member**.

#### 4.10.3. Tạo Dự án Nghiên cứu (Research Project)

Mỗi nhóm hoặc mỗi đề tài có thể có một hoặc nhiều dự án nghiên cứu. LAB_MANAGER tạo dự án bao gồm: mã dự án, tiêu đề, hướng nghiên cứu, mục tiêu, mô tả, ngày bắt đầu/kết thúc, độ ưu tiên, yêu cầu sản phẩm đầu ra và tiêu chí đánh giá.

Trạng thái dự án: **Nháp (DRAFT)** → **Đang thực hiện (IN_PROGRESS)** → **Hoàn thành (COMPLETED)** hoặc **Hủy (CANCELLED)**.

#### 4.10.4. Tạo Mốc Tiến độ (Milestone)

LAB_MANAGER chia dự án thành các mốc tiến độ theo thời gian:
- Tiêu đề và mô tả mốc.
- Ngày bắt đầu và hạn chót (deadline).
- Gán cho một nhóm cụ thể.

Trạng thái mốc: **Chưa bắt đầu (NOT_STARTED)** → **Đang thực hiện (IN_PROGRESS)** → **Chờ duyệt (WAITING_REVIEW)** → **Hoàn thành (COMPLETED)** hoặc **Quá hạn (OVERDUE)**.

LAB_MANAGER có thể cập nhật tiến độ (phần trăm hoàn thành) và ghi nhận xét.

#### 4.10.5. Giao Nhiệm vụ (Task)

Trong mỗi mốc, LAB_MANAGER tạo các nhiệm vụ cụ thể và gán cho từng sinh viên trong nhóm:
- Tiêu đề, mô tả nhiệm vụ.
- Người thực hiện (assignee — chọn từ thành viên nhóm).
- Hạn chót.

Trạng thái nhiệm vụ theo mô hình **Kanban**: **Cần làm (TODO)** → **Đang làm (DOING)** → **Chờ duyệt (WAITING_REVIEW)** → **Cần sửa (NEEDS_REVISION)** → **Hoàn thành (DONE)**.

#### 4.10.6. Duyệt Báo cáo cấp Manager (Manager Review)

Sau khi Leader đã duyệt báo cáo ở cấp nhóm, báo cáo chuyển lên LAB_MANAGER. Đây là **cấp duyệt thứ hai và cuối cùng**:

- **Duyệt (APPROVED):** Báo cáo được chấp nhận chính thức.
- **Từ chối (MANAGER_REJECTED):** Báo cáo bị từ chối, sinh viên cần nộp phiên bản mới.

#### 4.10.7. Đánh giá Sinh viên (Evaluation)

Khi dự án tiến triển hoặc kết thúc, LAB_MANAGER đánh giá từng sinh viên theo **năm tiêu chí**:

| Tiêu chí | Ý nghĩa |
|----------|---------|
| **Mức đóng góp (Contribution)** | Đóng góp tổng thể cho nhóm và dự án |
| **Hoàn thành nhiệm vụ (Task)** | Chất lượng và tiến độ thực hiện task |
| **Chất lượng báo cáo (Report)** | Nội dung, hình thức, tính chính xác của báo cáo |
| **Sản phẩm (Product)** | Chất lượng sản phẩm nghiên cứu đầu ra |
| **Thái độ (Attitude)** | Tính chủ động, tinh thần hợp tác, kỷ luật |

Mỗi tiêu chí được chấm điểm số (thang BigDecimal), hệ thống tính **tổng điểm** và cho phép LAB_MANAGER ghi nhận xét tổng hợp.

#### 4.10.8. Theo dõi Dashboard PTN và NCKH

LAB_MANAGER xem tổng quan hoạt động PTN qua dashboard:
- Số thành viên, số đơn ứng tuyển chờ duyệt.
- Thống kê sử dụng khung giờ.
- Tổng quan dự án NCKH: số nhóm, tiến độ milestone, task, báo cáo chờ duyệt.

---

## 5. Nghiệp vụ STUDENT — MEMBER

Sinh viên ở vai trò **MEMBER** là tác nhân sử dụng chính của hệ thống. Nghiệp vụ của Member được chia thành hai giai đoạn: **trước** và **sau** khi được nhận vào PTN.

### 5.1. Đăng ký Tài khoản và Xác thực Email

Quy trình đăng ký tài khoản diễn ra qua **ba bước**:

| Bước | Hành động | Kết quả |
|------|-----------|---------|
| **Bước 1** | Sinh viên nhập email | Hệ thống gửi mã xác thực (OTP) qua email. Mã có thời hạn sử dụng và được mã hóa trước khi lưu trữ. |
| **Bước 2** | Sinh viên nhập mã OTP | Hệ thống xác minh và cấp mã tạm thời (temporary token). |
| **Bước 3** | Sinh viên điền thông tin (tên đăng nhập, mật khẩu, họ tên, số điện thoại) kèm mã tạm thời | Hệ thống tạo tài khoản với vai trò STUDENT. |

### 5.2. Ứng tuyển vào PTN

Sau khi có tài khoản, sinh viên xem danh sách PTN đang hoạt động và nộp đơn ứng tuyển:

- **Đính kèm CV:** Upload file trực tiếp hoặc cung cấp đường dẫn (URL).
- **Trạng thái:** Đơn ứng tuyển ở trạng thái **Chờ duyệt** cho đến khi LAB_MANAGER xử lý.
- **Nộp lại:** Nếu bị từ chối, sinh viên được phép nộp đơn mới.

> **Ràng buộc:** Sinh viên không thể nộp hai đơn cùng lúc cho cùng một PTN (đơn trùng lặp bị từ chối).

### 5.3. Đăng ký Sử dụng PTN (Booking)

Sau khi được nhận vào PTN (membership active), sinh viên có quyền:

1. **Xem khung giờ:** Danh sách các khung giờ sử dụng của PTN mình thuộc về.
2. **Đăng ký:** Chọn khung giờ còn chỗ trống, tạo đăng ký. Nếu khung giờ đã đầy, sinh viên tự động được thêm vào **hàng đợi (Waitlist)** theo thứ tự.
3. **Hủy đăng ký:** Sinh viên có thể hủy đăng ký chưa diễn ra.
4. **Xem lịch sử:** Toàn bộ lịch sử đăng ký (đã duyệt, đã check-in, đã hoàn thành, đã hủy…).

### 5.4. Check-in bằng QR Code

Khi đến ca sử dụng PTN:

1. Sinh viên mở hệ thống, chọn đăng ký đã được duyệt.
2. Nhấn **"Tạo mã QR"** — hệ thống tạo mã QR có thời hạn ngắn.
3. Đưa mã QR cho LAB_MANAGER quét.
4. Trạng thái đăng ký chuyển thành **Đã check-in (CHECKED_IN)**.

### 5.5. Xem Vệ sinh và Vi phạm

- **Nhiệm vụ vệ sinh:** Sinh viên xem danh sách nhiệm vụ vệ sinh được phân công, xác nhận hoàn thành khi đã thực hiện.
- **Lịch sử vi phạm:** Xem các vi phạm bị ghi nhận kèm loại, điểm phạt, lý do.
- **Gửi khiếu nại:** Nếu không đồng ý với vi phạm, sinh viên nộp khiếu nại mô tả lý do. Khiếu nại được liên kết trực tiếp với vi phạm tương ứng.

### 5.6. Tham gia Nhóm Nghiên cứu

Sinh viên không tự tạo nhóm mà được **LAB_MANAGER thêm vào nhóm** với vai trò Member. Sau khi tham gia, sinh viên:

- Xem thông tin nhóm: tên, đề tài, dự án liên quan, danh sách thành viên.
- Truy cập các chức năng NCKH trong phạm vi nhóm.

### 5.7. Xem Mốc Tiến độ của Tôi

Member xem danh sách các mốc tiến độ thuộc nhóm mình tham gia. Với mỗi mốc, sinh viên thấy:
- Tiêu đề, mô tả, deadline.
- Tiến độ hiện tại (phần trăm).
- Trạng thái (chưa bắt đầu, đang thực hiện, hoàn thành…).

### 5.8. Xem Nhiệm vụ của Tôi

Member xem các nhiệm vụ **được giao riêng cho mình** trong nhóm. Hệ thống hiển thị dạng bảng (task board) với các cột trạng thái: Cần làm → Đang làm → Chờ duyệt → Hoàn thành.

> **Ràng buộc:** Member chỉ thấy nhiệm vụ giao cho mình, không thấy nhiệm vụ của thành viên khác (trừ khi là Leader).

### 5.9. Bắt đầu Nhiệm vụ

Khi sẵn sàng thực hiện, Member cập nhật trạng thái nhiệm vụ từ **TODO → DOING**, đánh dấu bắt đầu công việc.

### 5.10. Nộp Báo cáo Tiến độ

Khi hoàn thành (hoặc đạt tiến độ đáng kể) một nhiệm vụ, Member nộp báo cáo bao gồm:

| Thông tin | Mô tả |
|-----------|-------|
| Nội dung đã thực hiện | Mô tả công việc đã hoàn thành |
| Kết quả đạt được | Sản phẩm, phát hiện, kết luận |
| Khó khăn gặp phải | Vấn đề trong quá trình thực hiện |
| Kế hoạch tiếp theo | Dự kiến công việc giai đoạn sau |
| Tự đánh giá | Nhận xét cá nhân về tiến độ |
| File đính kèm | Upload tài liệu, bằng chứng (bắt buộc) |
| Liên kết bằng chứng | URL đến tài liệu bổ sung (tùy chọn) |

Báo cáo được gán **phiên bản (version)** tự động (v1, v2, v3…) và ở trạng thái **Đã nộp (SUBMITTED)**.

### 5.11. Cập nhật / Nộp lại Báo cáo

Khi báo cáo bị yêu cầu chỉnh sửa (NEEDS_REVISION) hoặc bị từ chối, Member có thể:
- **Thay thế báo cáo hiện tại:** Cập nhật nội dung và file đính kèm (giữ nguyên version).
- **Nộp phiên bản mới:** Tạo báo cáo mới với version tăng lên.

### 5.12. Xem Góp ý (Comments)

Member xem các bình luận và nhận xét trên báo cáo của mình — bao gồm góp ý từ Leader và LAB_MANAGER.

### 5.13. Xem Đánh giá Cá nhân

Khi LAB_MANAGER đã đánh giá, Member xem kết quả đánh giá cá nhân gồm: điểm từng tiêu chí, tổng điểm và nhận xét của giảng viên.

---

## 6. Nghiệp vụ STUDENT — LEADER

> **Nguyên tắc quan trọng:** LEADER = MEMBER + quyền quản lý nhóm.
>
> Trưởng nhóm kế thừa **toàn bộ** nghiệp vụ của Member (mục 5) và có thêm các quyền mở rộng trong phạm vi nhóm mình dẫn dắt.

### 6.1. Kế thừa Nghiệp vụ Member

Leader thực hiện đầy đủ mọi thao tác như một Member thông thường: đăng ký PTN, check-in, nhận nhiệm vụ, nộp báo cáo, xem đánh giá cá nhân…

### 6.2. Xem Mốc Tiến độ của Nhóm

Khác với Member chỉ thấy mốc liên quan đến mình, Leader xem **toàn bộ mốc tiến độ** của nhóm — bao gồm tổng quan tiến độ, trạng thái và thời hạn.

### 6.3. Xem Bảng Tiến độ Nhóm (Task Board)

Leader xem bảng nhiệm vụ (task board) **của tất cả thành viên trong nhóm**, không giới hạn ở nhiệm vụ của bản thân. Điều này giúp Leader:
- Nắm bắt tiến độ tổng thể.
- Phát hiện nhiệm vụ bị trễ hoặc gặp khó khăn.
- Đánh giá sự phân bổ công việc.

### 6.4. Xem Báo cáo Toàn Nhóm

Leader xem **toàn bộ báo cáo** do các thành viên nộp — bao gồm nội dung, file đính kèm, lịch sử phiên bản và trạng thái hiện tại.

### 6.5. Duyệt Báo cáo cấp Nhóm (Leader Review)

Đây là quyền đặc trưng nhất của Leader — thực hiện **cấp duyệt thứ nhất** cho báo cáo:

| Quyết định | Trạng thái mới | Hệ quả |
|------------|----------------|--------|
| **Chấp nhận** | LEADER_REVIEWED | Báo cáo chuyển lên LAB_MANAGER để duyệt cấp 2 |
| **Yêu cầu chỉnh sửa** | NEEDS_REVISION | Báo cáo trả về cho Member sửa và nộp lại |
| **Từ chối** | LEADER_REJECTED | Báo cáo bị bác bỏ, Member cần nộp phiên bản mới |

Leader ghi nhận xét khi duyệt, giúp Member hiểu lý do chấp nhận hoặc cần sửa.

> **Ràng buộc quan trọng:** Leader **không tự duyệt báo cáo của chính mình**. Khi Leader nộp báo cáo, quá trình review sẽ bỏ qua bước Leader Review và đẩy thẳng lên LAB_MANAGER duyệt.

### 6.6. Theo dõi Sản phẩm Nhóm

Leader xem danh sách sản phẩm nghiên cứu (source code, slide, paper, dataset…) do các thành viên nộp — bao gồm loại sản phẩm, phiên bản, trạng thái và liên kết tải xuống.

### 6.7. Theo dõi Nhật ký Nghiên cứu Nhóm

Leader xem nhật ký nghiên cứu (research log) của tất cả thành viên trong nhóm — ghi nhận hoạt động hàng ngày: ngày làm việc, thời lượng, nội dung, kết quả, vấn đề gặp phải và kế hoạch tiếp theo.

---

## 7. Các Ràng buộc Nghiệp vụ

Dưới đây là các quy tắc nghiệp vụ quan trọng mà hệ thống phải tuân thủ:

### 7.1. Ràng buộc về PTN và Người dùng

| # | Ràng buộc | Giải thích |
|---|-----------|-----------|
| RB-01 | Mỗi LAB_MANAGER quản lý **đúng một PTN** | Đảm bảo trách nhiệm rõ ràng, tránh xung đột quản lý. |
| RB-02 | Mỗi PTN có **một Manager chính** | Admin gán qua chức năng "Gán Manager". Có thể thay thế Manager. |
| RB-03 | Sinh viên có thể **tham gia nhiều PTN** | Nộp đơn ứng tuyển vào nhiều PTN khác nhau, mỗi PTN cần duyệt độc lập. |
| RB-04 | Không nộp đơn trùng lặp cho cùng PTN | Nếu đã có đơn PENDING hoặc APPROVED, không thể nộp thêm. Chỉ nộp lại sau khi bị REJECTED. |
| RB-05 | LAB_MANAGER chỉ thao tác trên PTN mình quản lý | Mọi API endpoint của Manager đều kiểm tra quyền sở hữu PTN. |

### 7.2. Ràng buộc về Đăng ký Sử dụng

| # | Ràng buộc | Giải thích |
|---|-----------|-----------|
| RB-06 | Chỉ thành viên PTN (membership active) mới đăng ký sử dụng | Sinh viên chưa được duyệt không thấy chức năng booking. |
| RB-07 | Không đăng ký trùng cùng khung giờ | Hệ thống từ chối nếu sinh viên đã có đăng ký cho khung giờ đó. |
| RB-08 | Khi khung giờ đầy → xếp hàng đợi | Hệ thống tự động thêm vào Waitlist với thứ tự (position). |
| RB-09 | Waitlist sử dụng khóa bi quan (pessimistic locking) | Đảm bảo thứ tự chính xác khi nhiều sinh viên đăng ký đồng thời. |
| RB-10 | Hủy khung giờ → thông báo toàn bộ sinh viên | Email tự động gửi đến tất cả sinh viên có đăng ký bị ảnh hưởng. |

### 7.3. Ràng buộc về NCKH

| # | Ràng buộc | Giải thích |
|---|-----------|-----------|
| RB-11 | Chỉ sinh viên có membership active mới truy cập tính năng NCKH | Đảm bảo chỉ thành viên chính thức tham gia nghiên cứu. |
| RB-12 | Mỗi đề tài có thể có **nhiều nhóm** | Một đề tài lớn chia thành nhiều hướng tiếp cận khác nhau. |
| RB-13 | Mọi nghiệp vụ NCKH chi tiết hoạt động **theo nhóm (group context)** | Milestone, task, report, product, evaluation, log đều gắn với group. |
| RB-14 | Member chỉ thấy nhiệm vụ và báo cáo **của mình** | Đảm bảo tính riêng tư, tránh so sánh không cần thiết. |
| RB-15 | Leader thấy toàn bộ dữ liệu **trong nhóm mình dẫn dắt** | Quyền mở rộng phục vụ vai trò điều phối. |
| RB-16 | Leader **không tự duyệt báo cáo của chính mình** | Báo cáo của Leader chuyển thẳng lên Manager duyệt, đảm bảo khách quan. |
| RB-17 | Báo cáo hỗ trợ **nhiều phiên bản (version)** | Mỗi lần nộp lại tạo version mới; ràng buộc unique trên (task_id, submitted_by_id, version). |
| RB-18 | Chỉ phiên bản mới nhất được xử lý (review) | Các phiên bản cũ được giữ lại làm lịch sử, không thể review lại. |
| RB-19 | Dữ liệu không bị xóa vật lý | Toàn hệ thống sử dụng soft-delete (đánh dấu xóa mềm), đảm bảo truy xuất lịch sử. |

---

## 8. Luồng Nghiệp vụ Tổng quát

Dưới đây mô tả luồng nghiệp vụ tổng thể của hệ thống Lab Portal, thể hiện sự tương tác giữa các tác nhân theo trình tự thời gian:

### Giai đoạn 1: Thiết lập hệ thống (ADMIN)

1. ADMIN đăng nhập hệ thống với tài khoản quản trị được tạo sẵn.
2. ADMIN tạo PTN mới trong hệ thống, nhập đầy đủ thông tin (tên, vị trí, sức chứa).
3. ADMIN tạo tài khoản LAB_MANAGER hoặc nâng vai trò từ STUDENT lên LAB_MANAGER.
4. ADMIN gán LAB_MANAGER vào PTN vừa tạo.
5. PTN sẵn sàng vận hành.

### Giai đoạn 2: Sinh viên gia nhập PTN

6. Sinh viên đăng ký tài khoản: nhập email → nhận OTP → xác minh → điền thông tin → tạo tài khoản STUDENT.
7. Sinh viên đăng nhập, xem danh sách PTN đang hoạt động.
8. Sinh viên nộp đơn ứng tuyển vào PTN mong muốn (kèm CV file hoặc URL).
9. LAB_MANAGER nhận thông báo có đơn mới, xem xét hồ sơ.
10. LAB_MANAGER duyệt đơn → hệ thống tạo Membership cho sinh viên.
11. Sinh viên truy cập đầy đủ tính năng PTN.

### Giai đoạn 3: Sử dụng PTN hàng ngày

12. LAB_MANAGER tạo khung giờ sử dụng cho PTN (ngày, giờ, sức chứa).
13. Sinh viên đăng ký sử dụng khung giờ phù hợp.
14. LAB_MANAGER duyệt đăng ký.
15. Đến giờ sử dụng: sinh viên tạo QR code → LAB_MANAGER quét xác nhận check-in.
16. Kết thúc ca: LAB_MANAGER phân công vệ sinh cho sinh viên tham gia.
17. Sinh viên hoàn thành vệ sinh → xác nhận trên hệ thống.
18. Nếu có vi phạm: LAB_MANAGER ghi nhận → sinh viên khiếu nại nếu cần → LAB_MANAGER xử lý.

### Giai đoạn 4: Hoạt động Nghiên cứu Khoa học

19. LAB_MANAGER tạo đề tài nghiên cứu cho PTN.
20. LAB_MANAGER tạo nhóm nghiên cứu, thêm sinh viên, chỉ định Leader.
21. LAB_MANAGER tạo dự án, gắn với nhóm và đề tài.
22. LAB_MANAGER tạo mốc tiến độ, chia thành nhiệm vụ, gán cho từng sinh viên.
23. Sinh viên (Member) thực hiện nhiệm vụ, cập nhật trạng thái, nộp báo cáo.
24. Leader duyệt báo cáo cấp nhóm (cấp 1).
25. LAB_MANAGER duyệt báo cáo cấp cuối (cấp 2).
26. Sinh viên nộp sản phẩm nghiên cứu, ghi nhật ký hoạt động.
27. LAB_MANAGER đánh giá từng sinh viên theo 5 tiêu chí.

### Giai đoạn 5: Giám sát và Kiểm toán (ADMIN)

28. ADMIN theo dõi dashboard tổng quan, kiểm tra nhật ký kiểm toán.
29. ADMIN điều chỉnh cấu hình hệ thống nếu cần.
30. ADMIN khóa tài khoản vi phạm nghiêm trọng.

---

## 9. Luồng Nghiệp vụ Báo cáo NCKH (Chi tiết)

Đây là luồng nghiệp vụ phức tạp nhất và có giá trị học thuật cao nhất của hệ thống. Luồng mô tả quá trình **từ khi nhiệm vụ được giao đến khi hoàn thành và được đánh giá**, qua mô hình review hai cấp.

### Bước 1: Nhiệm vụ được tạo (TODO)

LAB_MANAGER tạo nhiệm vụ trong mốc tiến độ, gán cho sinh viên cụ thể. Nhiệm vụ ở trạng thái **TODO**, hiển thị trong cột "Cần làm" trên bảng tiến độ.

### Bước 2: Sinh viên bắt đầu thực hiện (DOING)

Member nhận nhiệm vụ, cập nhật trạng thái từ TODO → **DOING**. Nhiệm vụ chuyển sang cột "Đang làm". Sinh viên bắt đầu thực hiện công việc, ghi nhật ký nghiên cứu hàng ngày.

### Bước 3: Nộp báo cáo (SUBMITTED)

Khi đạt kết quả, Member nộp báo cáo gồm: nội dung đã làm, kết quả, khó khăn, kế hoạch tiếp theo, tự đánh giá và **file đính kèm bắt buộc**. Báo cáo được gán version tự động (v1) và trạng thái **SUBMITTED**.

### Bước 4: Leader Review (Cấp 1)

Leader nhận thông báo có báo cáo mới từ thành viên nhóm. Leader xem nội dung, file đính kèm và đưa ra quyết định:

| Quyết định Leader | Trạng thái báo cáo | Hành động tiếp theo |
|--------------------|---------------------|---------------------|
| **Chấp nhận** | LEADER_REVIEWED | Chuyển lên Manager duyệt (Bước 5) |
| **Yêu cầu chỉnh sửa** | NEEDS_REVISION | Trả về Member (quay lại Bước 3 với version mới) |
| **Từ chối** | LEADER_REJECTED | Member cần nộp phiên bản hoàn toàn mới |

> **Trường hợp đặc biệt:** Khi **Leader tự nộp báo cáo**, bước này bị bỏ qua — báo cáo chuyển thẳng lên Manager để đảm bảo tính khách quan.

### Bước 5: Manager Review (Cấp 2 — Cấp cuối)

LAB_MANAGER xem danh sách báo cáo đã được Leader duyệt (trạng thái LEADER_REVIEWED) trong PTN mình quản lý. Manager xem xét kỹ lưỡng và đưa ra quyết định cuối cùng:

| Quyết định Manager | Trạng thái báo cáo | Hành động tiếp theo |
|---------------------|---------------------|---------------------|
| **Duyệt** | APPROVED | Báo cáo chính thức được chấp nhận (Bước 6) |
| **Từ chối** | MANAGER_REJECTED | Member cần nộp phiên bản mới (quay lại Bước 3) |

### Bước 6: Hoàn thành (DONE)

Khi báo cáo được duyệt (APPROVED), nhiệm vụ tương ứng có thể được cập nhật trạng thái thành **DONE**. Nhiệm vụ chuyển sang cột "Hoàn thành" trên bảng tiến độ.

### Tổng hợp luồng trạng thái

```
Nhiệm vụ:   TODO ──→ DOING ──→ WAITING_REVIEW ──→ NEEDS_REVISION ──→ DONE
                                       ↕                    ↕
Báo cáo:         SUBMITTED ──→ LEADER_REVIEWED ──→ APPROVED
                      ↑              │                   │
                      │         NEEDS_REVISION      MANAGER_REJECTED
                      │              │                   │
                      └──────────────┘                   │
                      └──────────────────────────────────┘
                        (Nộp phiên bản mới — version+1)
```

### Ví dụ minh họa

> Sinh viên Nguyễn Văn A được giao nhiệm vụ "Khảo sát thuật toán phân loại" trong mốc "Giai đoạn 1 — Nghiên cứu lý thuyết".
>
> 1. A nhận task (TODO), bắt đầu nghiên cứu (DOING).
> 2. Sau 5 ngày, A nộp báo cáo v1 kèm file tổng hợp (SUBMITTED).
> 3. Leader Trần Thị B đọc báo cáo, nhận thấy thiếu so sánh giữa các thuật toán → yêu cầu bổ sung (NEEDS_REVISION).
> 4. A bổ sung nội dung, nộp lại báo cáo v2 (SUBMITTED).
> 5. B duyệt v2 — đạt yêu cầu (LEADER_REVIEWED).
> 6. Manager — ThS. Lê Văn C duyệt cuối cùng — chấp nhận (APPROVED).
> 7. Task chuyển sang DONE. Kết quả được lưu trữ, phục vụ đánh giá cuối kỳ.

---

## 10. Kết luận Nghiệp vụ

### 10.1. Tổng kết giá trị hệ thống

Hệ thống Lab Portal giải quyết trọn vẹn hai nhóm nghiệp vụ lớn:

| Nhóm nghiệp vụ | Giá trị mang lại |
|-----------------|-------------------|
| **Quản lý vận hành PTN** | Số hóa toàn bộ quy trình từ ứng tuyển, đăng ký, check-in đến vệ sinh, vi phạm, khiếu nại — tạo ra hệ thống quản lý PTN minh bạch, có truy xuất. |
| **Quản lý NCKH sinh viên** | Cung cấp nền tảng quản lý hoạt động nghiên cứu theo nhóm với quy trình rõ ràng: đề tài → nhóm → dự án → mốc → nhiệm vụ → báo cáo → đánh giá. |

### 10.2. Vì sao phù hợp làm nền tảng cho Khóa luận Tốt nghiệp

Dự án Lab Portal đáp ứng các tiêu chí để phát triển thành đề tài khóa luận tốt nghiệp:

1. **Quy mô nghiệp vụ đủ lớn:** Hệ thống bao gồm 25 module nghiệp vụ, hơn 127 API endpoint, 30 bảng dữ liệu — đủ phức tạp để thể hiện năng lực phân tích, thiết kế và cài đặt của sinh viên.

2. **Phân quyền nhiều cấp:** Mô hình RBAC với 3 role hệ thống + 2 role nhóm, kết hợp kiểm soát quyền theo phạm vi (PTN, nhóm) — phản ánh yêu cầu thực tế trong các hệ thống doanh nghiệp.

3. **Luồng nghiệp vụ phức tạp:** Quy trình review báo cáo hai cấp (Leader → Manager) với hỗ trợ nhiều phiên bản — thể hiện khả năng xử lý luồng công việc đa bước trong phần mềm.

4. **Kiến trúc kỹ thuật hiện đại:** Sử dụng công nghệ phổ biến trong ngành (Spring Boot, React, MySQL, Redis, Docker) — giúp sinh viên tích lũy kinh nghiệm thực tế.

5. **Khả năng mở rộng:** Dự án có nhiều hướng phát triển tiềm năng cho khóa luận:
   - Tích hợp thông báo thời gian thực (WebSocket/SSE).
   - Phát triển ứng dụng di động.
   - Tích hợp cloud storage cho file upload.
   - Xây dựng dashboard phân tích dữ liệu nâng cao.
   - Triển khai CI/CD và containerize toàn bộ hệ thống.
   - Thêm module quản lý thiết bị PTN.

6. **Ý nghĩa thực tiễn:** Hệ thống giải quyết vấn đề thực tế tại các trường đại học, có thể triển khai thử nghiệm — tạo ra sản phẩm có giá trị sử dụng, không chỉ mang tính học thuật.

> Với nền tảng nghiệp vụ phong phú và kiến trúc kỹ thuật vững chắc đã xây dựng trong môn Dự án Công nghệ, hệ thống Lab Portal hoàn toàn phù hợp để phát triển sâu hơn thành đề tài Khóa luận Tốt nghiệp — tập trung vào các khía cạnh nâng cao về hiệu năng, trải nghiệm người dùng và khả năng triển khai thực tế.
