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
import zipfile
from typing import Any, Callable

import yaml


ROOT = Path(__file__).resolve().parents[1]
P7T2_SPEC = importlib.util.spec_from_file_location("p7t2_for_p7t3", ROOT / "scripts" / "training-pipeline-p7-t2.py")
P7T2 = importlib.util.module_from_spec(P7T2_SPEC)
assert P7T2_SPEC.loader is not None
P7T2_SPEC.loader.exec_module(P7T2)
GOVERNANCE_SPEC = importlib.util.spec_from_file_location(
    "p7t3_report_governance_for_decision",
    ROOT / "scripts" / "research-report-eval-governance-p7-t3.py",
)
GOVERNANCE = importlib.util.module_from_spec(GOVERNANCE_SPEC)
assert GOVERNANCE_SPEC.loader is not None
GOVERNANCE_SPEC.loader.exec_module(GOVERNANCE)

SCHEMA_VERSION = "1.0.0"
PIPELINE_VERSION = "1.0.0"
DECISION_RULE_VERSION = "P7-T3-RESEARCH-GATES-2.0.0"
GAP_DECISION_RULE_VERSION = "P7-T3-RESEARCH-GATES-3.0.0"
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
FROZEN_H01_ARTIFACT_TYPE = "P6-T5-H01-USER-APPROVED-FROZEN-CONTRACT"
BASELINE_BINDING_ARTIFACT_TYPE = "P7-T3-RESEARCH-BASELINE-EVIDENCE-BINDING"
GAP_SUITE_ARTIFACT_TYPE = "P7-T3-RESEARCH-GAP-EVALUATION-SUITE"
GAP_EVIDENCE_ARTIFACT_TYPE = "P7-T3-RESEARCH-GAP-EVIDENCE"
MERGED_EVIDENCE_ARTIFACT_TYPE = "P7-T3-RESEARCH-MERGED-BASELINE-EVIDENCE"
REQUIRED_RESEARCH_REFUSAL_SCENARIOS = {
    "RESEARCH_GROUP_OUTSIDE_DENY",
    "RESEARCH_TASK_UNAUTHORIZED_DENY",
}
MAX_EVIDENCE_INPUT_BYTES = 16 * 1024 * 1024


class ResearchDecisionError(ValueError):
    """Fail-closed P7-T3 diagnostic."""

    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


class DuplicateJsonKeyError(ValueError):
    """Raised when JSON input contains an ambiguous duplicate object key."""


def _reject_duplicate_json_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, child in pairs:
        if key in value:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        value[key] = child
    return value


def _load_json_text(text: str) -> dict[str, Any]:
    value = json.loads(text, object_pairs_hook=_reject_duplicate_json_keys)
    if not isinstance(value, dict):
        raise ResearchDecisionError("JSON document: object required")
    return value


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
        "artifactType",
        "schemaVersion",
        "decisionRuleVersion",
        "assistantKey",
        "baseModel",
        "promptProfile",
        "evaluationSuite",
        "evidenceReference",
        "sourceEvidence",
        "candidate",
        "sourceCommit",
        "caseResults",
    }
    if not _require_exact_fields(evidence, fields, "evidence", diagnostics):
        raise ResearchDecisionError(diagnostics)
    assert isinstance(evidence, dict)
    if evidence.get("artifactType") != BASELINE_BINDING_ARTIFACT_TYPE:
        diagnostics.append(f"evidence/artifactType: {BASELINE_BINDING_ARTIFACT_TYPE} required")
    if evidence.get("schemaVersion") != SCHEMA_VERSION:
        diagnostics.append("evidence/schemaVersion: unsupported version")
    if evidence.get("decisionRuleVersion") != DECISION_RULE_VERSION:
        diagnostics.append("evidence/decisionRuleVersion: unsupported Research gate rule")
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
    if not _nonempty_string(evidence.get("sourceCommit")):
        diagnostics.append("evidence/sourceCommit: non-empty source commit required")

    source = evidence.get("sourceEvidence")
    source_fields = {
        "artifactType",
        "reference",
        "sha256",
        "sizeBytes",
        "lineage",
        "executionAttempt",
        "reviewCheckpoint",
        "approvalDecision",
        "proposalRevision",
        "reviewInputReference",
        "reviewInputSha256",
        "manifestReference",
        "manifestSha256",
    }
    if _require_exact_fields(source, source_fields, "evidence/sourceEvidence", diagnostics):
        assert isinstance(source, dict)
        expected_source = {
            "artifactType": FROZEN_H01_ARTIFACT_TYPE,
            "lineage": "P6-T5-V5-R8-R3",
            "executionAttempt": "A2",
            "reviewCheckpoint": "H01",
            "approvalDecision": "APPROVED",
            "proposalRevision": "R3",
        }
        for field, expected in expected_source.items():
            if source.get(field) != expected:
                diagnostics.append(f"evidence/sourceEvidence/{field}: {expected} required")
        _validate_reference(source.get("reference"), "evidence/sourceEvidence/reference", diagnostics)
        for reference_field in ("reviewInputReference", "manifestReference"):
            _validate_reference(
                source.get(reference_field),
                f"evidence/sourceEvidence/{reference_field}",
                diagnostics,
            )
        for digest_field in ("sha256", "reviewInputSha256", "manifestSha256"):
            digest = source.get(digest_field)
            if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest):
                diagnostics.append(f"evidence/sourceEvidence/{digest_field}: lowercase SHA-256 required")
        size = source.get("sizeBytes")
        if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
            diagnostics.append("evidence/sourceEvidence/sizeBytes: positive integer required")

    candidate = evidence.get("candidate")
    if _require_exact_fields(candidate, {"id", "runId", "outputDigest"}, "evidence/candidate", diagnostics):
        assert isinstance(candidate, dict)
        if candidate.get("id") != "qwen3_4b" or candidate.get("runId") != "qwen3_4b-R01":
            diagnostics.append("evidence/candidate: exact qwen3_4b-R01 candidate required")
        output_digest = candidate.get("outputDigest")
        if not isinstance(output_digest, str) or not SHA256_PATTERN.fullmatch(output_digest):
            diagnostics.append("evidence/candidate/outputDigest: lowercase SHA-256 required")

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
            result_fields = {"evalCaseId", "result", "evidenceSha256", "sourceRecordReference"}
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
            _validate_reference(
                result.get("sourceRecordReference"),
                f"evidence/caseResults/{index}/sourceRecordReference",
                diagnostics,
            )
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def validate_gap_evidence(
    evidence: object,
    gap_suite: dict[str, Any],
    baseline_evidence: dict[str, Any],
    base_suite: dict[str, Any],
    *,
    governance_request: dict[str, Any] | None = None,
    governance_approval: dict[str, Any] | None = None,
) -> None:
    """Accept only actual, user-approved, case-level Research gap outcomes."""
    validate_gap_suite(gap_suite, base_suite)
    validate_baseline_evidence(baseline_evidence, base_suite)
    diagnostics: list[str] = []
    fields = {
        "artifactType",
        "schemaVersion",
        "decisionRuleVersion",
        "assistantKey",
        "baseModel",
        "promptProfile",
        "suiteLineage",
        "candidate",
        "approval",
        "evidenceReference",
        "sourceCommit",
        "executionCaseIds",
        "governanceApproval",
        "caseResults",
        "artifactIdentity",
    }
    if not _require_exact_fields(evidence, fields, "gap evidence", diagnostics):
        raise ResearchDecisionError(diagnostics)
    assert isinstance(evidence, dict)
    if evidence.get("artifactType") != GAP_EVIDENCE_ARTIFACT_TYPE:
        diagnostics.append(f"gap evidence/artifactType: {GAP_EVIDENCE_ARTIFACT_TYPE} required")
    if evidence.get("schemaVersion") != SCHEMA_VERSION or evidence.get(
        "decisionRuleVersion"
    ) != GAP_DECISION_RULE_VERSION:
        diagnostics.append("gap evidence: supported schema and decision-rule versions required")
    if evidence.get("assistantKey") != ASSISTANT_KEY:
        diagnostics.append("gap evidence/assistantKey: RESEARCH_ASSISTANT required")
    if evidence.get("baseModel") != BASE_MODEL or evidence.get("promptProfile") != PROMPT_PROFILE:
        diagnostics.append("gap evidence: exact Research shared-base model/profile required")
    _validate_reference(evidence.get("evidenceReference"), "gap evidence/evidenceReference", diagnostics)
    if not _nonempty_string(evidence.get("sourceCommit")):
        diagnostics.append("gap evidence/sourceCommit: non-empty value required")

    expected_lineage = {
        "base": copy.deepcopy(gap_suite["baseSuite"]),
        "gap": {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        },
    }
    if evidence.get("suiteLineage") != expected_lineage:
        diagnostics.append("gap evidence/suiteLineage: exact compatible suite lineage required")

    candidate = evidence.get("candidate")
    if not _require_exact_fields(
        candidate,
        {"id", "sourceRunId", "gapRunId", "outputDigest"},
        "gap evidence/candidate",
        diagnostics,
    ):
        candidate = {}
    assert isinstance(candidate, dict)
    baseline_candidate = baseline_evidence["candidate"]
    if candidate.get("id") != baseline_candidate.get("id") or candidate.get("sourceRunId") != baseline_candidate.get(
        "runId"
    ):
        diagnostics.append("gap evidence/candidate: exact historical qwen3_4b-R01 candidate required")
    if not _nonempty_string(candidate.get("gapRunId")):
        diagnostics.append("gap evidence/candidate/gapRunId: non-empty targeted run ID required")
    _validate_sha256(candidate.get("outputDigest"), "gap evidence/candidate/outputDigest", diagnostics)

    approval = evidence.get("approval")
    if not _require_exact_fields(
        approval,
        {"status", "reference", "sha256"},
        "gap evidence/approval",
        diagnostics,
    ):
        approval = {}
    assert isinstance(approval, dict)
    if approval.get("status") != "USER_APPROVED":
        diagnostics.append("gap evidence/approval/status: USER_APPROVED required")
    _validate_reference(approval.get("reference"), "gap evidence/approval/reference", diagnostics)
    _validate_sha256(approval.get("sha256"), "gap evidence/approval/sha256", diagnostics)

    cases = {
        case["evalCaseId"]: case
        for case in [*gap_suite["caseInventory"], *gap_suite["proposedCaseInventory"]]
    }
    execution_case_ids = evidence.get("executionCaseIds")
    if (
        not isinstance(execution_case_ids, list)
        or not execution_case_ids
        or execution_case_ids != sorted(execution_case_ids)
        or len(execution_case_ids) != len(set(execution_case_ids))
        or not set(execution_case_ids).issubset(cases)
    ):
        diagnostics.append("gap evidence/executionCaseIds: exact targeted case selection required")
        execution_case_ids = []
    report_selected = "E-FUNC-RESEARCH-006" in execution_case_ids
    if report_selected:
        if governance_request is None or governance_approval is None:
            diagnostics.append("gap evidence/governanceApproval: approved governance artifact required")
        else:
            try:
                GOVERNANCE.validate_request(
                    governance_request,
                    GOVERNANCE.load_document(GOVERNANCE.GOVERNANCE_PATH),
                    GOVERNANCE.load_document(GOVERNANCE.FIXTURE_PATH),
                    GOVERNANCE.load_document(GOVERNANCE.SCHEMA_PATH),
                    gap_suite,
                )
                GOVERNANCE.validate_execution_authorization(
                    governance_request,
                    governance_approval,
                    purpose="EVALUATION",
                )
            except GOVERNANCE.GovernanceError as error:
                diagnostics.extend(error.diagnostics)
        expected_governance = (
            {
                "requestIdentity": governance_approval.get("requestIdentity"),
                "approvalIdentity": governance_approval.get("artifactIdentity"),
            }
            if isinstance(governance_approval, dict)
            else None
        )
        if evidence.get("governanceApproval") != expected_governance:
            diagnostics.append("gap evidence/governanceApproval: exact approval identity binding required")
    elif evidence.get("governanceApproval") is not None:
        diagnostics.append("gap evidence/governanceApproval: safe-refusal-only evidence must not claim report approval")
    case_results = evidence.get("caseResults")
    seen: set[str] = set()
    result_fields = {
        "evalCaseId",
        "result",
        "caseDigest",
        "evidenceSha256",
        "sourceRecordReference",
        "humanReviewStatus",
    }
    if not isinstance(case_results, list):
        diagnostics.append("gap evidence/caseResults: list required")
        case_results = []
    for index, result in enumerate(case_results):
        path = f"gap evidence/caseResults/{index}"
        if not _require_exact_fields(result, result_fields, path, diagnostics):
            continue
        assert isinstance(result, dict)
        case_id = result.get("evalCaseId")
        if case_id in seen:
            diagnostics.append(f"{path}: duplicate evalCaseId")
        elif isinstance(case_id, str):
            seen.add(case_id)
        case = cases.get(case_id)
        if case is None:
            diagnostics.append(f"{path}: gap-suite evalCaseId required")
        elif result.get("caseDigest") != sha256_bytes(canonical_bytes(case)):
            diagnostics.append(f"{path}/caseDigest: exact gap-suite case digest required")
        if result.get("result") not in {"PASS", "FAIL"}:
            diagnostics.append(f"{path}/result: actual PASS or FAIL required")
        if result.get("humanReviewStatus") != "USER_APPROVED":
            diagnostics.append(f"{path}/humanReviewStatus: USER_APPROVED required")
        _validate_sha256(result.get("evidenceSha256"), f"{path}/evidenceSha256", diagnostics)
        _validate_reference(result.get("sourceRecordReference"), f"{path}/sourceRecordReference", diagnostics)
    if seen != set(execution_case_ids):
        diagnostics.append("gap evidence/caseResults: complete targeted case inventory required")
    if evidence.get("artifactIdentity") != gap_evidence_identity(evidence):
        diagnostics.append("gap evidence/artifactIdentity: canonical artifact identity mismatch")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def _combined_research_suite(
    base_suite: dict[str, Any],
    gap_suite: dict[str, Any] | None,
    *,
    include_proposed: bool = False,
) -> dict[str, Any]:
    if gap_suite is None:
        return base_suite
    validate_gap_suite(gap_suite, base_suite)
    combined = copy.deepcopy(base_suite)
    combined["caseInventory"] = [
        *copy.deepcopy(base_suite["caseInventory"]),
        *copy.deepcopy(gap_suite["caseInventory"]),
        *(copy.deepcopy(gap_suite["proposedCaseInventory"]) if include_proposed else []),
    ]
    if include_proposed:
        for case in combined["caseInventory"]:
            if case.get("caseState") == "GOVERNANCE_PENDING":
                case["caseState"] = "ACTIVE"
    return combined


def merge_research_evidence(
    baseline_evidence: dict[str, Any],
    gap_evidence: dict[str, Any],
    base_suite: dict[str, Any],
    gap_suite: dict[str, Any],
    *,
    evidence_reference: str,
    source_commit: str,
    governance_request: dict[str, Any] | None = None,
    governance_approval: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Merge immutable H01 binding plus approved gap evidence without inference."""
    validate_baseline_evidence(baseline_evidence, base_suite)
    validate_gap_evidence(
        gap_evidence,
        gap_suite,
        baseline_evidence,
        base_suite,
        governance_request=governance_request,
        governance_approval=governance_approval,
    )
    diagnostics: list[str] = []
    _validate_reference(evidence_reference, "merged evidence/reference", diagnostics)
    if not _nonempty_string(source_commit):
        diagnostics.append("merged evidence/sourceCommit: non-empty value required")

    merged_by_case: dict[str, dict[str, Any]] = {
        result["evalCaseId"]: {
            **copy.deepcopy(result),
            "sourceArtifactType": BASELINE_BINDING_ARTIFACT_TYPE,
            "humanReviewStatus": "USER_APPROVED",
        }
        for result in baseline_evidence["caseResults"]
    }
    for result in gap_evidence["caseResults"]:
        normalized = {
            "evalCaseId": result["evalCaseId"],
            "result": result["result"],
            "evidenceSha256": result["evidenceSha256"],
            "sourceRecordReference": result["sourceRecordReference"],
            "sourceArtifactType": GAP_EVIDENCE_ARTIFACT_TYPE,
            "humanReviewStatus": result["humanReviewStatus"],
        }
        existing = merged_by_case.get(result["evalCaseId"])
        if existing is not None:
            if existing["result"] != normalized["result"]:
                diagnostics.append(f"merged evidence/{result['evalCaseId']}: conflicting duplicate result")
                continue
            if existing["evidenceSha256"] != normalized["evidenceSha256"]:
                diagnostics.append(f"merged evidence/{result['evalCaseId']}: same result has inconsistent digest")
                continue
        merged_by_case[result["evalCaseId"]] = normalized
    if diagnostics:
        raise ResearchDecisionError(diagnostics)

    merged = {
        "artifactType": MERGED_EVIDENCE_ARTIFACT_TYPE,
        "schemaVersion": SCHEMA_VERSION,
        "decisionRuleVersion": GAP_DECISION_RULE_VERSION,
        "assistantKey": ASSISTANT_KEY,
        "baseModel": copy.deepcopy(BASE_MODEL),
        "promptProfile": copy.deepcopy(PROMPT_PROFILE),
        "evaluationSuite": copy.deepcopy(baseline_evidence["evaluationSuite"]),
        "gapSuite": {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
            "base": copy.deepcopy(gap_suite["baseSuite"]),
        },
        "evidenceReference": evidence_reference,
        "sourceArtifacts": [
            {
                "artifactType": BASELINE_BINDING_ARTIFACT_TYPE,
                "reference": baseline_evidence["evidenceReference"],
                "sha256": sha256_bytes(canonical_bytes(baseline_evidence)),
                "approvalStatus": "USER_APPROVED",
            },
            {
                "artifactType": GAP_EVIDENCE_ARTIFACT_TYPE,
                "reference": gap_evidence["evidenceReference"],
                "sha256": gap_evidence["artifactIdentity"],
                "approvalStatus": gap_evidence["approval"]["status"],
            },
        ],
        "candidate": copy.deepcopy(baseline_evidence["candidate"]),
        "governanceApproval": copy.deepcopy(gap_evidence["governanceApproval"]),
        "sourceCommit": source_commit,
        "caseResults": [merged_by_case[case_id] for case_id in sorted(merged_by_case)],
        "mergeIdentity": "",
    }
    merged["mergeIdentity"] = _merged_evidence_identity(merged)
    validate_merged_evidence(merged, base_suite, gap_suite)
    return merged


def validate_merged_evidence(
    evidence: object, base_suite: dict[str, Any], gap_suite: dict[str, Any]
) -> None:
    validate_gap_suite(gap_suite, base_suite)
    diagnostics: list[str] = []
    fields = {
        "artifactType",
        "schemaVersion",
        "decisionRuleVersion",
        "assistantKey",
        "baseModel",
        "promptProfile",
        "evaluationSuite",
        "gapSuite",
        "evidenceReference",
        "sourceArtifacts",
        "candidate",
        "governanceApproval",
        "sourceCommit",
        "caseResults",
        "mergeIdentity",
    }
    if not _require_exact_fields(evidence, fields, "merged evidence", diagnostics):
        raise ResearchDecisionError(diagnostics)
    assert isinstance(evidence, dict)
    if (
        evidence.get("artifactType") != MERGED_EVIDENCE_ARTIFACT_TYPE
        or evidence.get("schemaVersion") != SCHEMA_VERSION
        or evidence.get("decisionRuleVersion") != GAP_DECISION_RULE_VERSION
        or evidence.get("assistantKey") != ASSISTANT_KEY
        or evidence.get("baseModel") != BASE_MODEL
        or evidence.get("promptProfile") != PROMPT_PROFILE
    ):
        diagnostics.append("merged evidence: exact Research identity and rule version required")
    expected_gap = {
        "id": gap_suite["suiteId"],
        "version": gap_suite["suiteVersion"],
        "digest": gap_suite["suiteDigest"],
        "base": copy.deepcopy(gap_suite["baseSuite"]),
    }
    if evidence.get("gapSuite") != expected_gap:
        diagnostics.append("merged evidence/gapSuite: exact compatible gap suite required")
    evaluation_suite = evidence.get("evaluationSuite")
    if (
        not isinstance(evaluation_suite, dict)
        or evaluation_suite.get("id") != base_suite.get("suiteId")
        or evaluation_suite.get("version") != base_suite.get("suiteVersion")
        or evaluation_suite.get("digest") != gap_suite["baseSuite"]["digest"]
    ):
        diagnostics.append("merged evidence/evaluationSuite: exact frozen base suite required")
    candidate = evidence.get("candidate")
    if (
        not isinstance(candidate, dict)
        or set(candidate) != {"id", "runId", "outputDigest"}
        or candidate.get("id") != "qwen3_4b"
        or candidate.get("runId") != "qwen3_4b-R01"
    ):
        diagnostics.append("merged evidence/candidate: exact qwen3_4b-R01 candidate required")
    elif not isinstance(candidate.get("outputDigest"), str) or not SHA256_PATTERN.fullmatch(
        candidate["outputDigest"]
    ):
        diagnostics.append("merged evidence/candidate/outputDigest: lowercase SHA-256 required")
    _validate_reference(evidence.get("evidenceReference"), "merged evidence/evidenceReference", diagnostics)
    if not _nonempty_string(evidence.get("sourceCommit")):
        diagnostics.append("merged evidence/sourceCommit: non-empty value required")
    sources = evidence.get("sourceArtifacts")
    if not isinstance(sources, list) or len(sources) != 2:
        diagnostics.append("merged evidence/sourceArtifacts: historical and gap sources required")
    else:
        source_types: set[str] = set()
        for index, source in enumerate(sources):
            if not _require_exact_fields(
                source,
                {"artifactType", "reference", "sha256", "approvalStatus"},
                f"merged evidence/sourceArtifacts/{index}",
                diagnostics,
            ):
                continue
            assert isinstance(source, dict)
            source_types.add(str(source.get("artifactType")))
            if source.get("approvalStatus") != "USER_APPROVED":
                diagnostics.append(f"merged evidence/sourceArtifacts/{index}: USER_APPROVED required")
            _validate_reference(source.get("reference"), f"merged evidence/sourceArtifacts/{index}/reference", diagnostics)
            _validate_sha256(source.get("sha256"), f"merged evidence/sourceArtifacts/{index}/sha256", diagnostics)
        if source_types != {BASELINE_BINDING_ARTIFACT_TYPE, GAP_EVIDENCE_ARTIFACT_TYPE}:
            diagnostics.append("merged evidence/sourceArtifacts: exact historical and gap source types required")

    combined_cases = {
        case["evalCaseId"]: case
        for case in _combined_research_suite(base_suite, gap_suite, include_proposed=True)["caseInventory"]
    }
    case_results = evidence.get("caseResults")
    seen: set[str] = set()
    if not isinstance(case_results, list):
        diagnostics.append("merged evidence/caseResults: list required")
        case_results = []
    for index, result in enumerate(case_results):
        path = f"merged evidence/caseResults/{index}"
        if not _require_exact_fields(
            result,
            {
                "evalCaseId",
                "result",
                "evidenceSha256",
                "sourceRecordReference",
                "sourceArtifactType",
                "humanReviewStatus",
            },
            path,
            diagnostics,
        ):
            continue
        assert isinstance(result, dict)
        case_id = result.get("evalCaseId")
        if case_id in seen:
            diagnostics.append(f"{path}: duplicate evalCaseId")
        elif isinstance(case_id, str):
            seen.add(case_id)
        if case_id not in combined_cases or combined_cases[case_id].get("assistantKey") != ASSISTANT_KEY:
            diagnostics.append(f"{path}: Research case from compatible suite lineage required")
        if result.get("result") not in {"PASS", "FAIL"}:
            diagnostics.append(f"{path}/result: approved PASS or FAIL required")
        if result.get("humanReviewStatus") != "USER_APPROVED":
            diagnostics.append(f"{path}/humanReviewStatus: USER_APPROVED required")
        if result.get("sourceArtifactType") not in {
            BASELINE_BINDING_ARTIFACT_TYPE,
            GAP_EVIDENCE_ARTIFACT_TYPE,
        }:
            diagnostics.append(f"{path}/sourceArtifactType: approved source type required")
        _validate_sha256(result.get("evidenceSha256"), f"{path}/evidenceSha256", diagnostics)
        _validate_reference(result.get("sourceRecordReference"), f"{path}/sourceRecordReference", diagnostics)
    report_present = "E-FUNC-RESEARCH-006" in seen
    governance_binding = evidence.get("governanceApproval")
    if report_present:
        if (
            not isinstance(governance_binding, dict)
            or set(governance_binding) != {"requestIdentity", "approvalIdentity"}
            or any(not isinstance(value, str) or not SHA256_PATTERN.fullmatch(value) for value in governance_binding.values())
        ):
            diagnostics.append("merged evidence/governanceApproval: exact report approval identities required")
    elif governance_binding is not None:
        diagnostics.append("merged evidence/governanceApproval: safe-refusal-only merge must not claim report approval")
    if evidence.get("mergeIdentity") != _merged_evidence_identity(evidence):
        diagnostics.append("merged evidence/mergeIdentity: canonical merge identity mismatch")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def validate_research_evidence(
    evidence: dict[str, Any],
    base_suite: dict[str, Any],
    gap_suite: dict[str, Any] | None = None,
) -> None:
    if evidence.get("artifactType") == BASELINE_BINDING_ARTIFACT_TYPE:
        validate_baseline_evidence(evidence, base_suite)
    elif evidence.get("artifactType") == MERGED_EVIDENCE_ARTIFACT_TYPE and gap_suite is not None:
        validate_merged_evidence(evidence, base_suite, gap_suite)
    else:
        raise ResearchDecisionError("baseline evidence: supported historical or merged artifact required")


def gap_suite_identity(suite: dict[str, Any]) -> str:
    projection = {key: copy.deepcopy(value) for key, value in suite.items() if key != "suiteDigest"}
    return sha256_bytes(canonical_bytes(projection))


def gap_evidence_identity(evidence: dict[str, Any]) -> str:
    projection = {
        key: copy.deepcopy(value)
        for key, value in evidence.items()
        if key != "artifactIdentity"
    }
    if isinstance(projection.get("caseResults"), list):
        projection["caseResults"] = sorted(
            projection["caseResults"],
            key=lambda result: result.get("evalCaseId", "") if isinstance(result, dict) else "",
        )
    return sha256_bytes(canonical_bytes(projection))


def _merged_evidence_identity(evidence: dict[str, Any]) -> str:
    projection = {
        key: copy.deepcopy(value)
        for key, value in evidence.items()
        if key not in {"mergeIdentity", "sourceCommit", "evidenceReference"}
    }
    return sha256_bytes(canonical_bytes(projection))


def _validate_sha256(value: object, path: str, diagnostics: list[str]) -> None:
    if not isinstance(value, str) or not SHA256_PATTERN.fullmatch(value):
        diagnostics.append(f"{path}: lowercase SHA-256 required")


def validate_gap_suite(gap_suite: object, base_suite: dict[str, Any]) -> None:
    """Validate the additive Research-only cases without mutating frozen P6-T4."""
    validate_research_suite(base_suite)
    diagnostics: list[str] = []
    fields = {
        "artifactType",
        "schemaVersion",
        "suiteId",
        "suiteVersion",
        "suiteDigest",
        "baseSuite",
        "EVALUATION_ONLY",
        "TRAINING_PROHIBITED",
        "caseInventory",
        "proposedCaseInventory",
        "expectedObservations",
        "matrices",
        "governanceBlockers",
        "executionPolicy",
    }
    if not _require_exact_fields(gap_suite, fields, "gap suite", diagnostics):
        raise ResearchDecisionError(diagnostics)
    assert isinstance(gap_suite, dict)
    if gap_suite.get("artifactType") != GAP_SUITE_ARTIFACT_TYPE:
        diagnostics.append(f"gap suite/artifactType: {GAP_SUITE_ARTIFACT_TYPE} required")
    if gap_suite.get("schemaVersion") != SCHEMA_VERSION:
        diagnostics.append("gap suite/schemaVersion: unsupported version")
    if gap_suite.get("suiteId") != "P7-T3-RESEARCH-GAP-EVALUATION" or gap_suite.get("suiteVersion") != "1.1.0":
        diagnostics.append("gap suite: exact Research gap suite ID/version required")
    if gap_suite.get("EVALUATION_ONLY") is not True or gap_suite.get("TRAINING_PROHIBITED") is not True:
        diagnostics.append("gap suite: evaluation-only and training-prohibited declarations required")
    if gap_suite.get("suiteDigest") != gap_suite_identity(gap_suite):
        diagnostics.append("gap suite/suiteDigest: canonical suite identity mismatch")

    base = gap_suite.get("baseSuite")
    if not isinstance(base, dict) or set(base) != {"id", "version", "digest"}:
        diagnostics.append("gap suite/baseSuite: exact frozen base suite identity required")
    else:
        if base.get("id") != base_suite.get("suiteId") or base.get("version") != base_suite.get("suiteVersion"):
            diagnostics.append("gap suite/baseSuite: incompatible base suite ID/version")
        _validate_sha256(base.get("digest"), "gap suite/baseSuite/digest", diagnostics)

    cases = gap_suite.get("caseInventory")
    proposed_cases = gap_suite.get("proposedCaseInventory")
    observations = gap_suite.get("expectedObservations")
    if not isinstance(cases, list) or not cases:
        diagnostics.append("gap suite/caseInventory: non-empty case list required")
        cases = []
    if not isinstance(proposed_cases, list) or not proposed_cases:
        diagnostics.append("gap suite/proposedCaseInventory: non-empty proposed case list required")
        proposed_cases = []
    if not isinstance(observations, dict):
        diagnostics.append("gap suite/expectedObservations: object required")
        observations = {}
    case_ids = [case.get("evalCaseId") for case in cases if isinstance(case, dict)]
    if len(case_ids) != len(cases) or case_ids != sorted(case_ids) or len(case_ids) != len(set(case_ids)):
        diagnostics.append("gap suite/caseInventory: unique sorted evalCaseId values required")
    proposed_case_ids = [case.get("evalCaseId") for case in proposed_cases if isinstance(case, dict)]
    if (
        len(proposed_case_ids) != len(proposed_cases)
        or proposed_case_ids != sorted(proposed_case_ids)
        or len(proposed_case_ids) != len(set(proposed_case_ids))
        or set(case_ids) & set(proposed_case_ids)
    ):
        diagnostics.append("gap suite/proposedCaseInventory: unique sorted non-active evalCaseId values required")

    safe_scenarios: set[str] = set()
    report_case_ids: list[str] = []
    used_observations: list[str] = []
    required_case_fields = {
        "evalCaseId",
        "mandatoryScenarioId",
        "suiteTags",
        "caseState",
        "assistantKey",
        "useCaseId",
        "input",
        "authorizedContext",
        "p6t3Root",
        "expectedObservationId",
        "allowedTool",
        "rejectedTool",
        "structuredOutputContract",
        "referencedContextIds",
        "humanProfileId",
        "responseContract",
    }
    for index, case in enumerate([*cases, *proposed_cases]):
        proposed = index >= len(cases)
        inventory_index = index - len(cases) if proposed else index
        inventory_name = "proposedCaseInventory" if proposed else "caseInventory"
        path = f"gap suite/{inventory_name}/{inventory_index}"
        if not _require_exact_fields(case, required_case_fields, path, diagnostics):
            continue
        assert isinstance(case, dict)
        if case.get("assistantKey") != ASSISTANT_KEY:
            diagnostics.append(f"{path}/assistantKey: RESEARCH_ASSISTANT required")
        observation_id = case.get("expectedObservationId")
        observation = observations.get(observation_id)
        if not _nonempty_string(observation_id) or not isinstance(observation, dict):
            diagnostics.append(f"{path}: exact expected observation required")
            continue
        used_observations.append(observation_id)
        response = case.get("responseContract")
        if observation.get("responseContract") != response or observation.get("referencedContextIds") != case.get(
            "referencedContextIds"
        ):
            diagnostics.append(f"{path}: case and observation contracts must match")

        scenario = case.get("mandatoryScenarioId")
        if scenario in REQUIRED_RESEARCH_REFUSAL_SCENARIOS:
            safe_scenarios.add(scenario)
            rejected = case.get("rejectedTool")
            if (
                case.get("caseState") != "NULL_CONTEXT_ASSERTION"
                or case.get("useCaseId") is not None
                or case.get("input") is not None
                or case.get("authorizedContext") is not None
                or case.get("p6t3Root") is not None
                or case.get("allowedTool") is not None
                or case.get("structuredOutputContract") is not None
                or case.get("referencedContextIds") != []
                or case.get("humanProfileId") != "REFUSAL"
                or not isinstance(response, dict)
                or response.get("mode") != "SAFE_REFUSAL"
                or set(case.get("suiteTags", [])) != {"AUTHORIZATION", "SAFE_REFUSAL"}
                or not isinstance(rejected, dict)
                or rejected.get("kind") != "REJECTED"
                or rejected.get("group") != "RESEARCH_READ"
                or rejected.get("intent") != scenario
                or rejected.get("reason") != "PROHIBITED"
                or observation.get("behavior") != "SAFE_REFUSAL"
                or observation.get("actionRisk") != "PROHIBITED"
                or observation.get("toolRequest") != rejected
                or observation.get("structuredOutput") is not None
            ):
                diagnostics.append(f"{path}: closed Research safe-refusal semantics required")
        elif case.get("useCaseId") == "RESEARCH_UC_006" and scenario == "RESEARCH_UC_006" and proposed:
            report_case_ids.append(case["evalCaseId"])
            structured = observation.get("structuredOutput")
            if (
                case.get("caseState") != "GOVERNANCE_PENDING"
                or case.get("humanProfileId") != "DRAFT_RESEARCH"
                or case.get("structuredOutputContract") != "RESEARCH_REPORT_REVIEW_DRAFT"
                or case.get("authorizedContext") != {"p6t3FixtureCaseId": "POS-RESEARCH-006"}
                or case.get("p6t3Root") != "research"
                or case.get("referencedContextIds")
                != ["synthetic-group-006", "synthetic-project-006", "synthetic-report-006"]
                or not _nonempty_string(case.get("input"))
                or not isinstance(response, dict)
                or response.get("mode") != "DRAFT_PRESENTATION"
                or observation.get("behavior") != "SUCCESS"
                or observation.get("actionRisk") != "DRAFT_ONLY"
                or observation.get("toolRequest") != {"kind": "NONE"}
                or not isinstance(structured, dict)
                or set(structured)
                != {
                    "kind", "reportRef", "reviewSummary", "issues", "suggestions",
                    "requiresHumanReview", "advisoryOnly",
                }
                or structured.get("kind") != "RESEARCH_REPORT_REVIEW_DRAFT"
                or structured.get("reportRef") != "synthetic-report-006"
                or structured.get("requiresHumanReview") is not True
                or structured.get("advisoryOnly") is not True
                or case.get("allowedTool") is not None
                or case.get("rejectedTool") is not None
            ):
                diagnostics.append(f"{path}: pending report review must preserve bounded DRAFT_ONLY semantics")
        else:
            diagnostics.append(f"{path}: unsupported Research gap scenario")

    if set(used_observations) != set(observations) or len(used_observations) != len(set(used_observations)):
        diagnostics.append("gap suite/expectedObservations: exact one-to-one case binding required")
    matrices = gap_suite.get("matrices")
    expected_profiles = {
        "DRAFT_RESEARCH": sorted(report_case_ids),
        "REFUSAL": sorted(
            case["evalCaseId"] for case in cases if isinstance(case, dict) and case.get("humanProfileId") == "REFUSAL"
        ),
        "NONE": [],
    }
    if not isinstance(matrices, dict) or set(matrices) != {"humanApplicabilityBinding"} or matrices.get(
        "humanApplicabilityBinding"
    ) != expected_profiles:
        diagnostics.append("gap suite/matrices: exact human-review applicability required")

    blockers = gap_suite.get("governanceBlockers")
    if report_case_ids != ["E-FUNC-RESEARCH-006"]:
        diagnostics.append("gap suite/proposedCaseInventory: exact report-review proposal required")
    if not isinstance(blockers, list) or not any(
        isinstance(blocker, dict)
        and blocker.get("evalCaseId") == "E-DEFERRED-RESEARCH-006"
        and blocker.get("useCaseId") == "RESEARCH_UC_006"
        and blocker.get("categoryId") == "CAT_RESEARCH_REPORT_METADATA"
        and blocker.get("status") == "AWAITING_GOVERNANCE_APPROVAL"
        and blocker.get("useDecision") == "DEFERRED"
        and blocker.get("permittedPurposes") == []
        and set(blocker.get("prohibitedPurposes", []))
        == {"TRAINING", "EVALUATION", "BENCHMARK", "HUMAN_EVALUATION", "DEVELOPMENT_TEST"}
        and blocker.get("sanitizationDisposition") == "DEFERRED_NO_EXPORT"
        and blocker.get("governanceRequestReference")
        == "config/p7-t3-research-report-eval-governance-request.json"
        and blocker.get("proposedEvalCaseId") == "E-FUNC-RESEARCH-006"
        for blocker in blockers
    ):
        diagnostics.append("gap suite/governanceBlockers: exact CAT_RESEARCH_REPORT_METADATA blocker required")

    execution = gap_suite.get("executionPolicy")
    execution_fields = {
        "candidateId",
        "sourceRunId",
        "model",
        "caseIds",
        "postApprovalCaseIds",
        "governanceApprovalRequiredCaseIds",
        "governanceRequestReference",
        "executionScope",
        "networkAccess",
        "humanReviewRequired",
        "command",
        "approvedCommand",
        "outputReference",
        "reviewReference",
        "approvedOutputReference",
        "approvedReviewReference",
        "frozenEvidenceReference",
    }
    if not _require_exact_fields(execution, execution_fields, "gap suite/executionPolicy", diagnostics):
        execution = {}
    assert isinstance(execution, dict)
    if (
        execution.get("candidateId") != "qwen3_4b"
        or execution.get("sourceRunId") != "qwen3_4b-R01"
        or execution.get("model") != BASE_MODEL
        or execution.get("caseIds") != sorted(case_ids)
        or execution.get("postApprovalCaseIds") != sorted([*case_ids, *proposed_case_ids])
        or execution.get("governanceApprovalRequiredCaseIds") != sorted(proposed_case_ids)
        or execution.get("governanceRequestReference")
        != "config/p7-t3-research-report-eval-governance-request.json"
        or execution.get("executionScope") != "TARGETED_CASES_ONLY"
        or execution.get("networkAccess") != "PROHIBITED"
        or execution.get("humanReviewRequired") is not True
    ):
        diagnostics.append("gap suite/executionPolicy: exact offline targeted execution policy required")
    for field in (
        "command", "approvedCommand", "outputReference", "reviewReference",
        "approvedOutputReference", "approvedReviewReference", "frozenEvidenceReference",
    ):
        _validate_reference(execution.get(field), f"gap suite/executionPolicy/{field}", diagnostics)
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def validate_gap_suite_lock(
    gap_suite: dict[str, Any],
    gap_suite_path: Path,
    lock: object,
    base_suite: dict[str, Any],
    benchmark_config: dict[str, Any],
) -> None:
    validate_gap_suite(gap_suite, base_suite)
    diagnostics: list[str] = []
    fields = {
        "artifactType",
        "schemaVersion",
        "suiteId",
        "suiteVersion",
        "lockVersion",
        "purpose",
        "localFreezeStatus",
        "EVALUATION_ONLY",
        "TRAINING_PROHIBITED",
        "baseSuite",
        "suiteDigest",
        "fileDigest",
        "canonicalInventoryDigest",
        "files",
    }
    if not _require_exact_fields(lock, fields, "gap suite lock", diagnostics):
        raise ResearchDecisionError(diagnostics)
    assert isinstance(lock, dict)
    expected = {
        "artifactType": "P7-T3-RESEARCH-GAP-EVALUATION-SUITE-LOCK",
        "schemaVersion": SCHEMA_VERSION,
        "suiteId": gap_suite["suiteId"],
        "suiteVersion": gap_suite["suiteVersion"],
        "lockVersion": "1.1.0",
        "purpose": "EVALUATION",
        "localFreezeStatus": "CONTENT_LOCKED",
        "EVALUATION_ONLY": True,
        "TRAINING_PROHIBITED": True,
        "baseSuite": gap_suite["baseSuite"],
        "suiteDigest": gap_suite["suiteDigest"],
        "canonicalInventoryDigest": sha256_bytes(
            canonical_bytes(
                {
                    "caseInventory": gap_suite["caseInventory"],
                    "proposedCaseInventory": gap_suite["proposedCaseInventory"],
                }
            )
        ),
    }
    for field, value in expected.items():
        if lock.get(field) != value:
            diagnostics.append(f"gap suite lock/{field}: exact locked value required")
    file_digest = file_sha256(gap_suite_path)
    reference = _repository_reference(gap_suite_path)
    if lock.get("fileDigest") != file_digest or lock.get("files") != {reference: file_digest}:
        diagnostics.append("gap suite lock/files: exact suite file digest required")
    benchmark_suite = benchmark_config.get("suite") if isinstance(benchmark_config, dict) else None
    if not isinstance(benchmark_suite, dict) or gap_suite.get("baseSuite") != {
        "id": benchmark_suite.get("id"),
        "version": benchmark_suite.get("version"),
        "digest": benchmark_suite.get("digest"),
    }:
        diagnostics.append("gap suite lock/baseSuite: benchmark frozen-suite lineage mismatch")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)


def import_frozen_h01_contract(
    contract: object,
    suite: dict[str, Any],
    benchmark_config: dict[str, Any],
    *,
    source_reference: str,
    source_sha256: str,
    source_size: int,
    evidence_reference: str,
    review_input: object | None,
    review_input_reference: str | None,
    review_input_sha256: str | None,
    evidence_manifest: object | None,
    evidence_manifest_reference: str | None,
    evidence_manifest_sha256: str | None,
    source_commit: str,
) -> dict[str, Any]:
    """Bind the approved H01 qwen3_4b records without copying raw outputs."""
    validate_research_suite(suite)
    _validate_benchmark_context(suite, benchmark_config, None)
    diagnostics: list[str] = []
    if not isinstance(contract, dict):
        raise ResearchDecisionError("frozen H01 evidence: object required")
    required_contract_fields = {
        "artifactType",
        "materializationRevision",
        "lineage",
        "executionAttempt",
        "reviewCheckpoint",
        "approval",
        "sourceReviewInputSha256",
        "candidates",
        "contractAdaptation",
    }
    if set(contract) != required_contract_fields:
        diagnostics.append("frozen H01 evidence: exact user-approved frozen contract shape required")
    expected_contract = {
        "artifactType": FROZEN_H01_ARTIFACT_TYPE,
        "materializationRevision": "R2-FROZEN-CONTRACT",
        "lineage": "P6-T5-V5-R8-R3",
        "executionAttempt": "A2",
        "reviewCheckpoint": "H01",
    }
    for field, expected in expected_contract.items():
        if contract.get(field) != expected:
            label = "user-approved frozen contract" if field == "artifactType" else expected
            diagnostics.append(f"frozen H01 evidence/{field}: {label} required")
    _validate_reference(source_reference, "frozen H01 evidence/reference", diagnostics)
    _validate_reference(evidence_reference, "baseline binding/reference", diagnostics)
    if not isinstance(source_sha256, str) or not SHA256_PATTERN.fullmatch(source_sha256):
        diagnostics.append("frozen H01 evidence/sha256: lowercase SHA-256 required")
    if not isinstance(source_size, int) or isinstance(source_size, bool) or source_size <= 0:
        diagnostics.append("frozen H01 evidence/sizeBytes: positive integer required")
    if not _nonempty_string(source_commit):
        diagnostics.append("baseline binding/sourceCommit: non-empty value required")

    approval = contract.get("approval")
    approval_fields = {"decision", "source", "approvedAt", "proposalRevision", "proposalSha256"}
    if not _require_exact_fields(approval, approval_fields, "frozen H01 evidence/approval", diagnostics):
        approval = {}
    assert isinstance(approval, dict)
    if approval.get("decision") != "APPROVED" or approval.get("proposalRevision") != "R3":
        diagnostics.append("frozen H01 evidence/approval: approved R3 review required")
    if not _nonempty_string(approval.get("source")) or not _nonempty_string(approval.get("approvedAt")):
        diagnostics.append("frozen H01 evidence/approval: approval source and time required")
    proposal_digest = approval.get("proposalSha256")
    if not isinstance(proposal_digest, str) or not SHA256_PATTERN.fullmatch(proposal_digest):
        diagnostics.append("frozen H01 evidence/approval/proposalSha256: lowercase SHA-256 required")
    adaptation = contract.get("contractAdaptation")
    if not isinstance(adaptation, dict) or any(
        adaptation.get(field) is not False
        for field in ("semanticDecisionChanged", "outcomesChanged", "rationalesChanged")
    ):
        diagnostics.append("frozen H01 evidence: semantic outcomes must remain unchanged")

    if evidence_manifest is not None:
        if not isinstance(evidence_manifest, dict):
            diagnostics.append("P6-T5 evidence manifest: object required")
        else:
            approved_h01 = evidence_manifest.get("artifacts", {}).get("approvedH01")
            if not isinstance(approved_h01, dict):
                diagnostics.append("P6-T5 evidence manifest: approvedH01 entry required")
            else:
                manifest_path = approved_h01.get("path")
                if not isinstance(manifest_path, str) or Path(manifest_path).name != Path(source_reference).name:
                    diagnostics.append("P6-T5 evidence manifest: frozen H01 reference mismatch")
                if approved_h01.get("sha256") != source_sha256:
                    diagnostics.append("P6-T5 evidence manifest: frozen H01 SHA-256 mismatch")
                if approved_h01.get("sizeBytes") != source_size:
                    diagnostics.append("P6-T5 evidence manifest: frozen H01 size mismatch")
        if evidence_manifest_reference is None or evidence_manifest_sha256 is None:
            diagnostics.append("P6-T5 evidence manifest: reference and SHA-256 required")

    candidates = contract.get("candidates")
    candidate = candidates.get("qwen3_4b") if isinstance(candidates, dict) else None
    candidate_fields = {"candidateRunId", "candidateOutputDigest", "recordCount", "summary", "records"}
    if not _require_exact_fields(candidate, candidate_fields, "frozen H01 evidence/candidates/qwen3_4b", diagnostics):
        candidate = {}
    assert isinstance(candidate, dict)
    if candidate.get("candidateRunId") != "qwen3_4b-R01":
        diagnostics.append("frozen H01 evidence: exact qwen3_4b candidate run required")
    candidate_output_digest = candidate.get("candidateOutputDigest")
    if not isinstance(candidate_output_digest, str) or not SHA256_PATTERN.fullmatch(candidate_output_digest):
        diagnostics.append("frozen H01 evidence: qwen3_4b candidate output digest required")
    records = candidate.get("records")
    if not isinstance(records, list):
        diagnostics.append("frozen H01 evidence: qwen3_4b case records required; aggregate totals are insufficient")
        records = []
    if candidate.get("recordCount") != len(records):
        diagnostics.append("frozen H01 evidence: qwen3_4b record count mismatch")

    suite_by_case = {
        case["evalCaseId"]: case
        for case in suite.get("caseInventory", [])
        if isinstance(case, dict) and _nonempty_string(case.get("evalCaseId"))
    }
    records_by_case: dict[str, dict[str, Any]] = {}
    outcome_counts = {result: 0 for result in RESULTS}
    record_fields = {
        "evidenceRefs",
        "profileId",
        "reviewerRationale",
        "overall",
        "dimensions",
        "candidateCaseDigest",
        "evalCaseId",
    }
    for index, record in enumerate(records):
        if not _require_exact_fields(record, record_fields, f"frozen H01 evidence/records/{index}", diagnostics):
            continue
        assert isinstance(record, dict)
        case_id = record.get("evalCaseId")
        if case_id in records_by_case:
            diagnostics.append(f"frozen H01 evidence/records/{index}: duplicate evalCaseId")
        elif isinstance(case_id, str):
            records_by_case[case_id] = record
        case = suite_by_case.get(case_id)
        if case is None:
            diagnostics.append(f"frozen H01 evidence/records/{index}: evalCaseId absent from frozen suite")
        elif record.get("profileId") != case.get("humanProfileId"):
            diagnostics.append(f"frozen H01 evidence/records/{index}: human profile mismatch")
        if record.get("evidenceRefs") != [f"evalCaseId:{case_id}"]:
            diagnostics.append(f"frozen H01 evidence/records/{index}: frozen evalCaseId reference required")
        outcome = record.get("overall")
        if outcome not in RESULTS:
            diagnostics.append(f"frozen H01 evidence/records/{index}: invalid frozen overall outcome")
        else:
            outcome_counts[outcome] += 1
        digest = record.get("candidateCaseDigest")
        if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest):
            diagnostics.append(f"frozen H01 evidence/records/{index}: candidate case SHA-256 required")
        if not isinstance(record.get("dimensions"), list) or not _nonempty_string(record.get("reviewerRationale")):
            diagnostics.append(f"frozen H01 evidence/records/{index}: reviewed dimension evidence required")
    if candidate.get("summary") != outcome_counts:
        diagnostics.append("frozen H01 evidence: aggregate summary does not match case records; aggregate totals cannot satisfy gates")

    if review_input is not None:
        if not isinstance(review_input, dict):
            diagnostics.append("H01 review input: object required")
        else:
            expected_review_identity = {
                "lineage": contract.get("lineage"),
                "executionAttempt": contract.get("executionAttempt"),
                "reviewCheckpoint": contract.get("reviewCheckpoint"),
            }
            for field, expected in expected_review_identity.items():
                if review_input.get(field) != expected:
                    diagnostics.append(f"H01 review input/{field}: frozen contract mismatch")
            review_candidate = review_input.get("candidates", {}).get("qwen3_4b")
            if not isinstance(review_candidate, dict):
                diagnostics.append("H01 review input: qwen3_4b candidate required")
                review_records = []
            else:
                if review_candidate.get("candidateRunId") != candidate.get("candidateRunId"):
                    diagnostics.append("H01 review input: candidate run mismatch")
                if review_candidate.get("candidateOutputDigest") != candidate.get("candidateOutputDigest"):
                    diagnostics.append("H01 review input: candidate output digest mismatch")
                review_records = review_candidate.get("records")
                if not isinstance(review_records, list):
                    diagnostics.append("H01 review input: case records required")
                    review_records = []
            review_by_case: dict[str, dict[str, Any]] = {}
            for index, review_record in enumerate(review_records):
                if not isinstance(review_record, dict) or not _nonempty_string(review_record.get("evalCaseId")):
                    diagnostics.append(f"H01 review input/records/{index}: evalCaseId required")
                    continue
                case_id = review_record["evalCaseId"]
                if case_id in review_by_case:
                    diagnostics.append(f"H01 review input/records/{index}: duplicate evalCaseId")
                review_by_case[case_id] = review_record
            for case_id, record in records_by_case.items():
                review_record = review_by_case.get(case_id)
                if not isinstance(review_record, dict) or not isinstance(review_record.get("candidateCase"), dict):
                    diagnostics.append(f"H01 review input/{case_id}: candidate case missing")
                elif sha256_bytes(canonical_bytes(review_record["candidateCase"])) != record.get(
                    "candidateCaseDigest"
                ):
                    diagnostics.append(f"H01 review input/{case_id}: case digest mismatch")
        if review_input_sha256 != contract.get("sourceReviewInputSha256"):
            diagnostics.append("H01 review input: source SHA-256 mismatch")
        if review_input_reference is None:
            diagnostics.append("H01 review input: logical reference required")

    for reference, digest, label in (
        (review_input_reference, review_input_sha256, "H01 review input"),
        (evidence_manifest_reference, evidence_manifest_sha256, "P6-T5 evidence manifest"),
    ):
        _validate_reference(reference, f"{label}/reference", diagnostics)
        if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest):
            diagnostics.append(f"{label}/sha256: lowercase SHA-256 required")
    if diagnostics:
        raise ResearchDecisionError(diagnostics)

    required_case_ids = sorted({case_id for ids in required_gate_cases(suite).values() for case_id in ids})
    binding = {
        "artifactType": BASELINE_BINDING_ARTIFACT_TYPE,
        "schemaVersion": SCHEMA_VERSION,
        "decisionRuleVersion": DECISION_RULE_VERSION,
        "assistantKey": ASSISTANT_KEY,
        "baseModel": copy.deepcopy(BASE_MODEL),
        "promptProfile": copy.deepcopy(PROMPT_PROFILE),
        "evaluationSuite": {
            "id": suite["suiteId"],
            "version": suite["suiteVersion"],
            "digest": benchmark_config["suite"]["digest"],
        },
        "evidenceReference": evidence_reference,
        "sourceEvidence": {
            "artifactType": FROZEN_H01_ARTIFACT_TYPE,
            "reference": source_reference,
            "sha256": source_sha256,
            "sizeBytes": source_size,
            "lineage": contract["lineage"],
            "executionAttempt": contract["executionAttempt"],
            "reviewCheckpoint": contract["reviewCheckpoint"],
            "approvalDecision": approval["decision"],
            "proposalRevision": approval["proposalRevision"],
            "reviewInputReference": review_input_reference,
            "reviewInputSha256": review_input_sha256,
            "manifestReference": evidence_manifest_reference,
            "manifestSha256": evidence_manifest_sha256,
        },
        "candidate": {
            "id": "qwen3_4b",
            "runId": candidate["candidateRunId"],
            "outputDigest": candidate["candidateOutputDigest"],
        },
        "sourceCommit": source_commit,
        "caseResults": [
            {
                "evalCaseId": case_id,
                "result": records_by_case[case_id]["overall"],
                "evidenceSha256": records_by_case[case_id]["candidateCaseDigest"],
                "sourceRecordReference": f"{source_reference}#/candidates/qwen3_4b/records/{case_id}",
            }
            for case_id in required_case_ids
            if case_id in records_by_case
        ],
    }
    validate_baseline_evidence(binding, suite)
    return binding


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


def _deferred_report_case_ids(suite: dict[str, Any]) -> list[str]:
    return sorted(
        case["evalCaseId"]
        for case in suite["caseInventory"]
        if isinstance(case, dict)
        and case.get("useCaseId") == "RESEARCH_UC_006"
        and case.get("caseState") == "DEFERRED_ASSERTION_ONLY"
        and _nonempty_string(case.get("evalCaseId"))
    )


def _incompatible_research_refusal_cases(suite: dict[str, Any]) -> list[dict[str, str]]:
    incompatible: list[dict[str, str]] = []
    for case in suite["caseInventory"]:
        if not isinstance(case, dict) or case.get("assistantKey") == ASSISTANT_KEY:
            continue
        response = case.get("responseContract")
        scenario = case.get("mandatoryScenarioId")
        if (
            isinstance(response, dict)
            and response.get("mode") == "SAFE_REFUSAL"
            and isinstance(scenario, str)
            and scenario.startswith("RESEARCH_")
            and _nonempty_string(case.get("evalCaseId"))
        ):
            incompatible.append(
                {
                    "evalCaseId": case["evalCaseId"],
                    "assistantKey": str(case.get("assistantKey")),
                    "mandatoryScenarioId": scenario,
                }
            )
    return sorted(incompatible, key=lambda item: item["evalCaseId"])


def evaluate_research_baseline(
    suite: dict[str, Any],
    evidence: dict[str, Any] | None,
    *,
    gap_suite: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Use each case's frozen overall outcome; any missing mandatory gate keeps P7-T3 unresolved."""
    validate_research_suite(suite)
    if evidence is not None:
        validate_research_evidence(evidence, suite, gap_suite)
    result_by_case = {
        result["evalCaseId"]: result
        for result in (evidence or {}).get("caseResults", [])
    }
    effective_suite = _combined_research_suite(
        suite,
        gap_suite,
        include_proposed="E-FUNC-RESEARCH-006" in result_by_case,
    )
    gates: list[dict[str, Any]] = []
    for gate, case_ids in required_gate_cases(effective_suite).items():
        missing = [case_id for case_id in case_ids if case_id not in result_by_case]
        failed = [case_id for case_id in case_ids if result_by_case.get(case_id, {}).get("result") == "FAIL"]
        needs_review = [
            case_id for case_id in case_ids if result_by_case.get(case_id, {}).get("result") == "NEEDS_REVIEW"
        ]
        deferred = _deferred_report_case_ids(effective_suite) if gate == "REPORT_REVIEW_DRAFT" else []
        incompatible = _incompatible_research_refusal_cases(effective_suite) if gate == "SAFE_REFUSAL" else []
        research_refusal_scenarios = {
            case.get("mandatoryScenarioId")
            for case in _active_research_cases(effective_suite)
            if case.get("mandatoryScenarioId") in REQUIRED_RESEARCH_REFUSAL_SCENARIOS
        }
        scenario_contract_applies = bool(research_refusal_scenarios or incompatible)
        missing_scenarios = (
            sorted(REQUIRED_RESEARCH_REFUSAL_SCENARIOS - research_refusal_scenarios)
            if gate == "SAFE_REFUSAL" and scenario_contract_applies
            else []
        )
        report_governance_blocked = bool(
            gap_suite
            and any(
                isinstance(blocker, dict)
                and blocker.get("status") == "AWAITING_GOVERNANCE_APPROVAL"
                for blocker in gap_suite.get("governanceBlockers", [])
            )
        )
        if deferred and not case_ids:
            result = "UNRESOLVED"
            reason = (
                "AWAITING_GOVERNANCE_APPROVAL"
                if report_governance_blocked
                else "EVALUATION_SUITE_CASE_NOT_EXECUTABLE"
            )
            missing = deferred
        elif gate == "SAFE_REFUSAL" and missing_scenarios:
            result = "UNRESOLVED"
            reason = "RESEARCH_ONLY_CASE_COVERAGE_MISSING"
        elif not case_ids:
            result = "UNRESOLVED"
            reason = "FROZEN_CASE_COVERAGE_MISSING"
        elif missing:
            result = "UNRESOLVED"
            gap_execution_ids = set(gap_suite.get("executionPolicy", {}).get("caseIds", [])) if gap_suite else set()
            reason = (
                "EVIDENCE_EXECUTION_ENVIRONMENT_REQUIRED"
                if gate == "SAFE_REFUSAL" and set(missing).issubset(gap_execution_ids)
                else "CASE_RESULT_EVIDENCE_MISSING"
            )
        elif needs_review:
            result = "UNRESOLVED"
            reason = "CASE_RESULT_NEEDS_REVIEW"
        elif failed:
            result = "FAIL"
            reason = "MATERIAL_REQUIRED_CASE_FAILURE"
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
                "deferredCaseIds": deferred,
                "incompatibleCaseIds": [item["evalCaseId"] for item in incompatible],
                "missingScenarioIds": missing_scenarios,
                "evidence": [copy.deepcopy(result_by_case[case_id]) for case_id in case_ids if case_id in result_by_case],
            }
        )

    if any(gate["result"] == "UNRESOLVED" for gate in gates):
        overall = "UNRESOLVED"
    elif any(gate["result"] == "FAIL" for gate in gates):
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
        "baselineEvidenceStatus",
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
    except (ResearchDecisionError, GOVERNANCE.GovernanceError) as error:
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
    expected_baseline_status = (
        "BASELINE_EVIDENCE_INCOMPLETE" if overall == "UNRESOLVED" else "BASELINE_EVIDENCE_COMPLETE"
    )
    if record.get("baselineEvidenceStatus") != expected_baseline_status:
        diagnostics.append("decision record: baseline evidence status must match gate completeness")
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
        upstream_conflict = (
            decision == "ADAPTER_REQUIRED"
            and outcome == "ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED"
            and isinstance(candidate, dict)
            and candidate.get("reason") == "UPSTREAM_DECISION_CONFLICT"
        )
        if not upstream_conflict:
            if decision != "BASE_ONLY_APPROVED" or outcome != "BASE_ONLY_APPROVED":
                diagnostics.append("decision record: passing baseline requires BASE_ONLY or explicit upstream conflict")
            if isinstance(training, dict) and (
                training.get("invoked") is not False or training.get("status") != "NOT_REQUIRED"
            ):
                diagnostics.append("decision record: base-only approval must not invoke training")
            if isinstance(candidate, dict) and (
                candidate.get("status") != "NOT_REQUIRED"
                or candidate.get("metadata") is not None
                or candidate.get("artifactIdentity") is not None
            ):
                diagnostics.append("decision record: base-only approval cannot expose adapter artifacts")
        elif isinstance(training, dict) and training != {"status": "BLOCKED", "invoked": False}:
            diagnostics.append("decision record: upstream conflict must block training")
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
        "baselineEvidenceStatus": (
            "BASELINE_EVIDENCE_INCOMPLETE"
            if baseline["overallResult"] == "UNRESOLVED"
            else "BASELINE_EVIDENCE_COMPLETE"
        ),
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
    gap_suite: dict[str, Any] | None = None,
) -> dict[str, Any]:
    validate_research_suite(suite)
    if evidence is not None:
        validate_research_evidence(evidence, suite, gap_suite)
    _validate_benchmark_context(suite, benchmark_config, evidence)
    _validate_frozen_evidence_inventory(frozen_evidence)
    baseline = evaluate_research_baseline(suite, evidence, gap_suite=gap_suite)
    record = _base_record(suite, benchmark_config, evidence, baseline, frozen_evidence, source_commit)
    try:
        P7T2.validate_decision_manifest(decision_manifest)
        resolved_p6_decision = P7T2.resolve_decision(decision_manifest, ASSISTANT_KEY)
    except P7T2.TrainingPipelineError as error:
        raise ResearchDecisionError(error.diagnostics) from error
    if baseline["overallResult"] == "PASS":
        if resolved_p6_decision == "ADAPTER_REQUIRED":
            record.update(
                {
                    "decision": "ADAPTER_REQUIRED",
                    "outcome": "ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED",
                    "reason": "PASSING_BASELINE_CONFLICTS_WITH_APPROVED_P6_T6_ADAPTER_REQUIRED_DECISION",
                    "candidateBuild": _candidate_block("UPSTREAM_DECISION_CONFLICT"),
                    "training": {"status": "BLOCKED", "invoked": False},
                }
            )
            return _finalize_decision(record)
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
    except P7T2.TrainingPipelineError as error:
        raise ResearchDecisionError(error.diagnostics) from error
    if resolved_p6_decision != "ADAPTER_REQUIRED":
        record.update(
            {
                "reason": "FAILING_BASELINE_CONFLICTS_WITH_APPROVED_P6_T6_BASE_ONLY_DECISION",
                "candidateBuild": _candidate_block("UPSTREAM_DECISION_CONFLICT"),
            }
        )
        return _finalize_decision(record)

    dataset_identity = training_config["dataset"]["identity"]
    if dataset_identity in PLACEHOLDER_IDENTITIES:
        record.update(
            {
                "reason": "P7_T2_DATASET_IDENTITY_IS_A_FAIL_CLOSED_PLACEHOLDER",
                "candidateBuild": _candidate_block("RESEARCH_DATASET_NOT_APPROVED"),
            }
        )
        return _finalize_decision(record)
    if dataset_manifest_path is None or not dataset_manifest_path.is_file():
        record.update(
            {
                "reason": "APPROVED_P7_T1_RESEARCH_DATASET_MANIFEST_IS_MISSING",
                "candidateBuild": _candidate_block("RESEARCH_DATASET_NOT_APPROVED"),
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
        if path.stat().st_size > MAX_EVIDENCE_INPUT_BYTES:
            raise ResearchDecisionError(f"{label} {path}: input exceeds deterministic size limit")
        text = path.read_text(encoding="utf-8")
        value = _load_json_text(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    except (OSError, UnicodeError, json.JSONDecodeError, DuplicateJsonKeyError, yaml.YAMLError) as error:
        raise ResearchDecisionError(f"{label} {path}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise ResearchDecisionError(f"{label} {path}: object required")
    return value


def _load_p6_t5_evidence_bundle(bundle_path: Path, contract_path: Path) -> tuple[dict[str, Any], str]:
    manifest_name = "p6-t5-final-evidence-manifest-v1.json"
    contract_name = "p6-t5-v5-r8-r3-h01-user-approved-frozen-contract.json"
    closure_name = "p6-t5-final-closure-v1.json"
    try:
        if bundle_path.stat().st_size > MAX_EVIDENCE_INPUT_BYTES:
            raise ResearchDecisionError("P6-T5 evidence bundle: archive exceeds deterministic size limit")
        with zipfile.ZipFile(bundle_path) as bundle:
            names = bundle.namelist()
            if any(names.count(name) != 1 for name in (manifest_name, contract_name, closure_name)):
                raise ResearchDecisionError(
                    "P6-T5 evidence bundle: unique final manifest, closure, and frozen H01 required"
                )
            if any(
                bundle.getinfo(name).file_size > MAX_EVIDENCE_INPUT_BYTES
                for name in (manifest_name, contract_name, closure_name)
            ):
                raise ResearchDecisionError("P6-T5 evidence bundle: member exceeds deterministic size limit")
            manifest_bytes = bundle.read(manifest_name)
            bundled_contract = bundle.read(contract_name)
            closure_bytes = bundle.read(closure_name)
        manifest = _load_json_text(manifest_bytes.decode("utf-8"))
        closure = _load_json_text(closure_bytes.decode("utf-8"))
        external_contract = contract_path.read_bytes()
    except (OSError, UnicodeError, zipfile.BadZipFile, KeyError, json.JSONDecodeError, DuplicateJsonKeyError) as error:
        raise ResearchDecisionError(f"P6-T5 evidence bundle {bundle_path}: cannot load: {error}") from error
    expected = manifest.get("artifacts", {}).get("approvedH01")
    expected_closure = manifest.get("artifacts", {}).get("closure")
    if not isinstance(expected, dict):
        raise ResearchDecisionError("P6-T5 evidence bundle: approvedH01 manifest entry required")
    if not isinstance(expected_closure, dict):
        raise ResearchDecisionError("P6-T5 evidence bundle: closure manifest entry required")
    bundled_digest = sha256_bytes(bundled_contract)
    external_digest = sha256_bytes(external_contract)
    if (
        expected.get("sha256") != bundled_digest
        or expected.get("sha256") != external_digest
        or expected.get("sizeBytes") != len(external_contract)
        or Path(str(expected.get("path"))).name != contract_name
    ):
        raise ResearchDecisionError("P6-T5 evidence bundle: frozen H01 digest, size, or identity mismatch")
    closure_digest = sha256_bytes(closure_bytes)
    human_evaluation = closure.get("humanEvaluation")
    candidate_decision = closure.get("candidateDecision")
    if (
        expected_closure.get("sha256") != closure_digest
        or expected_closure.get("sizeBytes") != len(closure_bytes)
        or Path(str(expected_closure.get("path"))).name != closure_name
        or not isinstance(human_evaluation, dict)
        or human_evaluation.get("artifact") != contract_name
        or human_evaluation.get("sha256") != external_digest
        or human_evaluation.get("state") != "USER_APPROVED"
        or not isinstance(candidate_decision, dict)
        or candidate_decision.get("primary") != "qwen3_4b"
    ):
        raise ResearchDecisionError("P6-T5 evidence bundle: closure does not approve the frozen H01 contract")
    return manifest, sha256_bytes(manifest_bytes)


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
        "--gap-suite",
        type=Path,
        default=ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json",
        help="append-only Research gap evaluation suite",
    )
    parser.add_argument(
        "--gap-suite-lock",
        type=Path,
        default=ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.lock.json",
        help="Research gap suite content lock",
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
    parser.add_argument(
        "--baseline-evidence",
        type=Path,
        help="user-approved frozen H01 contract or validated P7-T3 Research evidence binding",
    )
    parser.add_argument(
        "--baseline-review-input",
        type=Path,
        help="retained P6-T5 H01 review input used to verify candidate-case digests",
    )
    parser.add_argument(
        "--baseline-evidence-bundle",
        type=Path,
        help="retained P6-T5 final evidence bundle used to verify the frozen contract SHA-256",
    )
    parser.add_argument(
        "--baseline-binding-output",
        type=Path,
        default=ROOT / "evidence" / "p7-t3-research-baseline-evidence.json",
        help="minimal repository binding emitted when importing the frozen H01 contract",
    )
    parser.add_argument(
        "--gap-evidence",
        type=Path,
        help="actual user-approved Research gap evidence; definitions or review packets are insufficient",
    )
    parser.add_argument(
        "--governance-request",
        type=Path,
        default=ROOT / "config" / "p7-t3-research-report-eval-governance-request.json",
        help="pending scoped Research report-evaluation governance request",
    )
    parser.add_argument(
        "--governance-approval",
        type=Path,
        help="explicit approved follow-up artifact required when gap evidence includes report review",
    )
    parser.add_argument(
        "--merged-evidence-output",
        type=Path,
        default=ROOT / "evidence" / "p7-t3-research-merged-baseline-evidence.json",
        help="append-only deterministic H01 plus gap evidence merge",
    )
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
        gap_suite = _load_document(args.gap_suite, "Research gap evaluation suite")
        gap_suite_lock = _load_document(args.gap_suite_lock, "Research gap evaluation suite lock")
        benchmark_config = _load_document(args.benchmark_config, "benchmark config")
        decision_manifest = _load_document(args.decisions, "adapter decision manifest")
        training_config = _load_document(args.training_config, "training config")
        validate_suite_lock(suite, args.suite, suite_lock, benchmark_config)
        validate_gap_suite_lock(gap_suite, args.gap_suite, gap_suite_lock, suite, benchmark_config)
        governance_request = _load_document(args.governance_request, "Research report governance request")
        GOVERNANCE.validate_request(
            governance_request,
            GOVERNANCE.load_document(GOVERNANCE.GOVERNANCE_PATH),
            GOVERNANCE.load_document(GOVERNANCE.FIXTURE_PATH),
            GOVERNANCE.load_document(GOVERNANCE.SCHEMA_PATH),
            gap_suite,
        )
        governance_approval = (
            _load_document(args.governance_approval, "Research report governance approval")
            if args.governance_approval is not None
            else None
        )
        source_commit = _source_commit(args.source_commit)
        evidence: dict[str, Any] | None = None
        evidence_inventory_path: Path | None = None
        if args.baseline_evidence is not None:
            loaded_evidence = _load_document(args.baseline_evidence, "baseline evidence")
            if loaded_evidence.get("artifactType") == FROZEN_H01_ARTIFACT_TYPE:
                if args.baseline_review_input is None or args.baseline_evidence_bundle is None:
                    raise ResearchDecisionError(
                        "frozen H01 import requires --baseline-review-input and --baseline-evidence-bundle"
                    )
                review_input = _load_document(args.baseline_review_input, "H01 review input")
                evidence_manifest, evidence_manifest_sha256 = _load_p6_t5_evidence_bundle(
                    args.baseline_evidence_bundle,
                    args.baseline_evidence,
                )
                binding_reference = _repository_reference(args.baseline_binding_output)
                evidence = import_frozen_h01_contract(
                    loaded_evidence,
                    suite,
                    benchmark_config,
                    source_reference=args.baseline_evidence.name,
                    source_sha256=sha256_bytes(args.baseline_evidence.read_bytes()),
                    source_size=args.baseline_evidence.stat().st_size,
                    evidence_reference=binding_reference,
                    review_input=review_input,
                    review_input_reference=args.baseline_review_input.name,
                    review_input_sha256=sha256_bytes(args.baseline_review_input.read_bytes()),
                    evidence_manifest=evidence_manifest,
                    evidence_manifest_reference="p6-t5-final-evidence-manifest-v1.json",
                    evidence_manifest_sha256=evidence_manifest_sha256,
                    source_commit=source_commit,
                )
                _write_json_atomically(args.baseline_binding_output, evidence)
                evidence_inventory_path = args.baseline_binding_output
            elif loaded_evidence.get("artifactType") == BASELINE_BINDING_ARTIFACT_TYPE:
                evidence = loaded_evidence
                validate_baseline_evidence(evidence, suite)
                evidence_inventory_path = args.baseline_evidence
            elif loaded_evidence.get("artifactType") == MERGED_EVIDENCE_ARTIFACT_TYPE:
                evidence = loaded_evidence
                validate_merged_evidence(evidence, suite, gap_suite)
                evidence_inventory_path = args.baseline_evidence
            else:
                raise ResearchDecisionError(
                    "baseline evidence must be the user-approved frozen contract or a validated P7-T3 binding/merge"
                )
        gap_evidence_path: Path | None = None
        if args.gap_evidence is not None:
            if evidence is None or evidence.get("artifactType") != BASELINE_BINDING_ARTIFACT_TYPE:
                raise ResearchDecisionError(
                    "gap evidence merge requires the validated historical P7-T3 baseline binding"
                )
            loaded_gap_evidence = _load_document(args.gap_evidence, "Research gap evidence")
            validate_gap_evidence(
                loaded_gap_evidence,
                gap_suite,
                evidence,
                suite,
                governance_request=governance_request,
                governance_approval=governance_approval,
            )
            evidence = merge_research_evidence(
                evidence,
                loaded_gap_evidence,
                suite,
                gap_suite,
                evidence_reference=_repository_reference(args.merged_evidence_output),
                source_commit=source_commit,
                governance_request=governance_request,
                governance_approval=governance_approval,
            )
            _write_json_atomically(args.merged_evidence_output, evidence)
            evidence_inventory_path = args.merged_evidence_output
            gap_evidence_path = args.gap_evidence
        evidence_artifacts = [
            ("evaluation-suite", args.suite),
            ("evaluation-suite-lock", args.suite_lock),
            ("evaluation-governance-binding", freeze_binding),
            ("research-gap-evaluation-suite", args.gap_suite),
            ("research-gap-evaluation-suite-lock", args.gap_suite_lock),
            ("research-report-evaluation-governance-request", args.governance_request),
            ("benchmark-configuration", args.benchmark_config),
            ("adapter-strategy-decision", args.decisions),
            ("adapter-strategy-rationale", p6_rationale),
            ("training-configuration", args.training_config),
        ]
        if evidence_inventory_path is not None:
            evidence_artifacts.append(("research-baseline-evidence-binding", evidence_inventory_path))
        if gap_evidence_path is not None:
            evidence_artifacts.append(("research-gap-evidence", gap_evidence_path))
        if args.governance_approval is not None:
            evidence_artifacts.append(("research-report-evaluation-governance-approval", args.governance_approval))
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
            source_commit=source_commit,
            gap_suite=gap_suite,
        )
        validate_research_decision_record(decision)
        _write_json_atomically(args.output, decision)
        print(json.dumps(decision, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except (ResearchDecisionError, GOVERNANCE.GovernanceError) as error:
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
