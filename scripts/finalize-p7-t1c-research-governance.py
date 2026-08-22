#!/usr/bin/env python3
"""Finalize the two approved P7-T1 governance chains without mutating P7-T1A data."""
from __future__ import annotations

import argparse
import copy
from datetime import datetime
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


ROOT = Path(__file__).resolve().parents[1]
SOURCE_EXPORT_PATH = ROOT / "datasets/p7-t1a-research-synthetic-source-v1/source-export.json"
PROVENANCE_PATH = ROOT / "datasets/p7-t1a-research-synthetic-source-v1/provenance.json"
TRAINING_REQUEST_PATH = ROOT / "config/p7-t1b-research-governance-packet-v1/training-approval-request.json"
PENDING_CARD_PATH = ROOT / "config/p7-t1b-research-governance-packet-v1/training-dataset-card.pending.json"
EVALUATION_REQUEST_PATH = ROOT / "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-approval-request.json"
PENDING_EVALUATION_MANIFEST_PATH = ROOT / "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-manifest.pending.json"
EVALUATION_SUITE_PATH = ROOT / "evals/p6-t4-evaluation-suites.yaml"
EVALUATION_LOCK_PATH = ROOT / "evals/p6-t4-evaluation-suite.lock.json"
EVALUATION_BINDING_PATH = ROOT / "evals/p6-t4-evaluation-freeze.binding.yaml"
P7T1_CONFIG_PATH = ROOT / "config/p7-t1-dataset-pipeline.json"

TRAINING_APPROVAL_REFERENCE = "evidence/p7-t1c-research-training-governance-approval.json"
EVALUATION_APPROVAL_REFERENCE = "evidence/p7-t1c-frozen-evaluation-governance-approval.json"
APPROVED_CARD_REFERENCE = "config/p7-t1c-research-governance-v1/training-dataset-card.approved.json"
APPROVED_EVALUATION_MANIFEST_REFERENCE = (
    "config/p7-t1c-research-governance-v1/frozen-evaluation-manifest.approved.json"
)
MATERIALIZED_DATASET_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v1"
EVALUATION_BINDING_REFERENCE = "evals/p6-t4-evaluation-freeze.binding.yaml"
EVALUATION_LOCK_REFERENCE = "evals/p6-t4-evaluation-suite.lock.json"
P7T1_CONFIG_REFERENCE = "config/p7-t1-dataset-pipeline.json"

TRAINING_REQUEST_IDENTITY = "32a766b69cd4410854c1285fc3b9b8e55d4d36cdf54352c27545f49d2fccc9b9"
EVALUATION_REQUEST_IDENTITY = "9f9b38c038c7c2ed2afaadc5496e5049f904fd3f26eac4c2feaee7ed0d8e0dba"
PENDING_CARD_IDENTITY = "4436771c6afc3ebe2fc33a82496f1f988778b370227d206615ccef8710ccdc8d"
PENDING_EVALUATION_MANIFEST_IDENTITY = "29155e0b968e43b0f53c42ddd57c47eb5fb7164385e2f54db3724decbf004615"
SOURCE_ID = "p7-t1a-research-synthetic-source"
SOURCE_VERSION = "1.0.0"
SOURCE_SHA256 = "7b5744e1e49925b228d346cf60817e2fbf976283b72c1673aedb329449503436"
CONTENT_IDENTITY = "c8dc56a1cbff71dd8c15c1eff6f561e1d6d1d4152326bbb24f99cdf2e4753722"
PROVENANCE_IDENTITY = "04ed322cf9604d753c1fdb2ab03120aa06c4856b185fb98d387f26e969c6ed1b"
EVALUATION_SUITE_ID = "P6-T4-EVALUATION-SUITES"
EVALUATION_SUITE_VERSION = "1.0.0"
EVALUATION_SUITE_DIGEST = "8b75d356890a8a5c2318305589301b6ee6d73fbd3665b9af2063f98e13ea7417"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
SCHEMA_VERSION = "1.0.0"
TIMESTAMP_PATTERN = re.compile(
    r"^20[0-9]{2}-(0[1-9]|1[0-2])-([0-2][0-9]|3[0-1])T([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$"
)


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {path.name}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


P7T1 = _load_module("p7t1_for_p7t1c", ROOT / "scripts/dataset-pipeline-p7-t1.py")
P7T2 = _load_module("p7t2_for_p7t1c", ROOT / "scripts/training-pipeline-p7-t2.py")
EVALUATION = _load_module("evaluation_for_p7t1c", ROOT / "scripts/validate-evaluation-suites.py")


class FinalizationError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def _reject_duplicate_json_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise FinalizationError(f"duplicate JSON key: {key}")
        value[key] = item
    return value


def load_document(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
        value = (
            json.loads(text, object_pairs_hook=_reject_duplicate_json_keys)
            if path.suffix.lower() == ".json"
            else yaml.safe_load(text)
        )
    except (OSError, json.JSONDecodeError, yaml.YAMLError) as error:
        raise FinalizationError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise FinalizationError(f"{path.name}: object required")
    return value


def canonical_bytes(value: object) -> bytes:
    try:
        return json.dumps(
            value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
        ).encode("utf-8")
    except (TypeError, ValueError) as error:
        raise FinalizationError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")


def ordered_json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=False, indent=2, allow_nan=False) + "\n").encode("utf-8")


def yaml_bytes(value: object) -> bytes:
    rendered = yaml.safe_dump(value, allow_unicode=True, sort_keys=False, line_break="\n")
    version = value.get("suiteVersion") if isinstance(value, dict) else None
    if isinstance(version, str):
        rendered = rendered.replace(f"suiteVersion: {version}\n", f'suiteVersion: "{version}"\n', 1)
    return rendered.encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def request_identity(request: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes({key: value for key, value in request.items() if key != "requestIdentity"}))


def artifact_identity(artifact: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes({key: value for key, value in artifact.items() if key != "artifactIdentity"}))


def _valid_timestamp(value: object) -> bool:
    if not isinstance(value, str) or not TIMESTAMP_PATTERN.fullmatch(value):
        return False
    try:
        datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError:
        return False
    return True


def _authoritative_inputs() -> dict[str, dict[str, Any]]:
    values = {
        "export": load_document(SOURCE_EXPORT_PATH),
        "provenance": load_document(PROVENANCE_PATH),
        "training_request": load_document(TRAINING_REQUEST_PATH),
        "pending_card": load_document(PENDING_CARD_PATH),
        "evaluation_request": load_document(EVALUATION_REQUEST_PATH),
        "pending_evaluation_manifest": load_document(PENDING_EVALUATION_MANIFEST_PATH),
        "suite": load_document(EVALUATION_SUITE_PATH),
        "lock": load_document(EVALUATION_LOCK_PATH),
        "binding": load_document(EVALUATION_BINDING_PATH),
        "p7t1_config": load_document(P7T1_CONFIG_PATH),
    }
    diagnostics: list[str] = []
    if sha256_bytes(SOURCE_EXPORT_PATH.read_bytes()) != SOURCE_SHA256:
        diagnostics.append("P7-T1A source SHA-256 mismatch")
    if sha256_bytes(canonical_bytes(values["export"].get("records"))) != CONTENT_IDENTITY:
        diagnostics.append("P7-T1A content identity mismatch")
    provenance = values["provenance"]
    if provenance.get("provenanceIdentity") != PROVENANCE_IDENTITY:
        diagnostics.append("P7-T1A provenance identity mismatch")
    training_request = values["training_request"]
    evaluation_request = values["evaluation_request"]
    if training_request.get("requestIdentity") != TRAINING_REQUEST_IDENTITY or request_identity(training_request) != TRAINING_REQUEST_IDENTITY:
        diagnostics.append("TRAINING request identity mismatch")
    if evaluation_request.get("requestIdentity") != EVALUATION_REQUEST_IDENTITY or request_identity(evaluation_request) != EVALUATION_REQUEST_IDENTITY:
        diagnostics.append("EVALUATION request identity mismatch")
    if sha256_bytes(canonical_bytes(values["pending_card"])) != PENDING_CARD_IDENTITY:
        diagnostics.append("pending dataset-card identity mismatch")
    if sha256_bytes(canonical_bytes(values["pending_evaluation_manifest"])) != PENDING_EVALUATION_MANIFEST_IDENTITY:
        diagnostics.append("pending evaluation-manifest identity mismatch")
    if EVALUATION.file_digest(EVALUATION_SUITE_PATH) != EVALUATION_SUITE_DIGEST:
        diagnostics.append("frozen evaluation suite digest mismatch")
    if training_request.get("approvalAuthority") != APPROVAL_AUTHORITY:
        diagnostics.append("TRAINING approval authority mismatch")
    if evaluation_request.get("approvalAuthority") != APPROVAL_AUTHORITY:
        diagnostics.append("EVALUATION approval authority mismatch")
    if diagnostics:
        raise FinalizationError(diagnostics)
    return values


def _training_approval(request: dict[str, Any], approved_by: str, approved_at: str) -> dict[str, Any]:
    source = request["source"]
    requested = request["requestedScope"]
    approval = {
        "artifactType": "P7-T1C-RESEARCH-TRAINING-GOVERNANCE-APPROVAL",
        "schemaVersion": SCHEMA_VERSION,
        "status": "APPROVED",
        "requestIdentity": request["requestIdentity"],
        "requestReference": "config/p7-t1b-research-governance-packet-v1/training-approval-request.json",
        "purpose": "TRAINING",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "source": copy.deepcopy(source),
        "dataset": copy.deepcopy(request["candidateCard"]),
        "scope": {
            "assistantKey": requested["assistantKey"],
            "permittedPurposes": list(requested["permittedPurposes"]),
            "modelDevelopmentOperation": requested["modelDevelopmentOperation"],
            "retentionClass": requested["requestedRetentionClass"],
            "projectOnly": requested["projectOnly"],
            "externalSharingAllowed": requested["externalSharingAllowed"],
            "productionPromptingAllowed": requested["productionPromptingAllowed"],
            "ragIngestionAllowed": requested["ragIngestionAllowed"],
        },
        "sourcePermission": {
            "status": "VERIFIED",
            "sourceDataOwner": requested["sourceDataOwner"],
            "evidenceReference": TRAINING_APPROVAL_REFERENCE,
        },
        "approval": {"decision": "APPROVED", "approvedBy": approved_by, "approvedAt": approved_at},
        "revocation": {"status": "ACTIVE", "authority": APPROVAL_AUTHORITY},
        "sourceCommit": request["sourceCommit"],
        "artifactIdentity": "",
    }
    approval["artifactIdentity"] = artifact_identity(approval)
    return approval


def _evaluation_approval(request: dict[str, Any], approved_by: str, approved_at: str) -> dict[str, Any]:
    requested = request["requestedScope"]
    approval = {
        "artifactType": "P7-T1C-FROZEN-EVALUATION-GOVERNANCE-APPROVAL",
        "schemaVersion": SCHEMA_VERSION,
        "status": "APPROVED",
        "requestIdentity": request["requestIdentity"],
        "requestReference": "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-approval-request.json",
        "purpose": "EVALUATION",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "suite": copy.deepcopy(request["suite"]),
        "scope": {
            "purposeMode": requested["purposeMode"],
            "permittedPurposes": list(requested["permittedPurposes"]),
            "forbiddenPurposes": list(requested["forbiddenPurposes"]),
            "lifecycleStatus": requested["requestedLifecycleStatus"],
            "freezeStatus": requested["requestedFreezeStatus"],
            "retentionClass": requested["requestedRetentionClass"],
            "trainingAllowed": requested["trainingAllowed"],
            "productionPromptingAllowed": requested["productionPromptingAllowed"],
            "ragIngestionAllowed": requested["ragIngestionAllowed"],
            "externalSharingAllowed": requested["externalSharingAllowed"],
        },
        "approval": {"decision": "APPROVED", "approvedBy": approved_by, "approvedAt": approved_at},
        "revocation": {"status": "ACTIVE", "authority": APPROVAL_AUTHORITY},
        "sourceCommit": request["sourceCommit"],
        "artifactIdentity": "",
    }
    approval["artifactIdentity"] = artifact_identity(approval)
    return approval


def _approved_card(
    pending: dict[str, Any], training_approval: dict[str, Any], evaluation_approval: dict[str, Any]
) -> dict[str, Any]:
    card = copy.deepcopy(pending)
    card.update(
        {
            "title": "Approved project-owned synthetic Research training dataset",
            "description": "P7-T1A synthetic Research source approved exclusively for P7-T1/P7-T2 TRAINING.",
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
    card["evaluation_freeze_prerequisite"]["evaluation_approval_reference"] = EVALUATION_APPROVAL_REFERENCE
    card["integrity"]["verified_at_reference"] = f"{TRAINING_APPROVAL_REFERENCE}#/source/sourceSha256"
    card["retention"].update(
        {
            "trigger_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval",
            "evidence_reference": f"{PROVENANCE_PATH.relative_to(ROOT).as_posix()}#/governance",
            "start_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval/approvedAt",
            "recheck_or_expiry_reference": f"{TRAINING_APPROVAL_REFERENCE}#/revocation",
        }
    )
    for decision in card["sanitization"]["field_decisions"]:
        decision["reviewer_reference"] = TRAINING_APPROVAL_REFERENCE
    card["sanitization"]["reviewer_reference"] = TRAINING_APPROVAL_REFERENCE
    return card


def _approved_evaluation_manifest(pending: dict[str, Any]) -> dict[str, Any]:
    manifest = copy.deepcopy(pending)
    manifest["approval_references"] = [EVALUATION_APPROVAL_REFERENCE]
    manifest["approval_status"] = "APPROVED"
    return manifest


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    expected = {
        TRAINING_APPROVAL_REFERENCE,
        EVALUATION_APPROVAL_REFERENCE,
        APPROVED_CARD_REFERENCE,
        APPROVED_EVALUATION_MANIFEST_REFERENCE,
    }
    if set(documents) != expected:
        raise FinalizationError("P7-T1C document inventory mismatch")
    training = documents[TRAINING_APPROVAL_REFERENCE]
    evaluation = documents[EVALUATION_APPROVAL_REFERENCE]
    card = documents[APPROVED_CARD_REFERENCE]
    manifest = documents[APPROVED_EVALUATION_MANIFEST_REFERENCE]
    inputs = _authoritative_inputs()
    approved_by = training.get("approval", {}).get("approvedBy")
    approved_at = training.get("approval", {}).get("approvedAt")
    diagnostics: list[str] = []
    if not _valid_timestamp(approved_at) or approved_by != APPROVAL_AUTHORITY:
        diagnostics.append("TRAINING approval: exact authority and real UTC timestamp required")
    if training != _training_approval(inputs["training_request"], approved_by, approved_at):
        diagnostics.append("TRAINING approval: exact request-bound artifact required")
    evaluation_record = evaluation.get("approval", {})
    if evaluation_record.get("approvedBy") != APPROVAL_AUTHORITY or not _valid_timestamp(evaluation_record.get("approvedAt")):
        diagnostics.append("EVALUATION approval: exact authority and real UTC timestamp required")
    if evaluation != _evaluation_approval(
        inputs["evaluation_request"], evaluation_record.get("approvedBy"), evaluation_record.get("approvedAt")
    ):
        diagnostics.append("EVALUATION approval: exact frozen-suite-bound artifact required")
    if card != _approved_card(inputs["pending_card"], training, evaluation):
        diagnostics.append("approved dataset card: exact finalized candidate required")
    if manifest != _approved_evaluation_manifest(inputs["pending_evaluation_manifest"]):
        diagnostics.append("approved evaluation manifest: exact finalized candidate required")
    if training.get("scope", {}).get("permittedPurposes") != ["TRAINING"]:
        diagnostics.append("TRAINING approval: purpose widening is forbidden")
    if (
        evaluation.get("scope", {}).get("permittedPurposes") != ["EVALUATION"]
        or evaluation.get("scope", {}).get("forbiddenPurposes") != ["TRAINING"]
        or evaluation.get("scope", {}).get("trainingAllowed") is not False
    ):
        diagnostics.append("EVALUATION approval: EVALUATION-only non-TRAINING scope required")
    try:
        P7T1.validate_card(card, manifest)
        P7T1.validate_controlled_export(
            inputs["export"],
            card,
            source_approval=training,
            source_approval_reference=TRAINING_APPROVAL_REFERENCE,
            source_export_sha256=SOURCE_SHA256,
            require_durable_source_approval=False,
        )
    except (P7T1.DatasetPipelineError, TypeError) as error:
        diagnostics.append(f"P7-T1 approval chain: {error}")
    if b"p7-t3-research-report-eval-governance-approval" in canonical_bytes(documents).lower():
        diagnostics.append("P7-T3 approval reuse is forbidden")
    if diagnostics:
        raise FinalizationError(diagnostics)


def build_documents(*, approved_by: str, approved_at: str) -> dict[str, dict[str, Any]]:
    if approved_by != APPROVAL_AUTHORITY:
        raise FinalizationError("approval authority must match the authorized human decision")
    if not _valid_timestamp(approved_at):
        raise FinalizationError("a real UTC --approved-at timestamp is required")
    inputs = _authoritative_inputs()
    training = _training_approval(inputs["training_request"], approved_by, approved_at)
    evaluation = _evaluation_approval(inputs["evaluation_request"], approved_by, approved_at)
    documents = {
        TRAINING_APPROVAL_REFERENCE: training,
        EVALUATION_APPROVAL_REFERENCE: evaluation,
        APPROVED_CARD_REFERENCE: _approved_card(inputs["pending_card"], training, evaluation),
        APPROVED_EVALUATION_MANIFEST_REFERENCE: _approved_evaluation_manifest(
            inputs["pending_evaluation_manifest"]
        ),
    }
    validate_documents(documents)
    return documents


def build_transition_documents(
    documents: dict[str, dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    validate_documents(documents)
    inputs = _authoritative_inputs()
    binding = copy.deepcopy(inputs["binding"])
    binding["governanceState"] = "GOVERNED_EVIDENCE_APPROVED"
    binding["approvalReference"] = EVALUATION_APPROVAL_REFERENCE
    current_lock = inputs["lock"]
    lock_fields = (
        "suiteId", "suiteVersion", "lockVersion", "purpose", "localFreezeStatus",
        "EVALUATION_ONLY", "TRAINING_PROHIBITED", "suiteDigest", "canonicalInventoryDigest",
    )
    lock_file_order = (
        "evals/evaluation-suite.schema.json",
        "evals/p6-t4-evaluation-suites.yaml",
        "evals/human-eval-rubric.yaml",
        EVALUATION_BINDING_REFERENCE,
        "evals/fixtures/p6-t4/valid-suite.yaml",
        "evals/fixtures/p6-t4/valid-candidate.yaml",
        "evals/fixtures/p6-t4/valid-human-review.yaml",
        "evals/fixtures/p6-t4/pending-human-review.yaml",
        "evals/fixtures/p6-t4/invalid-cases.yaml",
    )
    lock = {field: copy.deepcopy(current_lock[field]) for field in lock_fields}
    lock["files"] = {path: current_lock["files"][path] for path in lock_file_order}
    lock["files"][EVALUATION_BINDING_REFERENCE] = sha256_bytes(
        yaml_bytes(binding).replace(b"\r\n", b"\n")
    )
    config = copy.deepcopy(inputs["p7t1_config"])
    config["cardReference"] = APPROVED_CARD_REFERENCE
    transitions = {
        EVALUATION_BINDING_REFERENCE: binding,
        EVALUATION_LOCK_REFERENCE: lock,
        P7T1_CONFIG_REFERENCE: config,
    }
    if (
        binding.get("purpose") != "EVALUATION"
        or binding.get("EVALUATION_ONLY") is not True
        or binding.get("TRAINING_PROHIBITED") is not True
        or binding.get("requiredLifecycle") != "FROZEN"
        or binding.get("requiredRetention") != "FROZEN_EVALUATION"
        or binding.get("suiteDigest") != EVALUATION_SUITE_DIGEST
    ):
        raise FinalizationError("evaluation binding: frozen EVALUATION-only boundary required")
    P7T1.validate_config(config)
    return transitions


def _serialized_document(path: str, value: dict[str, Any]) -> bytes:
    if Path(path).suffix.lower() in {".yaml", ".yml"}:
        return yaml_bytes(value)
    if path == EVALUATION_LOCK_REFERENCE:
        return ordered_json_bytes(value)
    return json_bytes(value)


def _atomic_write(path: Path, content: bytes, *, append_only: bool) -> None:
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
        if append_only:
            os.link(temporary_name, path)
            Path(temporary_name).unlink()
        else:
            os.replace(temporary_name, path)
        temporary_name = None
    except OSError as error:
        if temporary_name is not None:
            Path(temporary_name).unlink(missing_ok=True)
        raise FinalizationError(f"output {path}: cannot write: {error}") from error


def write_document_set(
    root: Path,
    documents: dict[str, dict[str, Any]],
    transitions: dict[str, dict[str, Any]],
) -> None:
    validate_documents(documents)
    if transitions != build_transition_documents(documents):
        raise FinalizationError("transition document mismatch")
    existing = [reference for reference in documents if (root / reference).exists()]
    if existing:
        raise FinalizationError("append-only artifact already exists: " + ", ".join(sorted(existing)))
    for reference, value in sorted(documents.items()):
        _atomic_write(root / reference, _serialized_document(reference, value), append_only=True)
    for reference, value in sorted(transitions.items()):
        _atomic_write(root / reference, _serialized_document(reference, value), append_only=False)


def finalize_repository(*, approved_by: str, approved_at: str) -> dict[str, dict[str, Any]]:
    inputs = _authoritative_inputs()
    binding = inputs["binding"]
    if (
        binding.get("governanceState") != "GOVERNED_EVIDENCE_PENDING"
        or binding.get("approvalReference") is not None
    ):
        raise FinalizationError("evaluation binding is not at the exact pending transition state")
    if inputs["p7t1_config"].get("cardReference") != "replace-with-approved-dataset-card-reference":
        raise FinalizationError("P7-T1 config is not at the exact pending card-reference state")
    documents = build_documents(approved_by=approved_by, approved_at=approved_at)
    transitions = build_transition_documents(documents)
    write_document_set(ROOT, documents, transitions)
    return documents


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--approved-by", required=True)
    parser.add_argument("--approved-at", required=True)
    args = parser.parse_args()
    try:
        documents = finalize_repository(approved_by=args.approved_by, approved_at=args.approved_at)
        print(
            json.dumps(
                {
                    "status": "APPROVED",
                    "trainingApprovalIdentity": documents[TRAINING_APPROVAL_REFERENCE]["artifactIdentity"],
                    "evaluationApprovalIdentity": documents[EVALUATION_APPROVAL_REFERENCE]["artifactIdentity"],
                },
                sort_keys=True,
            )
        )
        return 0
    except FinalizationError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
