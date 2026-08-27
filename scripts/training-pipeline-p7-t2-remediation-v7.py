#!/usr/bin/env python3
"""Run governed P7-T2 Research retention-first remediation v7 training."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


V6_PIPELINE_PATH = Path(__file__).with_name("training-pipeline-p7-t2-remediation-v6.py")
BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation-v7.py")
SCHEMA_VERSION = "7.0.0"
PIPELINE_VERSION = "7.0.0"
DATASET_IDENTITY = "b973bf0a387b35b84140a2db1d2bbf7c5c0d7f054102a968c10bd1df6faeff23"
TRAINING_APPROVAL_IDENTITY = "627f4043fe2c2dcaff21cb0b38d923752d8c928ef8d184b7535f3fe07b2bb3f5"
TRAINING_CONTRACT_IDENTITY = "81be449d83a56303f42e55128f7d6ea4cd79fc606616334d3387a52fe47b8b8d"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
DATASET_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v7/manifest.approved.json"
TRAINING_REQUEST_IDENTITY = "11946dfb19433e42b55d7d9c3b7e815ad10f0ca45861348003eb0508f62e01cc"
PREPARED_MANIFEST_IDENTITY = "eca1dcfe4113019d00288e568b6ce0780b3295394151ce7f5207cc973dd337e0"
RECORD_SCHEMA_VERSIONS = {"6.0.0", "7.0.0"}
SPLIT_COUNTS = {"train": 384, "validation": 64, "evaluation": 64}
RETAINED_COUNTS = {"train": 288, "validation": 48, "evaluation": 48}
TARGETED_COUNTS = {"train": 96, "validation": 16, "evaluation": 16}


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


V6 = _load_module("p7_t2_remediation_pipeline_v6_for_v7", V6_PIPELINE_PATH)
BASE = V6.BASE
ROOT = V6.ROOT
TrainingPipelineError = V6.TrainingPipelineError
canonical_bytes = V6.canonical_bytes
sha256_bytes = V6.sha256_bytes
artifact_identity = V6.artifact_identity
load_document = V6.load_document

for name, value in {
    "BACKEND_PATH": BACKEND_PATH,
    "SCHEMA_VERSION": SCHEMA_VERSION,
    "PIPELINE_VERSION": PIPELINE_VERSION,
    "DATASET_IDENTITY": DATASET_IDENTITY,
    "TRAINING_APPROVAL_IDENTITY": TRAINING_APPROVAL_IDENTITY,
    "TRAINING_CONTRACT_IDENTITY": TRAINING_CONTRACT_IDENTITY,
    "DATASET_MANIFEST_REFERENCE": DATASET_MANIFEST_REFERENCE,
}.items():
    setattr(BASE, name, value)

validate_training_config = BASE.validate_training_config
training_config_identity = BASE.training_config_identity
training_run_identity = BASE.training_run_identity


def _load_records(path: Path, expected: dict[str, Any]) -> list[dict[str, Any]]:
    if not path.is_file() or sha256_bytes(path.read_bytes()) != expected.get("sha256"):
        raise TrainingPipelineError(f"dataset/{path.name}: checksum mismatch")
    try:
        values = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    except (UnicodeError, json.JSONDecodeError) as error:
        raise TrainingPipelineError(f"dataset/{path.name}: invalid JSONL: {error}") from error
    if len(values) != expected.get("recordCount") or not values:
        raise TrainingPipelineError(f"dataset/{path.name}: record count mismatch")
    for record in values:
        if not isinstance(record, dict) or set(record) != V6.RECORD_FIELDS:
            raise TrainingPipelineError(f"dataset/{path.name}: exact training record fields required")
        if (
            record.get("schemaVersion") not in RECORD_SCHEMA_VERSIONS
            or record.get("assistantKey") != "RESEARCH_ASSISTANT"
            or record.get("domain") != "RESEARCH"
            or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
            or record.get("useCaseId") not in V6.INCLUDED_USE_CASES
            or not isinstance(record.get("trainingPrompt"), dict)
            or not isinstance(record.get("trainingTarget"), dict)
            or set(record["trainingTarget"]) != BASE.OUTPUT_FIELDS
            or record["trainingTarget"].get("evalCaseId")
            != record["trainingPrompt"].get("evalCaseId")
            or record["trainingPrompt"].get("useCaseId") != record["useCaseId"]
        ):
            raise TrainingPipelineError(
                f"dataset/{path.name}: governed retention-first prompt/target contract mismatch"
            )
    return values


BASE._load_records = _load_records


def validate_dataset_and_contract_gates(
    manifest_path: Path, config: dict[str, Any], repository_root: Path
) -> dict[str, Any]:
    validate_training_config(config)
    manifest = load_document(manifest_path, "dataset manifest")
    if (
        manifest.get("datasetIdentity") != DATASET_IDENTITY
        or manifest.get("artifactIdentity") != artifact_identity(manifest, "artifactIdentity")
        or manifest.get("preparedManifestIdentity") != PREPARED_MANIFEST_IDENTITY
        or manifest.get("trainingApprovalIdentity") != TRAINING_APPROVAL_IDENTITY
        or manifest.get("trainingContractIdentity") != TRAINING_CONTRACT_IDENTITY
        or manifest.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or manifest.get("approval_status") != "APPROVED"
        or manifest.get("trainingAuthorized") is not True
        or manifest.get("recordCounts") != {"evaluation": 64, "train": 384, "validation": 64}
        or manifest.get("retainedRecordCounts")
        != {"evaluation": 48, "train": 288, "validation": 48}
        or manifest.get("targetedAdditionCounts")
        != {"evaluation": 16, "train": 96, "validation": 16}
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise TrainingPipelineError("dataset manifest: exact approved retention-first v7 identity required")
    records = V6._records(manifest_path, manifest)
    if {name: len(items) for name, items in records.items()} != SPLIT_COUNTS:
        raise TrainingPipelineError("dataset: exact v7 split counts required")
    for split_records in records.values():
        for record in split_records:
            V6._validate_record_contract(record)

    gates = config["contractGates"]
    approval = load_document(
        repository_root / gates["trainingApprovalReference"], "training approval"
    )
    authorization = approval.get("authorization", {})
    scope = approval.get("scope", {})
    if (
        approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or artifact_identity(approval, "artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or approval.get("requestIdentity") != TRAINING_REQUEST_IDENTITY
        or approval.get("datasetIdentity") != DATASET_IDENTITY
        or approval.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or approval.get("status") != "APPROVED"
        or approval.get("revocation", {}).get("status") != "ACTIVE"
        or authorization.get("externalTrainingAllowed") is not True
        or authorization.get("evaluationAllowed") is not False
        or authorization.get("promotionAllowed") is not False
        or authorization.get("runtimeNormalizationAllowed") is not False
        or authorization.get("constrainedDecodingAllowed") is not False
        or scope.get("includedUseCases") != sorted(V6.INCLUDED_USE_CASES)
        or scope.get("targetedUseCases") != ["RESEARCH_UC_004", "RESEARCH_UC_005"]
        or scope.get("retainedV6RecordsUnchanged") is not True
        or scope.get("frozenEvaluationTrainingUseAllowed") is not False
        or scope.get("freshBaseModelStartRequired") is not True
        or scope.get("candidateDispositionAfterTraining") != "CANDIDATE_ONLY"
    ):
        raise TrainingPipelineError("training approval: exact active retention-first v7 approval required")

    contract = load_document(
        repository_root / gates["trainingContractReference"], "training contract"
    )
    if (
        contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or artifact_identity(contract, "artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or contract.get("datasetIdentity") != DATASET_IDENTITY
        or contract.get("baseDatasetIdentity") != V6.DATASET_IDENTITY
        or contract.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or contract.get("trainingRecordCount") != 384
        or contract.get("validationRecordCount") != 64
        or contract.get("evaluationRecordCount") != 64
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("runtimeNormalizationAllowed") is not False
    ):
        raise TrainingPipelineError("training contract: exact immutable v7 contract required")

    runtime = gates["preparedRuntimeContract"]
    evaluator_approval = load_document(
        repository_root / runtime["evaluatorSuiteApprovalReference"],
        "evaluator suite approval",
    )
    evaluator_contract = load_document(
        repository_root / runtime["evaluatorContractReference"], "evaluator contract"
    )
    suite = load_document(
        repository_root / runtime["evaluationSuiteReference"], "evaluation suite"
    )
    prompt_profile = load_document(
        repository_root / runtime["promptProfileReference"], "prompt profile"
    )
    if (
        evaluator_approval.get("artifactIdentity") != V6.EVALUATOR_SUITE_APPROVAL_IDENTITY
        or artifact_identity(evaluator_approval, "artifactIdentity")
        != V6.EVALUATOR_SUITE_APPROVAL_IDENTITY
        or evaluator_approval.get("revocation", {}).get("status") != "ACTIVE"
        or evaluator_contract.get("artifactIdentity") != V6.EVALUATOR_IDENTITY
        or artifact_identity(evaluator_contract, "artifactIdentity") != V6.EVALUATOR_IDENTITY
        or evaluator_contract.get("evaluatorVersion") != "2.0.0"
        or suite.get("suiteDigest") != V6.EVALUATION_SUITE_IDENTITY
        or suite.get("suiteVersion") != "2.0.0"
        or suite.get("TRAINING_PROHIBITED") is not True
        or suite.get("externalExecutionAllowed") is not False
        or prompt_profile.get("artifactIdentity") != PROMPT_PROFILE_IDENTITY
        or artifact_identity(prompt_profile, "artifactIdentity") != PROMPT_PROFILE_IDENTITY
        or prompt_profile.get("profileVersion") != "3.0.0"
        or prompt_profile.get("activationAllowed") is not False
        or runtime.get("runtimeNormalizationAllowed") is not False
        or runtime.get("constrainedDecodingAllowed") is not False
    ):
        raise TrainingPipelineError("runtime contracts: exact approved v2/v3 bindings required")

    return {
        "state": "PASS",
        "counts": {split: len(values) for split, values in records.items()},
        "retention": {
            "state": "PASS",
            "retainedRecordCounts": RETAINED_COUNTS,
            "targetedAdditionCounts": TARGETED_COUNTS,
        },
        "contentIdsDisjoint": True,
        "contractHoldout": {
            "state": "PASS",
            "split": "evaluation",
            "recordCount": len(records["evaluation"]),
            "usedForOptimization": False,
        },
        "evaluatorContract": {"state": "PASS", "version": "2.0.0"},
        "evaluationSuite": {"state": "BOUND_NOT_EXECUTED", "version": "2.0.0"},
        "promptProfile": {
            "state": "BOUND_FOR_TRAINING_NOT_ACTIVATED",
            "version": "3.0.0",
            "identity": PROMPT_PROFILE_IDENTITY,
        },
        "runtimeControls": {
            "runtimeNormalizationAllowed": False,
            "constrainedDecodingAllowed": False,
        },
        "baselineStabilityControls": {
            "state": "PASS",
            "gradientScalerInitialScale": V6.GRADIENT_SCALER_INITIAL_SCALE,
            "freshBaseModelStartRequired": True,
            "terminalSupervisedEosRequired": True,
        },
    }


BASE.validate_dataset_and_contract_gates = validate_dataset_and_contract_gates
run_pipeline = BASE.run_pipeline
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
