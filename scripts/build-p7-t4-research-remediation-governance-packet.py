#!/usr/bin/env python3
"""Build the pending governance request for the P7-T4 remediation source."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
SOURCE_BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source.py"
SOURCE_DIRECTORY = ROOT / "datasets" / "p7-t4-research-remediation-source-v2"
GOVERNANCE_PATH = ROOT / "docs" / "architecture" / "ai" / "data-governance.yml"
APPROVED_EVALUATION_MANIFEST_PATH = (
    ROOT / "config" / "p7-t1c-research-governance-v1" / "frozen-evaluation-manifest.approved.json"
)
REMEDIATION_PATH = ROOT / "config" / "p7-t4-research-remediation.json"
CANONICAL_OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v2"
DATASET_ID = "p7-research-synthetic-training-dataset"
DATASET_VERSION = "2.0.0"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
CATEGORY_IDS = ["CAT_RESEARCH_ASSIGNED_TASK", "CAT_RESEARCH_DRAFT_CONTEXT"]
INCLUDED_USE_CASES = ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"]
EXCLUDED_USE_CASES = {
    "RESEARCH_UC_006": "TRAINING_PROHIBITED_BY_CURRENT_GOVERNANCE"
}
SOURCE_BASE_COMMIT = "804b18ec8336bf91cc0f71c2661683da48540188"
PRIOR_REMEDIATION_IDENTITY = "1a6546642298c983e426b79648a3b6430f3ebd16409c8f873754243257f1af1a"


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


SOURCE = _load_module("p7_t4_source_for_governance_packet", SOURCE_BUILDER_PATH)


class PacketBuildError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def load_document(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
        value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    except (OSError, UnicodeError, json.JSONDecodeError, yaml.YAMLError) as error:
        raise PacketBuildError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise PacketBuildError(f"{path.name}: object required")
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
        raise PacketBuildError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def request_identity(request: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes({key: value for key, value in request.items() if key != "requestIdentity"})
    )


def _authoritative_source() -> dict[str, Any]:
    generated = SOURCE.build_documents()
    diagnostics: list[str] = []
    for filename, document in generated.items():
        path = SOURCE_DIRECTORY / filename
        if not path.is_file() or path.read_bytes() != SOURCE.json_bytes(document):
            diagnostics.append(f"source-bound artifact mismatch: {filename}")
    if diagnostics:
        raise PacketBuildError(diagnostics)
    export = generated["source-export.json"]
    provenance = generated["provenance.json"]
    contract = generated["training-contract.json"]
    source_sha256 = sha256_bytes((SOURCE_DIRECTORY / "source-export.json").read_bytes())
    if (
        provenance.get("artifacts", [{}, {}])[1].get("sha256") != source_sha256
        or provenance.get("contentIdentity") != sha256_bytes(canonical_bytes(export["records"]))
        or provenance.get("contractIdentity") != contract.get("contractIdentity")
        or provenance.get("governance", {}).get("trainingAuthorized") is not False
    ):
        raise PacketBuildError("source-bound immutable identity mismatch")
    return {
        "export": export,
        "provenance": provenance,
        "contract": contract,
        "sourceSha256": source_sha256,
    }


def _sanitization() -> dict[str, Any]:
    transform = "scripts/build-p7-t4-research-remediation-source.py"
    result = "datasets/p7-t4-research-remediation-source-v2/provenance.json#/antiLeakage"
    residual = "datasets/p7-t4-research-remediation-source-v2/provenance.json#/coverage"
    reviewer = "P7-T4-REMEDIATION-AWAITING-GOVERNANCE-REVIEW"
    return {
        "disposition": "SYNTHETIC_GENERATION_ONLY",
        "field_decisions": [
            {
                "category_id": category_id,
                "field_decision": "SYNTHETIC_REPLACE",
                "transform_reference": transform,
                "reviewer_reference": reviewer,
                "result_reference": result,
                "residual_risk_reference": residual,
            }
            for category_id in CATEGORY_IDS
        ],
        "transform_reference": transform,
        "reviewer_reference": reviewer,
        "result_reference": result,
        "residual_risk_reference": residual,
    }


def _card(source: dict[str, Any]) -> dict[str, Any]:
    evaluation = load_document(APPROVED_EVALUATION_MANIFEST_PATH)
    provenance = source["provenance"]
    source_reference = "datasets/p7-t4-research-remediation-source-v2/source-export.json"
    provenance_reference = "datasets/p7-t4-research-remediation-source-v2/provenance.json"
    contract_reference = "datasets/p7-t4-research-remediation-source-v2/training-contract.json"
    return {
        "dataset_id": DATASET_ID,
        "dataset_version": DATASET_VERSION,
        "contract_version": "1.0.0",
        "schema_version": "1.0.0",
        "title": "Pending contract-aligned synthetic Research remediation dataset",
        "description": "Synthetic UC003-UC005 candidate for a new P7-T2 run; no approval, training authorization, or P7-T4 PASS is asserted.",
        "assistant_key": "RESEARCH_ASSISTANT",
        "partition": "RESEARCH_ASSISTANT",
        "visibility": "RESEARCH_ASSISTANT_ONLY",
        "category_ids": CATEGORY_IDS,
        "classification": "SENSITIVE",
        "use_decision": "SYNTHETIC_ONLY",
        "permitted_purposes": ["DEVELOPMENT_TEST"],
        "prohibited_purposes": [],
        "approved_purposes": [],
        "model_development_purpose": "TRAINING",
        "model_development_operation": "ADAPTER_FINE_TUNING",
        "source_data_owner": "RESEARCH_GOVERNANCE_DOMAIN_DATA_OWNER",
        "dataset_steward": "RESEARCH_GOVERNANCE_DATASET_STEWARD",
        "approval_authority": APPROVAL_AUTHORITY,
        "source_permission_references": [],
        "source_permission_status": "NOT_ASSESSED",
        "approval_references": [],
        "approval_status": "PENDING",
        "lifecycle_status": "PENDING_APPROVAL",
        "freeze_status": "NOT_REQUIRED",
        "retention": {
            "retention_class": "EPHEMERAL_DEVELOPMENT",
            "trigger_reference": "P7-T4-REMEDIATION-TRAINING-COMPLETION-OR-REQUEST-DISPOSITION",
            "evidence_reference": "P7-T4-REMEDIATION-RETENTION-EVIDENCE-REQUIRED",
            "start_reference": "P7-T4-REMEDIATION-RETENTION-START-REFERENCE-REQUIRED",
            "recheck_or_expiry_reference": "P7-T4-REMEDIATION-POST-EXPERIMENT-RECHECK-REQUIRED",
            "disposition_action": "DELETE",
            "disposition_owner": "RESEARCH_GOVERNANCE_DATASET_STEWARD",
        },
        "sanitization": _sanitization(),
        "provenance": {"type": "SYNTHETIC"},
        "lineage": {
            "source_references": [
                f"{source_reference}#sha256={source['sourceSha256']}",
                f"{provenance_reference}#identity={provenance['provenanceIdentity']}",
                f"{contract_reference}#identity={source['contract']['contractIdentity']}",
                f"p7-t4-remediation-content-sha256:{provenance['contentIdentity']}",
            ],
            "transform_references": ["scripts/build-p7-t4-research-remediation-source.py"],
        },
        "integrity": {
            "checksum": source["sourceSha256"],
            "checksum_algorithm": "SHA-256",
            "verified_at_reference": "P7-T4-REMEDIATION-DETERMINISTIC-SOURCE-VERIFICATION",
        },
        "split": {"label": "TRAIN"},
        "deduplication": {"disposition": "NO_CANDIDATE_OVERLAP"},
        "evaluation_freeze_prerequisite": {
            "evaluation_dataset_id": evaluation["dataset_id"],
            "evaluation_dataset_version": evaluation["dataset_version"],
            "evaluation_purpose": evaluation["model_development_purpose"],
            "evaluation_lifecycle_status": evaluation["lifecycle_status"],
            "evaluation_freeze_status": evaluation["freeze_status"],
            "evaluation_integrity_checksum": evaluation["integrity"]["checksum"],
            "evaluation_approval_reference": evaluation["approval_references"][0],
        },
        "limitations_and_bias": f"{provenance_reference}#/coverage",
        "created_at_reference": "P7-T4-REMEDIATION-NOT-RECORDED-DETERMINISTIC-REQUEST",
        "revocation_reference": "P7-T4-REMEDIATION-REVOCATION-EVIDENCE-REQUIRED",
    }


def _request(source: dict[str, Any], card: dict[str, Any]) -> dict[str, Any]:
    remediation = load_document(REMEDIATION_PATH)
    provenance = source["provenance"]
    request = {
        "artifactType": "P7-T1B-RESEARCH-REMEDIATION-TRAINING-GOVERNANCE-REQUEST",
        "schemaVersion": "1.0.0",
        "requestId": "P7-T1B-RESEARCH-REMEDIATION-TRAINING-GOVERNANCE-REQUEST-001",
        "status": "PENDING_USER_APPROVAL",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "source": {
            "sourceId": SOURCE.SOURCE_ID,
            "sourceVersion": SOURCE.SOURCE_VERSION,
            "sourceReference": "datasets/p7-t4-research-remediation-source-v2/source-export.json",
            "sourceSha256": source["sourceSha256"],
            "contentIdentity": provenance["contentIdentity"],
            "provenanceReference": "datasets/p7-t4-research-remediation-source-v2/provenance.json",
            "provenanceIdentity": provenance["provenanceIdentity"],
            "contractReference": "datasets/p7-t4-research-remediation-source-v2/training-contract.json",
            "contractIdentity": source["contract"]["contractIdentity"],
            "recordCount": provenance["inventory"]["recordCount"],
            "fullySynthetic": True,
            "evaluationMaterialCopied": False,
        },
        "candidateCard": {
            "datasetId": DATASET_ID,
            "datasetVersion": DATASET_VERSION,
            "reference": "config/p7-t4-research-remediation-governance-v2/training-dataset-card.pending.json",
            "candidateCardIdentity": sha256_bytes(canonical_bytes(card)),
        },
        "remediationBinding": {
            "reference": "config/p7-t4-research-remediation.json",
            "priorRemediationIdentity": PRIOR_REMEDIATION_IDENTITY,
            "failedCandidateId": remediation["failedCandidate"]["candidateId"],
            "failedComparisonIdentity": remediation["failedCandidate"]["comparisonIdentity"],
            "evaluationFreezeIdentity": remediation["evaluationFreeze"]["sourceInventory"]["identity"],
            "evaluationSuite": remediation["evaluationFreeze"]["suite"],
            "frozenEvaluationUnchanged": True,
        },
        "currentState": {
            "sourceState": "SOURCE_READY",
            "governanceState": "AWAITING_GOVERNANCE_APPROVAL",
            "approvalStatus": "PENDING",
            "lifecycleStatus": "PENDING_APPROVAL",
            "sourcePermissionStatus": "NOT_ASSESSED",
            "currentPermittedPurposes": ["DEVELOPMENT_TEST"],
            "datasetMaterialized": False,
            "trainingAuthorized": False,
        },
        "requestedScope": {
            "assistantKey": "RESEARCH_ASSISTANT",
            "sourceDataOwner": "RESEARCH_GOVERNANCE_DOMAIN_DATA_OWNER",
            "datasetSteward": "RESEARCH_GOVERNANCE_DATASET_STEWARD",
            "requestedUseDecision": "SYNTHETIC_ONLY",
            "permittedPurposes": ["TRAINING"],
            "includedUseCases": INCLUDED_USE_CASES,
            "excludedUseCases": EXCLUDED_USE_CASES,
            "modelDevelopmentOperation": "ADAPTER_FINE_TUNING",
            "requestedRetentionClass": "EPHEMERAL_DEVELOPMENT",
            "intendedDownstreamUse": [
                "P7-T1C_REMEDIATION_DATASET_MATERIALIZATION",
                "P7-T2_RESEARCH_QLORA_RETRAINING",
                "P7-T4_UNCHANGED_INDEPENDENT_REEVALUATION",
            ],
            "frozenEvaluationTrainingUseAllowed": False,
            "externalSharingAllowed": False,
            "productionPromptingAllowed": False,
            "ragIngestionAllowed": False,
        },
        "repositoryBaseCommit": SOURCE_BASE_COMMIT,
        "requester": "P7_T4_REMEDIATION_IMPLEMENTATION_AGENT",
        "approval": None,
        "approvedBy": None,
        "approvedAt": None,
        "requestIdentity": "",
    }
    request["requestIdentity"] = request_identity(request)
    return request


def _validate_card(card: dict[str, Any], source: dict[str, Any]) -> list[str]:
    governance = load_document(GOVERNANCE_PATH)["contract"]
    required = set(governance["dataset_card_contract"]["required_fields"]) | {
        "evaluation_freeze_prerequisite"
    }
    diagnostics: list[str] = []
    if set(card) != required:
        diagnostics.append("pending card: exact native dataset-card fields required")
    if (
        card.get("dataset_version") != DATASET_VERSION
        or card.get("approval_status") != "PENDING"
        or card.get("lifecycle_status") != "PENDING_APPROVAL"
        or card.get("source_permission_status") != "NOT_ASSESSED"
        or card.get("permitted_purposes") != ["DEVELOPMENT_TEST"]
        or any(card.get(field) for field in ("approval_references", "source_permission_references", "approved_purposes"))
    ):
        diagnostics.append("pending card: unresolved approval boundary required")
    matrix = {item["category_id"]: item for item in governance["data_governance_matrix"]}
    for category_id in CATEGORY_IDS:
        category = matrix.get(category_id, {})
        expected = {
            "partition": category.get("source_partition"),
            "visibility": category.get("visibility"),
            "classification": category.get("classification"),
            "use_decision": category.get("use_decision"),
            "source_data_owner": category.get("source_data_owner"),
            "dataset_steward": category.get("dataset_steward"),
            "approval_authority": category.get("approval_authority"),
        }
        if any(card.get(field) != value for field, value in expected.items()):
            diagnostics.append(f"pending card/{category_id}: governance matrix mismatch")
    report = matrix.get("CAT_RESEARCH_REPORT_METADATA", {})
    if report.get("use_decision") != "DEFERRED" or "TRAINING" not in report.get("prohibited_purposes", []):
        diagnostics.append("pending card: report-review TRAINING must remain deferred")
    if card.get("integrity", {}).get("checksum") != source["sourceSha256"]:
        diagnostics.append("pending card: source checksum mismatch")
    return diagnostics


def build_documents() -> dict[str, dict[str, Any]]:
    source = _authoritative_source()
    card = _card(source)
    documents = {
        "training-approval-request.json": _request(source, card),
        "training-dataset-card.pending.json": card,
    }
    validate_documents(documents)
    return documents


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    if set(documents) != {"training-approval-request.json", "training-dataset-card.pending.json"}:
        raise PacketBuildError("packet: exact artifact inventory required")
    source = _authoritative_source()
    card = documents["training-dataset-card.pending.json"]
    request = documents["training-approval-request.json"]
    diagnostics = _validate_card(card, source)
    if request != _request(source, card):
        diagnostics.append("training request: exact source-bound pending request required")
    if (
        request.get("status") != "PENDING_USER_APPROVAL"
        or any(request.get(field) is not None for field in ("approval", "approvedBy", "approvedAt"))
        or request.get("currentState", {}).get("trainingAuthorized") is not False
        or request.get("requestIdentity") != request_identity(request)
    ):
        diagnostics.append("training request: pending non-authorized state required")
    scope = request.get("requestedScope", {})
    if (
        scope.get("includedUseCases") != INCLUDED_USE_CASES
        or scope.get("excludedUseCases") != EXCLUDED_USE_CASES
        or scope.get("frozenEvaluationTrainingUseAllowed") is not False
    ):
        diagnostics.append("training request: exact source-bound scope required")
    rendered = canonical_bytes(documents).lower()
    for forbidden in (
        b"evals/p7-t3-research-gap-evaluation-suite",
        b"evidence/p7-t3-research-report-eval-governance-approval",
    ):
        if forbidden in rendered:
            diagnostics.append("packet: frozen evaluation source reuse is forbidden")
    if diagnostics:
        raise PacketBuildError(diagnostics)


def build_artifacts() -> dict[str, bytes]:
    return {filename: json_bytes(value) for filename, value in build_documents().items()}


def write_packet(output_directory: Path) -> dict[str, dict[str, Any]]:
    if output_directory.exists():
        raise PacketBuildError("output directory must not already exist")
    documents = build_documents()
    artifacts = {filename: json_bytes(value) for filename, value in documents.items()}
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(
            prefix=f".{output_directory.name}.", dir=output_directory.parent
        ) as name:
            temporary = Path(name)
            for filename, content in artifacts.items():
                (temporary / filename).write_bytes(content)
            os.replace(temporary, output_directory)
    except OSError as error:
        raise PacketBuildError(f"output cannot be written: {error}") from error
    return documents


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=CANONICAL_OUTPUT_DIRECTORY)
    args = parser.parse_args()
    try:
        documents = write_packet(args.output)
        request = documents["training-approval-request.json"]
        print(
            json.dumps(
                {
                    "status": request["status"],
                    "requestIdentity": request["requestIdentity"],
                    "datasetVersion": request["candidateCard"]["datasetVersion"],
                    "trainingAllowed": False,
                },
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except PacketBuildError as error:
        print(
            json.dumps({"diagnostics": error.diagnostics, "status": "ERROR"}, sort_keys=True),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
