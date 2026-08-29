#!/usr/bin/env python3
"""Finalize the exact approved P7-T4 remediation v8 training request."""
from __future__ import annotations

import argparse
import copy
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REQUEST_REFERENCE = "config/p7-t4-research-remediation-governance-v8/training-approval-request.json"
PENDING_CARD_REFERENCE = "config/p7-t4-research-remediation-governance-v8/training-dataset-card.pending.json"
PENDING_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v8/manifest.json"
SOURCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v8/source-export.json"
PROVENANCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v8/provenance.json"
CONTRACT_REFERENCE = "datasets/p7-t4-research-remediation-source-v8/training-contract.json"
PROMPT_PROFILE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/"
    "research-prompt-profile-v3.approved.json"
)
PREPARATION_APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v8-governance-approval.json"
TRAINING_APPROVAL_REFERENCE = "evidence/p7-t1c-research-remediation-v8-training-governance-approval.json"
APPROVED_CARD_REFERENCE = "config/p7-t1c-research-remediation-governance-v8/training-dataset-card.approved.json"
APPROVED_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v8/manifest.approved.json"

TRAINING_REQUEST_IDENTITY = "e486eb298d44ec2bc1c7db767a9ce4222c65084856090b9ba8e6f04bad6368d0"
DATASET_IDENTITY = "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
PENDING_MANIFEST_IDENTITY = "6946a9600538f496beb0411fc16cd0165e81f7ed2f96bb5247fb4889e6014880"
PENDING_CARD_IDENTITY = "97479f54ddb180ac75d8de70a864276f5e90f7f73785ec79517d4dd4ee42347a"
SOURCE_IDENTITY = "4ab0245ab18cc162c312dfd852245e349932dc56834265023307335602212a67"
PROVENANCE_IDENTITY = "1cbf8dd131cef2414e7cfbe5bdea3010cde6f1b91b2cb31e3789e95f24a33c03"
TRAINING_CONTRACT_IDENTITY = "cd6a1edd2283ad5c584ee2e376bab1b81a8c841e1205e730c441fa9f7c9962f2"
PREPARATION_APPROVAL_IDENTITY = "482525b6de1fa7dcea165d522bf8745d7e18b57aa12303fed46832969cc0d82c"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
QUALITY_IDENTITY = "ee5b6dd46d6db2f40fba561e6f7d142bbd965dc3b563945ff88bbdbac9c27f22"
V7_DATASET_IDENTITY = "b973bf0a387b35b84140a2db1d2bbf7c5c0d7f054102a968c10bd1df6faeff23"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY"
SPLIT_COUNTS = {"evaluation": 72, "train": 432, "validation": 72}
RETAINED_COUNTS = {"evaluation": 64, "train": 384, "validation": 64}
TARGETED_COUNTS = {"evaluation": 8, "train": 48, "validation": 8}
TIMESTAMP_PATTERN = re.compile(
    r"^20[0-9]{2}-(0[1-9]|1[0-2])-([0-2][0-9]|3[0-1])T"
    r"([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
)


class FinalizationError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def canonical_bytes(value: object) -> bytes:
    try:
        return json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as error:
        raise FinalizationError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def artifact_identity(value: dict[str, Any], field: str = "artifactIdentity") -> str:
    return hashlib.sha256(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    ).hexdigest()


def load_document(reference: str) -> dict[str, Any]:
    try:
        value = json.loads((ROOT / reference).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise FinalizationError(f"{reference}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise FinalizationError(f"{reference}: object required")
    return value


def _valid_timestamp(value: object) -> bool:
    if not isinstance(value, str) or TIMESTAMP_PATTERN.fullmatch(value) is None:
        return False
    try:
        datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError:
        return False
    return True


def _inputs() -> dict[str, dict[str, Any]]:
    values = {
        "request": load_document(REQUEST_REFERENCE),
        "card": load_document(PENDING_CARD_REFERENCE),
        "manifest": load_document(PENDING_MANIFEST_REFERENCE),
        "source": load_document(SOURCE_REFERENCE),
        "provenance": load_document(PROVENANCE_REFERENCE),
        "contract": load_document(CONTRACT_REFERENCE),
        "profile": load_document(PROMPT_PROFILE_REFERENCE),
        "preparationApproval": load_document(PREPARATION_APPROVAL_REFERENCE),
    }
    diagnostics: list[str] = []
    request = values["request"]
    if (
        request.get("requestIdentity") != TRAINING_REQUEST_IDENTITY
        or artifact_identity(request, "requestIdentity") != TRAINING_REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or request.get("trainingAuthorized") is not False
        or request.get("externalTrainingAllowed") is not False
        or request.get("approvalAuthority") != APPROVAL_AUTHORITY
        or request.get("datasetIdentity") != DATASET_IDENTITY
        or request.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or request.get("preparationApprovalIdentity") != PREPARATION_APPROVAL_IDENTITY
        or request.get("qualityIdentity") != QUALITY_IDENTITY
        or request.get("requestedScope", {}).get("targetedEvaluationCaseIds")
        != ["E-INJECT-001"]
    ):
        diagnostics.append("request: exact pending v8 request identity required")
    manifest = values["manifest"]
    if (
        manifest.get("artifactIdentity") != PENDING_MANIFEST_IDENTITY
        or artifact_identity(manifest) != PENDING_MANIFEST_IDENTITY
        or manifest.get("datasetIdentity") != DATASET_IDENTITY
        or manifest.get("recordCounts") != SPLIT_COUNTS
        or manifest.get("retainedRecordCounts") != RETAINED_COUNTS
        or manifest.get("targetedAdditionCounts") != TARGETED_COUNTS
        or manifest.get("targetedEvaluationCaseIds") != ["E-INJECT-001"]
        or manifest.get("trainingAuthorized") is not False
        or manifest.get("approval_status") != "PENDING_TRAINING_APPROVAL"
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        diagnostics.append("manifest: exact pending single-failure v8 identity required")
    card = values["card"]
    if (
        card.get("artifactIdentity") != PENDING_CARD_IDENTITY
        or artifact_identity(card) != PENDING_CARD_IDENTITY
        or card.get("datasetIdentity") != DATASET_IDENTITY
        or card.get("recordCounts") != SPLIT_COUNTS
        or card.get("disposition") != "PENDING_TRAINING_APPROVAL"
        or card.get("remediationPriority")
        != "SINGLE_FAILED_CASE_DATASET_QUALITY_ONLY"
        or card.get("targetedEvaluationCaseIds") != ["E-INJECT-001"]
    ):
        diagnostics.append("card: exact pending v8 identity required")
    for name, identity in (
        ("source", SOURCE_IDENTITY),
        ("provenance", PROVENANCE_IDENTITY),
        ("contract", TRAINING_CONTRACT_IDENTITY),
        ("profile", PROMPT_PROFILE_IDENTITY),
        ("preparationApproval", PREPARATION_APPROVAL_IDENTITY),
    ):
        value = values[name]
        if value.get("artifactIdentity") != identity or artifact_identity(value) != identity:
            diagnostics.append(f"{name}: exact approved identity required")
    contract = values["contract"]
    if (
        contract.get("datasetIdentity") != DATASET_IDENTITY
        or contract.get("baseDatasetIdentity") != V7_DATASET_IDENTITY
        or contract.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or contract.get("trainingRecordCount") != 432
        or contract.get("validationRecordCount") != 72
        or contract.get("evaluationRecordCount") != 72
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("runtimeNormalizationAllowed") is not False
        or contract.get("constrainedDecodingAllowed") is not False
    ):
        diagnostics.append("contract: exact immutable v8 restrictions required")
    if diagnostics:
        raise FinalizationError(diagnostics)
    return values


def _approval(approved_by: str, approved_at: str) -> dict[str, Any]:
    value: dict[str, Any] = {
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V8-TRAINING-GOVERNANCE-APPROVAL",
        "schemaVersion": "1.0.0",
        "status": "APPROVED",
        "purpose": "TRAINING",
        "requestIdentity": TRAINING_REQUEST_IDENTITY,
        "requestReference": REQUEST_REFERENCE,
        "approvalAuthority": APPROVAL_AUTHORITY,
        "datasetIdentity": DATASET_IDENTITY,
        "datasetManifestReference": PENDING_MANIFEST_REFERENCE,
        "trainingContractIdentity": TRAINING_CONTRACT_IDENTITY,
        "trainingContractReference": CONTRACT_REFERENCE,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "promptProfileReference": PROMPT_PROFILE_REFERENCE,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "preparationApprovalReference": PREPARATION_APPROVAL_REFERENCE,
        "qualityIdentity": QUALITY_IDENTITY,
        "scope": {
            "assistantKey": "RESEARCH_ASSISTANT",
            "includedUseCases": [f"RESEARCH_UC_{number:03d}" for number in range(1, 7)],
            "targetedUseCases": ["RESEARCH_UC_001"],
            "targetedEvaluationCaseIds": ["E-INJECT-001"],
            "retainedV7RecordsUnchanged": True,
            "fullySyntheticOnly": True,
            "privateResearchDocumentUseAllowed": False,
            "productionDataUseAllowed": False,
            "frozenEvaluationTrainingUseAllowed": False,
            "contractHoldoutUsedForOptimization": False,
            "freshBaseModelStartRequired": True,
            "candidateDispositionAfterTraining": "CANDIDATE_ONLY",
        },
        "authorization": {
            "externalTrainingAllowed": True,
            "evaluationAllowed": False,
            "promotionAllowed": False,
            "productionPromptingAllowed": False,
            "runtimeNormalizationAllowed": False,
            "constrainedDecodingAllowed": False,
            "separateEvaluationApprovalRequired": True,
        },
        "approval": {
            "decision": "APPROVED",
            "approvedBy": approved_by,
            "approvedAt": approved_at,
        },
        "revocation": {"status": "ACTIVE", "authority": APPROVAL_AUTHORITY},
        "artifactIdentity": "",
    }
    value["artifactIdentity"] = artifact_identity(value)
    return value


def _approved_card(pending: dict[str, Any], approval: dict[str, Any]) -> dict[str, Any]:
    card = copy.deepcopy(pending)
    card.update(
        {
            "preparedCardIdentity": pending["artifactIdentity"],
            "disposition": "APPROVED_FOR_TRAINING_ONLY",
            "trainingAuthorized": True,
            "trainingApprovalIdentity": approval["artifactIdentity"],
            "trainingApprovalReference": TRAINING_APPROVAL_REFERENCE,
            "evaluationAllowed": False,
            "promotionAllowed": False,
            "artifactIdentity": "",
        }
    )
    card["artifactIdentity"] = artifact_identity(card)
    return card


def _approved_manifest(pending: dict[str, Any], approval: dict[str, Any]) -> dict[str, Any]:
    manifest = copy.deepcopy(pending)
    manifest.update(
        {
            "preparedManifestIdentity": pending["artifactIdentity"],
            "approval_status": "APPROVED",
            "status": "APPROVED_FOR_TRAINING_ONLY",
            "trainingAuthorized": True,
            "trainingApprovalIdentity": approval["artifactIdentity"],
            "trainingApprovalReference": TRAINING_APPROVAL_REFERENCE,
            "trainingContractIdentity": TRAINING_CONTRACT_IDENTITY,
            "trainingContractReference": CONTRACT_REFERENCE,
            "promptProfileReference": PROMPT_PROFILE_REFERENCE,
            "artifactIdentity": "",
        }
    )
    manifest["artifactIdentity"] = artifact_identity(manifest)
    return manifest


def build_documents(
    *, request_identity: str, approved_by: str, approved_at: str
) -> dict[str, dict[str, Any]]:
    if request_identity != TRAINING_REQUEST_IDENTITY:
        raise FinalizationError("request identity does not match the approved request")
    if approved_by != APPROVAL_AUTHORITY:
        raise FinalizationError("approval authority must match the requested authority")
    if not _valid_timestamp(approved_at):
        raise FinalizationError("a real UTC --approved-at timestamp is required")
    inputs = _inputs()
    approval = _approval(approved_by, approved_at)
    documents = {
        TRAINING_APPROVAL_REFERENCE: approval,
        APPROVED_CARD_REFERENCE: _approved_card(inputs["card"], approval),
        APPROVED_MANIFEST_REFERENCE: _approved_manifest(inputs["manifest"], approval),
    }
    validate_documents(documents)
    return documents


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    expected = {
        TRAINING_APPROVAL_REFERENCE,
        APPROVED_CARD_REFERENCE,
        APPROVED_MANIFEST_REFERENCE,
    }
    if set(documents) != expected:
        raise FinalizationError("finalization: exact artifact inventory required")
    inputs = _inputs()
    approval = documents[TRAINING_APPROVAL_REFERENCE]
    approved_by = approval.get("approval", {}).get("approvedBy")
    approved_at = approval.get("approval", {}).get("approvedAt")
    diagnostics: list[str] = []
    if approved_by != APPROVAL_AUTHORITY or not _valid_timestamp(approved_at):
        diagnostics.append("approval: exact authority and real UTC timestamp required")
    elif approval != _approval(approved_by, approved_at):
        diagnostics.append("approval: exact request-bound artifact required")
    if documents[APPROVED_CARD_REFERENCE] != _approved_card(inputs["card"], approval):
        diagnostics.append("approved card: exact pending-card transition required")
    if documents[APPROVED_MANIFEST_REFERENCE] != _approved_manifest(
        inputs["manifest"], approval
    ):
        diagnostics.append("approved manifest: exact pending-manifest transition required")
    rendered = canonical_bytes(documents).lower()
    if b"evals/p7-t3-research-gap-evaluation-suite" in rendered:
        diagnostics.append("approval: frozen evaluation source reuse is forbidden")
    if diagnostics:
        raise FinalizationError(diagnostics)


def _atomic_append(path: Path, content: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "wb", dir=path.parent, prefix=f".{path.name}.", suffix=".tmp", delete=False
        ) as temporary:
            temporary.write(content)
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_name = temporary.name
        os.link(temporary_name, path)
        Path(temporary_name).unlink()
    except OSError as error:
        if temporary_name is not None:
            Path(temporary_name).unlink(missing_ok=True)
        raise FinalizationError(f"append-only output {path}: cannot write: {error}") from error


def write_documents(documents: dict[str, dict[str, Any]]) -> None:
    validate_documents(documents)
    existing = [reference for reference in documents if (ROOT / reference).exists()]
    if existing:
        raise FinalizationError(
            "append-only artifact already exists: " + ", ".join(sorted(existing))
        )
    for reference, document in sorted(documents.items()):
        _atomic_append(ROOT / reference, json_bytes(document))


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request-identity", required=True)
    parser.add_argument("--approved-by", required=True)
    parser.add_argument("--approved-at", required=True)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        documents = build_documents(
            request_identity=args.request_identity,
            approved_by=args.approved_by,
            approved_at=args.approved_at,
        )
        if args.check:
            for reference, expected in documents.items():
                path = ROOT / reference
                if not path.is_file() or path.read_bytes() != json_bytes(expected):
                    raise FinalizationError(f"{reference}: checked-in artifact mismatch")
        else:
            write_documents(documents)
    except FinalizationError as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}),
            file=sys.stderr,
        )
        return 2
    approval = documents[TRAINING_APPROVAL_REFERENCE]
    print(
        json.dumps(
            {
                "status": "APPROVED",
                "approvalIdentity": approval["artifactIdentity"],
                "requestIdentity": TRAINING_REQUEST_IDENTITY,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
