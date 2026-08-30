#!/usr/bin/env python3
"""Build the deterministic P7-T2 Research remediation v3 T4 bundle."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


BASE_BUILDER_PATH = Path(__file__).with_name("build-p7-t2-research-remediation-bundle.py")


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


BASE = _load_module("p7_t2_remediation_bundle_builder_v2_for_v3", BASE_BUILDER_PATH)
BUNDLE_NAME = "p7-t2-research-remediation-v3-t4"
DATASET_IDENTITY = "430390b22936bdea27c7e5b4022795ef483b55ac21f84e3e52cc663b9aaf9d10"
TRAINING_APPROVAL_IDENTITY = "a1f92aec9caca9b053daf780c1bfde951abdb88e2fe3e92f4f2545c676d45015"
TRAINING_CONTRACT_IDENTITY = "4431a4dea11dc3e9f420cbc21070abb6d351db95a4507668e5c189f9040643ad"
BUNDLE_VERSION = "3.0.0"
TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4-remediation-v3.json"
TRAINING_PIPELINE_REFERENCE = "scripts/training-pipeline-p7-t2-remediation-v3.py"
VALIDATOR_REFERENCE = "scripts/validate-p7-t2-research-remediation-v3-bundle.py"
SOURCE_FILES = (
    "ai-service/config/assistant-profiles.json",
    "ai-service/config/schemas/structured-output-schemas.json",
    "config/p6-t6-adapter-decisions.json",
    "config/p7-t1c-research-remediation-governance-v3/training-dataset-card.approved.json",
    TRAINING_CONFIG_REFERENCE,
    "config/p7-t4-research-remediation-governance-v3/training-approval-request.json",
    "datasets/p7-research-synthetic-training-dataset-v3/evaluation.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v3/manifest.json",
    "datasets/p7-research-synthetic-training-dataset-v3/rejections.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v3/train.jsonl",
    "datasets/p7-research-synthetic-training-dataset-v3/validation.jsonl",
    "datasets/p7-t4-research-remediation-source-v3/provenance.json",
    "datasets/p7-t4-research-remediation-source-v3/source-export.json",
    "datasets/p7-t4-research-remediation-source-v3/training-contract.json",
    "docs/architecture/ai/p7-t4-research-remediation-v3-runbook.txt",
    "evidence/p7-t1c-research-remediation-v3-training-governance-approval.json",
    "requirements/p7-t2-real-training-t4-cp313-requirements.txt",
    "scripts/p7-t2-real-training-remediation-v3.py",
    "scripts/p7-t2-real-training-remediation.py",
    "scripts/p7-t2-real-training.py",
    TRAINING_PIPELINE_REFERENCE,
    "scripts/training-pipeline-p7-t2-remediation.py",
    "scripts/training-pipeline-p7-t2.py",
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
