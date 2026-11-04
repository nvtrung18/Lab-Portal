# Kiểm thử các case còn lại — 05/11/2026

**Verified: 135 → 158/204 PASS (66,18% → 77,45%), đóng thêm 23 case. Còn 46 case.**

Các bài test chỉ dùng MySQL disposable `127.0.0.1:3307/lab_portal_it`, Redis `6380`, Spring cổng ngẫu nhiên và frontend `4173`. URL/catalog được kiểm trước datasource và sau kết nối thực tế. Không kết nối, migrate hoặc query DB PRD; không dùng app/DB cổng 5173/3306. Mặc định ngoài runner là H2 trong RAM; chỉ dọn process/container thuộc lượt test. [Bằng chứng JDBC](../../.artifacts/ai-remaining-20261105/fixtures-delivery-final/database-isolation.json).

Nhánh `fix/ai-service-regressions`; không commit/push/deploy. Source Kaggle `d379a940e366681cf9da332978331481418f10ba6de4ff078c2cc40624184698`, 68 file, 19 artifacts approved giữ checksum. Chỉ sửa xử lý sức chứa Python, chia đoạn Unicode Spring và bộ kiểm thử/báo cáo trong lượt này; các thay đổi có sẵn được giữ.

| Lớp bằng chứng | Kết quả |
|---|---:|
| Python local | 513 PASS, 1 SKIP |
| Java focused | 66 PASS |
| Spring/JWT/MySQL local | 317 PASS, 0 FAIL/SKIP |
| Spring/JWT/MySQL → Kaggle thật | 134 PASS, 0 FAIL/SKIP |
| Browser local | 13 PASS, 1 SKIP runtime-only |
| Regression bên trong Kaggle | 491 PASS |
| Corpus Kaggle | 88/88 PASS |
| Tải generation 1/2/4 và mixed planner/generation | 1/1, 2/2, 4/4, 4/4 PASS |

Các số trên không phải số case nghiệp vụ. 70 biến thể corpus gốc giữ nguyên; thêm 18 biến thể sức chứa. Các giá trị thập phân/số mũ và literal 5000 chữ số bị từ chối deterministic với 0 lần gọi model; planner/draft hợp lệ vẫn có assertion model thật. Không sửa công thức usage/quota hoặc mở thêm workflow.

Các lỗi production đã có regression đỏ trước sửa: literal sức chứa bị cắt/làm tròn hoặc thay bằng mặc định; interpreter gọi model dù literal đã chắc chắn sai, gây 504 trên Kaggle. Literal 5000 chữ số còn gây ValueError ở parser, hoặc gọi model trước khi từ chối; đã xử lý tại parser, giữ giới hạn chuyển số an toàn của Python. Runtime đầu giữ 83 PASS/3 FAIL, lượt giữa 86/86 PASS; lượt cuối 88/88 PASS. `AiRagTextChunker` cắt đôi surrogate pair tại biên 1200; giữ nguyên cặp Unicode, kiểm lại UTF-8 và round-trip MySQL qua HTTP.

Lỗi harness được phân biệt: test từng giả định quota âm có thể lưu nhưng MySQL đã chặn; RAG ingest phải dùng manager đúng Lab; output RAG vốn escape HTML nên assertion được sửa theo contract hiện hành. Client TestRestTemplate/Reactor bị kẹt khi stream JSON lớn; thread dump lưu đầy đủ, đã dùng transport đồng bộ trong test và giữ timeout hữu hạn. Không đổi HTTP client production để chữa lỗi test. Public smoke gặp Network is unreachable một lần; lỗi được giữ và lượt chạy lại PASS.

| Case đóng mới | Bằng chứng |
|---|---|
| C05 | 20 biến thể direct/unified: số nguyên 20/Integer.MAX_VALUE giữ nguyên; zero/âm/khoảng trắng/dấu trừ Unicode/thập phân/overflow/số mũ không tạo preview hoặc slot. 18 biến thể capacity trên Kaggle gồm literal 5000 chữ số ở direct/interpreter đã PASS, từ chối trước model. |
| C20 | Confirm kiểm lại manager, Lab active/deleted và operational status sau preview; negative giữ nguyên action state/slot count, positive tạo đúng một slot. |
| C22 | Confirm tại start-1ms, đúng start và start+1ms: chỉ FUTURE tạo slot; NOW/PAST bị chặn, pending action không bị đổi khi rollback. |
| G01 | Admin/Lab/Research đều đọc đúng tài liệu trong namespace/scope qua Spring → Kaggle; citation đúng documentId và canary xuất hiện đúng nội dung. |
| G02 | Ba assistant: chưa có tài liệu, không match, query một ký tự ASCII/Vietnamese đều không có citation hoặc canary; positive control vẫn match. |
| G08 | Persisted chunk sai domain/namespace/scope/resource/source/content bị loại khỏi HTTP answer/citation; version/index/page không hợp lệ bị MySQL constraint chặn, không nới schema. |
| G10 | 201 candidate chunks: query chung trả đúng top 5, chunk 199 còn trong cửa sổ, chunk 200 ngoài 200 candidates không lọt; thứ hạng/citation được kiểm. |
| G15 | HTTP kiểm rỗng/whitespace, 512000/512001 ký tự, 512/513 chunks; kiểm biên 1199/1200/1201/2400/2401 và emoji tại surrogate boundary. UTF-8/MySQL giữ nguyên nội dung, metadata trang/index đúng, rejection không ghi dở dang. |
| M02 | Tổng booking 0/1/3 được đối chiếu SQL; inactive/deleted bị loại, active CANCELLED_BY_STUDENT vẫn được tính theo query hiện hành, không gọi nhầm là pending. |
| Q02 | 36 biến thể quota assistant/role-module của các cặp role hợp lệ tại N-1/N/N+1; inactive/deleted/actor khác không tính; denial không ghi usage. |
| Q03 | Ba assistant: quota role/module disabled bị HTTP 403, không tiêu usage. Zero/negative requests/context bị ràng buộc DB từ chối; cấu hình cũ được giữ, unit policy vẫn fail closed. |
| Q05 | Clock quota phía Spring tại 23:59:59.999 UTC, 00:00 và +1ms trên MySQL; cả assistant và role/module reset theo cửa sổ UTC [start,end), không dùng nửa đêm Việt Nam. |
| R05 | Group summary kiểm cả Student/Manager với sai Lab, sai parent, project inactive/deleted/missing và nonmember; canary riêng không lọt vào câu trả lời. |
| R06 | Reverse project duy nhất được đọc; hai project active cùng trỏ group bị chặn; reverse inactive không gây ambiguous giả. |
| R10 | 15 biến thể assignee/leader/manager với parent project/group/milestone/Lab giả; assignment còn tồn tại không vượt kiểm scope, SQL task không đổi. |
| R13 | TODO → BLOCKED overdue → DONE qua direct/unified và cùng conversation: status, deadline, progress và blockedReason cập nhật theo SQL mới, không giữ canary cũ. |
| R15 | 11 biến thể membership/parent/role combinations; chỉ pure Student có membership và đúng project được proposal advisory, không tạo task. |
| S13 | Past/start-now và toàn bộ trạng thái slot khác AVAILABLE bị chặn; ca hợp lệ chỉ trả draft. HTTP, catalog và SQL trước/sau đã đối chiếu. |
| S14 | Full, booking trùng, Lab inactive và cấu hình disableBookingForInactiveLab true/false/true được kiểm; không tạo booking hoặc đổi slot. |
| S15 | Owner có booking APPROVED được hướng dẫn trong cửa sổ check-in lấy từ config/clock phía Spring; không tạo action preview hoặc đổi booking. |
| S16 | Clock fixture kiểm trước start, đúng start, giữa cửa sổ, đúng endInclusive và sau end qua Spring → Kaggle; hai đầu inclusive đúng. |
| S17 | Các trạng thái không APPROVED, booking/slot inactive/deleted/cancelled, owner sai, Lab sai hoặc inactive đều bị chặn; SQL không đổi. |
| SEC11 | Prompt và RAG chứa script/img-onerror/markdown javascript/data link được kiểm trong browser; không có executable DOM/link, không chạy script, reload history vẫn an toàn. |

| Case còn một phần | Ranh giới kiểm chứng |
|---|---|
| K17 | Đã quan sát Run All trên source trước gặp 3 timeout, sửa và chạy tiếp các cell canonical trên source mới; corpus, regression và tải/mixed PASS; duplicate-start guard giữ process cũ. Chưa chạy một lượt Run All không gián đoạn từ session mới với source cuối. |
| L11 | Capacity 5000 chữ số được unit/Kaggle kiểm từ chối an toàn trước inference. Chưa đủ biến thể max input/context/RAG/history hoặc token-budget tại gateway, nên chưa nghiệm thu toàn case. |
| Q04 | 9 biến thể policy trên MySQL đã kiểm min(assistant,role) tại N-1/N/N+1. Gateway hiện truyền PRE_CONTEXT_TOKEN_COUNT=0; chưa nghiệm thu ngân sách token của context/prompt thực tế. Không đổi accounting hoặc tự đặt công thức đếm. |
| R11 | Đã qua 12 lượt task direct/unified, null deadline/dueDate, progress 0/100 và SQL DATE. Chưa đủ biến thể milestone/null progress; cột progress_percent hiện NOT NULL, không sửa schema để tạo dữ liệu không hợp lệ. |

Toàn bộ 46 case chưa PASS và từng lý do nằm trong [checklist](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.md). Không kết luận đã xử lý hết các lỗi. Các fault về thiết lập Kaggle cần phiên riêng; quota race/context budget/outage và các biến thể còn thiếu vẫn chờ đúng lớp bằng chứng.

[JSON expected/actual, variant, requestId và fingerprint](AI_SERVICE_REMAINING_REPORT_20261105.json) · [Local fixture](../../.artifacts/ai-remaining-20261105/fixtures-delivery-final/fixture-assertions.jsonl) · [HTTP tới Kaggle](../../.artifacts/ai-remaining-20261105/spring-live-round2/wire-results.jsonl) · [Runtime](../../.artifacts/ai-remaining-20261105/kaggle-round3/runtime-corpus-1793820182767318243/runtime-results.json) · [Browser](../../.artifacts/ai-remaining-20261105/browser-rag-round2/browser-results.json) · [Archive Kaggle](../../.artifacts/ai-remaining-20261105/kaggle-round3.zip).

URL quan sát: `https://headquarters-brochures-bare-search.trycloudflare.com`; tunnel/session có thể hết hạn. Token chỉ lấy từ environment/Secret, không có trong notebook hoặc báo cáo. Nguyên nhân session tắt cũ vẫn Unknown; riêng lần dừng idle hiện tại đã thấy thông báo hơn 40 phút không tương tác của Kaggle.
