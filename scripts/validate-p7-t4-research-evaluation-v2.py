#!/usr/bin/env python3
"""P7-T4 evaluator v2 with the approved Research report-review contract."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import re
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BASE_PATH = ROOT / "scripts" / "validate-evaluation-suites.py"
EVALUATOR_ID = "P7-T4-RESEARCH-EVALUATOR"
EVALUATOR_VERSION = "2.0.0"
REPORT_REFERENCE_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")


def _load_base():
    specification = importlib.util.spec_from_file_location(
        "p6_evaluation_validator_for_p7_t4_v2", BASE_PATH
    )
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {BASE_PATH}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


BASE = _load_base()
DIAGNOSTICS = BASE.DIAGNOSTICS
RESPONSE_MARKERS = BASE.RESPONSE_MARKERS
TOOL_GROUPS = BASE.TOOL_GROUPS
REJECTION_REASONS = BASE.REJECTION_REASONS
validate_tool = BASE.validate_tool
validate_response = BASE.validate_response
digest = BASE.digest
error = BASE.error


def _bounded_nonempty_strings(value: object, *, maximum_items: int) -> bool:
    return (
        isinstance(value, list)
        and 1 <= len(value) <= maximum_items
        and all(isinstance(item, str) and 1 <= len(item) <= 300 for item in value)
    )


def validate_output(value: object, contract: object) -> list[str]:
    """Validate v1 outputs plus the governance-approved report-review draft."""
    if contract != "RESEARCH_REPORT_REVIEW_DRAFT":
        return BASE.validate_output(value, contract)
    if not isinstance(value, dict):
        return [error(DIAGNOSTICS["output"], "draft object required")]
    fields = {
        "advisoryOnly",
        "issues",
        "kind",
        "reportRef",
        "requiresHumanReview",
        "reviewSummary",
        "suggestions",
    }
    if set(value) != fields:
        return [error(DIAGNOSTICS["output"], "draft fields are not closed")]
    if (
        value.get("kind") != contract
        or value.get("requiresHumanReview") is not True
        or value.get("advisoryOnly") is not True
    ):
        return [error(DIAGNOSTICS["output"], "draft kind or review flags mismatch")]
    report_reference = value.get("reportRef")
    review_summary = value.get("reviewSummary")
    if (
        not isinstance(report_reference, str)
        or REPORT_REFERENCE_PATTERN.fullmatch(report_reference) is None
        or not isinstance(review_summary, str)
        or not 1 <= len(review_summary) <= 800
    ):
        return [error(DIAGNOSTICS["output"], "report review scalar fields are invalid")]
    if not _bounded_nonempty_strings(value.get("issues"), maximum_items=5) or not _bounded_nonempty_strings(
        value.get("suggestions"), maximum_items=5
    ):
        return [error(DIAGNOSTICS["output"], "report review arrays are invalid")]
    return []


def score_candidate(
    suite: dict[str, Any], candidate: object
) -> tuple[list[str], dict[str, object]]:
    """Score a candidate using exact v2 structural comparison."""
    if not isinstance(candidate, dict) or set(candidate) - {
        "suiteId",
        "suiteVersion",
        "candidateRunId",
        "modelMetadata",
        "cases",
    }:
        return [error(DIAGNOSTICS["input"], "candidate fields are not closed")], {}
    if (
        candidate.get("suiteId") != suite.get("suiteId")
        or candidate.get("suiteVersion") != suite.get("suiteVersion")
        or not isinstance(candidate.get("candidateRunId"), str)
    ):
        return [error(DIAGNOSTICS["input"], "candidate identity mismatch")], {}
    expected_cases = [
        item
        for item in suite.get("caseInventory", [])
        if item.get("caseState") != "DEFERRED_ASSERTION_ONLY"
    ]
    candidate_cases = candidate.get("cases")
    if not isinstance(candidate_cases, list):
        return [error(DIAGNOSTICS["input"], "candidate cases must be an array")], {}
    by_id = {
        item.get("evalCaseId"): item
        for item in candidate_cases
        if isinstance(item, dict)
    }
    if len(by_id) != len(candidate_cases) or set(by_id) != {
        item["evalCaseId"] for item in expected_cases
    }:
        return [error(DIAGNOSTICS["input"], "candidate inventory must exactly match evaluable cases")], {}

    findings: list[str] = []
    outcomes: list[dict[str, str]] = []
    exact_fields = {
        "evalCaseId",
        "response",
        "observedBehavior",
        "observedActionRisk",
        "toolRequest",
        "structuredOutput",
        "referencedContextIds",
    }
    for case in expected_cases:
        case_id = case["evalCaseId"]
        actual = by_id[case_id]
        expected = suite["expectedObservations"][case["expectedObservationId"]]
        if set(actual) != exact_fields:
            findings.append(error(DIAGNOSTICS["input"], f"{case_id} fields are not closed"))
            continue
        for actual_key, expected_key, diagnostic in (
            ("observedBehavior", "behavior", DIAGNOSTICS["behavior"]),
            ("observedActionRisk", "actionRisk", DIAGNOSTICS["risk"]),
            ("referencedContextIds", "referencedContextIds", DIAGNOSTICS["reference"]),
        ):
            if actual.get(actual_key) != expected.get(expected_key):
                findings.append(error(diagnostic, case_id))
        findings.extend(
            f"{item} ({case_id})"
            for item in validate_response(
                actual.get("response"), expected.get("responseContract")
            )
        )
        findings.extend(
            f"{item} ({case_id})"
            for item in validate_tool(actual.get("toolRequest"))
        )
        if actual.get("toolRequest") != expected.get("toolRequest"):
            expected_kind = expected["toolRequest"]["kind"]
            diagnostic = (
                DIAGNOSTICS["routing_none"]
                if expected_kind == "NONE"
                else DIAGNOSTICS["routing_rejection"]
                if expected_kind == "REJECTED"
                else DIAGNOSTICS["routing_identity"]
            )
            findings.append(error(diagnostic, case_id))
        findings.extend(
            f"{item} ({case_id})"
            for item in validate_output(
                actual.get("structuredOutput"), case.get("structuredOutputContract")
            )
        )
        outcomes.append(
            {
                "evalCaseId": case_id,
                "candidateCaseDigest": digest(actual),
                "automaticState": "PASS",
            }
        )
    for outcome in outcomes:
        if any(outcome["evalCaseId"] in finding for finding in findings):
            outcome["automaticState"] = "FAIL"
    return sorted(set(findings)), {
        "suiteId": suite["suiteId"],
        "suiteVersion": suite["suiteVersion"],
        "candidateRunId": candidate["candidateRunId"],
        "automaticReport": outcomes,
    }
