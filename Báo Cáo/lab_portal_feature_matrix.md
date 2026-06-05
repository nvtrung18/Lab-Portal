# Bảng Tổng hợp Chức năng — Dự án Lab Portal

> **Quy ước trạng thái:**
> - ✅ **Đã triển khai** — Code backend + frontend hoàn chỉnh, có endpoint + UI.
> - 🔧 **Đang hoàn thiện** — Có endpoint nhưng logic chưa đầy đủ hoặc trả placeholder.
> - 📦 **Mock data** — Có seed data/migration nhưng chức năng phụ thuộc chưa tích hợp.
> - 🔮 **Dự kiến mở rộng** — Chưa có trong code, dành cho khóa luận tốt nghiệp.

---

## Module 1: Auth

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 1 | Auth | Gửi mã OTP đăng ký | Khách | Nhập email → hệ thống gửi OTP qua SMTP, mã hóa BCrypt, lưu Redis có TTL | ✅ Đã triển khai | 3 bước: send → verify → register |
| 2 | Auth | Xác minh OTP đăng ký | Khách | Nhập OTP → hệ thống so sánh hash → cấp temporary token | ✅ Đã triển khai | Token lưu Redis |
| 3 | Auth | Hoàn tất đăng ký | Khách | Điền username, password, fullName, phone + temp token → tạo tài khoản STUDENT | ✅ Đã triển khai | Password BCrypt(12) |
| 4 | Auth | Đăng nhập | Tất cả | Email/username + password → JWT access token (24h) + refresh token (7 ngày) | ✅ Đã triển khai | DaoAuthenticationProvider |
| 5 | Auth | Làm mới token | Tất cả | Gửi refresh token → nhận access token mới | ✅ Đã triển khai | Axios interceptor auto refresh |
| 6 | Auth | Gửi OTP quên mật khẩu | Tất cả | Nhập email → hệ thống gửi OTP reset password | ✅ Đã triển khai | Tương tự luồng đăng ký |
| 7 | Auth | Xác minh OTP reset | Tất cả | Nhập OTP → cấp reset token | ✅ Đã triển khai | |
| 8 | Auth | Đặt lại mật khẩu | Tất cả | Nhập mật khẩu mới + reset token → cập nhật | ✅ Đã triển khai | |
| 9 | Auth | Xem hồ sơ cá nhân | Tất cả | GET /auth/me → thông tin user hiện tại | ✅ Đã triển khai | |
| 10 | Auth | Cập nhật hồ sơ | Tất cả | PUT /auth/me → cập nhật fullName, phone | ✅ Đã triển khai | |

---

## Module 2: Admin

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 11 | Admin | Xem dashboard thống kê | ADMIN | Tổng user, PTN, booking theo trạng thái | ✅ Đã triển khai | AdminDashboardController |
| 12 | Admin | Xem danh sách người dùng | ADMIN | Danh sách tất cả user (lọc bỏ ADMIN) | ✅ Đã triển khai | |
| 13 | Admin | Khóa tài khoản (Ban) | ADMIN | Vô hiệu hóa tài khoản, user không thể đăng nhập | ✅ Đã triển khai | PUT /admin/users/{id}/ban |
| 14 | Admin | Mở khóa tài khoản (Unban) | ADMIN | Khôi phục quyền đăng nhập | ✅ Đã triển khai | PUT /admin/users/{id}/unban |
| 15 | Admin | Đổi vai trò người dùng | ADMIN | Thay đổi role: STUDENT ↔ LAB_MANAGER | ✅ Đã triển khai | PATCH /admin/users/{id}/role |
| 16 | Admin | Cập nhật nhiều roles | ADMIN | Gán nhiều role cùng lúc cho 1 user | ✅ Đã triển khai | PUT /admin/users/{id}/roles |
| 17 | Admin | Xem danh sách manager khả dụng | ADMIN | Danh sách LAB_MANAGER có thể gán cho PTN | ✅ Đã triển khai | GET /admin/users/assignable-managers |

---

## Module 3: Lab / Application

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 18 | Lab | Tạo PTN mới | ADMIN | Nhập tên, vị trí, sức chứa, mô tả → trạng thái AVAILABLE | ✅ Đã triển khai | POST /labs |
| 19 | Lab | Xem danh sách PTN | Tất cả | Danh sách tất cả PTN đang hoạt động | ✅ Đã triển khai | GET /labs |
| 20 | Lab | Xem chi tiết PTN | Tất cả | Thông tin chi tiết 1 PTN | ✅ Đã triển khai | GET /labs/{id} |
| 21 | Lab | Gán Manager cho PTN | ADMIN | Liên kết LAB_MANAGER với PTN | ✅ Đã triển khai | PUT /labs/{id}/manager |
| 22 | Lab | Cập nhật trạng thái PTN | ADMIN | AVAILABLE / UNDER_MAINTENANCE / CLOSED | ✅ Đã triển khai | PATCH /labs/{id}/status |
| 23 | Lab | Cập nhật thông tin PTN | ADMIN | Sửa tên, vị trí, sức chứa | 🔧 Đang hoàn thiện | PUT /labs/{id} trả placeholder |
| 24 | Lab | Xóa PTN | ADMIN | Soft-delete PTN | 🔧 Đang hoàn thiện | DELETE /labs/{id} trả placeholder |
| 25 | Lab | Xem thành viên PTN | LAB_MANAGER | Danh sách members active trong PTN mình quản lý | ✅ Đã triển khai | Kiểm tra quyền sở hữu |
| 26 | Lab | Xóa thành viên PTN | LAB_MANAGER | Set membership active=false (không xóa user) | ✅ Đã triển khai | Manager không thể xóa chính mình |
| 27 | Lab | Xem SV đủ điều kiện NC | LAB_MANAGER | Danh sách STUDENT active trong PTN | ✅ Đã triển khai | Dùng khi tạo nhóm NC |
| 28 | Application | Nộp đơn ứng tuyển PTN | STUDENT | Upload CV file hoặc URL | ✅ Đã triển khai | Multipart form-data. Chặn đơn trùng |
| 29 | Application | Duyệt/từ chối đơn | LAB_MANAGER | APPROVED → tạo Membership / REJECTED | ✅ Đã triển khai | Chỉ Manager của PTN đó |
| 30 | Application | Xem danh sách đơn | ADMIN / LAB_MANAGER | Phân trang, sắp xếp theo thời gian | ✅ Đã triển khai | Manager chỉ thấy đơn PTN mình |
| 31 | Application | Xem đơn theo user | Tất cả | Lịch sử đơn ứng tuyển của 1 user | ✅ Đã triển khai | |
| 32 | Application | Nộp lại sau bị từ chối | STUDENT | SV nộp đơn mới khi đơn cũ REJECTED | ✅ Đã triển khai | V26 migration cho phép re-apply |

---

## Module 4: Slot / Booking / Check-in

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 33 | Slot | Tạo khung giờ sử dụng | LAB_MANAGER | Chọn ngày, giờ bắt đầu/kết thúc, sức chứa | ✅ Đã triển khai | POST /slots |
| 34 | Slot | Xem khung giờ theo PTN | Tất cả | Danh sách slot theo lab_id | ✅ Đã triển khai | GET /labs/{id}/slots |
| 35 | Slot | Cập nhật trạng thái slot | LAB_MANAGER | AVAILABLE / FULL / LOCKED / CLOSED | ✅ Đã triển khai | PATCH /slots/{id}/status |
| 36 | Slot | Hủy khung giờ | LAB_MANAGER | Hủy slot + tất cả booking → email thông báo SV | ✅ Đã triển khai | PATCH /slots/{id}/cancel |
| 37 | Slot | Xóa khung giờ | LAB_MANAGER | Soft-delete slot | ✅ Đã triển khai | DELETE /slots/{id} |
| 38 | Booking | Đăng ký sử dụng PTN | STUDENT | Chọn slot → tạo booking PENDING_APPROVAL | ✅ Đã triển khai | Kiểm tra capacity + trùng |
| 39 | Booking | Duyệt/từ chối booking | LAB_MANAGER | APPROVED / REJECTED | ✅ Đã triển khai | PATCH /bookings/{id}/review |
| 40 | Booking | Hủy booking (SV) | STUDENT | CANCELLED_BY_STUDENT | ✅ Đã triển khai | PATCH /bookings/{id}/cancel |
| 41 | Booking | Xem booking của tôi | STUDENT | Lịch sử booking cá nhân | ✅ Đã triển khai | GET /bookings/me |
| 42 | Booking | Xem booking theo slot | LAB_MANAGER | Danh sách SV đăng ký 1 slot | ✅ Đã triển khai | GET /slots/{id}/bookings |
| 43 | Booking | Xếp hàng đợi (Waitlist) | STUDENT | Slot đầy → auto thêm waitlist, position pessimistic lock | ✅ Đã triển khai | WaitlistEntity + auto promote |
| 44 | Booking | Tự động đánh dấu vắng | Hệ thống | Scheduled task quét no-show theo grace period | ✅ Đã triển khai | @Scheduled cron mỗi phút |
| 45 | Check-in | Tạo mã QR check-in | STUDENT | Tạo QR token short-lived từ booking APPROVED | ✅ Đã triển khai | POST /checkin/qr |
| 46 | Check-in | Xác nhận check-in | LAB_MANAGER | Quét/nhập QR token → booking CHECKED_IN | ✅ Đã triển khai | POST /checkin/confirm |

---

## Module 5: Cleaning

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 47 | Cleaning | Xem SV đủ điều kiện vệ sinh | LAB_MANAGER | Danh sách SV đã check-in trong slot | ✅ Đã triển khai | GET /slots/{id}/eligible-cleaners |
| 48 | Cleaning | Phân công vệ sinh | LAB_MANAGER | Chọn SV → tạo cleaning task PENDING | ✅ Đã triển khai | POST /cleaning-tasks |
| 49 | Cleaning | Xem nhiệm vụ vệ sinh theo PTN | LAB_MANAGER | Danh sách cleaning tasks trong PTN | ✅ Đã triển khai | GET /labs/{id}/cleaning-tasks |
| 50 | Cleaning | Xem nhiệm vụ vệ sinh của tôi | STUDENT | Danh sách vệ sinh được phân công | ✅ Đã triển khai | GET /users/me/cleaning-tasks |
| 51 | Cleaning | Xác nhận hoàn thành vệ sinh | STUDENT | Cập nhật trạng thái → COMPLETED | ✅ Đã triển khai | PATCH /cleaning-tasks/{id}/complete |
| 52 | Cleaning | Hủy nhiệm vụ vệ sinh | LAB_MANAGER | Cập nhật trạng thái → CANCELLED | ✅ Đã triển khai | PATCH /cleaning-tasks/{id}/cancel |

---

## Module 6: Penalty / Complaint

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 53 | Penalty | Ghi nhận vi phạm | LAB_MANAGER | Chọn SV, loại (NO_SHOW, LATE_CHECKIN, EQUIPMENT_DAMAGE, NOISE, OTHER), điểm, lý do | ✅ Đã triển khai | POST /penalties |
| 54 | Penalty | Xem vi phạm theo slot | LAB_MANAGER | Danh sách vi phạm trong 1 slot | ✅ Đã triển khai | GET /slots/{id}/penalties |
| 55 | Penalty | Xem vi phạm của tôi | STUDENT | Lịch sử vi phạm cá nhân | ✅ Đã triển khai | GET /users/me/penalties |
| 56 | Penalty | Xem vi phạm theo user | Tất cả | Vi phạm của 1 user cụ thể | ✅ Đã triển khai | GET /users/{id}/penalties |
| 57 | Penalty | Cấu hình mức phạt | LAB_MANAGER | Cập nhật cấu hình penalty | ✅ Đã triển khai | PUT /config/penalty |
| 58 | Complaint | Nộp khiếu nại | STUDENT | Gửi khiếu nại liên kết penalty_id, mô tả lý do | ✅ Đã triển khai | POST /complaints |
| 59 | Complaint | Xem khiếu nại theo PTN | LAB_MANAGER | Danh sách khiếu nại trong PTN mình quản lý | ✅ Đã triển khai | GET /labs/{id}/complaints |
| 60 | Complaint | Xử lý khiếu nại | LAB_MANAGER | RESOLVED (ghi resolution_note) / REJECTED | ✅ Đã triển khai | PATCH /complaints/{id}/review |

---

## Module 7: Research Project

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 61 | Research Topic | Tạo đề tài NC | LAB_MANAGER | Tên, mô tả, yêu cầu, tài liệu. Trạng thái: RECRUITING/ACTIVE/CLOSED | ✅ Đã triển khai | POST /research-topics |
| 62 | Research Topic | Xem đề tài theo PTN | Tất cả | Danh sách đề tài NC trong PTN | ✅ Đã triển khai | GET /labs/{id}/research-topics |
| 63 | Research Project | Tạo dự án NC | LAB_MANAGER | Code, title, mô tả, mục tiêu, hướng NC, ngày, ưu tiên | ✅ Đã triển khai | POST /research-projects |
| 64 | Research Project | Cập nhật dự án NC | LAB_MANAGER | Sửa thông tin dự án | ✅ Đã triển khai | PUT /research-projects/{id} |
| 65 | Research Project | Xem dự án theo PTN | MANAGER / STUDENT | Danh sách dự án NC trong PTN | ✅ Đã triển khai | GET /labs/{id}/research-projects |
| 66 | Research Project | Xem chi tiết dự án | MANAGER / STUDENT | Thông tin đầy đủ 1 dự án | ✅ Đã triển khai | GET /research-projects/{id} |
| 67 | Research Project | Xem dự án theo nhóm | MANAGER / STUDENT | Danh sách project gắn với 1 group | ✅ Đã triển khai | GET /groups/{id}/projects |

---

## Module 8: Research Group

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 68 | Group | Tạo nhóm NC | LAB_MANAGER | Đặt tên, gắn PTN/đề tài/dự án, chỉ định Leader | ✅ Đã triển khai | POST /groups, POST /research-groups |
| 69 | Group | Cập nhật nhóm NC | LAB_MANAGER | Sửa thông tin nhóm | ✅ Đã triển khai | PUT /research-groups/{id} |
| 70 | Group | Thêm thành viên nhóm | LAB_MANAGER | Thêm SV với role LEADER/MEMBER | ✅ Đã triển khai | POST /groups/{id}/members |
| 71 | Group | Xem nhóm theo PTN | LAB_MANAGER | Danh sách nhóm NC trong PTN | ✅ Đã triển khai | GET /labs/{id}/groups |
| 72 | Group | Xem nhóm theo đề tài | LAB_MANAGER | Các nhóm thuộc 1 đề tài | ✅ Đã triển khai | GET /research-topics/{id}/groups |
| 73 | Group | Xem nhóm theo dự án | MANAGER / STUDENT | Các nhóm thuộc 1 dự án | ✅ Đã triển khai | GET /research-projects/{id}/groups |
| 74 | Group | Xem nhóm của tôi theo PTN | STUDENT | Các nhóm SV tham gia trong PTN | ✅ Đã triển khai | GET /labs/{id}/research-groups/me |
| 75 | Group | Xem chi tiết nhóm | MANAGER / STUDENT | Thông tin nhóm + Leader + topic | ✅ Đã triển khai | GET /research-groups/{id} |
| 76 | Group | Xem thành viên nhóm | MANAGER / STUDENT | Danh sách members + role | ✅ Đã triển khai | GET /research-groups/{id}/members |

---

## Module 9: Milestone / Task

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 77 | Milestone | Tạo mốc tiến độ | LAB_MANAGER | Title, description, start date, deadline, gán group | ✅ Đã triển khai | POST /milestones |
| 78 | Milestone | Cập nhật mốc | LAB_MANAGER | Sửa thông tin, progress %, status, manager_comment | ✅ Đã triển khai | PUT /milestones/{id} |
| 79 | Milestone | Xem mốc theo project | MANAGER / STUDENT | Danh sách milestone trong dự án | ✅ Đã triển khai | GET /projects/{id}/milestones |
| 80 | Milestone | Xem mốc theo nhóm | MANAGER / STUDENT | Milestone gán cho nhóm | ✅ Đã triển khai | GET /research-groups/{id}/milestones |
| 81 | Milestone | Xem mốc của tôi trong nhóm | STUDENT | Milestone liên quan đến SV hiện tại | ✅ Đã triển khai | GET /research-groups/{id}/milestones/me |
| 82 | Milestone | Xem chi tiết mốc | MANAGER / STUDENT | Thông tin đầy đủ 1 milestone | ✅ Đã triển khai | GET /milestones/{id} |
| 83 | Task | Tạo nhiệm vụ | LAB_MANAGER | Title, description, deadline, gán assignee (SV) | ✅ Đã triển khai | POST /milestones/{id}/tasks |
| 84 | Task | Cập nhật trạng thái task | MANAGER / STUDENT | Kanban: TODO → DOING → WAITING_REVIEW → DONE | ✅ Đã triển khai | PUT /tasks/{id}/status |
| 85 | Task | Xem task theo milestone | MANAGER / STUDENT | Task board theo mốc tiến độ | ✅ Đã triển khai | GET /milestones/{id}/tasks |
| 86 | Task | Xem task theo nhóm | MANAGER / STUDENT (Leader) | Task board toàn nhóm | ✅ Đã triển khai | GET /research-groups/{id}/tasks |
| 87 | Task | Xem task của tôi trong nhóm | STUDENT (Member) | Chỉ task giao cho SV hiện tại | ✅ Đã triển khai | GET /research-groups/{id}/tasks/me |

---

## Module 10: Report / Review

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 88 | Report | Nộp báo cáo tiến độ | STUDENT | Upload file (bắt buộc) + nội dung 5 mục, version tự tăng | ✅ Đã triển khai | POST /reports (multipart) |
| 89 | Report | Cập nhật/thay thế báo cáo | STUDENT | Sửa nội dung/file báo cáo đang chờ review | ✅ Đã triển khai | PATCH /reports/{id}/replace |
| 90 | Report | Xem báo cáo theo milestone | MANAGER / STUDENT | Lịch sử báo cáo trong 1 mốc | ✅ Đã triển khai | GET /milestones/{id}/reports |
| 91 | Report | Xem báo cáo của tôi theo milestone | STUDENT | Báo cáo SV hiện tại trong mốc | ✅ Đã triển khai | GET /milestones/{id}/reports/me |
| 92 | Report | Xem báo cáo theo task | MANAGER / STUDENT | Lịch sử version báo cáo theo task | ✅ Đã triển khai | GET /tasks/{id}/reports |
| 93 | Report | Xem báo cáo theo nhóm | MANAGER / STUDENT (Leader) | Toàn bộ báo cáo trong nhóm | ✅ Đã triển khai | GET /groups/{id}/reports |
| 94 | Report | Xem báo cáo của tôi theo nhóm | STUDENT (Member) | Chỉ báo cáo SV hiện tại | ✅ Đã triển khai | GET /groups/{id}/reports/me |
| 95 | Report | Download file báo cáo | MANAGER / STUDENT | Tải file đính kèm báo cáo | ✅ Đã triển khai | GET /reports/{id}/file (streaming) |
| 96 | Report | Xem báo cáo chờ Manager duyệt | LAB_MANAGER | Danh sách LEADER_REVIEWED trong PTN | ✅ Đã triển khai | GET /labs/{id}/reports/pending-review |
| 97 | Review | Leader review báo cáo (cấp 1) | STUDENT (Leader) | ACCEPT → LEADER_REVIEWED / NEEDS_REVISION / REJECT → LEADER_REJECTED | ✅ Đã triển khai | PATCH /reports/{id}/leader-review |
| 98 | Review | Manager review báo cáo (cấp 2) | LAB_MANAGER | APPROVE → APPROVED / REJECT → MANAGER_REJECTED | ✅ Đã triển khai | PATCH /reports/{id}/manager-review |

---

## Module 11: Product / Evaluation

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 99 | Product | Nộp sản phẩm NC | STUDENT | Upload file + external link, chọn loại (8 types) | ✅ Đã triển khai | POST /products (multipart) |
| 100 | Product | Xem sản phẩm theo project | MANAGER / STUDENT | Danh sách sản phẩm dự án | ✅ Đã triển khai | GET /projects/{id}/products |
| 101 | Product | Xem sản phẩm theo nhóm | MANAGER / STUDENT | Sản phẩm nhóm NC | ✅ Đã triển khai | GET /research-groups/{id}/products |
| 102 | Evaluation | Đánh giá SV (5 tiêu chí) | LAB_MANAGER | Contribution, Task, Report, Product, Attitude + totalScore + comment | ✅ Đã triển khai | POST /evaluations |
| 103 | Evaluation | Xem đánh giá theo project | MANAGER / STUDENT | Manager: toàn bộ; SV: chỉ đánh giá mình | ✅ Đã triển khai | GET /projects/{id}/evaluations |
| 104 | Evaluation | Xem đánh giá theo nhóm | MANAGER / STUDENT | Đánh giá trong nhóm NC | ✅ Đã triển khai | GET /research-groups/{id}/evaluations |

---

## Module 12: Research Log

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 105 | Research Log | Tạo nhật ký NC | MANAGER / STUDENT | workDate, duration, content, result, problem, nextPlan | ✅ Đã triển khai | POST /logs |
| 106 | Research Log | Xem nhật ký theo project | MANAGER / STUDENT | Filter: group, milestone, task, author, type + phân trang | ✅ Đã triển khai | GET /projects/{id}/logs |
| 107 | Research Log | Xem nhật ký theo nhóm | MANAGER / STUDENT | Filter + phân trang | ✅ Đã triển khai | GET /research-groups/{id}/logs |

---

## Module 13: Dashboard

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 108 | Dashboard | Admin dashboard | ADMIN | Tổng user, PTN, thống kê hệ thống | ✅ Đã triển khai | GET /admin/dashboard/stats |
| 109 | Dashboard | Lab dashboard (Manager) | LAB_MANAGER | Thống kê PTN: members, bookings, vi phạm | ✅ Đã triển khai | GET /labs/{id}/dashboard/stats |
| 110 | Dashboard | Research stats | MANAGER / STUDENT | Thống kê tổng quan dự án NC | ✅ Đã triển khai | GET /projects/{id}/stats?type=overview |
| 111 | Dashboard | Student dashboard | STUDENT | Trang chủ sau đăng nhập | 🔧 Đang hoàn thiện | DashboardPlaceholder.tsx — chưa có nội dung |

---

## Module 14: System Config / Audit Logs

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 112 | System Config | Xem cấu hình hệ thống | ADMIN | Danh sách key-value config | ✅ Đã triển khai | GET /admin/system-config |
| 113 | System Config | Cập nhật cấu hình | ADMIN | Thay đổi value, tự ghi system_audit_logs (old/new value) | ✅ Đã triển khai | PUT /admin/system-config |
| 114 | Audit Log | Xem nhật ký kiểm toán | ADMIN | Tra cứu audit log: user, action, module, details, timestamp | ✅ Đã triển khai | GET /admin/audit-logs |

---

## Module 15: Docker / DevOps

| STT | Module | Chức năng | Tác nhân | Mô tả ngắn | Trạng thái | Ghi chú |
|:---:|--------|-----------|----------|-------------|:----------:|---------|
| 115 | Docker | MySQL container | Dev team | MySQL 8.0, port 3306, volume persist, UTF8MB4 | ✅ Đã triển khai | docker-compose.yml |
| 116 | Docker | Redis container | Dev team | Redis 7 Alpine, port 6379, OTP cache | ✅ Đã triển khai | docker-compose.yml |
| 117 | Docker | Frontend Admin container | Dev team | Vite dev server, port 5173, dark theme | ✅ Đã triển khai | docker-compose.yml |
| 118 | Docker | Frontend Manager container | Dev team | Vite dev server, port 5174 | ✅ Đã triển khai | docker-compose.yml |
| 119 | Docker | Frontend User container | Dev team | Vite dev server, port 5175 | ✅ Đã triển khai | docker-compose.yml |
| 120 | Docker | Backend Dockerfile | Dev team | Container hóa Spring Boot | 🔮 Dự kiến mở rộng | Hiện chạy ngoài Docker bằng Maven |
| 121 | DevOps | CI/CD pipeline | Dev team | Auto build, test, deploy | 🔮 Dự kiến mở rộng | GitHub Actions / GitLab CI |
| 122 | DevOps | Unit + Integration testing | Dev team | Test coverage cho service + API | 🔮 Dự kiến mở rộng | Dependency có, chưa viết test |
| 123 | DevOps | Cloud deployment | Dev team | Triển khai production (Kubernetes/VPS) | 🔮 Dự kiến mở rộng | Dành cho KLTN |

---

## Tổng kết trạng thái

| Trạng thái | Số lượng | Tỷ lệ |
|------------|:--------:|:------:|
| ✅ Đã triển khai | 114 | 92.7% |
| 🔧 Đang hoàn thiện | 3 | 2.4% |
| 📦 Mock data | 0 | 0% |
| 🔮 Dự kiến mở rộng | 4 | 3.3% |
| **Tổng** | **123** | **100%** |

> **Ghi chú:**
> - Seed data (V24, V25) có trong Flyway migration nhưng đó là dữ liệu thử nghiệm cho môi trường dev, không phải mock của chức năng.
> - 3 chức năng "Đang hoàn thiện" là: updateLab (placeholder), deleteLab (placeholder), Student dashboard (placeholder UI).
> - 4 chức năng "Dự kiến mở rộng" thuộc nhóm DevOps, dành cho khóa luận tốt nghiệp.
