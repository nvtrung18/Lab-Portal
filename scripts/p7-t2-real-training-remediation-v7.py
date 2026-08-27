#!/usr/bin/env python3
"""Real single-T4 QLoRA backend for governed retention-first remediation v7."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from typing import Any


V6_BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation-v6.py")
DATASET_IDENTITY = "b973bf0a387b35b84140a2db1d2bbf7c5c0d7f054102a968c10bd1df6faeff23"
RECORD_SCHEMA_VERSIONS = {"6.0.0", "7.0.0"}
EXPECTED_TRAIN_RECORDS = 384
EXPECTED_VALIDATION_RECORDS = 64
EXPECTED_CONTRACT_HOLDOUT_RECORDS = 64


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


V6 = _load_module("p7_t2_remediation_backend_v6_for_v7", V6_BACKEND_PATH)
BASE = V6.BASE
canonical_bytes = V6.canonical_bytes
sha256_bytes = V6.sha256_bytes
PROMPT_PROFILE = V6.PROMPT_PROFILE
SYSTEM_MESSAGE = V6.SYSTEM_MESSAGE
RECORD_FIELDS = V6.RECORD_FIELDS
OUTPUT_FIELDS = BASE.OUTPUT_FIELDS


def _artifact_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes(
            {key: item for key, item in value.items() if key != "artifactIdentity"}
        )
    )


def training_messages(record: dict[str, Any]) -> list[dict[str, str]]:
    if not isinstance(record, dict) or set(record) != RECORD_FIELDS:
        raise ValueError("training record: exact governed v6/v7 fields required")
    prompt = record.get("trainingPrompt")
    target = record.get("trainingTarget")
    if (
        record.get("schemaVersion") not in RECORD_SCHEMA_VERSIONS
        or record.get("assistantKey") != "RESEARCH_ASSISTANT"
        or record.get("domain") != "RESEARCH"
        or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
        or not isinstance(prompt, dict)
        or not isinstance(target, dict)
        or set(target) != OUTPUT_FIELDS
        or target.get("evalCaseId") != prompt.get("evalCaseId")
        or prompt.get("useCaseId") != record.get("useCaseId")
    ):
        raise ValueError("training record: governed retention-first prompt/target contract mismatch")
    return [
        {"role": "system", "content": SYSTEM_MESSAGE},
        {"role": "user", "content": canonical_bytes(prompt).decode("utf-8")},
        {"role": "assistant", "content": canonical_bytes(target).decode("utf-8")},
    ]


def load_training_inputs(manifest_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    if config.get("splits") != {
        "training": "train",
        "validation": "validation",
        "contractHoldout": "evaluation",
    }:
        raise ValueError("real remediation v7 requires isolated train/validation/evaluation splits")
    manifest = BASE.legacy._load_json(manifest_path, "dataset manifest")
    if (
        manifest.get("datasetIdentity") != DATASET_IDENTITY
        or config.get("dataset", {}).get("identity") != DATASET_IDENTITY
        or manifest.get("artifactIdentity") != _artifact_identity(manifest)
        or manifest.get("trainingAuthorized") is not True
        or manifest.get("approval_status") != "APPROVED"
    ):
        raise ValueError("dataset manifest: exact approved retention-first v7 identity required")
    inventory = {
        item.get("filename"): item
        for item in manifest.get("artifacts", [])
        if isinstance(item, dict) and isinstance(item.get("filename"), str)
    }
    result: dict[str, Any] = {"manifest": manifest}
    expected_counts = {
        "train.jsonl": EXPECTED_TRAIN_RECORDS,
        "validation.jsonl": EXPECTED_VALIDATION_RECORDS,
        "evaluation.jsonl": EXPECTED_CONTRACT_HOLDOUT_RECORDS,
    }
    for key, filename in (
        ("training", "train.jsonl"),
        ("validation", "validation.jsonl"),
        ("contractHoldout", "evaluation.jsonl"),
    ):
        artifact = inventory.get(filename)
        if not isinstance(artifact, dict):
            raise ValueError(f"dataset manifest: missing {filename}")
        path = manifest_path.parent / filename
        expected_sha256 = BASE.legacy._require_sha256(
            artifact.get("sha256"), f"dataset/{filename}"
        )
        if not path.is_file() or sha256_bytes(path.read_bytes()) != expected_sha256:
            raise ValueError(f"dataset/{filename}: checksum mismatch")
        count = artifact.get("recordCount")
        if count != expected_counts[filename]:
            raise ValueError(f"dataset/{filename}: exact v7 record count required")
        result[f"{key}Artifact"] = artifact
        result[f"{key}Records"] = BASE._load_jsonl(
            path, count, f"dataset/{filename}"
        )
    ids = {
        key: {item["contentId"] for item in result[f"{key}Records"]}
        for key in ("training", "validation", "contractHoldout")
    }
    if (
        ids["training"] & ids["validation"]
        or ids["training"] & ids["contractHoldout"]
        or ids["validation"] & ids["contractHoldout"]
    ):
        raise ValueError("dataset: optimization, validation, and contract holdout overlap")
    return result


BASE.training_messages = training_messages
BASE.legacy.training_messages = training_messages
BASE.load_training_inputs = load_training_inputs
tokenize_records_with_eos = V6.tokenize_records_with_eos
training_config_identity = BASE.training_config_identity
training_run_identity = BASE.training_run_identity
validate_model_snapshot = BASE.validate_model_snapshot
training_argument_values = BASE.training_argument_values
canonical_checkpoint_name = BASE.canonical_checkpoint_name
validate_runtime_preflight = BASE.validate_runtime_preflight
validate_finite_log_history = BASE.validate_finite_log_history
validate_finite_checkpoint_metrics = BASE.validate_finite_checkpoint_metrics
non_finite_metric_callback = BASE.non_finite_metric_callback
configure_gradient_scaler = BASE.configure_gradient_scaler
validate_real_metadata_contract = BASE.validate_real_metadata_contract
run_real_training = BASE.run_real_training
validate_real_training_output = BASE.validate_real_training_output
