# Chạy lại bộ kiểm thử AI local và Kaggle

Runner: `scripts/test-ai-service-remediation.ps1`. Chạy từ root repository, dùng output mới cho mỗi lượt; runner từ chối ghi đè bằng chứng cũ.

## Local, không cần Kaggle

```powershell
./scripts/test-ai-service-remediation.ps1 -Group Unit
./scripts/test-ai-service-remediation.ps1 -Group Fixtures
./scripts/test-ai-service-remediation.ps1 -Group Integration
./scripts/test-ai-service-remediation.ps1 -Group UI
./scripts/test-ai-service-remediation.ps1 -Group All
```

`All` chạy Unit, Integration và UI; Fixtures là tập con của Integration. Python cần dependencies API/pytest; Java dùng Maven wrapper/JDK của dự án. Fixtures/Integration/UI cần Docker với `mysql:8.0`, `redis:7-alpine`; Integration dùng Postman CLI simulation. UI cần `npm ci` và `npx playwright install chromium` trong `client`.

`Fixtures` chạy nhóm `AiRemediationIntegrationTest.FixtureExpansion`; số biến thể được lấy từ JUnit của từng lượt. Nhóm này cũng nằm trong `Integration` và `All`. Runner tự tạo actor, Lab, membership, project/group/task/report và tài liệu RAG tổng hợp sau migration thật. Không cần Kaggle hoặc tài khoản của app đang mở. Docker là điều kiện bắt buộc cho nhóm này. Có thể chạy tập trung, ví dụ `-Group Fixtures -FixtureMethods 'S13_S14_*+S15_S16_*+S17_*'`; test khóa DB luôn được chạy kèm.

Các biến thể gồm audit 0/1/14/15 bucket, JWT cũ sau khi actor inactive/deleted/suspended ở cả ba role, slot/project/Lab ngoài scope hoặc inactive/deleted/missing, report lệch parent ở cả leader và manager, thu hồi membership/assignment/manager giữa hai lượt cùng conversation, RAG riêng tư và config enabled/disabled/missing của ba assistant. Học viên được cấp membership ở một Lab fixture khác để kiểm đường `/ai/chat` sau khi mất quyền tại Lab đích; nhờ vậy test đi đến kiểm quyền resource thay vì dừng ở cổng admission.

`wire-results.jsonl` giữ request/response HTTP và requestId, không giữ JWT/header. `fixture-assertions.jsonl` giữ caseId, variant, expected/actual, backend và SQL/context oracle sau khi mọi assertion của biến thể đã qua. Error response hiện hành bỏ trường `data` null; entry point Security mặc định trả 403. Unified chat có thể từ chối bằng response `REFUSED` khi resolver đã loại mọi candidate, còn direct chat trả 403. Test kiểm chính xác từng contract này.

MySQL riêng `127.0.0.1:3307/lab_portal_it`, Redis `6380`, Python `8091`, frontend `4173`, Spring cổng ngẫu nhiên. Migration thật và JWT filter thật được dùng. Runner từ chối cổng đang bận, không tái sử dụng app đang mở, chỉ dọn container mang đúng run label và process do lượt chạy tạo.

**Khóa DB:** runner xóa ảnh hưởng của các cờ integration kế thừa trước khi chọn nhóm; chỉ bật MySQL sau khi tạo container thuộc lượt chạy. `AiRemediationIntegrationTest` từ chối URL khác `jdbc:mysql://127.0.0.1:3307/lab_portal_it` trước khi cấu hình datasource và kiểm lại URL/catalog JDBC thật trước mỗi test. Chạy ngoài runner dùng H2 trong RAM; HTTP fixture thiếu runner bị chặn. Flyway không được clean hoặc baseline tự động. `database-isolation.json` lưu URL/catalog thực tế, không lưu password. Các bài test này không sử dụng DB PRD hoặc DB app cổng 3306.

Backend local dùng `tests/local_http_app.py`: giữ production route/parser/validator, thay đầu ra model bằng scenario được chọn theo request ID. Không có endpoint điều khiển test trong API production. Token giả của fixture không được dùng với Kaggle.

UI dùng Playwright `webServer` với `reuseExistingServer: false` và `cwd` là thư mục client, theo [tài liệu chính thức](https://playwright.dev/docs/test-webserver). Browser fixtures chỉ chứa tài khoản tổng hợp của DB disposable.

## Model thật trên Kaggle

Đóng gói lại sau khi nhóm sửa đã qua local:

```powershell
python scripts/prepare-ai-service-kaggle.py
```

Import `AI_Service_Kaggle.ipynb`, chạy các cell từ trên xuống. Notebook kiểm SHA-256 source, 19 file model/adapter, readiness, regression, public smoke, corpus 88 biến thể, tải chức năng 1/2/4 request và planner/generation đồng thời. Trong corpus có 17 yêu cầu workflow chưa hỗ trợ, bắt buộc từ chối rõ giới hạn thay vì chọn công cụ đọc khác. Có 18 biến thể sức chứa bổ sung; giá trị thập phân/số mũ và literal 5000 chữ số không hợp lệ được kiểm deterministic với 0 token, còn planner và nội dung draft hợp lệ vẫn phải có bằng chứng model thật. Các phép thử tải này không chứng minh SLA hoặc giới hạn GPU production.

CLI đã nằm trong bundle, có thể chạy lại ngay trong Kaggle mà không tải model lần nữa:

```python
subprocess.run([
    PYTHON, str(SERVICE / "scripts/verify_runtime.py"),
    "--base-url", LOCAL_URL, "--source-root", str(SOURCE_ROOT),
    "--expected-source-sha", SOURCE_ZIP_SHA256,
    "--case-ids", "planner", "--repeat", "3", "--concurrency", "2",
    "--output-dir", str(WORK / ("planner-repeat-" + str(time.time_ns())))
], cwd=SERVICE, env={**os.environ, "AI_INTERNAL_SERVICE_TOKEN": TOKEN}, check=True)
```

Selector nhận group, variant ID hoặc business case ID trong corpus. Selector không tồn tại bị từ chối. JSON/JUnit được ghi sau mỗi response; fail lần đầu vẫn được giữ khi lần lặp sau pass. Preflight bị chặn là JUnit error, không phải suite xanh rỗng. Không truyền token qua command line.

## Spring thật → Kaggle

Tải `runtime-results.json` của toàn bộ corpus về máy. Trong terminal đã có `AI_INTERNAL_SERVICE_TOKEN` trong environment:

```powershell
./scripts/test-ai-service-remediation.ps1 -Group Runtime `
  -KaggleUrl 'https://<tunnel>.trycloudflare.com' `
  -RuntimeEvidence '<path>/runtime-results.json'
```

Runner kiểm đủ ID, kết quả, physical artifacts và source hash khớp notebook; sau đó dùng Spring/JWT/MySQL thật với Kaggle và chạy một browser smoke đối chiếu số người dùng với SQL. Không dùng báo cáo một group nhỏ thay cho toàn corpus. `Runtime` không tự khởi động backend giả. Browser smoke model thật được đánh dấu skip trong các lượt local.

Mỗi lượt có `summary.json`, `source-fingerprint.json`, checklist trước lượt chạy, log, JSON/JUnit và wire evidence tương ứng. Browser report có response/screenshot attachments; fixture credentials chỉ ở output ignored của lượt test.

## Nghiệm thu

### Nhóm fixture với Kaggle thật

`RuntimeFixtures` chạy 10 biến thể RAG direct/unified (member, nonmember, Admin-only, revoke inactive/deleted), 2 report, 8 field/approval của draft ca và 3 ca tạo Lab ngoài quyền. Nhóm này dùng Spring/JWT/MySQL/Redis thật, không khởi động model giả và không chạy browser. Gate corpus/source/artifacts giống `Runtime`:

```powershell
./scripts/test-ai-service-remediation.ps1 -Group RuntimeFixtures `
  -KaggleUrl 'https://<tunnel>.trycloudflare.com' `
  -RuntimeEvidence '<path>/runtime-results.json'
```

Để chạy nhóm fixture khác trên đúng runtime đã qua gate, thêm `-FixtureMethods`, ví dụ `-FixtureMethods 'S13_S14_*+S15_S16_*+S17_*+R05_R06_*'`. Không truyền token trên command line. `backend` trong wire record chỉ backend được cấu hình cho lượt chạy; 403 trước inference hoặc response deterministic 0 token không chứng minh model đã sinh nội dung. Fixture chỉ kiểm policy/SQL ghi lớp `Spring/MySQL`, backend `REAL_SPRING_MYSQL` và không tự tạo HTTP requestId.

### Fault probes trên Kaggle

Script `scripts/verify-ai-kaggle-platform.py` kiểm filesystem, process, tunnel và OOM/phục hồi với model thật. Script yêu cầu service chính đã ready; dùng process riêng ở cổng 8092, chỉ dùng GPU1 khi GPU đó còn trống. Dataset/weights nguồn chỉ được đọc. Mirror dùng hardlink hoặc symlink; config lỗi được replace trong mirror, không ghi xuyên link vào nguồn. Ca GPU1 bị SKIP nếu không có GPU thứ hai đủ trống; SKIP không đóng business case.

Để stage hai file test vào notebook khác, chạy đoạn Python dưới đây ở thư mục repo local. Nó tạo một cell Python plain trong output ignored; copy nội dung file đó sang một cell mới trên Kaggle sau khi notebook chuẩn đã ready. Cell chỉ giải mã hai file test, không chứa token và không nạp model.

```python
import base64, json
from pathlib import Path

root = Path.cwd()
notebook = json.loads((root / "AI_Service_Kaggle.ipynb").read_text(encoding="utf-8"))
cells = {cell["id"]: "".join(cell["source"]) for cell in notebook["cells"]}
fragments = {name: cells[cell_id] for name, cell_id in {
    "helpers": "bootstrap-helpers", "start": "start-service",
    "readiness": "wait-ready", "tunnel": "start-tunnel", "stop": "manual-stop",
}.items()}
fragments["sourceZipSha256"] = notebook["metadata"]["lab_portal_source"]["sha256"]
files = {
    "verify-ai-kaggle-platform.py": (root / "scripts/verify-ai-kaggle-platform.py").read_bytes(),
    "gpu-fragments.json": json.dumps(fragments).encode(),
}
encoded = {name: base64.b64encode(content).decode() for name, content in files.items()}
cell = "import base64\nfor name, data in " + repr(encoded) + ".items():\n    (WORK / name).write_bytes(base64.b64decode(data))\n"
destination = root / ".artifacts/platform-probe-upload-cell.py"
destination.parent.mkdir(exist_ok=True)
destination.write_text(cell, encoding="utf-8")
print(destination)
```

Sau khi chạy cell stage trên Kaggle:

```python
probe_output = WORK / ("platform-" + str(time.time_ns()))
subprocess.run([
    PYTHON, str(WORK / "verify-ai-kaggle-platform.py"),
    "--source-root", str(SOURCE_ROOT), "--artifact-root", str(ARTIFACT_ROOT),
    "--fragments", str(WORK / "gpu-fragments.json"), "--python", PYTHON,
    "--base-url", LOCAL_URL, "--groups", "io,service,gpu",
    "--output-dir", str(probe_output),
], cwd=SERVICE, env={**service_env, "AI_INTERNAL_SERVICE_TOKEN": TOKEN}, check=True)
```

JSON/JUnit lưu sau từng probe; giữ output directory mới cho mỗi lần chạy. `io`, `service`, `gpu` chọn được riêng. OOM giới hạn allocator trong process GPU1; sau lỗi, script nạp lại model thật trong process mới và kiểm 3 response bằng oracle. Lỗi Cloudflare không sinh URL được gây bằng provisioning connection-refused thật qua `--quick-service`; lỗi public ready dùng origin thật trả 503. Các probe fixture rỗng/hai root và token giả không thay thế nghiệm thu thao tác Attach Secret/Dataset hoặc Run All trên platform.

Lượt 04–05/11: [kết quả và phạm vi](AI_SERVICE_GPU_FOLLOWUP_20261104.md), [JSON](AI_SERVICE_GPU_FOLLOWUP_20261104.json). Bộ kiểm thử local bảo vệ port reuse, listener còn sống, EXDEV/permission và checksum streaming nằm trong `ai-service/tests/test_kaggle_notebook.py`; file này được loại khỏi bundle theo quy ước hiện có, nên không đổi hash source inference khi chỉ sửa test notebook.

Số test code xanh, số runtime variant xanh và số business case PASS/204 là ba chỉ số riêng. Chỉ đóng case khi đủ biến thể và lớp bằng chứng của chính case; model giả không thay bằng chứng chọn tool/sinh draft của model thật. Các case GPU off/OOM/restart, clock/quota race, outage và workflow ngoài khả năng hiện có phải giữ kết luận và lý do riêng trong checklist. Không đổi tiêu chí để tăng tỷ lệ PASS.
