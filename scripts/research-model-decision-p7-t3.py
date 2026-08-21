#!/usr/bin/env python3
"""Decide the Research shared-base outcome before any optional adapter build.

The tool consumes frozen P6 evidence and fails closed when per-gate results are
missing. It never treats P6-T6, a smoke adapter, or an expected observation as
proof that the Research shared-base profile passed or materially failed.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from typing import Any, Callable

import yaml


ROOT = Path(__file__).resolve().parents[1]
P7T2_SPEC = importlib.util.spec_from_file_location("p7t2_for_p7t3", ROOT / "scripts" / "training-pipeline-p7-t2.py")
P7T2 = importlib.util.module_from_spec(P7T2_SPEC)
assert P7T2_SPEC.loader is not None
P7T2_SPEC.loader.exec_module(P7T2)

SCHEMA_VERSION = "1.0.0"
PIPELINE_VERSION = "1.0.0"
ASSISTANT_KEY = "RESEARCH_ASSISTANT"
BASE_MODEL = {
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
PROMPT_PROFILE = {"profile": "research", "promptVersion": "research-v2"}
REQUIRED_GATES = (
    "TASK_PROPOSAL_DRAFT",
    "TASK_SUGGESTION",
    "REPORT_REVIEW_DRAFT",
    "SAFE_REFUSAL",
    "STRUCTURED_OUTPUT",
)
RESULTS = {"PASS", "FAIL", "NEEDS_REVIEW"}
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
WINDOWS_ABSOLUTE_PATTERN = re.compile(r"^[A-Za-z]:[\\/]")
PLACEHOLDER_IDENTITIES = {"0" * 64}


class ResearchDecisionError(ValueError):
    """Fail-closed P7-T3 diagnostic."""

    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
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
        raise ResearchDecisionError(f"value is not canonical JSON: {error}") from error
    return rendered.encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _nonempty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _absolute_reference(value: str) -> bool:
    return value.startswith(("/", "\\")) or bool(WINDOWS_ABSOLUTE_PATTERN.match(value))


def _validate_reference(value: object, path: str, diagnostics: list[str]) -> None:
    if not _nonempty_string(value):
        diagnostics.append(f"{path}: non-empty logical reference required")
    elif _absolute_reference(value):
        diagnostics.append(f"{path}: local absolute paths are forbidden")


def _validate_reference_list(
    value: object,
    path: str,
    diagnostics: list[str],
    *,
    allow_empty: bool = False,
) -> list[str]:
    if not isinstance(value, list) or (not allow_empty and not value):
        diagnostics.append(f"{path}: {'list' if allow_empty else 'non-empty reference list'} required")
        return []
    strings = [item for item in value if isinstance(item, str)]
    if len(strings) != len(value) or len(strings) != len(set(strings)):
        diagnostics.append(f"{path}: unique logical references required")
    for index, item in enumerate(value):
        _validate_reference(item, f"{path}/{index}", diagnostics)
    return strings


def _require_exact_fields(value: object, fields: set[str], path: str, diagnostics: list[str]) -> bool:
    if not isinstance(value, dict):
        diagnostics.append(f"{path}: object required")
        return False
    if set(value) != fields:
        diagnostics.append(f"{path}: exact fields {', '.join(sorted(fields))} required")
        return False
    return True


def validate_research_suite(suite: object) -> None:
    diagnostics: list[str] = []
    if not isinstance(suite, dict):
        raise ResearchDecisionError("suite: object required")
    if suite.get("suiteId") != "P6-T4-EVALUATION-SUITES":
        diagnostics.append("suite/suiteId: P6-T4-EVALUATION-SUITES required")
    if not _nonempty_string(suite.get("suiteVersion")):
        diagnostics.append("suite/suiteVersion: non-empty version required")
    if suite.get("EVALUATION_ONLY") is not True or suite.get("TRAINING_PROHIBITED") is not True:
        diagnostics.append("suite: frozen evaluation-only and training-prohibited declarations required")
    cases = suite.get("caseInventory")
    if not isinstance(cases, list):
        diagnostics.append("suite/caseInventory: list required")
    else:
        case_ids: list[str] = []
        for index, case in enumerate(cases):
            if not isinstance(case, dict) or not _nonempty_string(case.get("evalCaseId")):
                diagnostics.append(f"suite/caseInventory/{index}: evalCaseId required")
                continue
            case_ids.append(case["evalCaseId"])
        if len(case_ids) != len(set(case_ids)):
            diagnostics.append("suite/caseInventory: evalCaseId values must be unique")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def validate_baseline_evidence(evidence: object, suite: dict[str, Any]) -> None:
    diagnostics: list[str] = []
    fields = {
        "schemaVersion",
        "assistantKey",
        "baseModel",
        "promptProfile",
        "evaluationSuite",
        "evidenceReference",
        "caseResults",
    }
    if not _require_exact_fields(evidence, fields, "evidence", diagnostics):
        raise ResearchDecisionError(diagnostics)
    assert isinstance(evidence, dict)
    if evidence.get("schemaVersion") != SCHEMA_VERSION:
        diagnostics.append("evidence/schemaVersion: unsupported version")
    if evidence.get("assistantKey") != ASSISTANT_KEY:
        diagnostics.append("evidence/assistantKey: RESEARCH_ASSISTANT required")
    if evidence.get("baseModel") != BASE_MODEL:
        diagnostics.append("evidence/baseModel: exact qwen3_4b identifier and revision required")
    if evidence.get("promptProfile") != PROMPT_PROFILE:
        diagnostics.append("evidence/promptProfile: research/research-v2 required")
    evaluation_suite = evidence.get("evaluationSuite")
    if not isinstance(evaluation_suite, dict):
        diagnostics.append("evidence/evaluationSuite: object required")
    else:
        if evaluation_suite.get("id") != suite.get("suiteId"):
            diagnostics.append("evidence/evaluationSuite/id: frozen suite mismatch")
        if evaluation_suite.get("version") != suite.get("suiteVersion"):
            diagnostics.append("evidence/evaluationSuite/version: frozen suite mismatch")
        if not isinstance(evaluation_suite.get("digest"), str) or not SHA256_PATTERN.fullmatch(
            evaluation_suite["digest"]
        ):
            diagnostics.append("evidence/evaluationSuite/digest: lowercase SHA-256 required")
    _validate_reference(evidence.get("evidenceReference"), "evidence/evidenceReference", diagnostics)

    suite_cases = {
        case["evalCaseId"]: case
        for case in suite.get("caseInventory", [])
        if isinstance(case, dict) and _nonempty_string(case.get("evalCaseId"))
    }
    case_results = evidence.get("caseResults")
    seen: set[str] = set()
    if not isinstance(case_results, list):
        diagnostics.append("evidence/caseResults: list required")
    else:
        for index, result in enumerate(case_results):
            result_fields = {"evalCaseId", "result", "evidenceSha256"}
            if not _require_exact_fields(result, result_fields, f"evidence/caseResults/{index}", diagnostics):
                continue
            case_id = result.get("evalCaseId")
            if case_id in seen:
                diagnostics.append(f"evidence/caseResults/{index}: duplicate evalCaseId")
            seen.add(case_id)
            case = suite_cases.get(case_id)
            if case is None:
                diagnostics.append(f"evidence/caseResults/{index}: frozen evalCaseId required")
            elif case.get("assistantKey") != ASSISTANT_KEY:
                diagnostics.append(f"evidence/caseResults/{index}: Research-only evidence required")
            if result.get("result") not in RESULTS:
                diagnostics.append(f"evidence/caseResults/{index}: PASS, FAIL, or NEEDS_REVIEW required")
            digest = result.get("evidenceSha256")
            if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest):
                diagnostics.append(f"evidence/caseResults/{index}/evidenceSha256: lowercase SHA-256 required")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def _active_research_cases(suite: dict[str, Any]) -> list[dict[str, Any]]:
    return [
        case
        for case in suite["caseInventory"]
        if isinstance(case, dict)
        and case.get("assistantKey") == ASSISTANT_KEY
        and case.get("caseState") != "DEFERRED_ASSERTION_ONLY"
    ]


def required_gate_cases(suite: dict[str, Any]) -> dict[str, list[str]]:
    validate_research_suite(suite)
    cases = _active_research_cases(suite)
    mapping = {
        "TASK_PROPOSAL_DRAFT": [case["evalCaseId"] for case in cases if case.get("useCaseId") == "RESEARCH_UC_004"],
        "TASK_SUGGESTION": [case["evalCaseId"] for case in cases if case.get("useCaseId") == "RESEARCH_UC_005"],
        "REPORT_REVIEW_DRAFT": [case["evalCaseId"] for case in cases if case.get("useCaseId") == "RESEARCH_UC_006"],
        "SAFE_REFUSAL": [
            case["evalCaseId"]
            for case in cases
            if (
                isinstance(case.get("responseContract"), dict)
                and case["responseContract"].get("mode") == "SAFE_REFUSAL"
            )
            or (isinstance(case.get("suiteTags"), list) and "SAFE_REFUSAL" in case["suiteTags"])
        ],
        "STRUCTURED_OUTPUT": [
            case["evalCaseId"]
            for case in cases
            if isinstance(case.get("suiteTags"), list) and "STRUCTURED_OUTPUT" in case["suiteTags"]
        ],
    }
    return {gate: sorted(mapping[gate]) for gate in REQUIRED_GATES}


def evaluate_research_baseline(
    suite: dict[str, Any], evidence: dict[str, Any] | None
) -> dict[str, Any]:
    validate_research_suite(suite)
    if evidence is not None:
        validate_baseline_evidence(evidence, suite)
    result_by_case = {
        result["evalCaseId"]: result
        for result in (evidence or {}).get("caseResults", [])
    }
    gates: list[dict[str, Any]] = []
    for gate, case_ids in required_gate_cases(suite).items():
        missing = [case_id for case_id in case_ids if case_id not in result_by_case]
        failed = [case_id for case_id in case_ids if result_by_case.get(case_id, {}).get("result") == "FAIL"]
        needs_review = [
            case_id for case_id in case_ids if result_by_case.get(case_id, {}).get("result") == "NEEDS_REVIEW"
        ]
        if not case_ids:
            result = "UNRESOLVED"
            reason = "FROZEN_CASE_COVERAGE_MISSING"
        elif failed:
            result = "FAIL"
            reason = "MATERIAL_REQUIRED_CASE_FAILURE"
        elif missing:
            result = "UNRESOLVED"
            reason = "CASE_RESULT_EVIDENCE_MISSING"
        elif needs_review:
            result = "UNRESOLVED"
            reason = "CASE_RESULT_NEEDS_REVIEW"
        else:
            result = "PASS"
            reason = "ALL_FROZEN_REQUIRED_CASES_PASS"
        gates.append(
            {
                "gate": gate,
                "requiredCaseIds": case_ids,
                "result": result,
                "reason": reason,
                "failedCaseIds": failed,
                "missingCaseIds": missing,
                "needsReviewCaseIds": needs_review,
                "evidence": [copy.deepcopy(result_by_case[case_id]) for case_id in case_ids if case_id in result_by_case],
            }
        )

    if any(gate["result"] == "FAIL" for gate in gates):
        overall = "FAIL"
    elif all(gate["result"] == "PASS" for gate in gates):
        overall = "PASS"
    else:
        overall = "UNRESOLVED"
    return {"gates": gates, "overallResult": overall}


def _identity_projection(value: object) -> object:
    if isinstance(value, dict):
        return {
            key: _identity_projection(child)
            for key, child in sorted(value.items())
            if key not in {"decisionIdentity", "sourceCommit"}
        }
    if isinstance(value, list):
        return [_identity_projection(child) for child in value]
    return value


def _absolute_path_diagnostics(value: object, path: str = "decision") -> list[str]:
    diagnostics: list[str] = []
    if isinstance(value, str) and _absolute_reference(value):
        diagnostics.append(f"{path}: local absolute paths are forbidden in canonical decision identity")
    elif isinstance(value, dict):
        for key, child in value.items():
            diagnostics.extend(_absolute_path_diagnostics(child, f"{path}/{key}"))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            diagnostics.extend(_absolute_path_diagnostics(child, f"{path}/{index}"))
    return diagnostics


def research_decision_identity(record: dict[str, Any]) -> str:
    projection = _identity_projection(record)
    diagnostics = _absolute_path_diagnostics(projection)
    if diagnostics:
        raise ResearchDecisionError(diagnostics)
    return sha256_bytes(canonical_bytes(projection))


def _validate_benchmark_context(
    suite: dict[str, Any], benchmark_config: object, evidence: dict[str, Any] | None
) -> None:
    diagnostics: list[str] = []
    if not isinstance(benchmark_config, dict):
        raise ResearchDecisionError("benchmark config: object required")
    profile = benchmark_config.get("assistantProfiles", {}).get(ASSISTANT_KEY)
    if not isinstance(profile, dict) or profile.get("profile") != "research" or profile.get("prompt") != "research-v2":
        diagnostics.append("benchmark config: research/research-v2 profile required")
    suite_config = benchmark_config.get("suite")
    if not isinstance(suite_config, dict):
        diagnostics.append("benchmark config/suite: object required")
    else:
        if suite_config.get("id") != suite.get("suiteId") or suite_config.get("version") != suite.get("suiteVersion"):
            diagnostics.append("benchmark config/suite: frozen suite ID/version mismatch")
        digest = suite_config.get("digest")
        if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest):
            diagnostics.append("benchmark config/suite/digest: lowercase SHA-256 required")
        if evidence is not None and isinstance(evidence.get("evaluationSuite"), dict):
            if evidence["evaluationSuite"].get("digest") != digest:
                diagnostics.append("baseline evidence: frozen suite digest mismatch")
    candidates = benchmark_config.get("candidates")
    primary = next(
        (candidate for candidate in candidates if isinstance(candidate, dict) and candidate.get("id") == "qwen3_4b"),
        None,
    ) if isinstance(candidates, list) else None
    if not isinstance(primary, dict) or {
        "identifier": primary.get("repository"),
        "revision": primary.get("revision"),
    } != BASE_MODEL:
        diagnostics.append("benchmark config: exact qwen3_4b base model required")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def _validate_frozen_evidence_inventory(inventory: object) -> None:
    diagnostics: list[str] = []
    if not isinstance(inventory, list):
        raise ResearchDecisionError("frozen evidence: list required")
    references: list[str] = []
    for index, artifact in enumerate(inventory):
        if not _require_exact_fields(
            artifact,
            {"role", "reference", "sha256"},
            f"frozenEvidence/{index}",
            diagnostics,
        ):
            continue
        _validate_reference(artifact.get("reference"), f"frozenEvidence/{index}/reference", diagnostics)
        if _nonempty_string(artifact.get("reference")):
            references.append(artifact["reference"])
        if not _nonempty_string(artifact.get("role")):
            diagnostics.append(f"frozenEvidence/{index}/role: non-empty role required")
        digest = artifact.get("sha256")
        if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest):
            diagnostics.append(f"frozenEvidence/{index}/sha256: lowercase SHA-256 required")
    if len(references) != len(set(references)):
        diagnostics.append("frozenEvidence: references must be unique")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def file_sha256(path: Path) -> str:
    try:
        return sha256_bytes(path.read_bytes().replace(b"\r\n", b"\n"))
    except OSError as error:
        raise ResearchDecisionError(f"artifact {path}: cannot read: {error}") from error


def locked_file_sha256(path: Path) -> str:
    return file_sha256(path)


def _repository_reference(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT.resolve()).as_posix()
    except (OSError, ValueError) as error:
        raise ResearchDecisionError(f"artifact {path}: repository-relative path required") from error


def frozen_evidence_inventory(artifacts: list[tuple[str, Path]]) -> list[dict[str, str]]:
    inventory = [
        {
            "role": role,
            "reference": _repository_reference(path),
            "sha256": file_sha256(path),
        }
        for role, path in artifacts
    ]
    _validate_frozen_evidence_inventory(inventory)
    return inventory


def validate_suite_lock(
    suite: dict[str, Any],
    suite_path: Path,
    suite_lock: object,
    benchmark_config: dict[str, Any],
) -> None:
    validate_research_suite(suite)
    diagnostics: list[str] = []
    if not isinstance(suite_lock, dict):
        raise ResearchDecisionError("suite lock: object required")
    expected = {
        "suiteId": suite.get("suiteId"),
        "suiteVersion": suite.get("suiteVersion"),
        "purpose": "EVALUATION",
        "localFreezeStatus": "CONTENT_LOCKED",
        "EVALUATION_ONLY": True,
        "TRAINING_PROHIBITED": True,
    }
    for field, value in expected.items():
        if suite_lock.get(field) != value:
            diagnostics.append(f"suite lock/{field}: {value!r} required")
    digest = locked_file_sha256(suite_path)
    if suite_lock.get("suiteDigest") != digest:
        diagnostics.append("suite lock/suiteDigest: exact frozen suite file digest required")
    lock_files = suite_lock.get("files")
    suite_reference = _repository_reference(suite_path)
    if not isinstance(lock_files, dict) or lock_files.get(suite_reference) != digest:
        diagnostics.append("suite lock/files: exact frozen suite file digest required")
    benchmark_suite = benchmark_config.get("suite") if isinstance(benchmark_config, dict) else None
    if not isinstance(benchmark_suite, dict) or benchmark_suite.get("digest") != digest:
        diagnostics.append("benchmark config/suite/digest: exact frozen suite file digest required")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


P7_T1_REQUIRED_GOVERNANCE_FIELDS = {
    "dataset_id",
    "dataset_version",
    "contract_version",
    "schema_version",
    "assistant_key",
    "partition",
    "visibility",
    "category_ids",
    "classification",
    "use_decision",
    "permitted_purposes",
    "prohibited_purposes",
    "approved_purposes",
    "model_development_purpose",
    "model_development_operation",
    "source_data_owner",
    "dataset_steward",
    "approval_authority",
    "source_permission_references",
    "source_permission_status",
    "approval_references",
    "approval_status",
    "lifecycle_status",
    "freeze_status",
    "retention",
    "sanitization",
    "provenance",
    "lineage",
    "integrity",
    "split",
    "deduplication",
    "checksum",
    "checksum_algorithm",
    "manifest_created_at_reference",
    "card_reference",
    "revocation_reference",
    "evaluation_freeze_prerequisite",
}


def validate_research_dataset_approval(manifest: object) -> None:
    diagnostics: list[str] = []
    if not isinstance(manifest, dict):
        raise ResearchDecisionError("dataset manifest: object required")
    missing = sorted(P7_T1_REQUIRED_GOVERNANCE_FIELDS - set(manifest))
    if missing:
        diagnostics.append("dataset manifest: missing P7-T1 governance fields " + ", ".join(missing))
    expected_values = {
        "contract_version": "1.0.0",
        "schema_version": "1.0.0",
        "assistant_key": ASSISTANT_KEY,
        "partition": ASSISTANT_KEY,
        "visibility": "RESEARCH_ASSISTANT_ONLY",
        "classification": "SENSITIVE",
        "model_development_purpose": "TRAINING",
        "model_development_operation": "ADAPTER_FINE_TUNING",
        "source_data_owner": "RESEARCH_GOVERNANCE_DOMAIN_DATA_OWNER",
        "dataset_steward": "RESEARCH_GOVERNANCE_DATASET_STEWARD",
        "approval_authority": "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY",
        "source_permission_status": "VERIFIED",
        "approval_status": "APPROVED",
        "lifecycle_status": "APPROVED",
        "freeze_status": "NOT_REQUIRED",
        "checksum_algorithm": "SHA-256",
    }
    for field, expected in expected_values.items():
        if manifest.get(field) != expected:
            diagnostics.append(f"dataset manifest/{field}: {expected} required")
    diagnostics.extend(_absolute_path_diagnostics(manifest, "dataset manifest"))
    for field in ("dataset_id", "dataset_version"):
        if not _nonempty_string(manifest.get(field)):
            diagnostics.append(f"dataset manifest/{field}: non-empty identity required")
    for field in ("manifest_created_at_reference", "card_reference", "revocation_reference"):
        _validate_reference(manifest.get(field), f"dataset manifest/{field}", diagnostics)

    approved = _validate_reference_list(
        manifest.get("approved_purposes"), "dataset manifest/approved_purposes", diagnostics
    )
    permitted = _validate_reference_list(
        manifest.get("permitted_purposes"), "dataset manifest/permitted_purposes", diagnostics
    )
    prohibited = _validate_reference_list(
        manifest.get("prohibited_purposes"),
        "dataset manifest/prohibited_purposes",
        diagnostics,
        allow_empty=True,
    )
    if "TRAINING" not in approved or "TRAINING" not in permitted or "TRAINING" in prohibited:
        diagnostics.append("dataset manifest: TRAINING must be approved, permitted, and not prohibited")
    _validate_reference_list(
        manifest.get("source_permission_references"),
        "dataset manifest/source_permission_references",
        diagnostics,
    )
    _validate_reference_list(
        manifest.get("approval_references"), "dataset manifest/approval_references", diagnostics
    )

    category_ids = manifest.get("category_ids")
    if (
        not isinstance(category_ids, list)
        or not category_ids
        or any(not _nonempty_string(category_id) or not category_id.startswith("CAT_RESEARCH_") for category_id in category_ids)
        or len(category_ids) != len(set(category_id for category_id in category_ids if isinstance(category_id, str)))
    ):
        diagnostics.append("dataset manifest/category_ids: unique Research categories required")
        category_ids = []
    use_decision = manifest.get("use_decision")
    governance_identity = {
        "SYNTHETIC_ONLY": ("SYNTHETIC_GENERATION_ONLY", "SYNTHETIC", "EPHEMERAL_DEVELOPMENT"),
        "ELIGIBLE_AFTER_APPROVAL": ("SANITIZED_DERIVATIVE_REQUIRED", "INTERNAL_SANITIZED", "APPROVAL_BOUND"),
    }.get(use_decision)
    if governance_identity is None:
        diagnostics.append("dataset manifest/use_decision: training-eligible Research decision required")
    expected_sanitization, expected_provenance, expected_retention = governance_identity or (None, None, None)
    compatible_field_decisions = {
        "SYNTHETIC_GENERATION_ONLY": {"SYNTHETIC_REPLACE"},
        "SANITIZED_DERIVATIVE_REQUIRED": {
            "REMOVE",
            "MASK",
            "GENERALIZE",
            "PSEUDONYMIZE",
            "AGGREGATE",
            "DERIVE",
        },
    }.get(expected_sanitization, set())

    sanitization = manifest.get("sanitization")
    if not isinstance(sanitization, dict) or sanitization.get("disposition") != expected_sanitization:
        diagnostics.append("dataset manifest/sanitization: use-decision-compatible disposition required")
    else:
        for field in ("transform_reference", "reviewer_reference", "result_reference", "residual_risk_reference"):
            _validate_reference(sanitization.get(field), f"dataset manifest/sanitization/{field}", diagnostics)
        field_decisions = sanitization.get("field_decisions")
        observed_categories: list[str] = []
        if not isinstance(field_decisions, list) or not field_decisions:
            diagnostics.append("dataset manifest/sanitization/field_decisions: evidence required")
        else:
            for index, decision in enumerate(field_decisions):
                if not isinstance(decision, dict):
                    diagnostics.append(f"dataset manifest/sanitization/field_decisions/{index}: object required")
                    continue
                decision_category = decision.get("category_id")
                if _nonempty_string(decision_category):
                    observed_categories.append(decision_category)
                else:
                    diagnostics.append(
                        f"dataset manifest/sanitization/field_decisions/{index}/category_id: Research category required"
                    )
                if decision.get("field_decision") not in compatible_field_decisions:
                    diagnostics.append(
                        f"dataset manifest/sanitization/field_decisions/{index}/field_decision: incompatible decision"
                    )
                for field in (
                    "transform_reference",
                    "reviewer_reference",
                    "result_reference",
                    "residual_risk_reference",
                ):
                    _validate_reference(
                        decision.get(field),
                        f"dataset manifest/sanitization/field_decisions/{index}/{field}",
                        diagnostics,
                    )
            if sorted(observed_categories) != sorted(category_ids):
                diagnostics.append("dataset manifest/sanitization/field_decisions: category coverage mismatch")

    provenance = manifest.get("provenance")
    if not isinstance(provenance, dict) or provenance.get("type") != expected_provenance:
        diagnostics.append("dataset manifest/provenance/type: sanitization identity mismatch")
    retention = manifest.get("retention")
    if not isinstance(retention, dict) or retention.get("retention_class") != expected_retention:
        diagnostics.append("dataset manifest/retention: use-decision-compatible retention required")
    elif retention.get("disposition_action") != "DELETE":
        diagnostics.append("dataset manifest/retention/disposition_action: DELETE required")
    else:
        for field in (
            "start_reference",
            "trigger_reference",
            "recheck_or_expiry_reference",
            "disposition_owner",
            "evidence_reference",
        ):
            _validate_reference(retention.get(field), f"dataset manifest/retention/{field}", diagnostics)

    lineage = manifest.get("lineage")
    if not isinstance(lineage, dict):
        diagnostics.append("dataset manifest/lineage: complete evidence required")
    else:
        _validate_reference_list(
            lineage.get("source_references"), "dataset manifest/lineage/source_references", diagnostics
        )
        _validate_reference_list(
            lineage.get("transform_references"), "dataset manifest/lineage/transform_references", diagnostics
        )
    integrity = manifest.get("integrity")
    if not isinstance(integrity, dict):
        diagnostics.append("dataset manifest/integrity: complete evidence required")
    else:
        for field in ("checksum", "checksum_algorithm", "verified_at_reference"):
            _validate_reference(integrity.get(field), f"dataset manifest/integrity/{field}", diagnostics)
    if not isinstance(manifest.get("split"), dict) or manifest["split"].get("label") != "TRAIN":
        diagnostics.append("dataset manifest/split/label: TRAIN required")
    if (
        not isinstance(manifest.get("deduplication"), dict)
        or manifest["deduplication"].get("disposition") != "DUPLICATES_REMOVED"
    ):
        diagnostics.append("dataset manifest/deduplication: DUPLICATES_REMOVED required")

    prerequisite = manifest.get("evaluation_freeze_prerequisite")
    expected_prerequisite = {
        "evaluation_purpose": "EVALUATION",
        "evaluation_lifecycle_status": "FROZEN",
        "evaluation_freeze_status": "FROZEN",
    }
    if not isinstance(prerequisite, dict):
        diagnostics.append("dataset manifest/evaluation_freeze_prerequisite: complete evidence required")
    else:
        for field, expected in expected_prerequisite.items():
            if prerequisite.get(field) != expected:
                diagnostics.append(f"dataset manifest/evaluation_freeze_prerequisite/{field}: {expected} required")
        for field in (
            "evaluation_dataset_id",
            "evaluation_dataset_version",
            "evaluation_integrity_checksum",
            "evaluation_approval_reference",
        ):
            _validate_reference(
                prerequisite.get(field),
                f"dataset manifest/evaluation_freeze_prerequisite/{field}",
                diagnostics,
            )
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def validate_real_candidate_metadata(metadata: object) -> None:
    required = {
        "assistantKey",
        "baseModel",
        "datasetIdentity",
        "trainingConfigIdentity",
        "seed",
        "trainingRunIdentity",
        "adapterMethod",
        "checkpoints",
        "exportedArtifacts",
        "sourceCommit",
        "status",
        "backend",
        "qualityEvidence",
        "adapterDisposition",
    }
    if not isinstance(metadata, dict) or not required.issubset(metadata):
        raise ResearchDecisionError("candidate metadata: complete P7-T2 provenance required")
    if metadata.get("backend") == "SMOKE" or metadata.get("qualityEvidence") == "SMOKE_ONLY_NO_MODEL_QUALITY_EVIDENCE":
        raise ResearchDecisionError("smoke artifacts are not real candidate evidence")
    if not _nonempty_string(metadata.get("backend")) or metadata.get("backend") == "NONE":
        raise ResearchDecisionError("candidate metadata: real training backend required")
    if metadata.get("qualityEvidence") != "REAL_TRAINING_EXECUTION":
        raise ResearchDecisionError("candidate metadata: real training execution evidence required")
    if metadata.get("adapterDisposition") != "CANDIDATE_ONLY":
        raise ResearchDecisionError("candidate metadata: CANDIDATE_ONLY disposition required")
    if metadata.get("status") != "COMPLETED":
        raise ResearchDecisionError("candidate metadata: COMPLETED real training status required")
    if metadata.get("assistantKey") != ASSISTANT_KEY or metadata.get("baseModel") != BASE_MODEL:
        raise ResearchDecisionError("candidate metadata: Research base-model provenance mismatch")
    if not isinstance(metadata.get("datasetIdentity"), str) or not SHA256_PATTERN.fullmatch(metadata["datasetIdentity"]):
        raise ResearchDecisionError("candidate metadata: dataset SHA-256 required")
    if not isinstance(metadata.get("trainingConfigIdentity"), str) or not SHA256_PATTERN.fullmatch(
        metadata["trainingConfigIdentity"]
    ):
        raise ResearchDecisionError("candidate metadata: training config SHA-256 required")
    if not isinstance(metadata.get("trainingRunIdentity"), str) or not SHA256_PATTERN.fullmatch(
        metadata["trainingRunIdentity"]
    ):
        raise ResearchDecisionError("candidate metadata: training run SHA-256 required")
    checkpoints = metadata.get("checkpoints")
    if not isinstance(checkpoints, list) or not checkpoints:
        raise ResearchDecisionError("candidate metadata: checkpoint provenance required")
    for checkpoint in checkpoints:
        if (
            not isinstance(checkpoint, dict)
            or not _nonempty_string(checkpoint.get("checkpointName"))
            or not isinstance(checkpoint.get("globalStep"), int)
            or isinstance(checkpoint.get("globalStep"), bool)
            or checkpoint["globalStep"] <= 0
        ):
            raise ResearchDecisionError("candidate metadata: deterministic checkpoint provenance required")
    artifacts = metadata.get("exportedArtifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise ResearchDecisionError("candidate metadata: exported artifact checksums required")
    for artifact in artifacts:
        if (
            not isinstance(artifact, dict)
            or not _nonempty_string(artifact.get("filename"))
            or Path(artifact["filename"]).name != artifact["filename"]
            or not isinstance(artifact.get("sha256"), str)
            or not SHA256_PATTERN.fullmatch(artifact["sha256"])
        ):
            raise ResearchDecisionError("candidate metadata: deterministic exported artifact inventory required")


def _candidate_block(reason: str) -> dict[str, Any]:
    return {"status": "CANDIDATE_BUILD_BLOCKED", "reason": reason, "metadata": None, "artifactIdentity": None}


def _candidate_identity(metadata: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes(
            {
                "trainingRunIdentity": metadata["trainingRunIdentity"],
                "exportedArtifacts": metadata["exportedArtifacts"],
            }
        )
    )


def _finalize_decision(record: dict[str, Any]) -> dict[str, Any]:
    record["decisionIdentity"] = research_decision_identity(record)
    return record


def validate_research_decision_record(record: object) -> None:
    fields = {
        "schemaVersion",
        "pipelineVersion",
        "assistantKey",
        "baseModel",
        "promptProfile",
        "evaluationSuite",
        "frozenEvidence",
        "baselineEvidenceReference",
        "requiredCapabilityGates",
        "overallBaselineResult",
        "sourceCommit",
        "decision",
        "outcome",
        "reason",
        "candidateBuild",
        "training",
        "decisionIdentity",
    }
    diagnostics: list[str] = []
    if not _require_exact_fields(record, fields, "decision record", diagnostics):
        raise ResearchDecisionError(diagnostics)
    assert isinstance(record, dict)
    if record.get("schemaVersion") != SCHEMA_VERSION or record.get("pipelineVersion") != PIPELINE_VERSION:
        diagnostics.append("decision record: supported schema and pipeline versions required")
    if record.get("assistantKey") != ASSISTANT_KEY:
        diagnostics.append("decision record: RESEARCH_ASSISTANT required")
    if record.get("baseModel") != BASE_MODEL or record.get("promptProfile") != PROMPT_PROFILE:
        diagnostics.append("decision record: exact Research shared-base profile required")
    evaluation_suite = record.get("evaluationSuite")
    if (
        not isinstance(evaluation_suite, dict)
        or set(evaluation_suite) != {"id", "version", "digest"}
        or evaluation_suite.get("id") != "P6-T4-EVALUATION-SUITES"
        or not _nonempty_string(evaluation_suite.get("version"))
        or not isinstance(evaluation_suite.get("digest"), str)
        or not SHA256_PATTERN.fullmatch(evaluation_suite["digest"])
    ):
        diagnostics.append("decision record/evaluationSuite: exact frozen suite identity required")
    try:
        _validate_frozen_evidence_inventory(record.get("frozenEvidence"))
    except ResearchDecisionError as error:
        diagnostics.extend(error.diagnostics)
    baseline_reference = record.get("baselineEvidenceReference")
    if baseline_reference is not None:
        _validate_reference(baseline_reference, "decision record/baselineEvidenceReference", diagnostics)
    gates = record.get("requiredCapabilityGates")
    if not isinstance(gates, list) or [gate.get("gate") for gate in gates if isinstance(gate, dict)] != list(
        REQUIRED_GATES
    ):
        diagnostics.append("decision record: all required capability gates must be present in canonical order")
    elif any(gate.get("result") not in {"PASS", "FAIL", "UNRESOLVED"} for gate in gates):
        diagnostics.append("decision record: invalid capability-gate result")
    overall = record.get("overallBaselineResult")
    if overall not in {"PASS", "FAIL", "UNRESOLVED"}:
        diagnostics.append("decision record: invalid overall baseline result")
    decision = record.get("decision")
    outcome = record.get("outcome")
    training = record.get("training")
    candidate = record.get("candidateBuild")
    if not isinstance(training, dict) or set(training) != {"status", "invoked"} or not isinstance(
        training.get("invoked"), bool
    ):
        diagnostics.append("decision record/training: exact status and invoked fields required")
    if not isinstance(candidate, dict) or set(candidate) != {"status", "reason", "metadata", "artifactIdentity"}:
        diagnostics.append("decision record/candidateBuild: exact candidate fields required")
    if overall == "PASS":
        if decision != "BASE_ONLY_APPROVED" or outcome != "BASE_ONLY_APPROVED":
            diagnostics.append("decision record: a passing baseline must close BASE_ONLY_APPROVED")
        if isinstance(training, dict) and (training.get("invoked") is not False or training.get("status") != "NOT_REQUIRED"):
            diagnostics.append("decision record: base-only approval must not invoke training")
        if isinstance(candidate, dict) and (
            candidate.get("status") != "NOT_REQUIRED"
            or candidate.get("metadata") is not None
            or candidate.get("artifactIdentity") is not None
        ):
            diagnostics.append("decision record: base-only approval cannot expose adapter artifacts")
    else:
        if decision != "ADAPTER_REQUIRED" or outcome not in {
            "ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED",
            "ADAPTER_REQUIRED+CANDIDATE_AVAILABLE",
        }:
            diagnostics.append("decision record: non-passing baseline must fail closed as ADAPTER_REQUIRED")
    if overall == "UNRESOLVED" and outcome != "ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED":
        diagnostics.append("decision record: unresolved baseline cannot expose a candidate")
    if outcome == "ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED":
        if isinstance(candidate, dict) and (
            candidate.get("status") != "CANDIDATE_BUILD_BLOCKED"
            or candidate.get("metadata") is not None
            or candidate.get("artifactIdentity") is not None
        ):
            diagnostics.append("decision record: blocked candidate build cannot expose adapter artifacts")
        if overall == "UNRESOLVED" and isinstance(training, dict) and training != {
            "status": "BLOCKED",
            "invoked": False,
        }:
            diagnostics.append("decision record: unresolved evidence must block training before invocation")
    if outcome == "ADAPTER_REQUIRED+CANDIDATE_AVAILABLE":
        if not isinstance(candidate, dict) or candidate.get("status") != "CANDIDATE_AVAILABLE":
            diagnostics.append("decision record: available candidate metadata required")
        else:
            candidate_metadata = candidate.get("metadata")
            try:
                validate_real_candidate_metadata(candidate_metadata)
            except ResearchDecisionError as error:
                diagnostics.extend(error.diagnostics)
            if isinstance(candidate_metadata, dict) and candidate.get("artifactIdentity") != _candidate_identity(
                candidate_metadata
            ):
                diagnostics.append("decision record: candidate artifact identity mismatch")
        if isinstance(training, dict) and training != {"status": "REAL_CANDIDATE_BUILT", "invoked": True}:
            diagnostics.append("decision record: real candidate training provenance required")
    if not _nonempty_string(record.get("reason")) or not _nonempty_string(record.get("sourceCommit")):
        diagnostics.append("decision record: reason and source commit are required")
    identity = record.get("decisionIdentity")
    if not isinstance(identity, str) or not SHA256_PATTERN.fullmatch(identity):
        diagnostics.append("decision record/decisionIdentity: lowercase SHA-256 required")
    else:
        try:
            if identity != research_decision_identity(record):
                diagnostics.append("decision record/decisionIdentity: canonical identity mismatch")
        except ResearchDecisionError as error:
            diagnostics.extend(error.diagnostics)
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def _base_record(
    suite: dict[str, Any],
    benchmark_config: dict[str, Any],
    evidence: dict[str, Any] | None,
    baseline: dict[str, Any],
    frozen_evidence: list[dict[str, Any]],
    source_commit: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": SCHEMA_VERSION,
        "pipelineVersion": PIPELINE_VERSION,
        "assistantKey": ASSISTANT_KEY,
        "baseModel": copy.deepcopy(BASE_MODEL),
        "promptProfile": copy.deepcopy(PROMPT_PROFILE),
        "evaluationSuite": {
            "id": suite["suiteId"],
            "version": suite["suiteVersion"],
            "digest": benchmark_config["suite"]["digest"],
        },
        "frozenEvidence": copy.deepcopy(frozen_evidence),
        "baselineEvidenceReference": evidence.get("evidenceReference") if evidence else None,
        "requiredCapabilityGates": baseline["gates"],
        "overallBaselineResult": baseline["overallResult"],
        "sourceCommit": source_commit,
    }


def decide_research_model(
    suite: dict[str, Any],
    benchmark_config: dict[str, Any],
    decision_manifest: dict[str, Any],
    training_config: dict[str, Any],
    evidence: dict[str, Any] | None,
    *,
    frozen_evidence: list[dict[str, Any]],
    dataset_manifest_path: Path | None = None,
    training_output: Path | None = None,
    training_invoker: Callable[..., dict[str, Any]] | None = None,
    source_commit: str,
) -> dict[str, Any]:
    validate_research_suite(suite)
    if evidence is not None:
        validate_baseline_evidence(evidence, suite)
    _validate_benchmark_context(suite, benchmark_config, evidence)
    _validate_frozen_evidence_inventory(frozen_evidence)
    baseline = evaluate_research_baseline(suite, evidence)
    record = _base_record(suite, benchmark_config, evidence, baseline, frozen_evidence, source_commit)
    if baseline["overallResult"] == "PASS":
        record.update(
            {
                "decision": "BASE_ONLY_APPROVED",
                "outcome": "BASE_ONLY_APPROVED",
                "reason": "ALL_REQUIRED_RESEARCH_BASELINE_GATES_PASS",
                "candidateBuild": {"status": "NOT_REQUIRED", "reason": "BASELINE_PASSED", "metadata": None, "artifactIdentity": None},
                "training": {"status": "NOT_REQUIRED", "invoked": False},
            }
        )
        return _finalize_decision(record)

    record["decision"] = "ADAPTER_REQUIRED"
    record["outcome"] = "ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED"
    record["training"] = {"status": "BLOCKED", "invoked": False}
    if baseline["overallResult"] == "UNRESOLVED":
        record.update(
            {
                "reason": "BASELINE_EVIDENCE_IS_INCOMPLETE; FAIL_CLOSED_WITHOUT_TRAINING",
                "candidateBuild": _candidate_block("BASELINE_EVIDENCE_INCOMPLETE"),
            }
        )
        return _finalize_decision(record)

    if training_config.get("assistantKey") != ASSISTANT_KEY:
        raise ResearchDecisionError("P7-T3 is Research-only; training config must use RESEARCH_ASSISTANT")
    if training_config.get("baseModel") != BASE_MODEL:
        raise ResearchDecisionError("training config: exact qwen3_4b base model/revision required")
    try:
        P7T2.validate_training_config(training_config)
        P7T2.validate_decision_manifest(decision_manifest)
    except P7T2.TrainingPipelineError as error:
        raise ResearchDecisionError(error.diagnostics) from error

    try:
        resolved_p6_decision = P7T2.resolve_decision(decision_manifest, ASSISTANT_KEY)
    except P7T2.TrainingPipelineError as error:
        raise ResearchDecisionError(error.diagnostics) from error
    if resolved_p6_decision != "ADAPTER_REQUIRED":
        record.update(
            {
                "reason": "P6_T6_ADAPTER_DECISION_DOES_NOT_AUTHORIZE_RESEARCH_TRAINING",
                "candidateBuild": _candidate_block("TRAINING_DECISION_MISMATCH"),
            }
        )
        return _finalize_decision(record)

    dataset_identity = training_config["dataset"]["identity"]
    if dataset_identity in PLACEHOLDER_IDENTITIES:
        record.update(
            {
                "reason": "P7_T2_DATASET_IDENTITY_IS_A_FAIL_CLOSED_PLACEHOLDER",
                "candidateBuild": _candidate_block("PLACEHOLDER_DATASET_IDENTITY"),
            }
        )
        return _finalize_decision(record)
    if dataset_manifest_path is None or not dataset_manifest_path.is_file():
        record.update(
            {
                "reason": "APPROVED_P7_T1_RESEARCH_DATASET_MANIFEST_IS_MISSING",
                "candidateBuild": _candidate_block("APPROVED_RESEARCH_DATASET_MISSING"),
            }
        )
        return _finalize_decision(record)

    try:
        manifest = P7T2.validate_dataset_manifest(
            dataset_manifest_path,
            dataset_identity,
            ASSISTANT_KEY,
            {training_config["splits"]["training"], training_config["splits"]["evaluation"]},
        )
    except P7T2.TrainingPipelineError as error:
        record.update(
            {
                "reason": "P7_T1_DATASET_IDENTITY_OR_ARTIFACT_CHECKSUM_MISMATCH",
                "candidateBuild": _candidate_block("DATASET_IDENTITY_MISMATCH"),
            }
        )
        return _finalize_decision(record)
    try:
        validate_research_dataset_approval(manifest)
    except ResearchDecisionError:
        record.update(
            {
                "reason": "P7_T1_RESEARCH_DATASET_APPROVAL_OR_PROVENANCE_IS_INVALID",
                "candidateBuild": _candidate_block("DATASET_APPROVAL_INVALID"),
            }
        )
        return _finalize_decision(record)

    if training_invoker is None or training_output is None:
        record.update(
            {
                "reason": "P7_T2_REAL_TRAINING_BACKEND_IS_UNAVAILABLE",
                "candidateBuild": _candidate_block("TRAINING_RUNTIME_UNAVAILABLE"),
            }
        )
        return _finalize_decision(record)

    record["training"] = {"status": "INVOKED", "invoked": True}
    try:
        metadata = training_invoker(
            training_config,
            decision_manifest,
            dataset_manifest_path,
            training_output,
            smoke=False,
            source_commit=source_commit,
        )
        validate_real_candidate_metadata(metadata)
        if (
            metadata["datasetIdentity"] != dataset_identity
            or metadata["trainingConfigIdentity"] != P7T2.training_config_identity(training_config)
            or metadata["trainingRunIdentity"] != P7T2.training_run_identity(training_config)
            or metadata["seed"] != training_config["seed"]
            or metadata["adapterMethod"] != training_config["adapter"]["method"]
            or metadata["sourceCommit"] != source_commit
        ):
            raise ResearchDecisionError("candidate metadata: P7-T2 training provenance mismatch")
    except (P7T2.TrainingPipelineError, ResearchDecisionError, OSError) as error:
        smoke_artifact = isinstance(error, ResearchDecisionError) and any(
            diagnostic == "smoke artifacts are not real candidate evidence" for diagnostic in error.diagnostics
        )
        reason = "SMOKE_ARTIFACT_NOT_CANDIDATE" if smoke_artifact else "TRAINING_RUNTIME_UNAVAILABLE"
        record.update(
            {
                "reason": f"REAL_RESEARCH_CANDIDATE_BUILD_FAILED: {error}",
                "candidateBuild": _candidate_block(reason),
                "training": {"status": "BLOCKED", "invoked": True},
            }
        )
        return _finalize_decision(record)

    record.update(
        {
            "outcome": "ADAPTER_REQUIRED+CANDIDATE_AVAILABLE",
            "reason": "MATERIAL_BASELINE_FAILURE_AND_VALIDATED_REAL_P7_T2_CANDIDATE",
            "candidateBuild": {
                "status": "CANDIDATE_AVAILABLE",
                "reason": "REAL_P7_T2_TRAINING_COMPLETED",
                "metadata": copy.deepcopy(metadata),
                "artifactIdentity": _candidate_identity(metadata),
            },
            "training": {"status": "REAL_CANDIDATE_BUILT", "invoked": True},
        }
    )
    return _finalize_decision(record)


def _load_document(path: Path, label: str) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
        value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    except (OSError, UnicodeError, json.JSONDecodeError, yaml.YAMLError) as error:
        raise ResearchDecisionError(f"{label} {path}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise ResearchDecisionError(f"{label} {path}: object required")
    return value


def _source_commit(explicit: str | None) -> str:
    if explicit is not None:
        if not _nonempty_string(explicit):
            raise ResearchDecisionError("source commit: non-empty value required")
        return explicit
    try:
        result = subprocess.run(
            ["git", "-C", str(ROOT), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
            timeout=10,
        )
        commit = result.stdout.strip()
        return commit if commit else "UNAVAILABLE"
    except (OSError, subprocess.SubprocessError):
        return "UNAVAILABLE"


def _write_json_atomically(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    rendered = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n"
    temporary_name: str | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="\n",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as temporary:
            temporary.write(rendered)
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_name = temporary.name
        os.replace(temporary_name, path)
    except (OSError, TypeError, ValueError) as error:
        if temporary_name is not None:
            try:
                Path(temporary_name).unlink(missing_ok=True)
            except OSError:
                pass
        raise ResearchDecisionError(f"output {path}: cannot write atomically: {error}") from error


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--suite",
        type=Path,
        default=ROOT / "evals" / "p6-t4-evaluation-suites.yaml",
        help="frozen P6-T4 evaluation suite",
    )
    parser.add_argument(
        "--suite-lock",
        type=Path,
        default=ROOT / "evals" / "p6-t4-evaluation-suite.lock.json",
        help="P6-T4 suite content lock",
    )
    parser.add_argument(
        "--benchmark-config",
        type=Path,
        default=ROOT / "config" / "p6-t5-benchmark.yaml",
        help="P6-T5 benchmark configuration",
    )
    parser.add_argument(
        "--decisions",
        type=Path,
        default=ROOT / "config" / "p6-t6-adapter-decisions.json",
        help="approved P6-T6 adapter decision manifest",
    )
    parser.add_argument(
        "--training-config",
        type=Path,
        default=ROOT / "config" / "p7-t2-training-pipeline.json",
        help="deterministic P7-T2 Research training configuration",
    )
    parser.add_argument("--baseline-evidence", type=Path, help="retained per-case Research baseline evidence")
    parser.add_argument("--dataset-manifest", type=Path, help="approved P7-T1 Research manifest")
    parser.add_argument("--training-output", type=Path, help="new P7-T2 real-training artifact directory")
    parser.add_argument(
        "--execute-real-training",
        action="store_true",
        help="invoke the P7-T2 real backend only after a material baseline failure and prerequisite validation",
    )
    parser.add_argument("--source-commit", help="source commit; defaults to the current Git HEAD")
    parser.add_argument("--output", required=True, type=Path, help="durable P7-T3 decision JSON")
    args = parser.parse_args()

    freeze_binding = ROOT / "evals" / "p6-t4-evaluation-freeze.binding.yaml"
    p6_rationale = ROOT / "docs" / "architecture" / "p6-t6-adapter-strategy-decision.md"
    try:
        suite = _load_document(args.suite, "evaluation suite")
        suite_lock = _load_document(args.suite_lock, "evaluation suite lock")
        benchmark_config = _load_document(args.benchmark_config, "benchmark config")
        decision_manifest = _load_document(args.decisions, "adapter decision manifest")
        training_config = _load_document(args.training_config, "training config")
        evidence = (
            _load_document(args.baseline_evidence, "baseline evidence") if args.baseline_evidence is not None else None
        )
        validate_suite_lock(suite, args.suite, suite_lock, benchmark_config)
        evidence_artifacts = [
            ("evaluation-suite", args.suite),
            ("evaluation-suite-lock", args.suite_lock),
            ("evaluation-governance-binding", freeze_binding),
            ("benchmark-configuration", args.benchmark_config),
            ("adapter-strategy-decision", args.decisions),
            ("adapter-strategy-rationale", p6_rationale),
            ("training-configuration", args.training_config),
        ]
        if args.baseline_evidence is not None:
            evidence_artifacts.append(("research-baseline-evidence", args.baseline_evidence))
        inventory = frozen_evidence_inventory(evidence_artifacts)
        decision = decide_research_model(
            suite,
            benchmark_config,
            decision_manifest,
            training_config,
            evidence,
            frozen_evidence=inventory,
            dataset_manifest_path=args.dataset_manifest,
            training_output=args.training_output,
            training_invoker=P7T2.run_pipeline if args.execute_real_training else None,
            source_commit=_source_commit(args.source_commit),
        )
        validate_research_decision_record(decision)
        _write_json_atomically(args.output, decision)
        print(json.dumps(decision, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except ResearchDecisionError as error:
        print(
            json.dumps(
                {"status": "ERROR", "diagnostics": error.diagnostics},
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
