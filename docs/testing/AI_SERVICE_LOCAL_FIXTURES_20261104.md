# Kết quả fixture AI local — 04/11/2026

**Verified: 105/105 biến thể fixture PASS. Checklist tăng 112 → 125/204 PASS (61,27%), đóng mới 13 case.**

Spring/JWT/MySQL thật → Python local với đầu ra planner/generation kiểm soát được. Không gọi Kaggle trong lượt này; không có thay đổi production code. Các đường đọc deterministic và kiểm quyền/SQL được nghiệm thu riêng với việc chọn tool bằng model thật.

Integration rộng: 240 PASS, 4 SKIP, 0 FAIL/ERROR. Postman fault simulation: 13/13 PASS. Kết quả fixture focused 105/105 đã được chạy lại trong integration rộng.

4 SKIP là ba nhóm yêu cầu `LAB_PORTAL_LIVE_AI_IT=true` (report generation, shift validation, Lab ngoài quyền) và `browserFlows` yêu cầu phiên UI riêng. Không tính các nhóm này là đã chạy lại trong lượt local.

[JSON từng variant/expected/actual/requestId](AI_SERVICE_LOCAL_FIXTURES_20261104.json) · [Checklist 204 case](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.csv) · [Cách chạy lại](AI_SERVICE_HYBRID_TESTING.md).

| Case | Trước | Sau | Bằng chứng / phần còn chờ |
|---|---|---|---|
| A03 | PARTIAL | PASS | 8 biến thể audit direct/unified: từng bucket ngày/module/action/count khớp SQL; mô tả event riêng không lọt context/answer. |
| A04 | PENDING_FIXTURE | PASS | Audit 0/1/14/15 bucket: count, overfetch, truncated và cảnh báo danh sách giới hạn đúng; deleted/ngoài 14 ngày bị loại. |
| S05 | PARTIAL | PASS | Nonmember và membership inactive/deleted bị 403 ở direct; catalog bỏ tool đọc ca, lượt sau cùng conversation REFUSED; lời khai đã là member không đổi membership. |
| S07 | PARTIAL | PASS | 10 biến thể Student/Manager × active/inactive/deleted/foreign/missing: positive đúng selected slot; negative 403 không thay slot lân cận; SQL không đổi. |
| M07 | PENDING_FIXTURE | PASS | Manager có Lab active đọc đúng Lab; không có Lab, Lab inactive/deleted bị chặn direct, unified REFUSED và không có managed candidate. |
| M10 | PENDING_FIXTURE | PASS | Reassign manager giữa hai lượt cùng conversation: manager cũ mất candidate và direct 403/unified REFUSED; manager mới đọc được; slot SQL không đổi. |
| R02 | PARTIAL | PASS | Manager đúng Lab đọc title/facts từ project; project Lab khác 403, không lộ canary, SQL không đổi. |
| R03 | PARTIAL | PASS | Student member và manager: project active hợp lệ; inactive/deleted/missing 403, không tự thay resource; SQL trước/sau khớp. |
| R20 | PENDING_FIXTURE | PASS | Leader tự review, member thường và manager Lab khác đều 403; catalog không cấp report-review candidate và official review không đổi. |
| R21 | PARTIAL | PASS | 38 biến thể 19 state/mismatch × leader/manager: resource hợp lệ trước khi đổi; sau đổi 403; project/group/task/milestone coherence bắt buộc, official review không đổi. |
| R26 | PENDING_FIXTURE | PASS | Thu hồi group membership, assignment và manager giữa hai lượt: direct 403, unified cùng conversation REFUSED, official review không đổi; assignment trước đó có draft hợp lệ. |
| SEC02 | PENDING_FIXTURE | PASS | 9 tổ hợp 3 system role × inactive/deleted/suspended: JWT cấp trước thay đổi vẫn bị 403 tại direct và unified; DB actor giữ đúng state. |
| G04 | PARTIAL | PARTIAL | Đủ 10 biến thể local với parser/validator/context/RAG thật. Giữ PARTIAL vì tiêu chí P0 yêu cầu runtime riêng; chưa chạy model Kaggle cho private/unified/revoked cùng conversation. |
| G16 | PENDING_FIXTURE | PARTIAL | Đã có fixture và hai lượt cùng conversation local cho inactive/deleted. Còn xác minh planner/model thật không tái dùng nội dung private trong history sau revoke trên Kaggle. |
| Q01 | PENDING_FIXTURE | PASS | Cả ba assistant: enabled đọc được, disabled/missing config direct 403; unified theo contract hiện hành 403 hoặc REFUSED không assistant/citation/preview; config không bị tự khôi phục bởi request. |

## Phạm vi và bằng chứng

- MySQL `127.0.0.1:3307/lab_portal_it`, Redis `6380`, Python `8091`, Spring cổng ngẫu nhiên; migration thật, actor/resource tổng hợp. Chỉ dọn container/process do runner tạo; không dùng DB ở cổng 3306 hoặc ứng dụng 5173.
- Audit so từng bucket SQL, lọc deleted/ngoài 14 ngày, count và cảnh báo truncated; không đưa mô tả audit riêng vào câu trả lời.
- JWT được cấp trước khi đổi state; đúng token cũ gọi cả hai entry point. Các ca từ chối không được tính PASS chỉ dựa trên HTTP200.
- Report kiểm 19 trạng thái/mismatch × 2 actor và so các field official review trước/sau. Membership/assignment/manager đổi giữa hai lượt cùng conversation.
- Hai lượt thử đầu được giữ nguyên. Các lỗi là giả định sai của test harness về null/status/DTO/intent/admission; đã sửa theo source và contract hiện hành, không sửa production hoặc bỏ assertion nghiệp vụ.
- G04/G16 giữ PARTIAL dù local đã qua: còn model thật trên Kaggle với private/unified/history sau revoke. Quota/time boundary/outage và các lỗi GPU/platform chưa được chứng minh bởi bộ này.

[JUnit](../../.artifacts/ai-fixture-expansion-20261104/integration-final/junit/mysql-http) · [Wire HTTP](../../.artifacts/ai-fixture-expansion-20261104/integration-final/wire-results.jsonl) · [SQL/context assertions](../../.artifacts/ai-fixture-expansion-20261104/integration-final/fixture-assertions.jsonl) · [Source fingerprint](../../.artifacts/ai-fixture-expansion-20261104/integration-final/source-fingerprint.json).

Chạy lại: `./scripts/test-ai-service-remediation.ps1 -Group Fixtures`. Không commit/push/deploy.
