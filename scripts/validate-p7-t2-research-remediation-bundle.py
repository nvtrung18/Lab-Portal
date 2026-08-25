#!/usr/bin/env python3
"""Validate a P7-T2 Research remediation T4 bundle and optional real output."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
from typing import Any


BUNDLE_NAME = "p7-t2-research-remediation-t4"
MANIFEST_NAME = "bundle-manifest.json"
DATASET_IDENTITY = "0409e9087efe7332e298d0c3812d11f2edac7cedf538a8db475776d9c190eb30"
TRAINING_APPROVAL_IDENTITY = "5565b0339f9745d3e0b9cb44353bb97a131824fcdd7511130d11d6742b13dbd0"
TRAINING_CONTRACT_IDENTITY = "89e49c43fd6488a6d47473141ad9070bd0dd785e309bbdaf26246e41d277a145"
BUNDLE_VERSION = "2.0.0"
EXECUTION_APPROVAL_IDENTITY: str | None = None
TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4-remediation.json"
TRAINING_PIPELINE_REFERENCE = "scripts/training-pipeline-p7-t2-remediation.py"
BACKEND_REFERENCE = "scripts/p7-t2-real-training-remediation.py"
BASE_MODEL = {
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
REQUIRED_FILES = {
    "README.md",
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
}


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _load_json(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"{label}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{label}: object required")
    return value


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


def validate_bundle(bundle_root: Path) -> dict[str, Any]:
    bundle_root = bundle_root.resolve()
    manifest = _load_json(bundle_root / MANIFEST_NAME, "bundle manifest")
    expected_fields = {
        "artifactType",
        "bundleVersion",
        "runtimeProfile",
        "sourceCommit",
        "baseModel",
        "datasetIdentity",
        "trainingApprovalIdentity",
        "trainingContractIdentity",
        "trainingConfigIdentity",
        "fileInventory",
        "fileCount",
        "bundleIdentity",
    }
    if EXECUTION_APPROVAL_IDENTITY is not None:
        expected_fields.add("executionApprovalIdentity")
    if set(manifest) != expected_fields:
        raise ValueError("bundle manifest: exact fields required")
    if (
        manifest.get("artifactType")
        != "P7-T2-RESEARCH-REMEDIATION-REAL-TRAINING-BUNDLE"
        or manifest.get("bundleVersion") != BUNDLE_VERSION
        or manifest.get("runtimeProfile") != "COLAB_TESLA_T4_CP313_CUDA118"
    ):
        raise ValueError("bundle manifest: unsupported contract")
    if not isinstance(manifest.get("sourceCommit"), str) or COMMIT_PATTERN.fullmatch(
        manifest["sourceCommit"]
    ) is None:
        raise ValueError("bundle manifest: full source commit required")
    if (
        manifest.get("baseModel") != BASE_MODEL
        or manifest.get("datasetIdentity") != DATASET_IDENTITY
        or manifest.get("trainingApprovalIdentity") != TRAINING_APPROVAL_IDENTITY
        or manifest.get("trainingContractIdentity") != TRAINING_CONTRACT_IDENTITY
    ):
        raise ValueError("bundle manifest: exact model/dataset/governance binding required")
    if (
        EXECUTION_APPROVAL_IDENTITY is not None
        and manifest.get("executionApprovalIdentity") != EXECUTION_APPROVAL_IDENTITY
    ):
        raise ValueError("bundle manifest: exact execution approval binding required")
    expected_identity = sha256_bytes(
        canonical_bytes({key: value for key, value in manifest.items() if key != "bundleIdentity"})
    )
    if manifest.get("bundleIdentity") != expected_identity:
        raise ValueError("bundle manifest: identity mismatch")
    actual_inventory = bundle_inventory(bundle_root)
    if manifest.get("fileInventory") != actual_inventory:
        raise ValueError("bundle file checksum inventory mismatch")
    if manifest.get("fileCount") != len(actual_inventory):
        raise ValueError("bundle file count mismatch")
    paths = {item["path"] for item in actual_inventory}
    if paths != REQUIRED_FILES:
        raise ValueError("bundle payload: exact required file set mismatch")
    if any(path.endswith((".safetensors", ".bin", ".pt", ".ckpt")) for path in paths):
        raise ValueError("bundle payload: model/checkpoint weights forbidden")

    pipeline = _load_module(
        "p7_t2_remediation_bundle_pipeline_validator",
        bundle_root / TRAINING_PIPELINE_REFERENCE,
    )
    config = _load_json(
        bundle_root / TRAINING_CONFIG_REFERENCE,
        "training config",
    )
    pipeline.validate_training_config(config)
    if pipeline.training_config_identity(config) != manifest.get("trainingConfigIdentity"):
        raise ValueError("training config: bundle identity binding mismatch")
    pipeline.validate_dataset_and_contract_gates(
        bundle_root / config["dataset"]["manifestReference"], config, bundle_root
    )
    legacy = _load_module(
        "p7_t2_remediation_bundle_decision_validator",
        bundle_root / "scripts/training-pipeline-p7-t2.py",
    )
    decisions = _load_json(
        bundle_root / "config/p6-t6-adapter-decisions.json", "adapter decisions"
    )
    if legacy.resolve_decision(decisions, "RESEARCH_ASSISTANT") != "ADAPTER_REQUIRED":
        raise ValueError("adapter decision: ADAPTER_REQUIRED required")
    return manifest


def validate_run_output(bundle_root: Path, output_directory: Path) -> dict[str, Any]:
    validate_bundle(bundle_root)
    config = _load_json(
        bundle_root / TRAINING_CONFIG_REFERENCE,
        "training config",
    )
    backend = _load_module(
        "p7_t2_remediation_output_validator",
        bundle_root / BACKEND_REFERENCE,
    )
    return backend.validate_real_training_output(
        output_directory,
        config,
        bundle_root / config["dataset"]["manifestReference"],
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle-root", type=Path, required=True)
    parser.add_argument("--run-output", type=Path)
    args = parser.parse_args()
    try:
        if args.run_output is None:
            manifest = validate_bundle(args.bundle_root)
            result = {
                "state": "VALID",
                "bundleIdentity": manifest["bundleIdentity"],
                "candidateId": None,
                "datasetIdentity": manifest["datasetIdentity"],
                "fileCount": manifest["fileCount"],
            }
        else:
            metadata = validate_run_output(args.bundle_root, args.run_output)
            result = {
                "state": "REAL_TRAINING_COMPLETE",
                "candidateId": metadata["candidateId"],
                "trainingRunIdentity": metadata["trainingRunIdentity"],
                "realTraining": True,
            }
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 0
    except (OSError, ValueError) as error:
        print(
            json.dumps({"state": "ERROR", "diagnostics": [str(error)]}, sort_keys=True),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
