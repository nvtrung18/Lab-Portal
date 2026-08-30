#!/usr/bin/env python3
"""Run governed P7-T2 single-failure remediation v8 training."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


V7_PIPELINE_PATH = Path(__file__).with_name("training-pipeline-p7-t2-remediation-v7.py")
BACKEND_PATH = Path(__file__).with_name("p7-t2-real-training-remediation-v8.py")
SCHEMA_VERSION = "8.0.0"
PIPELINE_VERSION = "8.0.0"
DATASET_IDENTITY = "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
TRAINING_APPROVAL_IDENTITY = "bdcc8337530fa85cde395977c7e8f76f84ddd63c67b8b282be9e903b6c2276d8"
TRAINING_CONTRACT_IDENTITY = "cd6a1edd2283ad5c584ee2e376bab1b81a8c841e1205e730c441fa9f7c9962f2"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
DATASET_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v8/manifest.approved.json"
TRAINING_REQUEST_IDENTITY = "e486eb298d44ec2bc1c7db767a9ce4222c65084856090b9ba8e6f04bad6368d0"
PREPARED_MANIFEST_IDENTITY = "6946a9600538f496beb0411fc16cd0165e81f7ed2f96bb5247fb4889e6014880"
V7_DATASET_IDENTITY = "b973bf0a387b35b84140a2db1d2bbf7c5c0d7f054102a968c10bd1df6faeff23"
SPLIT_COUNTS = {"train": 432, "validation": 72, "evaluation": 72}
RETAINED_COUNTS = {"train": 384, "validation": 64, "evaluation": 64}
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


V7 = _load_module("p7_t2_remediation_pipeline_v7_for_v8", V7_PIPELINE_PATH)
BACKEND = _load_module("p7_t2_remediation_backend_for_v8_pipeline", BACKEND_PATH)
BASE = V7.BASE
ROOT = V7.ROOT
TrainingPipelineError = V7.TrainingPipelineError
canonical_bytes = V7.canonical_bytes
sha256_bytes = V7.sha256_bytes
artifact_identity = V7.artifact_identity
load_document = V7.load_document

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
        values = [
            json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
        ]
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


def validate_dataset_and_contract_gates(
    manifest_path: Path, config: dict[str, Any], repository_root: Path
) -> dict[str, Any]:
    validate_training_config(config)
    manifest = load_document(manifest_path, "dataset manifest")
    if (
        manifest.get("datasetIdentity") != DATASET_IDENTITY
        or manifest.get("artifactIdentity")
        != artifact_identity(manifest, "artifactIdentity")
        or manifest.get("preparedManifestIdentity") != PREPARED_MANIFEST_IDENTITY
        or manifest.get("trainingApprovalIdentity") != TRAINING_APPROVAL_IDENTITY
        or manifest.get("trainingContractIdentity") != TRAINING_CONTRACT_IDENTITY
        or manifest.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or manifest.get("approval_status") != "APPROVED"
        or manifest.get("trainingAuthorized") is not True
        or manifest.get("recordCounts")
        != {"evaluation": 72, "train": 432, "validation": 72}
        or manifest.get("retainedRecordCounts")
        != {"evaluation": 64, "train": 384, "validation": 64}
        or manifest.get("targetedAdditionCounts")
        != {"evaluation": 8, "train": 48, "validation": 8}
        or manifest.get("targetedEvaluationCaseIds") != ["E-INJECT-001"]
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise TrainingPipelineError("dataset manifest: exact approved v8 identity required")
    records = V7.V6._records(manifest_path, manifest)
    if {name: len(items) for name, items in records.items()} != SPLIT_COUNTS:
        raise TrainingPipelineError("dataset: exact v8 split counts required")
    for split_records in records.values():
        for record in split_records:
            try:
                BACKEND.training_messages(record)
            except ValueError as error:
                raise TrainingPipelineError(str(error)) from error
            target = record["trainingTarget"]
            prompt = record["trainingPrompt"]
            findings = []
            findings.extend(V7.V6.EVALUATOR.validate_tool(target.get("toolRequest")))
            findings.extend(V7.V6.EVALUATOR.validate_response(target.get("response")))
            findings.extend(
                V7.V6.EVALUATOR.validate_output(
                    target.get("structuredOutput"),
                    prompt.get("structuredOutputContract"),
                )
            )
            if findings:
                raise TrainingPipelineError("evaluator v2: " + "; ".join(findings))

    gates = config["contractGates"]
    approval = load_document(
        repository_root / gates["trainingApprovalReference"], "training approval"
    )
    authorization = approval.get("authorization", {})
    scope = approval.get("scope", {})
    if (
        approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or artifact_identity(approval, "artifactIdentity")
        != TRAINING_APPROVAL_IDENTITY
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
        or scope.get("targetedEvaluationCaseIds") != ["E-INJECT-001"]
        or scope.get("targetedUseCases") != ["RESEARCH_UC_001"]
        or scope.get("retainedV7RecordsUnchanged") is not True
        or scope.get("frozenEvaluationTrainingUseAllowed") is not False
        or scope.get("freshBaseModelStartRequired") is not True
        or scope.get("candidateDispositionAfterTraining") != "CANDIDATE_ONLY"
    ):
        raise TrainingPipelineError("training approval: exact active v8 approval required")

    contract = load_document(
        repository_root / gates["trainingContractReference"], "training contract"
    )
    if (
        contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or artifact_identity(contract, "artifactIdentity")
        != TRAINING_CONTRACT_IDENTITY
        or contract.get("datasetIdentity") != DATASET_IDENTITY
        or contract.get("baseDatasetIdentity") != V7_DATASET_IDENTITY
        or contract.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or contract.get("trainingRecordCount") != 432
        or contract.get("validationRecordCount") != 72
        or contract.get("evaluationRecordCount") != 72
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("runtimeNormalizationAllowed") is not False
        or contract.get("constrainedDecodingAllowed") is not False
    ):
        raise TrainingPipelineError("training contract: exact immutable v8 contract required")

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
        evaluator_approval.get("artifactIdentity")
        != V7.V6.EVALUATOR_SUITE_APPROVAL_IDENTITY
        or evaluator_approval.get("revocation", {}).get("status") != "ACTIVE"
        or evaluator_contract.get("artifactIdentity") != V7.V6.EVALUATOR_IDENTITY
        or evaluator_contract.get("evaluatorVersion") != "2.0.0"
        or suite.get("suiteDigest") != V7.V6.EVALUATION_SUITE_IDENTITY
        or suite.get("suiteVersion") != "2.0.0"
        or suite.get("TRAINING_PROHIBITED") is not True
        or suite.get("externalExecutionAllowed") is not False
        or prompt_profile.get("artifactIdentity") != PROMPT_PROFILE_IDENTITY
        or prompt_profile.get("profileVersion") != "3.0.0"
        or prompt_profile.get("activationAllowed") is not False
        or runtime.get("runtimeNormalizationAllowed") is not False
        or runtime.get("constrainedDecodingAllowed") is not False
    ):
        raise TrainingPipelineError("runtime contracts: exact v2/v3 bindings required")

    return {
        "state": "PASS",
        "counts": {split: len(values) for split, values in records.items()},
        "retention": {
            "state": "PASS",
            "retainedRecordCounts": RETAINED_COUNTS,
            "targetedAdditionCounts": TARGETED_COUNTS,
        },
        "targetedEvaluationCaseIds": ["E-INJECT-001"],
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
            "gradientScalerInitialScale": V7.V6.GRADIENT_SCALER_INITIAL_SCALE,
            "freshBaseModelStartRequired": True,
            "terminalSupervisedEosRequired": True,
        },
    }


BASE.validate_dataset_and_contract_gates = validate_dataset_and_contract_gates
run_pipeline = BASE.run_pipeline
main = BASE.main


if __name__ == "__main__":
    raise SystemExit(main())
