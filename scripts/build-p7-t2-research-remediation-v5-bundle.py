#!/usr/bin/env python3
"""Build the deterministic P7-T2 Research remediation v5 T4 bundle."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


BASE_BUILDER_PATH = Path(__file__).with_name(
    "build-p7-t2-research-remediation-bundle.py"
)


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


BASE = _load_module("p7_t2_remediation_bundle_builder_for_v5", BASE_BUILDER_PATH)
BUNDLE_NAME = "p7-t2-research-remediation-v5-t4"
DATASET_IDENTITY = "591ded79431b4ffbba786a63fdffe7486f2a19fe787ef91261b26d8c33e01f67"
TRAINING_APPROVAL_IDENTITY = "19a405ca049b7bc15b8f2b96d5b2c85eb364f138309b5528a71518ee86632dba"
TRAINING_CONTRACT_IDENTITY = "c3e90ed16695cde5fb08f33d2d80a30e8d59db6f9a9652d051316a15dc6d8e18"
BUNDLE_VERSION = "5.0.0"
TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4-remediation-v5.json"
TRAINING_PIPELINE_REFERENCE = "scripts/training-pipeline-p7-t2-remediation-v5.py"
VALIDATOR_REFERENCE = "scripts/validate-p7-t2-research-remediation-v5-bundle.py"
SOURCE_FILES = (
    "config/p6-t6-adapter-decisions.json",
    "config/p7-t1c-research-remediation-governance-v5/training-dataset-card.approved.json",
    TRAINING_CONFIG_REFERENCE,
    "config/p7-t4-research-remediation-governance-v5/evaluation-suite-v2.approved.json",
    "config/p7-t4-research-remediation-governance-v5/evaluator-contract-v2.approved.json",
    "config/p7-t4-research-remediation-governance-v5/training-approval-request.json",
    "config/p7-t4-research-remediation-governance-v5/training-data-quality-spec-v5.json",
    "datasets/p7-research-synthetic-training-dataset-v5/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v5/manifest.approved.json",
    "datasets/p7-research-synthetic-training-dataset-v5/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v5/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v5/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v5/validation.jsonl",
    "datasets/p7-t4-research-remediation-source-v5/provenance.json",
    "datasets/p7-t4-research-remediation-source-v5/source-export.json",
    "datasets/p7-t4-research-remediation-source-v5/training-contract.json",
    "docs/architecture/ai/p7-t4-research-remediation-v5-runbook.txt",
    "evidence/p7-t1c-research-remediation-v5-training-governance-approval.json",
    "evidence/p7-t4-research-remediation-v4-governance-approval.json",
    "evidence/p7-t4-research-remediation-v5-evaluator-governance-approval.json",
    "requirements/p7-t2-real-training-t4-cp313-requirements.txt",
    "scripts/p7-t2-real-training-remediation-v5.py",
    "scripts/p7-t2-real-training-remediation.py",
    "scripts/p7-t2-real-training.py",
    TRAINING_PIPELINE_REFERENCE,
    "scripts/training-pipeline-p7-t2-remediation.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-evaluation-suites.py",
    "scripts/validate-p7-t4-research-evaluation-v2.py",
    "scripts/validate-p7-t2-research-remediation-bundle.py",
    VALIDATOR_REFERENCE,
)

for name, value in {
    "BUNDLE_NAME": BUNDLE_NAME,
    "DATASET_IDENTITY": DATASET_IDENTITY,
    "TRAINING_APPROVAL_IDENTITY": TRAINING_APPROVAL_IDENTITY,
    "TRAINING_CONTRACT_IDENTITY": TRAINING_CONTRACT_IDENTITY,
    "BUNDLE_VERSION": BUNDLE_VERSION,
    "TRAINING_CONFIG_REFERENCE": TRAINING_CONFIG_REFERENCE,
    "TRAINING_PIPELINE_REFERENCE": TRAINING_PIPELINE_REFERENCE,
    "VALIDATOR_REFERENCE": VALIDATOR_REFERENCE,
    "SOURCE_FILES": SOURCE_FILES,
}.items():
    setattr(BASE, name, value)

canonical_bytes = BASE.canonical_bytes
json_bytes = BASE.json_bytes
sha256_bytes = BASE.sha256_bytes
verify_committed_sources = BASE.verify_committed_sources
bundle_inventory = BASE.bundle_inventory
build_bundle = BASE.build_bundle
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
