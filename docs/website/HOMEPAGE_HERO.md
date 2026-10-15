# Homepage Hero — nội dung quản trị qua CMS

Kiểm tra ngày 15/10/2026 (giờ Việt Nam), trên commit nền `61b7eb7`. Đã đối chiếu implementation và các handover trong `docs/website/`. `PROJECT_CONTEXT_FOR_CHATGPT.md` không còn có trong checkout tại thời điểm bắt đầu nhiệm vụ này.

Không commit, push, deploy, migrate hay ghi dữ liệu production. Các thao tác upload/save/publish bằng trình duyệt chỉ thực hiện với tài khoản giả và mock API tại localhost.

## Cấu hình

Cấu hình mới là trường tùy chọn `pages[].hero`, chỉ được phép trên page có `template: "home"`:

```json
{
  "title": { "vi": "Tiêu đề chính", "en": "Main title" },
  "eyebrow": { "vi": "Dòng giới thiệu nhỏ", "en": "Eyebrow introduction" },
  "description": { "vi": "Mô tả tiếng Việt", "en": "English description" },
  "primaryCta": {
    "label": { "vi": "Xem sản phẩm", "en": "Explore products" },
    "url": { "vi": "/vi/products", "en": "/en/products" }
  },
  "secondaryCta": {
    "label": { "vi": "Kết nối", "en": "Meet the team" },
    "url": { "vi": "#research", "en": "/en/people" }
  },
  "backgroundImage": "/api/uploads/website/hero.png",
  "backgroundAlt": { "vi": "Ảnh nền", "en": "Background image" },
  "footerText": { "vi": "Cùng khám phá", "en": "Discover together" }
}
```

- Thiếu `hero` hoặc `hero: null`: frontend dùng chính xác fallback hiện tại từ `LAB_BRAND` và ảnh `/images/earth.jpg`. Các CTA, footer và alt đã có override trong `content.copy` vẫn được tôn trọng. Đường dẫn CTA mặc định theo slug hiện có của Research/About; media override của `SiteImage` vẫn áp dụng như trước.
- Không suy Hero từ `page.title`/`description` cũ, vì implementation trước không dùng các trường này làm Hero. Không tự ghi fallback vào CMS khi đọc hoặc mở form.
- Khi đã có cấu hình, chuỗi rỗng là nội dung được chủ động xóa, không tự thay bằng mặc định. Nhãn hoặc URL CTA rỗng sẽ ẩn riêng nút ở ngôn ngữ tương ứng. Ảnh nền rỗng sẽ bỏ ảnh, vẫn giữ nền/shade Hero.
- URL CTA được nhập riêng VI/EN và dùng nguyên đường dẫn đã nhập. Preview thêm `preview=1` cho đường dẫn public nội bộ theo cơ chế SiteLink hiện có. Chấp nhận HTTP(S), đường dẫn nội bộ bắt đầu `/` và `#anchor`; từ chối JavaScript/data/file URL, protocol-relative URL và khoảng trắng.
- Backend kiểm tra ngôn ngữ/field bắt buộc; các chuỗi ngắn và URL tối đa 2000 ký tự/ngôn ngữ, mô tả tối đa 20000. Ảnh dùng quy tắc asset URL hiện có; upload tiếp tục PNG/JPEG/WebP, tối đa 5 MB, kiểm tra định dạng thật tại backend.
- TypeScript contract và Java DTO đồng bộ. Public projection giữ Hero trong page, vẫn loại private mappings/library theo cơ chế cũ. Save Draft/Publish và revision conflict giữ nguyên; không cần migration hay collection/bảng mới.

## Cách sử dụng

1. CMS → **Nội dung các tab → Trang chủ → Chỉnh sửa Hero / Banner giới thiệu**.
2. Form mở bằng cấu hình đang có, hoặc đúng nội dung fallback hiện tại. Nhập độc lập VI/EN cho từng trường.
3. Upload ảnh nền bằng API ADMIN hiện có. Trong lúc upload, khóa trường và thao tác Apply/Preview/đóng modal. Nếu upload thất bại, giữ URL ảnh đã chọn trước đó. File cũ không bị xóa vật lý.
4. **Xem trước Hero** gửi nội dung đang soạn qua cơ chế preview cùng origin, kể cả chưa Apply. Có thể chuyển VI/EN trong iframe, dùng nút Mobile/Desktop để xem responsive. Preview không ghi draft hay published.
5. **Hủy** bỏ thay đổi trong form, không đổi working draft. **Áp dụng Hero vào bản nháp** mới đưa cấu hình vào nội dung đang chỉnh sửa.
6. **Lưu nháp** lưu cấu hình nhưng giữ nguyên bản published. **Xuất bản** qua hộp xác nhận hiện có mới cập nhật public. Nếu revision conflict 409, tải lại bản mới nhất và rà soát lại như workflow cũ.

Giữ nguyên markup/classes, tỷ lệ/cách phủ ảnh `cover`, shade, icons, hiệu ứng, bố cục và các breakpoint Hero hiện có. Thêm bảo vệ wrap cho chuỗi dài để nội dung biên tập không tràn khung. Các section dưới Hero, header/footer thương hiệu, gallery, thành viên và Scholar không đổi. Tiêu đề tab Homepage theo tiêu đề Hero đã cấu hình; fallback vẫn giữ slogan cũ.

## File thay đổi

- `contracts/website-content.ts` — schema Hero và page.hero tùy chọn.
- `contracts/website-hero.ts` — fallback dùng chung, patch Homepage không mutate, kiểm tra URL.
- `client/src/modules/website/components/HeroEditor.tsx` — form Hero, upload, Preview, Apply/Cancel.
- `client/src/modules/website/components/PageContentEditor.tsx` — chỉ hiển thị editor Hero ở Trang chủ.
- `client/src/modules/website/components/EditorFields.tsx` — callback trạng thái upload, callback mới nhất và ngăn cập nhật sau unmount cho ImageField hiện có.
- `client/src/modules/website/public/components/lab-provider.tsx` — resolve Hero từ nội dung published/preview.
- `client/src/modules/website/public/components/views/home.tsx` — Hero đọc cấu hình CMS.
- `client/src/modules/website/pages/PublicWebsitePage.tsx` — title tab Homepage theo Hero.
- `client/src/modules/website/public/public.css` — wrap an toàn cho nội dung dài, giữ thiết kế.
- `client/src/modules/website/README.md` — hướng dẫn biên tập.
- `client/tests/website.test.ts` — 6 test Hero mới.
- `server/src/main/java/com/web/labportalbackend/admin/website/dto/WebsiteContent.java` — DTO và validation Hero.
- `server/src/main/java/com/web/labportalbackend/admin/website/service/impl/WebsiteContentServiceImpl.java` — giữ Hero trong public projection, giới hạn cấu hình ở Homepage.
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteHeroTest.java` — 9 test Hero mới.
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteProductsTest.java` — cập nhật constructor page tùy chọn.
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteGalleryTest.java` — cập nhật constructor page tùy chọn.
- `docs/website/HOMEPAGE_HERO.md` — tài liệu này.

## Kiểm thử

- `npm run test:website`: **22/22 đạt**, thêm 6 test Hero: missing/null fallback, copy override cũ/slug tùy chỉnh, Apply chỉ đổi Homepage, dữ liệu rỗng có chủ ý, JSON normalization/save/preview/public round-trip và URL an toàn.
- `tsc --noEmit -p tsconfig.json`: đạt; cũng chạy trong build.
- `npm run lint`: 0 lỗi, 31 cảnh báo cũ ở module khác.
- `VITE_API_BASE_URL=http://127.0.0.1:4181 npm run build`: đạt; còn cảnh báo bundle lớn có sẵn. Origin chỉ dùng cho mock API local, không đổi env production.
- `sh mvnw -q verify`: exit 0; Surefire **1785 test được báo cáo, 1753 thành công, 32 bỏ qua, 0 failure/error**, 150 suites. 9 test backend mới bao gồm JSON cũ/mới, validation cả hai ngôn ngữ, dữ liệu rỗng, quyền ADMIN, draft/publish/revision, public privacy và cấu hình chỉ được ở Homepage.
- `git diff --check`: đạt.

Browser smoke bằng build thật + mock API local:

- CMS Homepage có editor; Products không có. Form mặc định khớp Hero cũ; sửa rồi Hủy/mở lại không ghi thay đổi.
- Nhập title, eyebrow, mô tả, cả hai CTA label/URL, alt/footer VI/EN. URL JavaScript bị chặn trước Apply/Preview.
- Upload ảnh thật trong fixture PNG; Apply khóa khi upload; mô phỏng một upload 503 giữ nguyên URL đã tải thành công. Đã xác nhận ảnh nền decode 600x180 và hiển thị `cover` trên trang.
- Preview VI/EN đúng nội dung, URL query/anchor và ảnh đang sửa, kể cả chưa Apply. Lưu nháp giữ public Hero cũ; publish mock mới đổi public. Nội dung đã Hủy sau đó không thay dữ liệu đã lưu.
- Desktop 1280px; public và CMS mobile 390px; light/dark. Tiêu đề dài liền 180 ký tự vẫn wrap, không tràn ngang. Preview xác nhận ảnh nền rỗng không phục hồi ảnh cũ và CTA tiếng Anh có nhãn rỗng không phục hồi nhãn mặc định.

Giới hạn: CMS/browser dùng mock API để không tác động production; DTO, quyền, upload hiện có và save/publish service được kiểm tra bằng Java tests. Chưa kiểm tra Safari/điện thoại thật hoặc audit screen reader đầy đủ. Không thêm dependencies hoặc thay lockfile; không chạy pipeline deploy.
