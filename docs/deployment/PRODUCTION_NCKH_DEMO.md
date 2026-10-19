# Dọn dữ liệu nội bộ trước khi dùng dữ liệu thật

## Nguồn dữ liệu mẫu trong ứng dụng

Backend đã bỏ `DevDataSeeder`, các SQL seed/rollback thủ công trong `db/dev`,
`db/demo` và script `import-production-nckh-demo.ps1`. Cờ `DEV_SEED_ENABLED`
không còn được ứng dụng đọc. Cần build và triển khai phiên bản mới để loại những
nguồn seed đó khỏi runtime.

Các migration Flyway lịch sử vẫn giữ nguyên để bảo toàn checksum của database
đã triển khai. Việc gỡ source seed không tự xóa dữ liệu trong MySQL hiện hữu.
Không sửa lịch sử Flyway, chạy lại migration cũ hoặc xóa volume MySQL để dọn dữ liệu.

`ProductionAdminBootstrapper` tiếp tục khởi tạo quản trị viên production qua ENV.
Tài khoản đã tồn tại không bị đặt lại mật khẩu khi backend khởi động.

## Backup trước thao tác thủ công

Trong CMD tại checkout production, tạo backup DB, storage và uploads:

```cmd
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File ".\scripts\backup-production.ps1" -OutputDirectory "D:\LabPortal-Backups" -SkipRetention
```

Đợi `Backup written to ...` và giữ đường dẫn backup. `-SkipRetention` không dọn
các backup cũ. Backup chứa thông tin đăng nhập và dữ liệu cá nhân; không đưa lên Git
hoặc chia sẻ dump DB, ENV và file tải lên trong báo cáo vận hành.

## Phạm vi phải được chốt cho từng lần dọn

- Xác minh trên DB đang được backend production sử dụng: tài khoản cần giữ,
  quyền, bảng dữ liệu, khóa ngoại và dữ liệu được website giới thiệu tham chiếu.
- Nếu giữ website, giữ `PUBLIC_WEBSITE_CONTENT` cả bản nháp và bản công bố,
  các cấu hình `WEBSITE_SCHOLAR_*` và file website đang dùng.
- Khi xóa tài khoản/lĩnh vực nội bộ được CMS liên kết, chỉ gỡ `userId`/`labId`
  liên quan; giữ hồ sơ, bài viết, thứ tự, hình ảnh và nội dung đã biên tập.
- Phân biệt file nội bộ với file website. Không xóa cả volume storage/uploads.
- Không xem việc còn email thật là đủ để chứng minh quyền sở hữu tài khoản:
  đối chiếu ID, username, email và quyền trước khi quyết định tập bản ghi cần giữ.

SQL reset production dùng một lần được quản lý ngoài đường chạy ứng dụng và không
được đưa vào Flyway, startup hoặc pipeline deploy. Repository không cung cấp hook
tự động reset dữ liệu khi merge hoặc triển khai.

## Thứ tự thực hiện

1. Tạo backup mới, khôi phục vào MySQL tạm với volume riêng.
2. Kiểm chứng SQL dọn, dữ liệu được giữ, rollback khi lỗi và khôi phục DB/file.
3. Chốt thời gian bảo trì, dừng các tiến trình ghi dữ liệu và tạo backup cuối.
4. Chạy đúng SQL đã kiểm chứng, theo thứ tự khóa ngoại trong transaction.
   Không dùng `TRUNCATE` hoặc tắt kiểm tra khóa ngoại.
5. Kiểm kê sau khi dọn: dữ liệu nội bộ, tài khoản/quyền được giữ, CMS và file website.
6. Mở backend lại, kiểm tra readiness, đăng nhập và website trên trình duyệt.

Kết quả build, kết quả kiểm chứng trên bản sao và trạng thái production là các bằng
chứng khác nhau. Không suy ra production đã sạch từ việc gỡ source hoặc build thành công.

Giữ backup sau khi hoàn tất. Không chạy lại SQL reset sau khi bắt đầu nhập dữ liệu thật.
Log phát sinh từ hoạt động mới sau khi mở ứng dụng là dữ liệu mới.
