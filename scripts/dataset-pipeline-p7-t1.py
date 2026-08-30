#!/usr/bin/env python3
"""Build deterministic P7-T1 datasets from an approved offline export.

This tool never queries application databases. Its input is a bounded export
created after Spring authorization and backed by the P6-T2 dataset card,
source-permission, and data-owner approval evidence.
"""
from __future__ import annotations

import argparse
import copy
from functools import lru_cache
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from typing import Any

import yaml
from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parents[1]
GOVERNANCE_PATH = ROOT / "docs/architecture/ai/data-governance.yml"
PIPELINE_SCHEMA_VERSION = "1.0.0"
PIPELINE_VERSION = "1.0.0"
P7T1C_TRAINING_APPROVAL_ARTIFACT_TYPE = "P7-T1C-RESEARCH-TRAINING-GOVERNANCE-APPROVAL"
SPRING_AUTHORIZATION_BOUNDARY = "SPRING_AUTHORIZED_CONTEXT"
SPLIT_STRATEGY = "SHA256_CONTENT_BUCKET"
SANITIZER_VERSION = "p6-t3-root-allowlist-v1"
VALIDATION_VERSION = "p6-t3-schema-and-branch-v1"
DEDUPLICATION_VERSION = "canonical-training-content-sha256-v1"
SERIALIZATION_VERSION = "canonical-jsonl-utf8-lf-v1"
P6_SCHEMA_PATH = ROOT / "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json"
P6_VALIDATOR_PATH = ROOT / "scripts/validate-domain-dataset-schemas.py"
P6_SCHEMA_ID = "https://lab-portal.local/schemas/p6-t3/domain-dataset-schemas/1.0.0"
P6_ROOTS = {"ADMIN": "adminRecord", "LAB": "labRecord", "RESEARCH": "researchRecord", "SHARED": "sharedRecord"}
SOURCE_RECORD_FIELDS = (
    "schemaVersion",
    "recordId",
    "domain",
    "recordType",
    "visibility",
    "useCaseId",
    "input",
    "payload",
    "expectedOutput",
    "governance",
    "metadata",
)
TRAINING_RECORD_FIELDS = (
    "schemaVersion",
    "domain",
    "recordType",
    "visibility",
    "useCaseId",
    "input",
    "payload",
    "expectedOutput",
)
SPLIT_FILENAMES = {
    "train": "train.jsonl",
    "validation": "validation.jsonl",
    "evaluation": "evaluation.jsonl",
}


class DatasetPipelineError(ValueError):
    """Fail-closed pipeline diagnostic with stable ordering."""

    def __init__(self, diagnostics: list[str] | str):
        self.diagnostics = sorted(set([diagnostics] if isinstance(diagnostics, str) else diagnostics))
        super().__init__("; ".join(self.diagnostics))


def canonical_bytes(value: object) -> bytes:
    try:
        rendered = json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError) as error:
        raise DatasetPipelineError(f"value is not canonical JSON: {error}") from error
    return rendered.encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _nonempty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _contains_local_path(value: str) -> bool:
    return bool(re.match(r"^(?:[A-Za-z]:[\\/]|[\\/]{1,2})", value))


def _require_reference(value: object, path: str, diagnostics: list[str]) -> None:
    if not _nonempty_string(value):
        diagnostics.append(f"{path}: non-empty logical reference required")
    elif _contains_local_path(value):
        diagnostics.append(f"{path}: local absolute paths are forbidden")


def _reference_list(value: object, path: str, diagnostics: list[str], allow_empty: bool = False) -> list[str]:
    if not isinstance(value, list) or (not allow_empty and not value):
        diagnostics.append(f"{path}: {'reference list' if not allow_empty else 'list'} required")
        return []
    if len(value) != len(set(item for item in value if isinstance(item, str))):
        diagnostics.append(f"{path}: values must be unique")
    for index, item in enumerate(value):
        _require_reference(item, f"{path}/{index}", diagnostics)
    return [item for item in value if isinstance(item, str) and item.strip()]


def _local_path_diagnostics(value: object, path: str = "card") -> list[str]:
    diagnostics: list[str] = []
    if isinstance(value, str) and _contains_local_path(value):
        diagnostics.append(f"{path}: local absolute paths are forbidden")
    elif isinstance(value, dict):
        for key, child in value.items():
            diagnostics.extend(_local_path_diagnostics(child, f"{path}/{key}"))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            diagnostics.extend(_local_path_diagnostics(child, f"{path}/{index}"))
    return diagnostics


def _canonical_identity(value: dict[str, Any], identity_field: str) -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != identity_field}))


def _repository_reference_path(reference: object, label: str, diagnostics: list[str]) -> Path | None:
    _require_reference(reference, label, diagnostics)
    if not isinstance(reference, str) or not reference.strip() or _contains_local_path(reference):
        return None
    candidate = (ROOT / reference).resolve()
    try:
        candidate.relative_to(ROOT.resolve())
    except ValueError:
        diagnostics.append(f"{label}: repository-relative reference required")
        return None
    return candidate


def _validate_source_approval(
    export: dict[str, Any],
    card: dict[str, Any],
    source_approval: object,
    source_approval_reference: object,
    source_export_sha256: object,
    require_durable_source_approval: bool,
) -> list[str]:
    diagnostics: list[str] = []
    if not isinstance(source_approval, dict):
        return [
            "export/source/sourcePermissionReference and approvalReference: "
            "approved source approval sidecar required"
        ]
    required_fields = {
        "artifactType", "schemaVersion", "status", "requestIdentity", "requestReference", "purpose",
        "approvalAuthority", "source", "dataset", "scope", "sourcePermission", "approval", "revocation",
        "sourceCommit", "artifactIdentity",
    }
    if set(source_approval) != required_fields:
        diagnostics.append("source approval: exact fields required")
    if (
        source_approval.get("artifactType") != P7T1C_TRAINING_APPROVAL_ARTIFACT_TYPE
        or source_approval.get("schemaVersion") != PIPELINE_SCHEMA_VERSION
        or source_approval.get("status") != "APPROVED"
        or source_approval.get("purpose") != "TRAINING"
    ):
        diagnostics.append("source approval: exact approved TRAINING artifact required")
    if source_approval.get("artifactIdentity") != _canonical_identity(source_approval, "artifactIdentity"):
        diagnostics.append("source approval: canonical artifact identity mismatch")

    approval_path = _repository_reference_path(
        source_approval_reference, "source approval/reference", diagnostics
    )
    if source_approval_reference not in card.get("source_permission_references", []):
        diagnostics.append("source approval: verified card source-permission reference required")
    if source_approval_reference not in card.get("approval_references", []):
        diagnostics.append("source approval: approved card reference required")
    if approval_path is None or not approval_path.is_file():
        if require_durable_source_approval:
            diagnostics.append("source approval: durable repository evidence required")
    else:
        try:
            stored_approval = load_document(approval_path)
        except DatasetPipelineError as error:
            diagnostics.extend(error.diagnostics)
        else:
            if stored_approval != source_approval:
                diagnostics.append("source approval: sidecar does not match durable repository evidence")

    request_path = _repository_reference_path(
        source_approval.get("requestReference"), "source approval/requestReference", diagnostics
    )
    request: object = None
    if request_path is None or not request_path.is_file():
        diagnostics.append("source approval: durable request evidence required")
    else:
        request = load_document(request_path)
    if not isinstance(request, dict):
        diagnostics.append("source approval: request object required")
        request = {}
    request_identity = request.get("requestIdentity")
    if (
        source_approval.get("requestIdentity") != request_identity
        or request_identity != _canonical_identity(request, "requestIdentity")
    ):
        diagnostics.append("source approval: exact request identity binding required")
    if request.get("status") != "PENDING_USER_APPROVAL" or request.get("approvalAuthority") != card.get("approval_authority"):
        diagnostics.append("source approval: authoritative pending request required")
    if request.get("source") != source_approval.get("source"):
        diagnostics.append("source approval: exact request source binding required")
    requested_scope = request.get("requestedScope", {})
    approval_scope = source_approval.get("scope", {})
    if (
        not isinstance(requested_scope, dict)
        or requested_scope.get("permittedPurposes") != ["TRAINING"]
        or not isinstance(approval_scope, dict)
        or approval_scope.get("permittedPurposes") != ["TRAINING"]
    ):
        diagnostics.append("source approval: exact TRAINING scope required")

    approval_authority = source_approval.get("approvalAuthority")
    approval_record = source_approval.get("approval")
    if (
        approval_authority != card.get("approval_authority")
        or not isinstance(approval_record, dict)
        or approval_record.get("decision") != "APPROVED"
        or approval_record.get("approvedBy") != approval_authority
        or not _nonempty_string(approval_record.get("approvedAt"))
    ):
        diagnostics.append("source approval: exact authority decision required")
    permission = source_approval.get("sourcePermission")
    if (
        not isinstance(permission, dict)
        or permission.get("status") != "VERIFIED"
        or permission.get("sourceDataOwner") != card.get("source_data_owner")
        or permission.get("evidenceReference") != source_approval_reference
    ):
        diagnostics.append("source approval: verified source permission required")
    source = source_approval.get("source")
    if not isinstance(source, dict):
        diagnostics.append("source approval: source object required")
        source = {}
    if source.get("sourceSha256") != source_export_sha256 or source_export_sha256 != card.get("integrity", {}).get("checksum"):
        diagnostics.append("source approval: exact source SHA-256 binding required")
    if source.get("contentIdentity") != sha256_bytes(canonical_bytes(export.get("records"))):
        diagnostics.append("source approval: exact source content identity required")
    dataset = source_approval.get("dataset")
    if (
        not isinstance(dataset, dict)
        or dataset.get("datasetId") != card.get("dataset_id")
        or dataset.get("datasetVersion") != card.get("dataset_version")
    ):
        diagnostics.append("source approval: exact dataset binding required")
    return diagnostics


def load_document(path: Path) -> object:
    try:
        with path.open(encoding="utf-8") as handle:
            return yaml.safe_load(handle)
    except (OSError, yaml.YAMLError) as error:
        raise DatasetPipelineError(f"cannot load {path.name}: {error}") from error


def governance_contract() -> dict[str, Any]:
    document = load_document(GOVERNANCE_PATH)
    if not isinstance(document, dict) or not isinstance(document.get("contract"), dict):
        raise DatasetPipelineError("P6-T2 governance contract is malformed")
    return document["contract"]


def validate_config(config: object) -> None:
    diagnostics: list[str] = []
    if not isinstance(config, dict):
        raise DatasetPipelineError("config: object required")
    expected_fields = {"schemaVersion", "split", "cardReference"}
    if set(config) != expected_fields:
        diagnostics.append("config: exact fields schemaVersion, split, cardReference required")
    if config.get("schemaVersion") != PIPELINE_SCHEMA_VERSION:
        diagnostics.append("config/schemaVersion: unsupported version")
    _require_reference(config.get("cardReference"), "config/cardReference", diagnostics)
    split = config.get("split")
    split_fields = {"strategy", "seed", "trainWeight", "validationWeight", "evaluationWeight"}
    if not isinstance(split, dict) or set(split) != split_fields:
        diagnostics.append("config/split: exact strategy, seed, and split weights required")
    else:
        if split.get("strategy") != SPLIT_STRATEGY:
            diagnostics.append("config/split/strategy: unsupported strategy")
        _require_reference(split.get("seed"), "config/split/seed", diagnostics)
        weights = [split.get("trainWeight"), split.get("validationWeight"), split.get("evaluationWeight")]
        if any(not isinstance(weight, int) or isinstance(weight, bool) or weight <= 0 for weight in weights):
            diagnostics.append("config/split: weights must be positive integers")
        elif sum(weights) != 100:
            diagnostics.append("config/split: weights must total 100")
    if diagnostics:
        raise DatasetPipelineError(diagnostics)


def _required_card_fields(contract: dict[str, Any], card: dict[str, Any]) -> set[str]:
    required = set(contract["dataset_card_contract"]["required_fields"])
    if card.get("model_development_purpose") == "TRAINING":
        required.add("evaluation_freeze_prerequisite")
    if card.get("partition") == "SHARED" or card.get("visibility") == "SHARED":
        required.update(contract["dataset_card_contract"]["conditional_required_fields"]["shared"]["fields"])
    return required


def _validate_evaluation_prerequisite(card: dict[str, Any], evaluation_manifest: object) -> list[str]:
    diagnostics: list[str] = []
    prerequisite = card.get("evaluation_freeze_prerequisite")
    if not isinstance(prerequisite, dict) or not isinstance(evaluation_manifest, dict):
        return ["card/evaluation_freeze_prerequisite: matching frozen evaluation manifest required"]
    expected = {
        "evaluation_dataset_id": evaluation_manifest.get("dataset_id"),
        "evaluation_dataset_version": evaluation_manifest.get("dataset_version"),
        "evaluation_purpose": evaluation_manifest.get("model_development_purpose"),
        "evaluation_lifecycle_status": evaluation_manifest.get("lifecycle_status"),
        "evaluation_freeze_status": evaluation_manifest.get("freeze_status"),
        "evaluation_integrity_checksum": (
            evaluation_manifest.get("integrity", {}).get("checksum")
            if isinstance(evaluation_manifest.get("integrity"), dict)
            else None
        ),
    }
    for field, value in expected.items():
        if prerequisite.get(field) != value:
            diagnostics.append(f"card/evaluation_freeze_prerequisite/{field}: frozen manifest mismatch")
    approval_reference = prerequisite.get("evaluation_approval_reference")
    evaluation_approvals = evaluation_manifest.get("approval_references")
    if not isinstance(evaluation_approvals, list) or approval_reference not in evaluation_approvals:
        diagnostics.append("card/evaluation_freeze_prerequisite/evaluation_approval_reference: approval mismatch")
    if evaluation_manifest.get("approval_status") != "APPROVED":
        diagnostics.append("evaluationManifest/approval_status: APPROVED required")
    if evaluation_manifest.get("model_development_purpose") != "EVALUATION":
        diagnostics.append("evaluationManifest/model_development_purpose: EVALUATION required")
    if evaluation_manifest.get("lifecycle_status") != "FROZEN" or evaluation_manifest.get("freeze_status") != "FROZEN":
        diagnostics.append("evaluationManifest: FROZEN lifecycle and freeze status required")
    retention = evaluation_manifest.get("retention")
    if not isinstance(retention, dict) or retention.get("retention_class") != "FROZEN_EVALUATION":
        diagnostics.append("evaluationManifest/retention: FROZEN_EVALUATION required")
    return diagnostics


def _validate_sanitization(
    card: dict[str, Any],
    category_ids: list[str],
    expected_disposition: str | None,
    contract: dict[str, Any],
) -> list[str]:
    diagnostics: list[str] = []
    sanitization = card.get("sanitization")
    if not isinstance(sanitization, dict):
        return ["card/sanitization: complete sanitization evidence required"]
    if sanitization.get("disposition") != expected_disposition:
        diagnostics.append("card/sanitization/disposition: category governance mismatch")
    for field in ("transform_reference", "reviewer_reference", "result_reference", "residual_risk_reference"):
        _require_reference(sanitization.get(field), f"card/sanitization/{field}", diagnostics)
    decisions = sanitization.get("field_decisions")
    if not isinstance(decisions, list) or not decisions:
        return diagnostics + ["card/sanitization/field_decisions: non-empty evidence list required"]
    required_fields = set(contract["data_handling"]["sanitization_record"]["required_fields"])
    compatible = set(
        contract["data_handling"]["sanitization_record"]["decision_compatibility"].get(expected_disposition, [])
    )
    decision_categories: list[str] = []
    for index, decision in enumerate(decisions):
        if not isinstance(decision, dict) or not required_fields.issubset(decision):
            diagnostics.append(f"card/sanitization/field_decisions/{index}: complete evidence required")
            continue
        category_id = decision.get("category_id")
        decision_categories.append(category_id)
        if category_id not in category_ids:
            diagnostics.append(f"card/sanitization/field_decisions/{index}/category_id: card category required")
        if decision.get("field_decision") not in compatible:
            diagnostics.append(f"card/sanitization/field_decisions/{index}/field_decision: incompatible decision")
        for field in required_fields - {"category_id", "field_decision"}:
            _require_reference(decision.get(field), f"card/sanitization/field_decisions/{index}/{field}", diagnostics)
    if sorted(decision_categories) != sorted(category_ids):
        diagnostics.append("card/sanitization/field_decisions: exactly one decision per category required")
    return diagnostics


def _validate_shared_card(
    card: dict[str, Any], contract: dict[str, Any], matrix: dict[str, dict[str, Any]]
) -> list[str]:
    diagnostics: list[str] = []
    contributors = _reference_list(
        card.get("contributing_category_ids"), "card/contributing_category_ids", diagnostics
    )
    owners = _reference_list(
        card.get("contributing_source_data_owners"), "card/contributing_source_data_owners", diagnostics
    )
    authorities = _reference_list(
        card.get("contributing_approval_authorities"), "card/contributing_approval_authorities", diagnostics
    )
    permission_references = _reference_list(
        card.get("contributing_source_permission_references"),
        "card/contributing_source_permission_references",
        diagnostics,
    )
    approval_references = _reference_list(
        card.get("contributing_approval_references"), "card/contributing_approval_references", diagnostics
    )
    consumers = _reference_list(card.get("consumer_assistant_keys"), "card/consumer_assistant_keys", diagnostics)
    evidence_lists = (owners, authorities, permission_references, approval_references)
    if contributors and any(len(values) != len(contributors) for values in evidence_lists):
        diagnostics.append("card/shared_contributors: contributor evidence lists must align by category")

    forbidden_tokens = set(contract["controlled_vocabulary"]["assistant_keys"]) | {"SHARED"}
    for field, values in (
        ("contributing_source_data_owners", owners),
        ("contributing_approval_authorities", authorities),
        ("contributing_source_permission_references", permission_references),
        ("contributing_approval_references", approval_references),
    ):
        if forbidden_tokens.intersection(values):
            diagnostics.append(f"card/{field}: assistant keys and SHARED are forbidden evidence tokens")
    for index, category_id in enumerate(contributors):
        category = matrix.get(category_id)
        if category is None or category.get("source_partition") == "SHARED":
            diagnostics.append(f"card/contributing_category_ids/{index}: known non-shared category required")
            continue
        if index < len(owners) and owners[index] != category.get("source_data_owner"):
            diagnostics.append(f"card/contributing_source_data_owners/{index}: category owner mismatch")
        if index < len(authorities) and authorities[index] != category.get("approval_authority"):
            diagnostics.append(f"card/contributing_approval_authorities/{index}: category authority mismatch")
    assistant_keys = set(contract["controlled_vocabulary"]["assistant_keys"])
    if not consumers or not set(consumers).issubset(assistant_keys):
        diagnostics.append("card/consumer_assistant_keys: unique known assistant consumers required")
    return diagnostics


def validate_card(card: object, evaluation_manifest: object | None) -> None:
    if not isinstance(card, dict):
        raise DatasetPipelineError("card: object required")
    contract = governance_contract()
    diagnostics: list[str] = []
    diagnostics.extend(_local_path_diagnostics(card))
    missing = sorted(_required_card_fields(contract, card) - set(card))
    if missing:
        diagnostics.append("card: missing required fields " + ", ".join(missing))
    if card.get("contract_version") != contract.get("contract_version"):
        diagnostics.append("card/contract_version: P6-T2 version mismatch")
    if card.get("schema_version") != contract.get("schema_version"):
        diagnostics.append("card/schema_version: P6-T2 version mismatch")
    if card.get("source_permission_status") != "VERIFIED":
        diagnostics.append("card/source_permission_status: VERIFIED required")
    if card.get("approval_status") != "APPROVED":
        diagnostics.append("card/approval_status: APPROVED required")
    if card.get("lifecycle_status") not in {"APPROVED", "FROZEN"}:
        diagnostics.append("card/lifecycle_status: APPROVED or FROZEN required")
    approved_purposes = _reference_list(card.get("approved_purposes"), "card/approved_purposes", diagnostics)
    permitted_purposes = _reference_list(card.get("permitted_purposes"), "card/permitted_purposes", diagnostics)
    prohibited_purposes = _reference_list(
        card.get("prohibited_purposes"), "card/prohibited_purposes", diagnostics, allow_empty=True
    )
    source_permission_references = _reference_list(
        card.get("source_permission_references"), "card/source_permission_references", diagnostics
    )
    approval_references = _reference_list(card.get("approval_references"), "card/approval_references", diagnostics)
    if card.get("model_development_purpose") not in approved_purposes:
        diagnostics.append("card/approved_purposes: controlled purpose is not approved")
    if card.get("model_development_purpose") not in permitted_purposes:
        diagnostics.append("card/permitted_purposes: controlled purpose is not permitted")
    if card.get("model_development_purpose") in prohibited_purposes:
        diagnostics.append("card/prohibited_purposes: controlled purpose is prohibited")

    matrix = {item["category_id"]: item for item in contract.get("data_governance_matrix", [])}
    category_ids = card.get("category_ids")
    if (
        not isinstance(category_ids, list)
        or not category_ids
        or any(not isinstance(category_id, str) for category_id in category_ids)
        or len(category_ids) != len(set(category_ids))
    ):
        diagnostics.append("card/category_ids: non-empty unique categories required")
        category_ids = []
    expected_dispositions: set[str] = set()
    expected_retentions: set[str] = set()
    for category_id in category_ids:
        category = matrix.get(category_id)
        if category is None:
            diagnostics.append(f"card/category_ids/{category_id}: unknown category")
            continue
        comparisons = {
            "partition": "source_partition",
            "visibility": "visibility",
            "classification": "classification",
            "use_decision": "use_decision",
            "source_data_owner": "source_data_owner",
            "dataset_steward": "dataset_steward",
            "approval_authority": "approval_authority",
        }
        for card_field, category_field in comparisons.items():
            if card.get(card_field) != category.get(category_field):
                diagnostics.append(f"card/{card_field}: category governance mismatch")
        expected_dispositions.add(category["sanitization_disposition"])
        expected_retentions.add(category["retention_class"])
    expected_disposition = next(iter(expected_dispositions)) if len(expected_dispositions) == 1 else None
    if len(expected_dispositions) > 1:
        diagnostics.append("card/category_ids: categories require incompatible sanitization dispositions")
    diagnostics.extend(_validate_sanitization(card, category_ids, expected_disposition, contract))
    expected_provenance = {
        "SANITIZED_DERIVATIVE_REQUIRED": "INTERNAL_SANITIZED",
        "SYNTHETIC_GENERATION_ONLY": "SYNTHETIC",
    }.get(expected_disposition)
    provenance = card.get("provenance")
    if not isinstance(provenance, dict) or provenance.get("type") != expected_provenance:
        diagnostics.append("card/provenance/type: sanitization disposition mismatch")

    retention = card.get("retention")
    frozen_evaluation = (
        card.get("lifecycle_status") == "FROZEN"
        and card.get("model_development_purpose") in {"EVALUATION", "BENCHMARK", "HUMAN_EVALUATION"}
    )
    expected_retention = "FROZEN_EVALUATION" if frozen_evaluation else next(iter(expected_retentions), None)
    if len(expected_retentions) > 1:
        diagnostics.append("card/category_ids: categories require incompatible retention classes")
    if not isinstance(retention, dict) or retention.get("retention_class") != expected_retention:
        diagnostics.append("card/retention/retention_class: category or frozen-evaluation retention required")
    lineage = card.get("lineage")
    if not isinstance(lineage, dict):
        diagnostics.append("card/lineage: complete lineage evidence required")
    else:
        _reference_list(lineage.get("source_references"), "card/lineage/source_references", diagnostics)
        _reference_list(lineage.get("transform_references"), "card/lineage/transform_references", diagnostics)
    integrity = card.get("integrity")
    if not isinstance(integrity, dict):
        diagnostics.append("card/integrity: complete integrity evidence required")
    else:
        for field in ("checksum", "checksum_algorithm", "verified_at_reference"):
            _require_reference(integrity.get(field), f"card/integrity/{field}", diagnostics)

    if card.get("partition") != "SHARED" and card.get("assistant_key") != card.get("partition"):
        diagnostics.append("card/assistant_key: private partition mismatch")
    if card.get("partition") == "SHARED" or card.get("visibility") == "SHARED":
        if card.get("partition") != "SHARED" or card.get("visibility") != "SHARED":
            diagnostics.append("card: SHARED partition and visibility must be paired")
        diagnostics.extend(_validate_shared_card(card, contract, matrix))
    if card.get("model_development_operation") == "ADAPTER_FINE_TUNING" and card.get("model_development_purpose") != "TRAINING":
        diagnostics.append("card/model_development_operation: ADAPTER_FINE_TUNING requires TRAINING")
    if card.get("lifecycle_status") == "FROZEN" and card.get("model_development_purpose") == "TRAINING":
        diagnostics.append("card/lifecycle_status: frozen evaluation artifacts cannot be used for TRAINING")
    if card.get("model_development_purpose") == "TRAINING":
        diagnostics.extend(_validate_evaluation_prerequisite(card, evaluation_manifest))
    if diagnostics:
        raise DatasetPipelineError(diagnostics)


def validate_controlled_export(
    export: object,
    card: dict[str, Any],
    *,
    source_approval: object | None = None,
    source_approval_reference: str | None = None,
    source_export_sha256: str | None = None,
    require_durable_source_approval: bool = True,
) -> list[dict[str, Any]]:
    if not isinstance(export, dict):
        raise DatasetPipelineError("export: object required")
    diagnostics: list[str] = []
    if set(export) != {"exportSchemaVersion", "source", "records"}:
        diagnostics.append("export: exact exportSchemaVersion, source, records fields required")
    if export.get("exportSchemaVersion") != PIPELINE_SCHEMA_VERSION:
        diagnostics.append("export/exportSchemaVersion: unsupported version")
    source = export.get("source")
    source_fields = {
        "identity",
        "authorizationBoundary",
        "sourceDataOwner",
        "sourcePermissionReference",
        "approvalReference",
    }
    if not isinstance(source, dict) or set(source) != source_fields:
        diagnostics.append("export/source: closed authorization evidence required")
        source = source if isinstance(source, dict) else {}
    _require_reference(source.get("identity"), "export/source/identity", diagnostics)
    if source.get("authorizationBoundary") != SPRING_AUTHORIZATION_BOUNDARY:
        diagnostics.append("export/source/authorizationBoundary: SPRING_AUTHORIZED_CONTEXT required")
    if source.get("sourceDataOwner") != card.get("source_data_owner"):
        diagnostics.append("export/source/sourceDataOwner: dataset card owner mismatch")
    inline_permission = source.get("sourcePermissionReference")
    inline_approval = source.get("approvalReference")
    if inline_permission is None and inline_approval is None:
        diagnostics.extend(
            _validate_source_approval(
                export,
                card,
                source_approval,
                source_approval_reference,
                source_export_sha256,
                require_durable_source_approval,
            )
        )
    elif inline_permission is None or inline_approval is None:
        diagnostics.append("export/source: inline permission and approval references must be paired")
    else:
        if source_approval is not None or source_approval_reference is not None:
            diagnostics.append("export/source: sidecar approval is forbidden when inline evidence exists")
        if inline_permission not in card.get("source_permission_references", []):
            diagnostics.append("export/source/sourcePermissionReference: verified card evidence required")
        if inline_approval not in card.get("approval_references", []):
            diagnostics.append("export/source/approvalReference: approved card evidence required")
    records = export.get("records")
    if not isinstance(records, list) or any(not isinstance(record, dict) for record in records):
        diagnostics.append("export/records: array of record objects required")
    if diagnostics:
        raise DatasetPipelineError(diagnostics)
    return records


def sanitize_record(record: dict[str, Any]) -> dict[str, Any]:
    """Project the closed P6-T3 root allow-list before schema validation."""
    return {field: copy.deepcopy(record[field]) for field in SOURCE_RECORD_FIELDS if field in record}


def training_content(record: dict[str, Any]) -> dict[str, Any]:
    """Keep only model-development fields; source IDs and governance stay in the manifest plane."""
    return {field: copy.deepcopy(record[field]) for field in TRAINING_RECORD_FIELDS if field in record}


def _pointer(parts: list[object]) -> str:
    if not parts:
        return "/"
    return "/" + "/".join(str(part).replace("~", "~0").replace("/", "~1") for part in parts)


def _leaf_schema_errors(error: Any) -> list[Any]:
    if not error.context:
        return [error]
    leaves: list[Any] = []
    for child in error.context:
        leaves.extend(_leaf_schema_errors(child))
    return leaves


@lru_cache(maxsize=1)
def p6_validators() -> tuple[dict[str, Draft202012Validator], Any]:
    try:
        schema = json.loads(P6_SCHEMA_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise DatasetPipelineError(f"cannot load P6-T3 schema: {error}") from error
    if schema.get("$id") != P6_SCHEMA_ID:
        raise DatasetPipelineError("P6-T3 schema identity mismatch")
    registry = Registry().with_resource(P6_SCHEMA_ID, Resource.from_contents(schema))
    validators = {
        domain: Draft202012Validator({"$ref": f"{P6_SCHEMA_ID}#/$defs/{definition}"}, registry=registry)
        for domain, definition in P6_ROOTS.items()
    }
    specification = importlib.util.spec_from_file_location("p6t3_dataset_validator_for_p7t1", P6_VALIDATOR_PATH)
    if specification is None or specification.loader is None:
        raise DatasetPipelineError("cannot load P6-T3 branch validator")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return validators, module


def validate_record(record: dict[str, Any]) -> list[str]:
    validators, branch_validator = p6_validators()
    validator = validators.get(record.get("domain"))
    if validator is None:
        return ["/domain: active P6-T3 domain required"]
    diagnostics: list[str] = []
    for error in validator.iter_errors(record):
        for leaf in _leaf_schema_errors(error):
            diagnostics.append(f"{_pointer(list(leaf.absolute_path))}: {leaf.message}")
    diagnostics.extend(branch_validator.branch_errors(record))
    return sorted(set(diagnostics))


def _identity(record: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes(training_content(record)))


def prepare_records(records: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]], int, str]:
    """Sanitize, deduplicate, then validate in the required P7-T1 stage order."""
    sanitized = [sanitize_record(record) for record in records]
    canonical_sanitized = sorted(canonical_bytes(record) for record in sanitized)
    canonical_export_digest = sha256_bytes(b"\n".join(canonical_sanitized))

    duplicate_groups: dict[str, list[dict[str, Any]]] = {}
    for record in sanitized:
        duplicate_groups.setdefault(_identity(record), []).append(record)
    duplicates_removed = sum(len(group) - 1 for group in duplicate_groups.values())

    accepted: list[dict[str, Any]] = []
    rejections: list[dict[str, Any]] = []
    for content_id in sorted(duplicate_groups):
        record = min(duplicate_groups[content_id], key=canonical_bytes)
        diagnostics = validate_record(record)
        if diagnostics:
            rejections.append({"contentId": content_id, "diagnostics": diagnostics})
            continue
        artifact_record = training_content(record)
        artifact_record["contentId"] = content_id
        accepted.append(artifact_record)
    return accepted, rejections, duplicates_removed, canonical_export_digest


def split_records(records: list[dict[str, Any]], split_config: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    weights = (
        ("train", split_config["trainWeight"]),
        ("validation", split_config["validationWeight"]),
        ("evaluation", split_config["evaluationWeight"]),
    )
    split_records_by_name: dict[str, list[dict[str, Any]]] = {name: [] for name, _ in weights}
    seed = split_config["seed"].encode("utf-8")
    for record in records:
        bucket = int.from_bytes(hashlib.sha256(seed + b"\n" + record["contentId"].encode("ascii")).digest()[:8], "big") % 100
        upper_bound = 0
        for name, weight in weights:
            upper_bound += weight
            if bucket < upper_bound:
                split_records_by_name[name].append(record)
                break
    for split_records_for_name in split_records_by_name.values():
        split_records_for_name.sort(key=lambda item: item["contentId"])
    return split_records_by_name


def jsonl_bytes(records: list[dict[str, Any]]) -> bytes:
    return b"".join(canonical_bytes(record) + b"\n" for record in records)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open(encoding="utf-8", newline="") as handle:
        for line_number, line in enumerate(handle, start=1):
            if line.endswith("\r\n"):
                raise DatasetPipelineError(f"{path.name}:{line_number}: CRLF is not canonical")
            if line.strip():
                records.append(json.loads(line))
    return records


def read_artifact_records(output_directory: Path) -> list[dict[str, Any]]:
    return [
        record
        for split_name in ("train", "validation", "evaluation")
        for record in read_jsonl(output_directory / SPLIT_FILENAMES[split_name])
    ]


def _governance_manifest(card: dict[str, Any], config: dict[str, Any]) -> dict[str, Any]:
    contract = governance_contract()
    generated_fields = {"checksum", "checksum_algorithm", "manifest_created_at_reference", "card_reference"}
    fields = set(contract["dataset_manifest_contract"]["required_fields"]) - generated_fields
    fields.update(_required_card_fields(contract, card) - {"title", "description", "limitations_and_bias", "created_at_reference"})
    return {field: copy.deepcopy(card[field]) for field in sorted(fields) if field in card} | {
        "checksum_algorithm": "SHA-256",
        "manifest_created_at_reference": "NOT_RECORDED_REPRODUCIBLE_BUILD",
        "card_reference": config["cardReference"],
    }


def dataset_identity(manifest: dict[str, Any]) -> str:
    identity_document = {key: value for key, value in manifest.items() if key != "checksum"}
    return sha256_bytes(canonical_bytes(identity_document))


def _manifest_bytes(manifest: dict[str, Any]) -> bytes:
    rendered = json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
    return (rendered + "\n").encode("utf-8")


def _write_artifacts(output_directory: Path, artifacts: dict[str, bytes], manifest: dict[str, Any]) -> None:
    if output_directory.exists():
        raise DatasetPipelineError("output: directory must not already exist")
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f".{output_directory.name}.", dir=output_directory.parent) as temporary_name:
        temporary_directory = Path(temporary_name)
        for filename, artifact in artifacts.items():
            (temporary_directory / filename).write_bytes(artifact)
        (temporary_directory / "manifest.json").write_bytes(_manifest_bytes(manifest))
        os.replace(temporary_directory, output_directory)


def build_dataset(
    export: object,
    card: object,
    config: object,
    output_directory: Path,
    evaluation_manifest: object | None = None,
    *,
    source_approval: object | None = None,
    source_approval_reference: str | None = None,
    source_export_sha256: str | None = None,
) -> dict[str, Any]:
    validate_config(config)
    validate_card(card, evaluation_manifest)
    assert isinstance(card, dict)
    assert isinstance(config, dict)
    records = validate_controlled_export(
        export,
        card,
        source_approval=source_approval,
        source_approval_reference=source_approval_reference,
        source_export_sha256=source_export_sha256,
    )
    assert isinstance(export, dict) and isinstance(export["source"], dict)

    accepted, rejections, duplicates_removed, canonical_export_digest = prepare_records(records)
    splits = split_records(accepted, config["split"])
    artifacts = {SPLIT_FILENAMES[name]: jsonl_bytes(split_records_for_name) for name, split_records_for_name in splits.items()}
    artifacts["rejections.jsonl"] = jsonl_bytes(rejections)
    artifact_inventory = [
        {
            "filename": filename,
            "recordCount": len(rejections) if filename == "rejections.jsonl" else len(splits[filename.removesuffix(".jsonl")]),
            "sha256": sha256_bytes(artifact),
        }
        for filename, artifact in sorted(artifacts.items())
    ]

    manifest = _governance_manifest(card, config)
    manifest.update(
        {
            "pipeline_schema_version": PIPELINE_SCHEMA_VERSION,
            "pipeline_version": PIPELINE_VERSION,
            "source_export": {
                "exportSchemaVersion": export["exportSchemaVersion"],
                "identity": export["source"]["identity"],
                "authorizationBoundary": export["source"]["authorizationBoundary"],
                "sourceDataOwner": export["source"]["sourceDataOwner"],
                "sourcePermissionReference": (
                    export["source"]["sourcePermissionReference"] or source_approval_reference
                ),
                "approvalReference": export["source"]["approvalReference"] or source_approval_reference,
                "canonicalSanitizedRecordsSha256": canonical_export_digest,
            },
            "pipeline_configuration": copy.deepcopy(config),
            "pipeline_configuration_sha256": sha256_bytes(canonical_bytes(config)),
            "sanitizer": {"method": "P6_T3_ROOT_ALLOWLIST", "version": SANITIZER_VERSION},
            "validation": {"schemaId": P6_SCHEMA_ID, "version": VALIDATION_VERSION},
            "deduplication_method": {"method": "SHA256_CANONICAL_TRAINING_CONTENT", "version": DEDUPLICATION_VERSION},
            "split_configuration": copy.deepcopy(config["split"]),
            "serialization": {"format": "JSONL", "version": SERIALIZATION_VERSION, "encoding": "UTF-8", "newline": "LF"},
            "counts": {
                "sourceRecords": len(records),
                "duplicatesRemoved": duplicates_removed,
                "rejectedRecords": len(rejections),
                "acceptedRecords": len(accepted),
                "splits": {name: len(split_records_for_name) for name, split_records_for_name in splits.items()},
            },
            "artifacts": artifact_inventory,
        }
    )
    manifest["checksum"] = dataset_identity(manifest)
    _write_artifacts(output_directory, artifacts, manifest)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--export", required=True, type=Path, help="Spring-authorized offline export (JSON or YAML)")
    parser.add_argument("--card", required=True, type=Path, help="approved P6-T2 dataset card (JSON or YAML)")
    parser.add_argument("--config", required=True, type=Path, help="deterministic P7-T1 pipeline config")
    parser.add_argument("--evaluation-manifest", type=Path, help="required frozen evaluation manifest for TRAINING")
    parser.add_argument(
        "--source-approval",
        type=Path,
        help="approved sidecar for an immutable source whose inline approval fields remain unresolved",
    )
    parser.add_argument("--output", required=True, type=Path, help="new artifact directory")
    args = parser.parse_args()
    try:
        export = load_document(args.export)
        manifest = build_dataset(
            export,
            load_document(args.card),
            load_document(args.config),
            args.output,
            load_document(args.evaluation_manifest) if args.evaluation_manifest else None,
            source_approval=load_document(args.source_approval) if args.source_approval else None,
            source_approval_reference=args.source_approval.as_posix() if args.source_approval else None,
            source_export_sha256=sha256_bytes(args.export.read_bytes()),
        )
        print(f"PASS {manifest['dataset_id']} checksum={manifest['checksum']}")
        return 0
    except (DatasetPipelineError, OSError) as error:
        print(f"ERROR {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
