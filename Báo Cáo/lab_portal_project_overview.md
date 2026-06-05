# Project Overview — Lab Portal

> **Mục đích tài liệu:** Giúp người viết báo cáo nắm nhanh toàn bộ dự án trước khi viết báo cáo chính thức.
> **Nguồn dữ liệu:** Trích xuất trực tiếp từ source code backend (Spring Boot) và frontend (React + TypeScript).
> **Quy ước:** Mục nào ghi *⚠ Chưa hoàn thiện* nghĩa là chức năng tồn tại trong code nhưng chưa có logic đầy đủ.

---

## 1. Tên dự án

**Lab Portal** — Hệ thống quản lý phòng thí nghiệm và hoạt động nghiên cứu khoa học cho sinh viên.

- Repository: `Lab-Portal`
- Backend: `server/` (Spring Boot 3.4.5, Java 17)
- Frontend: `client/` (React 18, TypeScript, Vite 6)
- Infrastructure: `docker-compose.yml` (Redis, MySQL, 3 frontend instances)

---

## 2. Mục tiêu dự án

| # | Mục tiêu | Loại |
|---|----------|------|
| 1 | Xây dựng nền tảng web tập trung cho quản lý phòng thí nghiệm (PTN) | Nghiệp vụ |
| 2 | Số hóa quy trình ứng tuyển – duyệt – quản lý thành viên PTN | Nghiệp vụ |
| 3 | Tự động hóa đăng ký sử dụng PTN: slot, booking, check-in QR, waitlist | Nghiệp vụ |
| 4 | Quản lý vệ sinh, vi phạm, khiếu nại có lịch sử truy xuất | Nghiệp vụ |
| 5 | Quản lý toàn bộ vòng đời NCKH theo nhóm: đề tài → nhóm → dự án → milestone → task → report → đánh giá | Nghiệp vụ |
| 6 | Xây dựng quy trình duyệt báo cáo 2 cấp (Leader → Manager) có version | Nghiệp vụ |
| 7 | Triển khai REST API với phân quyền RBAC nhiều cấp | Kỹ thuật |
| 8 | Xây dựng frontend SPA responsive, phân giao diện theo role | Kỹ thuật |
| 9 | Docker hóa môi trường phát triển | Kỹ thuật |
| 10 | Tạo tiền đề vững chắc cho khóa luận tốt nghiệp | Chiến lược |

---

## 3. Bài toán giải quyết

### 3.1. Vấn đề 1 — Vận hành PTN thủ công

| Vấn đề | Hệ quả | Lab Portal giải quyết |
|--------|--------|----------------------|
| Đăng ký sử dụng qua giấy/email | Xung đột lịch, không kiểm soát số lượng | Slot + Booking + Waitlist tự động |
| Điểm danh bằng danh sách giấy | Sai sót, không truy xuất | Check-in QR code 2 chiều |
| Phân công vệ sinh bằng miệng | Không minh bạch | Cleaning task + trạng thái theo dõi |
| Vi phạm ghi sổ tay | Không lưu trữ, không tra cứu | Penalty entity + history |
| Khiếu nại không có kênh chính thức | Thiếu công bằng | Complaint entity liên kết penalty |

### 3.2. Vấn đề 2 — Quản lý thành viên rời rạc

| Vấn đề | Lab Portal giải quyết |
|--------|----------------------|
| Đơn ứng tuyển qua email, dễ thất lạc | Application entity + trạng thái PENDING → APPROVED/REJECTED |
| Không có cơ sở dữ liệu thành viên | Membership entity (active/inactive) |
| Không biết ai đang thuộc PTN nào | Danh sách thành viên theo PTN, xem/xóa |

### 3.3. Vấn đề 3 — NCKH thiếu công cụ

| Vấn đề | Lab Portal giải quyết |
|--------|----------------------|
| Phân công qua tin nhắn | Task entity gán assignee, trạng thái Kanban |
| Báo cáo nộp email, mất version | Report entity + auto version (v1, v2, v3…) |
| Không có quy trình duyệt rõ ràng | Review 2 cấp: Leader → Manager |
| Đánh giá chủ quan | Evaluation 5 tiêu chí + nhận xét |

---

## 4. Đối tượng sử dụng

### 4.1. Role hệ thống (lưu trong bảng `roles`)

| Role | Tên hiển thị | Mô tả | Giao diện |
|------|-------------|-------|-----------|
| `ADMIN` | Quản trị viên | Quản lý toàn hệ thống: user, PTN, config, audit | Admin Portal (:5173), dark theme |
| `LAB_MANAGER` | Quản lý PTN | Vận hành 1 PTN được gán: duyệt, slot, check-in, NCKH | Manager Portal (:5174) |
| `STUDENT` | Sinh viên | Sử dụng PTN, tham gia NCKH | User Portal (:5175) |

### 4.2. Group role (lưu trong bảng `group_members`)

| Group Role | Mô tả | Phạm vi |
|------------|-------|---------|
| `LEADER` | Trưởng nhóm NC — MEMBER + duyệt báo cáo cấp 1, xem toàn nhóm | Trong 1 nhóm NC cụ thể |
| `MEMBER` | Thành viên nhóm NC — thực hiện task, nộp report | Trong 1 nhóm NC cụ thể |

> Một SV có thể là Leader ở nhóm A, Member ở nhóm B — group role không phải system role.

---

## 5. Kiến trúc tổng quan

```
┌─────────────────────────────────────────────────────────┐
│                     FRONTEND (React 18 + TS)            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐    │
│  │ Admin Portal │ │Manager Portal│ │ User Portal  │    │
│  │    :5173     │ │    :5174     │ │    :5175     │    │
│  │  (dark theme)│ │              │ │              │    │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘    │
└─────────┼────────────────┼────────────────┼────────────┘
          │    REST API (JSON, JWT Bearer)   │
          ▼                ▼                ▼
┌─────────────────────────────────────────────────────────┐
│              BACKEND (Spring Boot 3.4.5)                │
│                    :8080 /api                           │
│  ┌──────┐ ┌─────┐ ┌───────┐ ┌────────┐ ┌──────┐      │
│  │ Auth │ │ Lab │ │Booking│ │Research│ │Admin │      │
│  └──┬───┘ └──┬──┘ └───┬───┘ └───┬────┘ └──┬───┘      │
│     │Spring Security (JWT + RBAC)│          │          │
│     │     Flyway Migration       │          │          │
└─────┼────────┼───────┼───────────┼──────────┼──────────┘
      │        │       │           │          │
      ▼        ▼       ▼           ▼          ▼
┌──────────┐ ┌──────────┐ ┌─────────────┐
│ MySQL 8  │ │ Redis 7  │ │ Gmail SMTP  │
│ lab_portal│ │OTP cache │ │ Email notif │
│  :3306   │ │  :6379   │ │  :587 TLS   │
└──────────┘ └──────────┘ └─────────────┘
```

**Mô hình:** Client–Server, Backend là **Modular Monolith** (package-by-feature).

---

## 6. Công nghệ sử dụng

### 6.1. Backend

| Công nghệ | Phiên bản | Vai trò |
|-----------|-----------|---------|
| Java | 17 | Ngôn ngữ chính |
| Spring Boot | 3.4.5 | Application framework |
| Spring Security 6 | — | Authentication + Authorization |
| Spring Data JPA | — | ORM (Hibernate) |
| Spring Data Redis | — | OTP cache |
| Spring Mail | — | SMTP email |
| Flyway | — | Database migration (53 files) |
| jjwt | 0.12.6 | JWT token generation/validation |
| SpringDoc OpenAPI | 2.8.6 | Swagger UI documentation |
| Lombok | — | Boilerplate reduction |
| Maven | — | Build tool |

### 6.2. Frontend

| Công nghệ | Phiên bản | Vai trò |
|-----------|-----------|---------|
| React | 18 | UI framework |
| TypeScript | 5.x | Static typing |
| Vite | 6 | Build tool + dev server |
| React Router | 6 | Client-side routing |
| TanStack React Query | 5 | Server state management |
| Axios | — | HTTP client + interceptor |
| React Hook Form + Zod | — | Form validation |
| Tailwind CSS | 3 | Styling |
| Lucide React | — | Icon library |
| Inter (via @fontsource) | — | Typography |

### 6.3. Hạ tầng

| Thành phần | Phiên bản | Port | Ghi chú |
|-----------|-----------|------|---------|
| MySQL | 8.0 | 3306 | Docker container, volume persist, UTF8MB4 |
| Redis | 7 Alpine | 6379 | Docker container |
| Frontend Admin | Vite dev | 5173 | Docker container |
| Frontend Manager | Vite dev | 5174 | Docker container |
| Frontend User | Vite dev | 5175 | Docker container |
| Backend | Spring Boot | 8080 | Chạy ngoài Docker (`mvn spring-boot:run`) |

---

## 7. Danh sách module

| # | Module | Backend Package | Frontend Path | Mô tả ngắn |
|---|--------|----------------|---------------|-------------|
| 1 | Auth | `auth/` | `modules/auth/` | Đăng ký, đăng nhập, OTP, JWT, profile |
| 2 | Admin User | `auth/controller/AdminUserController` | `modules/admin/` | Quản lý user, ban/unban, đổi role |
| 3 | Admin Dashboard | `admin/dashboard/` | `modules/admin/` | Thống kê hệ thống |
| 4 | Admin Lab | `admin/lab/` | `modules/admin/` | Tạo PTN, gán manager (admin) |
| 5 | Admin Config | `admin/systemconfig/` | `modules/admin/` | Cấu hình key-value |
| 6 | Admin Audit | `admin/audit/` | `modules/admin/` | Nhật ký kiểm toán |
| 7 | Laboratory | `lab/` | `modules/lab/` | Danh sách PTN, chi tiết, thành viên |
| 8 | Application | `lab/controller/ApplicationController` | `modules/application/` | Ứng tuyển PTN, duyệt đơn |
| 9 | Time Slot | `booking/controller/TimeSlotController` | `modules/booking/` | Tạo/quản lý khung giờ |
| 10 | Booking | `booking/controller/BookingController` | `modules/booking/` | Đăng ký/hủy/duyệt booking |
| 11 | Check-in | `booking/controller/CheckinController` | `modules/booking/` | QR tạo + xác nhận |
| 12 | Cleaning | `booking/controller/CleaningController` | `modules/lab/` | Phân công/hoàn thành vệ sinh |
| 13 | Penalty | `booking/controller/PenaltyController` | `modules/penalty/` | Ghi nhận vi phạm |
| 14 | Complaint | `booking/controller/ComplaintController` | `modules/penalty/` | Khiếu nại vi phạm |
| 15 | Research Topic | `research/controller/ResearchTopicController` | `modules/research/` | Đề tài NC |
| 16 | Research Group | `research/controller/GroupController` | `modules/research/` | Nhóm NC, thành viên |
| 17 | Research Project | `research/controller/ProjectController` | `modules/research/` | Dự án NC |
| 18 | Milestone | `research/controller/MilestoneController` | `modules/research/` | Mốc tiến độ |
| 19 | Task | `research/controller/TaskController` | `modules/research/` | Nhiệm vụ (Kanban) |
| 20 | Report | `research/controller/ReportController` | `modules/research/` | Báo cáo (version + review 2 cấp) |
| 21 | Product | `research/controller/ProductController` | `modules/research/` | Sản phẩm NC |
| 22 | Evaluation | `research/controller/EvaluationController` | `modules/research/` | Đánh giá SV (5 tiêu chí) |
| 23 | Research Log | `research/controller/ResearchLogController` | `modules/research/` | Nhật ký NC |
| 24 | Research Stats | `research/controller/StatsController` | `modules/research/` | Thống kê dự án |
| 25 | Lab Dashboard | `lab/controller/LaboratoryController` | `modules/lab/` | Dashboard PTN (Manager) |

---

## 8. Mô tả từng module

### 8.1. Auth — Xác thực & Tài khoản

**Đăng ký:** 3 bước — email → OTP (mã hóa BCrypt, lưu Redis có TTL) → xác minh → temp token → điền thông tin → tạo STUDENT.

**Đăng nhập:** Email/username + password → DaoAuthenticationProvider → BCrypt verify → JWT access token (24h) + refresh token (7 ngày).

**Quên mật khẩu:** Gửi OTP → xác minh → cấp reset token → đặt mật khẩu mới.

**Profile:** Xem/cập nhật họ tên, phone.

**Frontend:** Lưu token vào localStorage. Axios interceptor tự gắn `Authorization: Bearer {token}` vào mọi request. Tự redirect khi 401.

### 8.2. Admin — Quản trị hệ thống

- **Dashboard:** `GET /admin/dashboard/stats` → tổng user, PTN, booking theo trạng thái.
- **User Management:** Danh sách (lọc bỏ ADMIN), ban/unban, đổi role (STUDENT ↔ LAB_MANAGER).
- **Lab Management:** Tạo PTN (tên, vị trí, capacity), gán manager, cập nhật trạng thái.
- **System Config:** Key-value config, mọi thay đổi ghi system_audit_logs.
- **Audit Log:** Tra cứu nhật ký kiểm toán (user, action, module, details, timestamp).

**Giao diện:** AdminLayout riêng, dark theme, sidebar 5 mục.

### 8.3. Laboratory & Application — PTN & Ứng tuyển

**PTN:** Danh sách, chi tiết, trạng thái (AVAILABLE / UNDER_MAINTENANCE / CLOSED).

**Ứng tuyển:** SV nộp đơn (upload CV file hoặc URL) → PENDING → Manager duyệt:
- APPROVED → tạo Membership (active=true) → SV truy cập tính năng PTN.
- REJECTED → SV có thể nộp lại (V26 migration cho phép re-apply).

**Thành viên:** Manager xem danh sách active members, xóa thành viên (set active=false, không xóa user).

> **Ràng buộc:** Không nộp đơn trùng (DuplicateApplicationException). Manager chỉ duyệt đơn PTN mình quản lý.

### 8.4. Slot / Booking / Check-in — Sử dụng PTN

**Time Slot:** Manager tạo (lab, date, start/end time, capacity). Trạng thái: AVAILABLE → FULL / CANCELLED / COMPLETED.

**Booking:** SV đăng ký → PENDING_APPROVAL → Manager duyệt → APPROVED → check-in → CHECKED_IN → COMPLETED.

- **Hủy:** SV hủy (CANCELLED_BY_STUDENT) hoặc Manager hủy slot (CANCELLED_BY_MANAGER + email thông báo).
- **Waitlist:** Slot đầy → SV tự động vào hàng đợi. Position dùng **pessimistic locking** để đảm bảo thứ tự khi nhiều người đăng ký đồng thời. Khi có chỗ trống → promote tự động.
- **Automation:** `@EnableScheduling` chạy cron mỗi phút — auto no-show với grace period.

**Check-in QR:** SV tạo QR token (short-lived) → Manager quét/nhập token → xác nhận CHECKED_IN.

### 8.5. Cleaning — Vệ sinh PTN

Manager chọn slot đã kết thúc → xem danh sách SV đủ điều kiện (đã check-in) → phân công → PENDING → SV xác nhận hoàn thành → COMPLETED. Manager có thể hủy (CANCELLED).

### 8.6. Penalty & Complaint — Vi phạm & Khiếu nại

**Penalty:** Manager ghi nhận — chọn SV, loại vi phạm (NO_SHOW, LATE_CHECKIN, EQUIPMENT_DAMAGE, NOISE, OTHER), điểm phạt, lý do. Trạng thái: ACTIVE / RESOLVED / CANCELLED.

**Complaint:** SV nộp khiếu nại liên kết penalty_id → PENDING → Manager xem xét → RESOLVED (ghi resolution_note) / REJECTED.

### 8.7. Research Topic — Đề tài NC

Manager tạo đề tài thuộc PTN: tên, mô tả, yêu cầu, tài liệu tham khảo. Trạng thái: RECRUITING → ACTIVE → CLOSED.

### 8.8. Research Group — Nhóm NC

Manager tạo nhóm gắn với PTN/đề tài/dự án. Thêm SV từ danh sách thành viên PTN, chỉ định 1 Leader (bắt buộc). Trạng thái: ACTIVE / INACTIVE / COMPLETED.

### 8.9. Research Project — Dự án NC

Liên kết PTN + nhóm + đề tài. Bao gồm: code, title, description, objective, research direction, start/end date, priority (LOW/MEDIUM/HIGH), required products, evaluation criteria.

Trạng thái: DRAFT → IN_PROGRESS → COMPLETED / CANCELLED.

### 8.10. Milestone — Mốc tiến độ

Thuộc project, gán cho group. Thông tin: title, description, start date, deadline, progress (%).

Trạng thái: NOT_STARTED → IN_PROGRESS → WAITING_REVIEW → COMPLETED / OVERDUE / CANCELLED.

Manager có thể ghi manager_comment và evidence_url.

### 8.11. Task — Nhiệm vụ

Thuộc milestone, gán cho 1 SV (assignee_id). Trạng thái dạng **Kanban**:

```
TODO → DOING → WAITING_REVIEW → NEEDS_REVISION → DONE
                                                   ↑
                                              OVERDUE / CANCELLED
```

Frontend hiển thị dạng TaskBoard (cột theo trạng thái) + TaskCard.

### 8.12. Report — Báo cáo tiến độ ⭐

Đây là module phức tạp nhất — **review 2 cấp + versioning**.

**Nội dung báo cáo:**
- `contentDone` — Đã thực hiện
- `result` — Kết quả
- `difficulty` — Khó khăn
- `nextPlan` — Kế hoạch tiếp
- `selfAssessment` — Tự đánh giá
- File đính kèm (bắt buộc: URL, tên, loại, kích thước)
- Evidence link (tùy chọn)
- Version (tự tăng, unique constraint: task_id + submitted_by_id + version)

**Luồng trạng thái:**

```
SUBMITTED → (Leader review) → LEADER_REVIEWED → (Manager review) → APPROVED
    ↑                              │                                    │
    │                        NEEDS_REVISION                      MANAGER_REJECTED
    │                              │                                    │
    └──────── (SV nộp lại, version+1) ─────────────────────────────────┘
```

Quyết định Leader: ACCEPT / NEEDS_REVISION / REJECT (→ LEADER_REJECTED).
Quyết định Manager: APPROVE / REJECT (→ MANAGER_REJECTED).

> **Đặc biệt:** Leader nộp report → bỏ qua bước Leader review → thẳng lên Manager.

### 8.13. Product — Sản phẩm NC

SV nộp sản phẩm thuộc project/group. Upload file + external link.

Loại: FINAL_REPORT, SLIDE, SOURCE_CODE, DATASET, DEMO_VIDEO, PAPER, SOFTWARE_DEMO, OTHER.

Trạng thái: SUBMITTED → APPROVED / REJECTED.

### 8.14. Evaluation — Đánh giá SV

Manager đánh giá từng SV theo **5 tiêu chí** (BigDecimal):

| Tiêu chí | Ý nghĩa |
|----------|---------|
| `contributionScore` | Mức đóng góp |
| `taskScore` | Hoàn thành nhiệm vụ |
| `reportScore` | Chất lượng báo cáo |
| `productScore` | Sản phẩm đầu ra |
| `attitudeScore` | Thái độ, kỷ luật |

Tổng điểm (`totalScore`) tự tính. Kèm `lecturerComment`.

### 8.15. Research Log — Nhật ký NC

SV ghi nhật ký hàng ngày: workDate, durationMinutes, content, result, problem, nextPlan.

Loại: MANUAL / AUTO. Visibility: GROUP / PROJECT / PRIVATE.

Hỗ trợ filter: groupId, milestoneId, taskId, authorId, logType + phân trang.

### 8.16. Research Stats — Thống kê

`GET /projects/{id}/stats?type=overview` → ProjectStatsOverviewResponse (tổng quan tiến độ dự án).

### 8.17. Lab Dashboard — Tổng quan PTN

Manager xem thống kê PTN: `GET /labs/{id}/dashboard/stats` → số thành viên, booking, vi phạm…

---

## 9. Luồng nghiệp vụ chính

### Luồng 1: Đăng ký → Gia nhập PTN → Sử dụng

```
SV đăng ký tài khoản (email OTP 3 bước)
  → Đăng nhập
  → Xem danh sách PTN
  → Nộp đơn ứng tuyển (CV file/URL)
  → [PENDING] Manager duyệt
  → [APPROVED] Tạo Membership
  → SV truy cập tính năng PTN
```

### Luồng 2: Đăng ký sử dụng PTN

```
Manager tạo Time Slot (ngày, giờ, capacity)
  → SV đăng ký booking
  → [PENDING_APPROVAL]
  → Manager duyệt → [APPROVED]
  → SV tạo QR token
  → Manager quét QR → [CHECKED_IN]
  → Ca kết thúc → [COMPLETED]

Ngoại lệ:
  - Slot đầy → Waitlist (pessimistic lock) → auto promote khi trống
  - SV hủy → CANCELLED_BY_STUDENT
  - Manager hủy slot → CANCELLED_BY_MANAGER + email
  - Không check-in → auto NO_SHOW (scheduled task)
```

### Luồng 3: Vệ sinh → Vi phạm → Khiếu nại

```
Slot kết thúc
  → Manager phân công vệ sinh (chọn SV đã check-in)
  → [PENDING] SV hoàn thành → [COMPLETED]

Vi phạm:
  Manager ghi nhận → [ACTIVE]
  → SV xem → gửi khiếu nại → [PENDING]
  → Manager xem xét → [RESOLVED/REJECTED]
```

---

## 10. Luồng NCKH chi tiết

### 10.1. Chuỗi tổng quát

```
1. Manager tạo Đề tài (Research Topic)
     ↓
2. Manager tạo Nhóm NC (Research Group)
   - Thêm SV, chỉ định Leader
     ↓
3. Manager tạo Dự án NC (Research Project)
   - Gắn nhóm + đề tài
     ↓
4. Manager tạo Mốc tiến độ (Milestone)
   - Deadline, gán nhóm
     ↓
5. Manager tạo Nhiệm vụ (Task)
   - Gán cho từng SV (assignee)
     ↓
6. SV thực hiện Task (TODO → DOING)
     ↓
7. SV nộp Báo cáo (Report) — kèm file
     ↓
8. Leader duyệt cấp 1 (Leader Review)
     ↓
9. Manager duyệt cấp 2 (Manager Review)
     ↓
10. SV nộp Sản phẩm (Product) — file/link
     ↓
11. Manager Đánh giá SV (Evaluation) — 5 tiêu chí
```

Song song: SV ghi **Nhật ký NC** (Research Log) hàng ngày.

### 10.2. Luồng báo cáo chi tiết (quan trọng nhất)

```
Bước 1: Task ở trạng thái TODO
           ↓
Bước 2: SV bắt đầu → Task chuyển DOING
           ↓
Bước 3: SV nộp báo cáo v1
         - Điền: contentDone, result, difficulty, nextPlan, selfAssessment
         - Upload file (bắt buộc)
         - Version = 1, Status = SUBMITTED
           ↓
Bước 4: Leader review
         ├── ACCEPT      → Status = LEADER_REVIEWED → Bước 5
         ├── NEEDS_REVISION → Status = NEEDS_REVISION → SV sửa → Bước 3 (v2)
         └── REJECT      → Status = LEADER_REJECTED → SV nộp mới → Bước 3 (v2)
           ↓
Bước 5: Manager review
         ├── APPROVE → Status = APPROVED → Task có thể chuyển DONE
         └── REJECT  → Status = MANAGER_REJECTED → SV nộp mới → Bước 3 (v3)

⚠ Đặc biệt: Leader nộp report → Bước 4 bị bỏ qua → thẳng Bước 5
```

---

## 11. Phân quyền hệ thống

### 11.1. Cơ chế kỹ thuật

| Tầng | Cơ chế | Mô tả |
|------|--------|-------|
| API Gateway | `JwtAuthenticationFilter` | Trích JWT từ header, validate, set SecurityContext |
| Endpoint | `@PreAuthorize("hasRole('...')")` | Spring Method Security trên từng controller method |
| Logic | `assertCanManageLab()` | Kiểm tra Manager có quản lý PTN này không |
| Frontend | `ProtectedRoute` | Redirect nếu chưa login hoặc sai role |
| Frontend | `RoleBasedRoute` | Cho phép truy cập theo danh sách role |
| Frontend | `ActiveMembershipRoute` | Chặn nếu SV chưa có membership active |
| Frontend | Menu visibility | navItems thay đổi theo role và membership |

### 11.2. Ma trận phân quyền (trích)

| Chức năng | ADMIN | MANAGER | STUDENT | LEADER |
|-----------|:-----:|:-------:|:-------:|:------:|
| Admin Dashboard | ✅ | — | — | — |
| Quản lý User / Role | ✅ | — | — | — |
| Tạo PTN / Gán Manager | ✅ | — | — | — |
| System Config / Audit | ✅ | — | — | — |
| Dashboard PTN | — | ✅ own | — | — |
| Duyệt ứng tuyển | — | ✅ own | — | — |
| Tạo Slot / Duyệt Booking | — | ✅ own | — | — |
| Check-in (quét QR) | — | ✅ | — | — |
| Phân công vệ sinh | — | ✅ own | — | — |
| Ghi nhận vi phạm | — | ✅ | — | — |
| Xử lý khiếu nại | — | ✅ own | — | — |
| Tạo đề tài/nhóm/dự án/milestone/task | — | ✅ | — | — |
| Manager review báo cáo | — | ✅ | — | — |
| Đánh giá SV | — | ✅ | — | — |
| Ứng tuyển PTN | — | — | ✅ | ✅ |
| Booking + Check-in QR (tạo) | — | — | ✅ | ✅ |
| Nộp khiếu nại | — | — | ✅ | ✅ |
| Cập nhật task status | — | — | ✅ | ✅ |
| Nộp báo cáo / sản phẩm | — | — | ✅ | ✅ |
| Xem task/report **chỉ của mình** | — | — | ✅ | — |
| Xem task/report **toàn nhóm** | — | — | — | ✅ |
| Leader review báo cáo | — | — | — | ✅ |
| Ghi nhật ký NC | — | — | ✅ | ✅ |

> `✅ own` = Chỉ trên PTN mình quản lý.

---

## 12. Cơ sở dữ liệu chính

### 12.1. Tổng quan

- **DBMS:** MySQL 8.0, charset `utf8mb4_unicode_ci`.
- **Migration:** Flyway — 53 files (V1 → V53).
- **Hibernate:** `ddl-auto: validate` (chỉ kiểm tra, không tự tạo).
- **Pattern:** Soft-delete — tất cả entity kế thừa `BaseEntity` (id, createdAt, updatedAt, active, deleted).
- **Connection Pool:** HikariCP, max 10.

### 12.2. Danh sách bảng (30+)

**Module Auth (4 bảng):**
| Bảng | Khóa chính | Vai trò |
|------|-----------|---------|
| `users` | id | Tài khoản (username UK, email UK, password BCrypt) |
| `roles` | id | Vai trò hệ thống (name UK: ADMIN, LAB_MANAGER, STUDENT) |
| `user_roles` | user_id + role_id | M:N User ↔ Role |
| `verification_codes` | id | OTP hash + expiry |

**Module Lab (3 bảng):**
| Bảng | Khóa chính | Vai trò |
|------|-----------|---------|
| `laboratories` | id | PTN (lab_name UK, status, manager_id FK→users) |
| `applications` | id | Đơn ứng tuyển (user_id, lab_id, status, cv_url/file) |
| `memberships` | id | Tư cách thành viên (user_id, lab_id, role, active) |

**Module Booking (6 bảng):**
| Bảng | Khóa chính | Vai trò |
|------|-----------|---------|
| `time_slots` | id | Khung giờ (lab_id, date, start/end, capacity, status) |
| `bookings` | id | Đăng ký (student_id, slot_id, status, checked_in_at) |
| `waitlists` | id | Hàng đợi (student_id, slot_id, position, status) |
| `cleanings` | id | Vệ sinh (slot_id, assigned_to, status) |
| `penalties` | id | Vi phạm (student_id, penalty_type, point, reason) |
| `complaints` | id | Khiếu nại (user_id, penalty_id, status, resolution_note) |

**Module Research (12 bảng):**
| Bảng | Khóa chính | Vai trò |
|------|-----------|---------|
| `research_topics` | id | Đề tài (lab_id, name, status) |
| `research_groups` | id | Nhóm NC (lab_id, topic_id, project_id, leader_id) |
| `group_members` | id | Thành viên nhóm (group_id, user_id, role: LEADER/MEMBER) |
| `projects` | id | Dự án (code, title, status, priority, dates) |
| `milestones` | id | Mốc (project_id, group_id, deadline, progress, status) |
| `tasks` | id | Nhiệm vụ (milestone_id, assignee_id, status, deadline) |
| `reports` | id | Báo cáo (task_id, submitted_by_id, version, status, file) |
| `comments` | id | Bình luận (report_id, author_id, content) |
| `products` | id | Sản phẩm (project_id, group_id, product_type, file/link) |
| `evaluations` | id | Đánh giá (project_id, student_id, 5 scores + comment) |
| `research_logs` | id | Nhật ký (project_id, author_id, work_date, duration) |
| `project_logs` | id | Activity log (project_id, user_id, action, details) |

**Module Admin (3 bảng):**
| Bảng | Khóa chính | Vai trò |
|------|-----------|---------|
| `system_configs` | id | Cấu hình key-value |
| `system_audit_logs` | id | Lịch sử thay đổi config |
| `audit_logs` | id | Nhật ký kiểm toán hệ thống |

> ⚠ **Entity trùng lặp:** Tồn tại `ProjectEntity` (bảng `projects`) và `ResearchProject` (bảng `research_projects`) — có thể do refactor chưa hoàn tất.

---

## 13. API chính

**Tổng:** ~127 endpoints RESTful, prefix `/api`, Swagger UI tại `/api/swagger-ui.html`.

### 13.1. Thống kê theo module

| Module | Số endpoint | Method chính |
|--------|:-----------:|-------------|
| Auth | 14 | POST login/register, GET me |
| Admin User | 6 | GET/PUT/PATCH users |
| Admin System | 4 | GET/PUT config, GET audit |
| Laboratory | 13 | POST/GET/PUT/PATCH/DELETE labs |
| Application | 6 | POST apply, PUT review, GET list |
| TimeSlot | 6 | POST/GET/PATCH/DELETE slots |
| Booking | 6 | POST/GET/PATCH bookings |
| Check-in | 2 | POST qr, POST confirm |
| Cleaning | 9 | POST assign, PATCH complete/cancel |
| Penalty | 5 | POST/GET penalties |
| Complaint | 3 | POST/GET/PATCH complaints |
| Research Topic | 2 | POST/GET topics |
| Research Group | 10 | POST/PUT/GET groups, members |
| Research Project | 7 | POST/PUT/GET projects |
| Milestone | 7 | POST/PUT/GET milestones |
| Task | 6 | POST/GET/PUT tasks |
| Report | 11 | POST/GET/PATCH reports, review |
| Product | 3 | POST/GET products |
| Evaluation | 3 | POST/GET evaluations |
| Research Log | 3 | POST/GET logs |
| Research Stats | 1 | GET stats |
| **Tổng** | **~127** | |

### 13.2. Response format chuẩn

```json
{
  "status": true,
  "message": "Thao tác thành công",
  "data": { ... }
}
```

Lỗi: `status: false`, message mô tả lỗi, data: null.

---

## 14. Điểm nổi bật

| # | Điểm nổi bật | Chi tiết |
|---|-------------|----------|
| 1 | **Phân quyền RBAC nhiều cấp** | 3 system roles + 2 group roles. @PreAuthorize + logic check sở hữu PTN. Frontend route guard + menu visibility. |
| 2 | **Review báo cáo 2 cấp** | Leader review (cấp 1) → Manager review (cấp 2). Leader không tự duyệt mình. |
| 3 | **Versioning báo cáo** | Mỗi lần nộp lại tạo version mới. Unique constraint (task_id, submitted_by_id, version). Lịch sử phiên bản đầy đủ. |
| 4 | **OTP mã hóa + Redis** | OTP hash BCrypt trước khi lưu Redis (TTL tự hết hạn). Không lưu plain text. |
| 5 | **JWT Access + Refresh** | Access 24h, Refresh 7 ngày. Auto refresh qua interceptor. |
| 6 | **QR Code Check-in** | SV tạo QR token short-lived → Manager quét → xác nhận. |
| 7 | **Waitlist pessimistic locking** | Khi slot đầy, position auto-increment dùng DB lock → đảm bảo thứ tự chính xác khi concurrent. |
| 8 | **Booking automation** | @Scheduled cron job: auto no-show, auto complete. Grace period configurable. |
| 9 | **Soft-delete toàn hệ thống** | BaseEntity (deleted flag). Không mất dữ liệu. Query filter `deleted=false`. |
| 10 | **Flyway 53 migrations** | Schema evolution có kiểm soát. Hibernate validate mode. Seed data cho dev. |
| 11 | **React Query server state** | 88 query keys phân cấp rõ ràng. Auto invalidation sau mutation. |
| 12 | **Swagger OpenAPI** | Mọi endpoint có @Operation, @Tag. Swagger UI tự động. |
| 13 | **Docker dev environment** | 5 containers (Redis, MySQL, 3 FE). Volume persist. |
| 14 | **Đánh giá 5 tiêu chí** | BigDecimal scoring, tổng điểm tự tính, kèm nhận xét giảng viên. |
| 15 | **Upload đa năng** | CV (file+URL), Report (file bắt buộc), Product (file+external link). Max 50MB. |

---

## 15. Hạn chế

| # | Hạn chế | Mức độ | Chi tiết |
|---|---------|--------|----------|
| 1 | ⚠ **Endpoint chưa hoàn thiện** | Trung bình | `PUT /labs/{id}` và `DELETE /labs/{id}` trả placeholder text, chưa có logic service. |
| 2 | ⚠ **Chưa có unit test** | Cao | Dependency `spring-boot-starter-test` có trong pom.xml nhưng không có file test nào. |
| 3 | ⚠ **Backend chưa Docker hóa** | Thấp | Không có Dockerfile cho Spring Boot. Chạy ngoài Docker bằng Maven. |
| 4 | ⚠ **Entity trùng lặp** | Thấp | `ProjectEntity` (bảng `projects`) và `ResearchProject` (bảng `research_projects`) tồn tại song song — refactor chưa hoàn tất. |
| 5 | ⚠ **File storage cục bộ** | Trung bình | Upload lưu thư mục local (`storage/reports`, `storage/products`). Chưa cloud storage. |
| 6 | ⚠ **CORS chưa restrict** | Thấp | Cho phép `http://localhost:*`. Cần config domain cho production. |
| 7 | ⚠ **Mật khẩu seed hardcode** | Thấp | AuthDataSeeder: `admin123`, `manager123`, `user123` — OK cho dev, cần thay production. |
| 8 | ⚠ **Enum legacy** | Thấp | TaskStatus có `IN_PROGRESS`, `REVIEW` (legacy). MilestoneStatus có `PLANNED`, `DELAYED` (legacy). |
| 9 | ⚠ **Dashboard placeholder** | Thấp | `DashboardPlaceholder.tsx` chưa triển khai nội dung. |
| 10 | ⚠ **Chưa có real-time notification** | Trung bình | Mọi thông báo qua email. Chưa WebSocket/SSE. |

---

## 16. Hướng phát triển thành Khóa luận Tốt nghiệp

| # | Hướng | Mô tả | Công nghệ dự kiến | Ưu tiên |
|---|-------|-------|--------------------|---------|
| 1 | **Thông báo thời gian thực** | Push notification khi có booking/report/comment mới | WebSocket (STOMP) hoặc SSE | Cao |
| 2 | **Ứng dụng di động** | QR check-in nhanh, push notification, xem task board | React Native hoặc Flutter | Cao |
| 3 | **Cloud storage** | Thay file local bằng cloud, hỗ trợ CDN + backup | AWS S3 / Google Cloud Storage | Trung bình |
| 4 | **CI/CD pipeline** | Auto build, test, deploy khi push | GitHub Actions / GitLab CI | Trung bình |
| 5 | **Full containerization** | Dockerfile backend, Kubernetes cho production | Docker + K8s | Trung bình |
| 6 | **Unit + Integration testing** | Test coverage cho service layer + API | JUnit 5, Mockito, TestContainers | Cao |
| 7 | **Analytics dashboard** | Biểu đồ tiến độ NCKH, so sánh nhóm, dự đoán trễ deadline | Chart.js / Recharts + data aggregation | Thấp |
| 8 | **Chat nội bộ nhóm NC** | Trao đổi trực tiếp giữa thành viên | WebSocket + message persistence | Thấp |

**Vì sao Lab Portal phù hợp làm KLTN:**
1. Quy mô đủ lớn: 25 module, 127+ endpoint, 30+ bảng.
2. Nghiệp vụ phức tạp: review 2 cấp, versioning, waitlist, RBAC nhiều cấp.
3. Stack công nghệ phổ biến ngành.
4. Có ý nghĩa thực tiễn — triển khai được tại trường.
5. Nhiều hướng mở rộng rõ ràng, có tính khả thi.
