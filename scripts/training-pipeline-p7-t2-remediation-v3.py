#!/usr/bin/env python3
"""Run governed P7-T2 Research remediation v3 training."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


BASE_PIPELINE_PATH = Path(__file__).with_name("training-pipeline-p7-t2-remediation.py")


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


BASE = _load_module("p7_t2_remediation_pipeline_v2_for_v3", BASE_PIPELINE_PATH)
ROOT = BASE.ROOT
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation-v3.py"
SCHEMA_VERSION = "3.0.0"
PIPELINE_VERSION = "3.0.0"
DATASET_IDENTITY = "430390b22936bdea27c7e5b4022795ef483b55ac21f84e3e52cc663b9aaf9d10"
TRAINING_APPROVAL_IDENTITY = "a1f92aec9caca9b053daf780c1bfde951abdb88e2fe3e92f4f2545c676d45015"
TRAINING_CONTRACT_IDENTITY = "4431a4dea11dc3e9f420cbc21070abb6d351db95a4507668e5c189f9040643ad"
DATASET_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v3/manifest.json"
)
DATASET_RECORD_SCHEMA_VERSION = "3.0.0"

for name, value in {
    "BACKEND_PATH": BACKEND_PATH,
    "SCHEMA_VERSION": SCHEMA_VERSION,
    "PIPELINE_VERSION": PIPELINE_VERSION,
    "DATASET_IDENTITY": DATASET_IDENTITY,
    "TRAINING_APPROVAL_IDENTITY": TRAINING_APPROVAL_IDENTITY,
    "TRAINING_CONTRACT_IDENTITY": TRAINING_CONTRACT_IDENTITY,
    "DATASET_MANIFEST_REFERENCE": DATASET_MANIFEST_REFERENCE,
    "DATASET_RECORD_SCHEMA_VERSION": DATASET_RECORD_SCHEMA_VERSION,
}.items():
    setattr(BASE, name, value)

TrainingPipelineError = BASE.TrainingPipelineError
canonical_bytes = BASE.canonical_bytes
sha256_bytes = BASE.sha256_bytes
artifact_identity = BASE.artifact_identity
load_document = BASE.load_document
validate_training_config = BASE.validate_training_config
training_config_identity = BASE.training_config_identity
training_run_identity = BASE.training_run_identity
validate_dataset_and_contract_gates = BASE.validate_dataset_and_contract_gates
run_pipeline = BASE.run_pipeline
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
