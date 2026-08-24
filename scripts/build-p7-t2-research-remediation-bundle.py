#!/usr/bin/env python3
"""Build the deterministic P7-T2 Research remediation T4 execution bundle."""
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
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BUNDLE_NAME = "p7-t2-research-remediation-t4"
MANIFEST_NAME = "bundle-manifest.json"
DATASET_IDENTITY = "0409e9087efe7332e298d0c3812d11f2edac7cedf538a8db475776d9c190eb30"
TRAINING_APPROVAL_IDENTITY = "5565b0339f9745d3e0b9cb44353bb97a131824fcdd7511130d11d6742b13dbd0"
TRAINING_CONTRACT_IDENTITY = "89e49c43fd6488a6d47473141ad9070bd0dd785e309bbdaf26246e41d277a145"
BUNDLE_VERSION = "2.0.0"
TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4-remediation.json"
TRAINING_PIPELINE_REFERENCE = "scripts/training-pipeline-p7-t2-remediation.py"
VALIDATOR_REFERENCE = "scripts/validate-p7-t2-research-remediation-bundle.py"
BASE_MODEL = {
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SOURCE_FILES = (
    "ai-service/config/assistant-profiles.json",
    "ai-service/config/schemas/structured-output-schemas.json",
    "config/p6-t6-adapter-decisions.json",
    "config/p7-t1c-research-remediation-governance-v2/training-dataset-card.approved.json",
    "config/p7-t2-training-pipeline-t4-remediation.json",
    "config/p7-t4-research-remediation-governance-v2/training-approval-request.json",
    "datasets/p7-research-synthetic-training-dataset-v2/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v2/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v2/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v2/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v2/validation.jsonl",
    "datasets/p7-t4-research-remediation-source-v2/provenance.json",
    "datasets/p7-t4-research-remediation-source-v2/source-export.json",
    "datasets/p7-t4-research-remediation-source-v2/training-contract.json",
    "docs/architecture/ai/p7-t4-research-remediation-runbook.txt",
    "evidence/p7-t1c-research-remediation-training-governance-approval.json",
    "requirements/p7-t2-real-training-t4-cp313-requirements.txt",
    "scripts/p7-t2-real-training-remediation.py",
    "scripts/p7-t2-real-training.py",
    "scripts/training-pipeline-p7-t2-remediation.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-p7-t2-research-remediation-bundle.py",
)


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise ValueError(f"module unavailable: {path.name}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    try:
        sys.dont_write_bytecode = True
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


def verify_committed_sources(source_root: Path, source_commit: str) -> None:
    if COMMIT_PATTERN.fullmatch(source_commit) is None:
        raise ValueError("full source commit required")
    existence = subprocess.run(
        ["git", "cat-file", "-e", f"{source_commit}^{{commit}}"],
        cwd=source_root,
        capture_output=True,
    )
    if existence.returncode != 0:
        raise ValueError("source commit does not exist")
    mismatches: list[str] = []
    for logical in SOURCE_FILES:
        path = source_root / logical
        if not path.is_file():
            mismatches.append(f"missing:{logical}")
            continue
        committed = subprocess.run(
            ["git", "rev-parse", f"{source_commit}:{logical}"],
            cwd=source_root,
            capture_output=True,
            text=True,
        )
        if committed.returncode != 0:
            mismatches.append(f"uncommitted:{logical}")
            continue
        working = subprocess.run(
            ["git", "hash-object", f"--path={logical}", "--", logical],
            cwd=source_root,
            capture_output=True,
            text=True,
        )
        if working.returncode != 0 or working.stdout.strip() != committed.stdout.strip():
            mismatches.append(f"modified:{logical}")
    if mismatches:
        raise ValueError("bundle sources must match source commit: " + ", ".join(mismatches))


def bundle_inventory(bundle_root: Path) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for path in sorted(
        (item for item in bundle_root.rglob("*") if item.is_file()),
        key=lambda item: item.relative_to(bundle_root).as_posix(),
    ):
        logical = path.relative_to(bundle_root).as_posix()
        if logical == MANIFEST_NAME or "__pycache__" in path.parts or path.suffix == ".pyc":
            continue
        payload = path.read_bytes()
        result.append({"path": logical, "size": len(payload), "sha256": sha256_bytes(payload)})
    return result


def _readme(source_commit: str) -> bytes:
    return (
        "# P7-T2 Research remediation - Tesla T4\n\n"
        "This bundle trains a new candidate only. It does not establish P7-T4 PASS or promotion.\n\n"
        f"Source commit: `{source_commit}`\n\n"
        "Runtime: Colab Python 3.13.15, Tesla T4, CUDA 11.8. Install the hash-pinned "
        "requirements, restart the whole runtime, download the exact base-model revision, "
        "run `--preflight-only`, then execute the same command without that flag.\n\n"
        "The `evaluation` split is an independent contract holdout and is never supplied to "
        "Trainer for optimization, early stopping, or best-checkpoint selection. The resulting "
        "adapter remains `CANDIDATE_ONLY` until the unchanged P7-T4 evaluation passes.\n"
    ).encode("utf-8")


def _manifest(bundle_root: Path, source_commit: str) -> dict[str, Any]:
    pipeline = _load_module(
        "p7_t2_remediation_bundle_pipeline",
        bundle_root / TRAINING_PIPELINE_REFERENCE,
    )
    config = json.loads(
        (bundle_root / TRAINING_CONFIG_REFERENCE).read_text(encoding="utf-8")
    )
    pipeline.validate_training_config(config)
    pipeline.validate_dataset_and_contract_gates(
        bundle_root / config["dataset"]["manifestReference"], config, bundle_root
    )
    manifest = {
        "artifactType": "P7-T2-RESEARCH-REMEDIATION-REAL-TRAINING-BUNDLE",
        "bundleVersion": BUNDLE_VERSION,
        "runtimeProfile": "COLAB_TESLA_T4_CP313_CUDA118",
        "sourceCommit": source_commit,
        "baseModel": BASE_MODEL,
        "datasetIdentity": DATASET_IDENTITY,
        "trainingApprovalIdentity": TRAINING_APPROVAL_IDENTITY,
        "trainingContractIdentity": TRAINING_CONTRACT_IDENTITY,
        "trainingConfigIdentity": pipeline.training_config_identity(config),
        "fileInventory": bundle_inventory(bundle_root),
        "fileCount": 0,
        "bundleIdentity": "",
    }
    manifest["fileCount"] = len(manifest["fileInventory"])
    manifest["bundleIdentity"] = sha256_bytes(
        canonical_bytes({key: value for key, value in manifest.items() if key != "bundleIdentity"})
    )
    return manifest


def _write_zip(bundle_root: Path, zip_path: Path) -> None:
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(
            (item for item in bundle_root.rglob("*") if item.is_file()),
            key=lambda item: item.relative_to(bundle_root).as_posix(),
        ):
            logical = f"{BUNDLE_NAME}/{path.relative_to(bundle_root).as_posix()}"
            info = zipfile.ZipInfo(logical, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            info.create_system = 3
            archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build_bundle(
    *,
    source_root: Path,
    output_dir: Path,
    zip_path: Path,
    source_commit: str,
    enforce_committed_sources: bool = True,
) -> dict[str, Any]:
    source_root = source_root.resolve()
    if COMMIT_PATTERN.fullmatch(source_commit) is None:
        raise ValueError("full source commit required")
    if enforce_committed_sources:
        verify_committed_sources(source_root, source_commit)
    if output_dir.exists() or zip_path.exists():
        raise FileExistsError("bundle outputs are append-only and already exist")
    if output_dir.parent.resolve() != zip_path.parent.resolve():
        raise ValueError("bundle directory and ZIP must share a parent")
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f".{BUNDLE_NAME}.", dir=output_dir.parent) as name:
        staging = Path(name) / BUNDLE_NAME
        staging.mkdir()
        for logical in SOURCE_FILES:
            source = source_root / logical
            if not source.is_file():
                raise FileNotFoundError(f"required bundle source missing: {logical}")
            target = staging / logical
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
        (staging / "README.md").write_bytes(_readme(source_commit))
        manifest = _manifest(staging, source_commit)
        (staging / MANIFEST_NAME).write_bytes(json_bytes(manifest))
        validator = _load_module(
            "p7_t2_remediation_bundle_validator_for_builder",
            source_root / VALIDATOR_REFERENCE,
        )
        validator.validate_bundle(staging)
        staged_zip = Path(name) / f"{BUNDLE_NAME}.zip"
        _write_zip(staging, staged_zip)
        os.replace(staging, output_dir)
        os.replace(staged_zip, zip_path)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=ROOT)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--zip-path", type=Path)
    parser.add_argument("--source-commit")
    args = parser.parse_args()
    source_commit = args.source_commit
    if source_commit is None:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=args.source_root,
            check=True,
            capture_output=True,
            text=True,
        )
        source_commit = result.stdout.strip()
    output_dir = args.output_dir or ROOT / ".artifacts/p7-t2-remediation-ready" / BUNDLE_NAME
    zip_path = args.zip_path or output_dir.parent / f"{BUNDLE_NAME}.zip"
    manifest = build_bundle(
        source_root=args.source_root,
        output_dir=output_dir,
        zip_path=zip_path,
        source_commit=source_commit,
    )
    print(
        json.dumps(
            {
                "state": "READY_FOR_EXTERNAL_REAL_TRAINING",
                "bundle": str(output_dir),
                "archive": str(zip_path),
                "bundleIdentity": manifest["bundleIdentity"],
                "sha256": sha256_bytes(zip_path.read_bytes()),
            },
            sort_keys=True,
            separators=(",", ":"),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
