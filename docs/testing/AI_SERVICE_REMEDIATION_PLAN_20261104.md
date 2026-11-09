# Plan sửa và tối ưu AI Service — 04/11/2026

**Trạng thái: ĐÃ TRIỂN KHAI SOURCE, ĐANG NGHIỆM THU.** User đã yêu cầu thực hiện plan. Theo dõi thay đổi, test và phần còn thiếu tại [báo cáo sau sửa](AI_SERVICE_REMEDIATION_REPORT_20261104.md); kết quả baseline bên dưới được giữ nguyên làm đối chiếu.

Nhánh thực hiện: **`fix/ai-service-regressions`**, từ `be045a82c44c2eee33b6f016eba8c2af1912169d`. Các thay đổi local có trước được bảo toàn; notebook và trạng thái tài liệu plan trong28 fingerprint baseline được cập nhật theo phạm vi này. Chưa commit/push/merge.

## 1. Mục tiêu và bằng chứng đầu vào

Sửa toàn bộ lỗi đã tái hiện; chuẩn bị điều kiện kiểm thử còn thiếu; xác định rõ phạm vi của những chức năng main chưa hỗ trợ. Không coi một bản sửa hoặc unit test xanh là bằng chứng mọi case đã PASS.

- [Báo cáo lỗi](AI_SERVICE_LOCAL_TEST_REPORT_20261104.md): **DEF-00 đến DEF-18**.
- [Checklist 204 case](AI_SERVICE_LOCAL_TEST_CHECKLIST_20261104.md): **68 PASS, 28 FAIL, 48 PARTIAL, 56 BLOCKED, 4 GAP**; tổng cộng **136 case chưa PASS**, không phải 136 bug độc lập.
- [126 prompt cũ](AI_SERVICE_LEGACY_PROMPT_CHECKLIST_20261104.md): có kỳ vọng không tồn tại ở main; không cộng hai bộ thành 330 test độc lập.
- [Bản đồ đủ 204 case → công việc](AI_SERVICE_REMEDIATION_CASE_MAP_20261104.csv): giữ nguyên kết quả cũ, gắn task cho từng case, bao gồm các PASS phải hồi quy.

**Verified trong lượt lập plan:** đã đọc lại báo cáo và source routing, context, gateway, RAG authorization, shift merge/confirm, Python generation. `.codegraph` đọc được và được dùng tra cứu các lớp/quan hệ; một số symbol chưa có trong index, nên source hiện tại là căn cứ cuối.

**Inference cần kiểm chứng khi sửa:** nguyên nhân transaction rollback-only của overlap 500; lớp phát sinh ngày task sai; nguyên nhân report draft không qua validation; nguyên nhân timeout khi 4 người dùng. Không chốt cách sửa chỉ từ giả thuyết trong báo cáo.

## 2. Phạm vi và nguyên tắc

1. Spring tiếp tục quyết định quyền, resource, trạng thái hiện tại và mọi mutation. Không cấp quyền bằng prompt, model hoặc lịch sử.
2. Contract Java/Python/frontend phải đồng bộ. Giữ null, missing, zero, empty, truncated khác nhau; không tạo dữ liệu mặc định để đủ đáp án.
3. Counts, dates, enums, capacity và policy phải khớp trường được cấp. Model được diễn giải nhưng không được thay đổi fact.
4. Sửa tại lớp chịu trách nhiệm, dùng pattern và test có sẵn. Không đổi client HTTP, dependency hoặc kiến trúc ngoài phạm vi cần thiết.
5. Không chỉ tăng timeout, retry hoặc token limit để che lỗi. Không sửa checksum/manifest/weights nhằm ép READY.
6. Thay đổi public DTO phải có `@Schema` đúng; contract, validation, lỗi, caller và khả năng đọc history cũ phải được kiểm tra.
7. Mỗi phần làm theo vòng: **test tái hiện → sửa → focused regression → kiểm chứng đúng lớp**. Chỉ mở rộng testing khi có thay đổi hoặc vấn đề chưa được giải quyết.
8. Gây lỗi DB/Redis, đổi quyền, khóa user và chỉnh clock phải dùng môi trường/fixture riêng; không sửa dữ liệu thật đang dùng để ép điều kiện test.

## 3. Thứ tự thực hiện

```text
T00–T01: bảo toàn workspace, nhánh riêng, môi trường kiểm thử
    ↓
T02: chặn P0 lộ tài liệu RAG
    ↓
T03–T05: sửa contract, refusal/audit, public policy
    ↓
T06–T09: dữ liệu đúng, retrieval và planner
    ↓
T10–T13: capacity, hội thoại, transaction và giới hạn input
    ↓
T14–T16: runtime, MySQL regression và report draft
    ↓
T17–T19: bổ sung fixture, fault injection và Kaggle preflight
    ↓
T20–T21: xử lý GAP theo các quyết định phạm vi D1–D4
    ↓
T22–T23: notebook mới, runtime thật, UI và checklist sau sửa
```

Làm trực tiếp trong session theo từng thay đổi nhỏ. Đây là thứ tự công việc, không phải quy trình nhiều agent hoặc các cổng phê duyệt tự động.

## 4. Công việc sửa lỗi

Đường dẫn Java dưới đây thuộc `server/src/main/java/com/web/labportalbackend/ai/`, trừ khi ghi rõ. Tests nằm ở các lớp tương ứng trong `server/src/test/java/.../ai` và `ai-service/tests`. Cỡ S: khoảng 1–2 file; M: khoảng 3–5 file. Nếu một increment cần hơn 5 file độc lập, tách nhỏ trước khi sửa.

| Task | Phạm vi và hướng xử lý | Điều kiện chấp nhận / kiểm chứng | Phụ thuộc |
|---|---|---|---|
| **T00 / S — nhánh và baseline** | Lưu HEAD/status/hash của dirty và untracked; tạo `fix/ai-service-regressions`; ghi source/notebook baseline. | Đúng nhánh; file có sẵn giữ nguyên; không reset/clean, commit/push hoặc đổi remote. | Không |
| **T01 / M — môi trường hồi quy** | DB test riêng, fixture manifest có ID thật; tận dụng tests hiện có; runner ghi log an toàn và không lưu secret. | Có oracle từ DB/API, cleanup kiểm được; mỗi lỗi có test đỏ hoặc repro lưu trước sửa. | T00 |
| **T02 / M — quyền RAG** | `rag/service/impl/AiRagRetrievalServiceImpl`, query/projection membership phù hợp và tests. Kiểm quyền thực tế bên cạnh resource scope; dùng bounded/bulk query, tránh lookup từng chunk. | Admin-only, nonmember và revoked không nhận nội dung/metadata LAB_MEMBERS; member và manager đúng scope vẫn đọc theo policy. PROJECT/GROUP/OWNER không suy quyền từ cùng Lab. Unit + MySQL + canary API. | T01 |
| **T03a / M — Admin context** | `context/AiBoundedList`, `AiPythonAuthorizedContext`, `admin_mvp.py` và fixtures. Thống nhất shape canonical với Spring; kiểm cả returnedCount/limit/truncated. | Java serialize được Python nhận; 5 capability Admin trả output hợp lệ; malformed bounds vẫn reject. Contract test dùng payload được serialize từ Java. | T01 |
| **T03b / S — proposal arguments** | `AiPythonAuthorizedContext`, `research_mvp.py`, contract tests. Nếu contract không quy định order, so tập tên argument hợp lệ; không nới required/extra/duplicate checks. | Đảo order không bị từ chối vô cớ; đúng projectRef/groupRef; thiếu/thừa/trùng argument hoặc sai parent vẫn chặn. | T03a |
| **T04a / M — refusal và audit** | Gateway/client/outcome DTO, `AiAuditUsageServiceImpl`, tests. Phân biệt transport success, semantic refusal và valid deterministic draft 0 token. | Refusal không ghi như model-backed success; token/quota accounting phản ánh đúng; caller nhận outcome rõ; không false success. | T03a,b |
| **T04b / M — caller/UI outcome** | Unified response/service và frontend assistant types/hook/component tương ứng. Đồng bộ contract, `@Schema`, khả năng đọc history cũ. | REFUSED render đúng; ANSWER/PREVIEW/RESULT đúng; không nút confirm giả; message cũ vẫn đọc được. Java/client tests rồi browser. | T04a |
| **T05 / M — policy public/member** | `AiLabCapabilityPermissionAdapter`, context builder/repository và tests. Tái hiện403 để xác định owning gate trước khi sửa. | Theo quyết định D1, nonmember chỉ nhận subset được phép; không private RAG/citation; Lab unavailable vẫn chặn. | T02,T04a |
| **T06 / M — Lab facts** | `lab_mvp.py`, context/policy schema cần thiết, tests. Làm rõ check-in tính từ booking start; không biến thiếu pending count thành0. | Không nói10 phút sau lúc đặt booking; không dựng số pending; phân biệt null/0/empty; giữ đúng booking/slot IDs. Replay prompt gốc với model. | T05 |
| **T07 / M — Research facts** | `research_mvp.py`, output validation, tests. Giữ raw enum/date/progress; nhãn diễn giải không đổi nghĩa trạng thái. | DRAFT không thành BẢO MẬT; deadline/dueDate đúng SQL/API cho câu generic và explicit, Việt/không dấu/Anh; null không thành0. | T03b,T04a |
| **T08 / M — empty retrieval** | RAG context và validation ở Lab/Research hiện có. Empty chunks không được biến query thành nguồn/canary; business facts có trong context vẫn được dùng. | No-match/cross-scope trả citations[] và nêu thiếu tài liệu; không bịa mã. Injection và stale document không override authority/current facts. | T02,T06,T07 |
| **T09 / M — planner** | `tool_planner.py`, descriptions trong `AiToolCandidateCatalogServiceImpl`, tests. Mô tả chính xác fields/tools; validate canonical candidate. | Câu counts chọn system-summary và trả số đúng; unsupported writes nói rõ giới hạn; không hứa execute qua một câu xác nhận. Không hardcode prompt test thành regex routing. | T03–T08 |
| **T10 / M — capacity** | `shift_interpretation.py`, source/value validation và merge tests. Giữ dấu của số user nhập; kiểm literal/source evidence trước khi tin giá trị model. | -3 không thành+3;0/âm/thập phân/tràn miền không có actionable preview; valid/default capacity đúng; không lấy số ngày hoặc ID làm capacity. | T04a,T09 |
| **T11a / M — giữ state** | `shift_interpretation.py`, `AiShiftDialogueService`, `AiConversationHistoryServiceImpl`, tests. Null/omitted giữ nguyên khi CONTINUE; clear cần ý định rõ; NEW không thừa kế request đóng. | Bổ sung ngày sau13:13–13:43/capacity4 vẫn giữ cả3 field. DB pending state/history khớp, resource không đổi. | T10 |
| **T11b / M — sửa và đổi ý** | Planner/unified dialogue state, tests hiện có. Phân biệt sửa pending preview với sửa slot đã execute; thay preview an toàn. | Sửa giờ/capacity tạo preview mới đúng; preview cũ không actionable; đổi sang read không tiếp tục create; history giữ terminal outcome. | T11a |
| **T12 / M — overlap transaction** | `AiActionSuggestionServiceImpl`, `booking/service/impl/TimeSlotServiceImpl` và integration test transaction thật. Xác minh rollback-only, chọn boundary bảo toàn atomicity. | Overlap trả UNAVAILABLE/domain outcome, không500/slot thứ2; state lưu đúng. Double-confirm, confirm/cancel và rollback không tạo trùng hoặc false EXECUTED. Không thêm noRollbackFor rộng để che lỗi. | T01,T11 |
| **T13 / M — input/envelope** | `AiConversationHistoryServiceImpl`, request models Java/Python và contract tests. Tách public message limit khỏi trusted envelope/history budget. | Raw32768 xử lý đúng contract;32769 bị400; wrapper không gây hidden422→503; không cắt mù original input hoặc mất pending state. | T04,T11 |
| **T14a / M — concurrency** | `runtime.py`, tests và execution/deadline wiring liên quan. Đo trước khi chọn lock/admission policy; adapter selection và generation phải an toàn trên shared model. | Không adapter bleed; queue/wait có bound; cancellation/timeout không để generation cũ chiếm GPU vô hạn; lỗi có reason/requestId. Không tăng workers dùng chung model một cách mù quáng. | T03–T13 |
| **T14b / M — latency/truncation** | Runtime budgets/prompts/config/tests. Đo queue/planner/generation riêng; đáp án cô đọng; phát hiện finish reason/truncation. | Không trả câu cắt giữa chừng như completed answer; JSON draft hoàn chỉnh; replay1/2/4 user theo D4. Chỉ backpressure fail-fast chưa đủ đóng lỗi4 user cần answer. | T14a |
| **T15 / S — MySQL seed** | `AiRepositoryMySqlIntegrationTest`, migration/seed và setup hiện có. Fixture đúng lifecycle insert/reuse/update. | Cả5 opt-in chạy xanh,0 skip; không disable test, nới unique constraint hoặc xóa seed DB app thật. | T01 |
| **T16 / M — report draft** | `research_mvp.py`, `output_validation.py`, schema/fixture/tests. Thu reason validation an toàn; retry bounded nếu lỗi format có thể sửa. | Valid JSON đúng reportRef/issues/suggestions/advisoryOnly/requiresHumanReview; không official approval. Không bỏ validator để ép200; kiểm quyền Spring bằng report thật. | T03b,T04,T07,T14 |

Trong dependencies, T03/T04/T11/T14 bao gồm cả các phần a/b. Kiểm tra source/caller/test gần nhất trước mỗi thay đổi; không sửa đồng loạt chỉ vì các file có tên giống nhau.

## 5. Đóng các case thiếu fixture và điều kiện chạy

| Task | Công việc | Điều kiện chấp nhận | Phụ thuộc |
|---|---|---|---|
| **T17a — actor/quyền** | User active→locked/deleted sau login;4 tổ hợp multi-role; MEMBER/DEPUTY/SECRETARY; group MEMBER/LEADER; assignee/unassigned/revoked; manager reassigned/nhiều Lab. | Fresh authorization mỗi lượt; role selection trực tiếp và unified rõ ràng; không sửa account có sẵn của người dùng. | T01,T02,T04 |
| **T17b — booking/report/clock** | Slot future/past/start-now/full/cancelled; BookingStatus; owner APPROVED ở before/start/endInclusive/after; report self/other/latest/inactive/deleted/parent mismatch. | Clock kiểm soát được; DB isolated có cleanup; missing report403 không thay bằng chứng self-review policy. | T05,T12,T16 |
| **T17c — dữ liệu bounded** | Counts0; slots49/50/51; groups20/21; milestones20/21; tasks25/26; RAG200+candidates/5+matches; conversations50/51 và message paging. | Count/limit/truncated/order/cursor đúng; không coi bounded list là toàn bộ; giữ scope khi vượt bound. | T06–T08,T13 |
| **T18 — quota/fault** | Spring test riêng với fake upstream localhost; quota N-1/N/N+1, UTC rollover, config disabled/missing;401/422/429/503/504/HTML/reset/timeout; DB/Redis fault trong môi trường riêng. | Đếm được attempts/retries; không retry auth sai; accounting/race có DB proof; không false answer/mutation. Simulation không đổi thành Kaggle evidence. | T04,T12–T15 |
| **T19 — Kaggle preflight** | Phiên test riêng: GPUoff/noInternet/noSecret/noDataset; artifact ambiguous/missing/corrupt; ZIP traversal/disk/OOM; duplicateStart/Stop và restart tunnel. | Không false READY; giữ checksum19 file; start/stop chỉ process sở hữu. Trường hợp phần cứng không tái hiện được vẫn ghi BLOCKED/Unknown chính xác. | T14,T22 |

Chuẩn bị fixture là công việc của đợt triển khai; không để một case BLOCKED mãi chỉ vì DB hôm nay thiếu dữ liệu. T17a/b/c và T18 được chia thành các test increment nhỏ theo từng nhóm điều kiện.

## 6. Các quyết định cần chốt trước phần liên quan

### D1 — public policy

**Đề xuất:** nonmember được đọc policy subset của Lab active theo tiêu chí S01 và permission adapter hiện hành; tuyệt đối không private RAG. Nếu nghiệp vụ thực sự chỉ cho member, ghi lại rule và sửa acceptance có căn cứ, không đổi test chỉ để báo xanh.

### D2 — sáu capability chưa có qua unified chat

**Đề xuất:** đưa đủ19 capability hiện có vào Assistant bằng discovery/explicit-ID resolution có kiểm quyền và bound. Không đưa danh sách toàn bộ user/report/booking vào prompt. Chia việc:

| Task | Phạm vi đề xuất | Tiêu chí |
|---|---|---|
| **T20a — Admin target** | Lookup/account draft với target hợp lệ; clarify thiếu/ambiguous ID. | Không đọc target Admin/deleted/out-of-scope; không lộ credentials hoặc đổi status. |
| **T20b — Lab direct-only** | Slot read, booking draft, check-in guidance theo eligibility hiện hành. | Owner/scope/status/time window đúng; chưa tạo booking hoặc check-in. |
| **T20c — report** | Resolve report qua canonical project/group/submitter/latest version rồi mới cấp candidate. | Leader/manager đúng quyền; self/wrong-scope bị chặn; không official review write. |
| **T20d — discovery** | Nhiều managed Lab, resource ngoài topN, role theo capability/resource. | Không fallback Lab khác; query bounded; proposal-only-STUDENT rule giữ nguyên nếu chưa chốt thay đổi. |

Phụ thuộc T02–T05,T09,T16,T17. Đây là mở rộng hỗ trợ của unified chat; cần chốt phạm vi trước khi thực hiện. Mỗi nhánh catalog/query/resolver có tests riêng.

### D3 — workflow snapshots của126 prompt cũ

Main chưa có application/cleaning/complaint/penalty snapshots hoặc generic proposal/product/evaluation snapshots. Model không thể tự tạo dữ liệu đúng cho các trường thiếu.

- **T21a — mặc định đề xuất:** trả giới hạn hỗ trợ rõ, không bịa; vẫn ghi GAP cho chức năng chưa triển khai.
- **T21b — nếu cần đủ workflow cũ:** tách yêu cầu cho từng domain; đối chiếu enum, query, null, owner/manager visibility, latest version và count predicates; thêm typed context/capability/contract/test tương ứng.
- Chưa tự chọn business rules cho T21b. Không lấy activeBookingCount làm pendingBookingCount hoặc count thay danh sách chi tiết.

### D4 — tải4 người dùng

Mục tiêu là4 request có answer đúng, không nhiễm adapter; chưa có SLA giây được duyệt. Queue đầy trả lỗi rõ chỉ cải thiện failure behavior, chưa đủ đóng case nếu workload hợp budget vẫn không chạy được. Đo trước; nếu GPU không đủ sau tối ưu, nêu giới hạn và chốt workload/phần cứng. Không tự nâng timeout300s để báo PASS.

## 7. Chạy lại trên source mới

### T22 — regression và notebook

1. Chạy focused tests sau từng increment; cuối cùng full Python, Java và5 MySQL opt-in. Client tests/build khi thay public DTO/UI; notebook self-check riêng.
2. Tái tạo `docs/testing/AI_Service_Kaggle.ipynb` bằng script hiện có sau thay đổi Python/contract. Source ZIP/hash phải phản ánh nhánh sửa; giữ manifest/weights đã duyệt.
3. Chạy source mới trên Kaggle/test GPU; verify source fingerprint,19 artifact files, readiness, model/adapter identity và inference. URL mới không tự chứng minh code Python mới.
4. Restart đúng process Spring local để nạp code/config mới; nối URL/token hiện hành; replay request gốc và biến thể.

Nếu không thể thao tác notebook bằng công cụ được phép, bàn giao notebook để người dùng chạy và cung cấp URL. Không test runtime cũ rồi báo fix Python đã xác minh. Lượt lập plan không sửa production, CD hoặc remote resource.

### T23 — UI và checklist sau sửa

- Kiểm browser thật với Admin/Manager/Student/member/leader; ANSWER/REFUSED/CLARIFICATION/PREVIEW/RESULT; history/reload/paging/delete; logout/login;2 tab/switch conversation;mobile;HTML/markdown/link;timeout/retry và confirm races.
- Replay probes theo case map; bổ sung semantic assertions nơi runner cũ chỉ kiểm HTTP200/token. Lưu requestId/source/model version, DB trước/sau và cleanup.
- Xuất checklist sau sửa riêng; giữ báo cáo ban đầu làm baseline. Mỗi case có `before → after`, task, source fingerprint, evidence và lý do chưa đạt.
- Với126 prompt cũ, ghi đúng UI coverage; không chuyển PARTIAL thành PASS chỉ nhờ direct API.

## 8. Trace đầy đủ19 defect

| Defect | Task | Bằng chứng đóng lỗi |
|---|---|---|
| DEF-00 private Lab RAG | T02 | Canary Admin/nonmember/revoked không lộ; authorized positives vẫn đúng. |
| DEF-01 auditBuckets | T03a | Java serialize → Python validate;5 Admin runtime đúng. |
| DEF-02 Admin planner | T09 | Prompt counts gốc route đúng; số khớp DB; UI đúng. |
| DEF-03 proposal order | T03b | Order không đổi semantics; missing/extra/duplicate/parent vẫn chặn. |
| DEF-04 policy 403 | T05,D1 | Xác định owning gate; subset/denial theo policy chốt, không private chunks. |
| DEF-05 check-in anchor | T06,T17b | Từ booking start; boundary tests và câu trả lời đúng. |
| DEF-06 overlap 500 | T12 | MySQL transaction test và confirm repro, không500/dup/falseEXECUTED. |
| DEF-07 DRAFT dịch sai | T07 | Raw enum và nhãn đúng. |
| DEF-08 truncation | T07,T14b | Finish reason rõ; đáp án hoàn chỉnh hoặc nêu giới hạn; JSON draft valid. |
| DEF-09 refusal SUCCESS | T04a,b | Audit/outcome/UI/history đúng; valid draft 0 token không bị gắn lỗi. |
| DEF-10 mất fields | T11a,b | Chuỗi bổ sung ngày/sửa preview giữ state và đúng resource. |
| DEF-11 âm thành dương | T10 | Dấu/value khớp input; không preview trái ý định. |
| DEF-12 missing→0 | T06,T08 | Không có authorized count thì không suy0 dù DB tình cờ trùng. |
| DEF-13 sai deadline | T07 | Generic/explicit và3 ngôn ngữ khớp SQL/API. |
| DEF-14 empty RAG canary | T08 | Không claim có nguồn/canary từ query khi retrieval rỗng. |
| DEF-15 report refusal | T16,T17b | Reason validation được xác định; valid draft và permission E2E. |
| DEF-16 max input 503 | T13 |32768 đúng contract;32769 bị400; envelope không hidden422. |
| DEF-17 tải4 user | T14a,b,D4 | Workload1/2/4 user, latency/errors, queue/generation và adapter isolation. |
| DEF-18 MySQL duplicate | T15 | Cả5 opt-in thực chạy xanh, không nới constraint. |

## 9. Checkpoint và điều kiện nghiệm thu

| Checkpoint | Điều kiện |
|---|---|
| Sau T02–T05 | P0 có regression pass; contracts/outcomes đúng; không mở rộng private permission. |
| Sau T06–T13 | Request gốc về facts/dialogue/mutation đúng; DB/transaction an toàn; không mất quyền hợp lệ. |
| Sau T14–T19 | Regression + MySQL thật; concurrency/quota/fault có evidence; fixture cleanup được kiểm. |
| Sau T20–T23 | D1–D4 được ghi rõ; runtime chạy source mới; UI có kiểm chứng; mọi case còn chưa PASS có lý do. |

Một lỗi chỉ đóng khi có regression test làm lộ lỗi trước sửa, test xanh sau sửa và repro chạy lại tại đúng lớp. Nếu mới sửa source, ghi **FIXED_IN_CODE / PENDING_RUNTIME** hoặc **PENDING_UI**; không ghi PASS thay.

Rủi ro cần kiểm: tăng token budget có thể làm tải chậm hơn; serialize GPU bảo vệ adapter nhưng không tự tăng throughput; retry khi generation cũ còn chạy có thể nhân đôi công việc; tách transaction sai làm mất atomicity; nâng một đầu schema làm đầu còn lại hỏng; mở catalog/query có thể lộ metadata.

**Cập nhật triển khai:** source và notebook đã sửa theo D1, D2, T21a của D3 và mục tiêu đo của D4. Xem báo cáo sau sửa và checklist mới để biết trạng thái từng task. Chưa coi source/unit test xanh là 204 case runtime/UI đã PASS; Kaggle phải chạy source hash mới trước bước replay.
