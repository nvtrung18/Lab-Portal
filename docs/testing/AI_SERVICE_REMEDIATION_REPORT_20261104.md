# Nhật ký trước khi xác minh Kaggle — 04/11/2026

**Kết quả hiện tại:** xem [báo cáo đã xác minh trên Kaggle](AI_SERVICE_KAGGLE_VERIFIED_REPORT_20261104.md). Phần dưới là checkpoint lịch sử trước lượt Kaggle cuối, không phải trạng thái nghiệm thu hiện tại.

**Trạng thái: đang nghiệm thu, chưa tuyên bố toàn bộ 204 case PASS.** Làm trên nhánh `fix/ai-service-regressions`, base `be045a82c44c2eee33b6f016eba8c2af1912169d`; chỉ chạy local + Kaggle do người dùng cấp. Baseline được giữ nguyên. [Checklist 204 case](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.md) và [CSV](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.csv) ghi trạng thái cùng lý do cho từng case. Bộ 126 prompt là nguồn bổ trợ, không cộng thành 330 case độc lập.

## Kết quả đã xác minh

- **Verified — Python:** 324 pass, 1 skip. [Log](../../.artifacts/ai-live-remediation-20261104/python-final.log), [JUnit](../../.artifacts/ai-live-remediation-20261104/python-final.xml). Bao gồm context/schema, grounding, strict references, GPU lock/deadline bằng fake model, format repair, notebook/source manifest và planner plain/envelope equivalence. Không thay bằng chứng GPU thật.
- **Verified — MySQL:** 40 pass, 1 skip trên DB test `127.0.0.1:3307/lab_portal_it`; gồm 27 test Research trước đây chưa bật, các test AI và 2 regression ngày. [Log](../../.artifacts/ai-live-remediation-20261104/mysql-verified.log), [exit code](../../.artifacts/ai-live-remediation-20261104/mysql-verified-result.json). Test report gọi Kaggle là opt-in riêng nên còn skip. Runner chỉ dọn container/volume test có nhãn sở hữu. Không dùng DB app cổng3306 làm fixture của suite.
- **Verified — Java trước sửa ngày:** 1782 pass, 36 skip, 0 fail/error trên tổng1818 test. [Log](../../.artifacts/ai-live-remediation-20261104/java-post-live.log). Full suite sau mapping ngày đang chạy; chưa dùng kết quả cũ để tuyên bố bản cuối đã xanh.
- **Verified — HTTP faults:** 10 test qua upstream/socket cô lập kiểm 401/422/429 không retry,503 retry giới hạn,504 non-retryable, HTML530,502/reset và timeout. [Log](../../.artifacts/ai-remediation-20261104/simulation-tests.log), [traffic](../../.artifacts/ai-remediation-20261104/postman-simulation.ndjson). Đây là integration fault evidence, không phải Kaggle model evidence.
- **Verified — UI Admin:** đã thấy43 active/43 registered đúng SQL; reload và mở lại hội thoại giữ câu trả lời. Đã thấy REFUSED được render. Draft tài khoản trên UI từng bị planner nhầm thành sửa ca; ca này phải replay sau bản mới. Chưa kiểm UI của tất cả vai trò/mobile/2tab.

Không cộng focused tests/MySQL/live replay với full suite thành một tổng case độc lập. Warning Starlette TestClient và bundle-size ở build client còn tồn tại, không được gọi là lỗi nghiệp vụ đã sửa.

## Các lỗi và bản sửa có bằng chứng

| Nhóm | Bản sửa và bằng chứng | Giới hạn nghiệm thu |
|---|---|---|
| DEF-00 private RAG | Quyền public policy không cho phép đọc LAB_MEMBERS. Member nhận canary; nonmember/Admin-only nhận policy200 nhưng không private content/citation. Revoke có MySQL proof. | Unified + same-conversation revoke cần đủ replay. |
| DEF-01/02 Admin contract/routing | Canonical bounded-list shape; global resourceId và targetUser null được serialize rõ. Admin counts/audit/config hết refusal do context. Counts43/43 qua UI. | Audit day còn được sửa thêm ở dưới; account/unified planner cần bản mới. |
| DEF-03 proposal | Argument order dùng set đúng contract; direct proposal member/leader đã sinh JSON đúng group/project. | Envelope planner còn regression riêng. |
| DEF-04 policy admission | Security gate cho phép direct public-policy subset; capability/RAG vẫn kiểm scope. Nonmember live200 không private canary. | Các role combinations rộng vẫn phải kiểm riêng. |
| DEF-05 check-in | Giữ10phút tính từ booking start, không đổi thành sau check-in. Typed policy snapshot và inclusive-boundary regression. | App owner APPROVED + từng clock boundary chưa đủ fixture. |
| DEF-06 action transaction | Overlap trả UNAVAILABLE, lưu REJECTED; doubleconfirm và confirm/cancel race chỉ1target. Typed terminal exception không nuốt auth/infrastructure failures. | DB outage/disconnect lúc confirm chưa tiêm. |
| DEF-07/08/12/13 facts | Read trả snapshot được cấp, giữ raw status/null/zero/count/date; không model suy pending count. Renderer0token là hợp lệ, khác safeRefusal. | Phải sửa cả JDBC DATE bên dưới; mọi bound/variant chưa đầy đủ. |
| DEF-09 refusal | safeRefusal đi qua DTO/unified; usage ghi REFUSED/MODEL_REFUSED, vẫn giữ token count. DB live đã đối chiếu. | Audit metadata mọi outcome chưa đối chiếu hết live. |
| DEF-10/11 dialogue | Giữ giờ/capacity khi bổ sung ngày; sửa preview ra giờ/capacity mới; literal-3 không thành+3. Đổi sang read hủy suggestion cũ; confirm lại400. | Typo corpus, cross-domain và UI2tab chưa đầy đủ. |
| DEF-14 empty retrieval | Không lấy canary/source từ chính query khi không có authorized chunk; revoke mất citation. | No-hit/single-character và mọi UI variant cần replay. |
| DEF-15 report | Model chỉ viết reviewSummary/issues/suggestions; server gắn reportRef và cờ advisory từ resource đã authorize. Full legacy output vẫn qua strict schema/reference validation; foreignRef/extra approval không được hợp thức hóa. Payload report lấy từ Spring đã qua runtime eeffff. | Gateway+DB live test cần chạy riêng; synthetic payload không thay quyền DB. |
| DEF-16 input size | Public32768 đã200 đúng counts;32769/blank/missing400. Trusted envelope/history có budget riêng. | Near-bound RAG/context/OOM vẫn chưa đủ. |
| DEF-17 shared GPU | Adapter switch + generation trong cùng lock; queue/generation deadline;512 token chưaEOS không trả completed answer. | 4directreads xanh; mixed4 ở bản eeffff chỉ3/4 do planner envelope. Chưa đo VRAM/cold-start hoặc có production SLA. |
| DEF-18 MySQL fixtures | Reuse seed config; test duplicate constraint vẫn giữ. Cleanup notification chỉ của actor fixture trước khi xóa user test. Expanded MySQL40pass. | Đây là sửa setup/cleanup test, không thay constraint production. |

### Lỗi ngày ở boundary DB → context

**Verified:** task88 ở SQL và API nghiệp vụ đều `2026-10-09`, AI context cũ là `2026-10-08`. Project/milestone cũng lệch. Test ghi raw SQL DATE làm oracle tái hiện cả7trường; không dùng JPA write/read roundtrip vì có thể che hai lần dịch ngày. [Red](../../.artifacts/ai-live-remediation-20261104/calendar-date-red.log), [green](../../.artifacts/ai-live-remediation-20261104/calendar-date-green.log), [SQL/API evidence](../../.artifacts/ai-live-remediation-20261104/task88-db-api-boundary.json).

`ProjectEntity`, `MilestoneEntity`, `TaskEntity` dùng mapping `SqlTypes.LOCAL_DATE` cho đúng7trường để đọc/ghi LocalDate trực tiếp qua JDBC; schema DATE và API LocalDate giữ nguyên. Hibernate6.6 mô tả cơ chế này trong [SqlTypes.LOCAL_DATE](https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/type/SqlTypes.html#LOCAL_DATE). Regression kiểm cả ghi lại ngày và raw SQL.

Bucket audit cũng bị lệch ngày: SQL Nov04 nhưng answer Nov03. Query chuyển ngày thành ISO string trước JDBC; projection String thay Object; builder parse LocalDate. SELECT/GROUP BY/ORDER BY dùng cùng biểu thức để giữ ONLY_FULL_GROUP_BY và cardinality. [Raw oracle](../../.artifacts/ai-live-remediation-20261104/audit-raw-date-oracle.json), [red](../../.artifacts/ai-live-remediation-20261104/audit-date-red.log), green nằm trong MySQL40pass. Không cộng ngày bằng tay hoặc đổi timezone toàn ứng dụng.

### Lỗi planner còn lại và notebook mới

**Verified:** cùng prompt proposal và3candidate canonical: plain input chọn đúng tool; Spring envelope lại fallback clarification. [Repro](../../.artifacts/ai-live-remediation-20261104/planner-catalog-repro.log), [payload](../../.artifacts/ai-live-remediation-20261104/planner-spring-envelope-request.json). Khác biệt gồm envelope metadata/temporalContext. Routing được chuẩn hóa chỉ dùng pendingState/history và latest message; interpreter tạo ca vẫn nhận temporal context như trước. Regression kiểm model messages của plain/envelope giống nhau, giữ đủ3candidate và identity.

Notebook [AI_Service_Kaggle.ipynb](AI_Service_Kaggle.ipynb) mới chứa63file, ZIP129956byte, hash **`9d45286c18a2faa198cbddad0459951807343ddd6acab0db335fc1e12dd274c1`**. Smoke có Admin draft, report Spring fixture, canonical planner và đủ3candidate trong Spring envelope. **Inference:** URL hiện tại là phiên người dùng vừa cập nhật; chưa nhận hash/log PUBLIC SMOKE từ kernel, nên không gọi là byte-for-byte remote source verification. **Unknown:** phiên9d4528 chưa được cấp URL để replay.

## Dữ liệu test và phần còn thiếu

Action slots test43/44/45 đã soft-delete bằng API; preview thay thế/đổi ý đều terminal; RAG test docs19–25 đã revoke. File cleanup nằm trong `.artifacts/ai-live-remediation-20261104/`. Các hội thoại/usage/audit test còn giữ làm bằng chứng; không xóa dữ liệu có trước.

Còn các nhóm nghiệm thu: run notebook9d4528 và replay planner/mixedGPU/report; chạy jar mới và đối chiếu raw dates; role matrix19capabilities×roles/multi-role; booking clock/eligibility; bounded49/50/51,20/21,25/26,200+; quota race/UTCrollover; DB/Redis outage; Kaggle GPUoff/noInternet/OOM/duplicateStart; UI đa role/mobile/2tab/logout/retry. Workflow application/cleaning/complaint/product/evaluation chưa có snapshot trong main AI context phải giữ giới hạn hiện tại, không tạo dữ liệu giả để đánh dấu PASS.

Các case PARTIAL/PENDING ghi cụ thể trong checklist, không có nghĩa mọi case đó đã phát hiện bug. Case có regression đỏ vẫn giữ log trước sửa; source xanh chưa thay acceptance runtime/UI.
