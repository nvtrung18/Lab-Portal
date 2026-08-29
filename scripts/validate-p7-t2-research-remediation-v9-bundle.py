#!/usr/bin/env python3
"""Validate a P7-T2 Research remediation v9 T4 bundle and real output."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


V8_VALIDATOR_PATH = Path(__file__).with_name("validate-p7-t2-research-remediation-v8-bundle.py")


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {path.name}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


V8 = _load_module("p7_t2_remediation_bundle_validator_v8_for_v9", V8_VALIDATOR_PATH)
BASE = V8.BASE
BUNDLE_NAME = "p7-t2-research-remediation-v9-t4"
DATASET_IDENTITY = "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
TRAINING_APPROVAL_IDENTITY = "b3402cab5c4dfbe7d30c3bace1debbe28932da04a9c6f539f85a2865b58add34"
TRAINING_CONTRACT_IDENTITY = "4d3e2f9685afffb8b1ac26f33e140251e17df4924c041dab6a2a2600255a715c"
BUNDLE_VERSION = "9.0.0"
TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4-remediation-v9.json"
TRAINING_PIPELINE_REFERENCE = "scripts/training-pipeline-p7-t2-remediation-v9.py"
BACKEND_REFERENCE = "scripts/p7-t2-real-training-remediation-v9.py"
REQUIRED_FILES = V8.REQUIRED_FILES | {
    "config/p7-t1c-research-remediation-governance-v9/training-dataset-card.approved.json",
    TRAINING_CONFIG_REFERENCE,
    "config/p7-t4-research-remediation-governance-v9/failure-analysis-v8.json",
    "config/p7-t4-research-remediation-governance-v9/governance-amendment-request.json",
    "config/p7-t4-research-remediation-governance-v9/training-approval-request.json",
    "config/p7-t4-research-remediation-governance-v9/training-data-quality-spec-v9.json",
    "datasets/p7-research-synthetic-training-dataset-v9/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v9/manifest.approved.json",
    "datasets/p7-research-synthetic-training-dataset-v9/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v9/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v9/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v9/validation.jsonl",
    "datasets/p7-t4-research-remediation-source-v9/provenance.json",
    "datasets/p7-t4-research-remediation-source-v9/source-export.json",
    "datasets/p7-t4-research-remediation-source-v9/training-contract.json",
    "docs/architecture/ai/p7-t4-research-remediation-v9-runbook.txt",
    "evidence/p7-t1c-research-remediation-v9-training-governance-approval.json",
    "evidence/p7-t4-research-remediation-v9-governance-approval.json",
    BACKEND_REFERENCE,
    TRAINING_PIPELINE_REFERENCE,
    "scripts/validate-p7-t2-research-remediation-v9-bundle.py",
}

for name, value in {
    "BUNDLE_NAME": BUNDLE_NAME, "DATASET_IDENTITY": DATASET_IDENTITY,
    "TRAINING_APPROVAL_IDENTITY": TRAINING_APPROVAL_IDENTITY,
    "TRAINING_CONTRACT_IDENTITY": TRAINING_CONTRACT_IDENTITY,
    "BUNDLE_VERSION": BUNDLE_VERSION, "TRAINING_CONFIG_REFERENCE": TRAINING_CONFIG_REFERENCE,
    "TRAINING_PIPELINE_REFERENCE": TRAINING_PIPELINE_REFERENCE,
    "BACKEND_REFERENCE": BACKEND_REFERENCE, "REQUIRED_FILES": REQUIRED_FILES,
}.items():
    setattr(BASE, name, value)

canonical_bytes = BASE.canonical_bytes
sha256_bytes = BASE.sha256_bytes
bundle_inventory = BASE.bundle_inventory
validate_bundle = BASE.validate_bundle
validate_run_output = BASE.validate_run_output
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
