#!/usr/bin/env python3
"""Validate the governed v10 continuation bundle and optional real output."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import re
import sys


BUILDER_PATH = Path(__file__).with_name("build-p7-t2-research-remediation-v10-bundle.py")


def _load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None: raise ValueError(path)
    module = importlib.util.module_from_spec(spec)
    previous = sys.dont_write_bytecode; sys.dont_write_bytecode = True
    try: spec.loader.exec_module(module)
    finally: sys.dont_write_bytecode = previous
    return module


BUILDER = _load("p7_v10_builder_for_validator", BUILDER_PATH)
BUNDLE_NAME = BUILDER.BUNDLE_NAME
DATASET_IDENTITY = BUILDER.DATASET_IDENTITY
TRAINING_APPROVAL_IDENTITY = BUILDER.TRAINING_APPROVAL_IDENTITY
TRAINING_CONTRACT_IDENTITY = BUILDER.TRAINING_CONTRACT_IDENTITY
PARENT_ADAPTER_IDENTITY = BUILDER.PARENT_ADAPTER_IDENTITY
BUNDLE_VERSION = BUILDER.BUNDLE_VERSION
REQUIRED_FILES = set(BUILDER.SOURCE_FILES) | {
    "parent-adapter/README.md", "parent-adapter/adapter-manifest.json",
    "parent-adapter/adapter_config.json", "parent-adapter/adapter_model.safetensors",
    "parent-adapter/added_tokens.json", "parent-adapter/merges.txt",
    "parent-adapter/special_tokens_map.json", "parent-adapter/tokenizer.json",
    "parent-adapter/tokenizer_config.json", "parent-adapter/vocab.json",
}


def _json(path: Path, label: str):
    try: value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error: raise ValueError(f"{label}: {error}") from error
    if not isinstance(value, dict): raise ValueError(f"{label}: object required")
    return value


def validate_bundle(bundle_root: Path):
    root = bundle_root.resolve(); manifest = _json(root / "bundle-manifest.json", "bundle manifest")
    fields = {"artifactType", "bundleVersion", "runtimeProfile", "sourceCommit", "baseModel", "datasetIdentity", "trainingApprovalIdentity", "trainingContractIdentity", "parentAdapterIdentity", "trainingConfigIdentity", "fileInventory", "fileCount", "bundleIdentity"}
    if set(manifest) != fields or manifest.get("bundleVersion") != BUNDLE_VERSION or re.fullmatch(r"[0-9a-f]{40}", str(manifest.get("sourceCommit"))) is None:
        raise ValueError("bundle manifest: exact v10 fields required")
    if manifest.get("datasetIdentity") != DATASET_IDENTITY or manifest.get("trainingApprovalIdentity") != TRAINING_APPROVAL_IDENTITY or manifest.get("trainingContractIdentity") != TRAINING_CONTRACT_IDENTITY or manifest.get("parentAdapterIdentity") != PARENT_ADAPTER_IDENTITY:
        raise ValueError("bundle manifest: exact v10 governance binding required")
    expected_identity = BUILDER.sha256_bytes(BUILDER.canonical_bytes({k: v for k, v in manifest.items() if k != "bundleIdentity"}))
    inventory = BUILDER.bundle_inventory(root)
    if manifest.get("bundleIdentity") != expected_identity or manifest.get("fileInventory") != inventory or manifest.get("fileCount") != len(inventory):
        raise ValueError("bundle file checksum inventory mismatch")
    paths = {item["path"] for item in inventory}
    if paths != REQUIRED_FILES: raise ValueError("bundle payload: exact v10 file set required")
    forbidden = [path for path in paths if path.endswith((".safetensors", ".bin", ".pt", ".ckpt")) and path != "parent-adapter/adapter_model.safetensors"]
    if forbidden: raise ValueError("bundle payload: unexpected weights forbidden")
    backend = _load("p7_v10_backend_parent_validator", root / "scripts/p7-t2-real-training-remediation-v10.py")
    backend.validate_parent_adapter(root / "parent-adapter")
    pipeline = _load("p7_v10_pipeline_validator", root / BUILDER.PIPELINE)
    config = _json(root / BUILDER.CONFIG, "training config"); pipeline.validate_training_config(config)
    if pipeline.training_config_identity(config) != manifest.get("trainingConfigIdentity"): raise ValueError("training config identity mismatch")
    pipeline.validate_dataset_and_contract_gates(root / config["dataset"]["manifestReference"], config, root)
    return manifest


def validate_run_output(bundle_root: Path, output: Path):
    validate_bundle(bundle_root); config = _json(bundle_root / BUILDER.CONFIG, "training config")
    backend = _load("p7_v10_output_validator", bundle_root / "scripts/p7-t2-real-training-remediation-v10.py")
    return backend.validate_real_training_output(output, config, bundle_root / config["dataset"]["manifestReference"])


def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("--bundle-root", type=Path, required=True); parser.add_argument("--run-output", type=Path); args = parser.parse_args()
    try:
        if args.run_output is None:
            manifest = validate_bundle(args.bundle_root); result = {"state": "VALID", "bundleIdentity": manifest["bundleIdentity"], "candidateId": None, "datasetIdentity": manifest["datasetIdentity"], "parentAdapterIdentity": manifest["parentAdapterIdentity"], "fileCount": manifest["fileCount"]}
        else:
            metadata = validate_run_output(args.bundle_root, args.run_output); result = {"state": "REAL_TRAINING_COMPLETE", "candidateId": metadata["candidateId"], "trainingRunIdentity": metadata["trainingRunIdentity"], "realTraining": True}
        print(json.dumps(result, sort_keys=True, separators=(",", ":"))); return 0
    except (OSError, ValueError) as error:
        print(json.dumps({"state": "ERROR", "diagnostics": [str(error)]}, sort_keys=True), file=sys.stderr); return 2


if __name__ == "__main__": raise SystemExit(main())
