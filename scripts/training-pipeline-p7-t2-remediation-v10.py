#!/usr/bin/env python3
"""Run governed P7-T2 targeted warm-start remediation v10 training."""
from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


V9_PATH = Path(__file__).with_name("training-pipeline-p7-t2-remediation-v9.py")
BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation-v10.py")
SCHEMA_VERSION = PIPELINE_VERSION = "10.0.0"
DATASET_IDENTITY = "abce232c1721788bae5a1686f9d017f295a6892555193140ae74c5a044e0a409"
TRAINING_APPROVAL_IDENTITY = "fc9fd2b0d53ae50ce7568abdb2894f7d70c81b6e7e46d7ea65c5e333c480c553"
TRAINING_CONTRACT_IDENTITY = "3b924bb766dd6936c7e9aa5d0387c928856b9582e6283b06dbf5d36e2f3fa042"
TRAINING_REQUEST_IDENTITY = "1cc572882386fc684e66df553dcd381303820852f8b5fc3af396e0f7171a0c91"
PREPARED_MANIFEST_IDENTITY = "fbde87e52ecdfcab485db5f89c257614314111566e5bcaaf4f1a6af91d1516c0"
DATASET_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v10/manifest.approved.json"
PARENT_ADAPTER_IDENTITY = "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
PARENT_CANDIDATE_ID = "99596dcf3c00c6a25a4c38f01de7f12d917100e539cf4ea777556ef0c0a055c2"
PARENT_TRAINING_RUN_IDENTITY = "9ee7cd3cb56cb5b18c096ce8764fe14a23d69bdd39ef027a8f1472710b152314"
REPLAY_CASES = ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-004", "E-FUNC-RESEARCH-005", "E-HUMAN-003", "E-HUMAN-004", "E-INJECT-001", "E-INJECT-002", "E-INJECT-003", "E-ROUTE-002", "E-STRUCT-003", "E-STRUCT-004"]


def _load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None: raise RuntimeError(path)
    module = importlib.util.module_from_spec(spec)
    previous = sys.dont_write_bytecode; sys.dont_write_bytecode = True
    try: spec.loader.exec_module(module)
    finally: sys.dont_write_bytecode = previous
    return module


V9 = _load("p7_t2_pipeline_v9_for_v10", V9_PATH)
BACKEND = _load("p7_t2_backend_for_v10_pipeline", BACKEND_PATH)
BASE = V9.BASE
ROOT = V9.ROOT
TrainingPipelineError = V9.TrainingPipelineError
artifact_identity = V9.artifact_identity
load_document = V9.load_document
for name, value in {"BACKEND_PATH": BACKEND_PATH, "SCHEMA_VERSION": SCHEMA_VERSION, "PIPELINE_VERSION": PIPELINE_VERSION, "DATASET_IDENTITY": DATASET_IDENTITY, "TRAINING_APPROVAL_IDENTITY": TRAINING_APPROVAL_IDENTITY, "TRAINING_CONTRACT_IDENTITY": TRAINING_CONTRACT_IDENTITY, "DATASET_MANIFEST_REFERENCE": DATASET_MANIFEST_REFERENCE}.items():
    setattr(BASE, name, value)


def validate_training_config(config: object) -> None:
    if not isinstance(config, dict) or set(config) != {"schemaVersion", "pipelineVersion", "assistantKey", "baseModel", "adapter", "continuation", "seed", "dataset", "splits", "training", "contractGates", "output"}:
        raise TrainingPipelineError("config: exact v10 continuation fields required")
    continuation, training = config["continuation"], config["training"]
    if continuation != {
        "method": "QLORA_ADAPTER_CONTINUATION", "parentAdapterReference": "parent-adapter",
        "parentAdapterIdentity": PARENT_ADAPTER_IDENTITY, "parentCandidateId": PARENT_CANDIDATE_ID,
        "parentTrainingRunIdentity": PARENT_TRAINING_RUN_IDENTITY,
        "freshBaseModelLoadRequired": True, "freshAdapterInitializationRequired": False,
    }:
        raise TrainingPipelineError("config/continuation: exact v9 parent binding required")
    if training.get("maxSteps") != 48 or training.get("learningRate") > 2e-5 or training.get("earlyStoppingPatience") != 1:
        raise TrainingPipelineError("config/training: approved v10 limits required")
    shadow = copy.deepcopy(config); shadow.pop("continuation"); shadow["training"]["maxSteps"] = None
    V9.validate_training_config(shadow)


def _load_records(path: Path, expected: dict[str, Any]) -> list[dict[str, Any]]:
    if not path.is_file() or V9.sha256_bytes(path.read_bytes()) != expected.get("sha256"):
        raise TrainingPipelineError(f"dataset/{path.name}: checksum mismatch")
    try:
        values = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    except (UnicodeError, json.JSONDecodeError) as error:
        raise TrainingPipelineError(f"dataset/{path.name}: invalid JSONL: {error}") from error
    if len(values) != expected.get("recordCount") or not values:
        raise TrainingPipelineError(f"dataset/{path.name}: record count mismatch")
    for record in values:
        try: BACKEND.training_messages(record)
        except ValueError as error: raise TrainingPipelineError(f"dataset/{path.name}: {error}") from error
    return values


def validate_dataset_and_contract_gates(manifest_path: Path, config: dict[str, Any], repository_root: Path) -> dict[str, Any]:
    validate_training_config(config)
    manifest = load_document(manifest_path, "dataset manifest")
    if (
        manifest.get("datasetIdentity") != DATASET_IDENTITY or manifest.get("artifactIdentity") != artifact_identity(manifest, "artifactIdentity")
        or manifest.get("preparedManifestIdentity") != PREPARED_MANIFEST_IDENTITY
        or manifest.get("trainingApprovalIdentity") != TRAINING_APPROVAL_IDENTITY
        or manifest.get("trainingContractIdentity") != TRAINING_CONTRACT_IDENTITY
        or manifest.get("approval_status") != "APPROVED" or manifest.get("trainingAuthorized") is not True
        or manifest.get("recordCounts") != {"evaluation": 8, "train": 96, "validation": 20}
        or manifest.get("targetedAdditionCounts") != {"evaluation": 8, "train": 48, "validation": 8}
        or manifest.get("replayGuardCounts") != {"evaluation": 0, "train": 48, "validation": 12}
        or manifest.get("targetedEvaluationCaseIds") != ["E-FUNC-RESEARCH-006"]
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
        or manifest.get("contractHoldout", {}).get("usedForEarlyStopping") is not False
    ):
        raise TrainingPipelineError("dataset manifest: exact approved v10 identity required")
    records = V9.V8.V7.V6._records(manifest_path, manifest)
    if {name: len(items) for name, items in records.items()} != {"train": 96, "validation": 20, "evaluation": 8}:
        raise TrainingPipelineError("dataset: exact v10 split counts required")
    for values in records.values():
        for record in values:
            try: BACKEND.training_messages(record)
            except ValueError as error: raise TrainingPipelineError(str(error)) from error

    gates = config["contractGates"]
    approval = load_document(repository_root / gates["trainingApprovalReference"], "training approval")
    authorization, scope = approval.get("authorization", {}), approval.get("scope", {})
    if (
        approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY or artifact_identity(approval, "artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or approval.get("requestIdentity") != TRAINING_REQUEST_IDENTITY or approval.get("datasetIdentity") != DATASET_IDENTITY
        or approval.get("status") != "APPROVED" or approval.get("revocation", {}).get("status") != "ACTIVE"
        or authorization.get("externalTrainingAllowed") is not True or authorization.get("evaluationAllowed") is not False or authorization.get("promotionAllowed") is not False
        or scope.get("trainingMethod") != "QLORA_ADAPTER_CONTINUATION" or scope.get("parentAdapterIdentity") != PARENT_ADAPTER_IDENTITY
        or scope.get("parentCandidateId") != PARENT_CANDIDATE_ID or scope.get("parentTrainingRunIdentity") != PARENT_TRAINING_RUN_IDENTITY
        or scope.get("maximumSteps") != 48 or scope.get("learningRateMaximum") > 2e-5 or scope.get("earlyStoppingPatience") != 1
        or scope.get("replayGuardCaseIds") != REPLAY_CASES or scope.get("contractHoldoutUsedForOptimization") is not False
        or scope.get("contractHoldoutUsedForEarlyStopping") is not False or scope.get("candidateDispositionAfterTraining") != "CANDIDATE_ONLY"
    ):
        raise TrainingPipelineError("training approval: exact active v10 continuation approval required")
    contract = load_document(repository_root / gates["trainingContractReference"], "training contract")
    if contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY or artifact_identity(contract, "artifactIdentity") != TRAINING_CONTRACT_IDENTITY or contract.get("datasetIdentity") != DATASET_IDENTITY or contract.get("parentAdapterIdentity") != PARENT_ADAPTER_IDENTITY or contract.get("maximumSteps") != 48 or contract.get("contractHoldoutUseForOptimization") is not False or contract.get("contractHoldoutUseForEarlyStopping") is not False:
        raise TrainingPipelineError("training contract: exact immutable v10 continuation contract required")
    runtime = gates["preparedRuntimeContract"]
    evaluator_approval = load_document(repository_root / runtime["evaluatorSuiteApprovalReference"], "evaluator suite approval")
    evaluator_contract = load_document(repository_root / runtime["evaluatorContractReference"], "evaluator contract")
    suite = load_document(repository_root / runtime["evaluationSuiteReference"], "evaluation suite")
    profile = load_document(repository_root / runtime["promptProfileReference"], "prompt profile")
    if (
        evaluator_approval.get("artifactIdentity") != V9.V8.V7.V6.EVALUATOR_SUITE_APPROVAL_IDENTITY
        or evaluator_approval.get("revocation", {}).get("status") != "ACTIVE"
        or evaluator_contract.get("artifactIdentity") != V9.V8.V7.V6.EVALUATOR_IDENTITY
        or suite.get("suiteDigest") != V9.V8.V7.V6.EVALUATION_SUITE_IDENTITY
        or suite.get("TRAINING_PROHIBITED") is not True or suite.get("externalExecutionAllowed") is not False
        or profile.get("artifactIdentity") != V9.PROMPT_PROFILE_IDENTITY or profile.get("activationAllowed") is not False
        or runtime.get("runtimeNormalizationAllowed") is not False or runtime.get("constrainedDecodingAllowed") is not False
    ):
        raise TrainingPipelineError("runtime contracts: exact v2/v3 fail-closed bindings required")
    return {
        "state": "PASS", "counts": {name: len(items) for name, items in records.items()},
        "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"], "replayGuardCaseIds": REPLAY_CASES,
        "contentIdsDisjoint": True,
        "contractHoldout": {"state": "PASS", "split": "evaluation", "recordCount": 8, "usedForOptimization": False, "usedForEarlyStopping": False},
        "warmStart": {"state": "PASS", "method": "QLORA_ADAPTER_CONTINUATION", "parentAdapterIdentity": PARENT_ADAPTER_IDENTITY, "parentCandidateId": PARENT_CANDIDATE_ID, "freshBaseModelLoadRequired": True},
        "runtimeControls": {"runtimeNormalizationAllowed": False, "constrainedDecodingAllowed": False},
    }


BASE.validate_training_config = validate_training_config
BASE._load_records = _load_records
BASE.validate_dataset_and_contract_gates = validate_dataset_and_contract_gates
training_config_identity = BASE.training_config_identity
training_run_identity = BASE.training_run_identity
run_pipeline = BASE.run_pipeline
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
