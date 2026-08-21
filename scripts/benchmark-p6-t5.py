#!/usr/bin/env python3
"""Offline, fail-closed P6-T5 benchmark runner.

This runner deliberately has no default network, package-install, or model-load
side effect.  ``--run`` is an explicit local operator action and it is reached
only after the frozen-evaluation, lock, provenance, and GPU-quiescence gates.
"""
from __future__ import annotations

import argparse
import gc
import hashlib
import importlib.util
import json
import math
import os
import platform
import re
import subprocess
import sys
import tempfile
import threading
import time
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from typing import Any
from importlib import metadata

import yaml

ROOT = Path(__file__).resolve().parents[1]
EVALUATOR_PATH = ROOT / "scripts" / "validate-evaluation-suites.py"
DEFAULT_CONFIG = ROOT / "config" / "p6-t5-benchmark.yaml"
DEFAULT_LOCK = ROOT / "requirements" / "p6-t5-benchmark-requirements.txt"
ARTIFACT_ROOT = ROOT / ".artifacts" / "p6-t5"
CACHE_ROOT = ROOT / ".cache" / "p6-t5"
CASE_COUNT = 55
REPETITIONS = ("R01", "R02", "R03")
LOCK_LINE = re.compile(r"^([A-Za-z0-9_.-]+)==([A-Za-z0-9+_.!-]+) --hash=sha256:([0-9a-f]{64})$")
ASSISTANT_PROFILE_CONTRACT = {
    "ADMIN_ASSISTANT": ("admin", "admin-v2"),
    "LAB_ASSISTANT": ("lab", "lab-v2"),
    "RESEARCH_ASSISTANT": ("research", "research-v2"),
}
BENCHMARK_VERSION = "1.1.0"
PUBLIC_CASE_FIELDS = frozenset({"evalCaseId", "assistantKey", "caseState", "suiteTags", "useCaseId", "input",
                                "authorizedContext", "p6t3Root", "allowedTool", "rejectedTool",
                                "structuredOutputContract", "responseContract", "referencedContextIds"})
PUBLIC_P6T3_CONTEXT_FIELDS = ("domain", "recordType", "useCaseId", "input", "payload")
NO_AUTHORIZED_CONTEXT = {"state": "NO_AUTHORIZED_CONTEXT"}
CRITICAL_SAFETY_SUITE_TAGS = frozenset({"AUTHORIZATION", "PROMPT_INJECTION", "CROSS_DOMAIN"})
CRITICAL_SAFETY_REJECTION_REASONS = frozenset({"PROHIBITED", "UNKNOWN_TOOL", "CONFIRMATION_REQUIRED", "APPROVAL_REQUIRED"})
RUNTIME_TARGET_REGISTRY = {
    "kaggle-linux-cp312-cu118": {
        "configPath": "config/p6-t5-benchmark-kaggle-linux-cp312.yaml",
        "lockPath": "requirements/p6-t5-benchmark-kaggle-linux-cp312-requirements.txt",
        "lockSha256": "087e3b05c648b2e0af1ed9f72e63d8e9cf02b11956eda4a48fa0e7b3ffd47212",
        "manifestSha256": "8ec50e493376e4c7a373594f4b213e1b68815c4ae565ecf5b73e2122eb7de945",
        "pythonVersion": "3.12.13",
        "torchIndex": "https://download.pytorch.org/whl/cu118",
        "target": {
            "id": "kaggle-linux-cp312-cu118",
            "operatingSystem": "Linux",
            "architecture": "x86_64",
            "pythonImplementation": "CPython",
            "pythonVersion": "3.12.13",
            "selectedDevice": "cuda:0",
            "requiredDeviceCount": 2,
            "requiredGpuModel": "Tesla T4",
            "requiredGpuVramMiB": 15360,
            "expectedTorchCuda": "11.8",
            "allowedWheelTagFamilies": ["py3-none-any", "py2.py3-none-any", "cp312-cp312-manylinux", "abi3-manylinux"],
        },
    },
}
RECEIPT_V2_FIELDS = frozenset({"receiptSchemaVersion", "targetId", "createdAt", "configPath", "configSha256", "lockPath", "lockSha256",
                               "manifestSha256", "platform", "installation", "artifacts", "torch", "nvidia", "gpus", "receiptChecksum"})
UTC_TIMESTAMP = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$")
PIP_VERSION = re.compile(r"^\d+(?:\.\d+)+(?:[A-Za-z0-9.+-]*)?$")


class BenchmarkError(RuntimeError):
    """A deterministic benchmark failure that must be recorded, not repaired."""


class GpuQuiescenceError(BenchmarkError):
    pass


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def digest(value: object) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def atomic_write_json(path: Path, value: object, *, append_only: bool = False) -> None:
    if append_only and path.exists():
        raise BenchmarkError(f"RAW_EVIDENCE_ALREADY_EXISTS: {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False) as handle:
            temporary = Path(handle.name)
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()


def load_yaml(path: Path) -> dict[str, Any]:
    value = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise BenchmarkError(f"CONFIG_INVALID: expected mapping: {path}")
    return value


def load_evaluator() -> Any:
    spec = importlib.util.spec_from_file_location("p6t4_evaluator", EVALUATOR_PATH)
    if spec is None or spec.loader is None:
        raise BenchmarkError("P6_T4_EVALUATOR_UNAVAILABLE")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def prompt_input(case: dict[str, Any]) -> dict[str, Any]:
    """Project the closed prompt contract without examining oracle-bearing keys.

    Canonical P6-T4 cases deliberately retain scoring declarations next to their
    inputs.  Rejecting their presence makes the immutable suite impossible to
    render; a positive projection instead ensures those declarations cannot
    influence the prompt, including after an oracle-only mutation.
    """
    required = {"evalCaseId", "assistantKey", "caseState", "input"}
    if not required.issubset(case):
        raise ValueError("PROMPT_INPUT_INVALID")
    return {key: case[key] for key in sorted(case) if key in PUBLIC_CASE_FIELDS}


@lru_cache(maxsize=1)
def p6t3_context_source() -> tuple[Any, dict[str, dict[str, Any]], dict[str, Any]]:
    """Load the evaluator-owned P6-T3 validators once for read-only projection."""
    try:
        evaluator = load_evaluator()
        records, validators = evaluator.p6t3_records()
    except Exception as error:
        raise BenchmarkError("P6_T3_CONTEXT_INVALID: evaluator source unavailable") from error
    return evaluator, records, validators


def resolve_public_context(case: dict[str, Any]) -> dict[str, Any]:
    """Resolve one validated synthetic P6-T3 record into an allowlisted view."""
    evaluator, records, validators = p6t3_context_source()
    try:
        errors = evaluator.resolve_context(case, records, validators)
    except Exception as error:
        raise BenchmarkError("P6_T3_CONTEXT_INVALID: malformed context") from error
    if errors:
        raise BenchmarkError("P6_T3_CONTEXT_INVALID: " + "; ".join(errors))
    reference = case.get("authorizedContext")
    if reference is None:
        return dict(NO_AUTHORIZED_CONTEXT)
    fixture_case_id = reference["p6t3FixtureCaseId"]
    record = records[fixture_case_id]
    projected = {"p6t3FixtureCaseId": fixture_case_id}
    projected.update({key: record[key] for key in PUBLIC_P6T3_CONTEXT_FIELDS if key in record})
    return projected


def render_prompt(case: dict[str, Any], system_instruction: str) -> list[dict[str, str]]:
    source = prompt_input(case)
    user = json.dumps({**source, "authorizedContext": resolve_public_context(case)},
                      ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return [{"role": "system", "content": system_instruction}, {"role": "user", "content": user}]


def assistant_profile(config: dict[str, Any], assistant_key: str) -> dict[str, str]:
    """Return a closed, architecture-bound profile/template record."""
    profiles = config.get("assistantProfiles")
    if not isinstance(profiles, dict) or set(profiles) != set(ASSISTANT_PROFILE_CONTRACT):
        raise BenchmarkError("PROFILE_TEMPLATE_BINDING_INVALID")
    profile = profiles.get(assistant_key)
    expected = ASSISTANT_PROFILE_CONTRACT.get(assistant_key)
    if (not isinstance(profile, dict) or set(profile) != {"profile", "prompt", "systemInstruction"}
            or expected is None or (profile.get("profile"), profile.get("prompt")) != expected
            or not isinstance(profile.get("systemInstruction"), str) or not profile["systemInstruction"].strip()):
        raise BenchmarkError("PROFILE_TEMPLATE_BINDING_INVALID")
    return {key: profile[key] for key in ("profile", "prompt", "systemInstruction")}


def validate_profile_templates(config: dict[str, Any]) -> None:
    if config.get("benchmarkVersion") != BENCHMARK_VERSION:
        raise BenchmarkError("PROFILE_TEMPLATE_BINDING_INVALID")
    for assistant_key in ASSISTANT_PROFILE_CONTRACT:
        assistant_profile(config, assistant_key)


def context_budget(prompt_tokens: int, model_context_limit: int, config: dict[str, Any]) -> dict[str, int | bool]:
    runtime = config.get("runtime")
    decode = runtime.get("decode") if isinstance(runtime, dict) else None
    configured_limit = runtime.get("contextBudget") if isinstance(runtime, dict) else None
    max_new_tokens = decode.get("maxNewTokens") if isinstance(decode, dict) else None
    if (not isinstance(prompt_tokens, int) or prompt_tokens < 0 or not isinstance(model_context_limit, int)
            or model_context_limit <= 0 or not isinstance(configured_limit, int) or configured_limit <= 0
            or not isinstance(max_new_tokens, int) or max_new_tokens <= 0):
        raise BenchmarkError("CONTEXT_BUDGET_INVALID")
    effective_limit = min(model_context_limit, configured_limit)
    evidence = {"modelContextLimit": model_context_limit, "configuredContextLimit": configured_limit,
                "effectiveContextLimit": effective_limit, "promptTokens": prompt_tokens,
                "maxNewTokens": max_new_tokens, "accepted": prompt_tokens + max_new_tokens <= effective_limit}
    if not evidence["accepted"]:
        raise BenchmarkError("CONTEXT_BUDGET_EXCEEDED")
    return evidence


def model_context_limit(model: Any, tokenizer: Any) -> int:
    """Resolve a concrete finite model limit; unknown/unbounded limits are unsafe."""
    values = [getattr(getattr(model, "config", None), key, None)
              for key in ("max_position_embeddings", "max_sequence_length", "n_positions")]
    values.append(getattr(tokenizer, "model_max_length", None))
    finite = [value for value in values if isinstance(value, int) and 0 < value < 1_000_000]
    if not finite:
        raise BenchmarkError("CONTEXT_BUDGET_UNVERIFIABLE")
    return min(finite)


def generation_kwargs(decode: dict[str, Any], *, warmup: bool = False) -> dict[str, Any]:
    """Keep all decode controls explicit, including the no-top-k policy."""
    expected = {"doSample": False, "temperature": 0, "topP": 1.0, "topK": None,
                "repetitionPenalty": 1.0, "maxNewTokens": 512, "seed": 20260815}
    if any(decode.get(key) != value for key, value in expected.items()):
        raise BenchmarkError("DECODE_POLICY_INVALID")
    return {"do_sample": False, "temperature": 0.0, "top_p": 1.0, "top_k": None,
            "repetition_penalty": 1.0, "max_new_tokens": 1 if warmup else expected["maxNewTokens"],
            "use_cache": True}


def configure_determinism(torch_module: Any, seed: int) -> dict[str, Any]:
    """Configure and record deterministic Torch/CuDNN execution before loading a model."""
    try:
        torch_module.manual_seed(seed)
        torch_module.cuda.manual_seed_all(seed)
        torch_module.use_deterministic_algorithms(True)
        torch_module.backends.cudnn.deterministic = True
        torch_module.backends.cudnn.benchmark = False
        torch_module.backends.cuda.matmul.allow_tf32 = False
        torch_module.backends.cudnn.allow_tf32 = False
    except Exception as error:
        raise BenchmarkError("DETERMINISM_UNAVAILABLE") from error
    return {"seed": seed, "deterministicAlgorithms": bool(torch_module.are_deterministic_algorithms_enabled()),
            "cudnnDeterministic": bool(torch_module.backends.cudnn.deterministic),
            "cudnnBenchmark": bool(torch_module.backends.cudnn.benchmark),
            "cudaMatmulAllowTf32": bool(torch_module.backends.cuda.matmul.allow_tf32),
            "cudnnAllowTf32": bool(torch_module.backends.cudnn.allow_tf32)}


def scored_case(eval_case_id: str, parsed: dict[str, Any] | None, parse_error: str | None) -> dict[str, Any]:
    """Retain malformed output as a closed, scorer-visible failed observation."""
    if parsed is not None:
        return parsed
    failure = parse_error or "RAW_OUTPUT_PARSE_FAILURE"
    return {"evalCaseId": eval_case_id, "response": None, "observedBehavior": failure,
            "observedActionRisk": failure, "toolRequest": None, "structuredOutput": None,
            "referencedContextIds": []}


def parse_raw_response(eval_case_id: str, raw_text: str) -> tuple[dict[str, Any] | None, str | None]:
    """Parse a model-owned closed envelope without access to suite expectations."""
    try:
        value = json.loads(raw_text)
    except (TypeError, json.JSONDecodeError):
        return None, "RAW_OUTPUT_PARSE_FAILURE"
    if not isinstance(value, dict) or value.get("evalCaseId") != eval_case_id:
        return None, "RAW_OUTPUT_CASE_ID_MISMATCH"
    expected = {"evalCaseId", "response", "observedBehavior", "observedActionRisk", "toolRequest", "structuredOutput", "referencedContextIds"}
    if set(value) != expected:
        return None, "RAW_OUTPUT_ENVELOPE_INVALID"
    return value, None


def parse_compute_pids(output: str) -> dict[int, dict[str, Any]]:
    entries: dict[int, dict[str, Any]] = {}
    for line in output.splitlines():
        if not line.strip():
            continue
        parts = [part.strip() for part in line.split(",")]
        if len(parts) != 3 or not parts[0].isdigit() or not parts[2].isdigit():
            raise ValueError("GPU_PID_SNAPSHOT_MALFORMED")
        pid = int(parts[0])
        if pid in entries or not parts[1]:
            raise ValueError("GPU_PID_SNAPSHOT_MALFORMED")
        entries[pid] = {"name": parts[1], "memoryMiB": int(parts[2])}
    return entries


def compute_pid_snapshot() -> dict[int, dict[str, Any]]:
    command = ["nvidia-smi", "--query-compute-apps=pid,process_name,used_memory", "--format=csv,noheader,nounits"]
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=True, timeout=10)
        return parse_compute_pids(result.stdout)
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        raise GpuQuiescenceError("GPU_QUIESCENCE_UNVERIFIABLE") from error


def parse_gpu_inventory(output: str) -> list[dict[str, Any]]:
    inventory: list[dict[str, Any]] = []
    for line in output.splitlines():
        parts = [part.strip() for part in line.split(",")]
        if len(parts) != 3 or not parts[0].isdigit() or not parts[2].isdigit() or not parts[1]:
            raise ValueError("GPU_INVENTORY_MALFORMED")
        inventory.append({"index": int(parts[0]), "model": parts[1], "totalVramMiB": int(parts[2])})
    if [item["index"] for item in inventory] != list(range(len(inventory))):
        raise ValueError("GPU_INVENTORY_MALFORMED")
    return inventory


def gpu_inventory() -> tuple[str, list[dict[str, Any]]]:
    try:
        driver = subprocess.run(["nvidia-smi", "--query-gpu=driver_version", "--format=csv,noheader,nounits"],
                                capture_output=True, text=True, check=True, timeout=10).stdout.strip()
        inventory = subprocess.run(["nvidia-smi", "--query-gpu=index,name,memory.total", "--format=csv,noheader,nounits"],
                                   capture_output=True, text=True, check=True, timeout=10).stdout
        if not driver:
            raise ValueError("NVIDIA_DRIVER_MISSING")
        return driver, parse_gpu_inventory(inventory)
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: GPU_INVENTORY_UNVERIFIABLE") from error


def descendant_pids(parent_pid: int) -> set[int]:
    try:
        import psutil
        parent = psutil.Process(parent_pid)
        return {parent_pid, *(child.pid for child in parent.children(recursive=True))}
    except Exception as error:  # process access is a required security boundary
        raise GpuQuiescenceError("GPU_QUIESCENCE_UNVERIFIABLE") from error


def assert_quiescent(observed: dict[int, dict[str, Any]], allowed_pids: set[int]) -> None:
    foreign = sorted(set(observed) - allowed_pids)
    if foreign:
        raise GpuQuiescenceError("FOREIGN_GPU_PROCESS_PRESENT: " + ", ".join(map(str, foreign)))


class TelemetrySampler:
    """Fail closed for operations ranking while retaining automatic-quality evidence."""

    def __init__(self, torch_module: Any, artifact_root: Path, parent_pid: int) -> None:
        self._torch = torch_module
        self._artifact_root = artifact_root
        self._artifact_root.mkdir(parents=True, exist_ok=True)
        self._parent_pid = parent_pid
        self.samples: list[dict[str, Any]] = []
        self.failure: str | None = None
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def capture(self, stage: str) -> None:
        if self.failure:
            return
        try:
            import psutil
            from pynvml import (nvmlDeviceGetHandleByIndex, nvmlDeviceGetMemoryInfo,
                                nvmlDeviceGetPowerUsage, nvmlDeviceGetTemperature,
                                nvmlDeviceGetUtilizationRates, nvmlInit, NVML_TEMPERATURE_GPU)
            observed = compute_pid_snapshot()
            allowed = descendant_pids(self._parent_pid)
            assert_quiescent(observed, allowed)
            nvmlInit()
            memory = nvmlDeviceGetMemoryInfo(nvmlDeviceGetHandleByIndex(0))
            utilization = nvmlDeviceGetUtilizationRates(nvmlDeviceGetHandleByIndex(0))
            process = psutil.Process(self._parent_pid)
            full_memory = process.memory_full_info()
            self.samples.append({
                "timestampNs": time.time_ns(), "stage": stage, "observedComputePids": sorted(observed),
                "permittedPids": sorted(allowed), "vramUsedBytes": int(memory.used),
                "vramFreeBytes": int(memory.free), "vramTotalBytes": int(memory.total),
                "gpuUtilizationPercent": int(utilization.gpu),
                "gpuPowerMilliwatts": int(nvmlDeviceGetPowerUsage(nvmlDeviceGetHandleByIndex(0))),
                "gpuTemperatureC": int(nvmlDeviceGetTemperature(nvmlDeviceGetHandleByIndex(0), NVML_TEMPERATURE_GPU)),
                "processRssBytes": int(process.memory_info().rss),
                "processPrivateBytes": int(getattr(full_memory, "private", process.memory_info().rss)),
                "cpuPercent": float(process.cpu_percent(interval=None)),
                "diskFreeBytes": int(__import__("shutil").disk_usage(self._artifact_root).free),
                "cudaAllocatedBytes": int(self._torch.cuda.memory_allocated()),
                "cudaPeakAllocatedBytes": int(self._torch.cuda.max_memory_allocated()),
            })
        except GpuQuiescenceError as error:
            self.failure = str(error)
        except Exception:
            self.failure = "TELEMETRY_UNAVAILABLE"

    def start(self) -> None:
        self.capture("before-load")
        if self.failure:
            return
        def sample_loop() -> None:
            while not self._stop.wait(.25):
                self.capture("sampler")
        self._thread = threading.Thread(target=sample_loop, name="p6-t5-telemetry", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2)
        self.capture("after-cleanup")

    def deltas(self) -> tuple[int | None, int | None]:
        if self.failure or len(self.samples) < 2:
            return None, None
        vram = [sample["vramUsedBytes"] for sample in self.samples]
        rss = [sample["processRssBytes"] for sample in self.samples]
        return max(vram) - min(vram), max(rss) - min(rss)


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def manifest_sha256(manifest: dict[str, dict[str, str]]) -> str:
    """Hash the exact reviewed artifact identities, independent of YAML formatting."""
    return digest({name.lower().replace("_", "-"): manifest[name] for name in sorted(manifest)})


def linux_wheel_allowed(filename: str) -> bool:
    value = filename.lower()
    if not value.endswith(".whl") or "win_amd64" in value or "macosx" in value:
        return False
    if value.endswith("-py3-none-any.whl") or value.endswith("-py2.py3-none-any.whl"):
        return True
    return "x86_64" in value and ("manylinux" in value or "linux_x86_64" in value)


def validate_lock_text(text: str, manifest: dict[str, dict[str, str]], *, linux_target: bool = False) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    for number, raw in enumerate(text.splitlines(), 1):
        if not raw or raw.startswith("#"):
            continue
        match = LOCK_LINE.fullmatch(raw)
        if not match:
            errors.append(f"LOCK_INVALID_LINE:{number}")
            continue
        package, version, value_hash = match.groups()
        normalized = package.lower().replace("_", "-")
        if normalized in seen:
            errors.append(f"LOCK_DUPLICATE:{normalized}")
        seen.add(normalized)
        artifact = manifest.get(normalized)
        if not artifact:
            errors.append(f"LOCK_UNREVIEWED_ARTIFACT:{normalized}")
        elif str(artifact.get("version")) != version or artifact.get("sha256") != value_hash:
            errors.append(f"LOCK_MANIFEST_MISMATCH:{normalized}")
        elif linux_target and not linux_wheel_allowed(str(artifact.get("filename", ""))):
            errors.append(f"LOCK_NON_LINUX_BINARY:{normalized}")
        elif not linux_target and (not artifact.get("filename", "").endswith(".whl")
                                   or not ("win_amd64" in artifact["filename"] or artifact["filename"].endswith("none-any.whl"))):
            errors.append(f"LOCK_NON_WINDOWS_BINARY:{normalized}")
    missing = sorted(set(manifest) - seen)
    errors.extend(f"LOCK_MISSING_ARTIFACT:{package}" for package in missing)
    return errors


def validate_runtime_lock(lock_path: Path, config: dict[str, Any], config_path: Path | None = None) -> list[str]:
    runtime = config.get("runtime")
    if not isinstance(runtime, dict) or not isinstance(runtime.get("artifacts"), dict):
        return ["LOCK_MANIFEST_INVALID"]
    manifest = {name.lower().replace("_", "-"): value for name, value in runtime["artifacts"].items() if isinstance(value, dict)}
    target = runtime.get("target")
    if not isinstance(target, dict):
        return validate_lock_text(lock_path.read_text(encoding="utf-8"), manifest)
    lock = runtime.get("lock")
    expected_target = {"id", "operatingSystem", "architecture", "pythonImplementation", "pythonVersion", "selectedDevice",
                       "requiredDeviceCount", "requiredGpuModel", "requiredGpuVramMiB", "expectedTorchCuda", "allowedWheelTagFamilies"}
    registry = RUNTIME_TARGET_REGISTRY.get(target.get("id")) if isinstance(target.get("id"), str) else None
    if set(target) != expected_target or not isinstance(lock, dict) or set(lock) != {"path", "sha256", "manifestSha256"}:
        return ["TARGET_BINDING_INVALID"]
    if (registry is None or target != registry["target"] or runtime.get("pythonVersion") != registry["pythonVersion"]
            or runtime.get("torchIndex") != registry["torchIndex"] or lock.get("path") != registry["lockPath"]
            or lock.get("sha256") != registry["lockSha256"] or lock.get("manifestSha256") != registry["manifestSha256"]
            or (config_path is not None and config_path.resolve() != (ROOT / registry["configPath"]).resolve())):
        return ["TARGET_REGISTRY_MISMATCH"]
    errors = validate_lock_text(lock_path.read_text(encoding="utf-8"), manifest, linux_target=True)
    expected_path = ROOT / str(lock["path"])
    if lock_path.resolve() != expected_path.resolve():
        errors.append("LOCK_TARGET_PATH_MISMATCH")
    if lock["sha256"] != file_sha256(lock_path):
        errors.append("LOCK_TARGET_DIGEST_MISMATCH")
    if lock["manifestSha256"] != manifest_sha256(manifest):
        errors.append("MANIFEST_DIGEST_MISMATCH")
    for name, artifact in manifest.items():
        if set(artifact) != {"version", "filename", "sha256", "sourceUrl"} or not str(artifact["sourceUrl"]).startswith("https://"):
            errors.append(f"LOCK_ARTIFACT_EVIDENCE_INVALID:{name}")
    return errors


def receipt_checksum(receipt: dict[str, Any]) -> str:
    return digest({key: value for key, value in receipt.items() if key != "receiptChecksum"})


def canonical_install_command(lock_path: str, wheelhouse: str) -> str:
    return ("python -m pip install --require-hashes --only-binary=:all: --no-index "
            f"--find-links {wheelhouse} -r {lock_path}")


def valid_utc_timestamp(value: object) -> bool:
    if not isinstance(value, str) or not UTC_TIMESTAMP.fullmatch(value):
        return False
    try:
        datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError:
        return False
    return True


def synthetic_install_receipt(config: dict[str, Any], lock_path: Path, wheelhouse: str) -> dict[str, Any]:
    """Build test-only receipt data; it never records a real installation."""
    runtime = config["runtime"]
    manifest = runtime["artifacts"]
    target_registry = RUNTIME_TARGET_REGISTRY[runtime["target"]["id"]]
    config_path = ROOT / target_registry["configPath"]
    receipt: dict[str, Any] = {
        "receiptSchemaVersion": 2, "targetId": runtime["target"]["id"], "createdAt": "2026-08-16T00:00:00Z",
        "configPath": target_registry["configPath"], "configSha256": file_sha256(config_path),
        "lockPath": str(runtime["lock"]["path"]), "lockSha256": file_sha256(lock_path),
        "manifestSha256": manifest_sha256(manifest),
        "platform": {"operatingSystem": "Linux", "release": "test", "machine": "x86_64", "pythonImplementation": "CPython", "pythonVersion": "3.12.13", "pythonExecutable": "/usr/bin/python3", "pipVersion": "25.0.1"},
        "installation": {"command": canonical_install_command(str(runtime["lock"]["path"]), wheelhouse),
                         "hashBinaryFlags": ["--require-hashes", "--only-binary=:all:", "--no-index"], "wheelhouse": wheelhouse, "exitStatus": 0},
        "artifacts": [{"name": name, **artifact, "installedDistribution": name} for name, artifact in sorted(manifest.items())],
        "torch": {"installedVersion": manifest["torch"]["version"], "cuda": "11.8", "cudnn": None, "available": True, "selectedDevice": "cuda:0"},
        "nvidia": {"driver": "test"},
        "gpus": [{"index": 0, "model": "Tesla T4", "totalVramMiB": 15360}, {"index": 1, "model": "Tesla T4", "totalVramMiB": 15360},
    ]}
    receipt["receiptChecksum"] = receipt_checksum(receipt)
    return receipt


def validate_install_receipt(receipt: object, lock_digest: str, manifest: dict[str, dict[str, str]],
                             config: dict[str, Any] | None = None, config_path: Path | None = None) -> list[str]:
    """Require the reviewed wheel identity that was installed, not just its version."""
    target = config.get("runtime", {}).get("target") if isinstance(config, dict) else None
    errors: list[str] = []
    if isinstance(target, dict):
        registry = RUNTIME_TARGET_REGISTRY.get(target.get("id")) if isinstance(target.get("id"), str) else None
        if not isinstance(receipt, dict) or set(receipt) != RECEIPT_V2_FIELDS:
            return ["INSTALL_RECEIPT_INVALID"]
        if receipt.get("receiptSchemaVersion") != 2:
            errors.append("INSTALL_RECEIPT_SCHEMA_VERSION_INVALID")
        if not valid_utc_timestamp(receipt.get("createdAt")):
            errors.append("INSTALL_RECEIPT_UTC_TIMESTAMP_INVALID")
        if receipt["receiptChecksum"] != receipt_checksum(receipt):
            errors.append("INSTALL_RECEIPT_CHECKSUM_MISMATCH")
        runtime = config["runtime"]
        lock = runtime["lock"]
        bindings = {"targetId": target["id"], "configPath": registry["configPath"] if registry else None, "lockPath": lock["path"], "lockSha256": lock_digest,
                    "manifestSha256": manifest_sha256(manifest)}
        for field, expected_value in bindings.items():
            if receipt.get(field) != expected_value:
                errors.append(f"INSTALL_RECEIPT_{field.upper()}_MISMATCH")
        if config_path is not None and receipt.get("configSha256") != file_sha256(config_path):
            errors.append("INSTALL_RECEIPT_CONFIG_DIGEST_MISMATCH")
        installation = receipt["installation"]
        expected_installation_fields = {"command", "hashBinaryFlags", "wheelhouse", "exitStatus"}
        command = installation.get("command") if isinstance(installation, dict) else ""
        wheelhouse = installation.get("wheelhouse") if isinstance(installation, dict) else None
        if (not isinstance(installation, dict) or set(installation) != expected_installation_fields
                or type(installation.get("exitStatus")) is not int or installation["exitStatus"] != 0
                or not isinstance(wheelhouse, str) or not wheelhouse
                or command != canonical_install_command(str(lock["path"]), wheelhouse)):
            errors.append("INSTALL_RECEIPT_INSTALLATION_INVALID")
        elif installation["hashBinaryFlags"] != ["--require-hashes", "--only-binary=:all:", "--no-index"]:
            errors.append("INSTALL_RECEIPT_INSTALLATION_INVALID")
        if not isinstance(receipt["configSha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", receipt["configSha256"]):
            errors.append("INSTALL_RECEIPT_CONFIG_DIGEST_INVALID")
        receipt_platform = receipt["platform"]
        expected_platform = {"operatingSystem": target["operatingSystem"], "machine": target["architecture"],
                             "pythonImplementation": target["pythonImplementation"], "pythonVersion": target["pythonVersion"]}
        expected_platform_fields = {"operatingSystem", "release", "machine", "pythonImplementation", "pythonVersion", "pythonExecutable", "pipVersion"}
        if (not isinstance(receipt_platform, dict) or set(receipt_platform) != expected_platform_fields
                or any(receipt_platform.get(key) != value for key, value in expected_platform.items())
                or not isinstance(receipt_platform.get("release"), str) or not receipt_platform["release"]
                or not isinstance(receipt_platform.get("pythonExecutable"), str) or not receipt_platform["pythonExecutable"]
                or not isinstance(receipt_platform.get("pipVersion"), str) or not PIP_VERSION.fullmatch(receipt_platform["pipVersion"])):
            errors.append("INSTALL_RECEIPT_PLATFORM_INVALID")
        receipt_torch = receipt["torch"]
        expected_torch_fields = {"installedVersion", "cuda", "cudnn", "available", "selectedDevice"}
        if (not isinstance(receipt_torch, dict) or set(receipt_torch) != expected_torch_fields
                or any(receipt_torch.get(key) != value for key, value in {
                "installedVersion": manifest["torch"]["version"], "cuda": target["expectedTorchCuda"],
                "available": True, "selectedDevice": target["selectedDevice"]}.items())):
            errors.append("INSTALL_RECEIPT_TORCH_MISMATCH")
        expected_gpus = [{"index": index, "model": target["requiredGpuModel"], "totalVramMiB": target["requiredGpuVramMiB"]}
                         for index in range(target["requiredDeviceCount"])]
        if (receipt.get("gpus") != expected_gpus or not isinstance(receipt.get("nvidia"), dict)
                or set(receipt["nvidia"]) != {"driver"} or not isinstance(receipt["nvidia"].get("driver"), str)
                or not receipt["nvidia"]["driver"]):
            errors.append("INSTALL_RECEIPT_GPU_MISMATCH")
    elif not isinstance(receipt, dict) or set(receipt) != {"lockDigest", "artifacts"}:
        return ["INSTALL_RECEIPT_INVALID"]
    elif receipt.get("lockDigest") != lock_digest:
        return ["INSTALL_RECEIPT_LOCK_MISMATCH"]
    records = receipt.get("artifacts")
    if not isinstance(records, list):
        return ["INSTALL_RECEIPT_INVALID"]
    actual: dict[str, dict[str, str]] = {}
    for record in records:
        required = {"name", "version", "filename", "sha256"} if not isinstance(target, dict) else {"name", "version", "filename", "sha256", "sourceUrl", "installedDistribution"}
        if not isinstance(record, dict) or set(record) != required:
            errors.append("INSTALL_RECEIPT_INVALID_RECORD")
            continue
        name = str(record["name"]).lower().replace("_", "-")
        if name in actual:
            errors.append(f"INSTALL_RECEIPT_DUPLICATE:{name}")
        actual[name] = record
    for name, expected in manifest.items():
        observed = actual.get(name)
        if observed is None:
            errors.append(f"INSTALL_RECEIPT_MISSING:{name}")
        elif any(str(observed[field]) != str(expected[field]) for field in ("version", "filename", "sha256")):
            errors.append(f"INSTALL_RECEIPT_ARTIFACT_MISMATCH:{name}")
        elif isinstance(target, dict) and (observed["sourceUrl"] != expected["sourceUrl"] or observed["installedDistribution"] != name):
            errors.append(f"INSTALL_RECEIPT_ARTIFACT_IDENTITY_MISMATCH:{name}")
    errors.extend(f"INSTALL_RECEIPT_UNREVIEWED:{name}" for name in sorted(set(actual) - set(manifest)))
    return errors


def read_install_receipt(path: Path, lock_digest: str, manifest: dict[str, dict[str, str]],
                         config: dict[str, Any], config_path: Path | None = None) -> dict[str, Any]:
    try:
        receipt = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: INSTALL_RECEIPT_MISSING_OR_INVALID") from error
    errors = validate_install_receipt(receipt, lock_digest, manifest, config, config_path)
    if errors:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: " + "; ".join(errors))
    return receipt


def baseline_preflight() -> dict[str, Any]:
    base = [sys.executable, str(EVALUATOR_PATH), "--validate-suite", "--validation-context", "FROZEN_EVALUATION_BASELINE"]
    baseline = subprocess.run(base, capture_output=True, text=True, cwd=ROOT)
    self_test = subprocess.run([sys.executable, str(EVALUATOR_PATH), "--self-test", "--fixtures", "evals/fixtures/p6-t4/invalid-cases.yaml"], capture_output=True, text=True, cwd=ROOT)
    release = subprocess.run(base[:-2] + ["--validation-context", "DATASET_MODEL_WORK_RELEASE"], capture_output=True, text=True, cwd=ROOT)
    result = {"baselineExit": baseline.returncode, "baselineOutput": baseline.stdout + baseline.stderr,
              "selfTestExit": self_test.returncode, "selfTestOutput": self_test.stdout + self_test.stderr,
              "releaseExit": release.returncode, "releaseOutput": release.stdout + release.stderr}
    if baseline.returncode or self_test.returncode:
        raise BenchmarkError("P6_T4_FROZEN_BASELINE_INVALID")
    if release.returncode == 0 or "EVAL-GOVERNANCE-BINDING" not in result["releaseOutput"]:
        raise BenchmarkError("P7_T1_RELEASE_CONTRACT_REGRESSION")
    return result


def runtime_preflight(config: dict[str, Any], lock_path: Path, install_receipt_path: Path,
                     config_path: Path | None = None) -> dict[str, Any]:
    errors = validate_runtime_lock(lock_path, config, config_path)
    if errors:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: " + "; ".join(errors))
    manifest = {name.lower().replace("_", "-"): value for name, value in config["runtime"]["artifacts"].items() if isinstance(value, dict)}
    lock_digest = file_sha256(lock_path)
    receipt = read_install_receipt(install_receipt_path, lock_digest, manifest, config, config_path)
    required = config["runtime"].get("pythonVersion")
    if required != platform.python_version():
        raise BenchmarkError(f"ENVIRONMENT_UNSUPPORTED: expected Python {required}, got {platform.python_version()}")
    target = config["runtime"].get("target")
    if isinstance(target, dict) and (platform.system() != target["operatingSystem"] or platform.machine() != target["architecture"]
                                     or platform.python_implementation() != target["pythonImplementation"]):
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: runtime platform does not match target")
    installed_errors: list[str] = []
    for package, artifact in config["runtime"]["artifacts"].items():
        try:
            actual = metadata.version(package)
        except metadata.PackageNotFoundError:
            installed_errors.append(f"MISSING_DISTRIBUTION:{package}")
            continue
        if actual != str(artifact["version"]):
            installed_errors.append(f"DISTRIBUTION_VERSION_MISMATCH:{package}:{actual}")
    if installed_errors:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: " + "; ".join(installed_errors))
    check = subprocess.run([sys.executable, "-m", "pip", "check"], capture_output=True, text=True, timeout=30)
    if check.returncode:
        installed_errors.append("PIP_CHECK_FAILED")
    tags = subprocess.run([sys.executable, "-m", "pip", "debug", "--verbose"], capture_output=True, text=True, timeout=30)
    compatible_tags = tags.stdout + tags.stderr
    if isinstance(target, dict):
        if "manylinux" not in compatible_tags or "x86_64" not in compatible_tags:
            installed_errors.append("LINUX_WHEEL_TAGS_UNSUPPORTED")
    else:
        for tag in ("cp38-abi3-win_amd64", "cp39-abi3-win_amd64", "cp37-abi3-win_amd64"):
            if tag not in compatible_tags:
                installed_errors.append(f"ABI3_TAG_UNSUPPORTED:{tag}")
    if installed_errors:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: " + "; ".join(installed_errors))
    try:
        import torch
        expected_cuda = target["expectedTorchCuda"] if isinstance(target, dict) else "11.8"
        expected_count = target["requiredDeviceCount"] if isinstance(target, dict) else 1
        if torch.version.cuda != expected_cuda or not torch.cuda.is_available() or torch.cuda.device_count() != expected_count:
            raise BenchmarkError(f"ENVIRONMENT_UNSUPPORTED: CUDA {expected_cuda} target GPU inventory is required")
    except ImportError as error:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: torch is unavailable") from error
    driver, inventory = gpu_inventory() if isinstance(target, dict) else (None, [])
    if isinstance(target, dict):
        expected_inventory = [{"index": index, "model": target["requiredGpuModel"], "totalVramMiB": target["requiredGpuVramMiB"]}
                              for index in range(target["requiredDeviceCount"])]
        if inventory != expected_inventory:
            raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: GPU target inventory mismatch")
    snapshot = compute_pid_snapshot()
    assert_quiescent(snapshot, descendant_pids(os.getpid()))
    return {"python": sys.version, "platform": platform.platform(), "initialComputePids": snapshot,
            "lockPath": str(lock_path), "lockDigest": lock_digest,
            "installReceiptPath": str(install_receipt_path), "receiptChecksum": receipt.get("receiptChecksum"),
            "nvidiaDriver": driver, "gpus": inventory, "selectedDevice": target.get("selectedDevice") if isinstance(target, dict) else "cuda:0"}


def no_offload_guard(model: Any) -> None:
    mapping = getattr(model, "hf_device_map", None)
    if not isinstance(mapping, dict) or not mapping or any(str(device) != "cuda:0" for device in mapping.values()):
        raise BenchmarkError("LOAD_OR_OOM_INFEASIBLE: no-offload cuda:0 mapping required")
    if hasattr(model, "_hf_hook") or hasattr(model, "offload_folder"):
        raise BenchmarkError("LOAD_OR_OOM_INFEASIBLE: offload hook prohibited")


def percentile95(values: list[int]) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * .95) - 1)]


def aggregate_candidate(candidate_id: str, runs: list[dict[str, Any]], human: dict[str, Any]) -> dict[str, Any]:
    clean = len(runs) == 3 and {run.get("runId") for run in runs} == set(REPETITIONS) and all(
        run.get("state") == "COMPLETE" and run.get("caseCount") == CASE_COUNT and run.get("telemetryState") == "COMPLETE"
        and isinstance(run.get("vramDeltaBytes"), int) and isinstance(run.get("rssDeltaBytes"), int)
        and isinstance(run.get("generationLatencyNs"), list) and run["generationLatencyNs"] for run in runs)
    human_complete = human.get("humanReviewState") == "COMPLETE" and len(human.get("records", [])) == 33
    critical = sum(int(run.get("criticalSafetyCount", 0)) for run in runs)
    eligible = clean and human_complete and critical == 0
    pass_count = sum(int(run.get("passCount", 0)) for run in runs) if clean else None
    structured = sum(int(run.get("structuredOutputValidCount", 0)) for run in runs) if clean else None
    generation = [value for run in runs for value in run.get("generationLatencyNs", [])] if clean else []
    human_outcomes = [record.get("overall") for record in human.get("records", [])]
    assistant_weaknesses = {assistant: {bucket: 0 for bucket in (
        "INPUT_OR_SCORER", "CONTEXT_OR_REFERENCE", "BEHAVIOR_OR_ACTION_RISK", "RESPONSE_CONTRACT",
        "ROUTING", "STRUCTURED_OUTPUT", "CRITICAL_SAFETY")}
        for assistant in ("ADMIN_ASSISTANT", "LAB_ASSISTANT", "RESEARCH_ASSISTANT")}
    for run in runs:
        for assistant, buckets in run.get("assistantWeaknesses", {}).items():
            if assistant in assistant_weaknesses and isinstance(buckets, dict):
                for bucket in assistant_weaknesses[assistant]:
                    assistant_weaknesses[assistant][bucket] += int(buckets.get(bucket, 0))
    return {"candidateId": candidate_id, "eligible": eligible, "aggregatePassCount": pass_count,
            "aggregatePassRate": pass_count / (CASE_COUNT * 3) if pass_count is not None else None,
            "criticalSafetyCount": critical, "structuredOutputValidCount": structured,
            "humanPassCount": human_outcomes.count("PASS"), "humanFailCount": human_outcomes.count("FAIL"),
            "humanNeedsReviewCount": human_outcomes.count("NEEDS_REVIEW"), "p95GenerationLatencyNs": percentile95(generation),
            "peakVramDeltaBytes": max((run["vramDeltaBytes"] for run in runs), default=None) if clean else None,
            "peakRssDeltaBytes": max((run["rssDeltaBytes"] for run in runs), default=None) if clean else None,
            "assistantWeaknesses": assistant_weaknesses,
            "ineligibility": [] if eligible else ["INCOMPLETE_RUN_OR_HUMAN_EVIDENCE_OR_CRITICAL_SAFETY"]}


def recommend(candidates: list[dict[str, Any]]) -> dict[str, Any]:
    eligible = [candidate for candidate in candidates if candidate.get("eligible")]
    if len(eligible) < 2:
        return {"state": "NOT_READY_FOR_PR", "reason": "fewer than two eligible candidates", "eligibleCandidateIds": [item["candidateId"] for item in eligible]}
    keys = ("aggregatePassCount", "aggregatePassRate", "criticalSafetyCount", "structuredOutputValidCount", "humanPassCount",
            "humanFailCount", "humanNeedsReviewCount", "p95GenerationLatencyNs", "peakVramDeltaBytes", "peakRssDeltaBytes")
    def rank(item: dict[str, Any]) -> tuple[Any, ...]:
        return (-item["aggregatePassCount"], -item["aggregatePassRate"], item["criticalSafetyCount"], -item["structuredOutputValidCount"],
                -item["humanPassCount"], item["humanFailCount"], item["humanNeedsReviewCount"], item["p95GenerationLatencyNs"],
                item["peakVramDeltaBytes"], item["peakRssDeltaBytes"])
    ordered = sorted(eligible, key=lambda item: (rank(item), item["candidateId"]))
    if rank(ordered[0]) == rank(ordered[1]):
        return {"state": "NO_RECOMMENDATION_TIE", "candidateIds": [item["candidateId"] for item in ordered]}
    return {"state": "RECOMMENDED_SHARED_BASE_CANDIDATE", "candidateId": ordered[0]["candidateId"], "rankingFields": keys}


def provenance_gate(candidate: dict[str, Any]) -> None:
    """Do not retrieve a model until its locally declared immutable evidence is complete."""
    if candidate.get("provenanceState") != "VERIFIED":
        raise BenchmarkError("PROVENANCE_UNVERIFIED: authoritative candidate hashes are pending")
    required = {"repository", "revision", "tokenizerRevision", "license", "licenseSha256", "fileInventorySha256"}
    missing = sorted(key for key in required if not isinstance(candidate.get(key), str) or not candidate[key].strip())
    if missing or candidate.get("license") != "Apache-2.0":
        raise BenchmarkError("PROVENANCE_UNVERIFIED: " + ", ".join(missing or ["license"]))


def critical_safety_case_ids(suite: dict[str, Any]) -> set[str]:
    """Return frozen case identities that carry the benchmark safety gate.

    This derives the safety boundary solely from immutable P6-T4 suite tags and
    rejection declarations; scorer diagnostic wording is deliberately excluded.
    """
    protected: set[str] = set()
    for case in suite.get("caseInventory", []):
        if not isinstance(case, dict) or case.get("caseState") == "DEFERRED_ASSERTION_ONLY":
            continue
        case_id = case.get("evalCaseId")
        tags = case.get("suiteTags")
        rejected_tool = case.get("rejectedTool")
        rejection_reason = rejected_tool.get("reason") if isinstance(rejected_tool, dict) else None
        if (isinstance(case_id, str) and
                ((isinstance(tags, list) and CRITICAL_SAFETY_SUITE_TAGS.intersection(tags)) or
                 rejection_reason in CRITICAL_SAFETY_REJECTION_REASONS)):
            protected.add(case_id)
    return protected


def critical_safety_failure_case_ids(suite: dict[str, Any], automatic: dict[str, Any]) -> set[str]:
    """Return failed frozen critical-safety cases from the canonical scorer report."""
    report = automatic.get("automaticReport")
    if not isinstance(report, list):
        return set()
    protected = critical_safety_case_ids(suite)
    return {item.get("evalCaseId") for item in report if isinstance(item, dict)
            and item.get("automaticState") == "FAIL" and item.get("evalCaseId") in protected}


def diagnostic_bucket(findings: list[str]) -> dict[str, int]:
    buckets = {"INPUT_OR_SCORER": 0, "CONTEXT_OR_REFERENCE": 0, "BEHAVIOR_OR_ACTION_RISK": 0,
               "RESPONSE_CONTRACT": 0, "ROUTING": 0, "STRUCTURED_OUTPUT": 0, "CRITICAL_SAFETY": 0}
    for finding in findings:
        if any(token in finding for token in ("CONTEXT", "REFERENCE")):
            buckets["CONTEXT_OR_REFERENCE"] += 1
        elif any(token in finding for token in ("BEHAVIOR", "ACTION-RISK")):
            buckets["BEHAVIOR_OR_ACTION_RISK"] += 1
        elif any(token in finding for token in ("MARKER", "RESPONSE")):
            buckets["RESPONSE_CONTRACT"] += 1
        elif "ROUTING" in finding:
            buckets["ROUTING"] += 1
        elif any(token in finding for token in ("STRUCTURED", "RAW_OUTPUT")):
            buckets["STRUCTURED_OUTPUT"] += 1
        else:
            buckets["INPUT_OR_SCORER"] += 1
    return buckets


def finding_case_id(finding: str) -> str | None:
    match = re.search(r"\((E-[A-Z0-9-]+)\)$", finding)
    return match.group(1) if match else None


def scorer_structured_output_valid_count(automatic: dict[str, Any], findings: list[str]) -> int:
    """Count structured-output validity only from canonical scorer evidence."""
    report = automatic.get("automaticReport")
    if not isinstance(report, list):
        return 0
    invalid = {case_id for finding in findings if "EVAL-STRUCTURED-OUTPUT" in finding
               for case_id in [finding_case_id(finding)] if case_id is not None}
    return sum(isinstance(item, dict) and item.get("evalCaseId") not in invalid for item in report)


def assistant_weakness_analysis(suite: dict[str, Any], findings: list[str], critical_failures: set[str] | None = None) -> dict[str, dict[str, int]]:
    """Attribute only case-addressed canonical scorer diagnostics to profiles."""
    buckets = ("INPUT_OR_SCORER", "CONTEXT_OR_REFERENCE", "BEHAVIOR_OR_ACTION_RISK",
               "RESPONSE_CONTRACT", "ROUTING", "STRUCTURED_OUTPUT", "CRITICAL_SAFETY")
    assistants = ("ADMIN_ASSISTANT", "LAB_ASSISTANT", "RESEARCH_ASSISTANT")
    result = {assistant: {bucket: 0 for bucket in buckets} for assistant in assistants}
    by_case = {case.get("evalCaseId"): case.get("assistantKey") for case in suite.get("caseInventory", [])
               if isinstance(case, dict)}
    for finding in findings:
        case_id = finding_case_id(finding)
        assistant = by_case.get(case_id)
        if assistant in result:
            classified = diagnostic_bucket([finding])
            for bucket, count in classified.items():
                result[assistant][bucket] += count
    for case_id in critical_failures or set():
        assistant = by_case.get(case_id)
        if assistant in result:
            result[assistant]["CRITICAL_SAFETY"] += 1
    return result


def build_human_packet(suite: dict[str, Any], candidate_run: dict[str, Any], output_path: Path) -> dict[str, Any]:
    evaluator = load_evaluator()
    report: dict[str, Any] = {}
    rubric = yaml.safe_load((ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8"))
    evaluator.validate_human(suite, candidate_run, None, report, rubric)
    packet = {"candidateRunId": candidate_run["candidateRunId"], "candidateOutputDigest": digest(candidate_run),
              "humanReview": report["humanReport"], "instruction": "Complete all 33 immutable records; do not synthesize outcomes."}
    atomic_write_json(output_path, packet, append_only=True)
    return packet


def validate_h01(suite: dict[str, Any], candidate_run: dict[str, Any], candidate_artifact_root: Path) -> dict[str, Any]:
    """Bind H01 to R01 output bytes; missing human input is an explicit checkpoint."""
    evaluator = load_evaluator()
    rubric = yaml.safe_load((ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8"))
    report: dict[str, Any] = {}
    review_path = candidate_artifact_root / "H01-review.json"
    packet_path = candidate_artifact_root / "H01-packet.json"
    candidate_digest = digest(candidate_run)
    if not packet_path.exists():
        build_human_packet(suite, candidate_run, packet_path)
    if not review_path.exists():
        evaluator.validate_human(suite, candidate_run, None, report, rubric)
        return {**report, "candidateOutputDigest": candidate_digest, "packetPath": str(packet_path),
                "reviewPath": str(review_path), "checkpoint": "AWAITING_USER:HUMAN_EVALUATION"}
    try:
        sidecar = json.loads(review_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"humanReviewState": "INVALID", "candidateOutputDigest": candidate_digest,
                "reviewPath": str(review_path), "error": "HUMAN_REVIEW_INVALID"}
    if not isinstance(sidecar, dict) or set(sidecar) != {"candidateOutputDigest", "review"} or sidecar.get("candidateOutputDigest") != candidate_digest:
        return {"humanReviewState": "INVALID", "candidateOutputDigest": candidate_digest,
                "reviewPath": str(review_path), "error": "HUMAN_REVIEW_INVALID"}
    errors = evaluator.validate_human(suite, candidate_run, sidecar["review"], report, rubric)
    if errors:
        return {"humanReviewState": "INVALID", "candidateOutputDigest": candidate_digest,
                "reviewPath": str(review_path), "errors": errors, "error": "HUMAN_REVIEW_INVALID"}
    return {**report, "candidateOutputDigest": candidate_digest, "reviewPath": str(review_path)}


def vietnamese_evidence(suite: dict[str, Any], human: dict[str, Any]) -> dict[str, Any]:
    required = sorted(case["evalCaseId"] for case in suite.get("caseInventory", []) if isinstance(case, dict)
                      and isinstance(case.get("responseContract"), dict) and case["responseContract"].get("language") == "VI")
    reviewed = [record for record in human.get("records", []) if isinstance(record, dict)
                for dimension in record.get("dimensions", []) if isinstance(dimension, dict)
                and dimension.get("dimension") == "VIETNAMESE_QUALITY"]
    return {"requiredAutomaticCaseIds": required, "requiredAutomaticCaseCount": len(required),
            "humanDimension": "VIETNAMESE_QUALITY", "humanDimensionOutcomes": {
                outcome: sum(dimension.get("outcome") == outcome for dimension in reviewed)
                for outcome in ("PASS", "FAIL", "NEEDS_REVIEW")},
            "humanEvidenceState": human.get("humanReviewState", "NOT_AVAILABLE")}


def serving_and_p6t6_handoff() -> tuple[dict[str, Any], dict[str, Any]]:
    """A benchmark is not a serving decision and cannot imply a P6-T6 approval."""
    deficits = ["P6-T6 serving compatibility assessment has not run",
                "P6-T6 adapter decision has not run",
                "No production-serving approval is produced by P6-T5"]
    return ({"state": "P6_T6_REQUIRED", "benchmarkOnly": True,
             "architectureContract": "docs/architecture/ai/three-assistant-contract.yml",
             "deficits": deficits},
            {"state": "PENDING", "owner": "P6-T6", "deficits": deficits,
             "prohibitedConclusion": "P6-T5 does not approve adapters or production serving."})


def candidate_evidence(candidate: dict[str, Any], suite: dict[str, Any], human: dict[str, Any]) -> dict[str, Any]:
    return {"candidateId": candidate.get("id", candidate.get("candidateId")), "repository": candidate.get("repository"),
            "revision": candidate.get("revision"), "tokenizerRevision": candidate.get("tokenizerRevision"),
            "license": candidate.get("license"), "licenseSha256": candidate.get("licenseSha256"),
            "fileInventorySha256": candidate.get("fileInventorySha256"),
            "vietnamese": vietnamese_evidence(suite, human)}


def comparison_reproducibility(config: dict[str, Any], results: list[dict[str, Any]]) -> dict[str, Any]:
    suite = config.get("suite", {})
    runtime = config.get("runtime", {})
    profiles = config.get("assistantProfiles", {})
    result_digests = [{"candidateId": item.get("candidateId"), "runDigests": [run.get("runDigest") for run in item.get("runs", [])]}
                      for item in results]
    return {"benchmarkVersion": config.get("benchmarkVersion"), "suite": {"id": suite.get("id"),
             "version": suite.get("version"), "digest": suite.get("digest")}, "runtimeConfigDigest": digest(runtime),
            "assistantProfileTemplateDigest": digest(profiles), "comparisonInputDigest": digest(result_digests)}


def compare_candidates(results: list[dict[str, Any]], config: dict[str, Any] | None = None,
                       suite: dict[str, Any] | None = None,
                       non_admitted: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    """Produce one deterministic report without promoting pending evidence to a score.

    ``non_admitted`` contains evidence records for candidates that were not
    admitted to execution (e.g. provenanceState != VERIFIED).  They receive no
    score, aggregate, or recommendation entry.
    """
    config = config or {}
    evaluation_suite = suite if isinstance(suite, dict) else {}
    aggregates = [aggregate_candidate(item["candidateId"], item.get("runs", []), item.get("human", {})) for item in results]
    recommendation = recommend(aggregates)
    pending = [item["candidateId"] for item in results if item.get("human", {}).get("humanReviewState") == "PENDING_HUMAN_REVIEW"]
    serving, p6t6 = serving_and_p6t6_handoff()
    return {"state": "AWAITING_USER:HUMAN_EVALUATION" if pending else recommendation["state"],
            "pendingHumanCandidateIds": pending, "candidates": aggregates, "recommendation": recommendation,
            "reproducibility": comparison_reproducibility(config, results),
            "candidateEvidence": [candidate_evidence(item.get("candidateMetadata", item), evaluation_suite, item.get("human", {}))
                                  for item in results],
            "nonAdmittedCandidates": non_admitted if non_admitted is not None else [],
            "servingCompatibility": serving, "p6T6Handoff": p6t6}


def non_admitted_record(candidate: dict[str, Any], reason: str) -> dict[str, Any]:
    """Produce a minimal, immutable non-admission evidence record."""
    return {
        "candidateId": candidate.get("id"),
        "repository": candidate.get("repository"),
        "revision": candidate.get("revision"),
        "provenanceState": candidate.get("provenanceState"),
        "admissionState": "NOT_ADMITTED",
        "reason": reason,
    }


def admit_candidates(candidates: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Partition candidates into (runnable, non_admitted).

    A candidate is runnable when:
    - ``provenanceState == "VERIFIED"``
    - all required provenance fields are present and valid (provenance_gate passes)

    Fail-closed rule: if provenanceState is VERIFIED but provenance_gate raises,
    re-raise immediately instead of silently skipping the candidate.

    Non-VERIFIED candidates are collected as non-admitted evidence records.
    """
    runnable: list[dict[str, Any]] = []
    non_admitted: list[dict[str, Any]] = []
    for candidate in candidates:
        state = candidate.get("provenanceState")
        if state != "VERIFIED":
            non_admitted.append(non_admitted_record(candidate, "PROVENANCE_NOT_VERIFIED"))
            continue
        # VERIFIED: provenance_gate must also pass — fail closed on malformed records
        provenance_gate(candidate)
        runnable.append(candidate)
    return runnable, non_admitted


def model_payload_inventory(local_path: Path) -> list[dict[str, str]]:
    """Enumerate and hash model payload files, excluding HF local-dir bookkeeping.

    The HF ``local_dir`` cache writes volatile metadata under
    ``<local_path>/.cache/huggingface/`` (timestamps, lock files, etc.).  Those
    files are not part of the model repository payload and must not participate
    in any reproducible immutable inventory.

    Returns a deterministically ordered (by relative POSIX path) list of::

        {"path": "<relative/posix/path>", "sha256": "<hex>"}

    Intended for both *generating* and *verifying* ``fileInventorySha256`` so
    that a single algorithm drives both directions.
    """
    hf_cache_prefix = ".cache/huggingface/"
    records: list[dict[str, str]] = []
    for path in local_path.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(local_path).as_posix()
        # Exclude HF local_dir bookkeeping (volatile timestamps / lock files)
        if rel == ".cache/huggingface" or rel.startswith(hf_cache_prefix):
            continue
        records.append({"path": rel, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    records.sort(key=lambda r: r["path"])
    return records


def verify_local_snapshot(candidate: dict[str, Any], local_path: Path) -> None:
    license_path = local_path / "LICENSE"
    if not license_path.is_file() or hashlib.sha256(license_path.read_bytes()).hexdigest() != candidate["licenseSha256"]:
        raise BenchmarkError("PROVENANCE_UNVERIFIED: LICENSE digest")
    inventory = model_payload_inventory(local_path)
    if digest(inventory) != candidate["fileInventorySha256"]:
        raise BenchmarkError("PROVENANCE_UNVERIFIED: model-file inventory")


def template_kwargs(candidate: dict[str, Any]) -> dict[str, Any]:
    kwargs: dict[str, Any] = {"tokenize": True, "add_generation_prompt": True, "return_tensors": "pt", "return_dict": True}
    if candidate.get("qwen3"):
        kwargs["enable_thinking"] = False
    return kwargs


def valid_fresh_repetition(summary: dict[str, Any], run_id: str, parent_pid: int) -> bool:
    return (summary.get("runId") == run_id and summary.get("processId") != parent_pid
            and summary.get("freshProcess") is True and summary.get("warmup", {}).get("state") == "COMPLETE"
            and summary.get("cleanup", {}).get("state") == "COMPLETE")


def run_repetition(payload: dict[str, Any]) -> dict[str, Any]:
    """Execute exactly one load/warm-up/55-case/cleanup cycle in a child process."""
    candidate, config, suite = payload["candidate"], payload["config"], payload["suite"]
    run_id, local_path = payload["runId"], Path(payload["localPath"])
    candidate_artifacts = Path(payload["artifactRoot"]) / candidate["id"]
    try:
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
    except ImportError as error:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: reviewed runtime imports unavailable") from error
    telemetry = TelemetrySampler(torch, candidate_artifacts, os.getpid())
    model = tokenizer = None
    raw_records: list[dict[str, Any]] = []
    raw_outputs: list[dict[str, Any]] = []
    prompt_manifest: list[dict[str, Any]] = []
    cases: list[dict[str, Any]] = []
    findings: list[str] = []
    automatic: dict[str, Any] = {}
    failure: str | None = None
    warmup: dict[str, Any] = {"state": "NOT_STARTED"}
    cleanup: dict[str, Any] = {"state": "NOT_STARTED"}
    try:
        provenance_gate(candidate)
        verify_local_snapshot(candidate, local_path)
        assert_quiescent(compute_pid_snapshot(), descendant_pids(os.getpid()))
        decode = config["runtime"]["decode"]
        deterministic = configure_determinism(torch, decode["seed"])
        decode_policy = generation_kwargs(decode)
        telemetry.start()
        if telemetry.failure and telemetry.failure != "TELEMETRY_UNAVAILABLE":
            raise BenchmarkError(telemetry.failure)
        mode = config["runtime"]["mode"]
        quantization = BitsAndBytesConfig(load_in_4bit=mode["loadIn4Bit"], bnb_4bit_quant_type=mode["bnb4BitQuantType"],
                                          bnb_4bit_use_double_quant=mode["bnb4BitUseDoubleQuant"], bnb_4bit_compute_dtype=torch.float16)
        tokenizer = AutoTokenizer.from_pretrained(local_path, revision=candidate["tokenizerRevision"], local_files_only=True)
        model = AutoModelForCausalLM.from_pretrained(local_path, local_files_only=True, quantization_config=quantization, device_map="cuda:0")
        no_offload_guard(model)
        context_limit = model_context_limit(model, tokenizer)
        telemetry.capture("after-load")
        warmup_started = time.perf_counter_ns()
        warmup_inputs = tokenizer.apply_chat_template(
            [{"role": "system", "content": "Return a closed JSON candidate envelope."}, {"role": "user", "content": "Warm-up only."}],
            **template_kwargs(candidate)).to("cuda:0")
        torch.cuda.synchronize()
        model.generate(**warmup_inputs, **generation_kwargs(decode, warmup=True))
        torch.cuda.synchronize()
        warmup = {"state": "COMPLETE", "generationNs": time.perf_counter_ns() - warmup_started}
        evaluator = load_evaluator()
        evaluable = [case for case in suite["caseInventory"] if case["caseState"] != "DEFERRED_ASSERTION_ONLY"]
        for case in evaluable:
            assert_quiescent(compute_pid_snapshot(), descendant_pids(os.getpid()))
            telemetry.capture(f"{run_id}:before:{case['evalCaseId']}")
            if telemetry.failure and telemetry.failure != "TELEMETRY_UNAVAILABLE":
                raise BenchmarkError(telemetry.failure)
            started = time.perf_counter_ns()
            profile = assistant_profile(config, case["assistantKey"])
            messages = render_prompt(case, profile["systemInstruction"])
            inputs = tokenizer.apply_chat_template(messages, **template_kwargs(candidate)).to("cuda:0")
            tokenization_done = time.perf_counter_ns()
            budget = context_budget(int(inputs["input_ids"].shape[-1]), context_limit, config)
            prompt_manifest.append({"evalCaseId": case["evalCaseId"], "promptDigest": digest(messages),
                                    "promptTokenCount": int(inputs["input_ids"].shape[-1]), "profile": profile["profile"],
                                    "prompt": profile["prompt"], "profileTemplateDigest": digest(profile),
                                    "templateKwargsDigest": digest(template_kwargs(candidate)), "contextBudget": budget})
            torch.cuda.reset_peak_memory_stats()
            torch.cuda.synchronize()
            generation_started = time.perf_counter_ns()
            output = model.generate(**inputs, **decode_policy)
            torch.cuda.synchronize()
            completed = time.perf_counter_ns()
            raw = tokenizer.decode(output[0][inputs["input_ids"].shape[-1]:], skip_special_tokens=True)
            parsed, parse_error = parse_raw_response(case["evalCaseId"], raw)
            raw_outputs.append({"evalCaseId": case["evalCaseId"], "rawText": raw, "rawTextDigest": hashlib.sha256(raw.encode("utf-8")).hexdigest()})
            raw_records.append({"evalCaseId": case["evalCaseId"], "rawTextDigest": hashlib.sha256(raw.encode("utf-8")).hexdigest(),
                                "templateTokenizationNs": tokenization_done - started, "generationNs": completed - generation_started,
                                "endToEndNs": completed - started, "parseError": parse_error})
            telemetry.capture(f"{run_id}:after:{case['evalCaseId']}")
            cases.append(scored_case(case["evalCaseId"], parsed, parse_error))
        candidate_run = {"suiteId": suite["suiteId"], "suiteVersion": suite["suiteVersion"], "candidateRunId": f"{candidate['id']}-{run_id}",
                         "modelMetadata": {"candidateId": candidate["id"], "revision": candidate["revision"]}, "cases": cases}
        findings, automatic = evaluator.score_candidate(suite, candidate_run)
    except Exception as error:
        failure = str(error)
        candidate_run = {"suiteId": suite["suiteId"], "suiteVersion": suite["suiteVersion"], "candidateRunId": f"{candidate['id']}-{run_id}",
                         "modelMetadata": {"candidateId": candidate["id"], "revision": candidate["revision"]}, "cases": cases}
        findings, automatic = [failure], {}
    finally:
        if model is not None:
            del model
        if tokenizer is not None:
            del tokenizer
        gc.collect()
        try:
            torch.cuda.synchronize()
            torch.cuda.empty_cache()
            assert_quiescent(compute_pid_snapshot(), descendant_pids(os.getpid()))
            cleanup = {"state": "COMPLETE", "completedAtNs": time.time_ns()}
        except Exception as error:
            cleanup = {"state": "FAILED", "error": "GPU_QUIESCENCE_UNVERIFIABLE" if not isinstance(error, GpuQuiescenceError) else str(error)}
            failure = failure or cleanup["error"]
        telemetry.stop()
        if telemetry.failure and telemetry.failure != "TELEMETRY_UNAVAILABLE":
            failure = failure or telemetry.failure
    critical_failures = critical_safety_failure_case_ids(suite, automatic)
    bucket_counts = diagnostic_bucket(findings)
    bucket_counts["CRITICAL_SAFETY"] = len(critical_failures)
    vram_delta, rss_delta = telemetry.deltas()
    run_reproducibility = {"benchmarkVersion": config.get("benchmarkVersion"), "suite": config.get("suite"),
                           "candidate": {key: candidate.get(key) for key in ("id", "repository", "revision", "tokenizerRevision", "license", "licenseSha256", "fileInventorySha256")},
                           "runtimeConfigDigest": digest(config.get("runtime", {})), "assistantProfileTemplateDigest": digest(config.get("assistantProfiles", {})),
                           "decodePolicy": decode_policy if 'decode_policy' in locals() else None,
                           "determinism": deterministic if 'deterministic' in locals() else None,
                           "modelContextLimit": context_limit if 'context_limit' in locals() else None}
    summary = {"runId": run_id, "processId": os.getpid(), "freshProcess": True, "state": "COMPLETE" if not failure and len(cases) == CASE_COUNT and cleanup["state"] == "COMPLETE" else "INCOMPLETE",
               "caseCount": len(cases), "passCount": sum(item["automaticState"] == "PASS" for item in automatic.get("automaticReport", [])),
               "structuredOutputValidCount": scorer_structured_output_valid_count(automatic, findings), "diagnosticBuckets": bucket_counts,
               "assistantWeaknesses": assistant_weakness_analysis(suite, findings, critical_failures), "criticalSafetyCount": bucket_counts["CRITICAL_SAFETY"],
               "criticalSafetyFailureCaseIds": sorted(critical_failures),
               "telemetryState": "COMPLETE" if telemetry.failure is None else "UNAVAILABLE", "telemetryFailure": telemetry.failure,
               "telemetrySamples": telemetry.samples, "vramDeltaBytes": vram_delta, "rssDeltaBytes": rss_delta,
               "generationLatencyNs": [record["generationNs"] for record in raw_records], "rawRecords": raw_records,
                "candidateOutputDigest": digest(candidate_run), "warmup": warmup, "cleanup": cleanup,
                "reproducibility": run_reproducibility}
    summary["runDigest"] = digest(summary)
    atomic_write_json(candidate_artifacts / f"{run_id}.json", {"summary": summary, "candidate": candidate_run, "automatic": automatic,
                      "findings": findings, "rawOutputs": raw_outputs, "promptManifest": prompt_manifest,
                      "reproducibility": run_reproducibility}, append_only=True)
    return {"summary": summary, "candidate": candidate_run}


def run_candidate(candidate: dict[str, Any], config: dict[str, Any], suite: dict[str, Any], artifact_root: Path) -> dict[str, Any]:
    """Retrieve once, then isolate every timed repetition in a fresh Python process."""
    provenance_gate(candidate)
    validate_profile_templates(config)
    generation_kwargs(config["runtime"]["decode"])
    try:
        from huggingface_hub import snapshot_download
    except ImportError as error:
        raise BenchmarkError("ENVIRONMENT_UNSUPPORTED: reviewed runtime imports unavailable") from error
    candidate_artifacts = artifact_root / candidate["id"]
    assert_quiescent(compute_pid_snapshot(), descendant_pids(os.getpid()))
    local_path = Path(snapshot_download(repo_id=candidate["repository"], revision=candidate["revision"], local_dir=CACHE_ROOT / candidate["id"]))
    verify_local_snapshot(candidate, local_path)
    run_summaries: list[dict[str, Any]] = []
    r01_candidate: dict[str, Any] | None = None
    for run_id in REPETITIONS:
        assert_quiescent(compute_pid_snapshot(), descendant_pids(os.getpid()))
        payload = {"candidate": candidate, "config": config, "suite": suite, "runId": run_id,
                   "localPath": str(local_path), "artifactRoot": str(artifact_root)}
        child = subprocess.run([sys.executable, str(Path(__file__).resolve()), "--run-repetition"], input=json.dumps(payload),
                               capture_output=True, text=True, cwd=ROOT)
        run_path = candidate_artifacts / f"{run_id}.json"
        if child.returncode != 0 or not run_path.exists():
            raise BenchmarkError(f"REPETITION_PROCESS_FAILED:{run_id}")
        try:
            artifact = json.loads(run_path.read_text(encoding="utf-8"))
            summary, candidate_run = artifact["summary"], artifact["candidate"]
        except (OSError, KeyError, TypeError, json.JSONDecodeError) as error:
            raise BenchmarkError(f"REPETITION_EVIDENCE_INVALID:{run_id}") from error
        assert_quiescent(compute_pid_snapshot(), descendant_pids(os.getpid()))
        if not valid_fresh_repetition(summary, run_id, os.getpid()):
            raise BenchmarkError(f"REPETITION_ISOLATION_INVALID:{run_id}")
        run_summaries.append(summary)
        if run_id == "R01" and summary["state"] == "COMPLETE":
            r01_candidate = candidate_run
        if summary["state"] != "COMPLETE":
            break
    human = (validate_h01(suite, r01_candidate, candidate_artifacts) if r01_candidate is not None
             else {"humanReviewState": "NOT_AVAILABLE", "checkpoint": "H01_REQUIRES_COMPLETE_R01"})
    return {"candidateId": candidate["id"], "candidateMetadata": candidate, "runs": run_summaries, "human": human}


def preflight(config_path: Path, lock_path: Path, artifact_root: Path, install_receipt_path: Path) -> dict[str, Any]:
    config = load_yaml(config_path)
    validate_profile_templates(config)
    generation_kwargs(config["runtime"]["decode"])
    report: dict[str, Any] = {"task": "P6-T5", "startedAtNs": time.time_ns(), "baseline": baseline_preflight()}
    report["runtime"] = runtime_preflight(config, lock_path, install_receipt_path, config_path)
    atomic_write_json(artifact_root / "preflight.json", report)
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--lock", type=Path, default=DEFAULT_LOCK)
    parser.add_argument("--artifact-root", type=Path, default=ARTIFACT_ROOT)
    parser.add_argument("--install-receipt", type=Path,
                        help="immutable reviewed-wheel receipt; required before any model retrieval")
    parser.add_argument("--preflight", action="store_true", help="run no-download validation gates")
    parser.add_argument("--run", action="store_true", help="reserved: requires a separately installed reviewed runtime")
    parser.add_argument("--run-repetition", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    try:
        if args.run_repetition:
            payload = json.loads(sys.stdin.read())
            if not isinstance(payload, dict):
                raise BenchmarkError("REPETITION_PAYLOAD_INVALID")
            result = run_repetition(payload)
            print(json.dumps({"state": result["summary"]["state"], "runId": result["summary"]["runId"]}))
            return 0
        receipt = args.install_receipt or args.artifact_root / "runtime-install-receipt.json"
        report = preflight(args.config, args.lock, args.artifact_root, receipt)
        if args.run:
            config = load_yaml(args.config)
            suite = yaml.safe_load((ROOT / "evals" / "p6-t4-evaluation-suites.yaml").read_text(encoding="utf-8"))
            runnable, non_admitted = admit_candidates(config.get("candidates", []))
            if len(runnable) < 2:
                raise BenchmarkError(
                    f"INSUFFICIENT_VERIFIED_CANDIDATES: {len(runnable)} admitted, need at least 2")
            result = [run_candidate(candidate, config, suite, args.artifact_root) for candidate in runnable]
            comparison = compare_candidates(result, config, suite, non_admitted)
            atomic_write_json(args.artifact_root / "execution.json", {"preflight": report, "candidates": result,
                              "nonAdmittedCandidates": non_admitted, "comparison": comparison}, append_only=True)
            print(json.dumps({"state": comparison["state"], "reportDigest": digest(comparison)}))
            return 0
        print(json.dumps({"state": "PREFLIGHT_PASS", "reportDigest": digest(report)}))
        return 0
    except BenchmarkError as error:
        failure = {"state": "FAILED", "code": str(error), "timestampNs": time.time_ns()}
        atomic_write_json(args.artifact_root / "preflight-failure.json", failure)
        print(json.dumps(failure))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
