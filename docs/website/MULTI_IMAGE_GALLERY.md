# Multi-Image Gallery — ALELA

Ngày kiểm tra: 15/10/2026 (giờ Việt Nam). Thay đổi chỉ nằm trong mã nguồn và môi trường kiểm thử local; không commit, push, deploy, migration hay ghi dữ liệu production. `PROJECT_CONTEXT_FOR_CHATGPT.md` có sẵn được giữ nguyên.

## Dữ liệu và tương thích

`WebsiteSection`, `ResearchArea`, `Project`, `NewsItem` dùng chung các trường:

```json
{
  "image": "/api/uploads/website/cover.png",
  "imageAlt": { "vi": "Ảnh đại diện", "en": "Cover image" },
  "imageCaption": { "vi": "Chú thích ảnh đại diện", "en": "Cover caption" },
  "gallery": [
    {
      "id": "photo-stable-id",
      "src": "/api/uploads/website/diagram.png",
      "alt": { "vi": "Sơ đồ hệ thống", "en": "System diagram" },
      "caption": { "vi": "Nội dung chú thích", "en": "Image caption" }
    }
  ]
}
```

- `image` vẫn là cover cũ, không tự đưa vào `gallery`. Tối đa 15 ảnh bổ sung, tức tối đa 16 ảnh nếu có cover. Thứ tự gallery chính là thứ tự mảng.
- `gallery`, `imageAlt`, `imageCaption` có thể thiếu hoặc null trong JSON cũ. Không có tác vụ chuyển đổi dữ liệu tự động. Nội dung một ảnh cũ vẫn sử dụng đúng URL đã lưu.
- Mỗi ảnh gallery có ID ổn định, URL không rỗng, alt/caption VI/EN (cho phép chuỗi rỗng). ID phải duy nhất trong cùng bài; có thể dùng lại URL/ID ở bài khác.
- Java DTO kiểm tra tối đa 15 ảnh, ID theo quy tắc identifier hiện có, URL theo quy tắc asset hiện có, URL tối đa 2000 ký tự, alt/caption tối đa 2000 ký tự/ngôn ngữ. UI giới hạn metadata tương ứng.
- Products tiếp tục dùng Content Library, không có collection riêng. Gallery được giữ qua normalization, chọn/chuyển sections, preview và Save Draft/Publish. Public projection Research giữ gallery/metadata trong khi vẫn loại `labId`; member privacy và Scholar không đổi.
- Không thay đổi rich HTML, trường body cũ, revision conflict hoặc quyền ADMIN. Không có migration schema database.

## Thao tác CMS

1. Mở section trong Content Library hoặc bản ghi Research/Projects/News.
2. Trong **Ảnh bài viết**, chọn **Thêm nhiều ảnh**, hoặc kéo thả các file vào khối ảnh. Nếu chưa có cover, ảnh upload thành công đầu tiên làm cover; các ảnh sau vào gallery theo thứ tự chọn.
3. Chỉ nhận PNG/JPEG/WebP, tối đa 5 MB/ảnh. Frontend kiểm tra cả lô trước khi gọi API; backend tiếp tục kiểm tra kích thước và magic bytes qua API ADMIN `/api/admin/website/images` hiện có. Không thêm endpoint upload mới.
4. Nhập alt/caption VI/EN cho cover và từng ảnh; dùng nút lên/xuống để đổi thứ tự. **Đặt làm cover** hoán đổi ảnh được chọn với cover cũ, giữ cả hai bộ metadata. Có thể thay cover bằng URL đã upload.
5. **Bỏ ảnh** chỉ gỡ liên kết khỏi bài. Không gọi API xóa file; file có thể vẫn được dùng ở bài khác. **Bỏ ảnh đại diện** không xóa gallery.
6. Chờ upload hoàn tất. Trong lúc upload, khóa thao tác ảnh, Apply, Preview và đóng trình biên tập để tránh lưu thiếu ảnh. Các trường văn bản khác vẫn giữ thay đổi nếu được chỉnh trong lúc chờ.
7. **Áp dụng → Lưu nháp → Xem trước → Xuất bản** như trước. Upload file không tự lưu bài hoặc xuất bản.

Upload theo từng file, có tiến trình. Lỗi một file không loại bỏ các ảnh đã tải thành công và không xóa ảnh cũ; UI báo rõ tên file lỗi. Vượt số lượng bị từ chối, không âm thầm cắt bớt lô ảnh. Tệp đã upload nhưng chưa được Apply hoặc bị bỏ khỏi draft vẫn được giữ trên storage, giống cơ chế upload hiện có; chưa bổ sung tác vụ dọn file.

## Website công khai

- Không ảnh: không dựng gallery trống. Một ảnh: giữ khung ảnh hiện có, không có hàng thumbnail; vẫn có thể mở lightbox.
- Nhiều ảnh: ảnh chính, số thứ tự/tổng và một hàng thumbnail cuộn ngang. Chọn thumbnail để đổi ảnh chính; click ảnh để mở lớn. Caption trên trang tối đa hai dòng; caption đầy đủ trong lightbox.
- Editorial gallery nằm ngoài phần rich HTML thu gọn: chỉ hiển thị một khung ảnh và một hàng thumbnail, không xếp toàn bộ ảnh thành nhiều hàng. Nút Đọc tiếp/Thu gọn hoạt động độc lập và giữ HTML gốc.
- Structured cards chỉ đọc `image` làm cover (alt đã nhập nếu có). Gallery đầy đủ ở trang chi tiết. Nếu người biên tập chủ động để cover rỗng nhưng vẫn giữ gallery, gallery chi tiết dùng ảnh đầu tiên làm ảnh chính; muốn có ảnh trong card thì chọn lại cover.
- Mọi ảnh gallery đều `contain`, căn giữa, không phóng/cắt khi hover. Hero/background, portrait và Scholar không đổi.
- Lightbox tái sử dụng native dialog: nút trước/sau, phím trái/phải, đóng ESC/nút đóng, giữ focus trong dialog và trả focus về ảnh sau khi đóng. Có điều khiển chạm vuốt và nút 44px. Khóa cuộn nền khi mở; ảnh lightbox chỉ được dựng/tải khi mở. Giao diện dùng các màu theme hiện có, hỗ trợ dark mode.

## File thay đổi (22 file)

### Contracts

- `contracts/website-content.ts`
- `contracts/website-gallery.ts` (mới)

### CMS và tài liệu frontend

- `client/src/modules/website/components/GalleryEditor.tsx` (mới)
- `client/src/modules/website/components/EditorFields.tsx`
- `client/src/modules/website/components/SectionEditor.tsx`
- `client/src/modules/website/components/RecordEditor.tsx`
- `client/src/modules/website/components/ContentLibraryEditor.tsx`
- `client/src/modules/website/pages/AdminWebsitePage.tsx`
- `client/src/modules/website/README.md`

### Public UI

- `client/src/modules/website/public/components/article-gallery.tsx` (mới)
- `client/src/modules/website/public/components/dynamic-sections.tsx`
- `client/src/modules/website/public/components/shared.tsx`
- `client/src/modules/website/public/components/ui/dialog.tsx`
- `client/src/modules/website/public/components/views/detail.tsx`
- `client/src/modules/website/public/public.css`

### Backend và tests

- `server/src/main/java/com/web/labportalbackend/admin/website/dto/WebsiteContent.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/service/impl/WebsiteContentServiceImpl.java`
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteGalleryTest.java` (mới)
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteGalleryUploadTest.java` (mới)
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteProductsTest.java`
- `client/tests/website.test.ts`
- `docs/website/MULTI_IMAGE_GALLERY.md` (tài liệu này)

## Kết quả kiểm thử

- `npm run test:website`: **16/16 đạt**, gồm 7 test gallery mới: 0/1/nhiều ảnh, cover legacy/null, multi-upload order, metadata VI/EN, hoán đổi cover, reorder, giới hạn, file selection, normalization/transfer và JSON round-trip.
- `tsc --noEmit -p tsconfig.json`: đạt (cũng chạy trong build).
- `npm run lint`: 0 lỗi, 31 cảnh báo có sẵn ở các module không liên quan.
- `VITE_API_BASE_URL=http://127.0.0.1:4181 npm run build`: đạt; còn cảnh báo bundle lớn có sẵn. Origin trên chỉ dùng cho mock API local, không đổi env production.
- `sh mvnw -q verify`: exit 0. Surefire: **1776 test được báo cáo, 1744 thành công, 32 bỏ qua, 0 failure/error**, 149 suites. Có 11 test Java gallery/upload mới. Các test bỏ qua phụ thuộc điều kiện môi trường; log có nhiễu scheduler/H2 về bảng `booking_outbox_event` trong test nền, không có test thất bại. Không sửa module booking.
- `git diff --check`: đạt.

Browser smoke dùng ứng dụng build thật với HTTP mock API, tài khoản giả và storage tạm **chỉ trên localhost**:

- Chọn 3 file một lần: có 1 cover + 2 gallery; Apply khóa trong lúc upload; thumbnails đầy đủ.
- Nhập alt/caption VI/EN; đổi thứ tự bằng nút; thay cover giữ caption cũ; bỏ ảnh giữ nguyên file. Kiểm tra từ chối SVG, file >5 MB, lô vượt số lượng và lỗi mạng một phần vẫn giữ file thành công.
- Apply → Save Draft giữ public cũ; Products preview nhận 3 ảnh; publish mock local xong public mới nhận gallery. Kiểm tra JSON mock đã lưu giữ cả hai ngôn ngữ.
- Mở CMS Research/Projects/News xác nhận cùng trình biên tập gallery. Kiểm tra CMS ở desktop 1280px và mobile 390px, không tràn ngang.
- Public: 0/1/nhiều ảnh, thumbnail 15 ảnh một hàng, portrait 300x900, wide 1200x300, small 48x48; gallery nằm cạnh nội dung đang thu gọn. Research/Projects/News cards chỉ có cover, chi tiết mỗi bài có gallery.
- Lightbox VI/EN, caption, ảnh trước/sau, phím trái/phải, ESC, trả focus, khóa/mở lại cuộn nền, object-fit contain, light/dark và mobile 390px.

Giới hạn: chưa thử Safari/điện thoại thật hoặc cử chỉ vuốt trên thiết bị cảm ứng thật; chưa audit toàn bộ bằng screen reader. Browser CMS dùng mock API để không tác động production; quyền, DTO, upload storage và save/publish service được kiểm tra riêng bằng Java tests. Không thêm dependencies hay thay lockfile.
