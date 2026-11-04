# Báo cáo kiểm thử AI Service local — 04/11/2026

**Verified: chưa đạt nghiệm thu toàn bộ.** Trong 204 case nền: **68 PASS, 28 FAIL, 48 PARTIAL, 56 BLOCKED, 4 GAP**. Có lỗi P0: Admin không có membership vẫn đọc được tài liệu `LAB_MEMBERS`. Không coi HTTP200, token count hoặc unit test xanh là bằng chứng nghiệp vụ đúng.

Thời gian bằng chứng: 03:03–04:11 UTC, tương ứng 10:03–11:11 giờ Việt Nam. Checkout `main` tại `be045a82c44c2eee33b6f016eba8c2af1912169d`. Source ứng dụng giữ nguyên; thay đổi trong lượt này là hồ sơ kiểm thử và dữ liệu test local có cleanup.

## Checklist đầy đủ

- [204 case: checkbox, kết quả, phần đã chạy và lý do chưa hoàn thành](AI_SERVICE_LOCAL_TEST_CHECKLIST_20261104.md), [CSV](AI_SERVICE_LOCAL_TEST_CHECKLIST_20261104.csv).
- [Đối chiếu đủ126 prompt cũ](AI_SERVICE_LEGACY_PROMPT_CHECKLIST_20261104.md), [CSV](AI_SERVICE_LEGACY_PROMPT_CHECKLIST_20261104.csv): 2 PASS, 11 FAIL, 54 PARTIAL, 29 BLOCKED, 30 GAP. Bộ này yêu cầu UI và có nhiều snapshot không tồn tại ở main. API tương đương thành công chỉ được tính PARTIAL cho workflow UI chưa chạy.
- Hai bộ có ID trùng nhưng nội dung khác nhau; không cộng thành330test độc lập. Các biến thể mở rộng N±1/status/multi-role trong kế hoạch cũng không tự thành PASS từ một case nền.

`PASS`: hoàn thành tiêu chí case trong phạm vi ghi rõ. `FAIL`: có lỗi tái hiện. `PARTIAL`: mới chứng minh một phần. `BLOCKED`: chưa có fixture/khả năng điều khiển môi trường/UI cần thiết. `GAP`: capability/context/candidate chưa có trên đường được yêu cầu. Chỉ PASS được đánh dấu hoàn thành.

## Môi trường và ranh giới đã chạy

**Verified:** frontend `https://localhost:5173` → Vite proxy → Spring `http://127.0.0.1:8080/api`; MySQL8 ở3306 và Redis local. Python/model ở Kaggle qua Cloudflare, không đổi sang production.

URL đầu mất kết nối giữa lượt test: Cloudflare502 rồi530, Spring trả503. Sau khi người dùng cung cấp [URL mới](https://jon-coast-structures-distributions.trycloudflare.com), `/ready` đầy đủ cờ true, Spring inference lại được và các case bị gián đoạn đã được chạy lại khi đủ fixture. Cuối lượt `/health` và `/ready` tiếp tục200. Lỗi kết nối cũ không bị gộp thành lỗi nội dung model.

Model-info xác nhận Qwen/Qwen3-4B-Instruct-2507, revision `cdbee75f17c01a7cc42f958dc650907174af0554`, base identity `596f707d711e040f5bd1cbab1ab0370e3ce1ba3314072bf76e73cb1cc2677706`; Research adapter1.0.0 identity `8c080cac001798a0826d5c72b553b791c31b7e32f9b10d4dd8b93d4f4f92830d`. Admin/Lab base-only hợp lệ. **Unknown:** loại GPU/VRAM thực đo, cold-start và Run All của notebook trong chính phiên này.

Actor đã dùng gồm Admin; manager hai Lab khác nhau và NCKH manager; Student member/nonmember; Deputy/Secretary; research member/leader/assignee; leader hai nhóm cùng project. Đăng nhập bằng fixture seed đã đối chiếu source/DB. Không đọc JWT từ browser, không ghi token/password vào checklist.

**UI:** đã nhập câu hỏi counts trên Assistant Admin, quan sát `Cần thêm thông tin` thay vì43/43 và đối chiếu message persist. Chưa chạy hết UI: Computer Use ban đầu bị chặn vì không xác định chắc URL Windows; ở lượt sau công cụ báo bị dừng bằng phím Esc. Không tiếp tục điều khiển UI sau thông báo này. Không có screenshot file để tuyên bố đã chụp.

## Kết quả regression hiện có

| Lớp kiểm tra | Kết quả thực chạy | Giới hạn bằng chứng |
|---|---|---|
| Python `pytest tests` | **296 passed,1 skipped**,0failure/error | Skip tạo symlink trên Windows; có1deprecation warning Starlette/httpx. Các test dùng stub không chứng minh model thật. |
| Java `-Dtest=Ai*Test` | **547 tests,0failure,0error,5skipped**; BUILD SUCCESS | 542test thực chạy;5MySQL opt-in chưa bật ở lượt này. |
| Bật5MySQL opt-in trên DB riêng3307 | **3passed,2errors**,0skipped; BUILD FAILURE | 2lỗi duplicate seed `LAB_ASSISTANT`;2fresh-authorization tests và1migration test PASS. Không làm trên DB3306. Container test riêng đã được dọn. |
| Live probes và DB assertions | **375record hợp lệ**,370record có requestId | Một số record là so sánh DB/state, không phải375HTTP request. Assertion về shape/status không thay verdict semantic của checklist. |

Bằng chứng: [Python JUnit](../../.artifacts/ai-local-test-20261104/python-tests.xml), [Java log](../../.artifacts/ai-local-test-20261104/java-tests.log), [MySQL opt-in log](../../.artifacts/ai-local-test-20261104/java-mysql-it.log), [probe JSON đã che thông tin nhạy cảm](../../.artifacts/ai-local-test-20261104/probes.valid.jsonl). Không có Postman collection/cloud run được publish; Postman CLI1.76 không có các lệnh mới trong skill, nên live runner dùng HTTP client local.

## Các lỗi và phát hiện cần xử lý

| Mã / mức | Verified: kết quả thực tế | Nguyên nhân hoặc giới hạn xác định | Case chính |
|---|---|---|---|
| **DEF-00 / P0** | Admin id1 chỉroleADMIN,0membership Lab1, vẫn nhận `CANARY_LAB_7931` và citationdoc10 `LAB_MEMBERS` | [Retrieval authorization](../../server/src/main/java/com/web/labportalbackend/ai/rag/service/impl/AiRagRetrievalServiceImpl.java#L111) chỉ so labId cho LAB_MEMBERS, không kiểm membership/effective scope. Đây là failure theo tiêu chí G04. | G04,G13 |
| DEF-01 / P1 | Cả5Admin direct capability trả200, câu safe refusal,0/0token | Spring `AiBoundedList` serialize `values,returnedCount,limit,truncated`; Python `_BoundedAuditBuckets` yêu cầu `items,truncated`, forbid extra. [Local reproduction](../../.artifacts/ai-local-test-20261104/admin-context-schema-reproduction.json) và public Python request cùng shape đã tái hiện. Không tuyên bố capture outbound Spring wire. | A01,A03,A06,A09,A11 |
| DEF-02 / P1 | Admin hỏi counts trên UI nhận clarification nói không có công cụ, dù DB43/43 và có system-summary candidate | Planner không chọn được chức năng phù hợp ở request đã chạy. **Unknown** nguyên nhân nội tại model; chưa có wire planner trace. | A01 |
| DEF-03 / P1 | Member/leader proposal qua Spring đều safe refusal0token | Spring sort enum argumentNames thành `resource,parentResource`; Python đòi thứ tự ngược. [Parser reproduction](../../.artifacts/ai-local-test-20261104/proposal-contract-reproduction.json) reject thứ tự Spring; valid Python fixture cùng tool inference JSON thành công. | R14 |
| DEF-04 / P1 | Nonmember direct policy Lab1 bị403 | FAIL so với tiêu chí S01 về public policy subset. Permission adapter cho policy nhưng đường thực tế từ chối. **Unknown** owning gate cụ thể; chưa coi đây là lộ dữ liệu. Cần thống nhất public-policy acceptance nếu chủ đích sản phẩm khác. | S01 |
| DEF-05 / P1 | Member được hướng dẫn check-in trong10phút sau khi đặt lịch | Source check-in policy tính từ `booking.startTime`, không từ lúc đặt booking. Deputy/Secretary đọc docs được, không được nâng manager authority. | S02 |
| DEF-06 / P1 | Confirm preview107 sau khi106 tạo slot trùng giờ trả500 generic; không tạo slot thứ hai | **Inference:** catch domain exception bên ngoài `TimeSlotService.createSlot` có nested `@Transactional` có thể để transaction rollback-only. Chưa có backend stacktrace để khẳng định exception cụ thể. | C21 |
| DEF-07 / P1 | Manager project summary dịch `DRAFT` thành **BẢO MẬT** | Sai nghĩa trạng thái trong câu trả lời model. | R02 |
| DEF-08 / P2 | Một số project/group summaries dừng giữa câu ở512completion tokens | Có output bị cắt thực tế; không dùng mọi response512tokens làm bằng chứng truncation nếu không thấy nội dung bị cắt. | R01,R02,R04 |
| DEF-09 / P1 | Admin safe refusals vẫn có usage246–250 `SUCCESS`; Spring bỏ metadata safeRefusal | [Gateway mapping](../../server/src/main/java/com/web/labportalbackend/ai/service/impl/AiAssistantGatewayServiceImpl.java#L112) luôn ghi succeeded rồi chỉ trả answer/tokens/citations. Không phân biệt semantic refusal cho caller. **Lưu ý:** deterministic shift draft0token vẫn hợp lệ. | Q11,Q12 |
| DEF-10 / P1 | Conversation62 đã giữ13:13–13:43/capacity4, bổ sung ngày thì mất giờ và capacity thành30; sửa preview111 rõ giờ/capacity cũng không hiểu | [DB pending states](../../.artifacts/ai-local-test-20261104/final-resumed-db-evidence.json) chứng minh USER→CLEARED và mất fields. [Merge code](../../server/src/main/java/com/web/labportalbackend/ai/service/impl/AiShiftDialogueService.java#L83) phụ thuộc mode/clearFields. **Inference:** lỗi interpretation/merge; không tuyên bố có raw model patch trên wire. | C10,C11,H08 |
| DEF-11 / P1 | Capacity **-3** tạo preview109 với **+3** | Hiểu sai số âm;0 và2.5 không tạo preview. Đã cancel109, không tạo slot từ request này. | C05,L02 |
| DEF-12 / P1 | Model nói không có thông tin booking chờ duyệt rồi kết luận **0** | Context không có pending count. SQL cũng0 nhưng trùng ngẫu nhiên không hợp thức hóa phép suy từ missing→0. | M03,L05 |
| DEF-13 / P1 | Ba generic prompts có dấu/không dấu/Anh trả08-10; DB/API task88 đều09-10 | [Oracle SQL/API](../../.artifacts/ai-local-test-20261104/task88-oracle.json). Ba lần lặp prompt explicit field names trả09-10 đúng; lỗi phụ thuộc cách hỏi. **Unknown:** lớp phát sinh chính xác khi chưa capture context wire của request sai. | R07,L01 |
| DEF-14 / P1 | Empty authorized retrieval/citations[] vẫn sinh mãcanary trông như lấy được từ tài liệu | Ví dụ group13 không được doc17 nhưng trả `e2e-scope-...` làm canary. Đây là dữ liệu bịa, **không** phải canary thật nhóm14 bị lộ. | G02 |
| DEF-15 / P1 | Python report-review trusted fixture trảsafeRefusal sau446prompt/52completion tokens | Có generation nhưng output không được chấp nhận. **Unknown** raw generated JSON/lý do validation cụ thể; không suy lỗi permission Spring vì DBreports0. | Report capability; R19/R22 live BLOCKED |
| DEF-16 / P1 | Spring input đúng32768ký tự503; inputngắn200; PythonREADY | [Reproduction](../../.artifacts/ai-local-test-20261104/max-input-envelope-reproduction.json): bọc raw32768 thành JSON32919chars, vượt Python RequestInput32768. **Inference** đây là nguyên nhân request Spring; chưa capture upstream422 của request đó. | API12,L11 |
| DEF-17 / P1 | Lượt4uniqueusers:4/4HTTP503 sau~91s;2user:2/2HTTP200 | Kịch bản4người dùng không inference thành công trong cấu hình phiên này. Chưa có VRAM/OOM/queue trace, không khẳng định GPU OOM hay SLA production. | L09 |
| DEF-18 / Test | 2MySQL tests lỗi duplicate `LAB_ASSISTANT` | `AiRepositoryMySqlIntegrationTest` insert assistant config đã được migration seed, ở lines107/187. Lỗi fixture regression, không phải bằng chứng model sai. | MySQL suite |

**P0 proof để tái hiện:** requestId `local-ai-test-357abc4de6e24ce7899460683d644e09`; actor Admin không membership; gọi direct `LAB_POLICY_READ` Lab1 với query tag của tài liệu test. Response200 có canary và citationsdoc10. [Actor/document metadata](../../.artifacts/ai-local-test-20261104/rag-admin-boundary.json) và record `rag-lab-admin-no-bypass` tại03:52:04UTC trong probe JSON. Doc10 đã bị reindex/revoke; cần ingest lại fixture mới để tái hiện, không giả định ID10 còn active.

## Phủ19capability

| Capability | Positive/negative thực chạy | Kết luận chính |
|---|---|---|
| ADMIN_SYSTEM_SUMMARY | Admin direct + UI counts; Student/Manager denied | FAIL contract + planner |
| ADMIN_AUDIT_SUMMARY | Admin direct; roles khác403 | FAIL contract |
| ADMIN_USER_STATUS_LOOKUP | Target9; targetAdmin/missing403; roles khác403 | FAIL positive contract |
| ADMIN_CONFIG_DRAFT | Admin direct/unified apply; roles khác403 | Draft FAIL contract; không config write |
| ADMIN_ACCOUNT_ACTION_DRAFT | Target9 direct; lock-now unified; roles khác403 | Draft FAIL contract; không account write |
| LAB_POLICY_READ | Member/Deputy/Secretary/Manager-context; nonmember; Admin RAG | Có inference; FAIL check-in meaning và P0RAG |
| LAB_SLOT_READ | Historical39/future41; Admin/missing negative | Đọc được đúng slot/time/status |
| LAB_AVAILABLE_SLOTS_READ | Empty vàslot41; member/manager; nonmember/Admin deny | Happy paths đúng;49/50/51 chưa thử |
| LAB_OWN_BOOKING_READ | Owner historical29; khácowner/role403 | Historical positive đúng; current/expired membership chưa đủfixture |
| LAB_MANAGED_SUMMARY | Manager đúngLab1; student/foreignmanager/Admin403 | Counts32slots/23bookings đúng tại thời điểm đọc |
| LAB_SHIFT_CREATE_DRAFT | Direct JSON deterministic; unified preview/defaults/confirm/cancel/races | Có happy path; dialogue/negative-capacity/overlap failures |
| LAB_BOOKING_DRAFT | Eligible future41→JSON; past/cancelled/role negatives | PASS positive draft/no booking write;full/duplicate chưa thử |
| LAB_CHECKIN_GUIDANCE | NonAPPROVED/past denied; Python trusted fixture200 | Spring positive/biên thời gian BLOCKED |
| RESEARCH_PROJECT_SUMMARY | Member/manager; crossLab/missing | Có inference; status translation/truncation fail |
| RESEARCH_GROUP_SUMMARY | Member/leader/manager; crossgroup; same-project RAG | Scope hoạt động; summary cótruncation |
| RESEARCH_ASSIGNED_TASK_READ | Assignee/member/leader/manager; crossgroup/role | Read scope đúng; một sốprompt sai ngày |
| RESEARCH_TASK_PROPOSAL_DRAFT | Member/leader + wrongparent; Python valid fixture | Spring FAIL argument order; Python valid schema chạy được |
| RESEARCH_TASK_SUGGESTION_DRAFT | Assignee/leader/manager JSON; unassignedmember403 | PASS draft boundary/no task write |
| RESEARCH_REPORT_REVIEW_DRAFT | Missing/role negatives; valid Python fixture attempted | Spring positive BLOCKED vìreports0; Python fixture FAIL |

Sáu capability direct-only ở main không tự trở thành UI support: user lookup/account draft, slot read/booking draft/check-in, report review. Multi-Lab manager catalog chỉ chọn first Lab. Các GAP được ghi riêng, không tính PASS.

## Tải nhỏ, dữ liệu và cleanup

[Concurrency evidence](../../.artifacts/ai-local-test-20261104/concurrency.json): batch2 latency48.500/51.766s; batch4 latency91.078–91.219s, tất cả503. Median batch2=50.133s; median batch4=91.180s, nhưng batch4 đều lỗi. Không lấy các số này làm SLA/p95 đáng tin cho production. Một request đơn ngay sau tải mất122.125s mới200; các request đơn tiếp theo hồi phục. Cuối lượt READY200 chỉ chứng minh readiness, không xóa failure của batch4.

**Verified cleanup:**3slot test40/41/42 đều `deleted=1`; previews106/112 EXECUTED đúngtarget40/42,107–111 REJECTED, không còn PENDING.16record document/version test3–18 đều `active=0,deleted=1`; không đụng docs có sẵn1/2. Booking vẫn29, tasks96, reports0; users43active/43registered. Conversation34 bị soft-delete để kiểm H07; các hội thoại test còn lại và usage/audit được giữ làm bằng chứng. Không xóa hội thoại có sẵn của người dùng.

[DB cuối lượt](../../.artifacts/ai-local-test-20261104/final-resumed-db-evidence.json), [slot/preview cleanup](../../.artifacts/ai-local-test-20261104/resume-followup-booking-cleanup.json), [RAG cleanup](../../.artifacts/ai-local-test-20261104/rag-cleanup.json), [extra scope cleanup](../../.artifacts/ai-local-test-20261104/extra-scope-cleanup.json).

## Vì sao còn chưa hoàn thành

1. **Fixture nghiệp vụ chưa có:** reports0, bookingAPPROVED trong cửa sổcheck-in, expired/revoked memberships, inactive/deleted resources, user nhiềurole, manager nhiềuLab, quota sátlimit, datasetsN±1. Không thay quyền/trạng thái dữ liệu đang dùng để ép kết quả; cần fixture có cleanup hoặc DB độc lập. Report API có submit/file/review nhưng không DELETE, nên không tạo official submission rồi để lại dữ liệu khó dọn chỉ để làm đẹp checklist.
2. **Fault injection chưa có target điều khiển riêng:** GPUoff/OOM/dataset hỏng/thiếuweights, clock00:00UTC, DB/Redisdown, upstream422/503/504/HTML/retry-count, token rotation. Unit/isolated tests đã chạy nhưng không đổi chúng thành live PASS.
3. **UI chưa được kiểm hết:** chỉ có một turn Admin được quan sát trực tiếp. Sau khi công cụ báo Esc, các thao tác nhiềutab/mobile/logout/controls/render/timeout không tiếp tục. API success không thay bằng chứng UI.
4. **Giới hạn main và lỗi thật:** catalog/context thiếu một số chức năng của bộ126cũ; P0RAG và các contract/dialogue/data-quality failures cần xử lý trước khi nghiệm thu lại.

## Cách đọc và giới hạn hồ sơ bằng chứng

Tìm `probeNames` trong CSV, rồi tìm tên và timestamp tương ứng trong `probes.valid.jsonl` để lấy requestId/request/response/duration. Một tên có thể xuất hiện ở cả URL cũ và lượt retry URL mới; dùng timestamp cùng semantic verdict cuối, không lấy assertion `passed=true` về status làm kết luận nội dung đúng.

Một append log ở lượt song song đầu bị mất phần record `future-booking-draft`; bản gốc vẫn giữ, fragment được ghi trong [normalization metadata](../../.artifacts/ai-local-test-20261104/evidence-normalization.json). Không dựng lại requestId. Case đó đã được chạy lại thành công bằng slot41 và có record mới. Log PowerShellUTF16 hoàn tất được chuẩn hóa khi cần đọc, bản rawUTF16 riêng vẫn giữ cho resume-unified. Hạn chế harness này không bị tính thành lỗi sản phẩm.

**Unknown còn lại** được ghi theo từng case. Không có kết luận tất cảuser/biến thểngôn ngữ, GPUfault, browser hoặc runtime đều PASS từ những mẫu đã chạy.
