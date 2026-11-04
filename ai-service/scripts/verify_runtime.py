"""Run assertion-based AI runtime checks on Kaggle; never writes business data."""
from __future__ import annotations

import hashlib
import io
import json
import argparse
from datetime import datetime, timezone
import os
import re
from pathlib import Path
import time
import uuid
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET
import zipfile


def verify_source(root: Path) -> dict:
    root = root.resolve()
    manifest_path = root / "kaggle-source-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    contents = {}
    if not manifest.get("files"):
        raise ValueError("Source manifest is empty")
    for name, expected in manifest["files"].items():
        path = (root / name).resolve()
        if not path.is_relative_to(root):
            raise ValueError("Unsafe source manifest path")
        content = path.read_bytes()
        if hashlib.sha256(content).hexdigest() != expected:
            raise ValueError("Source checksum mismatch: " + name)
        contents[name] = content
    contents[manifest_path.name] = manifest_path.read_bytes()
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for name, content in sorted(contents.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.create_system = 0  # Canonical ZIP metadata, identical on Windows and Kaggle Linux.
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            bundle.writestr(info, content)
    return {"fileCount": len(manifest["files"]), "gitRevision": manifest.get("gitRevision"),
            "sourceZipSha256": hashlib.sha256(output.getvalue()).hexdigest()}


def select_cases(cases: list[dict], selectors: list[str]) -> list[dict]:
    def matches(case, selector):
        return selector in {"all", case["id"], case["group"], *case.get("caseIds", [])}
    for selector in selectors:
        if not any(matches(case, selector) for case in cases):
            raise ValueError("Unknown runtime case/group: " + selector)
    return [case for case in cases if any(matches(case, selector) for selector in selectors)]


def check_response(case: dict, status: int, body: dict) -> list[str]:
    errors = []
    if status != case.get("status", 200):
        return [f"HTTP {status}; expected {case.get('status', 200)}"]
    if case.get("status", 200) != 200:
        return [] if body.get("errorCode") == case.get("errorCode") else ["Wrong errorCode"]
    if (body.get("metadata", {}).get("safeRefusal") is True
            and case.get("responseExpected", {}).get("metadata.safeRefusal") is not True):
        errors.append("Unexpected safe refusal")
    if case.get("assistantKey") and body.get("assistantKey") != case["assistantKey"]:
        errors.append("Wrong assistantKey")
    if case.get("modelRequired") and not all(
        isinstance(body.get(key), int) and body[key] > 0 for key in ("promptTokens", "completionTokens")
    ):
        errors.append("Model generation tokens required")
    answer = body.get("answer", "")
    if case.get("messagePattern") and not re.search(case["messagePattern"], body.get("message") or ""):
        errors.append("Missing explicit workflow limitation")
    for term in case.get("contains", []):
        if term not in answer:
            errors.append("Missing fact: " + term)
    for term in case.get("excludes", []):
        if term in answer:
            errors.append("Forbidden fact: " + term)
    for field in ("expected", "responseExpected"):
        try:
            actual = json.loads(answer) if field == "expected" and case.get(field) else body
            for path, expected in case.get(field, {}).items():
                value = actual
                for part in path.split("."):
                    value = value[int(part)] if isinstance(value, list) else value[part]
                if path in case.get("unorderedFields", []) and isinstance(value, list) and isinstance(expected, list):
                    value = sorted(json.dumps(item, sort_keys=True) for item in value)
                    expected = sorted(json.dumps(item, sort_keys=True) for item in expected)
                if type(value) is not type(expected) or value != expected:
                    errors.append("Wrong " + path)
        except (ValueError, KeyError, IndexError, TypeError):
            errors.append("Invalid response shape/JSON: " + field)
    return errors


def write_results(output: Path, metadata: dict, results: list[dict]) -> None:
    output.mkdir(parents=True, exist_ok=True)
    failed = sum(not row["passed"] for row in results)
    report = {**metadata, "passed": len(results) - failed, "failed": failed, "results": results,
              "scope": "Runtime variants with synthetic authorized contexts; not portal authorization or DB writes"}
    (output / "runtime-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    blocked = bool(metadata.get("blocked"))
    suite = ET.Element("testsuite", name="ai-runtime", tests=str(len(results) + int(blocked)),
                       failures=str(failed), errors=str(int(blocked)))
    if blocked:
        ET.SubElement(ET.SubElement(suite, "testcase", name="runtime-preflight"), "error",
                      message=metadata.get("blockedReason", metadata["blocked"]))
    for row in results:
        case = ET.SubElement(suite, "testcase", name=row["id"] + "-" + str(row["repeat"]),
                             classname="runtime", time=str(row["seconds"]))
        if not row["passed"]:
            ET.SubElement(case, "failure", message="; ".join(row["errors"]))
    ET.ElementTree(suite).write(output / "runtime-results.xml", encoding="utf-8", xml_declaration=True)


class _NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def http_json(url: str, token: str, payload=None, *, request_id: str, timeout: float):
    headers = {"X-Request-Id": request_id}
    if token:
        headers["X-Internal-Service-Token"] = token
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    try:
        response = build_opener(_NoRedirect).open(Request(url, data=data, headers=headers), timeout=timeout)
    except HTTPError as error:
        response = error
    with response:
        return response.status, json.loads(response.read().decode("utf-8"))


def run_case(case: dict, base_url: str, token: str, repeat: int, timeout: float) -> dict:
    started = time.monotonic()
    request_id = "verify-" + uuid.uuid4().hex
    row = {"id": case["id"], "caseIds": case.get("caseIds", []), "group": case["group"],
           "repeat": repeat, "requestId": request_id, "evidenceLayer": "model-runtime",
           "startedAtUtc": datetime.now(timezone.utc).isoformat(),
           "expected": {key: case[key] for key in ("expected", "responseExpected", "contains", "excludes", "messagePattern", "modelRequired", "unorderedFields") if key in case}}
    try:
        status, body = http_json(base_url + case.get("path", "/v1/assistants/chat"), token,
                                 case.get("request"), request_id=request_id, timeout=timeout)
        errors = check_response(case, status, body)
        if token and token in json.dumps(body, ensure_ascii=False):
            errors.append("Internal token leaked in response")
        safe_body = json.loads(json.dumps(body, ensure_ascii=False).replace(token, "[REDACTED]")) if token else body
        row.update(status=status, response=safe_body, errors=errors, passed=not errors)
    except Exception as error:
        row.update(passed=False, errors=["Transport/protocol: " + type(error).__name__])
    row["seconds"] = round(time.monotonic() - started, 3)
    return row


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--corpus", type=Path, default=Path(__file__).resolve().parents[1] / "tests/fixtures/runtime-corpus.json")
    parser.add_argument("--source-root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--expected-source-sha", required=True)
    parser.add_argument("--artifact-root", type=Path, help="Stream-verify the pinned physical artifacts before inference")
    parser.add_argument("--case-ids", default="all", help="Comma-separated case IDs, variant IDs or groups")
    parser.add_argument("--repeat", type=int, choices=range(1, 11), default=1)
    parser.add_argument("--concurrency", type=int, choices=(1, 2, 4), default=1)
    parser.add_argument("--timeout", type=float, default=110)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    if not 0 < args.timeout <= 300:
        parser.error("Timeout must be between 0 and 300 seconds")
    if (args.output_dir / "runtime-results.json").exists():
        parser.error("Output already contains evidence; use a new output directory")
    parsed = urlsplit(args.base_url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        parser.error("Base URL must be an HTTP(S) origin without credentials or query")
    metadata = {"backend": "RUNTIME_HTTP", "baseUrl": args.base_url, "concurrency": args.concurrency,
                "startedAtUtc": datetime.now(timezone.utc).isoformat()}
    results = []
    try:
        source = verify_source(args.source_root)
        if source["sourceZipSha256"] != args.expected_source_sha:
            raise ValueError("Expected source checksum does not match this bundle")
        metadata.update(source)
        if args.artifact_root:
            verified = subprocess.run([sys.executable, str(Path(__file__).with_name("prepare_runtime_artifacts.py")),
                "--manifest", str(args.source_root / "ai-service/config/model-artifacts.runtime-t4.json"),
                "verify", "--artifact-root", str(args.artifact_root)], capture_output=True, text=True)
            if verified.returncode:
                raise ValueError("Physical artifact verification failed")
            metadata["physicalArtifactsVerified"] = True
        token = os.environ.get("AI_INTERNAL_SERVICE_TOKEN", "")
        if not token:
            raise ValueError("AI_INTERNAL_SERVICE_TOKEN is required")
        cases = select_cases(json.loads(args.corpus.read_text(encoding="utf-8"))["cases"], args.case_ids.split(","))
        for route in ("/ready", "/model-info"):
            status, body = http_json(args.base_url.rstrip("/") + route, token, request_id="verify-preflight-" + uuid.uuid4().hex, timeout=20)
            if status != 200 or any(body.get(key) is not True for key in
                                   ("ready", "profileLoaded", "artifactValidated", "modelLoaded", "adapterLoaded")):
                raise ValueError("Runtime is not fully ready")
            metadata[route[1:]] = body
        model = json.loads((args.source_root / "ai-service/config/model-artifacts.runtime-t4.json").read_text(encoding="utf-8"))
        info = metadata["model-info"]
        adapter = model["assistantAdapters"]["RESEARCH_ASSISTANT"]
        if (info.get("modelRevision") != model["baseModel"]["revision"]
                or info.get("artifactIdentity") != model["baseModel"]["artifact"]["identity"]
                or info.get("assistantAdapters", {}).get("RESEARCH_ASSISTANT", {}).get("artifactIdentity") != adapter["artifact"]["identity"]):
            raise ValueError("Runtime model/adapter identity mismatch")
        for repeat in range(1, args.repeat + 1):
            with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
                for row in pool.map(lambda case: run_case(case, args.base_url.rstrip("/"), token, repeat, args.timeout), cases):
                    results.append(row)
                    write_results(args.output_dir, metadata, results)
                    print(row["id"], "PASS" if row["passed"] else "FAIL", row["seconds"], flush=True)
        return 1 if any(not row["passed"] for row in results) else 0
    except Exception as error:
        metadata["blocked"] = type(error).__name__
        metadata["blockedReason"] = str(error) if isinstance(error, ValueError) else type(error).__name__
        write_results(args.output_dir, metadata, results)
        print("Runtime verification BLOCKED:", type(error).__name__, flush=True)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
