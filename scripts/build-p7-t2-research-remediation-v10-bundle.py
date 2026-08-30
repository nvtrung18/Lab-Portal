#!/usr/bin/env python3
"""Build the deterministic v10 T4 continuation bundle with the exact v9 adapter."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile


V9_PATH = Path(__file__).with_name("build-p7-t2-research-remediation-v9-bundle.py")
BUNDLE_NAME = "p7-t2-research-remediation-v10-t4"
DATASET_IDENTITY = "abce232c1721788bae5a1686f9d017f295a6892555193140ae74c5a044e0a409"
TRAINING_APPROVAL_IDENTITY = "fc9fd2b0d53ae50ce7568abdb2894f7d70c81b6e7e46d7ea65c5e333c480c553"
TRAINING_CONTRACT_IDENTITY = "3b924bb766dd6936c7e9aa5d0387c928856b9582e6283b06dbf5d36e2f3fa042"
PARENT_ADAPTER_IDENTITY = "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
PARENT_ARCHIVE_SHA256 = "f6c3e5d4ca52643f8c26941a61407444f7004b5e1e22c08445307821dbee767f"
BUNDLE_VERSION = "10.0.0"
CONFIG = "config/p7-t2-training-pipeline-t4-remediation-v10.json"
PIPELINE = "scripts/training-pipeline-p7-t2-remediation-v10.py"
VALIDATOR = "scripts/validate-p7-t2-research-remediation-v10-bundle.py"


def _load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None: raise RuntimeError(path)
    module = importlib.util.module_from_spec(spec)
    previous = sys.dont_write_bytecode; sys.dont_write_bytecode = True
    try: spec.loader.exec_module(module)
    finally: sys.dont_write_bytecode = previous
    return module


V9 = _load("p7_v9_builder_for_v10", V9_PATH)
ROOT = Path(__file__).resolve().parents[1]
V10_FILES = (
    "config/p7-t1c-research-remediation-governance-v10/training-dataset-card.approved.json", CONFIG,
    "config/p7-t4-research-remediation-governance-v10/governance-amendment-request.json",
    "config/p7-t4-research-remediation-governance-v10/human-review-finding-v9.json",
    "config/p7-t4-research-remediation-governance-v10/targeted-continuation-quality-spec-v10.json",
    "config/p7-t4-research-remediation-governance-v10/training-approval-request.json",
    "datasets/p7-research-synthetic-training-dataset-v10/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v10/manifest.approved.json",
    "datasets/p7-research-synthetic-training-dataset-v10/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v10/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v10/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v10/validation.jsonl",
    "datasets/p7-t4-research-remediation-source-v10/provenance.json",
    "datasets/p7-t4-research-remediation-source-v10/source-export.json",
    "datasets/p7-t4-research-remediation-source-v10/training-contract.json",
    "docs/architecture/ai/p7-t4-research-remediation-v10-runbook.txt",
    "evidence/p7-t1c-research-remediation-v10-training-governance-approval.json",
    "evidence/p7-t4-research-remediation-v10-governance-approval.json",
    "scripts/build-p7-t2-research-remediation-v10-bundle.py",
    "scripts/p7-t2-real-training-remediation-v10.py", PIPELINE, VALIDATOR,
)
SOURCE_FILES = tuple(dict.fromkeys(V9.SOURCE_FILES + V10_FILES))


def canonical_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode()


def sha256_bytes(value: bytes) -> str: return hashlib.sha256(value).hexdigest()


def bundle_inventory(root: Path) -> list[dict[str, object]]:
    result = []
    for path in sorted((p for p in root.rglob("*") if p.is_file()), key=lambda p: p.relative_to(root).as_posix()):
        logical = path.relative_to(root).as_posix()
        if logical == "bundle-manifest.json" or "__pycache__" in path.parts or path.suffix == ".pyc": continue
        payload = path.read_bytes(); result.append({"path": logical, "size": len(payload), "sha256": sha256_bytes(payload)})
    return result


def verify_committed_sources(source_root: Path, source_commit: str) -> None:
    if re.fullmatch(r"[0-9a-f]{40}", source_commit) is None or subprocess.run(["git", "cat-file", "-e", f"{source_commit}^{{commit}}"], cwd=source_root, capture_output=True).returncode != 0:
        raise ValueError("source commit does not exist")
    mismatches = []
    for logical in SOURCE_FILES:
        path = source_root / logical
        committed = subprocess.run(["git", "rev-parse", f"{source_commit}:{logical}"], cwd=source_root, capture_output=True, text=True)
        working = subprocess.run(["git", "hash-object", f"--path={logical}", "--", logical], cwd=source_root, capture_output=True, text=True)
        if not path.is_file(): mismatches.append(f"missing:{logical}")
        elif committed.returncode != 0: mismatches.append(f"uncommitted:{logical}")
        elif working.returncode != 0 or working.stdout.strip() != committed.stdout.strip(): mismatches.append(f"modified:{logical}")
    if mismatches: raise ValueError("bundle sources must match source commit: " + ", ".join(mismatches))


def _extract_parent(archive: Path, target: Path) -> None:
    if not archive.is_file() or sha256_bytes(archive.read_bytes()) != PARENT_ARCHIVE_SHA256:
        raise ValueError("parent output archive: exact v9 SHA-256 required")
    prefix = "p7-t2-research-remediation-v9-output/adapter/"
    with zipfile.ZipFile(archive) as source:
        entries = [item for item in source.infolist() if item.filename.startswith(prefix) and not item.is_dir()]
        if not entries: raise ValueError("parent output archive: adapter inventory missing")
        target.mkdir()
        for item in entries:
            name = item.filename[len(prefix):]
            if not name or "/" in name or "\\" in name: raise ValueError("parent output archive: unsafe adapter entry")
            (target / name).write_bytes(source.read(item))


def _write_zip(root: Path, path: Path) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for file in sorted((p for p in root.rglob("*") if p.is_file()), key=lambda p: p.relative_to(root).as_posix()):
            info = zipfile.ZipInfo(f"{BUNDLE_NAME}/{file.relative_to(root).as_posix()}", (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.external_attr = 0o100644 << 16; info.create_system = 3
            archive.writestr(info, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build_bundle(*, source_root: Path, output_dir: Path, zip_path: Path, source_commit: str, parent_output_archive: Path, enforce_committed_sources: bool = True) -> dict[str, object]:
    source_root = source_root.resolve()
    if re.fullmatch(r"[0-9a-f]{40}", source_commit) is None:
        raise ValueError("full source commit required")
    if enforce_committed_sources: verify_committed_sources(source_root, source_commit)
    if output_dir.exists() or zip_path.exists(): raise FileExistsError("bundle outputs already exist")
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".p7-v10.", dir=output_dir.parent) as temporary:
        staging = Path(temporary) / BUNDLE_NAME; staging.mkdir()
        for logical in SOURCE_FILES:
            source, target = source_root / logical, staging / logical
            if not source.is_file(): raise FileNotFoundError(logical)
            target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(source, target)
        _extract_parent(parent_output_archive, staging / "parent-adapter")
        pipeline = _load("p7_v10_pipeline_for_builder", staging / PIPELINE)
        config = json.loads((staging / CONFIG).read_text(encoding="utf-8")); pipeline.validate_training_config(config); pipeline.validate_dataset_and_contract_gates(staging / config["dataset"]["manifestReference"], config, staging)
        manifest = {"artifactType": "P7-T2-RESEARCH-REMEDIATION-REAL-TRAINING-BUNDLE", "bundleVersion": BUNDLE_VERSION, "runtimeProfile": "COLAB_TESLA_T4_CP313_CUDA118", "sourceCommit": source_commit, "baseModel": {"identifier": "Qwen/Qwen3-4B-Instruct-2507", "revision": "cdbee75f17c01a7cc42f958dc650907174af0554"}, "datasetIdentity": DATASET_IDENTITY, "trainingApprovalIdentity": TRAINING_APPROVAL_IDENTITY, "trainingContractIdentity": TRAINING_CONTRACT_IDENTITY, "parentAdapterIdentity": PARENT_ADAPTER_IDENTITY, "trainingConfigIdentity": pipeline.training_config_identity(config), "fileInventory": bundle_inventory(staging), "fileCount": 0, "bundleIdentity": ""}
        manifest["fileCount"] = len(manifest["fileInventory"]); manifest["bundleIdentity"] = sha256_bytes(canonical_bytes({k: v for k, v in manifest.items() if k != "bundleIdentity"}))
        (staging / "bundle-manifest.json").write_bytes(json_bytes(manifest))
        validator = _load("p7_v10_validator_for_builder", source_root / VALIDATOR); validator.validate_bundle(staging)
        staged_zip = Path(temporary) / f"{BUNDLE_NAME}.zip"; _write_zip(staging, staged_zip)
        os.replace(staging, output_dir); os.replace(staged_zip, zip_path)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("--source-root", type=Path, default=ROOT); parser.add_argument("--output-dir", type=Path); parser.add_argument("--zip-path", type=Path); parser.add_argument("--source-commit"); parser.add_argument("--parent-output-archive", type=Path, required=True); args = parser.parse_args()
    commit = args.source_commit or subprocess.run(["git", "rev-parse", "HEAD"], cwd=args.source_root, check=True, capture_output=True, text=True).stdout.strip()
    output = args.output_dir or ROOT / ".artifacts/p7-t2-remediation-v10-ready" / BUNDLE_NAME; archive = args.zip_path or output.parent / f"{BUNDLE_NAME}.zip"
    manifest = build_bundle(source_root=args.source_root, output_dir=output, zip_path=archive, source_commit=commit, parent_output_archive=args.parent_output_archive)
    print(json.dumps({"state": "READY_FOR_EXTERNAL_REAL_TRAINING", "bundle": str(output), "archive": str(archive), "bundleIdentity": manifest["bundleIdentity"], "sha256": sha256_bytes(archive.read_bytes())}, sort_keys=True, separators=(",", ":"))); return 0


if __name__ == "__main__": raise SystemExit(main())
