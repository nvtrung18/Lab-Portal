# Website giới thiệu và quản trị nội dung

Website đã được ghép vào frontend React/TypeScript/Vite hiện tại trong `client`.
Giao diện, font Manrope và ảnh được chuyển từ `Research_Lab.zip`. Website và
Admin, Manager, User dùng chung nhận diện thiết kế; bố cục website vẫn nằm trong
phạm vi `.lab-public`.

## Thiết kế dùng chung

`client/src/index.css` định nghĩa font và các biến `--lab-brand-*`: navy `#133449`,
ink `#152f40`, mint `#81cdbd`, nền nhẹ `#f4f7f8`, đường phân cách `#e3e8eb`, bo góc 5px.
Website công khai lấy các giá trị này; khu vực quản lý dùng các biến `--app-*`
cho nền, văn bản, viền và trạng thái tương tác ở chế độ sáng/tối.
`client/tailwind.config.js` ánh xạ các utility đang dùng sang cùng bảng màu.

- Auth: bố cục ảnh/navy và form hai cột; màn hình nhỏ tập trung vào form.
- Admin/Manager/User: thanh đầu trang nền trắng, menu nhóm với chỉ báo mint,
  liên kết trở về website, vùng nội dung tối đa 1440px.
- Bảng, form, modal, thống kê, thông báo và tab dùng cùng kiểu chữ/viền/khoảng cách.
- Nút chính navy; nút phụ mint; hủy dạng viền; xóa đỏ; xác nhận xanh.
  Màu cảnh báo và thông báo lỗi giữ ý nghĩa nghiệp vụ.
- CSS dùng chung không thay đổi API, dữ liệu, phân quyền hoặc các luồng duyệt hồ sơ.
  Giữ lựa chọn ngôn ngữ, chế độ tối, focus bàn phím và giảm chuyển động.

## Chạy

Chạy backend hiện tại với database, sau đó mở một terminal PowerShell:

```powershell
cd D:\CV\Lab-Portal\client
npm run dev
```

Dùng địa chỉ HTTP/HTTPS do Vite in ra:

- `/` chuyển tới `/vi`, là website giới thiệu công khai.
- `/vi` và `/en`: website tiếng Việt hoặc tiếng Anh.
- `/vi/<duong-dan-trang>`: các trang hiện có của website.
- `/login`: đăng nhập vào hệ thống quản lý hiện tại.
- `/admin/website`: quản trị website, chỉ tài khoản ADMIN.

Không cần chạy frontend Next.js ở thư mục `website` hay cổng 3000.
Biến `VITE_PUBLIC_WEBSITE_URL` không còn được sử dụng. API dùng cấu hình
`VITE_API_BASE_URL` và proxy `/api` hiện tại của frontend.

## Nội dung trong các tab cố định

Trong Admin chọn **Website giới thiệu → Nội dung các tab**:

1. Chọn một tab/trang có sẵn.
2. Tích chọn các mục trong **Thư viện nội dung** để đưa vào tab.
3. Trong **Nội dung đã chọn cho tab**, dùng nút lên/xuống để đổi thứ tự hoặc bỏ chọn.
   Bỏ chọn chỉ gỡ khỏi tab hiện tại; nội dung vẫn được giữ trong thư viện.
4. **Thêm nội dung mới**: chọn mẫu văn bản, ảnh + nội dung, khối nổi bật hoặc **Tự soạn**.
   Nhập tiêu đề, dòng nhấn, mô tả, các đoạn văn, ảnh và nút liên kết bằng tiếng Việt/Anh.
5. **Áp dụng vào bản nháp** đưa nội dung mới vào thư viện và chọn cho tab hiện tại.
6. Có thể dùng lại một nội dung ở nhiều tab. Sửa nội dung cập nhật mọi tab đang dùng nó;
   thứ tự được lưu riêng cho từng tab. Bỏ quyền hiển thị trong form sẽ ẩn ở mọi tab.
7. Xóa khỏi thư viện cần xác nhận và gỡ nội dung khỏi mọi tab trong bản nháp.

Admin quản lý thông tin trong tab. Màn hình không cung cấp thao tác thêm/xóa tab,
đổi tên menu, đường dẫn, thứ tự tab hoặc bố cục trang. Menu header lấy tên và thứ tự
từ mẫu: Trang chủ, Giới thiệu, Nghiên cứu, Thành viên, Công bố, Dự án, Tin & Sự kiện.
Các mục này vẫn xuất hiện khi nội dung trang trống. Tiêu đề, đoạn giới thiệu và các
mục nội dung đã lưu được giữ; các trang tự do cũ vẫn đọc được nhưng không thêm vào header.

Các mục bổ sung xuất hiện dưới phần giới thiệu/hero, theo thứ tự Admin đặt.
Admin chỉ quản lý nội dung động. Không hiển thị form **Thông tin chung & ảnh**, sửa văn bản
mẫu hoặc tiêu đề/đoạn giới thiệu cố định của trang. Các giá trị hiện có vẫn được giữ khi lưu.
Nội dung mới để trống; không tự tạo dữ liệu nghiên cứu hoặc thành viên. Nếu chưa từng có
mục bổ sung, thư viện trống và admin có thể tạo bằng các mẫu có sẵn.

Hướng nghiên cứu, thành viên, Publications, dự án và tin tức vẫn được quản lý tại
các mục tương ứng. Publications hỗ trợ Journal, Conference, Book và Preprint.
Các bản ghi này có thể thêm, sửa, xóa, đổi thứ tự và tích chọn hiển thị ngay trong danh sách.

## Hướng nghiên cứu từ danh sách nhóm hiện có

1. Vào **Hướng nghiên cứu → Thêm từ nhóm nghiên cứu**. Bộ chọn dùng lại `GET /api/labs`
   và kiểu `LabResponse` của hệ thống. Theo hệ quy chiếu mới, mỗi bản ghi phòng thí nghiệm
   được xem là một nhóm/hướng nghiên cứu. Tên bảng, API và ID cũ được giữ để tương thích.
2. Danh sách giữ quy tắc API hiện có: `active = true`, `deleted = false`, không lọc thêm
   theo trạng thái. Tìm theo tên, mô tả hoặc đơn vị; FE xếp ID tăng dần và phân trang 20 nhóm/trang.
3. Khi chọn tạo mới, tên và mô tả hiện có điền vào cả hai trường Việt/Anh (không tự dịch).
   Mô tả chưa có để trống; ảnh và chủ đề để trống để admin bổ sung. Chỉnh nội dung công khai,
   tích **Hiển thị**, áp dụng, xem trước rồi lưu nháp/xuất bản. Không tự xuất bản toàn bộ nhóm.
4. Nội dung mẫu/cũ vẫn giữ. Trong form chọn **Liên kết nhóm nghiên cứu** để gắn nhóm mà giữ
   ID và nội dung đã có, gồm các tham chiếu từ Thành viên, công bố và dự án.
   Đổi nhóm liên kết cũng giữ nội dung đang chỉnh.

`research[].labId` là trường tùy chọn trong JSON website, không cần migration schema mới.
Mỗi nhóm có tối đa một hướng nghiên cứu trên website, kể cả bản ghi đang ẩn. BE kiểm tra
các ID bằng một truy vấn theo danh sách, từ chối nhóm trùng, không tồn tại hoặc đã xóa.
Nhóm đã liên kết chuyển không hoạt động vẫn giữ nội dung công khai cho admin quyết định;
nếu đã bị xóa thì cần đổi liên kết hoặc gỡ nội dung trước khi lưu lại.
Endpoint công khai trả `labId: null` và chỉ dùng nội dung đã xuất bản.
Chỉnh/xóa nội dung website không đổi tên, mô tả hay xóa nhóm trong hệ thống nội bộ.
Thay đổi dữ liệu nhóm không tự ghi đè nội dung website đã chỉnh riêng.

## Thành viên từ danh sách người dùng

1. Vào **Thành viên → Thêm từ người dùng**. Danh sách đọc từ bảng `users` qua
   `GET /api/admin/website/member-sources`, chỉ ADMIN truy cập; bao gồm ADMIN và
   các trạng thái tài khoản, loại tài khoản đã bị xóa. Đây là danh sách để lựa chọn,
   không tự đưa tất cả người dùng lên website và không thay đổi quyền vào hệ thống.
2. Tìm theo tên, tên đăng nhập hoặc email; danh sách phân trang tại FE, 20 người/trang.
3. Chọn người dùng để mở hồ sơ công khai. Tên đầy đủ hiện tại được điền vào tên hiển thị
   Việt/Anh; nếu chưa có tên đầy đủ thì để trống. Ảnh, giới thiệu, vai trò học thuật,
   hướng nghiên cứu, chữ viết tắt và email công khai do admin nhập riêng.
   Email đăng nhập và thông tin riêng của tài khoản không tự sao chép sang hồ sơ.
4. Chỉnh từng người, tích **Hiển thị**, áp dụng, xem trước rồi lưu nháp/xuất bản như các mục khác.
   Mỗi tài khoản có tối đa một hồ sơ Thành viên, kể cả hồ sơ đang ẩn.
5. Hồ sơ mẫu/cũ không có `userId` vẫn đọc và chỉnh được. Trong form chọn **Liên kết người dùng**
   để gắn tài khoản mà vẫn giữ tên, nội dung, ID hồ sơ và tham chiếu từ công bố/dự án.
   Đổi người dùng liên kết cũng giữ nội dung đang chỉnh để admin tự cập nhật.

`members[].userId` là trường tùy chọn trong JSON website, không cần migration schema mới.
BE kiểm tra các ID tài khoản bằng một truy vấn theo danh sách và từ chối liên kết trùng,
không tồn tại hoặc đã xóa. Nếu tài khoản đã xóa, đổi liên kết hoặc gỡ hồ sơ trước khi lưu lại.
Website công khai chỉ trả các trường trình bày đã xuất bản, với `userId: null`; không truy vấn
hồ sơ tài khoản để tự ghi đè nội dung. Thay đổi tên tài khoản không tự đổi tên trên website.
Ẩn/xóa hồ sơ Thành viên không xóa tài khoản hoặc thu hồi quyền của người dùng.

## Form soạn thảo bài viết và nội dung trong tab

- **Tin tức & sự kiện** dùng form tiêu đề, mô tả ngắn, ảnh đại diện, loại/ngày/địa điểm và
  vùng soạn thảo trực quan. **Nội dung các tab** dùng cùng editor cho phần nội dung chính,
  giữ lựa chọn mẫu văn bản/ảnh cạnh nội dung/nổi bật và nút liên kết.
- Editor Tiptap hỗ trợ đậm, nghiêng, gạch chân, tiêu đề phụ H2/H3, danh sách, đánh số,
  trích dẫn, liên kết, ảnh giữa bài, hoàn tác và làm lại. Chọn ngôn ngữ Việt/Anh để soạn;
  dữ liệu của ngôn ngữ còn lại được giữ. Ảnh có thể chèn bằng URL hoặc tải PNG/JPEG/WebP
  tối đa 5 MB qua API ảnh website hiện có.
- **Xem trước** ngay trong form mở đúng trang/bài đang chỉnh, gồm thay đổi chưa áp dụng.
  Đóng preview để tiếp tục soạn, **Áp dụng vào bản nháp**, rồi **Lưu nháp** hoặc **Xuất bản**
  ở màn hình quản trị. Preview không tự lưu hay xuất bản nội dung.
- Thành viên và Hướng nghiên cứu tiếp tục dùng form theo từng trường.
- **Publications**, **Dự án giới thiệu** và **Tin tức & sự kiện** có form theo từng loại,
  mở rộng để soạn bài bằng editor Việt/Anh, chèn ảnh và liên kết. Publications giữ
  loại/năm công bố, tác giả, nơi công bố và DOI/PDF/Code; Dự án giữ nhóm nghiên cứu,
  thời gian, người phụ trách và mục tiêu; Tin tức giữ loại bài, ngày và địa điểm.
  Tác giả ngoài lab hiển thị đúng tên admin nhập.

### Nguồn / nội dung gốc cho cả ba loại

Các form **Biên tập công bố — Publications**, **Biên tập dự án giới thiệu** và
**Biên tập bài viết — Tin tức & sự kiện** đều có nhóm **Nguồn / nội dung gốc (tùy chọn)**:
nhập **Tên nguồn / báo / website** và **Link gốc** để dẫn tới bài báo, công bố hoặc
trang dự án liên quan. Nội dung do lab tự viết có thể để trống hai trường.
Admin tự nhập tiêu đề, tóm tắt và nội dung; nhập URL không tự tải hoặc sao chép bài nguồn.

`sourceName` (tối đa 200 ký tự) và `sourceUrl` (tối đa 2000 ký tự) là hai trường tùy chọn
trên mỗi mục trong `publications`, `projects` và `news`. Thiếu/null/chuỗi rỗng không
hiển thị nhãn hoặc nút tương ứng. URL nếu có
phải bắt đầu bằng `http://` hoặc `https://` và không chứa khoảng trắng; BE kiểm tra khi
lưu/xuất bản. Chỉ tên nguồn không cần URL; chỉ URL không cần tên nguồn. Website và preview
hiển thị tên nguồn đã nhập cùng nút **Xem nguồn gốc** trên trang chi tiết, mở tab mới.
Ngày và địa điểm trống không hiển thị dòng tương ứng trong khung chi tiết bài viết.
Dữ liệu cũ chưa có nguồn vẫn đọc được. Hai trường được lưu cùng JSON bản nháp/xuất bản
hiện có; không cần migration DB mới hoặc API mới.

`bodyHtml: {vi, en}` là trường tùy chọn cho Publication, Project, NewsItem và WebsiteSection.
Với Publication/Project, nội dung này là bài giới thiệu bổ sung, hiển thị dưới tóm tắt;
thiếu/null/chuỗi rỗng không bổ sung nội dung và không ghi đè tóm tắt hoặc mục tiêu.
Với NewsItem/WebsiteSection, khi thiếu/null, website vẫn hiển thị `body` cũ;
editor dựng các đoạn văn cũ thành HTML đã escape để chỉnh.
Lần chỉnh nội dung đầu tiên lưu HTML của cả hai ngôn ngữ và chuyển `body` thành mảng rỗng
để không gửi lại các đoạn cũ đã thay thế. `bodyHtml` có chuỗi trống nghĩa là chủ động xóa
nội dung, không phục hồi các đoạn cũ. Trường `body` vẫn có trong hợp đồng để đọc dữ liệu
cũ nhưng không ghi đè nội dung mới. Không cần migration schema DB mới.

Mỗi ngôn ngữ cho phép tối đa 200.000 ký tự HTML, tổng JSON tiếp tục giới hạn 4 MB.
BE dùng Bean Validation/jsoup để từ chối thẻ, thuộc tính và URL ngoài các định dạng editor
cho phép. FE dùng DOMPurify trước khi nhận nội dung dán và trước khi hiển thị, chỉ cho
phép liên kết HTTP(S)/đường dẫn nội bộ/anchor và ảnh HTTP(S)/đường dẫn nội bộ.
Không hỗ trợ nhúng script, iframe, video, HTML tùy ý, màu/font tùy ý hoặc ảnh base64.
Ảnh upload giữ đường dẫn tương đối trong DB và được resolve theo backend origin khi hiển thị.

## Lưu, xem trước, xuất bản

- **Lưu nháp** lưu ở BE, không thay bản công khai.
- **Xem trước** hiển thị chính website trong iframe cùng origin, gồm nội dung chưa lưu.
  Có lựa chọn desktop/mobile và mở cửa sổ riêng. Không truyền token hoặc bản nháp qua URL.
- **Xuất bản** lưu nội dung đang chỉnh sửa và thay bản công khai.
- Website công khai đọc `GET /api/public/website`, không gửi bearer token.
  BE chỉ trả nội dung đã chọn theo thứ tự của tab, loại mục và bản ghi bị ẩn;
  không trả thư viện đầy đủ hoặc nội dung chưa được chọn cho khách xem website.
  FE vẫn hiển thị khung trang và mục header cố định khi nội dung trang thiếu.
- Revision khác phiên đang lưu trả lỗi 409; tải lại bản nháp trước khi chỉnh tiếp.

## Dữ liệu và backend

Nội dung dùng khóa `PUBLIC_WEBSITE_CONTENT` trong bảng `system_configs` hiện có,
giữ riêng bản nháp và bản đã xuất bản. Migration **V72** từ phần CMS trước phải
được áp dụng để lưu/xuất bản. Nội dung trong các tab được lưu bằng JSON hiện có.

`pages` là trường bổ sung: dữ liệu cũ không có trường này được FE chuyển sang các trang
của mẫu khi đọc. Khi `pages: []` hoặc thiếu một trang, FE khôi phục cấu trúc trang và
tên menu; tiêu đề và đoạn giới thiệu của trang bị thiếu để trống. Trường nội dung đã
chủ động để trống vẫn giữ trống. Sau lần lưu tiếp theo, cấu trúc trang được lưu cùng bản nháp.

`contentLibrary` chứa các mục bổ sung dùng chung; `pages[].sectionIds` chứa danh sách ID
đã chọn theo thứ tự. `sectionIds: []` nghĩa là tab không có nội dung bổ sung được chọn.
Các trường mới là tùy chọn: khi đọc nội dung cũ, FE chuyển `pages[].sections` vào thư viện,
giữ các mục đang ẩn nhưng không tự chọn chúng. Các mục khác nhau trùng ID được cấp ID riêng.
Sau lần lưu tiếp theo, thư viện và lựa chọn được lưu trong JSON hiện có; không cần migration
schema mới. BE vẫn đọc được nội dung cũ chưa có thư viện và dùng `sections` khi `sectionIds`
không được cung cấp. Trong cấu trúc mới, ID được chọn phải tồn tại trong thư viện và không
trùng trong cùng tab. Giới hạn: 100 mục được chọn/tab, 5000 mục thư viện và tổng JSON 4 MB.
Endpoint công khai trả các `sections` đã resolve, không trả thư viện hay `sectionIds` riêng.

Các API ADMIN hiện có tiếp tục sử dụng `/api/admin/website`, `/draft`, `/publish`, `/images`.
Ảnh PNG/JPEG/WebP tối đa 5 MB lưu ở `uploads/website`; frontend dùng origin API hiện tại
để đọc ảnh. Ảnh/font mẫu nằm trong `client/public/lab-website`, gồm giấy phép font OFL.
Kiểu dữ liệu và dữ liệu mẫu dùng chung nằm trong `contracts/website-content.ts`
và `contracts/website-demo-content.json`. Docker đã được bổ sung copy/mount thư mục này.

Các form liên hệ/inquiry từ mẫu vẫn là minh họa, không gửi email hoặc CV.
Website, Admin, Manager và User dùng chung cổng FE 5173, chỉ chạy một terminal.
`dev:admin`, `dev:manager`, `dev:user` là alias của cùng lệnh `dev`.
Docker Compose dùng một service `frontend`; volume node_modules của Admin cũ được giữ để tái sử dụng.

Khách xem website không cần đăng nhập. Sau đăng nhập, ADMIN vào `/admin/dashboard`,
LAB_MANAGER vào `/app/lab-overview`, STUDENT có membership ACTIVE vào `/app/dashboard`.
STUDENT chưa được duyệt hoặc không còn membership ACTIVE trở về `/vi`.
Toàn bộ `/app` kiểm tra hồ sơ hiện tại từ BE, không dùng membership lưu cũ để mở quyền.

Sinh viên chưa được duyệt nộp CV trong phần **Hồ sơ tham gia** trên website; form và
API dùng lại luồng hiện có. Có thể xem trạng thái đơn, nộp lại theo quy tắc cũ và
bấm **Cập nhật trạng thái**. Hồ sơ tài khoản được làm mới mỗi 30 giây và khi quay lại cửa sổ.
Sau khi được duyệt, website hiện **Vào hệ thống**. Đăng xuất trở về website.

Nút **Tham gia** trên header được thay bằng **Đăng nhập** và **Đăng ký**. Khi chưa đăng nhập,
không mở form nộp giấy tờ. Luồng đăng nhập/đăng ký (kể cả Google) đưa sinh viên chưa được duyệt
về phần hồ sơ trên website; chuyển giữa hai form vẫn giữ địa chỉ quay lại.
Header của tài khoản đã đăng nhập hiện **Nộp hồ sơ** hoặc **Vào hệ thống** theo quyền hiện tại.
Trên màn hình nhỏ, các liên kết đăng nhập/đăng ký nằm trong menu điều hướng.

BE chặn API nội bộ khi STUDENT chưa có membership active. Ngoại lệ cần cho đăng ký:
GET `/users/me`, `/auth/me`, `/labs`, `/labs/{id}`, `/applications/users/{id}` và
POST `/labs/{id}/apply`, `/applications/labs/{id}/apply`. Đơn của người dùng khác bị từ chối
(trừ ADMIN). Các kiểm tra quyền theo lab/role hiện có tiếp tục áp dụng.
Khởi động lại BE và FE để nạp cấu hình mới. Thay đổi truy cập không cần migration DB mới.

Build/compile/lint chỉ xác nhận mã nguồn và cấu hình tương ứng; không chứng minh
lưu/xuất bản trên DB thực, hiển thị trình duyệt hoặc triển khai Docker.
