#!/usr/bin/env python3
"""Finalize the exact approved P7-T2 remediation v10 continuation request."""
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
REQUEST_REFERENCE = "config/p7-t4-research-remediation-governance-v10/training-approval-request.json"
PENDING_CARD_REFERENCE = "config/p7-t4-research-remediation-governance-v10/training-dataset-card.pending.json"
PENDING_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v10/manifest.json"
CONTRACT_REFERENCE = "datasets/p7-t4-research-remediation-source-v10/training-contract.json"
PREPARATION_APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v10-governance-approval.json"
TRAINING_APPROVAL_REFERENCE = "evidence/p7-t1c-research-remediation-v10-training-governance-approval.json"
APPROVED_CARD_REFERENCE = "config/p7-t1c-research-remediation-governance-v10/training-dataset-card.approved.json"
APPROVED_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v10/manifest.approved.json"

TRAINING_REQUEST_IDENTITY = "1cc572882386fc684e66df553dcd381303820852f8b5fc3af396e0f7171a0c91"
DATASET_IDENTITY = "abce232c1721788bae5a1686f9d017f295a6892555193140ae74c5a044e0a409"
TRAINING_CONTRACT_IDENTITY = "3b924bb766dd6936c7e9aa5d0387c928856b9582e6283b06dbf5d36e2f3fa042"
PREPARATION_APPROVAL_IDENTITY = "1740232600ee81580e993cf46d4efb5a62a6bd8b6f201327f25dbca70cec7a11"
QUALITY_IDENTITY = "cf9cb97f7acfd0eac2250ae206d9408f8d9b5e9ab2f70412621f5eff9d896b2b"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY"
COUNTS = {"evaluation": 8, "train": 96, "validation": 20}


class FinalizationError(ValueError):
    pass


def canonical_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode()


def artifact_identity(value: dict[str, Any], field: str = "artifactIdentity") -> str:
    return hashlib.sha256(canonical_bytes({k: v for k, v in value.items() if k != field})).hexdigest()


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
    result = {name: _load(ref) for name, ref in {
        "request": REQUEST_REFERENCE, "card": PENDING_CARD_REFERENCE,
        "manifest": PENDING_MANIFEST_REFERENCE, "contract": CONTRACT_REFERENCE,
        "preparation": PREPARATION_APPROVAL_REFERENCE,
    }.items()}
    request, card, manifest, contract, preparation = result.values()
    if request.get("requestIdentity") != TRAINING_REQUEST_IDENTITY or artifact_identity(request, "requestIdentity") != TRAINING_REQUEST_IDENTITY:
        raise FinalizationError("request: exact pending v10 identity required")
    if request.get("status") != "PENDING_USER_APPROVAL" or request.get("trainingAuthorized") is not False:
        raise FinalizationError("request: fail-closed pending state required")
    scope = request.get("requestedScope", {})
    required = {
        "trainingMethod": "QLORA_ADAPTER_CONTINUATION", "maximumSteps": 48,
        "earlyStoppingPatience": 1, "freshBaseModelLoadRequired": True,
        "freshAdapterInitializationRequired": False,
        "contractHoldoutUsedForOptimization": False,
        "contractHoldoutUsedForEarlyStopping": False,
    }
    if any(scope.get(k) != v for k, v in required.items()) or scope.get("learningRateMaximum") > 2e-5:
        raise FinalizationError("request: exact governed continuation scope required")
    if manifest.get("artifactIdentity") != artifact_identity(manifest) or manifest.get("datasetIdentity") != DATASET_IDENTITY or manifest.get("recordCounts") != COUNTS or manifest.get("trainingAuthorized") is not False:
        raise FinalizationError("manifest: exact pending v10 identity required")
    if card.get("artifactIdentity") != artifact_identity(card) or card.get("datasetIdentity") != DATASET_IDENTITY or card.get("recordCounts") != COUNTS:
        raise FinalizationError("card: exact pending v10 identity required")
    if contract.get("artifactIdentity") != TRAINING_CONTRACT_IDENTITY or artifact_identity(contract) != TRAINING_CONTRACT_IDENTITY:
        raise FinalizationError("contract: exact immutable v10 identity required")
    if preparation.get("artifactIdentity") != PREPARATION_APPROVAL_IDENTITY or preparation.get("revocation", {}).get("status") != "ACTIVE":
        raise FinalizationError("preparation approval: exact active v10 identity required")
    return result


def _approval(approved_by: str, approved_at: str, scope: dict[str, Any]) -> dict[str, Any]:
    value = {
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V10-TRAINING-GOVERNANCE-APPROVAL",
        "schemaVersion": "1.0.0", "status": "APPROVED", "purpose": "TRAINING",
        "requestIdentity": TRAINING_REQUEST_IDENTITY, "requestReference": REQUEST_REFERENCE,
        "approvalAuthority": APPROVAL_AUTHORITY, "datasetIdentity": DATASET_IDENTITY,
        "datasetManifestReference": PENDING_MANIFEST_REFERENCE,
        "trainingContractIdentity": TRAINING_CONTRACT_IDENTITY, "trainingContractReference": CONTRACT_REFERENCE,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "preparationApprovalReference": PREPARATION_APPROVAL_REFERENCE,
        "qualityIdentity": QUALITY_IDENTITY,
        "scope": copy.deepcopy(scope),
        "authorization": {
            "externalTrainingAllowed": True, "evaluationAllowed": False,
            "promotionAllowed": False, "productionPromptingAllowed": False,
            "runtimeNormalizationAllowed": False, "constrainedDecodingAllowed": False,
            "separateEvaluationApprovalRequired": True,
        },
        "approval": {"decision": "APPROVED", "approvedBy": approved_by, "approvedAt": approved_at},
        "revocation": {"status": "ACTIVE", "authority": APPROVAL_AUTHORITY},
        "artifactIdentity": "",
    }
    value["artifactIdentity"] = artifact_identity(value)
    return value


def build_documents(*, request_identity: str, approved_by: str, approved_at: str) -> dict[str, dict[str, Any]]:
    if request_identity != TRAINING_REQUEST_IDENTITY or approved_by != APPROVAL_AUTHORITY or not _valid_timestamp(approved_at):
        raise FinalizationError("exact approved request, authority, and UTC timestamp required")
    inputs = _inputs()
    approval = _approval(approved_by, approved_at, inputs["request"]["requestedScope"])
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
    expected = _approval(approval.get("approval", {}).get("approvedBy"), approval.get("approval", {}).get("approvedAt"), _inputs()["request"]["requestedScope"])
    if approval != expected or any(doc.get("artifactIdentity") != artifact_identity(doc) for doc in documents.values()):
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
    print(json.dumps({"status": "APPROVED", "approvalIdentity": documents[TRAINING_APPROVAL_REFERENCE]["artifactIdentity"], "requestIdentity": TRAINING_REQUEST_IDENTITY}, sort_keys=True)); return 0


if __name__ == "__main__":
    raise SystemExit(main())
