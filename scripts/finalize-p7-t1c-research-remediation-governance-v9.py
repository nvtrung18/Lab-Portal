#!/usr/bin/env python3
"""Finalize the exact approved P7-T4 remediation v9 training request."""
from __future__ import annotations

import argparse
import copy
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import tempfile
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REQUEST_REFERENCE = "config/p7-t4-research-remediation-governance-v9/training-approval-request.json"
PENDING_CARD_REFERENCE = "config/p7-t4-research-remediation-governance-v9/training-dataset-card.pending.json"
PENDING_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v9/manifest.json"
CONTRACT_REFERENCE = "datasets/p7-t4-research-remediation-source-v9/training-contract.json"
PREPARATION_APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v9-governance-approval.json"
TRAINING_APPROVAL_REFERENCE = "evidence/p7-t1c-research-remediation-v9-training-governance-approval.json"
APPROVED_CARD_REFERENCE = "config/p7-t1c-research-remediation-governance-v9/training-dataset-card.approved.json"
APPROVED_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v9/manifest.approved.json"

TRAINING_REQUEST_IDENTITY = "bdd4227a12c9695071ce397ff19e20eb7b9972dadd70bf9d7cb2952c90d73c63"
DATASET_IDENTITY = "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
TRAINING_CONTRACT_IDENTITY = "4d3e2f9685afffb8b1ac26f33e140251e17df4924c041dab6a2a2600255a715c"
PREPARATION_APPROVAL_IDENTITY = "a0987f0dc499b489838b854d0ca01f5aaa25fb144235c75e5577d0e548638440"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
QUALITY_IDENTITY = "2f49d629fd1620183c35ef33bcbeb154dee32b998b3d1775de720cacee3268fc"
BASE_DATASET_IDENTITY = "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY"
SPLIT_COUNTS = {"evaluation": 80, "train": 480, "validation": 80}
RETAINED_COUNTS = {"evaluation": 72, "train": 432, "validation": 72}
TARGETED_COUNTS = {"evaluation": 8, "train": 48, "validation": 8}


class FinalizationError(ValueError):
    pass


def canonical_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def artifact_identity(value: dict[str, Any], field: str = "artifactIdentity") -> str:
    return hashlib.sha256(canonical_bytes({key: item for key, item in value.items() if key != field})).hexdigest()


def _load(reference: str) -> dict[str, Any]:
    value = json.loads((ROOT / reference).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise FinalizationError(f"{reference}: object required")
    return value


def _valid_timestamp(value: str) -> bool:
    try:
        return datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ").strftime("%Y-%m-%dT%H:%M:%SZ") == value
    except (TypeError, ValueError):
        return False


def _inputs() -> dict[str, dict[str, Any]]:
    values = {
        "request": _load(REQUEST_REFERENCE),
        "card": _load(PENDING_CARD_REFERENCE),
        "manifest": _load(PENDING_MANIFEST_REFERENCE),
        "contract": _load(CONTRACT_REFERENCE),
        "preparation": _load(PREPARATION_APPROVAL_REFERENCE),
    }
    request, card, manifest, contract, preparation = values.values()
    if request.get("requestIdentity") != TRAINING_REQUEST_IDENTITY or artifact_identity(request, "requestIdentity") != TRAINING_REQUEST_IDENTITY:
        raise FinalizationError("request: exact pending v9 request identity required")
    if request.get("status") != "PENDING_USER_APPROVAL" or request.get("trainingAuthorized") is not False or request.get("externalTrainingAllowed") is not False:
        raise FinalizationError("request: pending fail-closed state required")
    if request.get("datasetIdentity") != DATASET_IDENTITY or request.get("requestedScope", {}).get("targetedEvaluationCaseIds") != ["E-FUNC-RESEARCH-006"]:
        raise FinalizationError("request: exact v9 dataset scope required")
    if manifest.get("artifactIdentity") != artifact_identity(manifest) or manifest.get("datasetIdentity") != DATASET_IDENTITY or manifest.get("recordCounts") != SPLIT_COUNTS:
        raise FinalizationError("manifest: exact pending v9 identity required")
    if manifest.get("retainedRecordCounts") != RETAINED_COUNTS or manifest.get("targetedAdditionCounts") != TARGETED_COUNTS or manifest.get("trainingAuthorized") is not False:
        raise FinalizationError("manifest: exact v8 retention and v9 additions required")
    if card.get("artifactIdentity") != artifact_identity(card) or card.get("datasetIdentity") != DATASET_IDENTITY or card.get("recordCounts") != SPLIT_COUNTS:
        raise FinalizationError("card: exact pending v9 identity required")
    if contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY or artifact_identity(contract) != TRAINING_CONTRACT_IDENTITY:
        raise FinalizationError("contract: exact immutable v9 identity required")
    if contract.get("baseDatasetIdentity") != BASE_DATASET_IDENTITY or contract.get("frozenEvaluationUseAllowed") is not False or contract.get("runtimeNormalizationAllowed") is not False or contract.get("constrainedDecodingAllowed") is not False:
        raise FinalizationError("contract: fail-closed v9 controls required")
    if preparation.get("artifactIdentity") != PREPARATION_APPROVAL_IDENTITY or preparation.get("revocation", {}).get("status") != "ACTIVE":
        raise FinalizationError("preparation approval: exact active identity required")
    return values


def _approval(approved_by: str, approved_at: str) -> dict[str, Any]:
    value = {
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V9-TRAINING-GOVERNANCE-APPROVAL",
        "schemaVersion": "1.0.0", "status": "APPROVED", "purpose": "TRAINING",
        "requestIdentity": TRAINING_REQUEST_IDENTITY, "requestReference": REQUEST_REFERENCE,
        "approvalAuthority": APPROVAL_AUTHORITY, "datasetIdentity": DATASET_IDENTITY,
        "datasetManifestReference": PENDING_MANIFEST_REFERENCE,
        "trainingContractIdentity": TRAINING_CONTRACT_IDENTITY, "trainingContractReference": CONTRACT_REFERENCE,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY, "preparationApprovalReference": PREPARATION_APPROVAL_REFERENCE,
        "qualityIdentity": QUALITY_IDENTITY,
        "scope": {
            "assistantKey": "RESEARCH_ASSISTANT", "includedUseCases": [f"RESEARCH_UC_{number:03d}" for number in range(1, 7)],
            "targetedUseCases": ["RESEARCH_UC_006"], "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"],
            "retainedV8RecordsUnchanged": True, "fullySyntheticOnly": True,
            "privateResearchDocumentUseAllowed": False, "productionDataUseAllowed": False,
            "frozenEvaluationTrainingUseAllowed": False, "contractHoldoutUsedForOptimization": False,
            "freshBaseModelStartRequired": True, "candidateDispositionAfterTraining": "CANDIDATE_ONLY",
        },
        "authorization": {
            "externalTrainingAllowed": True, "evaluationAllowed": False, "promotionAllowed": False,
            "productionPromptingAllowed": False, "runtimeNormalizationAllowed": False,
            "constrainedDecodingAllowed": False, "separateEvaluationApprovalRequired": True,
        },
        "approval": {"decision": "APPROVED", "approvedBy": approved_by, "approvedAt": approved_at},
        "revocation": {"status": "ACTIVE", "authority": APPROVAL_AUTHORITY}, "artifactIdentity": "",
    }
    value["artifactIdentity"] = artifact_identity(value)
    return value


def build_documents(*, request_identity: str, approved_by: str, approved_at: str) -> dict[str, dict[str, Any]]:
    if request_identity != TRAINING_REQUEST_IDENTITY:
        raise FinalizationError("request identity does not match the approved request")
    if approved_by != APPROVAL_AUTHORITY or not _valid_timestamp(approved_at):
        raise FinalizationError("exact approval authority and real UTC timestamp required")
    inputs = _inputs()
    approval = _approval(approved_by, approved_at)
    card = copy.deepcopy(inputs["card"])
    card.update({"preparedCardIdentity": card["artifactIdentity"], "disposition": "APPROVED_FOR_TRAINING_ONLY", "trainingAuthorized": True, "trainingApprovalIdentity": approval["artifactIdentity"], "trainingApprovalReference": TRAINING_APPROVAL_REFERENCE, "evaluationAllowed": False, "promotionAllowed": False, "artifactIdentity": ""})
    card["artifactIdentity"] = artifact_identity(card)
    manifest = copy.deepcopy(inputs["manifest"])
    manifest.update({"preparedManifestIdentity": manifest["artifactIdentity"], "approval_status": "APPROVED", "status": "APPROVED_FOR_TRAINING_ONLY", "trainingAuthorized": True, "trainingApprovalIdentity": approval["artifactIdentity"], "trainingApprovalReference": TRAINING_APPROVAL_REFERENCE, "trainingContractIdentity": TRAINING_CONTRACT_IDENTITY, "trainingContractReference": CONTRACT_REFERENCE, "artifactIdentity": ""})
    manifest["artifactIdentity"] = artifact_identity(manifest)
    documents = {TRAINING_APPROVAL_REFERENCE: approval, APPROVED_CARD_REFERENCE: card, APPROVED_MANIFEST_REFERENCE: manifest}
    validate_documents(documents)
    return documents


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    if set(documents) != {TRAINING_APPROVAL_REFERENCE, APPROVED_CARD_REFERENCE, APPROVED_MANIFEST_REFERENCE}:
        raise FinalizationError("exact finalization inventory required")
    approval = documents[TRAINING_APPROVAL_REFERENCE]
    if approval != _approval(approval.get("approval", {}).get("approvedBy"), approval.get("approval", {}).get("approvedAt")):
        raise FinalizationError("approval: exact request-bound artifact required")
    for document in documents.values():
        if document.get("artifactIdentity") != artifact_identity(document):
            raise FinalizationError("finalized artifact identity mismatch")


def write_documents(documents: dict[str, dict[str, Any]]) -> None:
    for reference in documents:
        if (ROOT / reference).exists():
            raise FinalizationError(f"append-only artifact already exists: {reference}")
    for reference, document in sorted(documents.items()):
        path = ROOT / reference
        path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile("wb", dir=path.parent, delete=False) as temporary:
            temporary.write(json_bytes(document)); temporary.flush(); os.fsync(temporary.fileno())
            temporary_path = Path(temporary.name)
        os.link(temporary_path, path); temporary_path.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--request-identity", required=True); parser.add_argument("--approved-by", required=True); parser.add_argument("--approved-at", required=True); parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        documents = build_documents(request_identity=args.request_identity, approved_by=args.approved_by, approved_at=args.approved_at)
        if args.check:
            if any(not (ROOT / ref).is_file() or (ROOT / ref).read_bytes() != json_bytes(doc) for ref, doc in documents.items()):
                raise FinalizationError("checked-in artifact mismatch")
        else:
            write_documents(documents)
    except (FinalizationError, OSError, json.JSONDecodeError) as error:
        print(json.dumps({"status": "ERROR", "diagnostics": [str(error)]}, sort_keys=True)); return 2
    approval = documents[TRAINING_APPROVAL_REFERENCE]
    print(json.dumps({"status": "APPROVED", "approvalIdentity": approval["artifactIdentity"], "requestIdentity": TRAINING_REQUEST_IDENTITY}, sort_keys=True)); return 0


if __name__ == "__main__":
    raise SystemExit(main())
