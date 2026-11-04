"""Exercise notebook/platform failures in owned Kaggle processes, preserving the serving GPU."""
from __future__ import annotations

import argparse
import ast
import errno
import hashlib
import io
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import socket
import subprocess
import sys
import time
import traceback
from types import SimpleNamespace
from datetime import datetime, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import uuid
import xml.etree.ElementTree as ET
import zipfile


class ProbeSkipped(Exception):
    pass


def assert_port_available(port: int) -> None:
    with socket.socket() as check:
        check.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        check.bind(("127.0.0.1", port))


def link_artifact(source: Path, destination: Path) -> None:
    try:
        os.link(source, destination)
    except OSError as error:
        if error.errno != errno.EXDEV:
            raise
        destination.symlink_to(source)


def sha256_file(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def http_json(origin: str, route: str, token: str, timeout: float = 10):
    try:
        with urlopen(Request(origin + route, headers={"X-Internal-Service-Token": token}), timeout=timeout) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.loads(error.read())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--artifact-root", type=Path, required=True)
    parser.add_argument("--fragments", type=Path, required=True)
    parser.add_argument("--python", required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--groups", default="io,service,gpu")
    args = parser.parse_args()
    if not Path("/kaggle/working").is_dir():
        parser.error("Run inside Kaggle")
    out = args.output_dir.resolve()
    if not out.is_relative_to(Path("/kaggle/working").resolve()) or out.exists():
        parser.error("Use a new output directory under /kaggle/working")
    out.mkdir(parents=True)
    source = args.source_root.resolve()
    service = source / "ai-service"
    manifest_path = service / "config/model-artifacts.runtime-t4.json"
    manifest = json.loads(manifest_path.read_text())
    source_manifest = json.loads((source / "kaggle-source-manifest.json").read_text())
    fragments = json.loads(args.fragments.read_text())
    rows, owned = [], []
    token = os.environ["AI_INTERNAL_SERVICE_TOKEN"]
    child_token = "platform-fixture-" + uuid.uuid4().hex
    origin = "http://127.0.0.1:8092"
    groups = set(args.groups.split(","))
    assert groups <= {"io", "service", "gpu"}
    baseline_status, baseline = http_json(args.base_url, "/ready", token)
    assert baseline_status == 200 and baseline["ready"] is True
    metadata = {"backend": "KAGGLE_PLATFORM", "sourceZipSha256": fragments["sourceZipSha256"],
                "probeSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                "startedAtUtc": datetime.now(timezone.utc).isoformat(), "baseUrl": args.base_url}
    verified_source = runpy.run_path(str(service / "scripts/verify_runtime.py"))["verify_source"](source)
    assert fragments["sourceZipSha256"] == verified_source["sourceZipSha256"]
    metadata["verifiedSource"] = verified_source
    helpers = {"__name__": "kaggle_probe_helpers"}
    exec(compile(fragments["helpers"], "notebook-helper-cell.py", "exec"), helpers)

    def save():
        report = {**metadata, "results": rows, "passed": sum(r["status"] == "PASS" for r in rows),
                  "failed": sum(r["status"] == "FAIL" for r in rows), "skipped": sum(r["status"] == "SKIP" for r in rows)}
        (out / "platform-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        suite = ET.Element("testsuite", name="kaggle-platform", tests=str(len(rows)), failures=str(report["failed"]),
                           skipped=str(report["skipped"]))
        for row in rows:
            case = ET.SubElement(suite, "testcase", name=row["variant"], classname=",".join(row["caseIds"]), time=str(row["seconds"]))
            if row["status"] == "FAIL": ET.SubElement(case, "failure", message=row["reason"])
            if row["status"] == "SKIP": ET.SubElement(case, "skipped", message=row["reason"])
        ET.ElementTree(suite).write(out / "platform-results.xml", encoding="utf-8", xml_declaration=True)

    def probe(ids, variant, expected, action):
        started = time.monotonic()
        row = {"caseIds": ids.split(","), "variant": variant, "expected": expected,
               "requestId": "gpu-platform-" + uuid.uuid4().hex, "evidenceLayer": "Kaggle process/filesystem/GPU"}
        try:
            row["actual"] = action()
            row["status"] = "PASS"
        except ProbeSkipped as error:
            row.update(status="SKIP", reason=str(error))
        except Exception as error:
            # Exception bodies, environment and HTTP headers can contain credentials.
            row.update(status="FAIL", reason=type(error).__name__)
            row["diagnostic"] = {"errno": getattr(error, "errno", None),
                                 "frames": [{"file": Path(frame.filename).name, "line": frame.lineno,
                                             "function": frame.name} for frame in traceback.extract_tb(error.__traceback__)]}
        row["seconds"] = round(time.monotonic() - started, 3)
        rows.append(row); save()
        print(variant, row["status"], row["seconds"], flush=True)

    def expect_error(action, allowed):
        try:
            action()
        except allowed as error:
            return {"errorType": type(error).__name__}
        raise AssertionError("Expected rejection")

    def run(command, name, env=None, timeout=120):
        result = subprocess.run(command, cwd=service, env=env, capture_output=True, timeout=timeout)
        # Only our fake token can reach child output, but redact both defensively.
        output = (result.stdout + result.stderr).decode("utf-8", errors="replace")
        for secret in (token, child_token): output = output.replace(secret, "[REDACTED]")
        (out / (name + ".log")).write_text(output)
        return result.returncode, output

    child_env = {**os.environ, "AI_INTERNAL_SERVICE_TOKEN": child_token, "AI_ENVIRONMENT": "local",
                 "AI_MODEL_ARTIFACTS_PATH": str(manifest_path), "AI_MODEL_ARTIFACT_ROOT": str(args.artifact_root),
                 "AI_ASSISTANT_PROFILES_PATH": str(service / "config/assistant-profiles.json"),
                 "AI_OUTPUT_SCHEMAS_PATH": str(service / "config/schemas/structured-output-schemas.json"),
                 "AI_RUNTIME_LOAD_ENABLED": "false", "AI_REQUEST_TIMEOUT_SECONDS": "90",
                 "HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1", "PYTHONPATH": str(service)}

    def start(name, env, code=None):
        assert_port_available(8092)
        command = [args.python, "-c", code] if code else [args.python, "-m", "uvicorn", "app.main:app", "--host", "127.0.0.1",
                                                         "--port", "8092", "--workers", "1", "--no-access-log"]
        with (out / (name + ".log")).open("wb") as log:
            process = subprocess.Popen(command, cwd=service, env=env, stdout=log, stderr=subprocess.STDOUT)
        owned.append(process)
        return process

    def wait_health(process, timeout=300):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            assert process.poll() is None, "Child exited before HTTP readiness"
            try:
                status, body = http_json(origin, "/health", child_token, 3)
                if status == 200: return body
            except (OSError, ValueError): pass
            time.sleep(2)
        raise TimeoutError("Child HTTP health timeout")

    def stop(process):
        if process is not None and process.poll() is None:
            process.terminate()
            try: process.wait(timeout=20)
            except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=20)

    try:
        if "io" in groups:
            for variant in ("absent", "ambiguous"):
                def discovery(variant=variant):
                    directory = out / ("input-" + variant); directory.mkdir()
                    if variant == "ambiguous":
                        for name in ("a", "b"):
                            for leaf in ("shared-base", "research-assistant"): (directory / name / leaf).mkdir(parents=True)
                    return expect_error(lambda: helpers["discover_artifacts"](directory), RuntimeError)
                probe("K06" if variant == "absent" else "K07", "dataset-discovery-" + variant,
                      "Reject filesystem fixture; does not detach real Dataset", discovery)

            for variant in ("garbage", "truncated", "traversal", "absolute", "drive", "symlink"):
                def unsafe_zip(variant=variant):
                    data = io.BytesIO()
                    name = {"traversal": "../escape.txt", "absolute": "/escape.txt", "drive": "C:/escape.txt"}.get(variant, "file.txt")
                    with zipfile.ZipFile(data, "w") as archive:
                        info = zipfile.ZipInfo(name)
                        if variant == "symlink": info.create_system = 3; info.external_attr = (0o120777 << 16)
                        archive.writestr(info, "fixture")
                    value = data.getvalue()
                    if variant == "garbage": value = b"NOT_A_ZIP"
                    if variant == "truncated": value = value[:len(value)//2]
                    result = expect_error(lambda: helpers["extract_zip"](io.BytesIO(value), out / ("extract-" + variant)),
                                          (zipfile.BadZipFile, ValueError))
                    assert not (out / "escape.txt").exists()
                    return result
                probe("K11", "zip-" + variant, "Reject invalid/escaping/symlink archive before extraction", unsafe_zip)

            files = {}
            def artifact_files(node):
                if isinstance(node, dict):
                    if isinstance(node.get("files"), list):
                        files.update({entry["path"]: entry["sha256"] for entry in node["files"]})
                    for value in node.values(): artifact_files(value)
                elif isinstance(node, list):
                    for value in node: artifact_files(value)
            artifact_files(manifest); assert len(files) == 19
            mirror = out / "artifact-mirror"
            link_error = None
            try:
                for relative in files:
                    target = mirror / relative; target.parent.mkdir(parents=True, exist_ok=True)
                    link_artifact(args.artifact_root / relative, target)
            except OSError as error:
                link_error = type(error).__name__
            for variant in ("missing-shard", "missing-adapter", "changed-byte", "crlf"):
                def artifact_fault(variant=variant):
                    if link_error: raise ProbeSkipped("Readonly artifacts are on another filesystem; refusing a full weights copy")
                    candidates = [p for p in files if (p.endswith(".safetensors") and ("research-assistant" in p) == (variant == "missing-adapter"))]
                    relative = candidates[0] if variant.startswith("missing") else next(p for p in files if p.endswith("config.json"))
                    original, target = args.artifact_root / relative, mirror / relative
                    if variant.startswith("missing"):
                        target.unlink()  # Only this run's hardlink; the source inode remains intact.
                    else:
                        value = original.read_bytes(); isolated = target.with_name(target.name + ".isolated")
                        isolated.write_bytes((bytes([value[0] ^ 1]) + value[1:]) if variant == "changed-byte" else value + b"\r\n")
                        isolated.replace(target)  # Never write through a link to Dataset/model files.
                    try:
                        code, output = run([args.python, str(service / "scripts/prepare_runtime_artifacts.py"), "--manifest", str(manifest_path),
                                            "verify", "--artifact-root", str(mirror)], variant)
                        assert code != 0 and ("Missing artifact file" if variant.startswith("missing") else "Checksum mismatch") in output
                        assert sha256_file(original) == files[relative]
                        return {"exitCode": code, "file": relative, "sourceChecksumUnchanged": True}
                    finally:
                        if target.is_symlink() or target.exists(): target.unlink()
                        link_artifact(original, target)
                probe("K09" if variant.startswith("missing") else "K10", variant, "Reject fault mirror; preserve source bytes", artifact_fault)

            validator = next(n for n in ast.parse(fragments["start"]).body if isinstance(n, ast.If)
                             and "TOKEN" in {v.id for v in ast.walk(n) if isinstance(v, ast.Name)})
            for label, value in [("empty", ""), ("whitespace", " "), ("embedded-space", "a b"), ("non-ascii", "test-đ"), ("long", "x"*1025)]:
                probe("K05", "secret-" + label, "Notebook validator rejects invalid token without printing it",
                      lambda value=value: expect_error(lambda: exec(compile(ast.Module([validator], []), "token-validation.py", "exec"), {"TOKEN": value}), ValueError))

        if "service" in groups or "gpu" in groups:
            process = start("model-not-loaded", child_env)
            def not_loaded():
                health = wait_health(process)
                status, body = http_json(origin, "/ready", child_token)
                assert status == 503 and body["ready"] is False and body["modelLoaded"] is False
                return {"healthStatus": 200, "readyStatus": status, "readiness": body}
            probe("K15", "health-up-ready-unavailable", "health200 is insufficient when model is not loaded", not_loaded)

            def duplicate_start():
                block = ast.parse(fragments["start"]).body[0]
                result = expect_error(lambda: exec(compile(ast.Module([block], []), "duplicate-start.py", "exec"), {"ai_process": process}), RuntimeError)
                assert process.poll() is None
                return {**result, "ownedProcessStillAlive": True}
            probe("K17", "duplicate-start-guard", "Reject duplicate startup without stopping current process", duplicate_start)

            def process_exit():
                worker = subprocess.Popen([args.python, "-c", "import os; os._exit(23)"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                owned.append(worker); worker.wait(timeout=15)
                ns = {**helpers, "ai_process": worker, "PORT": 8092, "TOKEN": child_token, "SERVICE": service}
                result = expect_error(lambda: exec(fragments["readiness"], ns), RuntimeError)
                assert ns["READY"] is False
                return {**result, "exitCode": worker.returncode, "ready": False}
            probe("K14", "startup-process-exit", "Notebook readiness guard detects real child process exit", process_exit)

            tunnel_ns = None
            def tunnel_failure(blocked):
                nonlocal tunnel_ns
                work = out / ("tunnel-blocked" if blocked else "tunnel-unready")
                work.mkdir()
                binary = args.fragments.parent / "cloudflared"
                assert binary.is_file(), "Use the verified notebook's downloaded cloudflared"
                os.link(binary, work / "cloudflared")
                ns = {**helpers, "READY": True, "ai_process": process, "WORK": work,
                      "LOCAL_URL": origin, "TOKEN": child_token,
                      "CONTRACT": json.loads((source / "contracts/ai-runtime-contract.json").read_text())}
                refused = None
                try:
                    if blocked:
                        # cloudflared's Quick Tunnel transport ignores HTTP_PROXY.
                        # Configure its real provisioning request to a bound, non-listening socket.
                        refused = socket.socket()
                        refused.bind(("127.0.0.1", 0))
                        refused_origin = "http://127.0.0.1:" + str(refused.getsockname()[1])
                        def refused_provisioning(command, **kwargs):
                            assert command[0] == str(work / "cloudflared") and command[1] == "tunnel"
                            return subprocess.Popen([*command, "--quick-service", refused_origin], **kwargs)
                        ns["subprocess"] = SimpleNamespace(run=subprocess.run, Popen=refused_provisioning, STDOUT=subprocess.STDOUT)
                    result = expect_error(lambda: exec(fragments["tunnel"], ns), (RuntimeError, TimeoutError))
                finally:
                    child = ns.get("tunnel_process")
                    if child is not None: owned.append(child)
                    if refused is not None: refused.close()
                    if blocked: stop(child)
                if blocked:
                    assert not ns.get("PUBLIC_URL")
                    assert "connection refused" in (work / "cloudflared.log").read_text(errors="replace").lower()
                    return {**result, "urlGenerated": False, "fault": "Real Cloudflare provisioning connection refused via --quick-service"}
                tunnel_ns = ns
                assert ns.get("PUBLIC_URL") and ns["tunnel_process"].poll() is None
                status, body = http_json(ns["PUBLIC_URL"], "/ready", child_token, 20)
                assert status == 503 and body["ready"] is False
                return {**result, "publicReadyStatus": status, "publicReady": False, "publicUrl": ns["PUBLIC_URL"]}

            probe("K18", "tunnel-no-url-provisioning-refused", "Unchanged tunnel cell detects real cloudflared provisioning failure without URL", lambda: tunnel_failure(True))
            probe("K18", "tunnel-public-ready-fails", "Exact tunnel cell rejects public readiness503 despite a real URL", lambda: tunnel_failure(False))

            def stop_flags():
                block = ast.parse(fragments["stop"]).body[1]
                tunnel = tunnel_ns.get("tunnel_process") if tunnel_ns else None
                assert tunnel is not None and tunnel.poll() is None, "A real owned tunnel must be tested too"
                ns = {"STOP_SERVICES": False, "ai_process": process, "tunnel_process": tunnel, "READY": True, "subprocess": subprocess}
                exec(compile(ast.Module([block], []), "stop-services.py", "exec"), ns)
                assert process.poll() is None and tunnel.poll() is None and ns["READY"] is True
                ns["STOP_SERVICES"] = True
                exec(compile(ast.Module([block], []), "stop-services.py", "exec"), ns)
                assert process.poll() is not None and tunnel.poll() is not None and ns["READY"] is False
                return {"disabledFlagKeptBothAlive": True, "enabledFlagStoppedOwnedServiceAndTunnel": True,
                        "serviceExitCode": process.returncode, "tunnelExitCode": tunnel.returncode}
            probe("K21", "stop-cell-explicit-flag", "Exact stop cell preserves/stops its owned service according to flag", stop_flags)
            stop(process)

            def bad_artifact_restart():
                broken = start("restart-missing-artifacts", {**child_env, "AI_MODEL_ARTIFACT_ROOT": str(out / "no-artifacts")})
                code = broken.wait(timeout=120)
                text = (out / "restart-missing-artifacts.log").read_text(errors="replace")
                assert code != 0 and "ARTIFACT_FILE_MISSING" in text
                return {"exitCode": code, "errorCode": "ARTIFACT_FILE_MISSING"}
            probe("Q14", "restart-rejects-missing-artifacts", "Broken artifact restart exits without readiness", bad_artifact_restart)

        if "gpu" in groups:
            gpu = subprocess.run(["nvidia-smi", "--query-gpu=index,name,memory.used,memory.total", "--format=csv,noheader,nounits"],
                                 capture_output=True, text=True, check=True).stdout
            metadata["gpuBeforeFaults"] = gpu
            devices = [line.split(",") for line in gpu.strip().splitlines()]
            can_use_second = len(devices) >= 2 and int(devices[1][2].strip()) < 1024

            def oom():
                if not can_use_second: raise ProbeSkipped("A second idle GPU is required; serving GPU0 must stay untouched")
                marker = out / "actual-oom.json"
                code = ("import json, pathlib, torch\n"
                        "from app.runtime import TransformersRuntimeBackend\n"
                        "torch.cuda.set_per_process_memory_fraction(0.01, 0)\n"
                        "original = TransformersRuntimeBackend.load_base_model\n"
                        "def observed(self, *args, **kwargs):\n"
                        "    try: return original(self, *args, **kwargs)\n"
                        "    except Exception as error:\n"
                        f"        pathlib.Path({str(marker)!r}).write_text(json.dumps({{'errorType':type(error).__name__, 'actualCudaOom':isinstance(error,torch.cuda.OutOfMemoryError)}}))\n"
                        "        raise\n"
                        "TransformersRuntimeBackend.load_base_model = observed\n"
                        "import uvicorn\n"
                        "uvicorn.run('app.main:app',host='127.0.0.1',port=8092,workers=1,access_log=False)\n")
                worker = start("actual-model-load-oom", {**child_env, "AI_RUNTIME_LOAD_ENABLED": "true", "CUDA_VISIBLE_DEVICES": "1"}, code)
                try:
                    wait_health(worker, 420)
                    status, body = http_json(origin, "/ready", child_token)
                    observed = json.loads(marker.read_text())
                    assert observed["actualCudaOom"] is True
                    assert status == 503 and body["ready"] is False and body["modelStatus"] == "ERROR"
                    return {"observation": observed, "readyStatus": status, "readiness": body, "physicalGpu": 1, "allocatorFraction": 0.01}
                finally: stop(worker)
            probe("K14,Q14", "real-model-load-cuda-oom", "Real model loader hits CUDA OOM in a separate GPU1 process; readiness503", oom)

            def recover():
                if not can_use_second: raise ProbeSkipped("A second idle GPU is required for isolated model recovery")
                worker = start("recovered-model", {**child_env, "AI_RUNTIME_LOAD_ENABLED": "true", "CUDA_VISIBLE_DEVICES": "1"})
                started = time.monotonic()
                try:
                    wait_health(worker, 600)
                    status, body = http_json(origin, "/ready", child_token)
                    assert status == 200 and body["ready"] is True
                    cold_seconds = round(time.monotonic() - started, 3)
                    runtime_out = out / "recovery-runtime"
                    code, _ = run([args.python, str(service / "scripts/verify_runtime.py"), "--base-url", origin,
                                   "--source-root", str(source), "--expected-source-sha", fragments["sourceZipSha256"],
                                   "--case-ids", "admin-account-vi,lab-booking-vi,research-report-manager-spring", "--output-dir", str(runtime_out)],
                                  "recovery-inference", {**child_env, "AI_INTERNAL_SERVICE_TOKEN": child_token}, timeout=420)
                    evidence = json.loads((runtime_out / "runtime-results.json").read_text())
                    assert code == 0 and evidence["passed"] == 3 and evidence["failed"] == 0 and not evidence.get("blocked")
                    return {"readyStatus": status, "physicalGpu": 1, "passedInferenceVariants": 3,
                            "coldStartupReadySeconds": cold_seconds,
                            "runtimeEvidence": str(runtime_out.relative_to(out) / "runtime-results.json")}
                finally: stop(worker)
            probe("Q14", "fresh-process-recovery-and-real-inference", "Restore real artifacts after failed restart/OOM; all three assistants generate valid responses", recover)

        status, healthy = http_json(args.base_url, "/ready", token)
        assert status == 200 and healthy["ready"] is True
        metadata["servingProcessReadyAfterFaults"] = True
        metadata["completedAtUtc"] = datetime.now(timezone.utc).isoformat(); save()
    finally:
        for process in reversed(owned): stop(process)
    return int(any(row["status"] == "FAIL" for row in rows))


if __name__ == "__main__":
    raise SystemExit(main())
