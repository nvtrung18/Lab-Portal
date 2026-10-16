# Public Search & Filter và đọc danh sách dài

Ngày kiểm tra: 16/10/2026 (Asia/Ho_Chi_Minh). Branch `feature/public-search-editorial`,
base `43f630d`, tách từ checkout sạch `duongnt`. Không chỉnh dữ liệu production,
không sửa collection/CMS JSON, backend DTO, routing legacy, Scholar hoặc portal.

## Nguyên nhân xác minh

- API published thực tại `https://api.alelanetwork.id.vn/api/public/website` trả
  `data.publications/projects/research/news` đều rỗng lúc kiểm tra. Nội dung có thật
  nằm ở `pages[].sections`: Publications có 101 `li` trong 4 `ol`, Projects có 15
  `li` trong `ul`. Các paragraph phân nhóm không được coi là công bố riêng.
- CMS chọn `sectionIds` từ Content Library; public projection trả các section đã
  resolve, không trả thư viện/draft. `PageHeading` trước đây render DynamicSections
  phía trên, trong khi `views/explore.tsx` chỉ tìm/lọc structured arrays phía dưới.
  Vì thế nội dung bài có trên trang nhưng số lượng kết quả luôn bằng 0.
- `ExpandableContent` áp `overflow:hidden` và `max-height` 5 dòng cho toàn bộ HTML.
  Trên snapshot thật, khung chỉ cao khoảng 119px trong khi nội dung cao hơn 10.000px.
  Expanded vẫn dùng chiều cao pixel đo được. Cơ chế này có thể giấu/cắt nội dung
  danh sách, marker, dòng và DOI ở biên khung; scroll trang không thay đổi crop.
  Không thấy `line-clamp` trong bibliography; line-clamp caption gallery không liên quan.
  Header sticky không phải nguyên nhân của bộ đếm 0; không sửa hành vi header.

## Tìm kiếm

- Component dùng chung cho Publications, Projects, Research, News. Search controls
  nằm trước nội dung. Mặc định vẫn xem được editorial gốc và các cards/rows structured.
- Index **chỉ nội dung đã resolve trên trang hiện tại**, ngôn ngữ hiện tại, không
  truy cập admin API, thư viện chưa chọn, draft hoặc section/record đang ẩn.
  Preview dùng working content từ LabProvider như cũ, không có persistence mới.
- DOM đã sanitize xác định ranh giới các `li` cấp ngoài cùng và các paragraph,
  heading, blockquote độc lập. Continuation paragraphs và nested lists ở trong `li`
  đi cùng mục cha; không tách mỗi paragraph trong một citation thành record mới.
  Không dùng regex bóc bibliography hay tự chia paragraph theo dấu câu/số thứ tự.
  Một paragraph chứa nhiều bài không rõ ranh giới vẫn là **một đoạn nội dung**.
- Tìm text gốc, title/eyebrow section và các URL liên kết an toàn (kể cả DOI chỉ nằm
  trong href). Kết quả là text đầy đủ, không line-clamp/cắt HTML; nút **Xem trong
  nội dung gốc** mở đúng section, focus/scroll tới mục có ID được tạo khi render.
  Nội dung gốc giữ markup, ảnh, links, `ol start`, số thứ tự và nhóm danh sách.
  ID của body có ngôn ngữ riêng để không tự coi cùng index VI/EN là cùng bài.
  Các ID/focus attributes chỉ ở DOM public; không ghi ngược CMS. Anchor theo vị trí
  block có thể đổi nếu admin sắp xếp lại HTML sau publish; không phải ID học thuật lâu dài.
- Structured Publications tìm title, mô tả/body, tác giả (tên thành viên hoặc ID/tên
  ngoài hệ thống đã lưu), năm, venue, loại và resource URLs. Projects tìm title, ID
  (khi được dùng làm mã), mô tả/body, category, period, objectives và area đã lưu.
  Mã đề tài ở rich HTML được tìm như text gốc. Research thêm topics; News thêm
  date/category/location và legacy/rich body.
- Tìm không phân biệt hoa/thường, dấu tiếng Việt/đ-Đ; bỏ khoảng trắng thừa. Nhiều
  từ khóa dùng AND, không phụ thuộc thứ tự. Không có fuzzy/semantic search hay dịch tự động.
- Metadata filter chỉ lấy từ structured records đang có trên trang: năm/loại/tác
  giả/lĩnh vực của Publications, status/area của Projects, category của News.
  Không bịa loại từ heading "Tạp chí quốc tế", không lấy năm từ DOI/prose, không suy
  trạng thái đề tài. Chỉ các tác giả/area thực được tham chiếu mới có trong dropdown;
  nếu nhãn liên kết chưa có, giữ ID đã lưu thay vì tạo tên mới.
- Khi bật metadata filter, văn bản editorial thiếu metadata bị loại và UI giải thích
  điều này; reset để tìm toàn bộ text. Ví dụ gõ `2026` tìm keyword trong bibliography,
  còn chọn Year `2026` chỉ lọc records có field year tương ứng.
- Đếm kết quả tách **bản ghi** và **mục danh sách/đoạn nội dung**, không tuyên bố tổng
  đó là số công bố/đề tài xác minh. Snapshot thật cho 105 đơn vị searchable ở trang
  Publications: 101 mục danh sách + 4 paragraph phân nhóm có chữ; paragraph chỉ có ảnh
  không bị đếm. Projects có 15 mục văn bản. Không tự deduplicate giữa editorial và
  structured bằng title gần giống; UI đếm kết quả theo nguồn, không đếm unique papers.
- Phân biệt dataset rỗng trong ngôn ngữ hiện tại, từ khóa/filter không khớp và lỗi
  tải backend. Lúc kiểm tra, body EN của các danh sách Publications/Projects trống;
  website báo chưa có nội dung EN thay vì tự dịch hoặc sao chép VI vào production.

## Đọc danh sách và scroll

- Body chứa `ol/ul` không áp giới hạn 5 dòng: hiển thị trọn vẹn trong flow tự nhiên,
  không crop từng item hay crop cả bibliography. Điều này áp dụng chung cho mọi
  editorial section, gồm Research/Products/News/Home khi có semantic lists.
- Prose thông thường vẫn có Đọc tiếp/Thu gọn độc lập và đo 5 dòng. Expanded dùng
  `max-height:none; overflow:visible`, tránh crop do pixel rounding, font/image load
  hoặc resize. Nội dung và markup giữ nguyên qua mở/thu gọn; keyboard focus tới link
  bị giấu vẫn mở body như trước. Chevron/reduced-motion styles giữ nguyên.
- Điều hướng từ kết quả và thu gọn từ cuối bài tính offset bằng chiều cao header
  thực tế +16px, không đổi sticky header. Source paragraph nhận focus, có outline;
  ID nguồn hỗ trợ deep link/reload. Quay lại kết quả giữ query/filter.
- Không thay gutter/list-style hoặc numbering của HTML gốc. Grouped lists reset
  theo markup; `ol.start` dùng giá trị DOM hợp lệ, không tạo `NaN` khi start sai.

## Kiểm thử

- `npm run test:website`: **30/30 đạt**. Ba regression mới kiểm tra text theo
  author/year/venue/DOI/code, normalization, mixed sources, explicit/unknown metadata,
  filter values, thứ tự ổn định và không mutate input.
- `/tests/website-search.browser.html`: **7/7 đạt** trên DOM/Tiptap sanitizer thật,
  với fixture mô phỏng cấu trúc CMS hiện tại (VI/EN, grouped ol/start, multiline
  citation, nested li, mixed paragraph không rõ ranh giới, DOI trong href, ảnh,
  empty HTML/legacy plaintext, hidden sections, XSS và structured fields).
  Harness chạy qua Vite dev, không thêm test framework/dependency và không được
  import vào production entry. CI Node tests không tự chạy browser harness.
- TypeScript no-emit check + Vite production build: đạt. ESLint: 0 lỗi, 31 cảnh báo
  cũ ở module khác. Cảnh báo chunk lớn có sẵn vẫn còn. `git diff --check`: đạt.
- `sh mvnw -q verify`: exit 0; **1.793 test báo cáo, 1.761 thành công, 32 bỏ qua,
  0 failure/error**, 150 suites. Không thay backend. Log scheduler/H2 về bảng
  `booking_outbox_event` vẫn xuất hiện như trước, không làm test thất bại.
- Browser dùng bản build/app thật với API local chứa snapshot public đọc từ website;
  không ghi hoặc publish lên production. `ProMESh` → 1 mục, `IEEE Access 2026` → 2
  mục, `QG25.05` → 1 đề tài; query không khớp và EN thiếu nội dung có empty state khác nhau.
- Fixture mixed thêm 2 records/collection: bộ lọc chỉ có metadata thật, year filter
  không kéo theo bibliography chưa được gán year. News hỗ trợ cả editorial và records.
- IAB kiểm tra desktop 1280px và mobile 390/320px, VI/EN, light/dark; không tràn ngang.
  Source target cách đáy sticky header khoảng 16px ở desktop và mobile. 101 mục giữ
  nguyên trong DOM, chiều cao viewport bằng inner body và overflow visible. Research
  prose mở hết/thu gọn giữ HTML và scroll về heading; Products mở hết không crop.
- Google Chrome native kiểm tra zoom thật **125% và 200%**, mở lại source anchor,
  cuộn danh sách nhiều dòng và bản EN fixture; marker, DOI, text wrap giữ nguyên.
  Đã khôi phục zoom 100% và đóng tab test, giữ tab người dùng. Không kiểm tra Safari,
  điện thoại thật hoặc full screen-reader audit.

## Nếu cần structured metadata chính xác cho bibliography

Không cần migration hay thay model để giải quyết search text hiện tại. Để có filter
năm/tác giả/loại/status chính xác cho từng record, cần admin xác minh dữ liệu. Đề xuất
bước tiếp theo (chưa triển khai hoặc chạy tự động):

1. Công cụ tạo **candidate** từ từng `li` có ranh giới rõ ràng; giữ nguyên đoạn HTML
   gốc, section ID/locale/index và fingerprint. Paragraph không rõ ranh giới yêu cầu
   admin chọn/chia thủ công; không tự đoán title/authors/year/type/status.
2. Admin xem cạnh nhau VI/EN và điền/kiểm tra các trường trong form record hiện có.
   Không ghép hai ngôn ngữ theo index nếu số mục/thứ tự khác nhau mà chưa xác nhận.
3. So trùng bằng DOI chuẩn hóa hoặc nguồn/ID đã xác nhận; title gần giống chỉ là
   cảnh báo cần rà soát. Candidate có fingerprint/DOI đã nhập phải được chặn nhập lại.
4. Xác nhận từng mục hoặc một batch đã review mới thêm vào **working draft** qua
   cơ chế CMS hiện tại. Giữ library/HTML nguồn; không xóa file, section hay published.
5. Preview cả nguồn và structured listing, kiểm tra trùng. Admin xác nhận riêng cách
   gỡ/ẩn nội dung nguồn khỏi trang để tránh hiển thị hai lần; lưu nháp rồi publish
   toàn bộ draft qua revision 409. Hủy bất kỳ bước nào giữ nguyên dữ liệu đã xuất bản.

Không có converter, parser suy đoán hoặc startup task ghi dữ liệu trong thay đổi này.

## File thay đổi

- `contracts/website-search.ts` — pure query/filter helpers; không thêm field CMS JSON.
- `client/src/modules/website/editorial-search.ts` — read-only DOM units, record index, source scroll.
- `client/src/modules/website/public/components/content-search.tsx` — shared search/results/source view.
- `client/src/modules/website/public/components/search-input.tsx` — tách input dùng chung, People giữ hành vi.
- `client/src/modules/website/public/components/views/explore.tsx` — bốn trang dùng ContentSearch.
- `client/src/modules/website/public/components/shared.tsx` — opt out DynamicSections trong heading của search pages.
- `client/src/modules/website/public/components/dynamic-sections.tsx` — source section selection/reveal.
- `client/src/modules/website/public/components/rich-text-body.tsx` — deterministic source anchors, sanitized HTML.
- `client/src/modules/website/public/components/expandable-content.tsx` — complete lists, unbounded expanded prose, scroll offset.
- `client/src/modules/website/public/public.css` — search styles, source focus, unclipped expanded content.
- `client/tests/website.test.ts` — query/filter regressions.
- `client/tests/fixtures/website-editorial-search.json` — representative CMS layout, no production mutation.
- `client/tests/website-search.browser.html` / `.ts` — repeatable browser harness.
- `client/src/modules/website/README.md` và tài liệu này.
