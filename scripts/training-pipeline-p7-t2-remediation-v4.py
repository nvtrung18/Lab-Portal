#!/usr/bin/env python3
"""Run governed P7-T2 Research remediation v4 training."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from typing import Any

from jsonschema import Draft202012Validator


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


BASE = _load_module("p7_t2_remediation_pipeline_v2_for_v4", BASE_PIPELINE_PATH)
ROOT = BASE.ROOT
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation-v4.py"
SCHEMA_VERSION = "4.0.0"
PIPELINE_VERSION = "4.0.0"
DATASET_IDENTITY = "d8e4792f43de2319acbd2eefb3518aff597ef24530b074434f8c9336ad7f51e4"
TRAINING_APPROVAL_IDENTITY = "fb14df19e4a0156d148b4c1f06fcfd67c673d7a086d6f1545fc8b0a4d2a21787"
TRAINING_CONTRACT_IDENTITY = "c535dca968c54b9206b91eb7969036d4c6973ab91a3b8bc5f9b165699b4f79e5"
DATASET_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v4/manifest.approved.json"
)
DATASET_RECORD_SCHEMA_VERSION = "4.0.0"
GRADIENT_SCALER_INITIAL_SCALE = 32768
INCLUDED_USE_CASES = {
    "RESEARCH_UC_003",
    "RESEARCH_UC_004",
    "RESEARCH_UC_005",
    "RESEARCH_UC_006",
}

for name, value in {
    "BACKEND_PATH": BACKEND_PATH,
    "SCHEMA_VERSION": SCHEMA_VERSION,
    "PIPELINE_VERSION": PIPELINE_VERSION,
    "DATASET_IDENTITY": DATASET_IDENTITY,
    "TRAINING_APPROVAL_IDENTITY": TRAINING_APPROVAL_IDENTITY,
    "TRAINING_CONTRACT_IDENTITY": TRAINING_CONTRACT_IDENTITY,
    "DATASET_MANIFEST_REFERENCE": DATASET_MANIFEST_REFERENCE,
    "DATASET_RECORD_SCHEMA_VERSION": DATASET_RECORD_SCHEMA_VERSION,
    "GRADIENT_SCALER_INITIAL_SCALE": GRADIENT_SCALER_INITIAL_SCALE,
    "INCLUDED_USE_CASES": INCLUDED_USE_CASES,
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


def _records(manifest_path: Path, manifest: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise TrainingPipelineError("dataset manifest: artifact inventory required")
    inventory = {item.get("filename"): item for item in artifacts if isinstance(item, dict)}
    result: dict[str, list[dict[str, Any]]] = {}
    for split in ("train", "validation", "evaluation"):
        item = inventory.get(f"{split}.jsonl")
        if not isinstance(item, dict):
            raise TrainingPipelineError(f"dataset manifest: missing {split}.jsonl")
        result[split] = BASE._load_records(manifest_path.parent / f"{split}.jsonl", item)
    content_ids = {
        split: {record["contentId"] for record in records}
        for split, records in result.items()
    }
    if (
        content_ids["train"] & content_ids["validation"]
        or content_ids["train"] & content_ids["evaluation"]
        or content_ids["validation"] & content_ids["evaluation"]
    ):
        raise TrainingPipelineError("dataset: train/validation/contract holdout must be disjoint")
    return result


def validate_dataset_and_contract_gates(
    manifest_path: Path, config: dict[str, Any], repository_root: Path
) -> dict[str, Any]:
    validate_training_config(config)
    manifest = load_document(manifest_path, "dataset manifest")
    if (
        manifest.get("checksum") != DATASET_IDENTITY
        or artifact_identity(manifest, "checksum") != DATASET_IDENTITY
        or manifest.get("training_approval_identity") != TRAINING_APPROVAL_IDENTITY
        or manifest.get("training_contract_identity") != TRAINING_CONTRACT_IDENTITY
        or manifest.get("prepared_manifest_identity")
        != "2389e2b8c97dd719cce0bd98e51b3769e333489d637096b4e40c68bf3826a9ae"
        or manifest.get("approval_status") != "APPROVED"
        or manifest.get("trainingAuthorized") is not True
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise TrainingPipelineError("dataset manifest: exact approved v4 identity required")
    records = _records(manifest_path, manifest)

    gates = config["contractGates"]
    approval = load_document(repository_root / gates["trainingApprovalReference"], "training approval")
    if (
        approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or artifact_identity(approval, "artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or approval.get("requestIdentity")
        != "d052e13698d1d4902a0b5018b2563889d2ea8fb52c55f0112d193efd088f6a9e"
        or approval.get("status") != "APPROVED"
        or approval.get("revocation", {}).get("status") != "ACTIVE"
        or approval.get("authorization", {}).get("externalTrainingAllowed") is not True
        or approval.get("authorization", {}).get("evaluationAllowed") is not False
        or approval.get("authorization", {}).get("promotionAllowed") is not False
        or approval.get("scope", {}).get("includedUseCases") != sorted(INCLUDED_USE_CASES)
        or approval.get("scope", {}).get("frozenEvaluationTrainingUseAllowed") is not False
        or approval.get("scope", {}).get("candidateDispositionAfterTraining") != "CANDIDATE_ONLY"
    ):
        raise TrainingPipelineError("training approval: exact active v4 approval required")

    contract = load_document(repository_root / gates["trainingContractReference"], "training contract")
    if (
        contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or artifact_identity(contract, "artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or contract.get("state") != "PREPARED_AWAITING_TRAINING_APPROVAL"
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("schemaBundle") != "research-assistant-output-v2"
        or contract.get("recordCount") != 144
    ):
        raise TrainingPipelineError("training contract: exact immutable v4 contract required")

    runtime = gates["preparedRuntimeContract"]
    profile_path = repository_root / runtime["assistantProfileReference"]
    schema_path = repository_root / runtime["schemaReference"]
    if (
        not profile_path.is_file()
        or sha256_bytes(profile_path.read_bytes()) != runtime["assistantProfileSha256"]
    ):
        raise TrainingPipelineError("runtime schema: assistant profile checksum mismatch")
    if not schema_path.is_file() or sha256_bytes(schema_path.read_bytes()) != runtime["schemaSha256"]:
        raise TrainingPipelineError("runtime schema: approved training schema checksum mismatch")
    schema_document = load_document(schema_path, "approved training schema")
    if (
        schema_document.get("artifactIdentity")
        != "7e8e436bb27681a6fc49bd752632b418e668da35f860874bbaec337c8a0f637d"
        or artifact_identity(schema_document, "artifactIdentity")
        != schema_document["artifactIdentity"]
        or schema_document.get("runtimeActivationAllowed") is not False
        or schema_document.get("status") != "APPROVED_FOR_DATASET_PREPARATION"
    ):
        raise TrainingPipelineError("runtime schema: exact approved non-active schema required")
    bundle = next(
        (
            item
            for item in schema_document.get("schemas", [])
            if item.get("schemaId") == runtime["schemaBundle"]
            and item.get("assistantKey") == "RESEARCH_ASSISTANT"
        ),
        None,
    )
    if not isinstance(bundle, dict):
        raise TrainingPipelineError("runtime schema: Research v2 bundle missing")
    validator = Draft202012Validator(bundle["schema"])
    for split_records in records.values():
        for record in split_records:
            structured = record["trainingTarget"]["structuredOutput"]
            if structured is not None and not validator.is_valid(structured):
                raise TrainingPipelineError("runtime schema: structured output mismatch")

    return {
        "state": "PASS",
        "counts": {split: len(values) for split, values in records.items()},
        "contentIdsDisjoint": True,
        "contractHoldout": {
            "state": "PASS",
            "split": "evaluation",
            "recordCount": len(records["evaluation"]),
            "usedForOptimization": False,
        },
        "preparedRuntimeContract": {
            "state": "PASS",
            "schemaBundle": runtime["schemaBundle"],
            "runtimeActivationAllowed": False,
        },
        "baselineStabilityControls": {
            "state": "PASS",
            "gradientScalerInitialScale": GRADIENT_SCALER_INITIAL_SCALE,
            "freshBaseModelStartRequired": True,
        },
    }


BASE.validate_dataset_and_contract_gates = validate_dataset_and_contract_gates
run_pipeline = BASE.run_pipeline
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
