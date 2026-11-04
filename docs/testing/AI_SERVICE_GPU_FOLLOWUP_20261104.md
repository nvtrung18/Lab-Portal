# Kiểm thử GPU/Kaggle bổ sung — 04–05/11/2026

**Verified: 125 → 135/204 PASS (61,27% → 66,18%), đóng thêm 10 case. Còn 69 case: 27 PARTIAL, 37 PENDING_FIXTURE, 5 PENDING_KAGGLE.**

Source Python giữ nguyên `63aa1ca0e241d55a06f0074f3fd3b7a652cc10e2ed6121d43d094ecbbf61142f` (68 file); 19 artifacts approved đã kiểm checksum. Nhánh `fix/ai-service-regressions`. Lượt này chỉ bổ sung/sửa bộ chạy test, test và báo cáo; không đổi production logic, commit/push hoặc deploy.

| Lớp bằng chứng mới | Kết quả |
|---|---:|
| Regression bên trong Kaggle | 449 PASS |
| Notebook và bộ chạy probes, local | 23 PASS |
| Inference smoke mới, model thật | 4 PASS |
| Spring/JWT/MySQL → Kaggle thật | 23 PASS, 0 SKIP |
| Filesystem/process/tunnel/GPU probes trên Kaggle | 26 PASS, 0 FAIL/SKIP |
| Đúng cell artifact/readiness/tunnel với input lỗi | 5 PASS |
| Inference sau phục hồi GPU1 | 3 PASS |

Các số trên không cộng thành số case nghiệp vụ. Corpus 70 biến thể từ lượt trước, cùng hash source, được dùng để qua gate RuntimeFixtures; không gọi đó là 70 test mới. Không chạy lại browser ở lượt này.

| Case đóng mới | Bằng chứng |
|---|---|
| G04 | 10 biến thể Spring/JWT/MySQL → Kaggle thật: member, nonmember, Admin-only, revoke inactive/deleted qua direct/unified; nội dung và citation đúng quyền. |
| G16 | Hỏi lại cùng conversation sau revoke inactive/deleted: model/planner thật không tái dùng canary/citation riêng; membership SQL không đổi. |
| K09 | Thiếu base shard và Research adapter trên mirror riêng: CLI và đúng cell artifact đều dừng, chỉ rõ file; không in ARTIFACT CHECK: PASS, link/bytes approved giữ nguyên. |
| K10 | Đổi một byte và thêm CRLF vào config của mirror: CLI và đúng cell artifact đều từ chối checksum; bytes/hash nguồn approved không đổi. |
| K11 | Đúng helper extraction trên Kaggle từ chối garbage, truncated, traversal, absolute, drive và symlink trước khi ghi file ngoài extraction root. |
| K14 | Process exit thật được readiness guard phát hiện; model loader gặp torch.cuda.OutOfMemoryError thật trên GPU1, modelStatus ERROR và /ready 503. |
| K15 | Child thật có /health 200, /ready 503, modelLoaded=false; đúng require_ready và cell tunnel từ chối trước tạo process tunnel. |
| K18 | Cloudflared thật lỗi provisioning nên không sinh URL; tunnel khác có URL nhưng origin /ready 503. Đúng cell tunnel từ chối cả hai, không báo PUBLIC READY: PASS. |
| K21 | Đúng cell stop với flag False giữ service/tunnel thật; flag True dừng cả hai process thuộc probe. Service chính vẫn /ready 200 sau thử nghiệm. |
| Q14 | Restart thiếu artifacts thoát ARTIFACT_FILE_MISSING; model-load OOM thật không ready; process mới trên GPU1 phục hồi /ready 200 và 3/3 inference hợp lệ. |

| Case còn một phần | Lý do |
|---|---|
| K05 | 5 giá trị token giả đã bị đúng validator notebook từ chối trên Kaggle; chưa thay giá trị Secret thật/đi qua UserSecretsClient cho từng biến thể. |
| K06 | Helper thật đã từ chối filesystem fixture rỗng trên Kaggle; chưa kiểm phiên không attach Dataset thật. |
| K07 | Helper thật đã từ chối hai root fixture cùng khớp trên Kaggle; chưa attach hai Dataset thật để nghiệm thu mount/platform. |
| K17 | Duplicate-start guard và stop flag đã chạy với process thật; chưa Run All từ đầu notebook chuẩn trong phiên mới để nghiệm thu đủ workflow. |

- **K02:** Chưa chạy phiên Kaggle có Accelerator tắt; CUDA OOM trong child GPU1 không thay thế biến thể GPU off.
- **K03:** Chưa chạy cài dependency/download khi Internet thực sự tắt hoặc download bị chặn; lỗi provisioning Cloudflare không thay thế ca này.
- **K04:** Chưa chạy phiên Secret thiếu/chưa Attach; giữ Secret đang dùng cho service kiểm thử.
- **K12:** Chưa tạo điều kiện disk đầy/thiếu dung lượng trong phiên Kaggle riêng.
- **K13:** Cài 63 package tương thích đã PASS; chưa gây wheel không phù hợp hoặc dependency conflict thật trên Kaggle.

Các case còn lại và từng lý do nằm trong [checklist 204 case](AI_SERVICE_REMEDIATION_CHECKLIST_20261104.md). Không đổi quy tắc quota/usage, không thêm workflow hoặc endpoint điều khiển test vào API.

**Verified về OOM/phục hồi:** child dùng GPU1 với allocator fraction 0,01 và gặp `torch.cuda.OutOfMemoryError` thật. `/ready=503`, `modelStatus=ERROR`, `modelLoaded=false`. Child mới dùng artifacts thật phục hồi `/ready=200` sau 40,034 giây và qua cả 3 assertion inference. Service chính trên GPU0 còn ready; sau cleanup GPU0 dùng 4553 MiB, GPU1 dùng 0 MiB. Đây không phải peak VRAM hoặc SLA.

**Lượt lỗi giữ lại:** round1 có 18 PASS, 4 FAIL, 4 SKIP. Skip do hardlink EXDEV; đã dùng symlink riêng và replace config trong mirror. Các restart sau bị OSError tại preflight; bộ chạy đã dùng SO_REUSEADDR, vẫn có test chặn listener thật. Cách chặn bằng proxy không tác động tới provisioning của cloudflared; round2 dùng `--quick-service` trỏ socket không listen để tạo connection-refused thật. Không sửa file Dataset hoặc weights gốc. [Source cloudflared về transport/provisioning](https://github.com/cloudflare/cloudflared/blob/master/cmd/cloudflared/tunnel/quick_tunnel.go).

**Unknown về session tắt ở cell đầu:** đã quan sát reset làm mất globals/working source ở lượt trước, nhưng không có log xác định nguyên nhân. Lượt bootstrap plain, artifact/dependency/regression/model/tunnel và fault probes hiện tại hoàn tất mà không tái hiện. Không kết luận lỗi cũ do OOM hay đã được sửa tận gốc.

[JSON từng case/requestId/expected/actual](AI_SERVICE_GPU_FOLLOWUP_20261104.json) · [Platform JSON](../../.artifacts/ai-gpu-followup-20261104/kaggle-round2/platform-followup-1793811429740116409/platform-results.json) · [Gate JSON](../../.artifacts/ai-gpu-followup-20261104/kaggle-round2/notebook-fault-gates.json) · [JUnit Spring](../../.artifacts/ai-gpu-followup-20261104/spring-runtime-fixtures/junit/spring-kaggle-fixtures) · [Wire HTTP](../../.artifacts/ai-gpu-followup-20261104/spring-runtime-fixtures/wire-results.jsonl) · [Notebook tests](../../.artifacts/ai-gpu-followup-20261104/notebook-regressions.xml) · [Archive round1](../../.artifacts/ai-gpu-followup-20261104/kaggle-round1.zip) · [Archive round2](../../.artifacts/ai-gpu-followup-20261104/kaggle-round2.zip).

MySQL `3307/lab_portal_it`, Redis `6380`, Spring cổng ngẫu nhiên. Runner đã dọn container/process do lượt này tạo. UI hiện tại và DB `3306` không được dùng. Public readiness được quan sát sau toàn bộ probes tại `https://sweet-assumed-fax-retirement.trycloudflare.com`; tunnel tạm thời có thể hết hạn. Token chỉ lấy từ Secret/environment.
