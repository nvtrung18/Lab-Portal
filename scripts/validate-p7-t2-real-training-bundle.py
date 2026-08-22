#!/usr/bin/env python3
"""Validate the portable P7-T2 real-training bundle and imported run output."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
from typing import Any


BUNDLE_NAME = "p7-t2-real-training"
MANIFEST_NAME = "bundle-manifest.json"
DATASET_IDENTITY = "7bc78402046966f603f81c374ae68bafe13be2eb0b90de297d16461e38b970e4"
TRAINING_APPROVAL_IDENTITY = "cf809163ab031f0fb4730cf6137073eca510cfad5914c4c9292b5c5fbef48eb4"
EVALUATION_APPROVAL_IDENTITY = "3ee4fa0f0dabee3ca8602e659c1c9b6a5fe0a30c523c88d99535a40036765479"
BASE_MODEL = {
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
REQUIRED_FILES = {
    "README.md",
    "config/p6-t6-adapter-decisions.json",
    "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-approval-request.json",
    "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-manifest.pending.json",
    "config/p7-t1b-research-governance-packet-v1/training-approval-request.json",
    "config/p7-t1b-research-governance-packet-v1/training-dataset-card.pending.json",
    "config/p7-t1c-research-governance-v1/frozen-evaluation-manifest.approved.json",
    "config/p7-t1c-research-governance-v1/training-dataset-card.approved.json",
    "config/p7-t2-training-pipeline.json",
    "datasets/p7-t1a-research-synthetic-source-v1/provenance.json",
    "datasets/p7-t1a-research-synthetic-source-v1/source-export.json",
    "datasets/p7-research-synthetic-training-dataset-v1/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v1/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v1/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v1/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v1/validation.jsonl",
    "evidence/p7-t1c-frozen-evaluation-governance-approval.json",
    "evidence/p7-t1c-research-training-governance-approval.json",
    "requirements/p7-t2-real-training-requirements.txt",
    "scripts/p7-t2-real-training.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-p7-t2-real-training-bundle.py",
}
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
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
        raise ValueError(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


def _inventory(bundle_root: Path) -> list[dict[str, Any]]:
    result = []
    for path in sorted(
        (item for item in bundle_root.rglob("*") if item.is_file()),
        key=lambda item: item.relative_to(bundle_root).as_posix(),
    ):
        logical = path.relative_to(bundle_root).as_posix()
        if logical == MANIFEST_NAME:
            continue
        payload = path.read_bytes()
        result.append({"path": logical, "size": len(payload), "sha256": sha256_bytes(payload)})
    return result


def validate_bundle(bundle_root: Path) -> dict[str, Any]:
    bundle_root = bundle_root.resolve()
    manifest = _load_json(bundle_root / MANIFEST_NAME, "bundle manifest")
    expected_fields = {
        "artifactType",
        "manifestVersion",
        "bundleVersion",
        "sourceCommit",
        "baseModel",
        "dataset",
        "approvals",
        "trainingConfigIdentity",
        "inventoryScope",
        "fileCount",
        "fileInventory",
        "bundleIdentity",
    }
    if set(manifest) != expected_fields:
        raise ValueError("bundle manifest: exact fields required")
    if (
        manifest.get("artifactType") != "P7-T2-REAL-TRAINING-BUNDLE-MANIFEST"
        or manifest.get("manifestVersion") != "1.0"
        or manifest.get("bundleVersion") != "1.0.0"
    ):
        raise ValueError("bundle manifest: unsupported contract")
    if not isinstance(manifest.get("sourceCommit"), str) or not COMMIT_PATTERN.fullmatch(manifest["sourceCommit"]):
        raise ValueError("bundle manifest: full source commit required")
    if manifest.get("baseModel") != BASE_MODEL:
        raise ValueError("bundle manifest: exact base model/revision required")
    if manifest.get("dataset") != {
        "identity": DATASET_IDENTITY,
        "manifestReference": "datasets/p7-research-synthetic-training-dataset-v1/manifest.json",
    }:
        raise ValueError("bundle manifest: exact approved dataset required")
    if manifest.get("approvals") != {
        "evaluation": EVALUATION_APPROVAL_IDENTITY,
        "training": TRAINING_APPROVAL_IDENTITY,
    }:
        raise ValueError("bundle manifest: exact independent approvals required")
    identity_document = {key: value for key, value in manifest.items() if key != "bundleIdentity"}
    if manifest.get("bundleIdentity") != sha256_bytes(canonical_bytes(identity_document)):
        raise ValueError("bundle manifest: identity mismatch")
    actual_inventory = _inventory(bundle_root)
    if manifest.get("fileInventory") != actual_inventory:
        raise ValueError("bundle file checksum inventory mismatch")
    if manifest.get("fileCount") != len(actual_inventory) + 1:
        raise ValueError("bundle file count mismatch")
    paths = {item["path"] for item in actual_inventory}
    if paths != REQUIRED_FILES:
        raise ValueError("bundle payload: exact required file set mismatch")
    if any(item["path"].endswith((".safetensors", ".bin", ".pt", ".ckpt")) for item in actual_inventory):
        raise ValueError("bundle payload: model/checkpoint weights are forbidden")

    pipeline = _load_module("p7_t2_bundle_pipeline", bundle_root / "scripts/training-pipeline-p7-t2.py")
    real = _load_module("p7_t2_bundle_real", bundle_root / "scripts/p7-t2-real-training.py")
    config = _load_json(bundle_root / "config/p7-t2-training-pipeline.json", "training config")
    decisions = _load_json(bundle_root / "config/p6-t6-adapter-decisions.json", "decision manifest")
    pipeline.validate_training_config(config)
    pipeline.validate_decision_manifest(decisions)
    if config.get("baseModel") != BASE_MODEL or config.get("dataset") != manifest["dataset"]:
        raise ValueError("training config: bundle identity binding mismatch")
    config_identity = pipeline.training_config_identity(config)
    if manifest.get("trainingConfigIdentity") != config_identity:
        raise ValueError("training config identity mismatch")
    if decisions["decisions"].get("RESEARCH_ASSISTANT") != "ADAPTER_REQUIRED":
        raise ValueError("decision manifest: Research adapter required")
    dataset_manifest_path = bundle_root / config["dataset"]["manifestReference"]
    pipeline.validate_dataset_manifest(
        dataset_manifest_path,
        config["dataset"]["identity"],
        config["assistantKey"],
        {config["splits"]["training"], config["splits"]["evaluation"]},
    )
    inputs = real.load_training_inputs(dataset_manifest_path, config)
    if len(inputs["trainingRecords"]) != 36 or len(inputs["validationRecords"]) != 3:
        raise ValueError("training input: exact split counts required")
    training_approval = _load_json(
        bundle_root / "evidence/p7-t1c-research-training-governance-approval.json",
        "training approval",
    )
    evaluation_approval = _load_json(
        bundle_root / "evidence/p7-t1c-frozen-evaluation-governance-approval.json",
        "evaluation approval",
    )
    if (
        training_approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or training_approval.get("purpose") != "TRAINING"
        or training_approval.get("status") != "APPROVED"
    ):
        raise ValueError("training approval: exact approved artifact required")
    if (
        evaluation_approval.get("artifactIdentity") != EVALUATION_APPROVAL_IDENTITY
        or evaluation_approval.get("purpose") != "EVALUATION"
        or evaluation_approval.get("status") != "APPROVED"
        or evaluation_approval.get("scope", {}).get("trainingAllowed") is not False
    ):
        raise ValueError("evaluation approval: EVALUATION-only artifact required")
    source_path = bundle_root / "datasets/p7-t1a-research-synthetic-source-v1/source-export.json"
    if sha256_bytes(source_path.read_bytes()) != "7b5744e1e49925b228d346cf60817e2fbf976283b72c1673aedb329449503436":
        raise ValueError("P7-T1A source: authoritative SHA-256 mismatch")
    provenance = _load_json(
        bundle_root / "datasets/p7-t1a-research-synthetic-source-v1/provenance.json",
        "P7-T1A provenance",
    )
    if provenance.get("provenanceIdentity") != "04ed322cf9604d753c1fdb2ab03120aa06c4856b185fb98d387f26e969c6ed1b":
        raise ValueError("P7-T1A provenance: identity mismatch")
    requests = (
        (
            "config/p7-t1b-research-governance-packet-v1/training-approval-request.json",
            "32a766b69cd4410854c1285fc3b9b8e55d4d36cdf54352c27545f49d2fccc9b9",
            "TRAINING",
        ),
        (
            "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-approval-request.json",
            "9f9b38c038c7c2ed2afaadc5496e5049f904fd3f26eac4c2feaee7ed0d8e0dba",
            "EVALUATION",
        ),
    )
    for reference, identity, purpose in requests:
        request = _load_json(bundle_root / reference, f"{purpose} request")
        if (
            request.get("requestIdentity") != identity
            or request.get("requestedScope", {}).get("permittedPurposes") != [purpose]
        ):
            raise ValueError(f"{purpose} request: exact identity/purpose required")
    if training_approval.get("source", {}).get("sourceSha256") != sha256_bytes(source_path.read_bytes()):
        raise ValueError("training approval: source SHA-256 binding mismatch")
    return manifest


def validate_run_output(bundle_root: Path, output_directory: Path) -> dict[str, Any]:
    manifest = validate_bundle(bundle_root)
    pipeline = _load_module("p7_t2_output_pipeline", bundle_root / "scripts/training-pipeline-p7-t2.py")
    real = _load_module("p7_t2_output_real", bundle_root / "scripts/p7-t2-real-training.py")
    config = _load_json(bundle_root / "config/p7-t2-training-pipeline.json", "training config")
    metadata = real.validate_real_training_output(
        output_directory.resolve(),
        config,
        bundle_root.resolve() / config["dataset"]["manifestReference"],
    )
    if (
        metadata.get("trainingConfigIdentity") != manifest["trainingConfigIdentity"]
        or metadata.get("sourceCommit") != manifest["sourceCommit"]
        or metadata.get("trainingRunIdentity") != pipeline.training_run_identity(config)
    ):
        raise ValueError("run output: bundle provenance mismatch")
    return metadata


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle-root", type=Path, required=True)
    parser.add_argument("--run-output", type=Path)
    args = parser.parse_args()
    try:
        if args.run_output is None:
            manifest = validate_bundle(args.bundle_root)
            result = {
                "status": "READY_FOR_REAL_TRAINING",
                "bundleIdentity": manifest["bundleIdentity"],
                "datasetIdentity": manifest["dataset"]["identity"],
                "trainingConfigIdentity": manifest["trainingConfigIdentity"],
                "baseModel": manifest["baseModel"],
            }
        else:
            metadata = validate_run_output(args.bundle_root, args.run_output)
            result = {
                "status": "REAL_TRAINING_COMPLETE",
                "backend": metadata["backend"],
                "realTraining": metadata["realTraining"],
                "candidateId": metadata["candidateId"],
                "trainingRunIdentity": metadata["trainingRunIdentity"],
            }
        print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except (OSError, ValueError) as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": [str(error)]}, sort_keys=True, separators=(",", ":")),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
