# Kết quả xác minh AI Service trên Kaggle — 04/11/2026

> Báo cáo lịch sử của source `3242c2a5…`. Kết quả đợt local/Kaggle mới nhất, source `63aa1ca0…`, nằm trong [báo cáo hybrid](AI_SERVICE_HYBRID_REPORT_20261104.md) và [checklist 204 case hiện tại](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.md). Các số dưới đây được giữ để đối chiếu lịch sử.

**Verified trên Kaggle:** 307 test pass, 0 fail; PUBLIC SMOKE: PASS; generation 1/2/4 và mixed planner/generation đều PASS. Bằng chứng tải từ notebook đã chạy, không lấy kết quả test local thay cho Kaggle.

[Notebook dùng lại](../../AI_Service_Kaggle.ipynb) · [Checklist 204 case](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.md) · [CSV](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.csv) · [Summary](../../.artifacts/ai-live-remediation-20261104/kaggle-observed-summary.json) · [Notebook giữ output thực tế](../../.artifacts/ai-live-remediation-20261104/kaggle-executed.ipynb).

Nhánh `fix/ai-service-regressions`, base `be045a82c44c2eee33b6f016eba8c2af1912169d`; chưa commit/push. Source SHA256 **`3242c2a5729c5f87ff3fe3bb6972509c2afe492cb11495b63521fbbfb3932ce8`**, SOURCE CHECK: PASS 63 file.

URL phiên: https://sold-hundreds-horizontal-rna.trycloudflare.com.

## Kết quả trực tiếp từ Kaggle

| Nhóm | Kết quả | Bằng chứng |
|---|---|---|
| API/security/context/schema/planner/regression | 307 pass, 0 fail trong 38,40s | [Log](../../.artifacts/ai-live-remediation-20261104/kaggle-regression.log) |
| Public tunnel smoke | PASS: auth, ba profile snapshot, Admin/proposal/report drafts và canonical planner; gồm Spring envelope đủ ba candidate | [Log](../../.artifacts/ai-live-remediation-20261104/kaggle-public-smoke.log) |
| 1 request generation | 1/1 PASS, 5,173s | [Generation log](../../.artifacts/ai-live-remediation-20261104/kaggle-generation-load.log) |
| 2 request generation | 2/2 PASS, 9,833s | Admin và Lab đều có model tokens |
| 4 request generation | 4/4 PASS, 29,933s | Admin account, Lab booking, Research proposal/report; đúng assistant/kind/resource/human review và có model tokens |
| 4 request mixed | 4/4 PASS, 29,712s | [Mixed log](../../.artifacts/ai-live-remediation-20261104/kaggle-mixed-load.log); planner chọn GROUP 19 / PROJECT 16 trong lúc ba draft cùng chạy |
| GPU sau test | GPU 0: 4577/15360 MiB; GPU 1: 0/15360 MiB | Snapshot sau test, chưa phải peak VRAM |

Các lượt tải chạy trong kernel Kaggle gọi loopback của GPU service; public tunnel được kiểm riêng bằng smoke. Đây là warm functional workload với authorized fixtures kỹ thuật. Kết quả không xác nhận production capacity/SLA, quyền portal-user, DB mutation hoặc UI. 307 test code khác 204 case nghiệm thu end-user; bộ 126 prompt cũng không cộng thành 330 case độc lập.

File bằng chứng trong Kaggle:

- `/kaggle/working/lab-portal-ai/contract-regression.xml`
- `/kaggle/working/lab-portal-ai/smoke-results.json`
- `/kaggle/working/lab-portal-ai/kaggle-verification.json`

## Những lỗi vừa sửa

- **Functional test timezone/capacity trả 504:** helper `test_lab_mvp._client` dùng deadline mặc định 5s. Đã đặt budget 30s riêng cho helper, giữ assertion HTTP 200, capacity 20, Asia/Bangkok và backend.calls 0. Các test timeout riêng vẫn dùng ngưỡng ngắn, nằm trong 307 test xanh tại Kaggle. **Inference:** lần fail cũ bị chậm/scheduling; traceback chưa xác định chính xác thao tác chậm trong kernel. Deadline production giữ nguyên.
- **Planner nhầm draft thành ca Lab:** phân biệt domain/draft. Repro tiếp theo xác định plain proposal đúng nhưng Spring envelope bị fallback clarification. Routing được chuẩn hóa chỉ lấy pendingState/history; temporal defaults vẫn dành cho shift interpreter. Full envelope/ba candidate đã qua public smoke và mixed 4/4 trên model thật.
- **Report JSON refusal:** model sinh nội dung nhận xét; server gắn report identity và advisory flags từ authorized resource. Legacy full output vẫn qua strict schema/reference validator; foreignRef/approval/extra fields bị chặn. Payload report lấy từ fixture Spring đã qua generation sequential và concurrent. Quyền end-user/gateway thực được ghi riêng trong checklist.
- **Admin context/draft:** canonical bounded-list shape, explicit null cho global resourceId/targetUser, account subject kiểu string đúng identity, repair tối đa một lần. Draft Admin đã PASS tại Kaggle.
- **Cloudflare network:** smoke đầu gặp `OSError: [Errno 101] Network is unreachable`; chạy lại cùng smoke đạt PASS. Q09 giữ PARTIAL cho các fault variants còn thiếu.

## Các bản sửa khác trong nhánh

| Nhóm | Bản sửa và ranh giới |
|---|---|
| Private RAG | Public policy không cấp LAB_MEMBERS; kiểm membership/manager thật. Có DB/canary evidence trước đó; còn các biến thể role/revoke/UI. |
| Proposal contract | Đúng argument set bất kể thứ tự, strict resource/parent/kind. Proposal model Kaggle đã PASS. |
| Nonmember policy | Narrow portal admission cho direct public-policy subset; capability/RAG vẫn kiểm scope. |
| Check-in | Giữ snapshot 10 phút tính từ booking start và inclusive boundary; còn fixture owner APPROVED/clock qua app. |
| Action transaction | Overlap UNAVAILABLE, persisted REJECTED; confirm/cancel race tối đa một target; terminal exception có kiểu riêng. |
| Facts/refusal | Snapshot giữ raw status/null/zero/count/date; activeBookingCount không thành pending count. safeRefusal qua DTO/unified, usage REFUSED/MODEL_REFUSED và token counts được giữ. |
| Dialogue | Giữ giờ/capacity khi patch; -3 không thành +3; đổi sang read hủy preview cũ, confirm lại bị 400. |
| Retrieval | Empty retrieval không lấy canary/source từ query; revoke mất citation. |
| Input budget | Public 32768 và trusted envelope/history có budget riêng; 32769 bị 400. |
| Shared GPU | Adapter switch và generation cùng lock; queue/generation deadline; 512 token chưa EOS không trả completed. Model thật đã qua 1/2/4 và mixed load tại Kaggle. |
| Test lifecycle | Reuse seed config, giữ duplicate constraints; cleanup notification đúng actor fixture. MySQL mở rộng trước đó đạt 40 pass, một live skip. |

### Ngày DB → context Java

**Verified trước yêu cầu chỉ test Kaggle:** task 88 ở SQL/API là `2026-10-09`, AI context cũ `2026-10-08`. Raw SQL DATE regression tái hiện bảy trường project/milestone/task. Mapping dùng `SqlTypes.LOCAL_DATE` để đọc/ghi trực tiếp, giữ API/schema DATE. Cơ chế được mô tả ở [Hibernate 6.6 LOCAL_DATE](https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/type/SqlTypes.html#LOCAL_DATE). [Red](../../.artifacts/ai-live-remediation-20261104/calendar-date-red.log), [green](../../.artifacts/ai-live-remediation-20261104/calendar-date-green.log).

Audit bucket cũng lệch ngày. Query đọc ISO string trước JDBC; projection String và LocalDate.parse. SELECT/GROUP BY/ORDER BY dùng cùng biểu thức, giữ ONLY_FULL_GROUP_BY. [MySQL: 40 pass, một live skip](../../.artifacts/ai-live-remediation-20261104/mysql-verified.log). Fixture H2 được bổ sung đủ base fields; [focused H2: tám pass, một skip](../../.artifacts/ai-live-remediation-20261104/calendar-h2-focused.log). Nghiệm thu ngày/role qua app vẫn là FIXED_PENDING_APP.

## Kết nối dự án và phần còn thiếu

Backend local được build bằng `-Dmaven.test.skip=true package` và khởi động trỏ URL mới, profile local, attempt timeout 95s, DEV_SEED_ENABLED=false. [Build-only result](../../.artifacts/ai-live-remediation-20261104/backend-build-no-tests-result.json), [process config](../../.artifacts/ai-live-remediation-20261104/spring-local-process.json). Bước này không chạy test local và chưa xác nhận frontend/actor end-to-end.

| Trạng thái 204 case | Số lượng |
|---|---:|
| PASS | 67 |
| PARTIAL | 57 |
| FIXED_PENDING_APP | 6 |
| PENDING_FIXTURE | 57 |
| PENDING_KAGGLE | 17 |

CSV ghi lý do từng ca. FIXED_PENDING_APP cần Spring/DB/UI thật. PENDING_FIXTURE gồm quota/UTC clock, role matrix, booking eligibility và bounded lists. PENDING_KAGGLE gồm GPU off/no Internet/OOM/duplicate start/fault session chưa gây trên phiên đang phục vụ. Các workflow application/cleaning/complaint/product/evaluation ngoài main context vẫn giữ giới hạn hỗ trợ hiện tại.

Notebook dùng lại đã thêm hai cell tải như bản vừa chạy và xóa saved outputs. Artifact evidence giữ output riêng; scan không phát hiện known local secret literals. Trong baseline 28 file, 26 file giữ nguyên; notebook và remediation plan là hai file thay đổi theo phạm vi được giao. Chưa publish notebook.
