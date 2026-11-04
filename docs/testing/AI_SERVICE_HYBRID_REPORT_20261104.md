# Kết quả kiểm thử AI — cập nhật 05/11/2026

**Verified: 158/204 PASS (77,45%), tăng 23 case từ 135. Còn 46 case chưa đủ nghiệm thu.** [Báo cáo mới và giới hạn](AI_SERVICE_REMAINING_REPORT_20261105.md) · [Checklist](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.md). Test chỉ dùng DB disposable `3307/lab_portal_it`, không dùng DB PRD.

Phần dưới giữ kết quả lịch sử trước lượt 05/11; các số/hash trong đó không được coi là kết quả mới.

# Kết quả triển khai kiểm thử AI local và Kaggle — 04/11/2026

**Verified hiện tại: 135/204 case PASS (66,18%). Lượt Kaggle bổ sung đóng thêm 10 case từ mốc 125; tăng tổng 68 case từ 67/204. Nhóm ưu tiên 25/25 giữ nguyên.** 69 case còn PARTIAL/PENDING. Xem [báo cáo GPU/Kaggle mới](AI_SERVICE_GPU_FOLLOWUP_20261104.md). Các kết quả lịch sử được giữ riêng bên dưới; browser không chạy lại trong lượt bổ sung.

[Checklist đầy đủ](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.md) · [CSV 204 case](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.csv) · [JSON expected/actual, biến thể, requestId và nguồn bằng chứng](AI_SERVICE_HYBRID_REPORT_20261104.json) · [Cách chạy lại](AI_SERVICE_HYBRID_TESTING.md).

Nhánh `fix/ai-service-regressions`, HEAD `be045a82c44c2eee33b6f016eba8c2af1912169d`; chưa commit/push. Source Python `63aa1ca0e241d55a06f0074f3fd3b7a652cc10e2ed6121d43d094ecbbf61142f` giữ nguyên và được kiểm lại trên Kaggle: 68 file source/config/test, 19 file model/adapter đúng checksum. Model `Qwen/Qwen3-4B-Instruct-2507`, revision `cdbee75f17c01a7cc42f958dc650907174af0554`; Research adapter đã load. Admin/Lab dùng base model theo descriptor. Public readiness còn PASS sau các fault probes ở lượt bổ sung; URL tạm thời có thể hết hạn.

## Bằng chứng mới trên Kaggle

449 regression PASS; 4 inference smoke PASS; Spring/JWT/MySQL → Kaggle 23/23 PASS; platform probes 26/26 PASS; đúng cell notebook với input lỗi 5/5 PASS; phục hồi sau CUDA OOM thật trên GPU1 qua 3/3 inference. Local notebook/harness regressions 23/23 PASS. Xem [JSON chi tiết](AI_SERVICE_GPU_FOLLOWUP_20261104.json); các số này không cộng thành số case nghiệp vụ.

## Kết quả kiểm chứng lịch sử

| Lớp | Kết quả | Bằng chứng |
|---|---|---|
| Python local, kiểm backend có kiểm soát | 448 PASS, 1 SKIP | [JUnit](../../.artifacts/ai-hybrid-20261104/python-guard-final.xml) |
| Notebook đóng gói | 18 PASS | [JUnit](../../.artifacts/ai-hybrid-20261104/notebook-guard-final.xml) |
| Regression chạy bên trong Kaggle | 449 PASS, 0 FAIL | [JUnit](../../.artifacts/ai-hybrid-20261104/kaggle-63aa/evidence/contract-regression-63aa.xml) |
| Service trên Kaggle với model thật | 70/70 biến thể PASS | [JSON](../../.artifacts/ai-hybrid-20261104/kaggle-63aa/evidence/runtime-corpus-1793799792468381446/runtime-results.json) |
| Generation đồng thời 1/2/4 và mixed planner/generation | 1/1, 2/2, 4/4, 4/4 PASS | [Archive JSON/JUnit](../../.artifacts/ai-hybrid-20261104/kaggle-63aa/evidence.zip) |
| Spring/JWT/MySQL local, generation có kiểm soát | 240 PASS, 4 SKIP | [JUnit mới](../../.artifacts/ai-fixture-expansion-20261104/integration-final/junit/mysql-http) |
| HTTP lỗi qua Postman và socket thật | 13 PASS | [JUnit mới](../../.artifacts/ai-fixture-expansion-20261104/integration-final/junit/http-faults) |
| Browser local, server riêng | 12 PASS, 1 SKIP | [JSON/screenshot attachments](../../.artifacts/ai-hybrid-20261104/browser-63aa-final/browser-results.json) |
| Spring thật → public tunnel → Python Kaggle | 66/66 PASS | [JUnit](../../.artifacts/ai-hybrid-20261104/spring-kaggle-63aa-final/junit/spring-kaggle) · [Wire evidence](../../.artifacts/ai-hybrid-20261104/spring-kaggle-63aa-final/wire-results.jsonl) |
| Browser → Spring → Kaggle, đối chiếu SQL và reload | 1 PASS | [Browser JSON](../../.artifacts/ai-hybrid-20261104/spring-kaggle-63aa-final/browser-results.json) |

Các số trên không cộng thành số case nghiệp vụ. 67 PASS nền được giữ với bằng chứng đã nghiệm thu; 45 case đóng ở lượt runtime trước, 13 case đóng ở lượt fixture local và 10 case đóng ở lượt GPU bổ sung có biến thể/lớp chứng minh trong JSON. Test local chứng minh parser/validator/quyền/DB; UI và model thật có bằng chứng riêng. Đường đọc snapshot deterministic được phép 0 model tokens. Các yêu cầu ngoài khả năng còn qua chặn giới hạn ở server; kết quả PASS không có nghĩa model tự phân loại đúng mọi câu.

GPU sau lượt tải lịch sử: CUDA 0 Tesla T4 dùng 4577/15360 MiB, CUDA 1 dùng 0 MiB. Lượt fault mới kết thúc với GPU0 dùng 4553 MiB, GPU1 dùng 0 MiB. Đây không phải peak VRAM hoặc cam kết capacity/SLA. Latency và kết quả từng request được giữ trong JSON.

## Fixture local bổ sung

Lượt local trước: 105/105 biến thể focused PASS; integration rộng 240 PASS, 4 SKIP, Postman 13/13 PASS. Không có thay đổi production code trong lượt đó; source và SQL/context evidence nằm trong [JSON fixture](AI_SERVICE_LOCAL_FIXTURES_20261104.json). G04/G16 lúc đó PARTIAL, nay đã PASS nhờ đủ 10 biến thể với model Kaggle thật, gồm history sau revoke.

## Các lỗi đã tái hiện và sửa ở lượt trước

- Planner trả capability name thay intent chuẩn cho proposal: chỉ chuẩn hóa khi tên khớp đúng candidate đã được Spring cấp. Không nhận tên/index ngoài danh sách.
- Model cắt hậu tố số của tên Lab hoặc bỏ năm được chỉ rõ: interpreter yêu cầu giữ đầy đủ tên/năm; từ chối và retry có giới hạn khi phát hiện mất dữ kiện, không tự điền ID hoặc năm. Kiểm thêm tiếng Việt, không dấu và tiếng Anh.
- Yêu cầu áp dụng cấu hình bị hiểu thành sửa ca/hủy hoặc từ chối draft có sẵn: retry tối đa một lần với schema/candidate rõ ràng, cộng đủ usage. Config vẫn chỉ là draft, SQL không đổi.
- Report manager trả chuỗi `"true"`, thiếu `suggestions` và tự sinh identity: làm rõ JSON chỉ chứa nội dung, đưa generation lỗi vào retry; validator vẫn nghiêm ngặt. Server gắn reportRef/advisory flags từ context đã cấp quyền.
- Workflow chưa có công cụ bị chuyển sang đọc ca/tổng quan hoặc draft proposal: bổ sung chặn hẹp cho yêu cầu tra cứu trạng thái/điểm đã lưu và thao tác thực thi cụ thể không được catalog hỗ trợ. 17 biến thể phải trả REFUSED nêu giới hạn, không có preview hoặc business write. Câu hỏi quy trình/hướng dẫn và yêu cầu chỉ tư vấn có regression riêng. Không thêm workflow hoặc endpoint production để điều khiển test.
- Test preview nhầm trạng thái API `AWAITING_CONFIRMATION` với trạng thái DB `PENDING`: sửa assertion theo hai contract thật, vẫn kiểm riêng suggestion/confirmation PENDING và slot count. UI test cũng được sửa để thao tác trên turn đã persist sau hydration; không bỏ assertion.

Các lần FAIL cũ được giữ: [planner alias đỏ](../../.artifacts/ai-hybrid-20261104/planner-alias-red.xml), [tên/ngày đỏ](../../.artifacts/ai-hybrid-20261104/name-config-red.xml), [config đỏ](../../.artifacts/ai-hybrid-20261104/config-refusal-red.xml), [report đỏ](../../.artifacts/ai-hybrid-20261104/report-repair-red.xml), [workflow guard đỏ](../../.artifacts/ai-hybrid-20261104/workflow-guard-red.xml), [workflow trước sửa](../../.artifacts/ai-hybrid-20261104/workflow-limitation-before.json), các lượt Spring trước trong `.artifacts/ai-hybrid-20261104/spring-kaggle-final` và `spring-kaggle-priority-final`. Retry thành công không xóa lần lỗi.

## Phạm vi fixture và trạng thái DB

MySQL riêng `127.0.0.1:3307/lab_portal_it`, Redis `6380`, Python local `8091`, frontend `4173`, Spring cổng ngẫu nhiên. Migration thật, JWT thật, actor/resource tổng hợp; runner từ chối cổng bận và chỉ dọn process/container có ownership của lượt test.

- Ma trận 19 capability × 3 single role: 57 quyết định quyền qua HTTP; bốn tổ hợp đa vai trò qua direct/unified ở ba phạm vi Admin/Lab/Research. Generation của ma trận quyền dùng backend kiểm soát được; inference thật có bằng chứng tách riêng.
- Ngày project/group/task kiểm bằng SQL DATE; task giữ status/priority/type/deadline/dueDate/progress/blockedReason. Leader/manager đúng scope được đọc, group/Lab khác bị 403; quyền read không tự cấp quyền draft.
- Target inactive/deleted/Admin/missing; draft account/config chỉ tư vấn, so sánh trường nghiệp vụ trước/sau. Report leader/manager không sửa official review/status.
- Slots 0/49/50/51, groups/milestones 20/21, tasks 25/26, history 50/51. Count/truncated và cảnh báo hiển thị cùng được kiểm.
- RAG ADMIN_ONLY và hai group cùng project kiểm canary/citation. Tài liệu stale/injection chỉ là tham khảo; giá trị policy hiện tại và roles/config/booking/slot không đổi.
- Browser kiểm reload, owner delete, đổi tài khoản, hai tab, preview/confirm/cancel/reopen, các response type, network fail/timeout/retry và JSON/Unicode dài trên mobile. [Ảnh mobile đã xem](../../.artifacts/ai-hybrid-20261104/browser-63aa-final/verified-mobile.png).

## Các case còn chờ

27 PARTIAL, 37 PENDING_FIXTURE, 5 PENDING_KAGGLE. Không còn FIXED_PENDING_APP trong nhóm đã chọn. Danh sách đầy đủ và lý do từng case nằm trong CSV/JSON.

Các nhóm chưa đủ bằng chứng gồm quota đồng thời/context-token budget/UTC rollover; DB/Redis outage; các biến thể quyền/parent/clock chưa có fixture đầy đủ; các cách diễn đạt/RAG/biên dữ liệu còn thiếu; và GPU off, Internet/Secret/Dataset setting, disk/dependency, Run All trong phiên mới. OOM thật và restart/phục hồi đã qua trên GPU1/process riêng. Nguyên nhân session tắt cũ vẫn Unknown. Không đổi quy tắc usage/quota hoặc tiêu chí 204 case để tăng tỷ lệ.

Runner dùng lại: `scripts/test-ai-service-remediation.ps1`; script Kaggle: `ai-service/scripts/verify_runtime.py` và `scripts/verify-ai-kaggle-platform.py`; notebook chuẩn: `AI_Service_Kaggle.ipynb`. URL được quan sát sau lượt mới: `https://sweet-assumed-fax-retirement.trycloudflare.com`. Token chỉ lấy từ environment/Secret, không có trong notebook/báo cáo. Giới hạn 17 cách hỏi đã kiểm không thay thế việc kiểm toàn bộ paraphrase còn chờ ở L02/L06.
