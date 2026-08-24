#!/usr/bin/env python3
"""Real single-T4 QLoRA backend for governed Research remediation v3."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


BASE_BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation.py")


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


BASE = _load_module("p7_t2_remediation_backend_v2_for_v3", BASE_BACKEND_PATH)
RECORD_SCHEMA_VERSION = "3.0.0"
EXPECTED_TRAIN_RECORDS = 214
EXPECTED_VALIDATION_RECORDS = 22
EXPECTED_CONTRACT_HOLDOUT_RECORDS = 34

for name, value in {
    "RECORD_SCHEMA_VERSION": RECORD_SCHEMA_VERSION,
    "EXPECTED_TRAIN_RECORDS": EXPECTED_TRAIN_RECORDS,
    "EXPECTED_VALIDATION_RECORDS": EXPECTED_VALIDATION_RECORDS,
    "EXPECTED_CONTRACT_HOLDOUT_RECORDS": EXPECTED_CONTRACT_HOLDOUT_RECORDS,
}.items():
    setattr(BASE, name, value)

canonical_bytes = BASE.canonical_bytes
sha256_bytes = BASE.sha256_bytes
training_config_identity = BASE.training_config_identity
training_run_identity = BASE.training_run_identity
validate_model_snapshot = BASE.validate_model_snapshot
training_messages = BASE.training_messages
load_training_inputs = BASE.load_training_inputs
training_argument_values = BASE.training_argument_values
canonical_checkpoint_name = BASE.canonical_checkpoint_name
validate_runtime_preflight = BASE.validate_runtime_preflight
validate_real_metadata_contract = BASE.validate_real_metadata_contract
run_real_training = BASE.run_real_training
validate_real_training_output = BASE.validate_real_training_output
