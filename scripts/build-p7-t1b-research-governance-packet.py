#!/usr/bin/env python3
"""Build the pending, source-bound P7-T1B Research governance packet."""
from __future__ import annotations

import argparse
import copy
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
SOURCE_EXPORT_PATH = ROOT / "datasets/p7-t1a-research-synthetic-source-v1/source-export.json"
PROVENANCE_PATH = ROOT / "datasets/p7-t1a-research-synthetic-source-v1/provenance.json"
GOVERNANCE_PATH = ROOT / "docs/architecture/ai/data-governance.yml"
EVALUATION_SUITE_PATH = ROOT / "evals/p6-t4-evaluation-suites.yaml"
EVALUATION_LOCK_PATH = ROOT / "evals/p6-t4-evaluation-suite.lock.json"
EVALUATION_BINDING_PATH = ROOT / "evals/p6-t4-evaluation-freeze.binding.yaml"
P7T1_PATH = ROOT / "scripts/dataset-pipeline-p7-t1.py"
EVALUATION_VALIDATOR_PATH = ROOT / "scripts/validate-evaluation-suites.py"
CANONICAL_OUTPUT_DIRECTORY = ROOT / "config/p7-t1b-research-governance-packet-v1"

SOURCE_ID = "p7-t1a-research-synthetic-source"
SOURCE_VERSION = "1.0.0"
SOURCE_SHA256 = "7b5744e1e49925b228d346cf60817e2fbf976283b72c1673aedb329449503436"
CONTENT_IDENTITY = "c8dc56a1cbff71dd8c15c1eff6f561e1d6d1d4152326bbb24f99cdf2e4753722"
PROVENANCE_IDENTITY = "04ed322cf9604d753c1fdb2ab03120aa06c4856b185fb98d387f26e969c6ed1b"
SOURCE_COMMIT = "f9de3c1f3838ec2815276fabce3460b0824e0909"
EVALUATION_SOURCE_COMMIT = "1aa4d2e158046d8fe08a94f36ceb7d811c54b716"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
EVALUATION_SUITE_ID = "P6-T4-EVALUATION-SUITES"
EVALUATION_SUITE_VERSION = "1.0.0"
EVALUATION_SUITE_DIGEST = "8b75d356890a8a5c2318305589301b6ee6d73fbd3665b9af2063f98e13ea7417"
DATASET_ID = "p7-research-synthetic-training-dataset"
DATASET_VERSION = "1.0.0"
DATASET_CARD_REFERENCE = "config/p7-t1b-research-governance-packet-v1/training-dataset-card.pending.json"
EVALUATION_MANIFEST_REFERENCE = (
    "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-manifest.pending.json"
)
CATEGORY_IDS = ["CAT_RESEARCH_ASSIGNED_TASK", "CAT_RESEARCH_DRAFT_CONTEXT"]


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {path.name}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


P7T1 = _load_module("p7t1_for_p7t1b", P7T1_PATH)
EVALUATION = _load_module("p6t4_for_p7t1b", EVALUATION_VALIDATOR_PATH)


class PacketBuildError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def load_document(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
        value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    except (OSError, json.JSONDecodeError, yaml.YAMLError) as error:
        raise PacketBuildError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise PacketBuildError(f"{path.name}: object required")
    return value


def canonical_bytes(value: object) -> bytes:
    try:
        return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    except (TypeError, ValueError) as error:
        raise PacketBuildError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def request_identity(request: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes({key: value for key, value in request.items() if key != "requestIdentity"}))


def _validate_authoritative_inputs() -> tuple[dict[str, Any], dict[str, Any]]:
    diagnostics: list[str] = []
    export = load_document(SOURCE_EXPORT_PATH)
    provenance = load_document(PROVENANCE_PATH)
    if sha256_bytes(SOURCE_EXPORT_PATH.read_bytes()) != SOURCE_SHA256:
        diagnostics.append("source export: authoritative SHA-256 mismatch")
    expected_source = {
        "identity": f"{SOURCE_ID}-v1",
        "authorizationBoundary": P7T1.SPRING_AUTHORIZATION_BOUNDARY,
        "sourceDataOwner": "RESEARCH_GOVERNANCE_DOMAIN_DATA_OWNER",
        "sourcePermissionReference": None,
        "approvalReference": None,
    }
    if export.get("source") != expected_source or len(export.get("records", [])) != 45:
        diagnostics.append("source export: exact pending P7-T1A source required")
    expected_provenance = {
        "sourceId": SOURCE_ID,
        "sourceVersion": SOURCE_VERSION,
        "contentIdentity": CONTENT_IDENTITY,
        "provenanceIdentity": PROVENANCE_IDENTITY,
        "sourceState": "SOURCE_READY",
        "governanceState": "AWAITING_GOVERNANCE_APPROVAL",
    }
    if any(provenance.get(key) != value for key, value in expected_provenance.items()):
        diagnostics.append("source provenance: authoritative identity or state mismatch")
    artifact = provenance.get("artifacts", [{}])[0]
    if artifact.get("sha256") != SOURCE_SHA256 or provenance.get("inventory", {}).get("recordCount") != 45:
        diagnostics.append("source provenance: export binding or inventory mismatch")
    if provenance.get("governance", {}).get("trainingAuthorized") is not False:
        diagnostics.append("source provenance: training must remain unauthorized")
    if diagnostics:
        raise PacketBuildError(diagnostics)
    return export, provenance


def _evaluation_manifest_candidate() -> dict[str, Any]:
    return {
        "dataset_id": EVALUATION_SUITE_ID,
        "dataset_version": EVALUATION_SUITE_VERSION,
        "model_development_purpose": "EVALUATION",
        "lifecycle_status": "FROZEN",
        "freeze_status": "FROZEN",
        "retention": {"retention_class": "FROZEN_EVALUATION"},
        "integrity": {
            "checksum": EVALUATION_SUITE_DIGEST,
            "checksum_algorithm": "SHA-256",
            "verified_at_reference": "evals/p6-t4-evaluation-suite.lock.json",
        },
        "approval_references": [],
        "approval_status": "PENDING",
    }


def _sanitization() -> dict[str, Any]:
    evidence = "datasets/p7-t1a-research-synthetic-source-v1/provenance.json#/antiLeakage"
    transform = "scripts/build-p7-t1a-research-synthetic-source.py"
    review = "P7-T1B-AWAITING-GOVERNANCE-REVIEW"
    residual = "datasets/p7-t1a-research-synthetic-source-v1/provenance.json#/coverage"
    return {
        "disposition": "SYNTHETIC_GENERATION_ONLY",
        "field_decisions": [
            {
                "category_id": category_id,
                "field_decision": "SYNTHETIC_REPLACE",
                "transform_reference": transform,
                "reviewer_reference": review,
                "result_reference": evidence,
                "residual_risk_reference": residual,
            }
            for category_id in CATEGORY_IDS
        ],
        "transform_reference": transform,
        "reviewer_reference": review,
        "result_reference": evidence,
        "residual_risk_reference": residual,
    }


def _training_card_candidate() -> dict[str, Any]:
    return {
        "dataset_id": DATASET_ID,
        "dataset_version": DATASET_VERSION,
        "contract_version": "1.0.0",
        "schema_version": "1.0.0",
        "title": "Pending project-owned synthetic Research training dataset",
        "description": "P7-T1A synthetic Research source candidate; no approval or training authorization is asserted.",
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
            "trigger_reference": "P7-T2-TRAINING-COMPLETION-OR-REQUEST-DISPOSITION",
            "evidence_reference": "P7-T1B-RETENTION-EVIDENCE-REQUIRED",
            "start_reference": "P7-T1B-RETENTION-START-REFERENCE-REQUIRED",
            "recheck_or_expiry_reference": "P7-T1B-POST-EXPERIMENT-RECHECK-REQUIRED",
            "disposition_action": "DELETE",
            "disposition_owner": "RESEARCH_GOVERNANCE_DATASET_STEWARD",
        },
        "sanitization": _sanitization(),
        "provenance": {"type": "SYNTHETIC"},
        "lineage": {
            "source_references": [
                f"datasets/p7-t1a-research-synthetic-source-v1/source-export.json#sha256={SOURCE_SHA256}",
                f"datasets/p7-t1a-research-synthetic-source-v1/provenance.json#identity={PROVENANCE_IDENTITY}",
                f"p7-t1a-content-sha256:{CONTENT_IDENTITY}",
            ],
            "transform_references": ["scripts/build-p7-t1a-research-synthetic-source.py"],
        },
        "integrity": {
            "checksum": SOURCE_SHA256,
            "checksum_algorithm": "SHA-256",
            "verified_at_reference": "P7-T1B-DETERMINISTIC-SOURCE-VERIFICATION",
        },
        "split": {"label": "TRAIN"},
        "deduplication": {"disposition": "NO_CANDIDATE_OVERLAP"},
        "evaluation_freeze_prerequisite": {
            "evaluation_dataset_id": EVALUATION_SUITE_ID,
            "evaluation_dataset_version": EVALUATION_SUITE_VERSION,
            "evaluation_purpose": "EVALUATION",
            "evaluation_lifecycle_status": "FROZEN",
            "evaluation_freeze_status": "FROZEN",
            "evaluation_integrity_checksum": EVALUATION_SUITE_DIGEST,
            "evaluation_approval_reference": None,
        },
        "limitations_and_bias": "datasets/p7-t1a-research-synthetic-source-v1/provenance.json#/coverage",
        "created_at_reference": "P7-T1B-NOT-RECORDED-DETERMINISTIC-REQUEST",
        "revocation_reference": "P7-T1B-REVOCATION-EVIDENCE-REQUIRED",
    }


def _training_request(card: dict[str, Any]) -> dict[str, Any]:
    request = {
        "artifactType": "P7-T1B-RESEARCH-TRAINING-GOVERNANCE-REQUEST",
        "schemaVersion": "1.0.0",
        "requestId": "P7-T1B-RESEARCH-TRAINING-GOVERNANCE-REQUEST-001",
        "status": "PENDING_USER_APPROVAL",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "source": {
            "sourceId": SOURCE_ID,
            "sourceVersion": SOURCE_VERSION,
            "sourceReference": "datasets/p7-t1a-research-synthetic-source-v1/source-export.json",
            "sourceSha256": SOURCE_SHA256,
            "contentIdentity": CONTENT_IDENTITY,
            "provenanceReference": "datasets/p7-t1a-research-synthetic-source-v1/provenance.json",
            "provenanceIdentity": PROVENANCE_IDENTITY,
            "sourceCommit": SOURCE_COMMIT,
            "recordCount": 45,
            "fullySynthetic": True,
            "independentlyAuthored": True,
            "projectOwned": True,
        },
        "candidateCard": {
            "datasetId": DATASET_ID,
            "datasetVersion": DATASET_VERSION,
            "reference": DATASET_CARD_REFERENCE,
            "candidateCardIdentity": sha256_bytes(canonical_bytes(card)),
        },
        "currentState": {
            "sourceState": "SOURCE_READY",
            "governanceState": "AWAITING_GOVERNANCE_APPROVAL",
            "approvalStatus": "PENDING",
            "lifecycleStatus": "PENDING_APPROVAL",
            "sourcePermissionStatus": "NOT_ASSESSED",
            "currentPermittedPurposes": ["DEVELOPMENT_TEST"],
            "trainingAuthorized": False,
        },
        "requestedScope": {
            "assistantKey": "RESEARCH_ASSISTANT",
            "sourceDataOwner": "RESEARCH_GOVERNANCE_DOMAIN_DATA_OWNER",
            "datasetSteward": "RESEARCH_GOVERNANCE_DATASET_STEWARD",
            "requestedUseDecision": "SYNTHETIC_ONLY",
            "permittedPurposes": ["TRAINING"],
            "repositoryProhibitedPurposes": [],
            "modelDevelopmentOperation": "ADAPTER_FINE_TUNING",
            "requestedRetentionClass": "EPHEMERAL_DEVELOPMENT",
            "intendedDownstreamUse": [
                "P7-T1_DATASET_MATERIALIZATION",
                "P7-T2_RESEARCH_LORA_QLORA_TRAINING",
            ],
            "projectOnly": True,
            "externalSharingAllowed": False,
            "productionPromptingAllowed": False,
            "ragIngestionAllowed": False,
        },
        "requester": "P7_T1B_IMPLEMENTATION_AGENT",
        "sourceCommit": SOURCE_COMMIT,
        "approval": None,
        "approvedBy": None,
        "approvedAt": None,
        "requestIdentity": "",
    }
    request["requestIdentity"] = request_identity(request)
    return request


def _evaluation_request(manifest: dict[str, Any]) -> dict[str, Any]:
    lock = load_document(EVALUATION_LOCK_PATH)
    request = {
        "artifactType": "P7-T1B-FROZEN-EVALUATION-GOVERNANCE-REQUEST",
        "schemaVersion": "1.0.0",
        "requestId": "P7-T1B-FROZEN-EVALUATION-GOVERNANCE-REQUEST-001",
        "status": "PENDING_USER_APPROVAL",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "suite": {
            "suiteId": EVALUATION_SUITE_ID,
            "suiteVersion": EVALUATION_SUITE_VERSION,
            "suiteDigest": EVALUATION_SUITE_DIGEST,
            "suiteReference": "evals/p6-t4-evaluation-suites.yaml",
            "lockReference": "evals/p6-t4-evaluation-suite.lock.json",
            "bindingReference": "evals/p6-t4-evaluation-freeze.binding.yaml",
            "lockVersion": lock.get("lockVersion"),
            "canonicalInventoryDigest": lock.get("canonicalInventoryDigest"),
        },
        "currentBinding": {
            "localFreezeStatus": lock.get("localFreezeStatus"),
            "governanceState": "GOVERNED_EVIDENCE_PENDING",
            "approvalReference": None,
            "evaluationOnly": True,
            "trainingProhibited": True,
        },
        "candidateManifest": {
            "reference": EVALUATION_MANIFEST_REFERENCE,
            "candidateManifestIdentity": sha256_bytes(canonical_bytes(manifest)),
        },
        "requestedScope": {
            "purposeMode": "EVALUATION_ONLY",
            "permittedPurposes": ["EVALUATION"],
            "forbiddenPurposes": ["TRAINING"],
            "requestedLifecycleStatus": "FROZEN",
            "requestedFreezeStatus": "FROZEN",
            "requestedRetentionClass": "FROZEN_EVALUATION",
            "trainingAllowed": False,
            "productionPromptingAllowed": False,
            "ragIngestionAllowed": False,
            "externalSharingAllowed": False,
        },
        "requester": "P7_T1B_IMPLEMENTATION_AGENT",
        "sourceCommit": EVALUATION_SOURCE_COMMIT,
        "approval": None,
        "approvedBy": None,
        "approvedAt": None,
        "requestIdentity": "",
    }
    request["requestIdentity"] = request_identity(request)
    return request


def _validate_card_contract(card: dict[str, Any], manifest: dict[str, Any]) -> list[str]:
    diagnostics: list[str] = []
    contract = load_document(GOVERNANCE_PATH)["contract"]
    required = set(contract["dataset_card_contract"]["required_fields"]) | {"evaluation_freeze_prerequisite"}
    if set(card) != required:
        diagnostics.append("pending card: exact native dataset-card fields required")
    expected_states = {
        "approval_status": "PENDING",
        "lifecycle_status": "PENDING_APPROVAL",
        "source_permission_status": "NOT_ASSESSED",
        "freeze_status": "NOT_REQUIRED",
        "model_development_purpose": "TRAINING",
        "model_development_operation": "ADAPTER_FINE_TUNING",
    }
    if any(card.get(key) != value for key, value in expected_states.items()):
        diagnostics.append("pending card: exact repository-supported pending state required")
    if any(card.get(field) for field in ("approval_references", "source_permission_references", "approved_purposes")):
        diagnostics.append("pending card: approval and source-permission evidence must remain unresolved")
    if card.get("permitted_purposes") != ["DEVELOPMENT_TEST"] or card.get("prohibited_purposes") != []:
        diagnostics.append("pending card: current purpose boundary mismatch")

    vocabulary = contract["controlled_vocabulary"]
    vocabulary_checks = {
        "approval_status": "approval_statuses",
        "lifecycle_status": "lifecycle_statuses",
        "source_permission_status": "source_permission_statuses",
        "freeze_status": "freeze_statuses",
        "model_development_purpose": "model_development_purposes",
        "model_development_operation": "model_development_operations",
        "use_decision": "use_decisions",
        "classification": "classifications",
    }
    for field, vocabulary_key in vocabulary_checks.items():
        if card.get(field) not in vocabulary[vocabulary_key]:
            diagnostics.append(f"pending card/{field}: controlled vocabulary value required")

    matrix = {item["category_id"]: item for item in contract["data_governance_matrix"]}
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
            diagnostics.append(f"pending card/{category_id}: authoritative category binding mismatch")
        if "TRAINING" not in category.get("permitted_purposes", []) or category.get("prohibited_purposes") != []:
            diagnostics.append(f"pending card/{category_id}: TRAINING is not request-eligible")
    decisions = card.get("sanitization", {}).get("field_decisions", [])
    if [item.get("category_id") for item in decisions] != CATEGORY_IDS:
        diagnostics.append("pending card/sanitization: exact category decisions required")
    prerequisite = card.get("evaluation_freeze_prerequisite", {})
    expected_prerequisite = {
        "evaluation_dataset_id": manifest.get("dataset_id"),
        "evaluation_dataset_version": manifest.get("dataset_version"),
        "evaluation_purpose": manifest.get("model_development_purpose"),
        "evaluation_lifecycle_status": manifest.get("lifecycle_status"),
        "evaluation_freeze_status": manifest.get("freeze_status"),
        "evaluation_integrity_checksum": manifest.get("integrity", {}).get("checksum"),
        "evaluation_approval_reference": None,
    }
    if prerequisite != expected_prerequisite:
        diagnostics.append("pending card/evaluation_freeze_prerequisite: candidate manifest mismatch")
    return diagnostics


def _validate_evaluation_contract(manifest: dict[str, Any]) -> list[str]:
    diagnostics: list[str] = []
    expected_manifest = _evaluation_manifest_candidate()
    if manifest != expected_manifest:
        diagnostics.append("pending evaluation manifest: exact P7-T1 prerequisite projection required")
    suite = load_document(EVALUATION_SUITE_PATH)
    lock = load_document(EVALUATION_LOCK_PATH)
    binding = load_document(EVALUATION_BINDING_PATH)
    lock_errors = EVALUATION.validate_lock(suite, lock, binding)
    diagnostics.extend(f"frozen evaluation: {item}" for item in lock_errors)
    if EVALUATION.file_digest(EVALUATION_SUITE_PATH) != EVALUATION_SUITE_DIGEST:
        diagnostics.append("frozen evaluation: authoritative suite digest mismatch")
    expected_binding_fields = {
        "suiteId": EVALUATION_SUITE_ID,
        "suiteVersion": EVALUATION_SUITE_VERSION,
        "purpose": "EVALUATION",
        "EVALUATION_ONLY": True,
        "TRAINING_PROHIBITED": True,
        "requiredLifecycle": "FROZEN",
        "requiredFreezeStatus": "FROZEN",
        "requiredRetention": "FROZEN_EVALUATION",
        "transitionOwner": "P7-T1",
        "suiteDigest": EVALUATION_SUITE_DIGEST,
    }
    if any(binding.get(key) != value for key, value in expected_binding_fields.items()):
        diagnostics.append("frozen evaluation: exact immutable binding fields required")
    return diagnostics


def _validate_future_p7t1_shape(card: dict[str, Any], manifest: dict[str, Any]) -> list[str]:
    future_card = copy.deepcopy(card)
    future_manifest = copy.deepcopy(manifest)
    future_export = copy.deepcopy(load_document(SOURCE_EXPORT_PATH))
    evaluation_reference = "PROBE_ONLY_EVALUATION_APPROVAL_REFERENCE"
    training_reference = "PROBE_ONLY_TRAINING_APPROVAL_REFERENCE"
    permission_reference = "PROBE_ONLY_SOURCE_PERMISSION_REFERENCE"
    future_manifest.update({"approval_status": "APPROVED", "approval_references": [evaluation_reference]})
    future_card.update(
        {
            "source_permission_status": "VERIFIED",
            "source_permission_references": [permission_reference],
            "approval_status": "APPROVED",
            "approval_references": [training_reference],
            "lifecycle_status": "APPROVED",
            "approved_purposes": ["TRAINING"],
            "permitted_purposes": ["DEVELOPMENT_TEST", "TRAINING"],
        }
    )
    future_card["evaluation_freeze_prerequisite"]["evaluation_approval_reference"] = evaluation_reference
    future_export["source"]["sourcePermissionReference"] = permission_reference
    future_export["source"]["approvalReference"] = training_reference
    try:
        P7T1.validate_card(future_card, future_manifest)
        P7T1.validate_controlled_export(future_export, future_card)
    except P7T1.DatasetPipelineError as error:
        return [f"future P7-T1 compatibility: {error}"]
    return []


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    diagnostics: list[str] = []
    expected_names = {
        "training-approval-request.json",
        "training-dataset-card.pending.json",
        "frozen-evaluation-approval-request.json",
        "frozen-evaluation-manifest.pending.json",
    }
    if set(documents) != expected_names:
        raise PacketBuildError("packet: exact artifact inventory required")
    card = documents["training-dataset-card.pending.json"]
    manifest = documents["frozen-evaluation-manifest.pending.json"]
    training_request = documents["training-approval-request.json"]
    evaluation_request = documents["frozen-evaluation-approval-request.json"]
    diagnostics.extend(_validate_card_contract(card, manifest))
    diagnostics.extend(_validate_evaluation_contract(manifest))
    diagnostics.extend(_validate_future_p7t1_shape(card, manifest))
    if training_request != _training_request(card):
        diagnostics.append("training request: exact source-bound pending request required")
    if evaluation_request != _evaluation_request(manifest):
        diagnostics.append("evaluation request: exact frozen-suite pending request required")
    for label, request in (("training", training_request), ("evaluation", evaluation_request)):
        if request.get("status") != "PENDING_USER_APPROVAL" or any(
            request.get(field) is not None for field in ("approval", "approvedBy", "approvedAt")
        ):
            diagnostics.append(f"{label} request: pending state with null decision fields required")
        if request.get("requestIdentity") != request_identity(request):
            diagnostics.append(f"{label} request: canonical identity mismatch")
    if training_request["requestIdentity"] == evaluation_request["requestIdentity"]:
        diagnostics.append("packet: TRAINING and EVALUATION decisions must have distinct identities")
    rendered = canonical_bytes(documents)
    if b"p7-t3-research-report-eval-governance-approval" in rendered.lower() or b"961957f646e9a8ae" in rendered:
        diagnostics.append("packet: P7-T3 approval reuse is forbidden")
    if diagnostics:
        raise PacketBuildError(diagnostics)


def build_documents() -> dict[str, dict[str, Any]]:
    _validate_authoritative_inputs()
    card = _training_card_candidate()
    evaluation_manifest = _evaluation_manifest_candidate()
    documents = {
        "training-approval-request.json": _training_request(card),
        "training-dataset-card.pending.json": card,
        "frozen-evaluation-approval-request.json": _evaluation_request(evaluation_manifest),
        "frozen-evaluation-manifest.pending.json": evaluation_manifest,
    }
    validate_documents(documents)
    return documents


def build_artifacts() -> dict[str, bytes]:
    return {filename: json_bytes(value) for filename, value in build_documents().items()}


def write_packet(output_directory: Path) -> dict[str, dict[str, Any]]:
    if output_directory.exists():
        raise PacketBuildError("output directory must not already exist")
    documents = build_documents()
    artifacts = {filename: json_bytes(value) for filename, value in documents.items()}
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(prefix=f".{output_directory.name}.", dir=output_directory.parent) as name:
            temporary_directory = Path(name)
            for filename, content in artifacts.items():
                (temporary_directory / filename).write_bytes(content)
            os.replace(temporary_directory, output_directory)
    except OSError as error:
        raise PacketBuildError(f"output cannot be written: {error}") from error
    return documents


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=CANONICAL_OUTPUT_DIRECTORY)
    args = parser.parse_args()
    try:
        documents = write_packet(args.output)
        print(
            json.dumps(
                {
                    "status": "AWAITING_USER_APPROVAL",
                    "trainingRequestIdentity": documents["training-approval-request.json"]["requestIdentity"],
                    "evaluationRequestIdentity": documents["frozen-evaluation-approval-request.json"]["requestIdentity"],
                },
                sort_keys=True,
            )
        )
        return 0
    except PacketBuildError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
