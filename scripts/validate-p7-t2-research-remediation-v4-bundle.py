#!/usr/bin/env python3
"""Validate a P7-T2 Research remediation v4 T4 bundle and real output."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


BASE_VALIDATOR_PATH = Path(__file__).with_name("validate-p7-t2-research-remediation-bundle.py")


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


BASE = _load_module("p7_t2_remediation_bundle_validator_v2_for_v4", BASE_VALIDATOR_PATH)
BUNDLE_NAME = "p7-t2-research-remediation-v4-t4"
DATASET_IDENTITY = "d8e4792f43de2319acbd2eefb3518aff597ef24530b074434f8c9336ad7f51e4"
TRAINING_APPROVAL_IDENTITY = "fb14df19e4a0156d148b4c1f06fcfd67c673d7a086d6f1545fc8b0a4d2a21787"
TRAINING_CONTRACT_IDENTITY = "c535dca968c54b9206b91eb7969036d4c6973ab91a3b8bc5f9b165699b4f79e5"
BUNDLE_VERSION = "4.0.0"
TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4-remediation-v4.json"
TRAINING_PIPELINE_REFERENCE = "scripts/training-pipeline-p7-t2-remediation-v4.py"
BACKEND_REFERENCE = "scripts/p7-t2-real-training-remediation-v4.py"
REQUIRED_FILES = {
    "README.md",
    "ai-service/config/assistant-profiles.json",
    "config/p6-t6-adapter-decisions.json",
    "config/p7-t1c-research-remediation-governance-v4/training-dataset-card.approved.json",
    TRAINING_CONFIG_REFERENCE,
    "config/p7-t4-research-remediation-governance-v4/data-governance-v2.approved.yml",
    "config/p7-t4-research-remediation-governance-v4/structured-output-schema.approved.json",
    "config/p7-t4-research-remediation-governance-v4/training-approval-request.json",
    "config/p7-t4-research-remediation-governance-v4/training-data-quality-spec.json",
    "datasets/p7-research-synthetic-training-dataset-v4/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v4/manifest.approved.json",
    "datasets/p7-research-synthetic-training-dataset-v4/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v4/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v4/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v4/validation.jsonl",
    "datasets/p7-t4-research-remediation-source-v4/provenance.json",
    "datasets/p7-t4-research-remediation-source-v4/source-export.json",
    "datasets/p7-t4-research-remediation-source-v4/training-contract.json",
    "docs/architecture/ai/p7-t4-research-remediation-v4-runbook.txt",
    "evidence/p7-t1c-research-remediation-v4-training-governance-approval.json",
    "evidence/p7-t4-research-remediation-v4-governance-approval.json",
    "requirements/p7-t2-real-training-t4-cp313-requirements.txt",
    BACKEND_REFERENCE,
    "scripts/p7-t2-real-training-remediation.py",
    "scripts/p7-t2-real-training.py",
    TRAINING_PIPELINE_REFERENCE,
    "scripts/training-pipeline-p7-t2-remediation.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-p7-t2-research-remediation-bundle.py",
    "scripts/validate-p7-t2-research-remediation-v4-bundle.py",
}

for name, value in {
    "BUNDLE_NAME": BUNDLE_NAME,
    "DATASET_IDENTITY": DATASET_IDENTITY,
    "TRAINING_APPROVAL_IDENTITY": TRAINING_APPROVAL_IDENTITY,
    "TRAINING_CONTRACT_IDENTITY": TRAINING_CONTRACT_IDENTITY,
    "BUNDLE_VERSION": BUNDLE_VERSION,
    "TRAINING_CONFIG_REFERENCE": TRAINING_CONFIG_REFERENCE,
    "TRAINING_PIPELINE_REFERENCE": TRAINING_PIPELINE_REFERENCE,
    "BACKEND_REFERENCE": BACKEND_REFERENCE,
    "REQUIRED_FILES": REQUIRED_FILES,
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
