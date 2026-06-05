<p align="center">
  <h1 align="center">🔬 Lab Portal — Hệ Thống Quản Lý Phòng Thí Nghiệm & Nghiên Cứu Khoa Học</h1>
  <p align="center">
    <em>Laboratory Management & Scientific Research Portal for Students</em>
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen?style=for-the-badge&logo=spring-boot" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/React-18-blue?style=for-the-badge&logo=react" alt="React"/>
  <img src="https://img.shields.io/badge/TypeScript-5.x-3178C6?style=for-the-badge&logo=typescript" alt="TypeScript"/>
  <img src="https://img.shields.io/badge/MySQL-8.0-orange?style=for-the-badge&logo=mysql" alt="MySQL"/>
  <img src="https://img.shields.io/badge/Redis-7-red?style=for-the-badge&logo=redis" alt="Redis"/>
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker" alt="Docker"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Vite-6-646CFF?style=flat-square&logo=vite" alt="Vite"/>
  <img src="https://img.shields.io/badge/TailwindCSS-3.4-06B6D4?style=flat-square&logo=tailwindcss" alt="Tailwind"/>
  <img src="https://img.shields.io/badge/Flyway-53%20migrations-CC0200?style=flat-square&logo=flyway" alt="Flyway"/>
  <img src="https://img.shields.io/badge/API-127%2B%20endpoints-9cf?style=flat-square" alt="API"/>
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="License"/>
</p>

---

## 📖 Mục Lục

- [Giới Thiệu](#-giới-thiệu)
- [Tính Năng Chính](#-tính-năng-chính)
- [Kiến Trúc Hệ Thống](#-kiến-trúc-hệ-thống)
- [Công Nghệ Sử Dụng](#-công-nghệ-sử-dụng)
- [Cấu Trúc Dự Án](#-cấu-trúc-dự-án)
- [Thiết Kế Cơ Sở Dữ Liệu](#-thiết-kế-cơ-sở-dữ-liệu)
- [API Endpoints](#-api-endpoints)
- [Phân Quyền Hệ Thống](#-phân-quyền-hệ-thống)
- [Yêu Cầu Hệ Thống](#-yêu-cầu-hệ-thống)
- [Hướng Dẫn Cài Đặt](#-hướng-dẫn-cài-đặt)
- [Hướng Dẫn Sử Dụng](#-hướng-dẫn-sử-dụng)
- [Luồng Nghiệp Vụ Chính](#-luồng-nghiệp-vụ-chính)
- [Kỹ Thuật Nổi Bật](#-kỹ-thuật-nổi-bật)
- [Kiểm Thử](#-kiểm-thử)
- [Hạn Chế & Hướng Phát Triển](#-hạn-chế--hướng-phát-triển) 

---

## 📌 Giới Thiệu

**Lab Portal** là hệ thống quản lý phòng thí nghiệm (PTN) và hoạt động nghiên cứu khoa học (NCKH) dành cho sinh viên, được phát triển như một đề tài khóa luận tốt nghiệp. Hệ thống số hóa toàn bộ quy trình vận hành PTN — từ ứng tuyển thành viên, đăng ký sử dụng, check-in bằng QR code, quản lý vệ sinh và vi phạm — đến quản lý vòng đời nghiên cứu khoa học theo nhóm: đề tài → nhóm → dự án → milestone → task → báo cáo (review 2 cấp) → đánh giá.

Hệ thống được thiết kế theo kiến trúc **Client-Server**, backend sử dụng mô hình **Modular Monolith** (package-by-feature), phân quyền **RBAC nhiều cấp** (3 system roles + 2 group roles), phục vụ 3 giao diện riêng biệt cho: Admin, Lab Manager, và Sinh viên.

### 🎯 Mục Tiêu Đề Tài

- Xây dựng nền tảng web tập trung cho quản lý phòng thí nghiệm, thay thế quy trình thủ công.
- Số hóa quy trình ứng tuyển – duyệt – quản lý thành viên PTN.
- Tự động hóa đăng ký sử dụng PTN: slot, booking, check-in QR code, waitlist.
- Quản lý vệ sinh, vi phạm, khiếu nại có lịch sử truy xuất đầy đủ.
- Quản lý toàn bộ vòng đời NCKH theo nhóm với quy trình **duyệt báo cáo 2 cấp** (Leader → Manager) có versioning.
- Triển khai phân quyền **RBAC nhiều cấp** kết hợp kiểm tra sở hữu tài nguyên.
- Docker hóa môi trường phát triển để đảm bảo tính nhất quán giữa các thành viên.

### 📋 Thông Tin Khóa Luận

| Thông Tin | Chi Tiết |
|-----------|----------|
| **Đề tài** | Xây dựng hệ thống quản lý phòng thí nghiệm và nghiên cứu khoa học cho sinh viên |
| **Loại** | Khóa luận tốt nghiệp |
| **Ngôn ngữ** | Java, TypeScript |
| **Mô hình** | Full-stack Web Application (Client–Server) |
| **Quy mô** | 25 module · 127+ API endpoints · 30+ bảng CSDL · 53 Flyway migrations |
| **Trạng thái** | ✅ Hoàn thành |

---

## ✨ Tính Năng Chính

### 👤 Phía Sinh Viên (Student)

| Tính Năng | Mô Tả |
|-----------|--------|
| 🔐 **Đăng ký 3 bước** | Email → OTP (mã hóa BCrypt, lưu Redis có TTL) → xác minh → điền thông tin → tạo tài khoản |
| 🔑 **Đăng nhập / Quên MK** | JWT access token (24h) + refresh token (7 ngày), quên mật khẩu qua OTP |
| 📝 **Ứng tuyển PTN** | Nộp đơn (upload CV file/URL), theo dõi trạng thái, nộp lại khi bị từ chối |
| 📅 **Đăng ký sử dụng PTN** | Chọn khung giờ → đặt booking → chờ duyệt → check-in |
| 📱 **Check-in QR Code** | Tạo mã QR token short-lived từ booking đã duyệt |
| 🧹 **Xác nhận vệ sinh** | Xem nhiệm vụ vệ sinh được phân công, xác nhận hoàn thành |
| ⚠️ **Xem vi phạm & Khiếu nại** | Xem lịch sử vi phạm, gửi khiếu nại có liên kết penalty |
| 📋 **Nhiệm vụ (Task)** | Xem task board Kanban, cập nhật trạng thái: TODO → DOING → WAITING_REVIEW → DONE |
| 📄 **Nộp báo cáo tiến độ** | Upload file (bắt buộc) + 5 mục nội dung, version tự tăng |
| 📦 **Nộp sản phẩm NC** | Upload file + external link, 8 loại sản phẩm |
| 📓 **Nhật ký NC** | Ghi nhật ký hàng ngày: nội dung, kết quả, khó khăn, kế hoạch tiếp |
| ⭐ **Xem đánh giá** | Xem kết quả đánh giá 5 tiêu chí của Manager |

### 🛡️ Phía Quản Lý PTN (Lab Manager)

| Tính Năng | Mô Tả |
|-----------|--------|
| 📊 **Dashboard PTN** | Thống kê: thành viên, booking, vi phạm trong PTN quản lý |
| 👥 **Quản lý thành viên** | Duyệt đơn ứng tuyển, xem/xóa thành viên PTN |
| 🕐 **Quản lý khung giờ** | Tạo time slot (ngày, giờ, sức chứa), hủy/xóa slot + email thông báo |
| 📅 **Duyệt booking** | Chấp nhận/từ chối đăng ký sử dụng PTN |
| 📱 **Quét QR Check-in** | Xác nhận check-in bằng QR token |
| 🧹 **Phân công vệ sinh** | Chọn SV đã check-in → phân công → theo dõi hoàn thành |
| ⚠️ **Ghi nhận vi phạm** | 5 loại vi phạm: NO_SHOW, LATE_CHECKIN, EQUIPMENT_DAMAGE, NOISE, OTHER |
| 📝 **Xử lý khiếu nại** | RESOLVED (ghi resolution_note) / REJECTED |
| 🔬 **Quản lý NCKH** | Tạo đề tài → nhóm → dự án → milestone → task → review báo cáo → đánh giá |
| 📄 **Review báo cáo (cấp 2)** | Duyệt báo cáo đã qua Leader review: APPROVE / REJECT |
| ⭐ **Đánh giá SV** | 5 tiêu chí BigDecimal + tổng điểm tự tính + nhận xét |

### 🏛️ Phía Quản Trị Viên (Admin)

| Tính Năng | Mô Tả |
|-----------|--------|
| 📊 **Dashboard hệ thống** | Thống kê tổng user, PTN, booking |
| 👥 **Quản lý người dùng** | Danh sách user, ban/unban, đổi role (STUDENT ↔ LAB_MANAGER) |
| 🏢 **Quản lý PTN** | Tạo PTN, gán Manager, cập nhật trạng thái |
| ⚙️ **Cấu hình hệ thống** | Key-value config, mọi thay đổi ghi audit log |
| 📋 **Nhật ký kiểm toán** | Tra cứu audit log: user, action, module, details, timestamp |

### 🔬 Tính Năng NCKH Nổi Bật

- **📄 Review báo cáo 2 cấp** — Leader duyệt cấp 1 → Manager duyệt cấp 2 (Leader nộp → bỏ qua cấp 1)
- **📊 Versioning báo cáo** — Mỗi lần nộp lại tạo version mới, unique constraint (task_id + submitted_by_id + version)
- **📋 Task board Kanban** — TODO → DOING → WAITING_REVIEW → NEEDS_REVISION → DONE
- **⭐ Đánh giá 5 tiêu chí** — Contribution, Task, Report, Product, Attitude + tổng điểm tự tính
- **📓 Nhật ký NC** — Ghi chép hàng ngày với filter đa chiều (group, milestone, task, author)

---

## 🏗 Kiến Trúc Hệ Thống

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                                │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐     │
│  │  Admin Portal    │ │ Manager Portal   │ │  User Portal     │     │
│  │  React + TS      │ │ React + TS       │ │  React + TS      │     │
│  │  Port: 5173      │ │ Port: 5174       │ │  Port: 5175      │     │
│  │  (dark theme)    │ │                  │ │                  │     │
│  └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘     │
│           │                    │                    │               │
└───────────┼────────────────────┼────────────────────┼───────────────┘
            │         REST API (JSON, JWT Bearer)     │
            ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    SERVER LAYER (Modular Monolith)                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │               Spring Boot Application (:8080)                 │  │
│  │  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────┐   │  │
│  │  │  Auth   │ │   Lab   │ │ Booking  │ │ Research │ │Admin │   │  │
│  │  │ module  │ │  module │ │  module  │ │  module  │ │module│   │  │
│  │  └─────────┘ └─────────┘ └──────────┘ └──────────┘ └──────┘   │  │
│  │  ┌─────────────────┐ ┌──────────────┐ ┌──────────────────┐    │  │
│  │  │ Spring Security │ │  Flyway (53  │ │  Swagger OpenAPI │    │  │
│  │  │   JWT + RBAC    │ │  migrations) │ │   Documentation  │    │  │
│  │  └─────────────────┘ └──────────────┘ └──────────────────┘    │  │
│  └───────────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────────┐
│                       DATA & SERVICE LAYER                          │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐       │
│  │   MySQL 8.0  │      │  Redis 7     │      │  Gmail SMTP  │       │
│  │  lab_portal  │      │  OTP Cache   │      │  Email Notif │       │
│  │   :3306      │      │   :6379      │      │   :587 TLS   │       │
│  └──────────────┘      └──────────────┘      └──────────────┘       │
│                      Docker Compose                                 │
└─────────────────────────────────────────────────────────────────────┘
```

### Mô Hình Kiến Trúc

- **Modular Monolith (Package-by-Feature)**: Auth → Lab → Booking → Research → Admin
- **Layered Architecture trong mỗi module**: Controller → Service → Repository
- **Design Patterns**: Repository, DTO/Mapper, Singleton, Dependency Injection
- **Nguyên tắc SOLID**: Áp dụng xuyên suốt dự án
- **Soft-Delete Pattern**: BaseEntity (id, createdAt, updatedAt, active, deleted)
- **Security**: JWT Authentication + RBAC Authorization + Resource Ownership Check

---

## 🛠 Công Nghệ Sử Dụng

### Backend

| Công Nghệ | Phiên Bản | Mục Đích |
|------------|-----------|----------|
| **Java** | 17 | Ngôn ngữ lập trình chính |
| **Spring Boot** | 3.4.5 | Application framework |
| **Spring Security 6** | — | Authentication & Authorization (JWT + RBAC) |
| **Spring Data JPA** | — | ORM (Hibernate) |
| **Spring Data Redis** | — | OTP cache với TTL |
| **Spring Mail** | — | Gửi email OTP & thông báo qua SMTP |
| **Spring Validation** | — | Bean validation |
| **MySQL** | 8.0 | Cơ sở dữ liệu chính (UTF8MB4) |
| **Redis** | 7 Alpine | Cache OTP (BCrypt hash + TTL) |
| **Flyway** | — | Database migration (53 files: V1 → V53) |
| **JWT (JJWT)** | 0.12.6 | Token generation & validation |
| **Springdoc OpenAPI** | 2.8.6 | Tài liệu API (Swagger UI) |
| **Lombok** | — | Giảm boilerplate code |
| **HikariCP** | — | Connection pool (max 10) |

### Frontend

| Công Nghệ | Phiên Bản | Mục Đích |
|------------|-----------|----------|
| **React** | 18 | UI Library |
| **TypeScript** | 5.x | Static typing |
| **Vite** | 6 | Build tool & dev server |
| **React Router** | 6 | Client-side routing (SPA) |
| **TanStack React Query** | 5 | Server state management (88 query keys) |
| **Axios** | — | HTTP client + JWT interceptor |
| **React Hook Form + Zod** | — | Form validation |
| **Tailwind CSS** | 3.4 | Styling framework |
| **Lucide React** | — | Icon library |
| **QRCode** | 1.5.4 | Tạo mã QR check-in |
| **Inter (@fontsource)** | — | Typography |

### DevOps & Công Cụ

| Công Nghệ | Mục Đích |
|------------|----------|
| **Docker & Docker Compose** | Container hóa infrastructure (5 containers) |
| **Maven** | Build & quản lý dependency (Backend) |
| **npm** | Package manager (Frontend) |
| **Postman** | Kiểm thử API |
| **Swagger UI** | Tài liệu API tương tác |
| **ESLint** | Linting code TypeScript/JavaScript |
| **Git** | Version control |

---

## 📁 Cấu Trúc Dự Án

```
Lab-Portal/
│
├── server/                              # Spring Boot Backend
│   ├── src/
│   │   └── main/
│   │       ├── java/com/web/labportalbackend/
│   │       │   ├── auth/                # Module: Xác thực & Tài khoản
│   │       │   │   ├── config/          # CORS, Redis, Security config
│   │       │   │   ├── controller/      # REST Controllers (Auth, Profile)
│   │       │   │   ├── dto/             # Data Transfer Objects
│   │       │   │   ├── entity/          # JPA Entities (User, Role, VerificationCode)
│   │       │   │   ├── mapper/          # Entity ↔ DTO mappers
│   │       │   │   ├── repository/      # Spring Data JPA Repositories
│   │       │   │   ├── security/        # JWT Filter, Auth Config, Provider
│   │       │   │   └── service/         # Business Logic
│   │       │   │
│   │       │   ├── lab/                 # Module: Phòng thí nghiệm
│   │       │   │   ├── controller/      # Lab, Application, Cleaning Controllers
│   │       │   │   ├── dto/
│   │       │   │   ├── entity/          # Laboratory, Membership, Application
│   │       │   │   ├── mapper/
│   │       │   │   ├── repository/
│   │       │   │   └── service/
│   │       │   │
│   │       │   ├── booking/             # Module: Đăng ký & Sử dụng PTN
│   │       │   │   ├── adapter/         # Adapter layer
│   │       │   │   ├── controller/      # Slot, Booking, Checkin, Penalty, Complaint
│   │       │   │   ├── dto/
│   │       │   │   ├── entity/          # TimeSlot, Booking, Waitlist, Cleaning, Penalty
│   │       │   │   ├── mapper/
│   │       │   │   ├── repository/
│   │       │   │   └── service/
│   │       │   │
│   │       │   ├── research/            # Module: Nghiên cứu khoa học
│   │       │   │   ├── controller/      # 10 Controllers (Topic, Group, Project,
│   │       │   │   │                    #   Milestone, Task, Report, Product,
│   │       │   │   │                    #   Evaluation, Log, Stats)
│   │       │   │   ├── dto/
│   │       │   │   ├── entity/          # 12 Entities
│   │       │   │   ├── enums/           # Status enums (TaskStatus, ReportStatus...)
│   │       │   │   ├── mapper/
│   │       │   │   ├── port/            # Port interfaces (Hexagonal)
│   │       │   │   ├── repository/
│   │       │   │   └── service/
│   │       │   │
│   │       │   ├── admin/               # Module: Quản trị viên
│   │       │   │   ├── dashboard/       # Dashboard thống kê
│   │       │   │   ├── systemconfig/    # Cấu hình key-value
│   │       │   │   └── audit/           # Nhật ký kiểm toán
│   │       │   │
│   │       │   └── common/              # Shared: BaseEntity, Exception Handler, Utils
│   │       │
│   │       └── resources/
│   │           ├── application.properties
│   │           └── db/migration/        # 53 Flyway migration scripts (V1 → V53)
│   │
│   ├── storage/                         # File upload storage (reports, products)
│   ├── .env.example                     # Environment variables template
│   ├── mvnw / mvnw.cmd                 # Maven Wrapper
│   └── pom.xml                          # Maven dependencies
│
├── client/                              # React Frontend (Multi-portal)
│   ├── src/
│   │   ├── app/                         # App entry points & routing
│   │   │   ├── App.tsx                  # Root component
│   │   │   ├── main.tsx                 # Bootstrap
│   │   │   ├── routes.tsx               # Route definitions
│   │   │   ├── router/                  # Router configuration
│   │   │   └── providers/               # Context providers
│   │   │
│   │   ├── modules/                     # Feature modules
│   │   │   ├── auth/                    # Đăng ký, Đăng nhập, OTP, Profile
│   │   │   │   ├── api/                 # Auth API services
│   │   │   │   ├── components/          # Auth UI components
│   │   │   │   ├── hooks/               # Auth custom hooks
│   │   │   │   ├── pages/               # Login, Register pages
│   │   │   │   └── types/               # Auth type definitions
│   │   │   │
│   │   │   ├── admin/                   # Admin Portal features
│   │   │   ├── lab/                     # Lab management & cleaning
│   │   │   ├── booking/                 # Slot, Booking, Check-in, Waitlist
│   │   │   ├── application/             # PTN application (ứng tuyển)
│   │   │   ├── penalty/                 # Penalty & Complaint
│   │   │   ├── research/                # NCKH: Topic, Group, Project, Milestone,
│   │   │   │                            #   Task, Report, Product, Evaluation, Log
│   │   │   └── user/                    # User profile & settings
│   │   │
│   │   ├── shared/                      # Shared resources
│   │   │   ├── api/                     # Axios instance & interceptors
│   │   │   ├── components/              # Reusable UI components
│   │   │   ├── constants/               # App constants
│   │   │   ├── hooks/                   # Shared custom hooks
│   │   │   ├── layout/                  # Layout components (Admin, Manager, User)
│   │   │   ├── types/                   # Shared type definitions
│   │   │   └── utils/                   # Utility functions
│   │   │
│   │   ├── layouts/                     # Portal-specific layouts
│   │   └── index.css                    # Global styles
│   │
│   ├── .env.development                 # Dev environment config
│   ├── .env.production                  # Production config
│   ├── tailwind.config.js               # Tailwind CSS configuration
│   ├── tsconfig.json                    # TypeScript configuration
│   ├── vite.config.ts                   # Vite build configuration
│   └── package.json                     # npm dependencies
│
├── docker/                              # Docker configurations
│   └── frontend/                        # Frontend Dockerfile
│
├── docker-compose.yml                   # Infrastructure: MySQL, Redis, 3 Frontend
├── .env.properties                      # Docker Compose env variables
├── Báo Cáo/                             # Tài liệu phân tích & báo cáo
│   ├── lab_portal_project_overview.md
│   ├── lab_portal_business_analysis.md
│   ├── lab_portal_feature_matrix.md
│   ├── lab_portal_use_cases.md
│   ├── lab_portal_report_outline.md
│   └── lab_portal_appendix.md
│
└── README.md                            # Tài liệu dự án (file này)
```

---

## 🗄 Thiết Kế Cơ Sở Dữ Liệu

### Tổng Quan

- **DBMS:** MySQL 8.0, charset `utf8mb4_unicode_ci`
- **Migration:** Flyway — 53 files (V1 → V53), Hibernate `ddl-auto: validate`
- **Pattern:** Soft-delete — BaseEntity (id, createdAt, updatedAt, active, deleted)
- **Connection Pool:** HikariCP, max 10

### Sơ Đồ Quan Hệ (ER Diagram)

```
┌──────────┐     ┌──────────┐     ┌──────────────────┐
│  users   │────<│  roles   │     │  laboratories    │
│──────────│     │(user_roles)    │──────────────────│
│ id       │     └──────────┘     │ id               │
│ username │                      │ lab_name         │
│ email    │                      │ location         │
│ password │                      │ capacity         │
│ fullName │                      │ status           │
│ phone    │                      │ manager_id (FK)  │
└────┬─────┘                      └──────┬───────────┘
     │                                   │
     │  ┌──────────────┐                 │
     ├─>│ applications │<────────────────┤
     │  │──────────────│                 │
     │  │ status       │                 │
     │  │ cv_file/url  │                 │
     │  └──────────────┘                 │
     │                                   │
     │  ┌──────────────┐                 │
     ├─>│ memberships  │<────────────────┤
     │  │──────────────│                 │
     │  │ role         │                 │
     │  │ active       │                 │
     │  └──────────────┘                 │
     │                                   │
     │  ┌──────────────┐  ┌──────────────┤
     │  │  time_slots  │<─┘              │
     │  │──────────────│                 │
     │  │ date         │  ┌──────────────┤
     │  │ start/end    │  │              │
     │  │ capacity     │  │  ┌───────────┴──────────┐
     │  │ status       │  │  │  research_topics     │
     │  └──────┬───────┘  │  │──────────────────────│
     │         │          │  │ name, description    │
     │  ┌──────┴───────┐  │  │ status               │
     ├─>│   bookings   │  │  └───────────┬──────────┘
     │  │──────────────│  │              │
     │  │ status       │  │  ┌───────────┴──────────┐
     │  │ checked_in_at│  │  │  research_groups     │
     │  └──────────────┘  │  │──────────────────────│
     │                    │  │ leader_id (FK→users) │
     │  ┌──────────────┐  │  └───────────┬──────────┘
     │  │  waitlists   │  │              │
     │  │──────────────│  │  ┌───────────┴──────────┐
     │  │ position     │  │  │     projects         │
     │  │ status       │  │  │──────────────────────│
     │  └──────────────┘  │  │ code, title          │
     │                    │  │ status, priority     │
     │  ┌──────────────┐  │  │ start/end date       │
     │  │  cleanings   │  │  └───────────┬──────────┘
     │  │──────────────│  │              │
     │  │ assigned_to  │  │  ┌───────────┴──────────┐
     │  │ status       │  │  │    milestones        │
     │  └──────────────┘  │  │──────────────────────│
     │                    │  │ deadline, progress   │
     │  ┌──────────────┐  │  │ status               │
     │  │  penalties   │  │  └───────────┬──────────┘
     │  │──────────────│  │              │
     │  │ penalty_type │  │  ┌───────────┴──────────┐
     │  │ point        │  │  │      tasks           │
     │  │ status       │  │  │──────────────────────│
     │  └──────┬───────┘  │  │ assignee_id (FK)     │
     │         │          │  │ status (Kanban)      │
     │  ┌──────┴───────┐  │  └───────────┬──────────┘
     │  │ complaints   │  │              │
     │  │──────────────│  │  ┌───────────┴──────────┐
     │  │ status       │  │  │     reports          │
     │  │ resolution   │  │  │──────────────────────│
     │  └──────────────┘  │  │ version (auto-incr)  │
     │                    │  │ status (2-level)     │
     │                    │  │ file (bắt buộc)      │
     │                    │  └──────────────────────┘
     │                    │
     │                    │  ┌──────────────────────┐
     │                    │  │     products         │
     │                    │  │──────────────────────│
     │                    │  │ product_type (8 loại)│
     │                    │  │ file + external_link │
     │                    │  └──────────────────────┘
     │                    │
     │                    │  ┌──────────────────────┐
     │                    │  │    evaluations       │
     │                    │  │──────────────────────│
     │                    │  │ 5 scores (BigDecimal)│
     │                    │  │ totalScore (auto)    │
     │                    │  └──────────────────────┘
     │                    │
     │                    │  ┌──────────────────────┐
     │                    │  │   research_logs      │
     │                    │  │──────────────────────│
     │                    │  │ workDate, duration   │
     │                    │  │ content, result      │
     │                    │  └──────────────────────┘
```

### Danh Sách Bảng (30+ tables)

**Module Auth (4 bảng):**

| STT | Tên Bảng | Mô Tả |
|-----|----------|--------|
| 1 | `users` | Tài khoản người dùng (username UK, email UK, password BCrypt) |
| 2 | `roles` | Vai trò hệ thống (ADMIN, LAB_MANAGER, STUDENT) |
| 3 | `user_roles` | Quan hệ M:N User ↔ Role |
| 4 | `verification_codes` | OTP hash + expiry |

**Module Lab (3 bảng):**

| STT | Tên Bảng | Mô Tả |
|-----|----------|--------|
| 5 | `laboratories` | PTN (lab_name UK, status, manager_id FK→users) |
| 6 | `applications` | Đơn ứng tuyển (user_id, lab_id, status, cv_url/file) |
| 7 | `memberships` | Tư cách thành viên (user_id, lab_id, role, active) |

**Module Booking (6 bảng):**

| STT | Tên Bảng | Mô Tả |
|-----|----------|--------|
| 8 | `time_slots` | Khung giờ sử dụng (lab_id, date, start/end, capacity, status) |
| 9 | `bookings` | Đăng ký sử dụng (student_id, slot_id, status, checked_in_at) |
| 10 | `waitlists` | Hàng đợi (student_id, slot_id, position, status) |
| 11 | `cleanings` | Nhiệm vụ vệ sinh (slot_id, assigned_to, status) |
| 12 | `penalties` | Vi phạm (student_id, penalty_type, point, reason) |
| 13 | `complaints` | Khiếu nại (user_id, penalty_id, status, resolution_note) |

**Module Research (12 bảng):**

| STT | Tên Bảng | Mô Tả |
|-----|----------|--------|
| 14 | `research_topics` | Đề tài NC (lab_id, name, status) |
| 15 | `research_groups` | Nhóm NC (lab_id, topic_id, project_id, leader_id) |
| 16 | `group_members` | Thành viên nhóm (group_id, user_id, role: LEADER/MEMBER) |
| 17 | `projects` | Dự án NC (code, title, status, priority, dates) |
| 18 | `milestones` | Mốc tiến độ (project_id, group_id, deadline, progress, status) |
| 19 | `tasks` | Nhiệm vụ (milestone_id, assignee_id, status, deadline) |
| 20 | `reports` | Báo cáo (task_id, submitted_by_id, version, status, file) |
| 21 | `comments` | Bình luận (report_id, author_id, content) |
| 22 | `products` | Sản phẩm NC (project_id, group_id, product_type, file/link) |
| 23 | `evaluations` | Đánh giá (project_id, student_id, 5 scores + comment) |
| 24 | `research_logs` | Nhật ký NC (project_id, author_id, work_date, duration) |
| 25 | `project_logs` | Activity log (project_id, user_id, action, details) |

**Module Admin (3 bảng):**

| STT | Tên Bảng | Mô Tả |
|-----|----------|--------|
| 26 | `system_configs` | Cấu hình key-value |
| 27 | `system_audit_logs` | Lịch sử thay đổi config |
| 28 | `audit_logs` | Nhật ký kiểm toán hệ thống |

---

## 🌐 API Endpoints

### Tổng Quan: **127+ REST API Endpoints** qua **25 Module**

<details>
<summary><b>🔐 Authentication — 14 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/auth/register/send-otp` | Gửi OTP đăng ký qua email |
| `POST` | `/api/auth/register/verify-otp` | Xác minh OTP đăng ký |
| `POST` | `/api/auth/register/complete` | Hoàn tất đăng ký |
| `POST` | `/api/auth/login` | Đăng nhập |
| `POST` | `/api/auth/refresh-token` | Làm mới access token |
| `POST` | `/api/auth/forgot-password/send-otp` | Gửi OTP quên mật khẩu |
| `POST` | `/api/auth/forgot-password/verify-otp` | Xác minh OTP reset |
| `POST` | `/api/auth/forgot-password/reset` | Đặt mật khẩu mới |
| `GET` | `/api/auth/me` | Xem hồ sơ cá nhân |
| `PUT` | `/api/auth/me` | Cập nhật hồ sơ |

</details>

<details>
<summary><b>👥 Admin User Management — 6 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `GET` | `/api/admin/users` | Danh sách người dùng |
| `PUT` | `/api/admin/users/{id}/ban` | Khóa tài khoản |
| `PUT` | `/api/admin/users/{id}/unban` | Mở khóa tài khoản |
| `PATCH` | `/api/admin/users/{id}/role` | Đổi vai trò |
| `PUT` | `/api/admin/users/{id}/roles` | Cập nhật nhiều roles |
| `GET` | `/api/admin/users/assignable-managers` | Danh sách Manager khả dụng |

</details>

<details>
<summary><b>🏢 Laboratory & Application — 15 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/labs` | Tạo PTN mới (Admin) |
| `GET` | `/api/labs` | Danh sách PTN |
| `GET` | `/api/labs/{id}` | Chi tiết PTN |
| `PUT` | `/api/labs/{id}/manager` | Gán Manager cho PTN |
| `PATCH` | `/api/labs/{id}/status` | Cập nhật trạng thái PTN |
| `GET` | `/api/labs/{id}/members` | Xem thành viên PTN |
| `DELETE` | `/api/labs/{id}/members/{userId}` | Xóa thành viên |
| `GET` | `/api/labs/{id}/eligible-students` | SV đủ điều kiện NC |
| `POST` | `/api/applications` | Nộp đơn ứng tuyển |
| `PUT` | `/api/applications/{id}/review` | Duyệt/từ chối đơn |
| `GET` | `/api/applications` | Danh sách đơn |
| `GET` | `/api/users/{id}/applications` | Đơn theo user |

</details>

<details>
<summary><b>📅 Slot & Booking & Check-in — 14 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/slots` | Tạo khung giờ |
| `GET` | `/api/labs/{id}/slots` | Khung giờ theo PTN |
| `PATCH` | `/api/slots/{id}/status` | Cập nhật trạng thái slot |
| `PATCH` | `/api/slots/{id}/cancel` | Hủy slot + email thông báo |
| `DELETE` | `/api/slots/{id}` | Xóa slot |
| `POST` | `/api/bookings` | Đăng ký sử dụng PTN |
| `PATCH` | `/api/bookings/{id}/review` | Duyệt/từ chối booking |
| `PATCH` | `/api/bookings/{id}/cancel` | Hủy booking (SV) |
| `GET` | `/api/bookings/me` | Booking của tôi |
| `GET` | `/api/slots/{id}/bookings` | Booking theo slot |
| `POST` | `/api/checkin/qr` | Tạo QR check-in |
| `POST` | `/api/checkin/confirm` | Xác nhận check-in |

</details>

<details>
<summary><b>🧹 Cleaning — 9 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `GET` | `/api/slots/{id}/eligible-cleaners` | SV đủ điều kiện vệ sinh |
| `POST` | `/api/cleaning-tasks` | Phân công vệ sinh |
| `GET` | `/api/labs/{id}/cleaning-tasks` | Vệ sinh theo PTN |
| `GET` | `/api/users/me/cleaning-tasks` | Vệ sinh của tôi |
| `PATCH` | `/api/cleaning-tasks/{id}/complete` | Xác nhận hoàn thành |
| `PATCH` | `/api/cleaning-tasks/{id}/cancel` | Hủy nhiệm vụ |

</details>

<details>
<summary><b>⚠️ Penalty & Complaint — 8 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/penalties` | Ghi nhận vi phạm |
| `GET` | `/api/slots/{id}/penalties` | Vi phạm theo slot |
| `GET` | `/api/users/me/penalties` | Vi phạm của tôi |
| `GET` | `/api/users/{id}/penalties` | Vi phạm theo user |
| `PUT` | `/api/config/penalty` | Cấu hình mức phạt |
| `POST` | `/api/complaints` | Nộp khiếu nại |
| `GET` | `/api/labs/{id}/complaints` | Khiếu nại theo PTN |
| `PATCH` | `/api/complaints/{id}/review` | Xử lý khiếu nại |

</details>

<details>
<summary><b>🔬 Research (Topic, Group, Project) — 19 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/research-topics` | Tạo đề tài NC |
| `GET` | `/api/labs/{id}/research-topics` | Đề tài theo PTN |
| `POST` | `/api/research-projects` | Tạo dự án NC |
| `PUT` | `/api/research-projects/{id}` | Cập nhật dự án |
| `GET` | `/api/labs/{id}/research-projects` | Dự án theo PTN |
| `GET` | `/api/research-projects/{id}` | Chi tiết dự án |
| `GET` | `/api/groups/{id}/projects` | Dự án theo nhóm |
| `POST` | `/api/groups` | Tạo nhóm NC |
| `PUT` | `/api/research-groups/{id}` | Cập nhật nhóm |
| `POST` | `/api/groups/{id}/members` | Thêm thành viên nhóm |
| `GET` | `/api/labs/{id}/groups` | Nhóm theo PTN |
| `GET` | `/api/research-topics/{id}/groups` | Nhóm theo đề tài |
| `GET` | `/api/research-projects/{id}/groups` | Nhóm theo dự án |
| `GET` | `/api/labs/{id}/research-groups/me` | Nhóm của tôi |
| `GET` | `/api/research-groups/{id}` | Chi tiết nhóm |
| `GET` | `/api/research-groups/{id}/members` | Thành viên nhóm |

</details>

<details>
<summary><b>📋 Milestone & Task — 11 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/milestones` | Tạo mốc tiến độ |
| `PUT` | `/api/milestones/{id}` | Cập nhật mốc |
| `GET` | `/api/projects/{id}/milestones` | Mốc theo project |
| `GET` | `/api/research-groups/{id}/milestones` | Mốc theo nhóm |
| `GET` | `/api/research-groups/{id}/milestones/me` | Mốc của tôi |
| `GET` | `/api/milestones/{id}` | Chi tiết mốc |
| `POST` | `/api/milestones/{id}/tasks` | Tạo nhiệm vụ |
| `PUT` | `/api/tasks/{id}/status` | Cập nhật trạng thái task |
| `GET` | `/api/milestones/{id}/tasks` | Task theo milestone |
| `GET` | `/api/research-groups/{id}/tasks` | Task theo nhóm |
| `GET` | `/api/research-groups/{id}/tasks/me` | Task của tôi |

</details>

<details>
<summary><b>📄 Report & Review — 11 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/reports` | Nộp báo cáo (multipart) |
| `PATCH` | `/api/reports/{id}/replace` | Cập nhật/thay thế báo cáo |
| `GET` | `/api/milestones/{id}/reports` | Báo cáo theo milestone |
| `GET` | `/api/milestones/{id}/reports/me` | Báo cáo của tôi |
| `GET` | `/api/tasks/{id}/reports` | Báo cáo theo task |
| `GET` | `/api/groups/{id}/reports` | Báo cáo theo nhóm |
| `GET` | `/api/groups/{id}/reports/me` | Báo cáo của tôi theo nhóm |
| `GET` | `/api/reports/{id}/file` | Download file (streaming) |
| `GET` | `/api/labs/{id}/reports/pending-review` | Báo cáo chờ Manager duyệt |
| `PATCH` | `/api/reports/{id}/leader-review` | Leader review (cấp 1) |
| `PATCH` | `/api/reports/{id}/manager-review` | Manager review (cấp 2) |

</details>

<details>
<summary><b>📦 Product, Evaluation, Log, Stats — 10 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `POST` | `/api/products` | Nộp sản phẩm NC (multipart) |
| `GET` | `/api/projects/{id}/products` | Sản phẩm theo project |
| `GET` | `/api/research-groups/{id}/products` | Sản phẩm theo nhóm |
| `POST` | `/api/evaluations` | Đánh giá SV (5 tiêu chí) |
| `GET` | `/api/projects/{id}/evaluations` | Đánh giá theo project |
| `GET` | `/api/research-groups/{id}/evaluations` | Đánh giá theo nhóm |
| `POST` | `/api/logs` | Tạo nhật ký NC |
| `GET` | `/api/projects/{id}/logs` | Nhật ký theo project |
| `GET` | `/api/research-groups/{id}/logs` | Nhật ký theo nhóm |
| `GET` | `/api/projects/{id}/stats?type=overview` | Thống kê dự án |

</details>

<details>
<summary><b>📊 Dashboard & System Config — 6 endpoints</b></summary>

| Method | Endpoint | Mô Tả |
|--------|----------|--------|
| `GET` | `/api/admin/dashboard/stats` | Admin dashboard |
| `GET` | `/api/labs/{id}/dashboard/stats` | Lab dashboard (Manager) |
| `GET` | `/api/admin/system-config` | Xem cấu hình hệ thống |
| `PUT` | `/api/admin/system-config` | Cập nhật cấu hình |
| `GET` | `/api/admin/audit-logs` | Nhật ký kiểm toán |

</details>

> 📄 **Tài liệu API chi tiết**: Truy cập Swagger UI tại `http://localhost:8080/swagger-ui.html` sau khi chạy Backend.

---

## 🔒 Phân Quyền Hệ Thống

### Cơ Chế Kỹ Thuật

| Tầng | Cơ Chế | Mô Tả |
|------|--------|-------|
| **API Gateway** | `JwtAuthenticationFilter` | Trích JWT từ header, validate, set SecurityContext |
| **Endpoint** | `@PreAuthorize("hasRole('...')")` | Spring Method Security trên từng controller method |
| **Business Logic** | `assertCanManageLab()` | Kiểm tra Manager có quản lý PTN này không |
| **Frontend Route** | `ProtectedRoute` | Redirect nếu chưa login hoặc sai role |
| **Frontend Route** | `RoleBasedRoute` | Cho phép truy cập theo danh sách role |
| **Frontend Route** | `ActiveMembershipRoute` | Chặn nếu SV chưa có membership active |
| **Frontend UI** | Menu visibility | navItems thay đổi theo role và membership |

### Vai Trò Hệ Thống

| Role | Tên Hiển Thị | Giao Diện | Mô Tả |
|------|-------------|-----------|-------|
| `ADMIN` | Quản trị viên | Admin Portal (:5173, dark theme) | Quản lý toàn hệ thống |
| `LAB_MANAGER` | Quản lý PTN | Manager Portal (:5174) | Vận hành 1 PTN được gán |
| `STUDENT` | Sinh viên | User Portal (:5175) | Sử dụng PTN, tham gia NCKH |
| `LEADER` | Trưởng nhóm NC | User Portal (:5175) | Group role — duyệt báo cáo cấp 1 |
| `MEMBER` | Thành viên nhóm | User Portal (:5175) | Group role — thực hiện task |

> Một SV có thể là **Leader** ở nhóm A, **Member** ở nhóm B — group role không phải system role.

### Ma Trận Phân Quyền

| Chức Năng | ADMIN | MANAGER | STUDENT | LEADER |
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
| Đánh giá SV (5 tiêu chí) | — | ✅ | — | — |
| Ứng tuyển PTN | — | — | ✅ | ✅ |
| Booking + Check-in QR | — | — | ✅ | ✅ |
| Nộp khiếu nại | — | — | ✅ | ✅ |
| Cập nhật task status | — | — | ✅ | ✅ |
| Nộp báo cáo / sản phẩm | — | — | ✅ | ✅ |
| Xem task/report **chỉ của mình** | — | — | ✅ | — |
| Xem task/report **toàn nhóm** | — | — | — | ✅ |
| Leader review báo cáo (cấp 1) | — | — | — | ✅ |
| Ghi nhật ký NC | — | — | ✅ | ✅ |

> `✅ own` = Chỉ trên PTN mình quản lý.

---

## 💻 Yêu Cầu Hệ Thống

### Phần Mềm Bắt Buộc

| Phần Mềm | Phiên Bản Tối Thiểu | Tải Về |
|-----------|---------------------|--------|
| **Java JDK** | 17+ | [Download](https://adoptium.net/) |
| **Node.js** | 18+ | [Download](https://nodejs.org/) |
| **Docker Desktop** | Latest | [Download](https://www.docker.com/products/docker-desktop/) |
| **Maven** | 3.9+ (hoặc dùng Maven Wrapper) | [Download](https://maven.apache.org/) |
| **Git** | Latest | [Download](https://git-scm.com/) |

### Phần Cứng Khuyến Nghị

| Thành Phần | Yêu Cầu |
|------------|----------|
| **RAM** | ≥ 8 GB (Docker containers cần ~3 GB) |
| **CPU** | ≥ 4 cores |
| **Disk** | ≥ 5 GB trống |
| **OS** | Windows 10/11, macOS, Linux |

---

## 🚀 Hướng Dẫn Cài Đặt

### Bước 1: Clone Repository

```bash
git clone https://github.com/nvtqx1/Lab-Portal.git
cd Lab-Portal
```

### Bước 2: Cấu Hình Environment

```bash
# Backend: Copy và chỉnh sửa file .env
cd server
cp .env.example .env.properties

# Cập nhật các giá trị trong .env.properties:
# - MAIL_USERNAME, MAIL_PASSWORD (Gmail App Password)
# - DB_PASSWORD (mật khẩu MySQL)
# - JWT_SECRET (base64 encoded, ≥ 32 bytes)
```

### Bước 3: Khởi Động Infrastructure (Docker)

```bash
# Quay về thư mục gốc
cd ..
docker-compose up -d
```

> ⏳ Đợi khoảng **30-60 giây** để MySQL và Redis khởi động hoàn tất.

Kiểm tra trạng thái containers:

```bash
docker-compose ps
```

Kết quả mong đợi:

```
NAME                           STATUS
lab_portal_mysql               running (0.0.0.0:3306->3306)
lab-portal-redis               running (0.0.0.0:6379->6379)
lab_portal_frontend_admin      running (0.0.0.0:5173->5173)
lab_portal_frontend_manager    running (0.0.0.0:5174->5174)
lab_portal_frontend_user       running (0.0.0.0:5175->5175)
```

### Bước 4: Chạy Backend

```bash
cd server

# Sử dụng Maven Wrapper
./mvnw spring-boot:run

# Hoặc trên Windows
mvnw.cmd spring-boot:run

# Hoặc sử dụng Maven global
mvn spring-boot:run
```

> ✅ Backend sẽ chạy tại: `http://localhost:8080`
>
> 📄 Swagger UI: `http://localhost:8080/swagger-ui.html`
>
> 🗄 Flyway sẽ tự động chạy 53 migration scripts khi khởi động lần đầu.

### Bước 5: Chạy Frontend (Thủ công — không dùng Docker)

```bash
cd client

# Cài đặt dependencies
npm install

# Chạy theo từng portal:
npm run dev:admin    # Admin Portal   tại port 5173
npm run dev:manager  # Manager Portal tại port 5174
npm run dev:user     # User Portal    tại port 5175

# Hoặc chạy mặc định
npm run dev          # Port 5173
```

> ⚠️ Nếu đã chạy frontend qua Docker Compose ở Bước 3, **không cần** chạy thủ công.

### Bước 6: Truy Cập Ứng Dụng

| Ứng Dụng | URL | Mô Tả |
|-----------|-----|--------|
| **Admin Portal** | `http://localhost:5173` | Giao diện quản trị viên (dark theme) |
| **Manager Portal** | `http://localhost:5174` | Giao diện quản lý PTN |
| **User Portal** | `http://localhost:5175` | Giao diện sinh viên |
| **Backend API** | `http://localhost:8080/api` | REST API |
| **Swagger UI** | `http://localhost:8080/swagger-ui.html` | Tài liệu API tương tác |

### ⚡ Quick Start (Tất Cả Trong 1)

```bash
# Terminal 1: Infrastructure + Frontend (Docker)
docker-compose up -d

# Terminal 2: Backend
cd server && ./mvnw spring-boot:run
```

---

## 📖 Hướng Dẫn Sử Dụng

### Tài Khoản Mặc Định

| Vai Trò | Email / Username | Mật Khẩu |
|---------|-----------------|-----------|
| **Admin** | `admin@labportal.com ` | `admin123` |
| **Lab Manager** | `manager01@labportal.com` | `manager123` |
| **Student** | `student01@labortal.com` | `user123` |

> ⚠️ **Lưu ý**: Tài khoản mặc định được tạo bởi `AuthDataSeeder` khi khởi động lần đầu. Cần thay đổi mật khẩu cho môi trường production.

### Quy Trình Sử Dụng Cơ Bản

```
Sinh viên:
  1. Đăng ký tài khoản (Email → OTP → Xác minh → Điền thông tin)
  2. Đăng nhập
  3. Xem danh sách PTN
  4. Nộp đơn ứng tuyển (upload CV)
  5. [Chờ Manager duyệt]
  6. Đăng ký sử dụng PTN (chọn slot)
  7. [Chờ Manager duyệt booking]
  8. Tạo QR code check-in
  9. Check-in khi đến PTN
  10. Tham gia nhóm NCKH → thực hiện task → nộp báo cáo

Manager:
  1. Đăng nhập (Manager Portal)
  2. Duyệt đơn ứng tuyển
  3. Tạo khung giờ sử dụng PTN
  4. Duyệt booking
  5. Quét QR check-in
  6. Tạo đề tài → nhóm → dự án → milestone → task
  7. Review báo cáo → đánh giá SV

Admin:
  1. Đăng nhập (Admin Portal)
  2. Tạo PTN mới
  3. Gán Manager cho PTN
  4. Quản lý người dùng
```

---

## 🔄 Luồng Nghiệp Vụ Chính

### Luồng 1: Đăng Ký → Gia Nhập PTN → Sử Dụng

```
SV đăng ký tài khoản (email OTP 3 bước)
  → Đăng nhập
  → Xem danh sách PTN
  → Nộp đơn ứng tuyển (CV file/URL)
  → [PENDING] Manager duyệt
  → [APPROVED] Tạo Membership
  → SV truy cập tính năng PTN
```

### Luồng 2: Đăng Ký Sử Dụng PTN

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
  - Manager hủy slot → CANCELLED_BY_MANAGER + email thông báo
  - Không check-in → auto NO_SHOW (scheduled task chạy mỗi phút)
```

### Luồng 3: Vệ Sinh → Vi Phạm → Khiếu Nại

```
Slot kết thúc
  → Manager phân công vệ sinh (chọn SV đã check-in)
  → [PENDING] SV hoàn thành → [COMPLETED]

Vi phạm:
  Manager ghi nhận → [ACTIVE]
  → SV xem → gửi khiếu nại → [PENDING]
  → Manager xem xét → [RESOLVED/REJECTED]
```

### Luồng 4: Nghiên Cứu Khoa Học (NCKH)

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
7. SV nộp Báo cáo (Report) — kèm file bắt buộc
     ↓
8. Leader duyệt cấp 1 (Leader Review)
     ↓
9. Manager duyệt cấp 2 (Manager Review)
     ↓
10. SV nộp Sản phẩm (Product) — file/link
     ↓
11. Manager Đánh giá SV (Evaluation) — 5 tiêu chí
```

### Luồng 5: Review Báo Cáo 2 Cấp (Chi Tiết)

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
         ├── ACCEPT         → Status = LEADER_REVIEWED → Bước 5
         ├── NEEDS_REVISION → Status = NEEDS_REVISION  → SV sửa → Bước 3 (v2)
         └── REJECT         → Status = LEADER_REJECTED → SV nộp mới → Bước 3 (v2)
           ↓
Bước 5: Manager review
         ├── APPROVE → Status = APPROVED          → Task có thể chuyển DONE
         └── REJECT  → Status = MANAGER_REJECTED  → SV nộp mới → Bước 3 (v3)

⚠ Đặc biệt: Leader nộp report → Bước 4 bị bỏ qua → thẳng Bước 5
```

---

## 🔑 Kỹ Thuật Nổi Bật

### 1. Phân Quyền RBAC Nhiều Cấp

```
3 System Roles: ADMIN, LAB_MANAGER, STUDENT
2 Group Roles:  LEADER, MEMBER (trong nhóm NC)

Kiểm tra 3 tầng:
  ① @PreAuthorize("hasRole('LAB_MANAGER')")     → Role check
  ② assertCanManageLab(labId)                    → Resource ownership check
  ③ Frontend: ProtectedRoute + RoleBasedRoute    → Client-side guard
```

### 2. OTP Mã Hóa + Redis Cache

```
Email → Generate OTP → BCrypt(OTP) → Store in Redis (TTL auto-expire)
                                       ↓
User nhập OTP → BCrypt verify against Redis hash → Cấp temp/reset token
```

> OTP **không bao giờ** lưu dạng plain text. Redis TTL tự động xóa OTP hết hạn.

### 3. Waitlist với Pessimistic Locking

```java
// Đảm bảo position không bị trùng khi nhiều SV đăng ký đồng thời
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT MAX(w.position) FROM WaitlistEntity w WHERE w.slot.id = :slotId")
Optional<Integer> findMaxPositionBySlotId(@Param("slotId") Long slotId);
```

> Sử dụng `SELECT...FOR UPDATE` để đảm bảo thứ tự waitlist chính xác khi concurrent request.

### 4. Booking Automation

```
@EnableScheduling + @Scheduled(cron = "0 * * * * *")  // Mỗi phút

Auto tasks:
  - No-show detection (grace period configurable)
  - Slot completion marking
  - Waitlist auto-promote khi có chỗ trống
```

### 5. Review Báo Cáo 2 Cấp + Versioning

```
Report version: unique constraint (task_id, submitted_by_id, version)

SUBMITTED → Leader: ACCEPT/NEEDS_REVISION/REJECT
         → LEADER_REVIEWED → Manager: APPROVE/REJECT
         → Nộp lại → version+1 (v1 → v2 → v3...)

Đặc biệt: Leader nộp → skip Leader review → thẳng Manager
```

### 6. Đánh Giá 5 Tiêu Chí (BigDecimal)

| Tiêu Chí | Ý Nghĩa |
|----------|---------|
| `contributionScore` | Mức đóng góp |
| `taskScore` | Hoàn thành nhiệm vụ |
| `reportScore` | Chất lượng báo cáo |
| `productScore` | Sản phẩm đầu ra |
| `attitudeScore` | Thái độ, kỷ luật |

> `totalScore` tự tính. Kèm `lecturerComment`. Sử dụng BigDecimal đảm bảo độ chính xác.

### 7. Soft-Delete Toàn Hệ Thống

```java
// BaseEntity — tất cả entity kế thừa
@MappedSuperclass
public abstract class BaseEntity {
    Long id;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    boolean active;
    boolean deleted;  // Soft-delete flag
}
```

> Không bao giờ mất dữ liệu. Query filter `deleted=false`. Lịch sử đầy đủ.

### 8. Flyway Database Migration

```
53 migration files: V1__create_users.sql → V53__*.sql

Hibernate ddl-auto: validate (chỉ kiểm tra, KHÔNG tự tạo schema)
Schema evolution có kiểm soát, seed data cho môi trường dev.
```

## 🧪 Kiểm Thử

### API Testing (Swagger UI)

1. Truy cập Swagger UI tại `http://localhost:8080/swagger-ui.html`
2. Đăng nhập bằng endpoint `/api/auth/login` để lấy JWT token
3. Click **Authorize** và nhập `Bearer {token}`
4. Test các endpoint trực tiếp trên Swagger

### Kiểm Thử Thủ Công

| Test Case | Mô Tả | Kết Quả Mong Đợi |
|-----------|--------|-------------------|
| Đăng ký OTP 3 bước | Email → OTP → xác minh → điền thông tin | Tạo tài khoản STUDENT thành công |
| Đăng nhập | Username + password đúng | Nhận JWT access + refresh token |
| Nộp đơn ứng tuyển | Upload CV file | Đơn ở trạng thái PENDING |
| Nộp đơn trùng | Nộp khi đơn cũ chưa xử lý | DuplicateApplicationException |
| Đăng ký booking | Chọn slot available | Booking PENDING_APPROVAL |
| Slot đầy | Đăng ký khi hết capacity | Tự động thêm vào waitlist |
| Waitlist concurrent | 2 SV đăng ký waitlist đồng thời | Position không trùng (pessimistic lock) |
| QR check-in | Tạo QR → Manager quét | Booking → CHECKED_IN |
| Auto no-show | Không check-in quá grace period | Booking → NO_SHOW |
| Nộp báo cáo | Upload file + nội dung | Report v1, status SUBMITTED |
| Leader review → NEEDS_REVISION | Leader yêu cầu sửa | SV nộp lại → v2 |
| Leader nộp report | Leader tự nộp | Bỏ qua Leader review → thẳng Manager |
| Manager approve | Manager duyệt | Report → APPROVED |
| Hủy slot | Manager hủy slot | Tất cả booking CANCELLED + email thông báo |
| Ban user | Admin khóa tài khoản | User không thể đăng nhập |

---

## ⚠️ Hạn Chế & Hướng Phát Triển

### Hạn Chế Hiện Tại

| # | Hạn Chế | Mức Độ | Chi Tiết |
|---|---------|--------|----------|
| 1 | Endpoint chưa hoàn thiện | Thấp | `PUT /labs/{id}` và `DELETE /labs/{id}` trả placeholder text |
| 2 | Chưa có unit test | Cao | Dependency test có trong pom.xml nhưng chưa viết test |
| 3 | Backend chưa Docker hóa | Thấp | Không có Dockerfile cho Spring Boot, chạy ngoài Docker |
| 4 | File storage cục bộ | Trung bình | Upload lưu thư mục local (`storage/`), chưa cloud storage |
| 5 | CORS chưa restrict | Thấp | Cho phép `http://localhost:*`, cần config domain cho production |
| 6 | Mật khẩu seed hardcode | Thấp | `admin123`, `manager123`, `user123` — OK cho dev |
| 7 | Student dashboard placeholder | Thấp | `DashboardPlaceholder.tsx` chưa triển khai nội dung |
| 8 | Chưa có real-time notification | Trung bình | Mọi thông báo qua email, chưa WebSocket/SSE |

### Hướng Phát Triển

| # | Hướng | Mô Tả | Công Nghệ Dự Kiến | Ưu Tiên |
|---|-------|-------|--------------------|---------| 
| 1 | **Thông báo thời gian thực** | Push notification khi có booking/report mới | WebSocket (STOMP) / SSE | Cao |
| 2 | **Ứng dụng di động** | QR check-in nhanh, push notification, xem task | React Native / Flutter | Cao |
| 3 | **Unit + Integration testing** | Test coverage cho service layer + API | JUnit 5, Mockito, TestContainers | Cao |
| 4 | **Cloud storage** | Thay file local bằng cloud, CDN + backup | AWS S3 / Google Cloud Storage | Trung bình |
| 5 | **CI/CD pipeline** | Auto build, test, deploy khi push | GitHub Actions / GitLab CI | Trung bình |
| 6 | **Full containerization** | Dockerfile backend, orchestration | Docker + Kubernetes | Trung bình |
| 7 | **Analytics dashboard** | Biểu đồ tiến độ NCKH, so sánh nhóm | Recharts + data aggregation | Thấp |
| 8 | **Chat nội bộ nhóm NC** | Trao đổi trực tiếp giữa thành viên | WebSocket + message persistence | Thấp |

---

