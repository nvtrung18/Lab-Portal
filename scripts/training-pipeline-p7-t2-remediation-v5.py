#!/usr/bin/env python3
"""Run governed P7-T2 Research remediation v5 training."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
from typing import Any


BASE_PIPELINE_PATH = Path(__file__).with_name("training-pipeline-p7-t2-remediation.py")
EVALUATOR_PATH = Path(__file__).with_name("validate-p7-t4-research-evaluation-v2.py")


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


BASE = _load_module("p7_t2_remediation_pipeline_for_v5", BASE_PIPELINE_PATH)
EVALUATOR = _load_module("p7_t4_research_evaluator_for_v5_training", EVALUATOR_PATH)
ROOT = BASE.ROOT
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation-v5.py"
SCHEMA_VERSION = "5.0.0"
PIPELINE_VERSION = "5.0.0"
DATASET_IDENTITY = "591ded79431b4ffbba786a63fdffe7486f2a19fe787ef91261b26d8c33e01f67"
TRAINING_APPROVAL_IDENTITY = "19a405ca049b7bc15b8f2b96d5b2c85eb364f138309b5528a71518ee86632dba"
TRAINING_CONTRACT_IDENTITY = "c3e90ed16695cde5fb08f33d2d80a30e8d59db6f9a9652d051316a15dc6d8e18"
DATASET_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v5/manifest.approved.json"
)
DATASET_RECORD_SCHEMA_VERSION = "5.0.0"
GRADIENT_SCALER_INITIAL_SCALE = 32768
INCLUDED_USE_CASES = {
    "RESEARCH_UC_003",
    "RESEARCH_UC_004",
    "RESEARCH_UC_005",
    "RESEARCH_UC_006",
}
TRAINING_REQUEST_IDENTITY = (
    "780a5deeb83e30a38e229d91c54cbb8c0c56fd0ef1717a402df8440bc23e06f0"
)
EVALUATOR_SUITE_APPROVAL_IDENTITY = (
    "53d48c6489ecd7bb4f7a4a1c85bbe2813454c94c520310f28c74499d4bfdae05"
)
EVALUATOR_IDENTITY = "99230c674b9064f1e06247dedd014f6e3da0714ca679017c07b3d877f1e285d3"
EVALUATION_SUITE_IDENTITY = (
    "65c87149ec97bf34a04257a80af0cba1114b48fe9702f6b3cacb253b573931a8"
)

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


def _records(
    manifest_path: Path, manifest: dict[str, Any]
) -> dict[str, list[dict[str, Any]]]:
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise TrainingPipelineError("dataset manifest: artifact inventory required")
    inventory = {
        item.get("filename"): item for item in artifacts if isinstance(item, dict)
    }
    result: dict[str, list[dict[str, Any]]] = {}
    for split in ("train", "validation", "evaluation"):
        item = inventory.get(f"{split}.jsonl")
        if not isinstance(item, dict):
            raise TrainingPipelineError(f"dataset manifest: missing {split}.jsonl")
        result[split] = BASE._load_records(
            manifest_path.parent / f"{split}.jsonl", item
        )
    content_ids = {
        split: {record["contentId"] for record in records}
        for split, records in result.items()
    }
    if (
        content_ids["train"] & content_ids["validation"]
        or content_ids["train"] & content_ids["evaluation"]
        or content_ids["validation"] & content_ids["evaluation"]
    ):
        raise TrainingPipelineError(
            "dataset: train/validation/contract holdout must be disjoint"
        )
    return result


def _validate_record_contract(record: dict[str, Any]) -> None:
    prompt = record["trainingPrompt"]
    target = record["trainingTarget"]
    findings = []
    findings.extend(EVALUATOR.validate_tool(target.get("toolRequest")))
    findings.extend(EVALUATOR.validate_response(target.get("response")))
    findings.extend(
        EVALUATOR.validate_output(
            target.get("structuredOutput"),
            prompt.get("structuredOutputContract"),
        )
    )
    if findings:
        raise TrainingPipelineError(
            "evaluator v2: training target contract mismatch: "
            + "; ".join(sorted(set(findings)))
        )


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
        != "460d7fcfe0574832f3a1e1ec6f58299b739bba005d32f13754eb6512c8189ac4"
        or manifest.get("approval_status") != "APPROVED"
        or manifest.get("trainingAuthorized") is not True
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise TrainingPipelineError("dataset manifest: exact approved v5 identity required")
    records = _records(manifest_path, manifest)
    if {name: len(items) for name, items in records.items()} != {
        "train": 144,
        "validation": 24,
        "evaluation": 24,
    }:
        raise TrainingPipelineError("dataset: exact v5 split counts required")
    for split_records in records.values():
        for record in split_records:
            _validate_record_contract(record)

    gates = config["contractGates"]
    approval = load_document(
        repository_root / gates["trainingApprovalReference"], "training approval"
    )
    authorization = approval.get("authorization", {})
    if (
        approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or artifact_identity(approval, "artifactIdentity")
        != TRAINING_APPROVAL_IDENTITY
        or approval.get("requestIdentity") != TRAINING_REQUEST_IDENTITY
        or approval.get("status") != "APPROVED"
        or approval.get("revocation", {}).get("status") != "ACTIVE"
        or authorization.get("externalTrainingAllowed") is not True
        or authorization.get("evaluationAllowed") is not False
        or authorization.get("promotionAllowed") is not False
        or authorization.get("runtimeNormalizationAllowed") is not False
        or authorization.get("constrainedDecodingAllowed") is not False
        or approval.get("scope", {}).get("includedUseCases")
        != sorted(INCLUDED_USE_CASES)
        or approval.get("scope", {}).get("frozenEvaluationTrainingUseAllowed")
        is not False
        or approval.get("scope", {}).get("freshBaseModelStartRequired") is not True
        or approval.get("scope", {}).get("candidateDispositionAfterTraining")
        != "CANDIDATE_ONLY"
    ):
        raise TrainingPipelineError("training approval: exact active v5 approval required")

    contract = load_document(
        repository_root / gates["trainingContractReference"], "training contract"
    )
    if (
        contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY
        or artifact_identity(contract, "artifactIdentity")
        != TRAINING_CONTRACT_IDENTITY
        or contract.get("state") != "PREPARED_AWAITING_TRAINING_APPROVAL"
        or contract.get("trainingAuthorized") is not False
        or contract.get("externalTrainingAllowed") is not False
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("runtimeNormalizationAllowed") is not False
        or contract.get("recordCount") != 192
    ):
        raise TrainingPipelineError("training contract: exact immutable v5 contract required")

    runtime = gates["preparedRuntimeContract"]
    evaluator_approval = load_document(
        repository_root / runtime["evaluatorSuiteApprovalReference"],
        "evaluator suite approval",
    )
    evaluator_contract = load_document(
        repository_root / runtime["evaluatorContractReference"],
        "evaluator contract",
    )
    suite = load_document(
        repository_root / runtime["evaluationSuiteReference"], "evaluation suite"
    )
    if (
        evaluator_approval.get("artifactIdentity")
        != EVALUATOR_SUITE_APPROVAL_IDENTITY
        or artifact_identity(evaluator_approval, "artifactIdentity")
        != EVALUATOR_SUITE_APPROVAL_IDENTITY
        or evaluator_approval.get("revocation", {}).get("status") != "ACTIVE"
        or evaluator_contract.get("artifactIdentity") != EVALUATOR_IDENTITY
        or artifact_identity(evaluator_contract, "artifactIdentity")
        != EVALUATOR_IDENTITY
        or evaluator_contract.get("evaluatorVersion") != "2.0.0"
        or suite.get("suiteDigest") != EVALUATION_SUITE_IDENTITY
        or suite.get("suiteVersion") != "2.0.0"
        or suite.get("TRAINING_PROHIBITED") is not True
        or suite.get("externalExecutionAllowed") is not False
        or runtime.get("runtimeNormalizationAllowed") is not False
        or runtime.get("constrainedDecodingAllowed") is not False
    ):
        raise TrainingPipelineError("evaluator/suite: exact approved v2 contracts required")

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
        "evaluatorContract": {"state": "PASS", "version": "2.0.0"},
        "evaluationSuite": {
            "state": "BOUND_NOT_EXECUTED",
            "version": "2.0.0",
        },
        "runtimeControls": {
            "runtimeNormalizationAllowed": False,
            "constrainedDecodingAllowed": False,
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
