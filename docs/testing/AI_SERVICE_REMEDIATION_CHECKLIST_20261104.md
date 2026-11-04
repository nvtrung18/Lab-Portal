# Checklist nghiệm thu AI Service — cập nhật 05/11/2026

**Verified: 158/204 PASS (77,45%), tăng 23 case từ 135/204. Còn 46 case chưa nghiệm thu.**

204 case và tiêu chí gốc được giữ nguyên. Số test code và số case nghiệp vụ không cộng lẫn nhau. [Báo cáo mới](AI_SERVICE_REMAINING_REPORT_20261105.md) · [CSV](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.csv) · [JSON](AI_SERVICE_HYBRID_REPORT_20261104.json).

| Trạng thái | Số case |
|---|---:|
| PARTIAL | 20 |
| PASS | 158 |
| PENDING_FIXTURE | 21 |
| PENDING_KAGGLE | 5 |

`Trước` giữ lịch sử gốc; mốc 135 trước lượt này được bảo toàn trong `.artifacts/ai-remaining-20261105/`. Mỗi bằng chứng có fingerprint riêng.

| Case | Trước | Sau | Bằng chứng / lý do còn chờ |
|---|---|---|---|
| K01 | PARTIAL | PASS | Kaggle chạy source hash3242c2a5…, SOURCE CHECK: PASS63file; artifact/regression/public smoke đều qua. |
| K02 | BLOCKED | PENDING_KAGGLE | Chưa chạy phiên Kaggle có Accelerator tắt; CUDA OOM trong child GPU1 không thay thế biến thể GPU off. |
| K03 | BLOCKED | PENDING_KAGGLE | Chưa chạy cài dependency/download khi Internet thực sự tắt hoặc download bị chặn; lỗi provisioning Cloudflare không thay thế ca này. |
| K04 | BLOCKED | PENDING_KAGGLE | Chưa chạy phiên Secret thiếu/chưa Attach; giữ Secret đang dùng cho service kiểm thử. |
| K05 | BLOCKED | PARTIAL | 5 giá trị token giả đã bị đúng validator notebook từ chối trên Kaggle; chưa thay giá trị Secret thật/đi qua UserSecretsClient cho từng biến thể. |
| K06 | BLOCKED | PARTIAL | Helper thật đã từ chối filesystem fixture rỗng trên Kaggle; chưa kiểm phiên không attach Dataset thật. |
| K07 | BLOCKED | PARTIAL | Helper thật đã từ chối hai root fixture cùng khớp trên Kaggle; chưa attach hai Dataset thật để nghiệm thu mount/platform. |
| K08 | PARTIAL | PASS | Kaggle đã xác minh artifacts Dataset hiện có; service/model/Research adapter READY và inference thật qua smoke. |
| K09 | BLOCKED | PASS | Thiếu base shard và Research adapter trên mirror riêng: CLI và đúng cell artifact đều dừng, chỉ rõ file; không in ARTIFACT CHECK: PASS, link/bytes approved giữ nguyên. |
| K10 | BLOCKED | PASS | Đổi một byte và thêm CRLF vào config của mirror: CLI và đúng cell artifact đều từ chối checksum; bytes/hash nguồn approved không đổi. |
| K11 | BLOCKED | PASS | Đúng helper extraction trên Kaggle từ chối garbage, truncated, traversal, absolute, drive và symlink trước khi ghi file ngoài extraction root. |
| K12 | BLOCKED | PENDING_KAGGLE | Chưa tạo điều kiện disk đầy/thiếu dung lượng trong phiên Kaggle riêng. |
| K13 | BLOCKED | PENDING_KAGGLE | Cài 63 package tương thích đã PASS; chưa gây wheel không phù hợp hoặc dependency conflict thật trên Kaggle. |
| K14 | BLOCKED | PASS | Process exit thật được readiness guard phát hiện; model loader gặp torch.cuda.OutOfMemoryError thật trên GPU1, modelStatus ERROR và /ready 503. |
| K15 | BLOCKED | PASS | Child thật có /health 200, /ready 503, modelLoaded=false; đúng require_ready và cell tunnel từ chối trước tạo process tunnel. |
| K16 | PASS | PASS | python/health; python/ready; python/model-info; v3-python/health; v3-python/ready; v3-python/model-info |
| K17 | BLOCKED | PARTIAL | Đã quan sát Run All trên source trước gặp 3 timeout, sửa và chạy tiếp các cell canonical trên source mới; corpus, regression và tải/mixed PASS; duplicate-start guard giữ process cũ. Chưa chạy một lượt Run All không gián đoạn từ session mới với source cuối. |
| K18 | BLOCKED | PASS | Cloudflared thật lỗi provisioning nên không sinh URL; tunnel khác có URL nhưng origin /ready 503. Đúng cell tunnel từ chối cả hai, không báo PUBLIC READY: PASS. |
| K19 | PASS | PASS | python-auth-missing; python-auth-wrong; python-auth-bearer |
| K20 | PASS | PASS | Xem regression/source evidence trong report; chưa có complete live scenario. |
| K21 | BLOCKED | PASS | Đúng cell stop với flag False giữ service/tunnel thật; flag True dừng cả hai process thuộc probe. Service chính vẫn /ready 200 sau thử nghiệm. |
| K22 | PARTIAL | PASS | RUNTIME K22 browser smoke |
| API01 | PASS | PASS | python/health; python/ready; python/model-info; v3-python/health; v3-python/ready; v3-python/model-info |
| API02 | PASS | PASS | python-auth-missing; python-auth-wrong; python-duplicate-internal-token |
| API03 | PASS | PASS | python-auth-bearer |
| API04 | PASS | PASS | python-invalid-request-id-18; python-invalid-request-id-129; python-generated-request-id; python-valid-request-id; python-duplicate-request-id |
| API05 | PASS | PASS | Kaggle307service tests, ba profile snapshot và structured generation có token thật; đúng assistant/kind/resource/human review. |
| API06 | PASS | PASS | python-validation-unknown-assistant; python-validation-missing-assistant; python-validation-null-assistant; python-validation-missing-input; python-validation-blank-input; python-validation-wrong-input-type; python-malformed-json |
| API07 | PASS | PASS | python-forbidden-authority-userJwt; python-forbidden-authority-actorRole; python-forbidden-authority-sql; python-forbidden-authority-approved; python-forbidden-authority-executeNow |
| API08 | PARTIAL | PARTIAL | Empty candidate và canonical routing có live proof; malformed candidate/schema/resource chủ yếu có unit/contract proof. |
| API09 | PARTIAL | PARTIAL | Outcome clarification/refusal có regression307; còn phủ đủ các biến thể qua gateway/UI. |
| API10 | PARTIAL | PENDING_FIXTURE | HTTP faults qua simulated upstream đã kiểm; chưa gây đủ model-unloaded/exception/timeout trong Python GPU thực. |
| API11 | PASS | PASS | unsupported/v1/assistants/suggestions; unsupported/v1/research/suggestions |
| API12 | FAIL | PASS | Input32768 đã 200 đúng counts43/43;32769, blank, missing bị 400. |
| API13 | PASS | PASS | spring-unified-forbidden-assistantKey; spring-unified-forbidden-capability; spring-unified-forbidden-actorRole; spring-unified-forbidden-approved; spring-unified-forbidden-authorizedContext |
| API14 | PASS | PASS | spring-direct-invalid-missing-resource; spring-direct-invalid-zero-resource; spring-direct-invalid-negative-resource; spring-direct-invalid-extra-parent; spring-direct-invalid-global-with-resource; spring-direct-invalid-missing-parent |
| API15 | PASS | PASS | spring-domain-mismatch |
| API16 | BLOCKED | PENDING_FIXTURE | Canonical tool/schema/resource rejection có source/unit proof; chưa tiêm đầy đủ malformed model response qua app isolated HTTP. |
| A01 | FAIL | PASS | API và UI trả43 active/43 registered, khớp SQL; reload/reopen giữ đúng answer. |
| A02 | BLOCKED | PENDING_FIXTURE | Authorized Admin phải tồn tại và ACTIVE theo query hiện hành; fixture tổng0 không thể tạo bằng cách xóa/tắt Admin. Renderer0 có Python regression, chưa coi là live0-user case. |
| A03 | FAIL | PASS | 8 biến thể audit direct/unified: từng bucket ngày/module/action/count khớp SQL; mô tả event riêng không lọt context/answer. |
| A04 | BLOCKED | PASS | Audit 0/1/14/15 bucket: count, overfetch, truncated và cảnh báo danh sách giới hạn đúng; deleted/ngoài 14 ngày bị loại. |
| A05 | PASS | PASS | admin-raw-audit |
| A06 | FAIL | PASS | direct-admin-target-user |
| A07 | PARTIAL | PASS | A07_targetStateDoesNotCreateAnAdminBypass |
| A08 | GAP | PASS | A08_unifiedTargetUserUsesTheAuthorizedIdentity |
| A09 | FAIL | PASS | A09_A10_accountDraftCannotWriteTheTargetUser |
| A10 | PASS | PASS | A09_A10_accountDraftCannotWriteTheTargetUser |
| A11 | FAIL | PASS | A11_A12_configDraftNeverAppliesConfiguration |
| A12 | PASS | PASS | A11_A12_configDraftNeverAppliesConfiguration |
| A13 | PASS | PASS | admin-missing-lab-count |
| A14 | PARTIAL | PASS | admin-slot-no-bypass |
| S01 | FAIL | PASS | nonmember-public-policy; rag-lab-nonmember; rag-lab-admin-no-bypass |
| S02 | FAIL | PASS | member-lab-policy; deputy-lab-policy; secretary-lab-policy |
| S03 | PASS | PASS | member-empty-available-slots; future-slots-list; resume-nonempty-slots |
| S04 | PARTIAL | PASS | S04_availableSlotsRespectDatabaseBoundaries |
| S05 | PARTIAL | PASS | Nonmember và membership inactive/deleted bị 403 ở direct; catalog bỏ tool đọc ca, lượt sau cùng conversation REFUSED; lời khai đã là member không đổi membership. |
| S06 | PASS | PASS | member-historical-slot; future-slot-read; resume-future-slot-read |
| S07 | PARTIAL | PASS | 10 biến thể Student/Manager × active/inactive/deleted/foreign/missing: positive đúng selected slot; negative 403 không thay slot lân cận; SQL không đổi. |
| S08 | GAP | PARTIAL | Explicit-ID discovery đã bổ sung; thiếu all positive/negative unified slot/booking/checkin sau sửa. |
| S09 | PARTIAL | PASS | historical-owner-booking |
| S10 | BLOCKED | PASS | historical-owner-booking |
| S11 | PASS | PASS | student-other-booking-denied |
| S12 | PASS | PASS | future-booking-draft; advisory-no-business-write; resume-future-booking-draft; resume-draft-no-booking-write |
| S13 | PARTIAL | PASS | Past/start-now và toàn bộ trạng thái slot khác AVAILABLE bị chặn; ca hợp lệ chỉ trả draft. HTTP, catalog và SQL trước/sau đã đối chiếu. |
| S14 | BLOCKED | PASS | Full, booking trùng, Lab inactive và cấu hình disableBookingForInactiveLab true/false/true được kiểm; không tạo booking hoặc đổi slot. |
| S15 | BLOCKED | PASS | Owner có booking APPROVED được hướng dẫn trong cửa sổ check-in lấy từ config/clock phía Spring; không tạo action preview hoặc đổi booking. |
| S16 | BLOCKED | PASS | Clock fixture kiểm trước start, đúng start, giữa cửa sổ, đúng endInclusive và sau end qua Spring → Kaggle; hai đầu inclusive đúng. |
| S17 | PARTIAL | PASS | Các trạng thái không APPROVED, booking/slot inactive/deleted/cancelled, owner sai, Lab sai hoặc inactive đều bị chặn; SQL không đổi. |
| S18 | PARTIAL | PENDING_FIXTURE | Prompt không tạo preview/write; còn SQL before/after trên booking và UI controls. |
| S19 | PASS | PASS | unsupportedWorkflowRequestsCannotInventExecutionOrBusinessWrites |
| S20 | BLOCKED | PENDING_FIXTURE | Chưa tái hiện đủ biến thể của scenario trên fixture/runtime phù hợp; không suy PASS từ source/unit hoặc chỉ HTTP200. |
| M01 | PASS | PASS | managed-lab-summary |
| M02 | BLOCKED | PASS | Tổng booking 0/1/3 được đối chiếu SQL; inactive/deleted bị loại, active CANCELLED_BY_STUDENT vẫn được tính theo query hiện hành, không gọi nhầm là pending. |
| M03 | FAIL | PASS | manager-pending-count |
| M04 | PASS | PASS | unsupportedWorkflowRequestsCannotInventExecutionOrBusinessWrites |
| M05 | PARTIAL | PASS | manager-empty-available-slots |
| M06 | PASS | PASS | manager-other-lab-denied |
| M07 | BLOCKED | PASS | Manager có Lab active đọc đúng Lab; không có Lab, Lab inactive/deleted bị chặn direct, unified REFUSED và không có managed candidate. |
| M08 | GAP | PENDING_FIXTURE | Multi-Lab/reassign đã có isolated MySQL proof; thiếu unified/UI chọn Lab thứ2. |
| M09 | PASS | PASS | unsupportedWorkflowRequestsCannotInventExecutionOrBusinessWrites |
| M10 | BLOCKED | PASS | Reassign manager giữa hai lượt cùng conversation: manager cũ mất candidate và direct 403/unified REFUSED; manager mới đọc được; slot SQL không đổi. |
| C01 | PASS | PASS | unified-create-complete; preview-no-business-write |
| C02 | PASS | PASS | liveShiftFieldsAndApprovalClaimsKeepServerValidation |
| C03 | BLOCKED | PENDING_FIXTURE | Single managedLab có authoritative default; duplicate-name fixture còn thiếu. |
| C04 | PASS | PASS | unified-create-complete |
| C05 | FAIL | PASS | 20 biến thể direct/unified: số nguyên 20/Integer.MAX_VALUE giữ nguyên; zero/âm/khoảng trắng/dấu trừ Unicode/thập phân/overflow/số mũ không tạo preview hoặc slot. 18 biến thể capacity trên Kaggle gồm literal 5000 chữ số ở direct/interpreter đã PASS, từ chối trước model. |
| C06 | PASS | PASS | liveShiftFieldsAndApprovalClaimsKeepServerValidation |
| C07 | PARTIAL | PARTIAL | 31/02invalid đã clarify; past/exact-now cần trusted-clock fixture. |
| C08 | BLOCKED | PENDING_FIXTURE | Relative date dùng trusted instant có regression; cần near-midnight replay. |
| C09 | PARTIAL | PENDING_FIXTURE | Asia/Ho_Chi_Minh đã đúng UTC trong preview; offset/cross-midnight variants còn thiếu. |
| C10 | FAIL | PASS | create-missing-date; create-follow-up-date; spaced-dialogue-missing-date; spaced-dialogue-supply-date; spaced-dialogue-correct-fields; spaced-dialogue-change-intent; resume-default-capacity; resume-correct-existing-preview; resume-stop-existing-intent; resume-race-preview |
| C11 | FAIL | PASS | spaced-dialogue-missing-date; spaced-dialogue-supply-date; spaced-dialogue-correct-fields; spaced-dialogue-change-intent; resume-default-capacity; resume-correct-existing-preview; resume-stop-existing-intent; resume-race-preview; pending-preview-superseded-and-read-cancelled |
| C12 | PASS | PASS | Sửa preview hủy bản cũ; đổi ý sangread lưu REJECTED; confirm lại 118/120 bị 400, không target. |
| C13 | PASS | PASS | liveShiftFieldsAndApprovalClaimsKeepServerValidation |
| C14 | PASS | PASS | liveC14_unifiedCreationCannotUseAnUnauthorizedOrForeignLab |
| C15 | PASS | PASS | resume-direct-shift-draft |
| C16 | PASS | PASS | double-confirm-0; double-confirm-1 |
| C17 | PASS | PASS | unified-create-cancellable; cancel-preview |
| C18 | PASS | PASS | C18_invalidOrForeignActionCannotRevealPayloadOrWriteSlots |
| C19 | PASS | PASS | double-confirm-0; double-confirm-1; confirm-cancelled; resume-confirm-cancel-race-confirm; resume-confirm-cancel-race-cancel; resume-confirm-cancel-race-consistency; confirm-abandoned-preview-118; confirm-abandoned-preview-120 |
| C20 | BLOCKED | PASS | Confirm kiểm lại manager, Lab active/deleted và operational status sau preview; negative giữ nguyên action state/slot count, positive tạo đúng một slot. |
| C21 | FAIL | PASS | unified-overlap-preview; overlap-confirm |
| C22 | BLOCKED | PASS | Confirm tại start-1ms, đúng start và start+1ms: chỉ FUTURE tạo slot; NOW/PAST bị chặn, pending action không bị đổi khi rollback. |
| C23 | PARTIAL | PENDING_FIXTURE | Wrongrefs/extrafields/schema có unit proof; cần malformed upstream→preview→confirm wire test. |
| C24 | BLOCKED | PENDING_FIXTURE | Overlap/doubleconfirm/race đúng DB; outage/disconnect lúcconfirm chưa tiêm trong appisolated. |
| R01 | PARTIAL | PASS | researchDatesReachTheHttpAnswerWithoutShifting |
| R02 | FAIL | PASS | Manager đúng Lab đọc title/facts từ project; project Lab khác 403, không lộ canary, SQL không đổi. |
| R03 | PARTIAL | PASS | Student member và manager: project active hợp lệ; inactive/deleted/missing 403, không tự thay resource; SQL trước/sau khớp. |
| R04 | PARTIAL | PASS | researchDatesReachTheHttpAnswerWithoutShifting |
| R05 | PARTIAL | PASS | Group summary kiểm cả Student/Manager với sai Lab, sai parent, project inactive/deleted/missing và nonmember; canary riêng không lọt vào câu trả lời. |
| R06 | PARTIAL | PASS | Reverse project duy nhất được đọc; hai project active cùng trỏ group bị chặn; reverse inactive không gây ambiguous giả. |
| R07 | FAIL | PASS | researchDatesReachTheHttpAnswerWithoutShifting |
| R08 | PASS | PASS | researchDatesReachTheHttpAnswerWithoutShifting |
| R09 | PASS | PASS | researchDatesReachTheHttpAnswerWithoutShifting |
| R10 | BLOCKED | PASS | 15 biến thể assignee/leader/manager với parent project/group/milestone/Lab giả; assignment còn tồn tại không vượt kiểm scope, SQL task không đổi. |
| R11 | PARTIAL | PARTIAL | Đã qua 12 lượt task direct/unified, null deadline/dueDate, progress 0/100 và SQL DATE. Chưa đủ biến thể milestone/null progress; cột progress_percent hiện NOT NULL, không sửa schema để tạo dữ liệu không hợp lệ. |
| R12 | BLOCKED | PASS | R12_researchListsRespectAllDatabaseBounds |
| R13 | PARTIAL | PASS | TODO → BLOCKED overdue → DONE qua direct/unified và cùng conversation: status, deadline, progress và blockedReason cập nhật theo SQL mới, không giữ canary cũ. |
| R14 | FAIL | PASS | research-proposal-member; research-proposal-leader; python-valid-proposal-fixture; python-spring-proposal-argument-order; advisory-no-business-write; v3-planner-research.task.proposal.draft; v3-planner-research.task.suggestion.draft; planner-full-catalog-plain; planner-full-catalog-spring-envelope |
| R15 | PARTIAL | PASS | 11 biến thể membership/parent/role combinations; chỉ pure Student có membership và đúng project được proposal advisory, không tạo task. |
| R16 | PASS | PASS | research-task-suggestion-assignee; advisory-no-business-write |
| R17 | PASS | PASS | research-task-suggestion-leader; research-task-suggestion-manager; advisory-no-business-write |
| R18 | PASS | PASS | research-unassigned-task-draft-denied |
| R19 | BLOCKED | PASS | liveReportGenerationUsesTheRealAuthorizedFixtureWithoutOfficialReviewWrite |
| R20 | BLOCKED | PASS | Leader tự review, member thường và manager Lab khác đều 403; catalog không cấp report-review candidate và official review không đổi. |
| R21 | PARTIAL | PASS | 38 biến thể 19 state/mismatch × leader/manager: resource hợp lệ trước khi đổi; sau đổi 403; project/group/task/milestone coherence bắt buộc, official review không đổi. |
| R22 | BLOCKED | PASS | liveReportGenerationUsesTheRealAuthorizedFixtureWithoutOfficialReviewWrite |
| R23 | GAP | PARTIAL | Explicit report discovery có DB proof; yêu cầu không có reportID còn routegroup summary. Cần clarify/route đúng và review E2E. |
| R24 | PASS | PASS | unsupportedWorkflowRequestsCannotInventExecutionOrBusinessWrites |
| R25 | PASS | PASS | unsupportedWorkflowRequestsCannotInventExecutionOrBusinessWrites |
| R26 | BLOCKED | PASS | Thu hồi group membership, assignment và manager giữa hai lượt: direct 403, unified cùng conversation REFUSED, official review không đổi; assignment trước đó có draft hợp lệ. |
| SEC01 | PASS | PASS | guest-POST/ai/chat; guest-GET/ai/conversations; guest-GET/ai/conversations/1; guest-POST/ai/actions/1/confirm; guest-POST/ai/actions/1/cancel; spring-malformed-jwt; spring-expired-jwt |
| SEC02 | BLOCKED | PASS | 9 tổ hợp 3 system role × inactive/deleted/suspended: JWT cấp trước thay đổi vẫn bị 403 tại direct và unified; DB actor giữ đúng state. |
| SEC03 | PARTIAL | PASS | RoleMatrix: 19 capabilities x 3 single roles |
| SEC04 | BLOCKED | PASS | SEC04_multiRoleDirectAndUnifiedKeepEachGrantedScope |
| SEC05 | PASS | PASS | student-admin-summary-denied; student-impersonate-manager |
| SEC06 | PASS | PASS | rag-injection-untrusted; python-forbidden-authority-userJwt; python-forbidden-authority-actorRole; python-forbidden-authority-sql; python-forbidden-authority-approved; python-forbidden-authority-executeNow; student-impersonate-manager |
| SEC07 | PASS | PASS | nonmember-slots-denied; student-other-booking-denied; research-other-project-denied; research-other-group-denied; rag-lab-nonmember; rag-lab-admin-no-bypass; conversation-idor-GET; conversation-idor-DELETE; conversation-idor-POST |
| SEC08 | PASS | PASS | secrets-and-execution |
| SEC09 | PASS | PASS | rag-injection-untrusted; secrets-and-execution |
| SEC10 | BLOCKED | PENDING_FIXTURE | Fresh context authorization có DBproof; chưa dùng historical claim trong 2lượt live. |
| SEC11 | PARTIAL | PASS | Prompt và RAG chứa script/img-onerror/markdown javascript/data link được kiểm trong browser; không có executable DOM/link, không chạy script, reload history vẫn an toàn. |
| SEC12 | BLOCKED | PENDING_FIXTURE | Unit failclosed cóproof; repository/DBfault chưa tiêm côlập. |
| SEC13 | PASS | PASS | spring-unified-forbidden-assistantKey; spring-unified-forbidden-capability; spring-unified-forbidden-actorRole; spring-unified-forbidden-approved; spring-unified-forbidden-authorizedContext |
| SEC14 | PARTIAL | PARTIAL | 4directread actors đều200; mixedGPU4 chỉ2/4 đúng, 2drafts bị plannernhầmshift. Chưa chứngminh4generation isolation trên bản mới. |
| G01 | FAIL | PASS | Admin/Lab/Research đều đọc đúng tài liệu trong namespace/scope qua Spring → Kaggle; citation đúng documentId và canary xuất hiện đúng nội dung. |
| G02 | FAIL | PASS | Ba assistant: chưa có tài liệu, không match, query một ký tự ASCII/Vietnamese đều không có citation hoặc canary; positive control vẫn match. |
| G03 | PASS | PASS | G03_adminOnlyKnowledgeKeepsNamespaceAndActorBoundaries |
| G04 | FAIL | PASS | 10 biến thể Spring/JWT/MySQL → Kaggle thật: member, nonmember, Admin-only, revoke inactive/deleted qua direct/unified; nội dung và citation đúng quyền. |
| G05 | PASS | PASS | rag-research-member-scoped; rag-research-other-project |
| G06 | PASS | PASS | G06_groupKnowledgeDoesNotCrossGroupsInTheSameProject |
| G07 | PASS | PASS | rag-research-member-scoped; rag-research-other-project; rag-research-owner |
| G08 | PARTIAL | PASS | Persisted chunk sai domain/namespace/scope/resource/source/content bị loại khỏi HTTP answer/citation; version/index/page không hợp lệ bị MySQL constraint chặn, không nới schema. |
| G09 | PASS | PASS | rag-version-update; rag-new-version-only; rag-revoke-new-version; rag-revoked-no-citation |
| G10 | BLOCKED | PASS | 201 candidate chunks: query chung trả đúng top 5, chunk 199 còn trong cửa sổ, chunk 200 ngoài 200 candidates không lọt; thứ hạng/citation được kiểm. |
| G11 | PASS | PASS | G11_G12_injectedAndStaleKnowledgeCannotChangePolicyOrBusinessState |
| G12 | PASS | PASS | G11_G12_injectedAndStaleKnowledgeCannotChangePolicyOrBusinessState |
| G13 | FAIL | PASS | rag-lab-authorized; rag-new-version-only; rag-research-member-scoped |
| G14 | PASS | PASS | student-ingest-denied; manager-foreign-ingest-denied; manager-ingest-lab; admin-ingest-admin; manager-ingest-project; manager-ingest-group; manager-ingest-owner; foreign-document-DELETE; foreign-document-PUT; rag-injection-ingest |
| G15 | PARTIAL | PASS | HTTP kiểm rỗng/whitespace, 512000/512001 ký tự, 512/513 chunks; kiểm biên 1199/1200/1201/2400/2401 và emoji tại surrogate boundary. UTF-8/MySQL giữ nguyên nội dung, metadata trang/index đúng, rejection không ghi dở dang. |
| G16 | BLOCKED | PASS | Hỏi lại cùng conversation sau revoke inactive/deleted: model/planner thật không tái dùng canary/citation riêng; membership SQL không đổi. |
| H01 | PASS | PASS | create-follow-up-date; ui-a01-persisted; spaced-dialogue-missing-date; spaced-dialogue-supply-date; spaced-dialogue-correct-fields; spaced-dialogue-change-intent; resume-default-capacity; resume-correct-existing-preview; resume-stop-existing-intent; resume-race-preview |
| H02 | PARTIAL | PASS | H02 H03; H09 H12 Playwright flows |
| H03 | PARTIAL | PASS | H03_historyListIsBoundedAndCannotIncludeAnotherActor |
| H04 | PASS | PASS | conversation-idor-GET; conversation-idor-DELETE; conversation-idor-POST |
| H05 | PASS | PASS | H05_conversationReferenceNeverSilentlySwitchesAnInvalidConversation |
| H06 | PASS | PASS | history-invalid-size-0; history-invalid-size-31; history-invalid-before; history-valid-page-1; history-valid-page-30; history-cursor-no-duplicate; history-boundary-zero; history-boundary-oversize; history-boundary-zero-before; history-boundary-missing-before; history-boundary-foreign-before |
| H07 | PASS | PASS | H07_deleteIsOwnedSoftDeleteAndCannotBeReadOrUsedAgain; H07 Playwright |
| H08 | FAIL | PARTIAL | Giữgiờ/capacity qua bổsungngày, sửa preview đúng UTC; đổiread hủypreview cũ. ChuyểnResearch/Admindraft đang plannernhầmdomain chờbản mới. |
| H09 | PARTIAL | PASS | H09 H12 Playwright flows |
| H10 | BLOCKED | PASS | H10 Playwright flows |
| H11 | BLOCKED | PASS | H11 Playwright |
| H12 | PARTIAL | PASS | H12 Playwright flows |
| H13 | BLOCKED | PASS | SEC11 H13 Playwright |
| H14 | PARTIAL | PASS | H14 Playwright flows |
| Q01 | PARTIAL | PASS | Cả ba assistant: enabled đọc được, disabled/missing config direct 403; unified theo contract hiện hành 403 hoặc REFUSED không assistant/citation/preview; config không bị tự khôi phục bởi request. |
| Q02 | PARTIAL | PASS | 36 biến thể quota assistant/role-module của các cặp role hợp lệ tại N-1/N/N+1; inactive/deleted/actor khác không tính; denial không ghi usage. |
| Q03 | BLOCKED | PASS | Ba assistant: quota role/module disabled bị HTTP 403, không tiêu usage. Zero/negative requests/context bị ràng buộc DB từ chối; cấu hình cũ được giữ, unit policy vẫn fail closed. |
| Q04 | BLOCKED | PARTIAL | 9 biến thể policy trên MySQL đã kiểm min(assistant,role) tại N-1/N/N+1. Gateway hiện truyền PRE_CONTEXT_TOKEN_COUNT=0; chưa nghiệm thu ngân sách token của context/prompt thực tế. Không đổi accounting hoặc tự đặt công thức đếm. |
| Q05 | BLOCKED | PASS | Clock quota phía Spring tại 23:59:59.999 UTC, 00:00 và +1ms trên MySQL; cả assistant và role/module reset theo cửa sổ UTC [start,end), không dùng nửa đêm Việt Nam. |
| Q06 | BLOCKED | PENDING_FIXTURE | Chưa race sátquota trong DBisolated; mixedload dướiquota không thay scenario này. |
| Q07 | BLOCKED | PASS | Xem regression/source evidence trong report; chưa có complete live scenario. |
| Q08 | PARTIAL | PASS | AiGatewayPostmanSimulationTest |
| Q09 | PARTIAL | PASS | AiGatewayPostmanSimulationTest |
| Q10 | BLOCKED | PENDING_FIXTURE | Chưa DB/Redisoutage vàrecovery consistency trong target riêng. |
| Q11 | FAIL | PARTIAL | Usage REFUSED/MODEL_REFUSED vàtokens đã queryDB; requestId/capability metadata còn unitproof, chưa đốichiếu mọi auditrow live. |
| Q12 | FAIL | PASS | DB usage đã lưuREFUSED/MODEL_REFUSED, không SUCCESS; snapshot0token không bị coilàrefusal. |
| Q13 | PARTIAL | PENDING_FIXTURE | URLold530→newREADY/Springreconnect đã thực hiện; tokenrotate lệch→khớp chưa làm. |
| Q14 | BLOCKED | PASS | Restart thiếu artifacts thoát ARTIFACT_FILE_MISSING; model-load OOM thật không ready; process mới trên GPU1 phục hồi /ready 200 và 3/3 inference hợp lệ. |
| L01 | FAIL | PARTIAL | Đã3ngônngữ task88 nhưng contextdate vẫnlệch trước JDBCfix; cần replaySQLoracle vàmọihappy-path. |
| L02 | FAIL | PARTIAL | Followup/correction/negativecapacity đã đúng; Research/Admindraft routing vàtypo corpus chưa đủ. |
| L03 | PARTIAL | PENDING_FIXTURE | ExplicitIDs cóauth/parent guard; duplicate names vàmulti-resource ambiguity chưa đủ fixture. |
| L04 | PASS | PASS | do-not-create |
| L05 | FAIL | PENDING_FIXTURE | Snapshotnull/empty/zero đúng trong mẫu; truncated variants vàtoàn bộcontext types chưa kiểm đủ. |
| L06 | PARTIAL | PARTIAL | Secrets/SQL bị clarify; ngoàiRAG/general-topic prompts chưa đủ. |
| L07 | PASS | PASS | L07_threeIndependentConversationsKeepTheSameSqlDates |
| L08 | PARTIAL | PARTIAL | Warm workload, chưa cold-start độc lập hoặc peak VRAM/SLA. |
| L09 | FAIL | PARTIAL | Đây là warm functional workload dùng fixture; còn peak VRAM/cold-start, statistical benchmark và4portal actors qua Spring. |
| L10 | PARTIAL | PASS | Kaggle1/2/4generation và4request mixed planner+Admin/Lab/Research đều đúng kind/resource. Base/Research adapter isolation đạt trong mẫu đã chạy. |
| L11 | FAIL | PARTIAL | Capacity 5000 chữ số được unit/Kaggle kiểm từ chối an toàn trước inference. Chưa đủ biến thể max input/context/RAG/history hoặc token-budget tại gateway, nên chưa nghiệm thu toàn case. |
| L12 | BLOCKED | PENDING_FIXTURE | Chưa ngắtmạng giữaGPUgeneration/response trong target côlập. |
| L13 | BLOCKED | PENDING_FIXTURE | Chưa clickconfirmUI khiinferencekhác đang chạy; actionraceAPI đã đúng. |
| L14 | PARTIAL | PENDING_FIXTURE | Tunnel đổiURL/reconnect cóproof; chưa chủđộng GPUexpiration/recovery. |
