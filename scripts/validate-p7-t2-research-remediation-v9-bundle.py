#!/usr/bin/env python3
"""Validate a P7-T2 Research remediation v9 T4 bundle and real output."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


V8_VALIDATOR_PATH = Path(__file__).with_name("validate-p7-t2-research-remediation-v8-bundle.py")
BUILDER_PATH = Path(__file__).with_name("build-p7-t2-research-remediation-v9-bundle.py")


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
BUILDER = _load_module("p7_t2_remediation_bundle_builder_for_v9_validator", BUILDER_PATH)
BASE = V8.BASE
BUNDLE_NAME = BUILDER.BUNDLE_NAME
DATASET_IDENTITY = BUILDER.DATASET_IDENTITY
TRAINING_APPROVAL_IDENTITY = BUILDER.TRAINING_APPROVAL_IDENTITY
TRAINING_CONTRACT_IDENTITY = BUILDER.TRAINING_CONTRACT_IDENTITY
BUNDLE_VERSION = BUILDER.BUNDLE_VERSION
TRAINING_CONFIG_REFERENCE = BUILDER.TRAINING_CONFIG_REFERENCE
TRAINING_PIPELINE_REFERENCE = BUILDER.TRAINING_PIPELINE_REFERENCE
BACKEND_REFERENCE = "scripts/p7-t2-real-training-remediation-v9.py"
REQUIRED_FILES = {"README.md", *BUILDER.SOURCE_FILES}

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
