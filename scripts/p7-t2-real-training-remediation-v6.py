#!/usr/bin/env python3
"""Real single-T4 QLoRA backend for governed remediation v6."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


BASE_BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation.py")
ROOT = Path(__file__).resolve().parents[1]
PROMPT_PROFILE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/"
    "research-prompt-profile-v3.approved.json"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)
DATASET_IDENTITY = "7a0c264196889beb0c91414cd10195681df895073dc7ce3aeef586123de751c1"
RECORD_SCHEMA_VERSION = "6.0.0"
EXPECTED_TRAIN_RECORDS = 288
EXPECTED_VALIDATION_RECORDS = 48
EXPECTED_CONTRACT_HOLDOUT_RECORDS = 48
RECORD_FIELDS = {
    "schemaVersion",
    "assistantKey",
    "domain",
    "recordType",
    "visibility",
    "useCaseId",
    "trainingPrompt",
    "trainingTarget",
    "contentId",
    "curriculumSegment",
    "semanticFamily",
}


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


BASE = _load_module("p7_t2_remediation_backend_for_v6", BASE_BACKEND_PATH)
canonical_bytes = BASE.canonical_bytes
sha256_bytes = BASE.sha256_bytes


def _artifact_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != "artifactIdentity"})
    )


def _load_prompt_profile() -> tuple[dict[str, Any], str]:
    try:
        profile = json.loads((ROOT / PROMPT_PROFILE_REFERENCE).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"prompt profile v3 unavailable: {error}") from error
    if (
        not isinstance(profile, dict)
        or profile.get("artifactIdentity") != PROMPT_PROFILE_IDENTITY
        or _artifact_identity(profile) != PROMPT_PROFILE_IDENTITY
        or profile.get("status") != "APPROVED"
        or profile.get("profileVersion") != "3.0.0"
        or profile.get("activationAllowed") is not False
    ):
        raise ValueError("prompt profile v3: exact approved training-only identity required")
    instruction = (
        profile.get("assistantProfiles", {})
        .get("RESEARCH_ASSISTANT", {})
        .get("systemInstruction")
    )
    if not isinstance(instruction, str) or not instruction:
        raise ValueError("prompt profile v3: Research system instruction required")
    return profile, instruction


PROMPT_PROFILE, SYSTEM_MESSAGE = _load_prompt_profile()
for name, value in {
    "RECORD_SCHEMA_VERSION": RECORD_SCHEMA_VERSION,
    "EXPECTED_TRAIN_RECORDS": EXPECTED_TRAIN_RECORDS,
    "EXPECTED_VALIDATION_RECORDS": EXPECTED_VALIDATION_RECORDS,
    "EXPECTED_CONTRACT_HOLDOUT_RECORDS": EXPECTED_CONTRACT_HOLDOUT_RECORDS,
    "SYSTEM_MESSAGE": SYSTEM_MESSAGE,
    "RECORD_FIELDS": RECORD_FIELDS,
}.items():
    setattr(BASE, name, value)


training_messages = BASE.training_messages
BASE.legacy.training_messages = training_messages


def load_training_inputs(manifest_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    if config.get("splits") != {
        "training": "train",
        "validation": "validation",
        "contractHoldout": "evaluation",
    }:
        raise ValueError("real remediation v6 requires isolated train/validation/evaluation splits")
    manifest = BASE.legacy._load_json(manifest_path, "dataset manifest")
    if (
        manifest.get("datasetIdentity") != DATASET_IDENTITY
        or config.get("dataset", {}).get("identity") != DATASET_IDENTITY
        or manifest.get("checksum")
        != sha256_bytes(
            canonical_bytes({key: item for key, item in manifest.items() if key != "checksum"})
        )
        or manifest.get("trainingAuthorized") is not True
    ):
        raise ValueError("dataset manifest: exact approved v6 identity required")
    inventory = {
        item.get("filename"): item
        for item in manifest.get("artifacts", [])
        if isinstance(item, dict) and isinstance(item.get("filename"), str)
    }
    result: dict[str, Any] = {"manifest": manifest}
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
        expected_count = {
            "train.jsonl": EXPECTED_TRAIN_RECORDS,
            "validation.jsonl": EXPECTED_VALIDATION_RECORDS,
            "evaluation.jsonl": EXPECTED_CONTRACT_HOLDOUT_RECORDS,
        }[filename]
        if count != expected_count:
            raise ValueError(f"dataset/{filename}: exact v6 record count required")
        result[f"{key}Artifact"] = artifact
        result[f"{key}Records"] = BASE._load_jsonl(path, count, f"dataset/{filename}")
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


_BASE_TOKENIZE_RECORDS = BASE.legacy._tokenize_records


def tokenize_records_with_eos(records: list[dict[str, Any]], tokenizer: Any):
    dataset, metrics = _BASE_TOKENIZE_RECORDS(records, tokenizer)
    eos_token_id = getattr(tokenizer, "eos_token_id", None)
    if not isinstance(eos_token_id, int) or isinstance(eos_token_id, bool):
        raise ValueError("tokenizer chat template: EOS token id required")
    post_eos_tokens_masked = 0
    for index in range(len(dataset)):
        example = dataset[index]
        labels = example.get("labels")
        input_ids = example.get("input_ids")
        if not isinstance(labels, list) or not isinstance(input_ids, list):
            raise ValueError("tokenizer chat template: token ids and labels required")
        eos_positions = [
            position
            for position, value in enumerate(labels)
            if value == eos_token_id
        ]
        if len(eos_positions) != 1:
            raise ValueError(
                "tokenizer chat template: exactly one terminal supervised EOS required"
            )
        eos_position = eos_positions[0]
        trailing_positions = [
            position
            for position in range(eos_position + 1, len(labels))
            if labels[position] != -100
        ]
        if trailing_positions:
            trailing_ids = [input_ids[position] for position in trailing_positions]
            trailing_text = tokenizer.decode(
                trailing_ids,
                skip_special_tokens=False,
            )
            if not isinstance(trailing_text, str) or not trailing_text.isspace():
                raise ValueError(
                    "tokenizer chat template: non-whitespace supervised tokens after EOS"
                )
            for position in trailing_positions:
                labels[position] = -100
            post_eos_tokens_masked += len(trailing_positions)
        supervised = [item for item in labels if item != -100]
        if (
            not supervised
            or supervised[-1] != eos_token_id
            or supervised.count(eos_token_id) != 1
        ):
            raise ValueError(
                "tokenizer chat template: exactly one terminal supervised EOS required"
            )
    return dataset, {**metrics, "postEosWhitespaceTokensMasked": post_eos_tokens_masked}


BASE.load_training_inputs = load_training_inputs
BASE.legacy._tokenize_records = tokenize_records_with_eos
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
