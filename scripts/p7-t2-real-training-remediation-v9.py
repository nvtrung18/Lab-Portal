#!/usr/bin/env python3
"""Real single-T4 QLoRA backend for governed remediation v9."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from typing import Any


V8_BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation-v8.py")
DATASET_IDENTITY = "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
EXPECTED_TRAIN_RECORDS = 480
EXPECTED_VALIDATION_RECORDS = 80
EXPECTED_CONTRACT_HOLDOUT_RECORDS = 80


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


V8 = _load_module("p7_t2_remediation_backend_v8_for_v9", V8_BACKEND_PATH)
BASE = V8.BASE
canonical_bytes = V8.canonical_bytes
sha256_bytes = V8.sha256_bytes

for name, value in {
    "EXPECTED_TRAIN_RECORDS": EXPECTED_TRAIN_RECORDS,
    "EXPECTED_VALIDATION_RECORDS": EXPECTED_VALIDATION_RECORDS,
    "EXPECTED_CONTRACT_HOLDOUT_RECORDS": EXPECTED_CONTRACT_HOLDOUT_RECORDS,
}.items():
    setattr(BASE, name, value)


def training_messages(record: dict[str, Any]) -> list[dict[str, str]]:
    if not isinstance(record, dict) or record.get("schemaVersion") != "9.0.0":
        return V8.training_messages(record)
    prompt = record.get("trainingPrompt")
    target = record.get("trainingTarget")
    structured = target.get("structuredOutput") if isinstance(target, dict) else None
    if (
        set(record) != V8.V8_RECORD_FIELDS
        or record.get("assistantKey") != "RESEARCH_ASSISTANT"
        or record.get("domain") != "RESEARCH"
        or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
        or record.get("useCaseId") != "RESEARCH_UC_006"
        or record.get("targetedEvaluationCaseId") != "E-FUNC-RESEARCH-006"
        or not isinstance(prompt, dict)
        or not isinstance(target, dict)
        or set(target) != V8.OUTPUT_FIELDS
        or target.get("evalCaseId") != prompt.get("evalCaseId")
        or target.get("observedBehavior") != "SUCCESS"
        or target.get("observedActionRisk") != "DRAFT_ONLY"
        or not isinstance(structured, dict)
        or structured.get("kind") != "RESEARCH_REPORT_REVIEW_DRAFT"
        or structured.get("requiresHumanReview") is not True
        or structured.get("advisoryOnly") is not True
    ):
        raise ValueError("training record: exact governed v6/v7/v8/v9 contract required")
    return [
        {"role": "system", "content": V8.SYSTEM_MESSAGE},
        {"role": "user", "content": canonical_bytes(prompt).decode("utf-8")},
        {"role": "assistant", "content": canonical_bytes(target).decode("utf-8")},
    ]


def load_training_inputs(manifest_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    if config.get("splits") != {"training": "train", "validation": "validation", "contractHoldout": "evaluation"}:
        raise ValueError("real remediation v9 requires isolated splits")
    manifest = BASE.legacy._load_json(manifest_path, "dataset manifest")
    if (
        manifest.get("datasetIdentity") != DATASET_IDENTITY
        or config.get("dataset", {}).get("identity") != DATASET_IDENTITY
        or manifest.get("artifactIdentity") != V8._artifact_identity(manifest)
        or manifest.get("trainingAuthorized") is not True
        or manifest.get("approval_status") != "APPROVED"
    ):
        raise ValueError("dataset manifest: exact approved v9 identity required")
    inventory = {item.get("filename"): item for item in manifest.get("artifacts", []) if isinstance(item, dict)}
    expected_counts = {"train.jsonl": 480, "validation.jsonl": 80, "evaluation.jsonl": 80}
    result: dict[str, Any] = {"manifest": manifest}
    for key, filename in (("training", "train.jsonl"), ("validation", "validation.jsonl"), ("contractHoldout", "evaluation.jsonl")):
        artifact = inventory.get(filename)
        path = manifest_path.parent / filename
        if not isinstance(artifact, dict) or not path.is_file() or sha256_bytes(path.read_bytes()) != artifact.get("sha256") or artifact.get("recordCount") != expected_counts[filename]:
            raise ValueError(f"dataset/{filename}: exact v9 artifact required")
        records = BASE._load_jsonl(path, expected_counts[filename], f"dataset/{filename}")
        for record in records:
            training_messages(record)
        result[f"{key}Artifact"] = artifact
        result[f"{key}Records"] = records
    ids = {key: {item["contentId"] for item in result[f"{key}Records"]} for key in ("training", "validation", "contractHoldout")}
    if ids["training"] & ids["validation"] or ids["training"] & ids["contractHoldout"] or ids["validation"] & ids["contractHoldout"]:
        raise ValueError("dataset: optimization, validation, and holdout overlap")
    return result


BASE.training_messages = training_messages
BASE.legacy.training_messages = training_messages
BASE.load_training_inputs = load_training_inputs
tokenize_records_with_eos = V8.tokenize_records_with_eos
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
