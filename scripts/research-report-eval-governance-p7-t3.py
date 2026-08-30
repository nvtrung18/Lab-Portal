#!/usr/bin/env python3
"""Validate and explicitly finalize the scoped P7-T3 report-evaluation request.

This tool never changes authoritative data governance. It emits a new,
append-only approval artifact only when an approver and timestamp are supplied.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
REQUEST_PATH = ROOT / "config" / "p7-t3-research-report-eval-governance-request.json"
GOVERNANCE_PATH = ROOT / "docs" / "architecture" / "ai" / "data-governance.yml"
FIXTURE_PATH = ROOT / "docs" / "architecture" / "ai" / "datasets" / "fixtures" / "p6-t3-cases.yaml"
SCHEMA_PATH = ROOT / "docs" / "architecture" / "ai" / "datasets" / "domain-dataset-schemas.schema.json"
GAP_SUITE_PATH = ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json"
REQUEST_ARTIFACT_TYPE = "P7-T3-RESEARCH-REPORT-EVAL-GOVERNANCE-REQUEST"
APPROVAL_ARTIFACT_TYPE = "P7-T3-RESEARCH-REPORT-EVAL-GOVERNANCE-APPROVAL"
SCHEMA_VERSION = "1.0.0"
REQUEST_REFERENCE = "config/p7-t3-research-report-eval-governance-request.json"
ALLOWED_PURPOSES = ["EVALUATION", "HUMAN_EVALUATION"]
FORBIDDEN_PURPOSES = [
    "BENCHMARK",
    "DEVELOPMENT_TEST",
    "EXTERNAL_SHARING",
    "GENERAL_DEVELOPMENT_EXPORT",
    "PRODUCTION_PROMPTING",
    "RAG_INGESTION",
    "TRAINING",
    "UNRESTRICTED_BENCHMARK_REUSE",
]
TIMESTAMP_PATTERN = re.compile(r"^20[0-9]{2}-(0[1-9]|1[0-2])-([0-2][0-9]|3[0-1])T([0-1][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


class GovernanceError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        self.diagnostics = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        super().__init__("; ".join(self.diagnostics))


def _reject_duplicate_json_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise GovernanceError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_document(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
        value = (
            json.loads(text, object_pairs_hook=_reject_duplicate_json_keys)
            if path.suffix.lower() == ".json"
            else yaml.safe_load(text)
        )
    except (OSError, json.JSONDecodeError, yaml.YAMLError) as error:
        raise GovernanceError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise GovernanceError(f"{path.name}: object required")
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
        raise GovernanceError(f"canonical JSON required: {error}") from error


def canonical_sha256(value: object) -> str:
    return hashlib.sha256(canonical_bytes(value)).hexdigest()


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def request_identity(request: dict[str, Any]) -> str:
    return canonical_sha256({key: value for key, value in request.items() if key != "requestIdentity"})


def approval_identity(approval: dict[str, Any]) -> str:
    return canonical_sha256({key: value for key, value in approval.items() if key != "artifactIdentity"})


def _valid_utc_timestamp(value: object) -> bool:
    if not isinstance(value, str) or not TIMESTAMP_PATTERN.fullmatch(value):
        return False
    try:
        datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError:
        return False
    return True


def category_record(governance: dict[str, Any]) -> dict[str, Any]:
    categories = governance.get("contract", {}).get("data_governance_matrix")
    if not isinstance(categories, list):
        raise GovernanceError("governance category inventory unavailable")
    matches = [item for item in categories if isinstance(item, dict) and item.get("category_id") == "CAT_RESEARCH_REPORT_METADATA"]
    if len(matches) != 1:
        raise GovernanceError("CAT_RESEARCH_REPORT_METADATA: exactly one authoritative category required")
    return matches[0]


def fixture_record(fixture_document: dict[str, Any]) -> dict[str, Any]:
    cases = fixture_document.get("cases")
    matches = [item.get("record") for item in cases or [] if isinstance(item, dict) and item.get("id") == "POS-RESEARCH-006"]
    if len(matches) != 1 or not isinstance(matches[0], dict):
        raise GovernanceError("POS-RESEARCH-006: exact fixture record required")
    return matches[0]


def report_case(gap_suite: dict[str, Any]) -> dict[str, Any]:
    cases = gap_suite.get("proposedCaseInventory")
    matches = [item for item in cases or [] if isinstance(item, dict) and item.get("evalCaseId") == "E-FUNC-RESEARCH-006"]
    if len(matches) != 1:
        raise GovernanceError("E-FUNC-RESEARCH-006: exact proposed case required")
    return matches[0]


def _require_exact_fields(value: object, expected: set[str], label: str, diagnostics: list[str]) -> bool:
    if not isinstance(value, dict):
        diagnostics.append(f"{label}: object required")
        return False
    if set(value) != expected:
        diagnostics.append(f"{label}: exact fields required")
        return False
    return True


def validate_request(
    request: object,
    governance: dict[str, Any],
    fixture_document: dict[str, Any],
    schema: dict[str, Any],
    gap_suite: dict[str, Any],
) -> None:
    diagnostics: list[str] = []
    fields = {
        "artifactType", "schemaVersion", "requestId", "status", "category", "requestedScope",
        "fixture", "schema", "evaluationCase", "candidate", "suiteLineage", "requester",
        "sourceCommit", "approval", "approvedBy", "approvedAt", "requestIdentity",
    }
    if not _require_exact_fields(request, fields, "governance request", diagnostics):
        raise GovernanceError(diagnostics)
    assert isinstance(request, dict)
    if request.get("artifactType") != REQUEST_ARTIFACT_TYPE or request.get("schemaVersion") != SCHEMA_VERSION:
        diagnostics.append("governance request: supported artifact and schema version required")
    if request.get("requestId") != "P7-T3-RESEARCH-REPORT-EVAL-GOVERNANCE-REQUEST-001":
        diagnostics.append("governance request/requestId: stable request ID required")
    if request.get("status") != "PENDING_USER_APPROVAL" or any(
        request.get(field) is not None for field in ("approval", "approvedBy", "approvedAt")
    ):
        diagnostics.append("governance request: pending state with null approval fields required")

    category = category_record(governance)
    expected_category = {
        "categoryId": category.get("category_id"),
        "sourcePartition": category.get("source_partition"),
        "sourceDataOwner": category.get("source_data_owner"),
        "datasetSteward": category.get("dataset_steward"),
        "approvalAuthority": category.get("approval_authority"),
        "classification": category.get("classification"),
        "currentUseDecision": category.get("use_decision"),
        "currentPermittedPurposes": sorted(category.get("permitted_purposes", [])),
        "currentProhibitedPurposes": sorted(category.get("prohibited_purposes", [])),
        "currentRetentionClass": category.get("retention_class"),
        "visibility": category.get("visibility"),
        "currentSanitizationDisposition": category.get("sanitization_disposition"),
        "useCaseIds": category.get("use_case_ids"),
    }
    if request.get("category") != expected_category:
        diagnostics.append("governance request/category: exact authoritative deferred category required")
    if (
        category.get("use_decision") != "DEFERRED"
        or category.get("permitted_purposes") != []
        or set(category.get("prohibited_purposes", []))
        != {"TRAINING", "EVALUATION", "BENCHMARK", "HUMAN_EVALUATION", "DEVELOPMENT_TEST"}
        or category.get("retention_class") != "QUARANTINE_UNTIL_DISPOSITION"
        or category.get("sanitization_disposition") != "DEFERRED_NO_EXPORT"
    ):
        diagnostics.append("governance request/category: authoritative deferred baseline changed")

    expected_scope = {
        "purposeMode": "EVALUATION_ONLY",
        "requestedUseDecision": "SYNTHETIC_ONLY",
        "permittedPurposes": ALLOWED_PURPOSES,
        "forbiddenPurposes": FORBIDDEN_PURPOSES,
        "dataScope": "BOUND_SYNTHETIC_FIXTURE_ONLY",
        "requestedRetentionClass": "APPROVAL_BOUND",
        "requestedSanitizationDisposition": "SYNTHETIC_GENERATION_ONLY",
        "trainingAllowed": False,
        "productionPromptingAllowed": False,
        "ragIngestionAllowed": False,
        "externalSharingAllowed": False,
        "generalExportAllowed": False,
    }
    if request.get("requestedScope") != expected_scope:
        diagnostics.append("governance request/requestedScope: exact evaluation-only synthetic scope required")

    fixture = fixture_record(fixture_document)
    expected_fixture = {
        "fixtureCaseId": "POS-RESEARCH-006",
        "reference": "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml#/cases/POS-RESEARCH-006",
        "schemaRoot": "researchRecord",
        "synthetic": True,
        "sha256": canonical_sha256(fixture),
    }
    if request.get("fixture") != expected_fixture:
        diagnostics.append("governance request/fixture: exact synthetic fixture identity required")
    if (
        fixture.get("metadata") != {"synthetic": True}
        or fixture.get("useCaseId") != "RESEARCH_UC_006"
        or fixture.get("governance", {}).get("categoryIds") != ["CAT_RESEARCH_REPORT_METADATA"]
    ):
        diagnostics.append("governance request/fixture: bounded synthetic RESEARCH_UC_006 fixture required")

    expected_schema = {
        "id": "https://lab-portal.local/schemas/p6-t3/domain-dataset-schemas/1.0.0",
        "reference": "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
        "sha256": file_sha256(SCHEMA_PATH),
    }
    if schema.get("$id") != expected_schema["id"] or request.get("schema") != expected_schema:
        diagnostics.append("governance request/schema: exact P6-T3 schema identity required")

    case = report_case(gap_suite)
    expected_case = {
        "evalCaseId": "E-FUNC-RESEARCH-006",
        "useCaseId": "RESEARCH_UC_006",
        "assistantKey": "RESEARCH_ASSISTANT",
        "actionRisk": "DRAFT_ONLY",
        "caseState": "GOVERNANCE_PENDING",
        "sha256": canonical_sha256(case),
    }
    if request.get("evaluationCase") != expected_case:
        diagnostics.append("governance request/evaluationCase: exact pending report-review case identity required")

    expected_candidate = {
        "id": "qwen3_4b",
        "sourceRunId": "qwen3_4b-R01",
        "model": {
            "identifier": "Qwen/Qwen3-4B-Instruct-2507",
            "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
        },
    }
    if request.get("candidate") != expected_candidate:
        diagnostics.append("governance request/candidate: exact qwen3_4b-R01 identity required")
    expected_lineage = {
        "base": gap_suite.get("baseSuite"),
        "gap": {
            "id": gap_suite.get("suiteId"),
            "version": gap_suite.get("suiteVersion"),
            "digest": gap_suite.get("suiteDigest"),
        },
    }
    if request.get("suiteLineage") != expected_lineage:
        diagnostics.append("governance request/suiteLineage: exact suite lineage required")
    if not isinstance(request.get("requester"), str) or not request["requester"].strip():
        diagnostics.append("governance request/requester: non-empty requester required")
    if not isinstance(request.get("sourceCommit"), str) or not re.fullmatch(r"[0-9a-f]{40}", request["sourceCommit"]):
        diagnostics.append("governance request/sourceCommit: full Git commit required")
    if request.get("requestIdentity") != request_identity(request):
        diagnostics.append("governance request/requestIdentity: canonical identity mismatch")
    if diagnostics:
        raise GovernanceError(diagnostics)


def finalize_approval(
    request: dict[str, Any],
    *,
    approved_by: str,
    approved_at: str,
    gap_suite: dict[str, Any] | None = None,
) -> dict[str, Any]:
    if not isinstance(approved_by, str) or not approved_by.strip():
        raise GovernanceError("approval finalizer: explicit --approved-by required")
    if not _valid_utc_timestamp(approved_at):
        raise GovernanceError("approval finalizer: explicit --approved-at UTC timestamp required")
    validate_request(
        request,
        load_document(GOVERNANCE_PATH),
        load_document(FIXTURE_PATH),
        load_document(SCHEMA_PATH),
        gap_suite or load_document(GAP_SUITE_PATH),
    )
    scope = request.get("requestedScope", {})
    approval = {
        "artifactType": APPROVAL_ARTIFACT_TYPE,
        "schemaVersion": SCHEMA_VERSION,
        "status": "APPROVED",
        "requestIdentity": request["requestIdentity"],
        "requestReference": REQUEST_REFERENCE,
        "sourceCommit": request["sourceCommit"],
        "categoryId": request["category"]["categoryId"],
        "permittedPurposes": list(scope["permittedPurposes"]),
        "forbiddenPurposes": list(scope["forbiddenPurposes"]),
        "fixture": dict(request["fixture"]),
        "schema": dict(request["schema"]),
        "evaluationCase": dict(request["evaluationCase"]),
        "candidate": dict(request["candidate"]),
        "suiteLineage": dict(request["suiteLineage"]),
        "scope": {
            "purposeMode": scope["purposeMode"],
            "dataScope": scope["dataScope"],
            "useDecision": scope["requestedUseDecision"],
            "retentionClass": scope["requestedRetentionClass"],
            "sanitizationDisposition": scope["requestedSanitizationDisposition"],
            "trainingAllowed": False,
            "productionPromptingAllowed": False,
            "ragIngestionAllowed": False,
            "externalSharingAllowed": False,
            "generalExportAllowed": False,
        },
        "approval": {
            "decision": "APPROVED",
            "approvedBy": approved_by.strip(),
            "approvedAt": approved_at,
        },
        "artifactIdentity": "",
    }
    approval["artifactIdentity"] = approval_identity(approval)
    validate_execution_authorization(request, approval, purpose="EVALUATION")
    return approval


def validate_execution_authorization(
    request: dict[str, Any],
    approval: object | None,
    *,
    purpose: str,
) -> None:
    if not isinstance(approval, dict) or approval.get("artifactType") != APPROVAL_ARTIFACT_TYPE:
        raise GovernanceError("AWAITING_GOVERNANCE_APPROVAL: approved follow-up artifact required")
    diagnostics: list[str] = []
    fields = {
        "artifactType", "schemaVersion", "status", "requestIdentity", "requestReference", "sourceCommit",
        "categoryId", "permittedPurposes", "forbiddenPurposes", "fixture", "schema", "evaluationCase",
        "candidate", "suiteLineage", "scope", "approval", "artifactIdentity",
    }
    if not _require_exact_fields(approval, fields, "governance approval", diagnostics):
        raise GovernanceError(diagnostics)
    if approval.get("schemaVersion") != SCHEMA_VERSION or approval.get("status") != "APPROVED":
        diagnostics.append("governance approval: exact APPROVED artifact required")
    bindings = (
        ("requestIdentity", request.get("requestIdentity"), "request identity"),
        ("requestReference", REQUEST_REFERENCE, "request reference"),
        ("sourceCommit", request.get("sourceCommit"), "source identity"),
        ("categoryId", request.get("category", {}).get("categoryId"), "category"),
        ("fixture", request.get("fixture"), "fixture"),
        ("schema", request.get("schema"), "schema"),
        ("evaluationCase", request.get("evaluationCase"), "evaluation case"),
        ("candidate", request.get("candidate"), "candidate"),
        ("suiteLineage", request.get("suiteLineage"), "suite lineage"),
        ("permittedPurposes", request.get("requestedScope", {}).get("permittedPurposes"), "purposes"),
        ("forbiddenPurposes", request.get("requestedScope", {}).get("forbiddenPurposes"), "forbidden purposes"),
    )
    for field, expected, label in bindings:
        if approval.get(field) != expected:
            diagnostics.append(f"governance approval/{field}: exact {label} binding required")
    expected_scope = {
        "purposeMode": "EVALUATION_ONLY",
        "dataScope": "BOUND_SYNTHETIC_FIXTURE_ONLY",
        "useDecision": "SYNTHETIC_ONLY",
        "retentionClass": "APPROVAL_BOUND",
        "sanitizationDisposition": "SYNTHETIC_GENERATION_ONLY",
        "trainingAllowed": False,
        "productionPromptingAllowed": False,
        "ragIngestionAllowed": False,
        "externalSharingAllowed": False,
        "generalExportAllowed": False,
    }
    if approval.get("scope") != expected_scope:
        diagnostics.append("governance approval/scope: exact non-deferred evaluation-only scope required")
    approval_record = approval.get("approval")
    if not isinstance(approval_record, dict) or set(approval_record) != {"decision", "approvedBy", "approvedAt"}:
        diagnostics.append("governance approval/approval: exact approval fields required")
    else:
        if approval_record.get("decision") != "APPROVED":
            diagnostics.append("governance approval/approval: APPROVED decision required")
        if not isinstance(approval_record.get("approvedBy"), str) or not approval_record["approvedBy"].strip():
            diagnostics.append("governance approval/approval: approvedBy required")
        if not _valid_utc_timestamp(approval_record.get("approvedAt")):
            diagnostics.append("governance approval/approval: approvedAt required")
    if purpose not in ALLOWED_PURPOSES or purpose in FORBIDDEN_PURPOSES:
        diagnostics.append(f"governance approval: purpose {purpose} is not authorized")
    if approval.get("artifactIdentity") != approval_identity(approval):
        diagnostics.append("governance approval/artifactIdentity: canonical identity mismatch")
    if diagnostics:
        raise GovernanceError(diagnostics)


def write_append_only(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise GovernanceError(f"output {path}: append-only artifact already exists")
    rendered = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n"
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w", encoding="utf-8", newline="\n", dir=path.parent,
            prefix=f".{path.name}.", suffix=".tmp", delete=False,
        ) as temporary:
            temporary.write(rendered)
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_name = temporary.name
        os.link(temporary_name, path)
        Path(temporary_name).unlink()
        temporary_name = None
    except (OSError, TypeError, ValueError) as error:
        if temporary_name is not None:
            Path(temporary_name).unlink(missing_ok=True)
        raise GovernanceError(f"output {path}: cannot write append-only artifact: {error}") from error


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request", type=Path, default=REQUEST_PATH)
    parser.add_argument("--approved-by", required=True)
    parser.add_argument("--approved-at", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        request = load_document(args.request)
        validate_request(
            request,
            load_document(GOVERNANCE_PATH),
            load_document(FIXTURE_PATH),
            load_document(SCHEMA_PATH),
            load_document(GAP_SUITE_PATH),
        )
        approval = finalize_approval(
            request,
            approved_by=args.approved_by,
            approved_at=args.approved_at,
            gap_suite=load_document(GAP_SUITE_PATH),
        )
        write_append_only(args.output, approval)
        print(json.dumps(approval, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except GovernanceError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
