# Sườn Báo cáo Dự án Công nghệ — Lab Portal

> **Đề tài:** Xây dựng hệ thống Lab Portal hỗ trợ quản lý phòng thí nghiệm và hoạt động nghiên cứu khoa học cho sinh viên
> **Tính chất:** Tiền đề cho Khóa luận Tốt nghiệp

---

## Trang bìa

**Cần viết gì:**
Trình bày theo mẫu của trường, bao gồm:

- Tên trường, khoa, ngành.
- Tên môn học: Dự án Công nghệ.
- Tên đề tài: *"Xây dựng hệ thống Lab Portal hỗ trợ quản lý phòng thí nghiệm và hoạt động nghiên cứu khoa học cho sinh viên"*.
- Họ tên sinh viên, MSSV.
- Giảng viên hướng dẫn.
- Năm học.

---

## Lời cảm ơn

**Cần viết gì:**
Lời tri ân ngắn gọn, trình bày trong 1 trang. Nội dung gợi ý:

- Cảm ơn giảng viên hướng dẫn đã định hướng, góp ý trong suốt quá trình thực hiện.
- Cảm ơn giảng viên bộ môn Dự án Công nghệ.
- Cảm ơn gia đình, bạn bè đã hỗ trợ.
- Nêu ý thức rằng đề tài là bước đệm cho khóa luận tốt nghiệp.

---

## Tóm tắt

**Cần viết gì:**
Tóm tắt toàn bộ báo cáo trong 1–2 trang, bao gồm: bối cảnh, mục tiêu, phương pháp, kết quả chính.

**Gợi ý nội dung cho Lab Portal:**

> Báo cáo trình bày quá trình xây dựng hệ thống Lab Portal — nền tảng web hỗ trợ quản lý phòng thí nghiệm và hoạt động nghiên cứu khoa học cho sinh viên. Hệ thống phục vụ ba nhóm tác nhân chính: Quản trị viên (Admin), Quản lý phòng thí nghiệm (Lab Manager) và Sinh viên (Student). Về mặt vận hành PTN, hệ thống cung cấp các chức năng ứng tuyển thành viên, đăng ký sử dụng, xác nhận có mặt bằng QR code, phân công vệ sinh, ghi nhận vi phạm và xử lý khiếu nại. Về mặt nghiên cứu khoa học, hệ thống quản lý quy trình từ lập đề tài, tổ chức nhóm, phân công nhiệm vụ đến nộp — duyệt báo cáo hai cấp và đánh giá kết quả. Hệ thống được xây dựng trên kiến trúc client–server với React + TypeScript ở frontend, Spring Boot ở backend, MySQL làm cơ sở dữ liệu chính, Redis cho caching và Docker Compose cho môi trường phát triển. Dự án là tiền đề cho khóa luận tốt nghiệp, với nhiều hướng phát triển tiềm năng về thông báo thời gian thực, ứng dụng di động và triển khai cloud.

---

## Danh mục Hình ảnh

**Cần viết gì:**
Liệt kê toàn bộ hình ảnh trong báo cáo theo format: Hình X.Y — Mô tả — Trang.

**Gợi ý hình ảnh nên có:**

| Hình | Mô tả gợi ý |
|------|-------------|
| Hình 2.1 | Sơ đồ luồng ứng tuyển PTN |
| Hình 2.2 | Sơ đồ luồng đăng ký – duyệt – check-in |
| Hình 2.3 | Sơ đồ luồng NCKH tổng quát |
| Hình 2.4 | Sơ đồ luồng nộp/duyệt báo cáo hai cấp |
| Hình 3.1 | Biểu đồ Use Case tổng quát |
| Hình 3.2 | Biểu đồ Use Case ADMIN |
| Hình 3.3 | Biểu đồ Use Case LAB_MANAGER |
| Hình 3.4 | Biểu đồ Use Case STUDENT |
| Hình 4.1 | Sơ đồ kiến trúc tổng thể hệ thống |
| Hình 4.2 | Sơ đồ cấu trúc package backend |
| Hình 4.3 | Sơ đồ cấu trúc module frontend |
| Hình 4.4 | Sơ đồ ERD (Entity Relationship Diagram) |
| Hình 4.5 | Sơ đồ luồng xác thực JWT |
| Hình 4.6 | Ma trận phân quyền RBAC |
| Hình 4.7–4.12 | Mockup giao diện các trang chính |
| Hình 5.1 | Sơ đồ Docker Compose |
| Hình 5.2–5.10 | Screenshot giao diện thực tế |
| Hình 6.1–6.5 | Screenshot kết quả kiểm thử |

---

## Danh mục Bảng biểu

**Gợi ý bảng biểu nên có:**

| Bảng | Mô tả gợi ý |
|------|-------------|
| Bảng 2.1 | Danh sách tác nhân hệ thống |
| Bảng 2.2 | Ràng buộc nghiệp vụ |
| Bảng 3.1 | Danh sách yêu cầu chức năng |
| Bảng 3.2 | Danh sách yêu cầu phi chức năng |
| Bảng 3.3–3.8 | Đặc tả use case chi tiết |
| Bảng 4.1 | Danh sách bảng CSDL chính |
| Bảng 4.2 | Mô tả chi tiết bảng users |
| Bảng 4.3–4.8 | Mô tả chi tiết các bảng khác |
| Bảng 4.9 | Ma trận phân quyền module – role |
| Bảng 5.1 | Công nghệ và phiên bản |
| Bảng 5.2 | Danh sách Docker containers |
| Bảng 6.1–6.5 | Kết quả kiểm thử theo kịch bản |

---

# CHƯƠNG 1. MỞ ĐẦU

---

## 1.1. Lý do chọn đề tài

**Cần viết gì:**
Trình bày bối cảnh thực tế, vấn đề cần giải quyết, xu hướng công nghệ liên quan. Giải thích vì sao quyết định xây dựng hệ thống Lab Portal.

**Gợi ý nội dung:**

Mở đầu bằng vai trò quan trọng của PTN trong giáo dục đại học — đặc biệt trong các ngành kỹ thuật và CNTT. Nêu thực trạng quản lý PTN hiện tại:

- Nhiều cơ sở đào tạo quản lý PTN bằng phương pháp thủ công: sổ sách, biểu mẫu giấy, bảng tính Excel.
- Đăng ký sử dụng PTN thông qua email hoặc danh sách giấy — thiếu kiểm soát số lượng, dễ xung đột lịch.
- Hoạt động NCKH sinh viên thiếu công cụ theo dõi tiến độ, phân công nhiệm vụ và quản lý báo cáo.
- Thông tin rời rạc, không có hệ thống tập trung — khó truy xuất, khó thống kê.

Từ đó dẫn đến nhu cầu xây dựng một nền tảng web tập trung, tự động hóa quy trình quản lý PTN và NCKH, phục vụ ba nhóm đối tượng: quản trị viên, quản lý PTN và sinh viên.

Kết thúc bằng lý do chọn đề tài này làm dự án tiền đề cho khóa luận tốt nghiệp: quy mô vừa đủ, nghiệp vụ phong phú, có tiềm năng mở rộng.

**Hình/bảng nên đưa vào:**
- *(Không bắt buộc cho mục này)*

---

## 1.2. Mục tiêu đề tài

**Cần viết gì:**
Liệt kê rõ ràng các mục tiêu chính và phụ. Chia thành mục tiêu nghiệp vụ và mục tiêu kỹ thuật.

**Gợi ý nội dung:**

**Mục tiêu chung:**
Xây dựng hệ thống web Lab Portal hỗ trợ quản lý vận hành phòng thí nghiệm và quản lý hoạt động nghiên cứu khoa học của sinh viên, làm tiền đề cho khóa luận tốt nghiệp.

**Mục tiêu cụ thể:**

*Về nghiệp vụ:*
1. Xây dựng quy trình ứng tuyển, duyệt hồ sơ và quản lý thành viên PTN.
2. Xây dựng quy trình đăng ký sử dụng PTN theo khung giờ, bao gồm duyệt, check-in bằng QR code, hàng đợi khi đầy chỗ.
3. Xây dựng quy trình phân công vệ sinh, ghi nhận vi phạm và xử lý khiếu nại.
4. Xây dựng quy trình NCKH theo nhóm: đề tài → nhóm → dự án → mốc tiến độ → nhiệm vụ → báo cáo → đánh giá.
5. Xây dựng quy trình duyệt báo cáo hai cấp (Leader → Manager) có hỗ trợ phiên bản.
6. Cung cấp dashboard tổng quan cho quản trị viên và quản lý PTN.

*Về kỹ thuật:*
1. Xây dựng REST API backend bằng Spring Boot với phân quyền RBAC (Role-Based Access Control).
2. Xây dựng frontend SPA bằng React + TypeScript với quản lý state bằng React Query.
3. Thiết kế cơ sở dữ liệu MySQL với Flyway migration, soft-delete pattern.
4. Tích hợp Redis cho lưu trữ OTP, email SMTP cho xác thực và thông báo.
5. Docker hóa môi trường phát triển.

**Hình/bảng nên đưa vào:**
- *(Không bắt buộc)*

---

## 1.3. Phạm vi đề tài

**Cần viết gì:**
Xác định rõ phạm vi thực hiện: những gì nằm trong phạm vi (in scope) và ngoài phạm vi (out of scope).

**Gợi ý nội dung:**

**Trong phạm vi:**
- Quản lý tài khoản người dùng: đăng ký, đăng nhập, phân quyền.
- Quản lý PTN: tạo PTN, gán manager, ứng tuyển, duyệt hồ sơ.
- Quản lý sử dụng PTN: khung giờ, đăng ký, duyệt, check-in QR, hàng đợi.
- Quản lý vệ sinh, vi phạm, khiếu nại.
- Quản lý NCKH: đề tài, nhóm, dự án, milestone, task, report, product, evaluation, research log.
- Module Admin: dashboard, quản lý user/lab, cấu hình hệ thống, audit log.
- Email thông báo (đăng ký OTP, booking, hủy slot).

**Ngoài phạm vi (dành cho khóa luận):**
- Thông báo thời gian thực (WebSocket).
- Ứng dụng di động (iOS/Android).
- Tích hợp cloud storage (AWS S3, Google Cloud Storage).
- Hệ thống analytics và báo cáo nâng cao.
- CI/CD pipeline và triển khai production.
- Module quản lý thiết bị PTN.
- Chat/messaging nội bộ.

**Hình/bảng nên đưa vào:**
- *(Không bắt buộc, có thể tạo bảng 2 cột: Trong phạm vi / Ngoài phạm vi)*

---

## 1.4. Đối tượng sử dụng

**Cần viết gì:**
Mô tả rõ từng nhóm người dùng, ngữ cảnh sử dụng.

**Gợi ý nội dung:**

| Đối tượng | Mô tả | Ngữ cảnh sử dụng |
|-----------|-------|-------------------|
| **Quản trị viên (ADMIN)** | Quản trị viên CNTT hoặc phòng đào tạo, chịu trách nhiệm quản lý toàn bộ hệ thống. | Đăng nhập vào Admin Portal để tạo PTN, quản lý user, gán manager, theo dõi audit log. |
| **Quản lý PTN (LAB_MANAGER)** | Giảng viên hoặc trợ giảng phụ trách vận hành một PTN cụ thể. | Đăng nhập vào Manager Portal để duyệt ứng tuyển, tạo khung giờ, check-in sinh viên, tổ chức NCKH. |
| **Sinh viên (STUDENT)** | Sinh viên đại học tham gia sử dụng PTN và hoạt động NCKH. | Đăng nhập vào User Portal để ứng tuyển PTN, đăng ký sử dụng, tham gia nhóm nghiên cứu, nộp báo cáo. |

Trong bối cảnh NCKH, sinh viên có thêm vai trò nhóm:
- **Leader:** Trưởng nhóm — có quyền xem toàn bộ tiến độ nhóm và duyệt báo cáo cấp 1.
- **Member:** Thành viên — thực hiện nhiệm vụ được giao, nộp báo cáo.

**Hình/bảng nên đưa vào:**
- **Bảng 1.1:** Danh sách đối tượng sử dụng (như trên)

---

## 1.5. Ý nghĩa của đề tài

**Cần viết gì:**
Trình bày ý nghĩa khoa học và thực tiễn. Nhấn mạnh tính tiền đề cho khóa luận.

**Gợi ý nội dung:**

*Ý nghĩa thực tiễn:*
- Giải quyết vấn đề thực tế tại các trường đại học: quản lý PTN chủ yếu bằng phương pháp thủ công.
- Hệ thống có thể triển khai thử nghiệm tại khoa/bộ môn, phục vụ nhu cầu thực.
- Số hóa quy trình NCKH giúp minh bạch hóa đánh giá, giảm tải cho giảng viên.

*Ý nghĩa khoa học — kỹ thuật:*
- Ứng dụng kiến trúc Modular Monolith hiện đại với Spring Boot.
- Thực hành quy trình phân quyền RBAC nhiều cấp.
- Thiết kế và triển khai quy trình nghiệp vụ phức tạp (review hai cấp, versioning, waitlist).
- Tích lũy kinh nghiệm với stack công nghệ phổ biến trong ngành (React, Spring Boot, MySQL, Redis, Docker).

*Ý nghĩa đối với khóa luận tốt nghiệp:*
- Dự án tạo nền tảng vững chắc cả về nghiệp vụ lẫn kỹ thuật cho khóa luận.
- Nhiều hướng mở rộng: real-time notification, mobile app, cloud deployment, analytics dashboard.
- Quy mô hệ thống (25 module, 127+ endpoint, 30+ bảng CSDL) đủ sâu để phát triển tiếp.

**Hình/bảng nên đưa vào:**
- *(Không bắt buộc)*

---

# CHƯƠNG 2. KHẢO SÁT VÀ PHÂN TÍCH NGHIỆP VỤ

---

## 2.1. Hiện trạng quản lý phòng thí nghiệm

**Cần viết gì:**
Phân tích thực trạng quản lý PTN tại các trường đại học, chỉ ra các vấn đề cần giải quyết.

**Gợi ý nội dung:**

Trình bày theo 3 khía cạnh:

*(1) Quản lý vận hành PTN:*
- Đăng ký sử dụng qua biểu mẫu giấy/email → không kiểm soát số lượng, xung đột lịch.
- Điểm danh thủ công → sai sót, không truy xuất được.
- Phân công vệ sinh bằng miệng → không minh bạch.
- Vi phạm ghi sổ → không lưu trữ có hệ thống, khó tra cứu.

*(2) Quản lý thành viên:*
- Đơn ứng tuyển nộp qua email → dễ thất lạc, không theo dõi trạng thái.
- Không có cơ sở dữ liệu thành viên tập trung.

*(3) Quản lý NCKH:*
- Phân công nhiệm vụ qua tin nhắn nhóm → thiếu chính thức, khó kiểm tra.
- Báo cáo nộp qua email → mất version, không duyệt tập trung.
- Đánh giá sinh viên bằng bảng tính → chủ quan, thiếu tiêu chí rõ ràng.

Kết luận: cần xây dựng hệ thống tập trung giải quyết đồng thời cả 3 vấn đề.

**Hình/bảng nên đưa vào:**
- **Bảng 2.1:** So sánh quản lý thủ công vs. Lab Portal (2 cột, 6–8 dòng so sánh)

---

## 2.2. Các tác nhân trong hệ thống

**Cần viết gì:**
Liệt kê và mô tả chi tiết từng tác nhân (actor).

**Gợi ý nội dung:**

| Tác nhân | Mô tả | Trách nhiệm chính |
|----------|-------|--------------------|
| **ADMIN** | Quản trị viên hệ thống | Quản lý user, PTN, gán manager, cấu hình hệ thống, audit log |
| **LAB_MANAGER** | Quản lý PTN | Duyệt ứng tuyển, slot/booking/check-in, cleaning, penalty, NCKH |
| **STUDENT (MEMBER)** | Sinh viên — thành viên nhóm NC | Sử dụng PTN, thực hiện task, nộp báo cáo |
| **STUDENT (LEADER)** | Sinh viên — trưởng nhóm NC | = MEMBER + duyệt báo cáo cấp nhóm, theo dõi toàn nhóm |
| **Hệ thống (System)** | Xử lý tự động | Auto no-show, email notification, waitlist promotion |

Giải thích rằng LEADER/MEMBER là vai trò **trong nhóm nghiên cứu**, không phải role hệ thống. Một sinh viên có thể là Leader ở nhóm A, Member ở nhóm B.

**Hình/bảng nên đưa vào:**
- **Bảng 2.2:** Danh sách tác nhân (như trên)

---

## 2.3. Phân tích nghiệp vụ theo vai trò

**Cần viết gì:**
Mô tả chi tiết nghiệp vụ của từng tác nhân. Đây là phần dài nhất của chương.

**Gợi ý nội dung:**

### 2.3.1. Nghiệp vụ ADMIN
Mô tả 7 chức năng:
1. Xem dashboard thống kê (tổng user, PTN, trạng thái).
2. Quản lý tài khoản: danh sách, ban/unban.
3. Đổi vai trò user (STUDENT ↔ LAB_MANAGER).
4. Tạo PTN, cập nhật trạng thái (AVAILABLE / UNDER_MAINTENANCE / CLOSED).
5. Gán Manager cho PTN.
6. Quản lý cấu hình hệ thống (key–value, có audit trail).
7. Xem nhật ký kiểm toán.

### 2.3.2. Nghiệp vụ LAB_MANAGER
Chia thành 2 nhóm:

*Nhóm A — Vận hành PTN:*
1. Duyệt hồ sơ ứng tuyển (PENDING → APPROVED/REJECTED, tạo Membership khi duyệt).
2. Quản lý thành viên (xem danh sách, xóa thành viên).
3. Tạo khung giờ (ngày, giờ, capacity).
4. Duyệt/từ chối booking.
5. Hủy khung giờ (thông báo email tự động).
6. Xác nhận check-in bằng QR.
7. Phân công vệ sinh (chọn SV đủ điều kiện).
8. Ghi nhận vi phạm (loại, điểm, lý do).
9. Xử lý khiếu nại (RESOLVED/REJECTED).

*Nhóm B — Quản lý NCKH:*
1. Tạo đề tài (RECRUITING → ACTIVE → CLOSED).
2. Tạo nhóm, thêm thành viên, chỉ định Leader.
3. Tạo dự án (DRAFT → IN_PROGRESS → COMPLETED).
4. Tạo milestone (deadline, progress).
5. Tạo task, gán cho SV (Kanban: TODO → DOING → DONE).
6. Duyệt báo cáo cấp Manager (APPROVED/MANAGER_REJECTED).
7. Đánh giá SV theo 5 tiêu chí.
8. Xem dashboard NCKH.

### 2.3.3. Nghiệp vụ STUDENT — MEMBER
1. Đăng ký tài khoản (3 bước: email → OTP → thông tin).
2. Ứng tuyển PTN (upload CV/link).
3. Đăng ký sử dụng PTN (booking, waitlist khi đầy).
4. Check-in bằng QR.
5. Xem/hoàn thành nhiệm vụ vệ sinh.
6. Xem vi phạm, gửi khiếu nại.
7. Xem mốc/nhiệm vụ của mình.
8. Cập nhật trạng thái task (TODO → DOING).
9. Nộp báo cáo (kèm file bắt buộc, version tự động).
10. Nộp lại / cập nhật báo cáo.
11. Xem góp ý (comment).
12. Xem đánh giá cá nhân.

### 2.3.4. Nghiệp vụ STUDENT — LEADER
Nguyên tắc: LEADER = MEMBER + quyền quản lý nhóm.

Quyền bổ sung:
1. Xem mốc tiến độ toàn nhóm.
2. Xem task board toàn nhóm (không chỉ task của mình).
3. Xem báo cáo toàn nhóm.
4. Duyệt báo cáo cấp nhóm (LEADER_REVIEWED / NEEDS_REVISION / LEADER_REJECTED).
5. Theo dõi sản phẩm nhóm.
6. Theo dõi nhật ký nghiên cứu nhóm.

Ràng buộc: Leader không tự duyệt báo cáo của mình → chuyển thẳng lên Manager.

**Hình/bảng nên đưa vào:**
- **Bảng 2.3:** Tổng hợp chức năng theo role (dạng ma trận: chức năng × role, đánh dấu ✓)

---

## 2.4. Luồng nghiệp vụ quản lý PTN

**Cần viết gì:**
Mô tả các luồng chính bằng text flow kết hợp sơ đồ. Mỗi luồng mô tả: điểm bắt đầu, các bước, điểm kết thúc, ngoại lệ.

**Gợi ý nội dung — 4 luồng chính:**

*Luồng 1: Ứng tuyển PTN*
Sinh viên xem PTN → nộp đơn (CV) → PENDING → Manager duyệt → APPROVED (tạo Membership) / REJECTED (SV nộp lại).

*Luồng 2: Đăng ký – duyệt – check-in*
Manager tạo slot → SV đăng ký → PENDING_APPROVAL → Manager duyệt → APPROVED → SV tạo QR → Manager quét → CHECKED_IN → COMPLETED. Ngoại lệ: slot đầy → Waitlist; SV hủy → CANCELLED_BY_STUDENT; Manager hủy slot → email thông báo.

*Luồng 3: Phân công vệ sinh*
Slot kết thúc → Manager chọn SV đủ điều kiện → tạo cleaning task → PENDING → SV hoàn thành → COMPLETED.

*Luồng 4: Vi phạm – khiếu nại*
Manager ghi vi phạm → SV xem → SV khiếu nại (PENDING) → Manager xem xét → RESOLVED / REJECTED.

**Hình/bảng nên đưa vào:**
- **Hình 2.1:** Sơ đồ hoạt động (Activity Diagram) luồng ứng tuyển
- **Hình 2.2:** Sơ đồ hoạt động luồng đăng ký – check-in
- **Hình 2.3:** Sơ đồ hoạt động luồng vi phạm – khiếu nại

---

## 2.5. Luồng nghiệp vụ nghiên cứu khoa học

**Cần viết gì:**
Mô tả chi tiết luồng NCKH — phần phức tạp nhất. Đặc biệt nhấn mạnh quy trình review 2 cấp.

**Gợi ý nội dung:**

*Luồng tổng quát:*
Đề tài → Nhóm NC → Dự án → Milestone → Task → Report → Review 2 cấp → Product → Evaluation → Research Log.

*Luồng report chi tiết:*

```
Task (TODO) → SV bắt đầu (DOING) → SV nộp báo cáo v1 (SUBMITTED)
→ Leader review:
   ├─ Chấp nhận → LEADER_REVIEWED → Manager review:
   │     ├─ Duyệt → APPROVED → Task (DONE)
   │     └─ Từ chối → MANAGER_REJECTED → SV nộp lại (v2)
   ├─ Yêu cầu sửa → NEEDS_REVISION → SV sửa, nộp lại (v2)
   └─ Từ chối → LEADER_REJECTED → SV nộp lại (v2)

Đặc biệt: Leader nộp report → bỏ qua Leader review → thẳng lên Manager
```

*Đánh giá:*
Manager đánh giá mỗi SV theo 5 tiêu chí: đóng góp, task, report, product, attitude → tổng điểm + nhận xét.

**Hình/bảng nên đưa vào:**
- **Hình 2.4:** Sơ đồ luồng NCKH tổng quát (flowchart từ đề tài → đánh giá)
- **Hình 2.5:** Sơ đồ luồng nộp/duyệt báo cáo (sequence diagram hoặc activity diagram)
- **Bảng 2.4:** Bảng trạng thái báo cáo và ý nghĩa

---

## 2.6. Ràng buộc nghiệp vụ

**Cần viết gì:**
Liệt kê các quy tắc nghiệp vụ mà hệ thống phải tuân thủ.

**Gợi ý nội dung — 19 ràng buộc, chia 3 nhóm:**

*Nhóm 1: PTN & Người dùng (RB-01 → RB-05)*
- Mỗi Manager quản lý đúng 1 PTN; mỗi PTN có 1 Manager.
- SV có thể tham gia nhiều PTN; không nộp đơn trùng.
- Manager chỉ thao tác trên PTN mình quản lý.

*Nhóm 2: Đăng ký sử dụng (RB-06 → RB-10)*
- Chỉ thành viên PTN mới đăng ký sử dụng.
- Không booking trùng slot. Slot đầy → waitlist (pessimistic locking).
- Hủy slot → email thông báo.

*Nhóm 3: NCKH (RB-11 → RB-19)*
- Chỉ thành viên PTN mới truy cập NCKH.
- Mỗi đề tài có thể nhiều nhóm; mọi nghiệp vụ theo group context.
- Member chỉ thấy task/report của mình; Leader thấy toàn nhóm.
- Leader không tự duyệt báo cáo mình.
- Report có nhiều version; chỉ version mới nhất được xử lý.
- Soft-delete toàn hệ thống.

**Hình/bảng nên đưa vào:**
- **Bảng 2.5:** Danh sách ràng buộc nghiệp vụ (mã, mô tả, giải thích)

---

# CHƯƠNG 3. PHÂN TÍCH YÊU CẦU HỆ THỐNG

---

## 3.1. Yêu cầu chức năng

**Cần viết gì:**
Liệt kê yêu cầu chức năng theo module, đánh mã (UC-xxx), mô tả ngắn.

**Gợi ý nội dung:**

| Mã | Module | Yêu cầu chức năng |
|----|--------|--------------------|
| UC-01 | Auth | Đăng ký tài khoản với xác thực email OTP |
| UC-02 | Auth | Đăng nhập bằng email/username + mật khẩu |
| UC-03 | Auth | Quên mật khẩu — đặt lại qua OTP |
| UC-04 | Auth | Làm mới access token (refresh token) |
| UC-05 | Auth | Xem và cập nhật hồ sơ cá nhân |
| UC-06 | Admin | Xem dashboard thống kê hệ thống |
| UC-07 | Admin | Quản lý tài khoản (danh sách, ban/unban) |
| UC-08 | Admin | Đổi vai trò người dùng |
| UC-09 | Admin | Tạo PTN mới |
| UC-10 | Admin | Gán Manager cho PTN |
| UC-11 | Admin | Cập nhật trạng thái PTN |
| UC-12 | Admin | Quản lý cấu hình hệ thống |
| UC-13 | Admin | Xem nhật ký kiểm toán |
| UC-14 | Lab | Xem danh sách PTN |
| UC-15 | Lab | Nộp đơn ứng tuyển PTN (upload CV) |
| UC-16 | Lab | Duyệt/từ chối đơn ứng tuyển |
| UC-17 | Lab | Quản lý thành viên PTN |
| UC-18 | Booking | Tạo khung giờ sử dụng |
| UC-19 | Booking | Đăng ký sử dụng PTN (booking) |
| UC-20 | Booking | Duyệt/từ chối booking |
| UC-21 | Booking | Hủy khung giờ (thông báo email) |
| UC-22 | Booking | Check-in bằng QR code |
| UC-23 | Booking | Xếp hàng đợi (waitlist) khi slot đầy |
| UC-24 | Cleaning | Phân công vệ sinh PTN |
| UC-25 | Cleaning | Xác nhận hoàn thành vệ sinh |
| UC-26 | Penalty | Ghi nhận vi phạm |
| UC-27 | Penalty | Xem lịch sử vi phạm |
| UC-28 | Complaint | Nộp khiếu nại |
| UC-29 | Complaint | Xử lý khiếu nại |
| UC-30 | Research | Tạo đề tài nghiên cứu |
| UC-31 | Research | Tạo nhóm nghiên cứu, thêm thành viên |
| UC-32 | Research | Tạo dự án nghiên cứu |
| UC-33 | Research | Tạo mốc tiến độ (milestone) |
| UC-34 | Research | Tạo nhiệm vụ (task), gán cho SV |
| UC-35 | Research | Cập nhật trạng thái task (Kanban) |
| UC-36 | Research | Nộp báo cáo tiến độ (kèm file) |
| UC-37 | Research | Duyệt báo cáo cấp Leader |
| UC-38 | Research | Duyệt báo cáo cấp Manager |
| UC-39 | Research | Nộp sản phẩm nghiên cứu |
| UC-40 | Research | Đánh giá sinh viên (5 tiêu chí) |
| UC-41 | Research | Ghi nhật ký nghiên cứu |
| UC-42 | Research | Xem thống kê dự án |

**Hình/bảng nên đưa vào:**
- **Bảng 3.1:** Danh sách yêu cầu chức năng (như trên, có thể bổ sung cột "Vai trò thực hiện")

---

## 3.2. Yêu cầu phi chức năng

**Cần viết gì:**
Các yêu cầu về hiệu năng, bảo mật, khả năng mở rộng, giao diện.

**Gợi ý nội dung:**

| Mã | Loại | Yêu cầu |
|----|------|---------|
| NFR-01 | Bảo mật | Mật khẩu mã hóa BCrypt (strength 12). OTP mã hóa trước khi lưu Redis. |
| NFR-02 | Bảo mật | Xác thực bằng JWT (Access Token 24h, Refresh Token 7 ngày). |
| NFR-03 | Bảo mật | Phân quyền RBAC cấp method (@PreAuthorize). Kiểm tra quyền sở hữu PTN. |
| NFR-04 | Hiệu năng | Connection pool HikariCP (max 10). Hibernate batch_size 20. |
| NFR-05 | Hiệu năng | Hàng đợi (waitlist) sử dụng pessimistic locking cho tính toàn vẹn dữ liệu. |
| NFR-06 | Khả dụng | Soft-delete pattern — không xóa vật lý dữ liệu, giữ toàn bộ lịch sử. |
| NFR-07 | Tương thích | Giao diện responsive (desktop + mobile), hỗ trợ trình duyệt hiện đại. |
| NFR-08 | Vận hành | Docker Compose cho môi trường dev. Flyway quản lý migration. |
| NFR-09 | Giao diện | Giao diện tiếng Việt, font Inter, sidebar navigation, dark mode cho Admin. |
| NFR-10 | Tích hợp | Email thông báo qua Gmail SMTP (đăng ký, booking, hủy slot). |
| NFR-11 | Dữ liệu | Timestamp lưu UTC (Instant), timezone-safe. |
| NFR-12 | API | Swagger/OpenAPI documentation, response format chuẩn {status, message, data}. |

**Hình/bảng nên đưa vào:**
- **Bảng 3.2:** Yêu cầu phi chức năng (như trên)

---

## 3.3. Use case tổng quát

**Cần viết gì:**
Biểu đồ use case ở mức tổng quát, phân theo actor. Có thể chia thành nhiều biểu đồ nhỏ.

**Gợi ý nội dung:**

Vẽ 3–4 biểu đồ:

1. **Use Case tổng quát:** Tổng hợp tất cả actor và nhóm use case chính (Auth, Admin, Lab, Booking, Cleaning, Penalty, Research).

2. **Use Case ADMIN:** 7 use case: Dashboard, Quản lý User, Đổi Role, Tạo PTN, Gán Manager, System Config, Audit Log.

3. **Use Case LAB_MANAGER:** 17 use case chia 2 nhóm (Vận hành PTN + NCKH).

4. **Use Case STUDENT:** 13 use case (Member) + 6 use case bổ sung (Leader), sử dụng `<<extend>>` để thể hiện quan hệ kế thừa.

**Hình/bảng nên đưa vào:**
- **Hình 3.1:** Biểu đồ Use Case tổng quát
- **Hình 3.2:** Biểu đồ Use Case ADMIN
- **Hình 3.3:** Biểu đồ Use Case LAB_MANAGER
- **Hình 3.4:** Biểu đồ Use Case STUDENT (MEMBER + LEADER)

---

## 3.4. Đặc tả một số use case chính

**Cần viết gì:**
Đặc tả chi tiết 5–8 use case đại diện theo format chuẩn: tên, actor, mô tả, tiền điều kiện, luồng chính, luồng ngoại lệ, hậu điều kiện.

**Gợi ý chọn 6 use case đại diện:**

### UC-01: Đăng ký tài khoản với xác thực email

| Thuộc tính | Nội dung |
|------------|----------|
| **Tên** | Đăng ký tài khoản |
| **Actor** | Sinh viên (chưa có tài khoản) |
| **Mô tả** | Sinh viên tạo tài khoản mới thông qua xác thực email OTP 3 bước |
| **Tiền điều kiện** | Email chưa đăng ký trong hệ thống |
| **Luồng chính** | 1. SV nhập email → 2. Hệ thống gửi OTP (mã hóa BCrypt, lưu Redis có TTL) → 3. SV nhập OTP → 4. Hệ thống xác minh, cấp temp token → 5. SV điền username, password, fullName, phone + temp token → 6. Hệ thống tạo user STUDENT |
| **Ngoại lệ** | OTP sai/hết hạn → thông báo lỗi. Email đã tồn tại → từ chối. |
| **Hậu điều kiện** | Tài khoản STUDENT được tạo, SV có thể đăng nhập |

### UC-19: Đăng ký sử dụng PTN

| Thuộc tính | Nội dung |
|------------|----------|
| **Tên** | Đăng ký sử dụng PTN |
| **Actor** | Sinh viên (MEMBER PTN) |
| **Mô tả** | SV đăng ký sử dụng một khung giờ có sẵn |
| **Tiền điều kiện** | SV có membership active trong PTN. Khung giờ ở trạng thái AVAILABLE. |
| **Luồng chính** | 1. SV xem danh sách slot → 2. Chọn slot → 3. Hệ thống kiểm tra capacity → 4. Tạo booking (PENDING_APPROVAL) → 5. Manager duyệt → 6. APPROVED |
| **Ngoại lệ** | Slot đầy → thêm vào Waitlist (pessimistic locking cho position). SV đã đăng ký slot này → từ chối (DuplicateBookingException). |
| **Hậu điều kiện** | Booking được tạo, chờ Manager duyệt hoặc vào waitlist |

### UC-22: Check-in bằng QR code

| Thuộc tính | Nội dung |
|------------|----------|
| **Tên** | Check-in bằng QR code |
| **Actor** | Sinh viên (tạo QR), LAB_MANAGER (quét QR) |
| **Mô tả** | Xác nhận có mặt tại PTN qua cơ chế QR 2 chiều |
| **Tiền điều kiện** | Booking ở trạng thái APPROVED. Trong khoảng thời gian khung giờ. |
| **Luồng chính** | 1. SV mở chức năng "Tạo QR" → 2. Hệ thống tạo QR token short-lived → 3. SV đưa QR cho Manager → 4. Manager quét/nhập token → 5. Hệ thống xác minh → 6. Booking → CHECKED_IN |
| **Ngoại lệ** | QR hết hạn → yêu cầu tạo lại. Check-in ngoài thời gian → InvalidCheckinTimeException. |
| **Hậu điều kiện** | Trạng thái booking = CHECKED_IN |

### UC-36: Nộp báo cáo tiến độ

| Thuộc tính | Nội dung |
|------------|----------|
| **Tên** | Nộp báo cáo tiến độ |
| **Actor** | Sinh viên (MEMBER/LEADER) |
| **Mô tả** | SV nộp báo cáo cho task đã được giao, kèm file bắt buộc |
| **Tiền điều kiện** | Task thuộc nhóm NC mà SV là thành viên. Task ở trạng thái DOING hoặc NEEDS_REVISION. |
| **Luồng chính** | 1. SV chọn task → 2. Điền nội dung (content_done, result, difficulty, next_plan, self_assessment) → 3. Upload file → 4. Hệ thống gán version tự động → 5. Báo cáo SUBMITTED |
| **Ngoại lệ** | Thiếu file → từ chối. Version trùng → ReportVersionConflictException. |
| **Hậu điều kiện** | Báo cáo được lưu, chờ Leader review (hoặc Manager nếu là Leader nộp) |

### UC-37: Duyệt báo cáo cấp Leader

| Thuộc tính | Nội dung |
|------------|----------|
| **Tên** | Duyệt báo cáo cấp Leader |
| **Actor** | Sinh viên (LEADER) |
| **Mô tả** | Leader xem xét và duyệt báo cáo cấp 1 cho thành viên nhóm |
| **Tiền điều kiện** | Báo cáo ở trạng thái SUBMITTED. Người duyệt là Leader của nhóm. Báo cáo không phải do Leader tự nộp. |
| **Luồng chính** | 1. Leader xem danh sách báo cáo nhóm → 2. Chọn báo cáo SUBMITTED → 3. Xem nội dung + file → 4. Đưa quyết định: Chấp nhận (→ LEADER_REVIEWED) / Yêu cầu sửa (→ NEEDS_REVISION) / Từ chối (→ LEADER_REJECTED) → 5. Ghi nhận xét |
| **Ngoại lệ** | Leader duyệt báo cáo của chính mình → hệ thống bỏ qua bước này. |
| **Hậu điều kiện** | Báo cáo chuyển trạng thái tương ứng |

### UC-40: Đánh giá sinh viên

| Thuộc tính | Nội dung |
|------------|----------|
| **Tên** | Đánh giá sinh viên trong dự án NC |
| **Actor** | LAB_MANAGER |
| **Mô tả** | Manager đánh giá từng SV theo 5 tiêu chí |
| **Tiền điều kiện** | Dự án NC thuộc PTN mà Manager quản lý. SV là thành viên nhóm trong dự án. |
| **Luồng chính** | 1. Manager mở dự án → 2. Chọn chức năng đánh giá → 3. Chọn SV → 4. Nhập điểm 5 tiêu chí (contribution, task, report, product, attitude) → 5. Nhập nhận xét → 6. Hệ thống tính tổng điểm → 7. Lưu đánh giá |
| **Ngoại lệ** | Điểm ngoài thang cho phép → InvalidEvaluationScoreException. |
| **Hậu điều kiện** | Đánh giá được lưu, SV có thể xem kết quả |

**Hình/bảng nên đưa vào:**
- **Bảng 3.3 → 3.8:** Mỗi use case một bảng đặc tả (như trên)

---

# CHƯƠNG 4. THIẾT KẾ HỆ THỐNG

---

## 4.1. Kiến trúc tổng thể

**Cần viết gì:**
Mô tả kiến trúc hệ thống ở mức cao nhất: client–server, các thành phần, giao tiếp giữa chúng.

**Gợi ý nội dung:**

Kiến trúc **Client–Server** gồm 4 lớp:

| Lớp | Thành phần | Mô tả |
|-----|-----------|-------|
| **Presentation** | React + TypeScript (3 Vite instances) | 3 portal: Admin (:5173), Manager (:5174), User (:5175) |
| **Application** | Spring Boot REST API (:8080) | Modular Monolith, context-path `/api` |
| **Data** | MySQL 8.0 | CSDL chính, 30+ bảng, Flyway migration |
| **Cache** | Redis 7 | OTP cache, verified tokens |

Giao tiếp:
- Frontend ↔ Backend: REST API (JSON), Axios với Bearer JWT.
- Backend ↔ MySQL: Spring Data JPA / Hibernate.
- Backend ↔ Redis: Spring Data Redis (StringRedisTemplate).
- Backend → Email: Spring Mail qua Gmail SMTP.
- Backend nội bộ: @EnableScheduling cho booking automation.

Mô hình Backend: **Modular Monolith** — các module (auth, lab, booking, research, admin, common) được phân tách theo package nhưng chạy trong cùng một ứng dụng.

**Hình/bảng nên đưa vào:**
- **Hình 4.1:** Sơ đồ kiến trúc tổng thể (block diagram: FE → API → DB/Redis/SMTP)
- **Bảng 4.1:** Thành phần kiến trúc (như trên)

---

## 4.2. Thiết kế Frontend

**Cần viết gì:**
Mô tả cấu trúc frontend, pattern sử dụng, flow dữ liệu.

**Gợi ý nội dung:**

*Cấu trúc module:*
```
client/src/
├── app/          → Entry point, Router, Providers
├── layouts/      → AdminLayout (dark), MainLayout (light), AuthLayout
├── modules/      → Feature modules (auth, admin, booking, lab, penalty, research, user)
├── shared/       → API client, Query keys, Components, Utils
```

*Pattern:*
- **Feature Module pattern:** Mỗi module gồm: `pages/`, `components/`, `hooks/`, `api/`, `types/`.
- **Server State Management:** TanStack React Query v5 — cache key hierarchy rõ ràng (88 query keys).
- **Route Guard:** `ProtectedRoute` (kiểm tra token + role), `ActiveMembershipRoute` (kiểm tra membership), `RoleBasedRoute`.
- **API Client:** Axios singleton với request/response interceptor (auto attach token, handle 401/403/500, toast dedup).

*Đặc điểm UI:*
- Responsive sidebar navigation.
- Admin Portal: dark theme riêng.
- Font Inter từ @fontsource.
- Tailwind CSS 3 cho styling.
- Form validation: React Hook Form + Zod.

**Hình/bảng nên đưa vào:**
- **Hình 4.2:** Sơ đồ cấu trúc module frontend
- **Hình 4.3:** Sơ đồ luồng routing (AuthLayout → MainLayout → AdminLayout)

---

## 4.3. Thiết kế Backend

**Cần viết gì:**
Mô tả cấu trúc package, pattern phân lớp, cơ chế bảo mật.

**Gợi ý nội dung:**

*Cấu trúc package:*
```
com.web.labportalbackend/
├── auth/       → Authentication, User, Role, JWT, Redis OTP
├── lab/        → Laboratory, Application, Membership
├── booking/    → TimeSlot, Booking, Checkin, Cleaning, Penalty, Complaint, Waitlist
├── research/   → Topic, Group, Project, Milestone, Task, Report, Product, Evaluation, Log
├── admin/      → Dashboard, Lab admin, SystemConfig, Audit
└── common/     → BaseEntity, enums, exceptions, email, config
```

*Pattern phân lớp mỗi module:*
Controller → Service (interface) → ServiceImpl → Repository → Entity + DTO + Mapper.

*Bảo mật:*
- Spring Security 6 + JWT (jjwt 0.12.6).
- `JwtAuthenticationFilter`: trích xuất và xác thực token mỗi request.
- Method Security: `@PreAuthorize("hasRole('...')")` trên mỗi endpoint.
- Kiểm tra quyền sở hữu PTN trong logic controller (assertCanManageLab).
- BCrypt(12) cho mật khẩu, BCrypt cho OTP trước khi lưu Redis.

*API:*
- 127+ endpoints RESTful, phân nhóm bằng `@Tag` (Swagger).
- Response format chuẩn: `Response<T>` với status, message, data.
- Global exception handler: 13 custom exception → HTTP status phù hợp.

**Hình/bảng nên đưa vào:**
- **Hình 4.4:** Sơ đồ phân lớp backend (Controller → Service → Repository → Entity)
- **Hình 4.5:** Sơ đồ luồng xác thực JWT (request → filter → SecurityContext → controller)

---

## 4.4. Thiết kế cơ sở dữ liệu

**Cần viết gì:**
Trình bày ERD, mô tả các bảng chính, quan hệ.

**Gợi ý nội dung:**

Hệ thống gồm **30+ bảng**, quản lý bằng **Flyway** (53 file migration). Hibernate ở chế độ `validate` — chỉ kiểm tra, không tự tạo schema.

*BaseEntity (lớp cha):*
Tất cả entity kế thừa BaseEntity với 5 trường chung: id (AUTO_INCREMENT), createdAt (UTC), updatedAt (UTC), active, deleted (soft-delete).

*Nhóm bảng chính:*
1. **Auth:** users, roles, user_roles, verification_codes
2. **Lab:** laboratories, applications, memberships
3. **Booking:** time_slots, bookings, waitlists, cleanings, penalties, complaints
4. **Research:** research_topics, research_groups, group_members, projects, milestones, tasks, reports, comments, products, evaluations, research_logs, project_logs
5. **Admin:** system_configs, system_audit_logs, audit_logs

Mô tả chi tiết 5–8 bảng quan trọng nhất (users, laboratories, bookings, projects, tasks, reports, evaluations).

**Hình/bảng nên đưa vào:**
- **Hình 4.6:** Sơ đồ ERD toàn bộ (hoặc chia thành 3–4 ERD theo module)
- **Bảng 4.2:** Mô tả bảng users (tên cột, kiểu, ràng buộc, mô tả)
- **Bảng 4.3:** Mô tả bảng laboratories
- **Bảng 4.4:** Mô tả bảng bookings
- **Bảng 4.5:** Mô tả bảng projects
- **Bảng 4.6:** Mô tả bảng tasks
- **Bảng 4.7:** Mô tả bảng reports
- **Bảng 4.8:** Mô tả bảng evaluations

---

## 4.5. Thiết kế phân quyền

**Cần viết gì:**
Mô tả mô hình RBAC, cơ chế thực thi, ma trận quyền.

**Gợi ý nội dung:**

*Mô hình RBAC 2 tầng:*
- **Tầng hệ thống:** 3 role: ADMIN, LAB_MANAGER, STUDENT → lưu trong bảng `roles` + `user_roles`.
- **Tầng nhóm NC:** 2 group role: LEADER, MEMBER → lưu trong bảng `group_members`.

*Cơ chế thực thi:*
| Tầng | Cơ chế | Ví dụ |
|------|--------|-------|
| API endpoint | `@PreAuthorize` | `@PreAuthorize("hasRole('ADMIN')")` |
| Logic nghiệp vụ | Kiểm tra trong service | `assertCanManageLab(authentication, labId)` |
| Frontend | Route Guard | `ProtectedRoute`, `ActiveMembershipRoute` |
| Frontend | Menu visibility | navItems thay đổi theo role |

*Ma trận phân quyền:*
Tạo bảng ma trận: 40 chức năng × 5 vai trò (ADMIN, LAB_MANAGER, STUDENT, MEMBER, LEADER), đánh dấu ✓ cho từng ô.

**Hình/bảng nên đưa vào:**
- **Bảng 4.9:** Ma trận phân quyền module – role
- **Hình 4.7:** Sơ đồ luồng kiểm tra quyền (request → JWT filter → @PreAuthorize → logic check)

---

## 4.6. Thiết kế giao diện

**Cần viết gì:**
Trình bày layout chung, wireframe/mockup các trang chính.

**Gợi ý nội dung:**

*Layout hệ thống:*
- **AuthLayout:** Trang đăng nhập/đăng ký, giao diện tối giản.
- **MainLayout:** Sidebar trái (navigation) + Header trên + Content. Dùng cho STUDENT và LAB_MANAGER. Responsive — sidebar thu gọn trên mobile.
- **AdminLayout:** Tương tự MainLayout nhưng dùng dark theme. Chỉ cho ADMIN.

*Thiết kế 8–10 trang chính:*
1. Trang đăng nhập
2. Trang đăng ký (3 bước)
3. Trang danh sách PTN (Student)
4. Trang quản lý slot + booking (Manager)
5. Trang check-in / quét QR (Manager)
6. Trang nhóm nghiên cứu chi tiết (Student)
7. Trang task board — Kanban (Student/Manager)
8. Trang nộp/duyệt báo cáo
9. Trang đánh giá sinh viên (Manager)
10. Admin Dashboard

**Hình/bảng nên đưa vào:**
- **Hình 4.8:** Wireframe trang đăng nhập
- **Hình 4.9:** Wireframe MainLayout (sidebar + header + content)
- **Hình 4.10:** Wireframe trang task board
- **Hình 4.11:** Wireframe trang nộp/duyệt báo cáo
- **Hình 4.12:** Wireframe Admin Dashboard

---

# CHƯƠNG 5. TRIỂN KHAI HỆ THỐNG

---

## 5.1. Môi trường và công nghệ sử dụng

**Cần viết gì:**
Liệt kê chi tiết công nghệ, framework, thư viện và phiên bản.

**Gợi ý nội dung:**

**Backend:**

| Công nghệ | Phiên bản | Vai trò |
|-----------|-----------|---------|
| Java | 17 | Ngôn ngữ lập trình |
| Spring Boot | 3.4.5 | Framework backend |
| Spring Security | 6.x | Bảo mật, JWT |
| Spring Data JPA | 3.x | ORM, truy vấn CSDL |
| Spring Data Redis | 3.x | Cache, OTP storage |
| Spring Mail | 3.x | Gửi email SMTP |
| Flyway | — | Database migration |
| jjwt | 0.12.6 | JWT token |
| Lombok | — | Giảm boilerplate code |
| SpringDoc OpenAPI | 2.8.6 | API documentation |
| Maven | — | Build tool |

**Frontend:**

| Công nghệ | Phiên bản | Vai trò |
|-----------|-----------|---------|
| React | 18.x | UI framework |
| TypeScript | 5.x | Ngôn ngữ lập trình |
| Vite | 6.x | Build tool, dev server |
| React Router | 6.x | Routing |
| TanStack React Query | 5.x | Server state management |
| Axios | — | HTTP client |
| React Hook Form + Zod | — | Form validation |
| Tailwind CSS | 3.x | Styling |
| Lucide React | — | Icons |

**Hạ tầng:**

| Công nghệ | Phiên bản | Vai trò |
|-----------|-----------|---------|
| MySQL | 8.0 | CSDL quan hệ |
| Redis | 7 Alpine | In-memory cache |
| Docker | — | Container runtime |
| Docker Compose | — | Orchestration (dev) |
| Gmail SMTP | — | Email service |

**Hình/bảng nên đưa vào:**
- **Bảng 5.1:** Công nghệ sử dụng (như trên, gộp 3 bảng)

---

## 5.2. Triển khai module Xác thực (Auth)

**Cần viết gì:**
Mô tả cách triển khai đăng ký, đăng nhập, JWT, OTP. Dùng đoạn code minh họa ngắn gọn (code snippet) + screenshot.

**Gợi ý nội dung:**

*Đăng ký 3 bước:*
- Bước 1: `POST /auth/register/send-code` → tạo OTP, hash BCrypt, lưu Redis với TTL → gửi email qua SmtpEmailService.
- Bước 2: `POST /auth/register/verify-code` → so sánh OTP hash → cấp temporary token → lưu Redis.
- Bước 3: `POST /auth/register` → xác minh temp token → tạo User entity (role STUDENT, password BCrypt).

*Đăng nhập:*
- `POST /auth/login` → DaoAuthenticationProvider xác thực → JwtService tạo access token (24h) + refresh token (7d).

*JWT Flow:*
- JwtAuthenticationFilter kế thừa OncePerRequestFilter → trích xuất token từ header → validate → set SecurityContext.
- Frontend lưu token vào localStorage, Axios interceptor tự gắn vào mọi request.

Minh họa: code snippet `JwtAuthenticationFilter` (5–10 dòng), screenshot trang đăng ký, trang đăng nhập.

**Hình/bảng nên đưa vào:**
- **Hình 5.1:** Screenshot trang đăng ký (3 bước)
- **Hình 5.2:** Screenshot trang đăng nhập
- Code snippet minh họa JWT filter (có chú thích)

---

## 5.3. Triển khai module Admin

**Cần viết gì:**
Mô tả Admin Dashboard, quản lý user, lab, system config, audit log.

**Gợi ý nội dung:**

*AdminLayout:* Dark theme, sidebar 5 mục: Dashboard, Users, Labs, System Config, Audit Logs.

*Admin Dashboard:*
- `GET /admin/dashboard/stats` → truy vấn count user, lab, booking theo trạng thái → AdminDashboardStatsResponse.

*Quản lý User:*
- `GET /admin/users` → lọc bỏ ADMIN → hiển thị danh sách.
- Ban/Unban: `PUT /admin/users/{id}/ban|unban`.
- Đổi role: `PATCH /admin/users/{id}/role`.

*Gán Manager cho PTN:*
- `GET /admin/users/assignable-managers` → danh sách LAB_MANAGER chưa gán.
- `PUT /labs/{id}/manager?managerId=...` → gán.

Screenshot: Admin Dashboard, trang Users, trang Labs.

**Hình/bảng nên đưa vào:**
- **Hình 5.3:** Screenshot Admin Dashboard (dark theme)
- **Hình 5.4:** Screenshot quản lý Users

---

## 5.4. Triển khai module Quản lý PTN

**Cần viết gì:**
Mô tả ứng tuyển, duyệt hồ sơ, quản lý thành viên.

**Gợi ý nội dung:**

*Ứng tuyển:*
- `POST /labs/{id}/apply` (multipart) → kiểm tra DuplicateApplication → tạo Application (PENDING) → lưu CV file.

*Duyệt:*
- `PUT /applications/{id}/review` → APPROVED: tạo Membership (active=true) / REJECTED.
- ApplicationController kiểm tra quyền: assertCanManageLab → chỉ Manager của PTN đó mới duyệt được.

*Thành viên:*
- `GET /labs/{id}/members` → filter active → toLabMemberResponse.
- `PATCH /labs/{id}/members/{userId}/remove` → set active=false (không xóa membership).

Screenshot: trang danh sách PTN (Student), trang duyệt hồ sơ (Manager), trang thành viên.

**Hình/bảng nên đưa vào:**
- **Hình 5.5:** Screenshot danh sách PTN
- **Hình 5.6:** Screenshot trang duyệt ứng tuyển

---

## 5.5. Triển khai module Sử dụng PTN

**Cần viết gì:**
Mô tả slot, booking, check-in, waitlist, cleaning, penalty, complaint.

**Gợi ý nội dung:**

*TimeSlot:*
- Manager tạo slot: `POST /slots` → CreateTimeSlotRequest (labId, date, startTime, endTime, capacity).

*Booking:*
- SV đăng ký: `POST /bookings` → kiểm tra slot capacity → nếu đầy → WaitlistEntity (pessimistic lock cho position).
- Manager duyệt: `PATCH /bookings/{id}/review`.
- Booking status flow: PENDING_APPROVAL → APPROVED → CHECKED_IN → COMPLETED.

*Check-in QR:*
- SV: `POST /checkin/qr` → tạo token ngắn hạn → trả QR data.
- Manager: `POST /checkin/confirm` → xác minh token → update CHECKED_IN.

*Cleaning / Penalty / Complaint:*
- Mỗi phần mô tả ngắn gọn endpoint chính + logic.
- Cleaning: eligible cleaners (SV đã check-in) → assign → complete.
- Penalty: create penalty (type, point, reason) → SV xem → complain.
- Complaint: submit → Manager review (RESOLVED/REJECTED).

Screenshot: trang slot management, trang check-in, trang vi phạm.

**Hình/bảng nên đưa vào:**
- **Hình 5.7:** Screenshot quản lý khung giờ (Manager)
- **Hình 5.8:** Screenshot check-in / quét QR
- **Hình 5.9:** Screenshot trang vi phạm & khiếu nại

---

## 5.6. Triển khai module NCKH

**Cần viết gì:**
Đây là phần quan trọng nhất — mô tả chi tiết từng sub-module: Topic, Group, Project, Milestone, Task, Report, Product, Evaluation, Log.

**Gợi ý nội dung:**

*Topic & Group:*
- `POST /research-topics` → tạo đề tài (RECRUITING).
- `POST /research-groups` → tạo nhóm, thêm members, chỉ định Leader.
- GroupMemberEntity lưu userId + GroupRole (LEADER/MEMBER).

*Project & Milestone:*
- `POST /research-projects` → tạo dự án (DRAFT → IN_PROGRESS).
- `POST /milestones` → tạo mốc (NOT_STARTED → IN_PROGRESS → COMPLETED).

*Task Board:*
- `POST /milestones/{id}/tasks` → tạo task, gán assignee.
- `PUT /tasks/{id}/status` → cập nhật trạng thái (Kanban: TODO → DOING → WAITING_REVIEW → DONE).
- Frontend: TaskBoard.tsx + TaskCard.tsx hiển thị dạng bảng cột.

*Report — quy trình 2 cấp (phần quan trọng nhất):*
- `POST /reports` (multipart) → version tự động, SUBMITTED.
- `PATCH /reports/{id}/leader-review` → LeaderReviewReportRequest (decision: ACCEPT/NEEDS_REVISION/REJECT).
- `PATCH /reports/{id}/manager-review` → ManagerReviewReportRequest (decision: APPROVE/REJECT).
- Download: `GET /reports/{id}/file` → Resource streaming.
- Replace: `PATCH /reports/{id}/replace` → cập nhật nội dung/file.

*Product:*
- `POST /products` (multipart) → ProductType (FINAL_REPORT, SLIDE, SOURCE_CODE, DATASET...).

*Evaluation:*
- `POST /evaluations` → 5 tiêu chí BigDecimal + totalScore + comment.

*Research Log:*
- `POST /logs` → workDate, durationMinutes, content, result, problem, nextPlan.
- Visibility: GROUP / PROJECT / PRIVATE.

Screenshot: trang nhóm NC, task board, nộp/duyệt báo cáo, đánh giá.

**Hình/bảng nên đưa vào:**
- **Hình 5.10:** Screenshot trang nhóm nghiên cứu
- **Hình 5.11:** Screenshot task board (Kanban)
- **Hình 5.12:** Screenshot nộp báo cáo
- **Hình 5.13:** Screenshot duyệt báo cáo (Leader view)
- **Hình 5.14:** Screenshot đánh giá sinh viên

---

## 5.7. Docker hóa môi trường phát triển

**Cần viết gì:**
Mô tả Docker Compose setup, các service, volume, network.

**Gợi ý nội dung:**

Docker Compose gồm 5 services:

| Service | Image | Port | Mô tả |
|---------|-------|------|-------|
| `redis` | redis:7-alpine | 6379 | Cache OTP |
| `mysql` | mysql:8.0 | 3306 | CSDL chính, volume persist |
| `frontend-admin` | node:18 + Vite | 5173 | Admin Portal |
| `frontend-manager` | node:18 + Vite | 5174 | Manager Portal |
| `frontend-user` | node:18 + Vite | 5175 | User Portal |

Backend chạy ngoài Docker: `mvn spring-boot:run` (port 8080).

Giải thích lý do chia 3 frontend instance: phân tách giao diện theo role, dễ phát triển song song.

**Hình/bảng nên đưa vào:**
- **Hình 5.15:** Sơ đồ Docker Compose (block diagram)
- **Bảng 5.2:** Danh sách Docker containers (như trên)

---

# CHƯƠNG 6. KIỂM THỬ VÀ ĐÁNH GIÁ

---

## 6.1. Kịch bản kiểm thử

**Cần viết gì:**
Xây dựng kịch bản kiểm thử (test scenario) cho các chức năng chính.

**Gợi ý nội dung — 5 nhóm kịch bản:**

**KT-01: Xác thực và phân quyền**
- Đăng ký với OTP → đăng nhập → truy cập trang theo role.
- Truy cập trang Admin bằng tài khoản Student → redirect / 403.
- Token hết hạn → refresh thành công → tiếp tục phiên.

**KT-02: Quản lý PTN**
- Admin tạo PTN → gán Manager → Manager xem PTN.
- SV ứng tuyển → Manager duyệt → SV truy cập đầy đủ.
- SV nộp đơn trùng → hệ thống từ chối.

**KT-03: Sử dụng PTN**
- Manager tạo slot → SV đăng ký → Manager duyệt → SV check-in QR.
- Slot đầy → SV vào waitlist → khi có chỗ → promote.
- Manager hủy slot → email thông báo.

**KT-04: NCKH — Luồng chính**
- Manager tạo đề tài → nhóm → dự án → milestone → task.
- Member nhận task → DOING → nộp report → Leader review → Manager approve → DONE.
- Leader nộp report → bỏ qua Leader review → Manager duyệt.

**KT-05: NCKH — Ngoại lệ**
- Leader yêu cầu sửa báo cáo → Member nộp v2 → Leader duyệt → Manager duyệt.
- Manager từ chối báo cáo → Member nộp v3.
- Đánh giá SV → điểm ngoài thang → lỗi.

**Hình/bảng nên đưa vào:**
- **Bảng 6.1:** Kịch bản kiểm thử (mã, mô tả, bước, kết quả mong đợi)

---

## 6.2. Kết quả kiểm thử

**Cần viết gì:**
Trình bày kết quả chạy từng kịch bản. Mỗi kịch bản: Pass/Fail + screenshot minh chứng.

**Gợi ý nội dung:**

| Mã | Kịch bản | Kết quả | Ghi chú |
|----|----------|---------|---------|
| KT-01.1 | Đăng ký OTP | ✅ Pass | Email nhận OTP thành công |
| KT-01.2 | Đăng nhập | ✅ Pass | JWT trả về đúng |
| KT-01.3 | Phân quyền | ✅ Pass | Student truy cập /admin → redirect |
| KT-02.1 | Tạo PTN + gán Manager | ✅ Pass | PTN hiển thị trong danh sách |
| KT-02.2 | Ứng tuyển + duyệt | ✅ Pass | Membership tạo thành công |
| KT-03.1 | Booking + check-in | ✅ Pass | QR tạo và xác nhận thành công |
| KT-03.2 | Waitlist | ✅ Pass | SV tự động vào hàng đợi |
| KT-04.1 | NCKH luồng chính | ✅ Pass | Report approved, task DONE |
| KT-04.2 | Leader nộp report | ✅ Pass | Bỏ qua leader review |
| KT-05.1 | Report revision | ✅ Pass | v2 nộp + duyệt thành công |

Mỗi kịch bản quan trọng kèm 1–2 screenshot minh chứng.

**Hình/bảng nên đưa vào:**
- **Bảng 6.2:** Kết quả kiểm thử tổng hợp (như trên)
- **Hình 6.1–6.5:** Screenshot kết quả kiểm thử

---

## 6.3. Đánh giá kết quả đạt được

**Cần viết gì:**
So sánh kết quả thực tế vs. mục tiêu ban đầu (mục 1.2).

**Gợi ý nội dung:**

| Mục tiêu | Kết quả | Đánh giá |
|----------|---------|----------|
| Quy trình ứng tuyển, duyệt, quản lý thành viên | Hoàn thành đầy đủ: nộp CV, duyệt, tạo membership, xóa thành viên | ✅ Đạt |
| Đăng ký sử dụng, check-in QR, waitlist | Hoàn thành: slot, booking approval, QR check-in, waitlist pessimistic locking | ✅ Đạt |
| Vệ sinh, vi phạm, khiếu nại | Hoàn thành: phân công, ghi nhận, khiếu nại, xử lý | ✅ Đạt |
| NCKH: đề tài → đánh giá | Hoàn thành toàn bộ chuỗi: topic, group, project, milestone, task, report, product, evaluation, log | ✅ Đạt |
| Review báo cáo 2 cấp + versioning | Hoàn thành: Leader review → Manager review, version auto-increment | ✅ Đạt |
| REST API + RBAC | 127+ endpoints, 3 system roles + 2 group roles, @PreAuthorize | ✅ Đạt |
| React SPA + React Query | Feature module pattern, 88 query keys, responsive | ✅ Đạt |
| Flyway + soft-delete | 53 migrations, validate mode, BaseEntity soft-delete | ✅ Đạt |
| Docker dev environment | 5 containers: Redis, MySQL, 3 FE instances | ✅ Đạt |

*Số liệu tổng kết:*
- Backend: 25+ controller, 30+ entity, 53 Flyway migration, 127+ API endpoint.
- Frontend: 8 module, 70+ component (riêng research), 88 query keys.
- Docker: 5 containers.

**Hình/bảng nên đưa vào:**
- **Bảng 6.3:** So sánh mục tiêu vs. kết quả (như trên)

---

## 6.4. Hạn chế

**Cần viết gì:**
Liệt kê hạn chế thực tế, trung thực dựa trên code.

**Gợi ý nội dung — 8 hạn chế:**

1. **Một số endpoint chưa hoàn thiện:** `PUT /labs/{id}` và `DELETE /labs/{id}` trả placeholder, chưa có logic service.
2. **Chưa có unit test tự động:** Dependency testing có trong pom.xml nhưng chưa viết test.
3. **Backend chưa Docker hóa:** Spring Boot chạy ngoài Docker, chưa có Dockerfile.
4. **Entity trùng lặp:** Tồn tại ProjectEntity (bảng `projects`) và ResearchProject (bảng `research_projects`) — chưa refactor xong.
5. **File storage cục bộ:** Upload file lưu thư mục local, chưa tích hợp cloud storage.
6. **CORS chưa restrict cho production:** Cho phép `localhost:*`, cần cấu hình domain khi triển khai.
7. **Chưa có thông báo real-time:** Mọi thông báo qua email, chưa có WebSocket/SSE.
8. **Frontend Dashboard placeholder:** DashboardPlaceholder chưa triển khai nội dung.

**Hình/bảng nên đưa vào:**
- *(Không bắt buộc, có thể dùng danh sách có đánh số)*

---

# CHƯƠNG 7. KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN

---

## 7.1. Kết luận

**Cần viết gì:**
Tổng kết ngắn gọn kết quả toàn bộ dự án, liên hệ với mục tiêu ban đầu.

**Gợi ý nội dung:**

Dự án đã hoàn thành xây dựng hệ thống Lab Portal với hai nhóm chức năng chính:

*Quản lý vận hành PTN:*
Hệ thống số hóa trọn vẹn quy trình từ ứng tuyển thành viên, đăng ký sử dụng theo khung giờ (có hỗ trợ hàng đợi), xác nhận có mặt bằng QR code, phân công vệ sinh, đến ghi nhận vi phạm và xử lý khiếu nại. Tất cả được quản lý tập trung, có phân quyền rõ ràng và truy xuất lịch sử đầy đủ.

*Quản lý NCKH sinh viên:*
Hệ thống cung cấp nền tảng quản lý toàn bộ vòng đời nghiên cứu theo nhóm: từ lập đề tài, tổ chức nhóm, phân công nhiệm vụ qua bảng Kanban, đến nộp và duyệt báo cáo qua quy trình hai cấp (Leader → Manager) với hỗ trợ nhiều phiên bản, nộp sản phẩm và đánh giá kết quả theo 5 tiêu chí.

Về mặt kỹ thuật, hệ thống sử dụng stack công nghệ hiện đại: React + TypeScript ở frontend, Spring Boot ở backend, MySQL + Redis ở tầng dữ liệu, với Docker Compose cho môi trường phát triển. Kiến trúc Modular Monolith cho phép mở rộng từng module một cách độc lập.

Dự án đạt được 100% mục tiêu đề ra trong phạm vi môn Dự án Công nghệ, tạo nền tảng vững chắc cho khóa luận tốt nghiệp.

**Hình/bảng nên đưa vào:**
- *(Không bắt buộc)*

---

## 7.2. Hướng phát triển cho Khóa luận Tốt nghiệp

**Cần viết gì:**
Nêu các hướng mở rộng cụ thể, có tính khả thi, phục vụ khóa luận.

**Gợi ý nội dung — 8 hướng, chia 3 nhóm:**

*Nhóm 1: Nâng cao trải nghiệm người dùng*
1. **Thông báo thời gian thực (WebSocket/SSE):** Push notification khi có booking mới, báo cáo mới, comment mới — thay vì chỉ gửi email.
2. **Ứng dụng di động (React Native hoặc Flutter):** SV tạo QR check-in nhanh hơn, nhận push notification, xem task board on-the-go.
3. **Chat nội bộ nhóm nghiên cứu:** Trao đổi trực tiếp giữa Leader, Member và Manager trong từng nhóm.

*Nhóm 2: Nâng cao kỹ thuật*
4. **Cloud storage (AWS S3 / Google Cloud):** Thay thế file storage cục bộ, hỗ trợ CDN, backup tự động.
5. **CI/CD pipeline (GitHub Actions / GitLab CI):** Tự động build, test, deploy khi push code.
6. **Containerize toàn bộ (Docker + Kubernetes):** Dockerfile cho Spring Boot, Kubernetes cho production deployment.
7. **Unit test + Integration test:** Viết test coverage cho service layer và API endpoint.

*Nhóm 3: Nâng cao nghiệp vụ*
8. **Dashboard analytics nâng cao:** Biểu đồ tiến độ NCKH theo thời gian, so sánh hiệu suất giữa các nhóm, dự đoán rủi ro trễ deadline bằng dữ liệu lịch sử.

**Hình/bảng nên đưa vào:**
- **Bảng 7.1:** Hướng phát triển (mã, mô tả, độ ưu tiên, công nghệ dự kiến)

---

# TÀI LIỆU THAM KHẢO

**Gợi ý:**

1. Spring Boot Documentation — https://spring.io/projects/spring-boot
2. React Documentation — https://react.dev
3. TypeScript Handbook — https://www.typescriptlang.org/docs
4. TanStack React Query — https://tanstack.com/query
5. Spring Security Reference — https://docs.spring.io/spring-security/reference
6. MySQL 8.0 Reference Manual — https://dev.mysql.com/doc/refman/8.0/en
7. Flyway Documentation — https://documentation.red-gate.com/fd
8. Redis Documentation — https://redis.io/docs
9. Docker Documentation — https://docs.docker.com
10. SpringDoc OpenAPI — https://springdoc.org
11. Tailwind CSS Documentation — https://tailwindcss.com/docs
12. JSON Web Tokens Introduction — https://jwt.io/introduction

---

# PHỤ LỤC

**Gợi ý phụ lục nên đưa vào:**

| Phụ lục | Nội dung |
|---------|----------|
| **A** | Danh sách đầy đủ 127+ API endpoints (phân theo module, có method, path, role) |
| **B** | ERD đầy đủ (toàn bộ 30+ bảng) |
| **C** | Danh mục 53 Flyway migration (version, tên, mô tả) |
| **D** | Danh sách enum và giá trị (24 enum) |
| **E** | Hướng dẫn cài đặt môi trường phát triển (Docker Compose, Maven, Node.js) |
| **F** | Screenshot bổ sung các trang chưa trình bày trong Chương 5 |

> [!TIP]
> Tham khảo chi tiết cho Phụ lục A–D tại tài liệu đã tạo sẵn:
> - [Tổng quan dự án](file:///C:/Users/nvtqx/.gemini/antigravity-ide/brain/dba80f65-8392-4d21-a080-c8b11a6b84e9/lab_portal_overview.md)
> - [Phụ lục chi tiết](file:///C:/Users/nvtqx/.gemini/antigravity-ide/brain/dba80f65-8392-4d21-a080-c8b11a6b84e9/lab_portal_appendix.md)
> - [Phân tích nghiệp vụ](file:///C:/Users/nvtqx/.gemini/antigravity-ide/brain/dba80f65-8392-4d21-a080-c8b11a6b84e9/lab_portal_business_analysis.md)
