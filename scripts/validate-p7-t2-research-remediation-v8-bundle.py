#!/usr/bin/env python3
"""Validate a P7-T2 Research remediation v8 T4 bundle and real output."""
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


BASE = _load_module("p7_t2_remediation_bundle_validator_for_v8", BASE_VALIDATOR_PATH)
BUNDLE_NAME = "p7-t2-research-remediation-v8-t4"
DATASET_IDENTITY = "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
TRAINING_APPROVAL_IDENTITY = "bdcc8337530fa85cde395977c7e8f76f84ddd63c67b8b282be9e903b6c2276d8"
TRAINING_CONTRACT_IDENTITY = "cd6a1edd2283ad5c584ee2e376bab1b81a8c841e1205e730c441fa9f7c9962f2"
BUNDLE_VERSION = "8.0.0"
TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4-remediation-v8.json"
TRAINING_PIPELINE_REFERENCE = "scripts/training-pipeline-p7-t2-remediation-v8.py"
BACKEND_REFERENCE = "scripts/p7-t2-real-training-remediation-v8.py"
REQUIRED_FILES = {
    "README.md",
    "config/p6-t6-adapter-decisions.json",
    "config/p7-t1c-research-remediation-governance-v8/training-dataset-card.approved.json",
    TRAINING_CONFIG_REFERENCE,
    "config/p7-t4-research-remediation-governance-v5/evaluation-suite-v2.approved.json",
    "config/p7-t4-research-remediation-governance-v5/evaluator-contract-v2.approved.json",
    "config/p7-t4-research-remediation-governance-v6/research-prompt-profile-v3.approved.json",
    "config/p7-t4-research-remediation-governance-v8/failure-analysis-v7.json",
    "config/p7-t4-research-remediation-governance-v8/governance-amendment-request.json",
    "config/p7-t4-research-remediation-governance-v8/training-approval-request.json",
    "config/p7-t4-research-remediation-governance-v8/training-data-quality-spec-v8.json",
    "datasets/p7-research-synthetic-training-dataset-v7/manifest.approved.json",
    "datasets/p7-research-synthetic-training-dataset-v8/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v8/manifest.approved.json",
    "datasets/p7-research-synthetic-training-dataset-v8/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v8/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v8/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v8/validation.jsonl",
    "datasets/p7-t4-research-remediation-source-v8/provenance.json",
    "datasets/p7-t4-research-remediation-source-v8/source-export.json",
    "datasets/p7-t4-research-remediation-source-v8/training-contract.json",
    "docs/architecture/ai/p7-t4-research-remediation-v8-runbook.txt",
    "evidence/p7-t1c-research-remediation-v8-training-governance-approval.json",
    "evidence/p7-t4-research-remediation-v5-evaluator-governance-approval.json",
    "evidence/p7-t4-research-remediation-v8-governance-approval.json",
    "requirements/p7-t2-real-training-t4-cp313-requirements.txt",
    BACKEND_REFERENCE,
    "scripts/p7-t2-real-training-remediation-v7.py",
    "scripts/p7-t2-real-training-remediation-v6.py",
    "scripts/p7-t2-real-training-remediation.py",
    "scripts/p7-t2-real-training.py",
    TRAINING_PIPELINE_REFERENCE,
    "scripts/training-pipeline-p7-t2-remediation-v7.py",
    "scripts/training-pipeline-p7-t2-remediation-v6.py",
    "scripts/training-pipeline-p7-t2-remediation.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-evaluation-suites.py",
    "scripts/validate-p7-t4-research-evaluation-v2.py",
    "scripts/validate-p7-t2-research-remediation-bundle.py",
    "scripts/validate-p7-t2-research-remediation-v8-bundle.py",
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
