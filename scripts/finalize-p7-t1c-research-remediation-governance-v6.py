#!/usr/bin/env python3
"""Finalize the exact approved P7-T4 remediation v6 training request."""
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
    "config/p7-t4-research-remediation-governance-v6/training-approval-request.json"
)
PENDING_CARD_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/"
    "training-dataset-card.pending.json"
)
PENDING_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v6/manifest.json"
)
SOURCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v6/source-export.json"
PROVENANCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v6/provenance.json"
CONTRACT_REFERENCE = "datasets/p7-t4-research-remediation-source-v6/training-contract.json"
PROMPT_PROFILE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/"
    "research-prompt-profile-v3.approved.json"
)
PREPARATION_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v6-governance-approval.json"
)
TRAINING_APPROVAL_REFERENCE = (
    "evidence/p7-t1c-research-remediation-v6-training-governance-approval.json"
)
APPROVED_CARD_REFERENCE = (
    "config/p7-t1c-research-remediation-governance-v6/"
    "training-dataset-card.approved.json"
)
APPROVED_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v6/manifest.approved.json"
)
TRAINING_REQUEST_IDENTITY = (
    "2f5e30613ec08a5ff14444cc45dfe4e35b2daae8bcea728e64e54785548a75c8"
)
DATASET_IDENTITY = "7a0c264196889beb0c91414cd10195681df895073dc7ce3aeef586123de751c1"
PENDING_MANIFEST_IDENTITY = (
    "7963f1661e4d49cd6845026451fbad208243604462f5ef3e5a0ee4e3f6fd33a0"
)
PENDING_CARD_IDENTITY = (
    "544eb15afee0b9a009dbfeafa77cdb39a9249d035350cc5a666b4c2e524e1625"
)
SOURCE_IDENTITY = "f20f0437e697801d71fe24671e5411c2f097245ab86be0b68a3e441cf71860f5"
PROVENANCE_IDENTITY = "8a0462abf6e8d0128ce487dff6776776e2349fb76262456f2862c80dcbaa94d6"
TRAINING_CONTRACT_IDENTITY = (
    "960db4ecf481348361ade47e90e46447ab777597edc12600db56f28080e09335"
)
PREPARATION_APPROVAL_IDENTITY = (
    "5522f5f68b4f7d85c15a0a139625dc79d4b80a0bb60665480797e3485b78e91c"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)
QUALITY_IDENTITY = "e5471fbf05010f389d0c16fd407bff2c2b8811ed997c3284d1c6daa580b2f4dc"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY"
INCLUDED_USE_CASES = [f"RESEARCH_UC_{number:03d}" for number in range(1, 7)]
SPLIT_COUNTS = {"evaluation": 48, "train": 288, "validation": 48}
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


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any], field: str = "artifactIdentity") -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != field}))


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
    request = values["request"]
    manifest = values["manifest"]
    diagnostics: list[str] = []
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
    ):
        diagnostics.append("request: exact pending v6 request identity required")
    if (
        manifest.get("artifactIdentity") != PENDING_MANIFEST_IDENTITY
        or artifact_identity(manifest) != PENDING_MANIFEST_IDENTITY
        or manifest.get("datasetIdentity") != DATASET_IDENTITY
        or manifest.get("recordCounts") != SPLIT_COUNTS
        or manifest.get("trainingAuthorized") is not False
        or manifest.get("approval_status") != "PENDING_TRAINING_APPROVAL"
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        diagnostics.append("manifest: exact pending v6 identity required")
    card = values["card"]
    if (
        card.get("artifactIdentity") != PENDING_CARD_IDENTITY
        or artifact_identity(card) != PENDING_CARD_IDENTITY
        or card.get("datasetIdentity") != DATASET_IDENTITY
        or card.get("recordCounts") != SPLIT_COUNTS
        or card.get("disposition") != "PENDING_TRAINING_APPROVAL"
    ):
        diagnostics.append("card: exact pending v6 identity required")
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
        or contract.get("promptProfileIdentity") != PROMPT_PROFILE_IDENTITY
        or contract.get("trainingRecordCount") != 288
        or contract.get("validationRecordCount") != 48
        or contract.get("evaluationRecordCount") != 48
        or contract.get("frozenEvaluationUseAllowed") is not False
        or contract.get("runtimeNormalizationAllowed") is not False
    ):
        diagnostics.append("contract: exact immutable v6 restrictions required")
    if diagnostics:
        raise FinalizationError(diagnostics)
    return values


def _approval(request: dict[str, Any], approved_by: str, approved_at: str) -> dict[str, Any]:
    value: dict[str, Any] = {
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V6-TRAINING-GOVERNANCE-APPROVAL",
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
            "includedUseCases": copy.deepcopy(INCLUDED_USE_CASES),
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
            "checksum": "",
        }
    )
    manifest.pop("artifactIdentity", None)
    manifest["checksum"] = artifact_identity(manifest, "checksum")
    return manifest


def build_documents(*, request_identity: str, approved_by: str, approved_at: str) -> dict[str, dict[str, Any]]:
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
        APPROVED_CARD_REFERENCE: _approved_card(inputs["card"], approval),
        APPROVED_MANIFEST_REFERENCE: _approved_manifest(inputs["manifest"], approval),
    }
    validate_documents(documents)
    return documents


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    expected = {TRAINING_APPROVAL_REFERENCE, APPROVED_CARD_REFERENCE, APPROVED_MANIFEST_REFERENCE}
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
    if documents[APPROVED_CARD_REFERENCE] != _approved_card(inputs["card"], approval):
        diagnostics.append("approved card: exact pending-card transition required")
    if documents[APPROVED_MANIFEST_REFERENCE] != _approved_manifest(inputs["manifest"], approval):
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
                if not (ROOT / reference).is_file() or (ROOT / reference).read_bytes() != json_bytes(expected):
                    raise FinalizationError(f"{reference}: checked-in artifact mismatch")
        else:
            write_documents(documents)
    except FinalizationError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}), file=sys.stderr)
        return 2
    approval = documents[TRAINING_APPROVAL_REFERENCE]
    print(json.dumps({"status": "APPROVED", "approvalIdentity": approval["artifactIdentity"], "requestIdentity": TRAINING_REQUEST_IDENTITY}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
