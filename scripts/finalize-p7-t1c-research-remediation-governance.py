#!/usr/bin/env python3
"""Finalize the approved P7-T4 remediation training request without widening scope."""
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
TRAINING_REQUEST_PATH = (
    ROOT
    / "config"
    / "p7-t4-research-remediation-governance-v2"
    / "training-approval-request.json"
)
PENDING_CARD_PATH = (
    ROOT
    / "config"
    / "p7-t4-research-remediation-governance-v2"
    / "training-dataset-card.pending.json"
)
SOURCE_EXPORT_PATH = (
    ROOT / "datasets" / "p7-t4-research-remediation-source-v2" / "source-export.json"
)
PROVENANCE_PATH = (
    ROOT / "datasets" / "p7-t4-research-remediation-source-v2" / "provenance.json"
)
TRAINING_CONTRACT_PATH = (
    ROOT / "datasets" / "p7-t4-research-remediation-source-v2" / "training-contract.json"
)
P7T1_CONFIG_PATH = ROOT / "config" / "p7-t1-research-remediation-dataset-pipeline-v2.json"

TRAINING_APPROVAL_REFERENCE = (
    "evidence/p7-t1c-research-remediation-training-governance-approval.json"
)
APPROVED_CARD_REFERENCE = (
    "config/p7-t1c-research-remediation-governance-v2/training-dataset-card.approved.json"
)
MATERIALIZED_DATASET_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v2"
TRAINING_REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v2/training-approval-request.json"
)
TRAINING_REQUEST_IDENTITY = (
    "64692cc3d48aa10676c39242a42efa212e692c5b05be18a0a66445c1a8b2ae09"
)
SOURCE_SHA256 = "6654416e24e190d6614fab7881e28ddd83a63284e08f1eef56c06b68de235bdb"
CONTENT_IDENTITY = "243352c24fb4aa5c95fad616a8cbd2e4062652854ec69619d3c426fb23f871af"
PROVENANCE_IDENTITY = "359b5836a34eb7fc1a91249df60583d687dad82104ab3dfe701b6057f01f4cc5"
CONTRACT_IDENTITY = "89e49c43fd6488a6d47473141ad9070bd0dd785e309bbdaf26246e41d277a145"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
TIMESTAMP_PATTERN = re.compile(
    r"^20[0-9]{2}-(0[1-9]|1[0-2])-([0-2][0-9]|3[0-1])T([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
)


class FinalizationError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def load_document(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise FinalizationError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise FinalizationError(f"{path.name}: object required")
    return value


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


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != "artifactIdentity"})
    )


def request_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != "requestIdentity"})
    )


def _valid_timestamp(value: object) -> bool:
    if not isinstance(value, str) or not TIMESTAMP_PATTERN.fullmatch(value):
        return False
    try:
        datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError:
        return False
    return True


def _inputs() -> dict[str, dict[str, Any]]:
    values = {
        "request": load_document(TRAINING_REQUEST_PATH),
        "pendingCard": load_document(PENDING_CARD_PATH),
        "source": load_document(SOURCE_EXPORT_PATH),
        "provenance": load_document(PROVENANCE_PATH),
        "contract": load_document(TRAINING_CONTRACT_PATH),
    }
    request = values["request"]
    provenance = values["provenance"]
    contract = values["contract"]
    diagnostics: list[str] = []
    if (
        request.get("requestIdentity") != TRAINING_REQUEST_IDENTITY
        or request_identity(request) != TRAINING_REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or any(request.get(field) is not None for field in ("approval", "approvedBy", "approvedAt"))
    ):
        diagnostics.append("request: exact pending request identity required")
    if sha256_bytes(SOURCE_EXPORT_PATH.read_bytes()) != SOURCE_SHA256:
        diagnostics.append("source: authoritative SHA-256 mismatch")
    if (
        provenance.get("contentIdentity") != CONTENT_IDENTITY
        or provenance.get("provenanceIdentity") != PROVENANCE_IDENTITY
        or provenance.get("contractIdentity") != CONTRACT_IDENTITY
        or provenance.get("governance", {}).get("trainingAuthorized") is not False
    ):
        diagnostics.append("source provenance: pending immutable identity mismatch")
    if (
        contract.get("contractIdentity") != CONTRACT_IDENTITY
        or sha256_bytes(
            canonical_bytes(
                {key: item for key, item in contract.items() if key != "contractIdentity"}
            )
        )
        != CONTRACT_IDENTITY
        or contract.get("scope", {}).get("frozenEvaluationDerivedRecordsAllowed") is not False
    ):
        diagnostics.append("training contract: immutable pending contract mismatch")
    source_block = request.get("source", {})
    if (
        source_block.get("sourceSha256") != SOURCE_SHA256
        or source_block.get("contentIdentity") != CONTENT_IDENTITY
        or source_block.get("provenanceIdentity") != PROVENANCE_IDENTITY
        or source_block.get("contractIdentity") != CONTRACT_IDENTITY
    ):
        diagnostics.append("request: source identity binding mismatch")
    if diagnostics:
        raise FinalizationError(diagnostics)
    return values


def _approval(
    request: dict[str, Any], approved_by: str, approved_at: str
) -> dict[str, Any]:
    scope = request["requestedScope"]
    approval = {
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-TRAINING-GOVERNANCE-APPROVAL",
        "schemaVersion": "1.0.0",
        "status": "APPROVED",
        "requestIdentity": request["requestIdentity"],
        "requestReference": TRAINING_REQUEST_REFERENCE,
        "purpose": "TRAINING",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "source": copy.deepcopy(request["source"]),
        "dataset": copy.deepcopy(request["candidateCard"]),
        "remediationBinding": copy.deepcopy(request["remediationBinding"]),
        "scope": {
            "assistantKey": scope["assistantKey"],
            "permittedPurposes": copy.deepcopy(scope["permittedPurposes"]),
            "includedUseCases": copy.deepcopy(scope["includedUseCases"]),
            "excludedUseCases": copy.deepcopy(scope["excludedUseCases"]),
            "modelDevelopmentOperation": scope["modelDevelopmentOperation"],
            "retentionClass": scope["requestedRetentionClass"],
            "frozenEvaluationTrainingUseAllowed": scope[
                "frozenEvaluationTrainingUseAllowed"
            ],
            "externalSharingAllowed": scope["externalSharingAllowed"],
            "productionPromptingAllowed": scope["productionPromptingAllowed"],
            "ragIngestionAllowed": scope["ragIngestionAllowed"],
        },
        "sourcePermission": {
            "status": "VERIFIED",
            "sourceDataOwner": scope["sourceDataOwner"],
            "evidenceReference": TRAINING_APPROVAL_REFERENCE,
        },
        "approval": {
            "decision": "APPROVED",
            "approvedBy": approved_by,
            "approvedAt": approved_at,
        },
        "revocation": {"status": "ACTIVE", "authority": APPROVAL_AUTHORITY},
        "repositoryBaseCommit": request["repositoryBaseCommit"],
        "artifactIdentity": "",
    }
    approval["artifactIdentity"] = artifact_identity(approval)
    return approval


def _approved_card(pending: dict[str, Any], approval: dict[str, Any]) -> dict[str, Any]:
    card = copy.deepcopy(pending)
    card.update(
        {
            "title": "Approved contract-aligned synthetic Research remediation dataset",
            "description": "Synthetic UC003-UC005 source approved exclusively for P7-T1C/P7-T2 remediation TRAINING.",
            "permitted_purposes": ["DEVELOPMENT_TEST", "TRAINING"],
            "approved_purposes": ["TRAINING"],
            "source_permission_references": [TRAINING_APPROVAL_REFERENCE],
            "source_permission_status": "VERIFIED",
            "approval_references": [TRAINING_APPROVAL_REFERENCE],
            "approval_status": "APPROVED",
            "lifecycle_status": "APPROVED",
            "created_at_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval/approvedAt",
            "revocation_reference": f"{TRAINING_APPROVAL_REFERENCE}#/revocation",
        }
    )
    card["integrity"]["verified_at_reference"] = (
        f"{TRAINING_APPROVAL_REFERENCE}#/source/sourceSha256"
    )
    card["retention"].update(
        {
            "trigger_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval",
            "evidence_reference": "datasets/p7-t4-research-remediation-source-v2/provenance.json#/governance",
            "start_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval/approvedAt",
            "recheck_or_expiry_reference": f"{TRAINING_APPROVAL_REFERENCE}#/revocation",
        }
    )
    for decision in card["sanitization"]["field_decisions"]:
        decision["reviewer_reference"] = TRAINING_APPROVAL_REFERENCE
    card["sanitization"]["reviewer_reference"] = TRAINING_APPROVAL_REFERENCE
    return card


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    expected = {TRAINING_APPROVAL_REFERENCE, APPROVED_CARD_REFERENCE}
    if set(documents) != expected:
        raise FinalizationError("finalization: exact artifact inventory required")
    inputs = _inputs()
    approval = documents[TRAINING_APPROVAL_REFERENCE]
    card = documents[APPROVED_CARD_REFERENCE]
    approval_record = approval.get("approval", {})
    approved_by = approval_record.get("approvedBy")
    approved_at = approval_record.get("approvedAt")
    diagnostics: list[str] = []
    if approved_by != APPROVAL_AUTHORITY or not _valid_timestamp(approved_at):
        diagnostics.append("approval: exact authority and real UTC timestamp required")
    if approval != _approval(inputs["request"], approved_by, approved_at):
        diagnostics.append("approval: exact request-bound artifact required")
    if card != _approved_card(inputs["pendingCard"], approval):
        diagnostics.append("approved card: exact source-bound transition required")
    if (
        approval.get("scope", {}).get("permittedPurposes") != ["TRAINING"]
        or approval.get("scope", {}).get("includedUseCases")
        != ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"]
        or approval.get("scope", {}).get("excludedUseCases")
        != {"RESEARCH_UC_006": "TRAINING_PROHIBITED_BY_CURRENT_GOVERNANCE"}
        or approval.get("scope", {}).get("frozenEvaluationTrainingUseAllowed") is not False
    ):
        diagnostics.append("approval: exact narrow remediation scope required")
    rendered = canonical_bytes(documents).lower()
    if (
        b"evals/p7-t3-research-gap-evaluation-suite" in rendered
        or b"evidence/p7-t3-research-report-eval-governance-approval" in rendered
    ):
        diagnostics.append("approval: frozen evaluation source reuse is forbidden")
    if diagnostics:
        raise FinalizationError(diagnostics)


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
    approval = _approval(inputs["request"], approved_by, approved_at)
    documents = {
        TRAINING_APPROVAL_REFERENCE: approval,
        APPROVED_CARD_REFERENCE: _approved_card(inputs["pendingCard"], approval),
    }
    validate_documents(documents)
    return documents


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
        temporary_name = None
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


def finalize_repository(
    *, request_identity: str, approved_by: str, approved_at: str
) -> dict[str, dict[str, Any]]:
    documents = build_documents(
        request_identity=request_identity,
        approved_by=approved_by,
        approved_at=approved_at,
    )
    write_documents(documents)
    return documents


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request-identity", required=True)
    parser.add_argument("--approved-by", required=True)
    parser.add_argument("--approved-at", required=True)
    args = parser.parse_args()
    try:
        documents = finalize_repository(
            request_identity=args.request_identity,
            approved_by=args.approved_by,
            approved_at=args.approved_at,
        )
        approval = documents[TRAINING_APPROVAL_REFERENCE]
        print(
            json.dumps(
                {
                    "status": "APPROVED",
                    "requestIdentity": approval["requestIdentity"],
                    "trainingApprovalIdentity": approval["artifactIdentity"],
                    "trainingAllowed": True,
                },
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except FinalizationError as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
