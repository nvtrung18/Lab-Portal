# Hero Rich Text và Our Story — bàn giao

Triển khai ngày 15/10/2026 trên branch `feature/cms-hero-story-richtext`, tách từ
`duongnt`. Working tree sạch trước khi bắt đầu. `PROJECT_CONTEXT_FOR_CHATGPT.md`
không có trong checkout; đã đối chiếu các handover trong `docs/website/` và mã nguồn.
Không commit, push, tạo PR, merge, deploy, migration hoặc ghi dữ liệu production.

## Quản trị nội dung

1. CMS → **Nội dung các tab → Trang chủ → Chỉnh sửa Hero / Banner giới thiệu**.
   Trường **Đoạn mô tả** dùng Rich Text Editor, chuyển **Tiếng Việt / English** để
   soạn từng bản. Các trường title, eyebrow, CTA, ảnh nền, alt và footer Hero giữ
   nguyên chức năng.
2. CMS → **Nội dung các tab → Giới thiệu → Chỉnh sửa Câu chuyện / Our Story**.
   Sửa nhãn nhỏ, tiêu đề, đoạn giới thiệu có định dạng và nhãn CTA riêng cho VI/EN.
   CTA tiếp tục dẫn đến trang Thành viên theo routing hiện có; nhãn rỗng ẩn nút
   trong ngôn ngữ tương ứng. Đây là section Our Story đang có, không phải section
   mới trong Content Library và không tạo bản sao trên About.
3. Bôi đen rồi chọn **In đậm**, **In nghiêng**, **Chèn / sửa liên kết** hoặc dùng
   **Ctrl/Cmd+B**, **Ctrl/Cmd+I**. **Enter** tạo đoạn; **Shift+Enter** xuống dòng.
   Chọn liên kết rồi mở nút liên kết để sửa URL; dùng **Gỡ liên kết** để bỏ.
   Có undo/redo. Toolbar này không hỗ trợ ảnh, headings, danh sách hay style tùy ý.
   Editor bài viết hiện có vẫn giữ nguyên bộ công cụ đầy đủ.
4. **Xem trước Hero / Câu chuyện** xem nội dung đang soạn kể cả chưa Apply; dùng
   VI/EN, Desktop/Mobile trong preview. **Hủy** không đổi working draft.
5. **Áp dụng … vào bản nháp → Xem trước → Lưu nháp → Xuất bản**. Apply chỉ đổi bản
   đang biên tập; Save Draft giữ nguyên public. Publish vẫn có hộp xác nhận và
   revision check 409. Khi conflict, tải lại và rà soát bản mới nhất theo workflow cũ.

## Schema và tương thích

- `pages[template=home].hero.description` vẫn là plain text VI/EN cũ. Trường mới
  `hero.descriptionHtml?: {vi, en} | null` lưu HTML; không đoán nội dung từ dấu `<`.
  Thiếu/null HTML dùng plain text, còn HTML rỗng là chủ động xóa mô tả. Mở form
  chuyển text cũ thành HTML đã escape để giữ literal markup, ký tự và xuống dòng;
  không tự ghi đè trường plain text hay published data.
- `pages[template=about].ourStory?: {eyebrow, title, description, ctaLabel,
  descriptionHtml?} | null` là cấu hình độc lập. Các trường văn bản là `{vi, en}`.
  `description` giữ plain text fallback; `descriptionHtml` cùng semantics với Hero.
  Thiếu/null toàn bộ `ourStory` dùng đúng brand introduction, tên và copy overrides
  Our Story/CTA hiện tại. Không lấy nội dung từ Hero đã biên tập.
- Title/eyebrow/CTA tối đa 2.000 ký tự/ngôn ngữ; plain description 20.000;
  formatted description 200.000 ký tự HTML/ngôn ngữ. Ngôn ngữ thiếu/null trong một
  cấu hình mới bị backend từ chối; chuỗi rỗng được chấp nhận. Giới hạn tổng CMS
  payload 4 MB không đổi. Không âm thầm cắt nội dung.
- TypeScript contract, Java DTO, public projection và normalization giữ cả hai
  cấu hình qua save/preview/publish/JSON round-trip. Chỉ Home được có Hero, chỉ
  About được có Our Story. Không thêm collection, endpoint hay migration.
- Các section trong thư viện, ảnh, gallery, Scholar, thông tin học thuật và portal
  không bị thay đổi. About giữ vị trí/bố cục section hiện tại. Public render dùng
  class riêng cho prose để kế thừa typography của Hero/About, không lấy style của
  rich body bài viết. Hero vẫn giữ ảnh `cover`, shade, CTA và các breakpoint cũ.

## HTML và an toàn

Frontend dùng lại `BilingualRichTextField`/Tiptap với variant `intro` và DOMPurify.
Chỉ cho phép `p`, `br`, `strong/b`, `em/i`, `a`; thuộc tính liên kết phù hợp
`href`, `target`, `rel`, `title`. URL dùng HTTP(S), đường dẫn `/…` hoặc `#anchor`;
JavaScript/data URL và protocol-relative URL bị chặn. Liên kết `_blank` được thêm
`rel="noopener noreferrer"`. Style, script, event handlers, SVG/iframe và ảnh bị loại.
Plain text render bằng React, không đưa thẳng vào `dangerouslySetInnerHTML`.

Backend mở rộng validator Jsoup hiện có bằng `@SafeWebsiteHtml(proseOnly=true)`.
Save/Publish API dùng Bean Validation để **từ chối** HTML/URL ngoài allowlist;
không tự cắt hoặc âm thầm thay nội dung đã lưu. Public và editor vẫn làm sạch độc
lập để bảo vệ preview hoặc nội dung cũ. Allowlist bài viết không đổi.

## Kiểm thử

- `git diff --check`: đạt.

- `npm run test:website`: **27/27 đạt** (5 regression mới: exact fallback/copy,
  escape legacy line breaks/literal markup, edit isolation, JSON/normalization,
  explicit empty rich content và độc lập VI/EN).
- `npm run lint`: 0 lỗi, 31 cảnh báo có sẵn ở module khác; không thêm warning.
- `VITE_API_BASE_URL=http://127.0.0.1:4181 npm run build`: đạt, bao gồm TypeScript
  no-emit check. Còn cảnh báo chunk lớn có sẵn. Origin chỉ dùng cho mock local,
  không chỉnh env hay tạo gói deploy production.
- Backend focused tests Hero/Gallery/Products: đạt. `sh mvnw -q verify`: exit 0;
  1.793 test được báo cáo, **1.761 thành công, 32 bỏ qua, 0 failure/error**, 150 suites.
  `WebsiteHeroTest` có 17 test (8 mới), gồm legacy plain/null, rich JSON, XSS
  cho hai field/cả hai ngôn ngữ, giới hạn/missing/empty, draft/publish/conflict,
  public projection, edit isolation, ADMIN và template validation. Log verify
  vẫn có nhiễu scheduler/H2 về `booking_outbox_event` từ test nền; không có test lỗi.
- Browser dùng production build + API/tài khoản giả tại localhost: legacy fallback,
  editor Hero/Story, Cmd+B/I, Enter/Shift+Enter, URL nguy hiểm bị chặn, thêm/sửa link,
  chuyển ngôn ngữ, preview trước Apply, mobile preview, Apply/Save Draft giữ public
  cũ, Publish và reload giữ HTML. Our Story chỉ có một section; CTA `/vi/people`
  không đổi; Hero render weight 700/italic và ảnh `cover`. Hủy/mở lại không giữ các
  sửa đổi chưa áp dụng. Paste HTML chứa script/event/style/ảnh/JavaScript URL vào
  editor cho kết quả không có phần nguy hiểm, không thực thi script, giữ link hợp lệ.
- Đã kiểm tra public/CMS VI/EN, desktop/mobile 390px và light/dark. Không thấy tràn
  ngang trong các vùng thay đổi. Backend kiểm tra service/DTO thực; browser mock
  không chứng minh kết nối DB production hoặc publish trên production.

Giới hạn: chưa kiểm tra Safari/điện thoại thật hoặc audit screen reader đầy đủ.
Không thêm dependency/lockfile. DevOps cần build cả backend và frontend cùng phiên
bản qua quy trình triển khai được duyệt để API nhận các field mới.

## File thay đổi

Contracts:

- `contracts/website-content.ts`
- `contracts/website-story.ts` (mới)
- `contracts/website-rich-text.ts` (mới; helper escape plain text dùng chung)

CMS:

- `client/src/modules/website/components/HeroEditor.tsx`
- `client/src/modules/website/components/OurStoryEditor.tsx` (mới)
- `client/src/modules/website/components/PageContentEditor.tsx`
- `client/src/modules/website/components/BilingualRichTextField.tsx`
- `client/src/modules/website/components/RichTextEditor.tsx`
- `client/src/modules/website/components/rich-text.css`
- `client/src/modules/website/richText.ts`

Public:

- `client/src/modules/website/public/components/intro-text-body.tsx` (mới)
- `client/src/modules/website/public/components/lab-provider.tsx`
- `client/src/modules/website/public/components/views/home.tsx`
- `client/src/modules/website/public/components/views/information.tsx`
- `client/src/modules/website/public/public.css`

Backend/tests/docs:

- `server/src/main/java/com/web/labportalbackend/admin/website/dto/WebsiteContent.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/service/impl/WebsiteContentServiceImpl.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/validation/SafeWebsiteHtml.java`
- `server/src/main/java/com/web/labportalbackend/admin/website/validation/WebsiteHtmlValidator.java`
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteHeroTest.java`
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteGalleryTest.java` (chỉ bổ sung constructor arg tùy chọn)
- `server/src/test/java/com/web/labportalbackend/admin/website/WebsiteProductsTest.java` (chỉ bổ sung constructor arg tùy chọn)
- `client/tests/website.test.ts`
- `client/src/modules/website/README.md`
- `docs/website/HOMEPAGE_HERO.md`
- `docs/website/HERO_AND_OUR_STORY.md` (tài liệu này)
