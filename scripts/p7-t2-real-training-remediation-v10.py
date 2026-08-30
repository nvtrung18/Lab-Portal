#!/usr/bin/env python3
"""Real single-T4 governed QLoRA continuation backend for remediation v10."""
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


V9_PATH = Path(__file__).with_name("p7-t2-real-training-remediation-v9.py")
DATASET_IDENTITY = "abce232c1721788bae5a1686f9d017f295a6892555193140ae74c5a044e0a409"
PARENT_ADAPTER_IDENTITY = "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
PARENT_CANDIDATE_ID = "99596dcf3c00c6a25a4c38f01de7f12d917100e539cf4ea777556ef0c0a055c2"
PARENT_TRAINING_RUN_IDENTITY = "9ee7cd3cb56cb5b18c096ce8764fe14a23d69bdd39ef027a8f1472710b152314"
EXPECTED_TRAIN_RECORDS = 96
EXPECTED_VALIDATION_RECORDS = 20
EXPECTED_CONTRACT_HOLDOUT_RECORDS = 8


def _load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(path)
    module = importlib.util.module_from_spec(spec)
    previous = sys.dont_write_bytecode; sys.dont_write_bytecode = True
    try: spec.loader.exec_module(module)
    finally: sys.dont_write_bytecode = previous
    return module


V9 = _load("p7_t2_backend_v9_for_v10", V9_PATH)
BASE = V9.BASE
for name, value in {
    "EXPECTED_TRAIN_RECORDS": 96, "EXPECTED_VALIDATION_RECORDS": 20,
    "EXPECTED_CONTRACT_HOLDOUT_RECORDS": 8,
}.items():
    setattr(BASE, name, value)


def training_messages(record: dict[str, Any]) -> list[dict[str, str]]:
    if not isinstance(record, dict) or record.get("schemaVersion") != "10.0.0":
        return V9.training_messages(record)
    prompt, target = record.get("trainingPrompt"), record.get("trainingTarget")
    structured = target.get("structuredOutput") if isinstance(target, dict) else None
    expected_fields = V9.V8.V8_RECORD_FIELDS
    if (
        set(record) != expected_fields or record.get("assistantKey") != "RESEARCH_ASSISTANT"
        or record.get("domain") != "RESEARCH" or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
        or record.get("useCaseId") != "RESEARCH_UC_006"
        or record.get("targetedEvaluationCaseId") != "E-FUNC-RESEARCH-006"
        or not isinstance(prompt, dict) or not isinstance(target, dict)
        or set(target) != V9.V8.OUTPUT_FIELDS or target.get("evalCaseId") != prompt.get("evalCaseId")
        or target.get("observedBehavior") != "SUCCESS" or target.get("observedActionRisk") != "DRAFT_ONLY"
        or not isinstance(structured, dict) or structured.get("kind") != "RESEARCH_REPORT_REVIEW_DRAFT"
        or structured.get("requiresHumanReview") is not True or structured.get("advisoryOnly") is not True
    ):
        raise ValueError("training record: exact governed v6-v10 contract required")
    return [
        {"role": "system", "content": V9.V8.SYSTEM_MESSAGE},
        {"role": "user", "content": V9.canonical_bytes(prompt).decode()},
        {"role": "assistant", "content": V9.canonical_bytes(target).decode()},
    ]


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_parent_adapter(path: Path) -> dict[str, Any]:
    manifest_path = path / "adapter-manifest.json"
    if not manifest_path.is_file():
        raise ValueError("parent adapter: manifest required")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if (
        manifest.get("adapterIdentity") != PARENT_ADAPTER_IDENTITY
        or manifest.get("candidateId") != PARENT_CANDIDATE_ID
        or manifest.get("trainingRunIdentity") != PARENT_TRAINING_RUN_IDENTITY
        or manifest.get("adapterDisposition") != "CANDIDATE_ONLY"
    ):
        raise ValueError("parent adapter: exact governed v9 identity required")
    inventory = {item.get("filename"): item for item in manifest.get("artifacts", []) if isinstance(item, dict)}
    expected_files = {"README.md", "adapter_config.json", "adapter_model.safetensors", "added_tokens.json", "merges.txt", "special_tokens_map.json", "tokenizer.json", "tokenizer_config.json", "vocab.json"}
    if set(inventory) != expected_files:
        raise ValueError("parent adapter: exact artifact inventory required")
    for filename, item in inventory.items():
        artifact = path / filename
        if not isinstance(item, dict) or not artifact.is_file() or artifact.stat().st_size != item.get("size") or _sha(artifact) != item.get("sha256"):
            raise ValueError(f"parent adapter: {filename} mismatch")
    return manifest


def load_parent_adapter(peft: Any, model: Any, path: Path) -> Any:
    if not (path / "adapter_config.json").is_file() or not (path / "adapter_model.safetensors").is_file():
        raise ValueError("parent adapter: trainable PEFT files required")
    return peft.PeftModel.from_pretrained(model, str(path.resolve()), is_trainable=True, local_files_only=True)


def load_training_inputs(manifest_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    manifest = BASE.legacy._load_json(manifest_path, "dataset manifest")
    if (
        config.get("splits") != {"training": "train", "validation": "validation", "contractHoldout": "evaluation"}
        or manifest.get("datasetIdentity") != DATASET_IDENTITY
        or config.get("dataset", {}).get("identity") != DATASET_IDENTITY
        or manifest.get("artifactIdentity") != V9.V8._artifact_identity(manifest)
        or manifest.get("trainingAuthorized") is not True or manifest.get("approval_status") != "APPROVED"
    ):
        raise ValueError("dataset manifest: exact approved v10 identity required")
    inventory = {item.get("filename"): item for item in manifest.get("artifacts", []) if isinstance(item, dict)}
    expected = {"train.jsonl": 96, "validation.jsonl": 20, "evaluation.jsonl": 8}
    result: dict[str, Any] = {"manifest": manifest}
    for key, filename in (("training", "train.jsonl"), ("validation", "validation.jsonl"), ("contractHoldout", "evaluation.jsonl")):
        item, path = inventory.get(filename), manifest_path.parent / filename
        if not isinstance(item, dict) or not path.is_file() or _sha(path) != item.get("sha256") or item.get("recordCount") != expected[filename]:
            raise ValueError(f"dataset/{filename}: exact v10 artifact required")
        records = BASE._load_jsonl(path, expected[filename], f"dataset/{filename}")
        for record in records: training_messages(record)
        result[f"{key}Artifact"], result[f"{key}Records"] = item, records
    ids = [{item["contentId"] for item in result[f"{key}Records"]} for key in ("training", "validation", "contractHoldout")]
    if ids[0] & ids[1] or ids[0] & ids[2] or ids[1] & ids[2]:
        raise ValueError("dataset: optimization, validation, and holdout overlap")
    return result


def training_argument_values(config: dict[str, Any], checkpoint_root: Path) -> dict[str, Any]:
    values = V9.training_argument_values(config, checkpoint_root)
    values["max_steps"] = config["training"]["maxSteps"]
    return values


def run_real_training(**kwargs: Any) -> dict[str, Any]:
    config, manifest_path = kwargs["config"], kwargs["dataset_manifest_path"]
    root = manifest_path.resolve().parents[2]
    parent_path = root / config.get("continuation", {}).get("parentAdapterReference", "")
    validate_parent_adapter(parent_path)
    original_runtime = BASE.legacy._runtime_modules

    def continuation_runtime(precision: str) -> dict[str, Any]:
        runtime = original_runtime(precision)
        peft = runtime["peft"]
        class Proxy:
            def __getattr__(self, name: str) -> Any: return getattr(peft, name)
            def get_peft_model(self, model: Any, _configuration: Any) -> Any:
                return load_parent_adapter(peft, model, parent_path)
        runtime["peft"] = Proxy()
        return runtime

    BASE.legacy._runtime_modules = continuation_runtime
    try:
        return BASE.run_real_training(**kwargs)
    finally:
        BASE.legacy._runtime_modules = original_runtime


BASE.training_messages = training_messages
BASE.legacy.training_messages = training_messages
BASE.load_training_inputs = load_training_inputs
BASE.training_argument_values = training_argument_values
tokenize_records_with_eos = V9.tokenize_records_with_eos
training_config_identity = BASE.training_config_identity
training_run_identity = BASE.training_run_identity
validate_model_snapshot = BASE.validate_model_snapshot
validate_runtime_preflight = BASE.validate_runtime_preflight
validate_real_training_output = BASE.validate_real_training_output
