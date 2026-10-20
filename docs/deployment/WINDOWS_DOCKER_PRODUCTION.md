# Deploy Lab Portal trên một máy Windows chạy 24/7

Runbook này dùng `docker-compose.prod.yml` và Docker Desktop với Linux containers/WSL2. `docker-compose.yml` chỉ dành cho phát triển.

## Trạng thái phát hành hiện tại

Mã nguồn đã tách dữ liệu phát triển khỏi profile `prod`, nhưng chưa có bằng chứng khởi chạy stack production trên máy này. Kiểm tra migration, tài khoản quản trị và các luồng vận hành trước khi mở truy cập.

- Backend đã bỏ `DevDataSeeder`, cấu hình bật seed và các bộ SQL demo thủ công. `AuthDataSeeder` chỉ nâng cấp mật khẩu plaintext lịch sử ở profile khác `prod`, không tạo tài khoản.
- Flyway production chạy thêm `db/migration-prod`: migration `V68_1` vô hiệu hóa tài khoản demo lịch sử và `V72` xóa các bản ghi demo xác định được. `ProductionAdminBootstrapper` tạo admin bằng thông tin cấu hình ở lần chạy đầu, không đặt lại mật khẩu của admin đã tồn tại.
- MySQL production dùng volume riêng. Dữ liệu mock đang có trong MySQL development không tự chuyển sang production; không trỏ production vào database development để chuyển dữ liệu.

## Kiến trúc

```
Trình duyệt -> Caddy :80/:443 -> frontend và Spring API
                                  -> MySQL, Redis, Face service
                                           -> AI trên AWS (khi cấu hình)
```

Chỉ Caddy publish port lên Windows. MySQL, Redis, backend và Face nằm trên mạng nội bộ của Compose. Production không build hoặc chạy AI trên PC; backend chỉ gọi AI trên AWS sau khi cấu hình endpoint. Các service local dùng `restart: unless-stopped`, health checks, named volumes cho dữ liệu và Docker `local` log driver để giới hạn dung lượng log.

## 1. Chuẩn bị máy

1. Dùng Ethernet có dây, SSD đủ chỗ cho Docker images, MySQL và uploads. Nếu cần tiếp tục chạy khi mất điện ngắn, dùng UPS; bật tùy chọn khôi phục nguồn sau mất điện trong BIOS/UEFI nếu máy hỗ trợ.
2. Cài WSL2 và Docker Desktop; chọn Linux containers/WSL2 backend. Sau khi cài, kiểm tra:

   ```powershell
   wsl --version
   docker version
   docker compose version
   ```

3. Trong Docker Desktop, bật **Settings → General → Start Docker Desktop when you sign in**. Đặt Windows không sleep/hibernate khi cắm điện. Màn hình vẫn có thể tự tắt.
4. Giữ tài khoản Windows đã khởi chạy Docker Desktop đăng nhập; có thể khóa màn hình. Docker Desktop tự chạy khi người dùng đăng nhập, còn restart policy chỉ phục hồi containers sau khi Docker Engine chạy lại. Sau khi Windows reboot mà chưa đăng nhập, dịch vụ có thể chưa chạy.
5. Không tắt Windows Update. Chọn giờ bảo trì để cài bản cập nhật và kiểm tra lại dịch vụ sau reboot.

## 2. Lấy source vào thư mục ổn định

Ví dụ dùng `D:\Lab-Portal`. Nếu chọn đường dẫn khác, cập nhật đường dẫn `.env.production` trong workflow production cho khớp.

```powershell
git clone https://github.com/nvtrung18/Lab-Portal.git D:\Lab-Portal
Set-Location D:\Lab-Portal
git switch main
git pull --ff-only origin main
git rev-parse HEAD
```

Ghi lại SHA của commit đã triển khai. Chỉ cập nhật lên commit đã xem xét; không pull tự động mỗi lần khởi động.

## 3. Chọn phạm vi truy cập

Chọn một trong hai mô hình và đặt `APP_DOMAIN`, `API_DOMAIN` trong `.env.production`.

### Chỉ truy cập trong LAN

- Tạo hai tên DNS nội bộ, ví dụ `app.lab-portal.home.arpa` và `api.lab-portal.home.arpa`, trỏ về IP LAN cố định/reservation của PC.
- Caddy cấp HTTPS bằng CA nội bộ. Sau khi Caddy đã chạy và tạo CA, xuất root certificate rồi cài vào kho **Trusted Root Certification Authorities** trên từng PC client (chạy PowerShell quyền Administrator):

  ```powershell
  docker compose --env-file .env.production -f docker-compose.prod.yml cp caddy:/data/caddy/pki/authorities/local/root.crt .\caddy-local-root.crt
  certutil -addstore -f "ROOT" .\caddy-local-root.crt
  ```

  Trình duyệt/thiết bị không tin CA sẽ cảnh báo certificate. Không chia sẻ hoặc xuất private key của CA.
- Không cấu hình port-forwarding trên router.

### Truy cập từ Internet

- Cần domain public; bản ghi DNS của cả `APP_DOMAIN` và `API_DOMAIN` phải trỏ đến public IP của mạng.
- Với domain ở Namecheap, vào **Domain List → Manage → Nameservers** để xem DNS đang được quản lý ở đâu. Nếu dùng **Namecheap BasicDNS**, vào **Advanced DNS → Host Records**. Nếu nameserver là CustomDNS hoặc của nhà cung cấp hosting khác, thêm bản ghi tại nhà cung cấp đó; đừng đổi nameserver chỉ để sửa bản ghi vì các bản ghi hiện tại không tự chuyển sang nameserver mới.
- Vào trang quản trị router xem **Internet/WAN IPv4**, rồi so sánh với public IPv4 mà trang kiểm tra IP trên PC hiển thị. Không dùng địa chỉ `192.168.x.x` từ `ipconfig` làm giá trị DNS; đó là IP LAN của PC. Nếu WAN IP khác public IP hiển thị hoặc thuộc dải private, hỏi ISP xem đường truyền có public IPv4 trực tiếp không (CGNAT/double NAT có thể chặn port-forwarding).
- Tạo hai bản ghi **A** trỏ đến public IPv4 của router, không phải IP LAN của PC:

  | Type | Host | Value |
  |---|---|---|
  | A Record | `app` | public IPv4 của đường truyền |
  | A Record | `api` | public IPv4 của đường truyền |

  Đặt TTL `Automatic`. Không sửa/xóa MX, TXT, SPF hoặc DKIM đang dùng cho email. Không thêm AAAA trừ khi đường truyền và firewall IPv6 đã cấu hình đầy đủ.
- Nếu public IP thay đổi theo thời gian, Namecheap hỗ trợ Dynamic DNS trên BasicDNS/PremiumDNS/FreeDNS: bật Dynamic DNS, tạo `A+Dynamic DNS Record` cho `app` và `api`, rồi cấu hình router hoặc DDNS client bằng Dynamic DNS password (không phải mật khẩu tài khoản Namecheap). Dynamic DNS Namecheap cập nhật IPv4; nếu không có client tương thích, yêu cầu public IP tĩnh từ ISP.
- Sau khi DNS đã cập nhật, trên Windows kiểm tra `Resolve-DnsName app.example.com` và `Resolve-DnsName api.example.com`; thay `example.com` bằng domain thật. Cả hai phải trả về public IPv4 của router.
- Forward duy nhất TCP 80 và 443 từ router đến IP LAN cố định của PC. Mở đúng hai port đó trong Windows Firewall nếu cần. Không forward 3306, 6379, 8080, 8090 hoặc 8091.
- Caddy tự lấy và gia hạn certificate HTTPS khi DNS trỏ đúng và Caddy có thể nhận kết nối qua port 80/443. Nếu ISP dùng CGNAT, port-forwarding thường không đủ; dùng một tunnel có kiểm soát hoặc chỉ triển khai LAN.
- Chỉ bật port-forwarding sau khi seed tài khoản production đã được xử lý.

## 4. Tạo file cấu hình và secrets

```powershell
Set-Location D:\Lab-Portal
Copy-Item .env.production.example .env.production
notepad .env.production
```

Thay tất cả placeholder. Không commit hoặc gửi `.env.production`. Tạo secrets ngẫu nhiên bằng PowerShell:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
function New-Base64Secret([int]$ByteCount) {
    $bytes = New-Object byte[] $ByteCount
    $rng.GetBytes($bytes)
    [Convert]::ToBase64String($bytes)
}
New-Base64Secret 48  # dùng riêng cho JWT_SECRET và các internal/challenge tokens
New-Base64Secret 32  # dùng riêng cho FACE_EMBEDDING_ENCRYPTION_KEY
$rng.Dispose()
```

Sinh giá trị mới cho từng trường, không dùng chung một secret. Đặt `MYSQL_ROOT_PASSWORD`, `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `AI_INTERNAL_SERVICE_TOKEN`, `FACE_INTERNAL_SERVICE_TOKEN`, `FACE_CHALLENGE_SECRET` và `FACE_EMBEDDING_ENCRYPTION_KEY`. Trường mã hóa embedding cần base64 của đúng 32 byte.

Điền SMTP thật để gửi OTP/email. Nếu dùng Google Sign-In, cấu hình `GOOGLE_CLIENT_ID` bằng client ID đã khai báo đúng origins cho `APP_DOMAIN`; giá trị này được đóng gói vào frontend nên không phải secret. Không đặt mật khẩu hoặc token vào biến `VITE_*`.

Để `AI_GATEWAY_BASE_URL` trống khi chưa có endpoint AWS. Backend vẫn khởi động; yêu cầu AI sẽ trả 503. Giữ `AI_INTERNAL_SERVICE_TOKEN` vì backend cần giá trị này ngay cả khi AI chưa hoạt động.

## 5. Kết nối AI trên AWS khi sẵn sàng

Production Compose không chứa `ai-service`, nên lệnh deploy không build AI hoặc yêu cầu model artifacts trên PC. Khi AI trên AWS sẵn sàng, đặt `AI_GATEWAY_BASE_URL` trong `.env.production` thành URL mà container backend truy cập được và bảo đảm `AI_INTERNAL_SERVICE_TOKEN` khớp với phía AWS. Kiểm tra kết nối, TLS và các API AI trước khi coi tính năng này đã hoạt động.

Tài liệu `AI_SERVICE_AWS_TEST.md` hiện dùng SSM tunnel tạm thời cho kiểm thử; địa chỉ `localhost` trong tài liệu đó không phải endpoint AWS mà container backend trên PC có thể dùng trực tiếp.

## 6. Kiểm tra cấu hình Compose

```powershell
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
```

Lệnh này chỉ kiểm tra cú pháp và thay biến môi trường; nó không kiểm tra credentials, DNS, certificate, model load hoặc luồng đăng nhập.

Chỉ khởi động stack khi `.env.production` đã có thông tin bootstrap admin riêng và đã có bản sao lưu nếu đang cập nhật một database hiện hữu. Sau khi chạy, kiểm tra kết quả migration và tài khoản thực tế trước khi mở truy cập:

```powershell
.\scripts\deploy-production.ps1 -Build
docker compose --env-file .env.production -f docker-compose.prod.yml ps
docker compose --env-file .env.production -f docker-compose.prod.yml logs --tail 100 backend
docker compose --env-file .env.production -f docker-compose.prod.yml logs --tail 100 caddy
```

Kiểm tra readiness API:

```powershell
Invoke-RestMethod https://api.example.com/api/actuator/health/readiness
Invoke-WebRequest https://app.example.com
```

Thay domain bằng giá trị thật. Với LAN, tên domain phải resolve về IP LAN và client phải tin CA của Caddy. Thử đăng nhập, gửi email OTP, tạo/đọc dữ liệu và kiểm tra upload từ một máy client trước khi đưa vào sử dụng.

## 7. Sao lưu hằng ngày

Script sao lưu tạo thư mục theo ngày gồm MySQL dump, `/app/storage` và `/app/uploads`; mặc định giữ 30 ngày. Chọn ổ đĩa khác ổ cài Windows nếu có:

```powershell
.\scripts\backup-production.ps1 -OutputDirectory 'E:\LabPortalBackups' -RetentionDays 30
```

Trước khi ghi dữ liệu, script giới hạn quyền đọc thư mục backup vừa tạo cho tài khoản Windows chạy script, `SYSTEM` và `Administrators`. Sau lần chạy đầu, kiểm tra ACL bằng `Get-Acl 'E:\LabPortalBackups\lab-portal-YYYYMMDD-HHMMSS' | Format-List Access`; thay tên thư mục thực tế. File dump và uploads vẫn chưa mã hóa, nên giữ ổ backup và mọi bản sao ngoài máy trong phạm vi được kiểm soát quyền truy cập.

MySQL dump dùng `--single-transaction`; thư mục storage/uploads được chép tiếp khi ứng dụng vẫn chạy nên cả bộ backup không phải một snapshot đồng thời. Lên lịch vào giờ ít sử dụng và thử khôi phục định kỳ trước khi dựa vào backup này.

Tạo tác vụ trong **Task Scheduler** chạy hằng ngày, ví dụ 02:30:

- Program: `powershell.exe`
- Arguments: `-NoProfile -ExecutionPolicy Bypass -File "D:\Lab-Portal\scripts\backup-production.ps1" -OutputDirectory "E:\LabPortalBackups" -RetentionDays 30`
- Chạy bằng cùng tài khoản Windows có quyền gọi Docker Desktop và chọn **Run only when user is logged on**. Kiểm tra **Last Run Result** và thư mục có `backup-complete.json`.

Sao lưu `.env.production` riêng bằng cơ chế mã hóa/được kiểm soát quyền truy cập; không bỏ secrets vào thư mục backup thường. Giữ thêm một bản ngoài PC (ổ rời cất riêng hoặc backup mã hóa) và định kỳ thử restore trên môi trường tách biệt. Backup trên cùng ổ đĩa không bảo vệ khi ổ hỏng.

## 8. Cập nhật và khôi phục

Trước mỗi lần cập nhật, xác nhận backup gần nhất hoàn chỉnh rồi cập nhật source có chủ đích:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
git rev-parse HEAD
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
.\scripts\deploy-production.ps1 -Build
```

Compose deploy script không chạy `down` nên giữ named volumes. Không dùng `docker compose down -v`; thao tác đó xóa volume dữ liệu. Nếu bản mới lỗi, quay lại SHA đã ghi và build lại. Không rollback database migration mù quáng; khi migration đã đổi schema, khôi phục cần backup đã kiểm tra và kế hoạch tương thích dữ liệu.

## Nguồn chính thức

- [Docker Desktop WSL2 backend](https://docs.docker.com/desktop/features/wsl/)
- [Docker Desktop settings và tự khởi động khi đăng nhập](https://docs.docker.com/desktop/settings-and-maintenance/settings/)
- [Docker container restart policies](https://docs.docker.com/engine/containers/start-containers-automatically/)
- [Docker local logging driver](https://docs.docker.com/engine/logging/drivers/local/)
- [Compose startup order và health checks](https://docs.docker.com/compose/how-tos/startup-order/)
- [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https)
- [Microsoft powercfg options](https://learn.microsoft.com/en-us/windows-hardware/design/device-experiences/powercfg-command-line-options)
- [Namecheap: cấu hình Host Records](https://www.namecheap.com/support/knowledgebase/article.aspx/434/2237/how-do-i-set-up-host-records-for-a-domain/)
- [Namecheap: bật Dynamic DNS](https://www.namecheap.com/support/knowledgebase/article.aspx/595/11/how-do-i-enable-dynamic-dns-for-a-domain/)
