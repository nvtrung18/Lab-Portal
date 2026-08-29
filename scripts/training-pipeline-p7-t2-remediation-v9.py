#!/usr/bin/env python3
"""Run governed P7-T2 report-review remediation v9 training."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


V8_PIPELINE_PATH = Path(__file__).with_name("training-pipeline-p7-t2-remediation-v8.py")
BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation-v9.py")
SCHEMA_VERSION = PIPELINE_VERSION = "9.0.0"
DATASET_IDENTITY = "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
TRAINING_APPROVAL_IDENTITY = "b3402cab5c4dfbe7d30c3bace1debbe28932da04a9c6f539f85a2865b58add34"
TRAINING_CONTRACT_IDENTITY = "4d3e2f9685afffb8b1ac26f33e140251e17df4924c041dab6a2a2600255a715c"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
TRAINING_REQUEST_IDENTITY = "bdd4227a12c9695071ce397ff19e20eb7b9972dadd70bf9d7cb2952c90d73c63"
PREPARED_MANIFEST_IDENTITY = "a117c87b8cb6e6799a4df3dc190deebda4cca2e514af2ac389414e778e35c21c"
BASE_DATASET_IDENTITY = "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
DATASET_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v9/manifest.approved.json"
SPLIT_COUNTS = {"train": 480, "validation": 80, "evaluation": 80}
RETAINED_COUNTS = {"train": 432, "validation": 72, "evaluation": 72}
TARGETED_COUNTS = {"train": 48, "validation": 8, "evaluation": 8}


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


V8 = _load_module("p7_t2_remediation_pipeline_v8_for_v9", V8_PIPELINE_PATH)
BACKEND = _load_module("p7_t2_remediation_backend_for_v9_pipeline", BACKEND_PATH)
BASE = V8.BASE
ROOT = V8.ROOT
TrainingPipelineError = V8.TrainingPipelineError
artifact_identity = V8.artifact_identity
load_document = V8.load_document
sha256_bytes = V8.sha256_bytes

for name, value in {
    "BACKEND_PATH": BACKEND_PATH, "SCHEMA_VERSION": SCHEMA_VERSION,
    "PIPELINE_VERSION": PIPELINE_VERSION, "DATASET_IDENTITY": DATASET_IDENTITY,
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
        try:
            BACKEND.training_messages(record)
        except ValueError as error:
            raise TrainingPipelineError(f"dataset/{path.name}: {error}") from error
    return values


BASE._load_records = _load_records


def validate_dataset_and_contract_gates(manifest_path: Path, config: dict[str, Any], repository_root: Path) -> dict[str, Any]:
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
        or manifest.get("recordCounts") != {"evaluation": 80, "train": 480, "validation": 80}
        or manifest.get("retainedRecordCounts") != {"evaluation": 72, "train": 432, "validation": 72}
        or manifest.get("targetedAdditionCounts") != {"evaluation": 8, "train": 48, "validation": 8}
        or manifest.get("targetedEvaluationCaseIds") != ["E-FUNC-RESEARCH-006"]
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise TrainingPipelineError("dataset manifest: exact approved v9 identity required")
    records = V8.V7.V6._records(manifest_path, manifest)
    if {name: len(items) for name, items in records.items()} != SPLIT_COUNTS:
        raise TrainingPipelineError("dataset: exact v9 split counts required")
    for split_records in records.values():
        for record in split_records:
            try:
                BACKEND.training_messages(record)
            except ValueError as error:
                raise TrainingPipelineError(str(error)) from error
            target, prompt = record["trainingTarget"], record["trainingPrompt"]
            findings = V8.V7.V6.EVALUATOR.validate_tool(target.get("toolRequest"))
            findings += V8.V7.V6.EVALUATOR.validate_response(target.get("response"))
            findings += V8.V7.V6.EVALUATOR.validate_output(target.get("structuredOutput"), prompt.get("structuredOutputContract"))
            if findings:
                raise TrainingPipelineError("evaluator v2: " + "; ".join(findings))

    gates = config["contractGates"]
    approval = load_document(repository_root / gates["trainingApprovalReference"], "training approval")
    authorization, scope = approval.get("authorization", {}), approval.get("scope", {})
    if (
        approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or artifact_identity(approval, "artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or approval.get("requestIdentity") != TRAINING_REQUEST_IDENTITY
        or approval.get("datasetIdentity") != DATASET_IDENTITY
        or approval.get("status") != "APPROVED" or approval.get("revocation", {}).get("status") != "ACTIVE"
        or authorization.get("externalTrainingAllowed") is not True
        or authorization.get("evaluationAllowed") is not False
        or authorization.get("promotionAllowed") is not False
        or authorization.get("runtimeNormalizationAllowed") is not False
        or authorization.get("constrainedDecodingAllowed") is not False
        or scope.get("targetedEvaluationCaseIds") != ["E-FUNC-RESEARCH-006"]
        or scope.get("targetedUseCases") != ["RESEARCH_UC_006"]
        or scope.get("retainedV8RecordsUnchanged") is not True
        or scope.get("frozenEvaluationTrainingUseAllowed") is not False
        or scope.get("freshBaseModelStartRequired") is not True
        or scope.get("candidateDispositionAfterTraining") != "CANDIDATE_ONLY"
    ):
        raise TrainingPipelineError("training approval: exact active v9 approval required")
    contract = load_document(repository_root / gates["trainingContractReference"], "training contract")
    if (
        contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or artifact_identity(contract, "artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or contract.get("datasetIdentity") != DATASET_IDENTITY
        or contract.get("baseDatasetIdentity") != BASE_DATASET_IDENTITY
        or contract.get("trainingRecordCount") != 480 or contract.get("validationRecordCount") != 80 or contract.get("evaluationRecordCount") != 80
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("runtimeNormalizationAllowed") is not False
        or contract.get("constrainedDecodingAllowed") is not False
    ):
        raise TrainingPipelineError("training contract: exact immutable v9 contract required")
    runtime = gates["preparedRuntimeContract"]
    evaluator_approval = load_document(repository_root / runtime["evaluatorSuiteApprovalReference"], "evaluator suite approval")
    evaluator_contract = load_document(repository_root / runtime["evaluatorContractReference"], "evaluator contract")
    suite = load_document(repository_root / runtime["evaluationSuiteReference"], "evaluation suite")
    profile = load_document(repository_root / runtime["promptProfileReference"], "prompt profile")
    if (
        evaluator_approval.get("artifactIdentity") != V8.V7.V6.EVALUATOR_SUITE_APPROVAL_IDENTITY
        or evaluator_approval.get("revocation", {}).get("status") != "ACTIVE"
        or evaluator_contract.get("artifactIdentity") != V8.V7.V6.EVALUATOR_IDENTITY
        or suite.get("suiteDigest") != V8.V7.V6.EVALUATION_SUITE_IDENTITY
        or suite.get("TRAINING_PROHIBITED") is not True or suite.get("externalExecutionAllowed") is not False
        or profile.get("artifactIdentity") != PROMPT_PROFILE_IDENTITY or profile.get("activationAllowed") is not False
        or runtime.get("runtimeNormalizationAllowed") is not False or runtime.get("constrainedDecodingAllowed") is not False
    ):
        raise TrainingPipelineError("runtime contracts: exact v2/v3 bindings required")
    return {
        "state": "PASS", "counts": {split: len(values) for split, values in records.items()},
        "retention": {"state": "PASS", "retainedRecordCounts": RETAINED_COUNTS, "targetedAdditionCounts": TARGETED_COUNTS},
        "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"], "contentIdsDisjoint": True,
        "contractHoldout": {"state": "PASS", "split": "evaluation", "recordCount": 80, "usedForOptimization": False},
        "evaluatorContract": {"state": "PASS", "version": "2.0.0"},
        "evaluationSuite": {"state": "BOUND_NOT_EXECUTED", "version": "2.0.0"},
        "promptProfile": {"state": "BOUND_FOR_TRAINING_NOT_ACTIVATED", "version": "3.0.0", "identity": PROMPT_PROFILE_IDENTITY},
        "runtimeControls": {"runtimeNormalizationAllowed": False, "constrainedDecodingAllowed": False},
        "baselineStabilityControls": {"state": "PASS", "gradientScalerInitialScale": V8.V7.V6.GRADIENT_SCALER_INITIAL_SCALE, "freshBaseModelStartRequired": True, "terminalSupervisedEosRequired": True},
    }


BASE.validate_dataset_and_contract_gates = validate_dataset_and_contract_gates
run_pipeline = BASE.run_pipeline
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
