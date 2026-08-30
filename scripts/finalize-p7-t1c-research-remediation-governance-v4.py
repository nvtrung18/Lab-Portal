#!/usr/bin/env python3
"""Finalize the exact approved P7-T4 remediation v4 training request."""
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
REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/training-approval-request.json"
)
PENDING_CARD_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/training-dataset-card.pending.json"
)
PENDING_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v4/manifest.json"
SOURCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v4/source-export.json"
PROVENANCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v4/provenance.json"
CONTRACT_REFERENCE = "datasets/p7-t4-research-remediation-source-v4/training-contract.json"
TRAINING_APPROVAL_REFERENCE = (
    "evidence/p7-t1c-research-remediation-v4-training-governance-approval.json"
)
APPROVED_CARD_REFERENCE = (
    "config/p7-t1c-research-remediation-governance-v4/training-dataset-card.approved.json"
)
APPROVED_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v4/manifest.approved.json"
)
TRAINING_REQUEST_IDENTITY = (
    "d052e13698d1d4902a0b5018b2563889d2ea8fb52c55f0112d193efd088f6a9e"
)
PENDING_MANIFEST_IDENTITY = (
    "2389e2b8c97dd719cce0bd98e51b3769e333489d637096b4e40c68bf3826a9ae"
)
SOURCE_SHA256 = "d4ade7ef34df3cff59d99e63842ae1879b51d2d8693adbf25e25be8723b8f6b3"
CONTENT_IDENTITY = "b3a9814071b8344c33e350d7217a0286d3136248d2ec79c98f7379f097a2f4c4"
PROVENANCE_IDENTITY = "1652394aa1542dd5a5fb809d3ca70a78e7e374c9c6f029cd57825db93a7cc336"
CONTRACT_IDENTITY = "c535dca968c54b9206b91eb7969036d4c6973ab91a3b8bc5f9b165699b4f79e5"
GOVERNANCE_APPROVAL_IDENTITY = (
    "ee8576fa361c3617dba9a019579a1e19b73eac0d1cd9d01226c519e7f3fa52e3"
)
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
INCLUDED_USE_CASES = [
    "RESEARCH_UC_003",
    "RESEARCH_UC_004",
    "RESEARCH_UC_005",
    "RESEARCH_UC_006",
]
TIMESTAMP_PATTERN = re.compile(
    r"^20[0-9]{2}-(0[1-9]|1[0-2])-([0-2][0-9]|3[0-1])T([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
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


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any], field: str = "artifactIdentity") -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != field}))


def load_document(reference: str) -> dict[str, Any]:
    path = ROOT / reference
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
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
        "pendingCard": load_document(PENDING_CARD_REFERENCE),
        "pendingManifest": load_document(PENDING_MANIFEST_REFERENCE),
        "source": load_document(SOURCE_REFERENCE),
        "provenance": load_document(PROVENANCE_REFERENCE),
        "contract": load_document(CONTRACT_REFERENCE),
    }
    request = values["request"]
    manifest = values["pendingManifest"]
    provenance = values["provenance"]
    contract = values["contract"]
    diagnostics: list[str] = []
    if (
        request.get("requestIdentity") != TRAINING_REQUEST_IDENTITY
        or artifact_identity(request, "requestIdentity") != TRAINING_REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or request.get("approval") is not None
        or request.get("currentState", {}).get("trainingAuthorized") is not False
    ):
        diagnostics.append("request: exact pending request identity required")
    source_binding = request.get("source", {})
    if source_binding != {
        "contentIdentity": CONTENT_IDENTITY,
        "contractIdentity": CONTRACT_IDENTITY,
        "provenanceIdentity": PROVENANCE_IDENTITY,
        "reference": SOURCE_REFERENCE,
        "sha256": SOURCE_SHA256,
    }:
        diagnostics.append("request: exact source identity binding required")
    if sha256_bytes((ROOT / SOURCE_REFERENCE).read_bytes()) != SOURCE_SHA256:
        diagnostics.append("source: authoritative SHA-256 mismatch")
    if (
        provenance.get("artifactIdentity") != PROVENANCE_IDENTITY
        or artifact_identity(provenance) != PROVENANCE_IDENTITY
        or provenance.get("trainingAuthorized") is not False
        or provenance.get("frozenEvaluationContentUsed") is not False
        or provenance.get("fullySynthetic") is not True
    ):
        diagnostics.append("provenance: exact prepared synthetic identity required")
    if (
        contract.get("artifactIdentity") != CONTRACT_IDENTITY
        or artifact_identity(contract) != CONTRACT_IDENTITY
        or contract.get("state") != "PREPARED_AWAITING_TRAINING_APPROVAL"
        or contract.get("trainingAuthorized") is not False
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("schemaBundle") != "research-assistant-output-v2"
    ):
        diagnostics.append("training contract: exact prepared identity required")
    if (
        manifest.get("manifestIdentity") != PENDING_MANIFEST_IDENTITY
        or artifact_identity(manifest, "manifestIdentity") != PENDING_MANIFEST_IDENTITY
        or manifest.get("trainingAuthorized") is not False
        or manifest.get("approval_status") != "PENDING"
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        diagnostics.append("manifest: exact pending identity required")
    dataset = request.get("dataset", {})
    if (
        dataset.get("manifestIdentity") != PENDING_MANIFEST_IDENTITY
        or dataset.get("manifestReference") != PENDING_MANIFEST_REFERENCE
        or dataset.get("recordCount") != 144
    ):
        diagnostics.append("request: exact dataset identity binding required")
    if request.get("governanceAmendmentApprovalReference") != (
        "evidence/p7-t4-research-remediation-v4-governance-approval.json"
    ):
        diagnostics.append("request: governance amendment binding required")
    governance = load_document(request["governanceAmendmentApprovalReference"])
    if (
        governance.get("artifactIdentity") != GOVERNANCE_APPROVAL_IDENTITY
        or artifact_identity(governance) != GOVERNANCE_APPROVAL_IDENTITY
        or governance.get("status") != "APPROVED"
        or governance.get("revocation", {}).get("status") != "ACTIVE"
    ):
        diagnostics.append("governance amendment: exact active approval required")
    if diagnostics:
        raise FinalizationError(diagnostics)
    return values


def _approval(request: dict[str, Any], approved_by: str, approved_at: str) -> dict[str, Any]:
    value: dict[str, Any] = {
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V4-TRAINING-GOVERNANCE-APPROVAL",
        "schemaVersion": "1.0.0",
        "status": "APPROVED",
        "requestIdentity": request["requestIdentity"],
        "requestReference": REQUEST_REFERENCE,
        "purpose": "TRAINING",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "governanceAmendmentApprovalIdentity": GOVERNANCE_APPROVAL_IDENTITY,
        "governanceAmendmentApprovalReference": request["governanceAmendmentApprovalReference"],
        "source": copy.deepcopy(request["source"]),
        "dataset": copy.deepcopy(request["dataset"]),
        "scope": {
            "assistantKey": "RESEARCH_ASSISTANT",
            "includedUseCases": copy.deepcopy(INCLUDED_USE_CASES),
            "syntheticCategoryOnly": "CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC",
            "realReportMetadataUseAllowed": False,
            "privateResearchDocumentUseAllowed": False,
            "productionDataUseAllowed": False,
            "frozenEvaluationTrainingUseAllowed": False,
            "contractHoldoutUsedForOptimization": False,
            "candidateDispositionAfterTraining": "CANDIDATE_ONLY",
        },
        "authorization": {
            "externalTrainingAllowed": True,
            "evaluationAllowed": False,
            "promotionAllowed": False,
            "productionPromptingAllowed": False,
            "runtimeSchemaActivationAllowed": False,
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


def _approved_card(pending: dict[str, Any]) -> dict[str, Any]:
    card = copy.deepcopy(pending)
    card.update(
        {
            "title": "Approved P7-T4 remediation v4 synthetic Research training dataset",
            "permitted_purposes": ["DEVELOPMENT_TEST", "TRAINING"],
            "approved_purposes": ["TRAINING"],
            "prohibited_purposes": [],
            "source_permission_references": [TRAINING_APPROVAL_REFERENCE],
            "source_permission_status": "VERIFIED",
            "approval_references": [TRAINING_APPROVAL_REFERENCE],
            "approval_status": "APPROVED",
            "lifecycle_status": "APPROVED",
            "trainingAuthorized": True,
            "created_at_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval/approvedAt",
            "revocation_reference": f"{TRAINING_APPROVAL_REFERENCE}#/revocation",
        }
    )
    return card


def _approved_manifest(pending: dict[str, Any], approval: dict[str, Any]) -> dict[str, Any]:
    manifest = copy.deepcopy(pending)
    manifest.update(
        {
            "prepared_manifest_identity": pending["manifestIdentity"],
            "card_reference": APPROVED_CARD_REFERENCE,
            "approval_authority": APPROVAL_AUTHORITY,
            "approval_references": [TRAINING_APPROVAL_REFERENCE],
            "approval_status": "APPROVED",
            "approved_purposes": ["TRAINING"],
            "permitted_purposes": ["DEVELOPMENT_TEST", "TRAINING"],
            "prohibited_purposes": [],
            "lifecycle_status": "APPROVED",
            "trainingAuthorized": True,
            "training_approval_identity": approval["artifactIdentity"],
            "checksum_algorithm": "SHA-256",
            "checksum": "",
        }
    )
    manifest.pop("manifestIdentity", None)
    manifest["checksum"] = artifact_identity(manifest, "checksum")
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
    approval = _approval(inputs["request"], approved_by, approved_at)
    documents = {
        TRAINING_APPROVAL_REFERENCE: approval,
        APPROVED_CARD_REFERENCE: _approved_card(inputs["pendingCard"]),
        APPROVED_MANIFEST_REFERENCE: _approved_manifest(inputs["pendingManifest"], approval),
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
    elif approval != _approval(inputs["request"], approved_by, approved_at):
        diagnostics.append("approval: exact request-bound artifact required")
    if documents[APPROVED_CARD_REFERENCE] != _approved_card(inputs["pendingCard"]):
        diagnostics.append("approved card: exact pending-card transition required")
    if documents[APPROVED_MANIFEST_REFERENCE] != _approved_manifest(
        inputs["pendingManifest"], approval
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
        raise FinalizationError("append-only artifact already exists: " + ", ".join(sorted(existing)))
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
                    "externalTrainingAllowed": True,
                    "evaluationAllowed": False,
                    "promotionAllowed": False,
                },
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except FinalizationError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
