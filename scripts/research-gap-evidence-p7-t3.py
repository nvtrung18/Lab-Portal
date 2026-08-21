#!/usr/bin/env python3
"""Execute only P7-T3 Research gap cases and stop at human approval.

The runner never retrieves a model or installs packages. A pre-provisioned
snapshot at the exact frozen revision is mandatory. The run artifact is not
frozen evidence until a complete user review is validated by ``--freeze``.
"""
from __future__ import annotations

import argparse
import copy
import importlib.metadata
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
BASE_SUITE_PATH = ROOT / "evals" / "p6-t4-evaluation-suites.yaml"
GAP_SUITE_PATH = ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json"
GAP_LOCK_PATH = ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.lock.json"
GOVERNANCE_REQUEST_PATH = ROOT / "config" / "p7-t3-research-report-eval-governance-request.json"
BENCHMARK_CONFIG_PATH = ROOT / "config" / "p6-t5-benchmark.yaml"
BASELINE_EVIDENCE_PATH = ROOT / "evidence" / "p7-t3-research-baseline-evidence.json"
RUBRIC_PATH = ROOT / "evals" / "human-eval-rubric.yaml"
RUN_ID = "qwen3_4b-R01-P7T3-GAP01"
RUN_ARTIFACT_TYPE = "P7-T3-RESEARCH-GAP-CANDIDATE-RUN"
REVIEW_INPUT_ARTIFACT_TYPE = "P7-T3-RESEARCH-GAP-HUMAN-REVIEW-INPUT"
LOCAL_PATH_PATTERN = re.compile(
    r"(?<![A-Za-z0-9])(?:[A-Za-z]:[\\/][^\s\"'<>]+|/(?:home|Users|tmp|var/tmp)/[^\s\"'<>]+)"
)


def _load_module(name: str, path: Path) -> Any:
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


P7T3 = _load_module("p7t3_gap_contract", ROOT / "scripts" / "research-model-decision-p7-t3.py")
GOVERNANCE = _load_module(
    "p7t3_report_governance",
    ROOT / "scripts" / "research-report-eval-governance-p7-t3.py",
)
BENCHMARK = _load_module("p7t3_gap_benchmark", ROOT / "scripts" / "benchmark-p6-t5.py")
EVALUATOR = BENCHMARK.load_evaluator()


def _load(path: Path) -> dict[str, Any]:
    return P7T3._load_document(path, path.name)


def _write_append_only(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        raise P7T3.ResearchDecisionError(f"output {path}: append-only artifact already exists")
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
        os.link(temporary_name, path)
        Path(temporary_name).unlink()
        temporary_name = None
    except (OSError, TypeError, ValueError) as error:
        if temporary_name is not None:
            Path(temporary_name).unlink(missing_ok=True)
        raise P7T3.ResearchDecisionError(f"output {path}: cannot write: {error}") from error


def select_execution_suite(
    gap_suite: dict[str, Any],
    case_ids: list[str],
    *,
    governance_request: dict[str, Any],
    governance_approval: dict[str, Any] | None,
) -> dict[str, Any]:
    """Return only explicitly selected P7-T3 cases; report review needs approval."""
    if not case_ids or len(case_ids) != len(set(case_ids)):
        raise P7T3.ResearchDecisionError("TARGETED_CASE_SELECTION_INVALID: unique case IDs required")
    active = {case["evalCaseId"]: case for case in gap_suite["caseInventory"]}
    proposed = {case["evalCaseId"]: case for case in gap_suite["proposedCaseInventory"]}
    allowed = set(active) | set(proposed)
    if not set(case_ids).issubset(allowed):
        raise P7T3.ResearchDecisionError("TARGETED_CASE_SELECTION_INVALID: unrelated case requested")
    selected_ids = sorted(case_ids)
    report_selected = "E-FUNC-RESEARCH-006" in selected_ids
    if report_selected:
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
            raise P7T3.ResearchDecisionError(error.diagnostics) from error

    execution_suite = copy.deepcopy(gap_suite)
    selected_cases: list[dict[str, Any]] = []
    for case_id in selected_ids:
        case = copy.deepcopy(active.get(case_id) or proposed[case_id])
        if case_id in proposed:
            case["caseState"] = "ACTIVE"
        selected_cases.append(case)
    execution_suite["caseInventory"] = selected_cases
    execution_suite["expectedObservations"] = {
        case["expectedObservationId"]: copy.deepcopy(
            gap_suite["expectedObservations"][case["expectedObservationId"]]
        )
        for case in selected_cases
    }
    execution_suite["matrices"] = {
        "humanApplicabilityBinding": {
            "DRAFT_RESEARCH": [case["evalCaseId"] for case in selected_cases if case["humanProfileId"] == "DRAFT_RESEARCH"],
            "REFUSAL": [case["evalCaseId"] for case in selected_cases if case["humanProfileId"] == "REFUSAL"],
            "NONE": [],
        }
    }
    execution_suite["executionPolicy"]["caseIds"] = selected_ids
    if report_selected:
        execution_suite["governanceBlockers"] = []
    return execution_suite


def preflight_environment(
    model_path: Path | None,
    gap_suite: dict[str, Any],
    gap_lock: dict[str, Any],
    base_suite: dict[str, Any],
    benchmark_config: dict[str, Any],
    *,
    device: str = "cpu",
    output_dir: Path | None = None,
    case_ids: list[str] | None = None,
) -> dict[str, Any]:
    """Report environment facts without model retrieval or package installation."""
    P7T3.validate_gap_suite_lock(
        gap_suite,
        GAP_SUITE_PATH,
        gap_lock,
        base_suite,
        benchmark_config,
    )
    diagnostics: list[str] = []
    revision = P7T3.BASE_MODEL["revision"]
    if model_path is None:
        diagnostics.append("MODEL_SNAPSHOT_REQUIRED")
        model_reference = None
    else:
        resolved = model_path.resolve()
        model_reference = str(resolved)
        if not resolved.is_dir():
            diagnostics.append("MODEL_SNAPSHOT_UNAVAILABLE")
        elif revision not in resolved.parts:
            diagnostics.append("MODEL_REVISION_PATH_MISMATCH")
        else:
            required_files = ("config.json", "tokenizer_config.json", "p7-t3-model-identity.json")
            if any(not (resolved / name).is_file() for name in required_files):
                diagnostics.append("MODEL_LOCAL_FILES_INCOMPLETE")
            tokenizer_available = any((resolved / name).is_file() for name in ("tokenizer.json", "tokenizer.model"))
            weights_available = any(resolved.glob("*.safetensors")) or any(resolved.glob("pytorch_model*.bin"))
            if not tokenizer_available or not weights_available:
                diagnostics.append("MODEL_LOCAL_FILES_INCOMPLETE")
            try:
                identity = json.loads((resolved / "p7-t3-model-identity.json").read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                identity = None
            if identity != P7T3.BASE_MODEL:
                diagnostics.append("MODEL_IDENTITY_MISMATCH")
            try:
                config = json.loads((resolved / "config.json").read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                config = None
            if not isinstance(config, dict) or config.get("model_type") != "qwen3":
                diagnostics.append("MODEL_CONFIG_IDENTITY_MISMATCH")

    required_versions = {
        "torch": str(benchmark_config.get("runtime", {}).get("artifacts", {}).get("torch", {}).get("version")),
        "transformers": str(
            benchmark_config.get("runtime", {}).get("artifacts", {}).get("transformers", {}).get("version")
        ),
    }
    observed_versions: dict[str, str | None] = {}
    for package, required in required_versions.items():
        try:
            observed = importlib.metadata.version(package)
        except importlib.metadata.PackageNotFoundError:
            observed = None
            diagnostics.append(f"{package.upper()}_RUNTIME_UNAVAILABLE")
        observed_versions[package] = observed
        if observed is not None and observed != required:
            diagnostics.append(f"{package.upper()}_VERSION_MISMATCH")

    if device == "cuda:0" and observed_versions.get("torch") is not None:
        try:
            import torch

            if not torch.cuda.is_available():
                diagnostics.append("CUDA_RUNTIME_UNAVAILABLE")
        except ImportError:
            diagnostics.append("TORCH_RUNTIME_UNAVAILABLE")
    elif device != "cpu":
        diagnostics.append("REQUESTED_DEVICE_UNSUPPORTED")

    evidence_output = (output_dir or (ROOT / "evidence" / "p7-t3-gap-run")).resolve()
    writable_parent = evidence_output
    while not writable_parent.exists() and writable_parent != writable_parent.parent:
        writable_parent = writable_parent.parent
    if not writable_parent.is_dir() or not os.access(writable_parent, os.W_OK) or evidence_output.is_file():
        diagnostics.append("EVIDENCE_OUTPUT_NOT_WRITABLE")
    try:
        evidence_output_reference = P7T3._repository_reference(evidence_output)
    except P7T3.ResearchDecisionError:
        evidence_output_reference = "EXTERNAL_OUTPUT_DIRECTORY"

    return {
        "status": "READY_FOR_TARGETED_EXECUTION" if not diagnostics else "EVIDENCE_EXECUTION_ENVIRONMENT_REQUIRED",
        "diagnostics": sorted(set(diagnostics)),
        "candidateId": gap_suite["executionPolicy"]["candidateId"],
        "sourceRunId": gap_suite["executionPolicy"]["sourceRunId"],
        "model": gap_suite["executionPolicy"]["model"],
        "modelPath": model_reference,
        "caseIds": sorted(case_ids or gap_suite["executionPolicy"]["caseIds"]),
        "suite": {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        },
        "runtimeVersions": observed_versions,
        "requestedDevice": device,
        "evidenceOutputDirectory": evidence_output_reference,
        "networkAccessAllowed": False,
        "localFilesOnly": True,
        "offlineEnvironment": {"HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1"},
    }


def _validate_report_output(value: object, *, expected_report_ref: object) -> list[str]:
    fields = {
        "kind", "reportRef", "reviewSummary", "issues", "suggestions",
        "requiresHumanReview", "advisoryOnly",
    }
    if not isinstance(value, dict) or set(value) != fields:
        return ["EVAL-STRUCTURED-OUTPUT: report-review fields are not closed"]
    if (
        value.get("kind") != "RESEARCH_REPORT_REVIEW_DRAFT"
        or value.get("reportRef") != expected_report_ref
        or value.get("requiresHumanReview") is not True
        or value.get("advisoryOnly") is not True
        or not isinstance(value.get("reportRef"), str)
        or not isinstance(value.get("reviewSummary"), str)
        or not 1 <= len(value["reviewSummary"]) <= 800
    ):
        return ["EVAL-STRUCTURED-OUTPUT: bounded advisory report-review draft and report reference required"]
    for field in ("issues", "suggestions"):
        items = value.get(field)
        if (
            not isinstance(items, list)
            or not 1 <= len(items) <= 5
            or any(not isinstance(item, str) or not 1 <= len(item) <= 300 for item in items)
        ):
            return [f"EVAL-STRUCTURED-OUTPUT: bounded {field} required"]
    return []


def score_gap_candidate(
    execution_suite: dict[str, Any], candidate: object
) -> tuple[list[str], dict[str, object]]:
    if (
        not isinstance(candidate, dict)
        or set(candidate) - {"suiteId", "suiteVersion", "candidateRunId", "modelMetadata", "cases"}
        or candidate.get("suiteId") != execution_suite["suiteId"]
        or candidate.get("suiteVersion") != execution_suite["suiteVersion"]
        or not isinstance(candidate.get("candidateRunId"), str)
        or not isinstance(candidate.get("cases"), list)
    ):
        return ["EVAL-INVALID-INPUT: candidate identity or fields invalid"], {}
    expected_cases = execution_suite["caseInventory"]
    candidate_cases = candidate["cases"]
    by_id = {item.get("evalCaseId"): item for item in candidate_cases if isinstance(item, dict)}
    expected_ids = {item["evalCaseId"] for item in expected_cases}
    if len(by_id) != len(candidate_cases) or set(by_id) != expected_ids:
        return ["EVAL-INVALID-INPUT: candidate inventory must exactly match targeted cases"], {}
    findings: list[str] = []
    outcomes: list[dict[str, str]] = []
    for case in expected_cases:
        case_id = case["evalCaseId"]
        actual = by_id[case_id]
        expected = execution_suite["expectedObservations"][case["expectedObservationId"]]
        finding_count = len(findings)
        outcome = {
            "evalCaseId": case_id,
            "candidateCaseDigest": P7T3.sha256_bytes(P7T3.canonical_bytes(actual)),
            "automaticState": "FAIL",
        }
        outcomes.append(outcome)
        if set(actual) != {
            "evalCaseId", "response", "observedBehavior", "observedActionRisk",
            "toolRequest", "structuredOutput", "referencedContextIds",
        }:
            findings.append(f"EVAL-INVALID-INPUT: {case_id} fields are not closed")
            continue
        for actual_key, expected_key, code in (
            ("observedBehavior", "behavior", "EVAL-BEHAVIOR"),
            ("observedActionRisk", "actionRisk", "EVAL-ACTION-RISK"),
            ("referencedContextIds", "referencedContextIds", "EVAL-FORBIDDEN-REFERENCE"),
        ):
            if actual.get(actual_key) != expected.get(expected_key):
                findings.append(f"{code}: {case_id}")
        findings.extend(
            f"{error} ({case_id})"
            for error in EVALUATOR.validate_response(actual.get("response"), expected.get("responseContract"))
        )
        findings.extend(
            f"{error} ({case_id})" for error in EVALUATOR.validate_tool(actual.get("toolRequest"))
        )
        if actual.get("toolRequest") != expected.get("toolRequest"):
            findings.append(f"EVAL-ROUTING-IDENTITY: {case_id}")
        if case.get("structuredOutputContract") == "RESEARCH_REPORT_REVIEW_DRAFT":
            output_errors = _validate_report_output(
                actual.get("structuredOutput"),
                expected_report_ref=expected.get("structuredOutput", {}).get("reportRef"),
            )
        else:
            output_errors = EVALUATOR.validate_output(
                actual.get("structuredOutput"), case.get("structuredOutputContract")
            )
        findings.extend(f"{error} ({case_id})" for error in output_errors)
        if len(findings) == finding_count:
            outcome["automaticState"] = "PASS"
    return sorted(set(findings)), {
        "suiteId": execution_suite["suiteId"],
        "suiteVersion": execution_suite["suiteVersion"],
        "candidateRunId": candidate["candidateRunId"],
        "automaticReport": outcomes,
    }


def _sanitize_review_value(value: object) -> object:
    if isinstance(value, str):
        redacted = re.sub(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", "[REDACTED_EMAIL]", value)
        redacted = LOCAL_PATH_PATTERN.sub("[REDACTED_LOCAL_PATH]", redacted)
        return redacted[:2000]
    if isinstance(value, list):
        return [_sanitize_review_value(item) for item in value[:10]]
    if isinstance(value, dict):
        return {key: _sanitize_review_value(child) for key, child in value.items()}
    return value


def build_run_artifact(
    gap_suite: dict[str, Any],
    candidate: dict[str, Any],
    *,
    raw_outputs: list[dict[str, Any]],
    governance_approval: dict[str, Any] | None = None,
) -> dict[str, Any]:
    findings, automatic = score_gap_candidate(gap_suite, candidate)
    candidate_digest = P7T3.sha256_bytes(P7T3.canonical_bytes(candidate))
    expected_case_ids = sorted(case["evalCaseId"] for case in gap_suite["caseInventory"])
    raw_case_ids = sorted(output.get("evalCaseId") for output in raw_outputs if isinstance(output, dict))
    if len(raw_case_ids) != len(raw_outputs) or raw_case_ids != expected_case_ids:
        raise P7T3.ResearchDecisionError("gap run: raw output inventory must exactly match targeted cases")
    if any(
        set(output) != {"evalCaseId", "rawText", "rawTextDigest"}
        or not isinstance(output.get("rawText"), str)
        or len(output["rawText"]) > 12000
        or output.get("rawTextDigest") != P7T3.sha256_bytes(output["rawText"].encode("utf-8"))
        for output in raw_outputs
        if isinstance(output, dict)
    ):
        raise P7T3.ResearchDecisionError("gap run: bounded digest-bound raw text required")
    report_selected = "E-FUNC-RESEARCH-006" in expected_case_ids
    approval_binding = None
    if report_selected:
        if not isinstance(governance_approval, dict):
            raise P7T3.ResearchDecisionError("AWAITING_GOVERNANCE_APPROVAL")
        approval_binding = {
            "requestIdentity": governance_approval.get("requestIdentity"),
            "approvalIdentity": governance_approval.get("artifactIdentity"),
        }
    return {
        "artifactType": RUN_ARTIFACT_TYPE,
        "schemaVersion": "1.0.0",
        "suite": {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        },
        "candidate": candidate,
        "candidateOutputDigest": candidate_digest,
        "caseSelection": expected_case_ids,
        "caseDigests": {
            case["evalCaseId"]: P7T3.sha256_bytes(
                P7T3.canonical_bytes(
                    next(
                        (
                            proposed
                            for proposed in gap_suite.get("proposedCaseInventory", [])
                            if proposed["evalCaseId"] == case["evalCaseId"]
                        ),
                        case,
                    )
                )
            )
            for case in gap_suite["caseInventory"]
        },
        "governanceApproval": approval_binding,
        "automatic": automatic,
        "findings": findings,
        "rawOutputs": raw_outputs,
        "checkpoint": "AWAITING_USER:HUMAN_EVALUATION",
    }


def build_review_input(gap_suite: dict[str, Any], run_artifact: dict[str, Any]) -> dict[str, Any]:
    rubric = _load(RUBRIC_PATH)
    selected = set(run_artifact["caseSelection"])
    cases = {
        case["evalCaseId"]: case
        for case in [*gap_suite["caseInventory"], *gap_suite.get("proposedCaseInventory", [])]
        if case["evalCaseId"] in selected
    }
    execution_suite = copy.deepcopy(gap_suite)
    execution_suite["caseInventory"] = []
    for case_id in sorted(selected):
        case = copy.deepcopy(cases[case_id])
        if case.get("caseState") == "GOVERNANCE_PENDING":
            case["caseState"] = "ACTIVE"
        execution_suite["caseInventory"].append(case)
    execution_suite["expectedObservations"] = {
        case["expectedObservationId"]: copy.deepcopy(gap_suite["expectedObservations"][case["expectedObservationId"]])
        for case in execution_suite["caseInventory"]
    }
    execution_suite["matrices"] = {
        "humanApplicabilityBinding": {
            "DRAFT_RESEARCH": [case["evalCaseId"] for case in execution_suite["caseInventory"] if case["humanProfileId"] == "DRAFT_RESEARCH"],
            "REFUSAL": [case["evalCaseId"] for case in execution_suite["caseInventory"] if case["humanProfileId"] == "REFUSAL"],
            "NONE": [],
        }
    }
    report: dict[str, Any] = {}
    errors = EVALUATOR.validate_human(execution_suite, run_artifact["candidate"], None, report, rubric)
    if errors:
        raise P7T3.ResearchDecisionError(errors)
    candidate_cases = {case["evalCaseId"]: case for case in run_artifact["candidate"]["cases"]}
    automatic = {item["evalCaseId"]: item for item in run_artifact["automatic"]["automaticReport"]}
    return {
        "artifactType": REVIEW_INPUT_ARTIFACT_TYPE,
        "schemaVersion": "1.0.0",
        "candidateOutputDigest": run_artifact["candidateOutputDigest"],
        "candidate": {
            "candidateRunId": run_artifact["candidate"]["candidateRunId"],
            "modelMetadata": copy.deepcopy(run_artifact["candidate"]["modelMetadata"]),
        },
        "caseReviews": [
            {
                "evalCaseId": case_id,
                "expectedBehavior": copy.deepcopy(
                    execution_suite["expectedObservations"][cases[case_id]["expectedObservationId"]]
                ),
                "sanitizedModelOutput": {
                    key: _sanitize_review_value(candidate_cases[case_id][key])
                    for key in (
                        "response", "observedBehavior", "observedActionRisk", "toolRequest",
                        "structuredOutput", "referencedContextIds",
                    )
                },
                "rubricProfile": cases[case_id]["humanProfileId"],
                "rubricDimensions": copy.deepcopy(rubric["profiles"][cases[case_id]["humanProfileId"]]["dimensions"]),
                "automaticValidation": copy.deepcopy(
                    automatic.get(
                        case_id,
                        {
                            "evalCaseId": case_id,
                            "candidateCaseDigest": P7T3.sha256_bytes(
                                P7T3.canonical_bytes(candidate_cases[case_id])
                            ),
                            "automaticState": "FAIL",
                        },
                    )
                ),
                "caseDigest": run_artifact["caseDigests"][case_id],
            }
            for case_id in sorted(selected)
        ],
        "humanReport": report["humanReport"],
        "checkpoint": "AWAITING_USER:HUMAN_EVALUATION",
        "instruction": "Complete every listed rubric record and provide explicit USER_APPROVED sidecar approval; do not synthesize outcomes.",
    }


def freeze_gap_evidence(
    run_artifact: dict[str, Any],
    review_sidecar: dict[str, Any],
    gap_suite: dict[str, Any],
    base_suite: dict[str, Any],
    baseline_evidence: dict[str, Any],
    *,
    source_reference: str,
    review_reference: str,
    evidence_reference: str,
    source_commit: str,
    governance_request: dict[str, Any] | None = None,
    governance_approval: dict[str, Any] | None = None,
) -> dict[str, Any]:
    P7T3.validate_gap_suite(gap_suite, base_suite)
    P7T3.validate_baseline_evidence(baseline_evidence, base_suite)
    governance_request = governance_request or _load(GOVERNANCE_REQUEST_PATH)
    diagnostics: list[str] = []
    run_fields = {
        "artifactType",
        "schemaVersion",
        "suite",
        "candidate",
        "candidateOutputDigest",
        "caseSelection",
        "caseDigests",
        "governanceApproval",
        "automatic",
        "findings",
        "rawOutputs",
        "checkpoint",
    }
    if not isinstance(run_artifact, dict) or set(run_artifact) != run_fields:
        diagnostics.append("gap run: exact artifact fields required")
        if not isinstance(run_artifact, dict):
            run_artifact = {}
    elif (
        run_artifact.get("artifactType") != RUN_ARTIFACT_TYPE
        or run_artifact.get("schemaVersion") != "1.0.0"
        or run_artifact.get("suite")
        != {
            "id": gap_suite["suiteId"],
            "version": gap_suite["suiteVersion"],
            "digest": gap_suite["suiteDigest"],
        }
        or run_artifact.get("checkpoint") != "AWAITING_USER:HUMAN_EVALUATION"
    ):
        diagnostics.append("gap run: exact suite identity and human checkpoint required")
    selection = run_artifact.get("caseSelection") if isinstance(run_artifact, dict) else None
    try:
        execution_suite = select_execution_suite(
            gap_suite,
            selection if isinstance(selection, list) else [],
            governance_request=governance_request,
            governance_approval=governance_approval,
        )
    except P7T3.ResearchDecisionError as error:
        diagnostics.extend(error.diagnostics)
        execution_suite = copy.deepcopy(gap_suite)
        execution_suite["caseInventory"] = []
    expected_case_digests = {
        case["evalCaseId"]: P7T3.sha256_bytes(
            P7T3.canonical_bytes(
                next(
                    (
                        proposed
                        for proposed in gap_suite.get("proposedCaseInventory", [])
                        if proposed["evalCaseId"] == case["evalCaseId"]
                    ),
                    case,
                )
            )
        )
        for case in execution_suite["caseInventory"]
    }
    if run_artifact.get("caseDigests") != expected_case_digests:
        diagnostics.append("gap run/caseDigests: exact targeted case digests required")
    raw_outputs = run_artifact.get("rawOutputs")
    if (
        not isinstance(raw_outputs, list)
        or sorted(output.get("evalCaseId") for output in raw_outputs if isinstance(output, dict))
        != sorted(selection or [])
        or any(
            not isinstance(output, dict)
            or set(output) != {"evalCaseId", "rawText", "rawTextDigest"}
            or not isinstance(output.get("rawText"), str)
            or len(output["rawText"]) > 12000
            or output.get("rawTextDigest") != P7T3.sha256_bytes(output["rawText"].encode("utf-8"))
            for output in raw_outputs
        )
    ):
        diagnostics.append("gap run/rawOutputs: complete bounded digest-bound execution output required")
    report_selected = isinstance(selection, list) and "E-FUNC-RESEARCH-006" in selection
    expected_governance_binding = (
        {
            "requestIdentity": governance_approval.get("requestIdentity"),
            "approvalIdentity": governance_approval.get("artifactIdentity"),
        }
        if report_selected and isinstance(governance_approval, dict)
        else None
    )
    if run_artifact.get("governanceApproval") != expected_governance_binding:
        diagnostics.append("gap run/governanceApproval: exact approved governance binding required")
    candidate = run_artifact.get("candidate") if isinstance(run_artifact, dict) else None
    if not isinstance(candidate, dict):
        diagnostics.append("gap run/candidate: object required")
        candidate = {}
    candidate_digest = P7T3.sha256_bytes(P7T3.canonical_bytes(candidate))
    if run_artifact.get("candidateOutputDigest") != candidate_digest:
        diagnostics.append("gap run/candidateOutputDigest: canonical candidate digest mismatch")
    expected_metadata = {
        "candidateId": "qwen3_4b",
        "sourceRunId": "qwen3_4b-R01",
        "identifier": P7T3.BASE_MODEL["identifier"],
        "revision": P7T3.BASE_MODEL["revision"],
    }
    if candidate.get("candidateRunId") != RUN_ID or candidate.get("modelMetadata") != expected_metadata:
        diagnostics.append("gap run/candidate: exact qwen3_4b-R01 shared-base identity required")
    score_findings, automatic = score_gap_candidate(execution_suite, candidate)
    if run_artifact.get("findings") != score_findings or run_artifact.get("automatic") != automatic:
        diagnostics.append("gap run: automatic scorer evidence mismatch")

    review_fields = {"candidateOutputDigest", "approval", "review"}
    if not isinstance(review_sidecar, dict) or set(review_sidecar) != review_fields:
        diagnostics.append("gap review: exact candidate digest, approval, and review required")
        review_sidecar = {}
    if review_sidecar.get("candidateOutputDigest") != candidate_digest:
        diagnostics.append("gap review: candidate output digest mismatch")
    approval = review_sidecar.get("approval")
    if not isinstance(approval, dict) or set(approval) != {"decision", "source", "approvedAt"}:
        diagnostics.append("gap review/approval: exact approval fields required")
        approval = {}
    if approval.get("decision") != "USER_APPROVED":
        diagnostics.append("gap review/approval: USER_APPROVED required")
    if not all(isinstance(approval.get(field), str) and approval[field].strip() for field in ("source", "approvedAt")):
        diagnostics.append("gap review/approval: source and approvedAt required")
    rubric = _load(RUBRIC_PATH)
    human_report: dict[str, Any] = {}
    human_errors = EVALUATOR.validate_human(
        execution_suite,
        candidate,
        review_sidecar.get("review"),
        human_report,
        rubric,
    )
    diagnostics.extend(human_errors)
    records = human_report.get("humanReport", {}).get("records", [])
    if human_report.get("humanReviewState") != "COMPLETE" or any(
        record.get("overall") not in {"PASS", "FAIL"} for record in records if isinstance(record, dict)
    ):
        diagnostics.append("gap review: complete actual PASS/FAIL human outcomes required")
    for reference, path in (
        (source_reference, "gap run/reference"),
        (review_reference, "gap review/reference"),
        (evidence_reference, "gap evidence/reference"),
    ):
        P7T3._validate_reference(reference, path, diagnostics)
    if not isinstance(source_commit, str) or not source_commit.strip() or source_commit in {
        "UNAVAILABLE",
        "SOURCE_COMMIT",
    }:
        diagnostics.append("gap evidence/source commit: resolved Git commit required")
    if diagnostics:
        raise P7T3.ResearchDecisionError(diagnostics)

    record_by_case = {record["evalCaseId"]: record for record in records}
    evidence = {
        "artifactType": P7T3.GAP_EVIDENCE_ARTIFACT_TYPE,
        "schemaVersion": P7T3.SCHEMA_VERSION,
        "decisionRuleVersion": P7T3.GAP_DECISION_RULE_VERSION,
        "assistantKey": P7T3.ASSISTANT_KEY,
        "baseModel": dict(P7T3.BASE_MODEL),
        "promptProfile": dict(P7T3.PROMPT_PROFILE),
        "suiteLineage": {
            "base": dict(gap_suite["baseSuite"]),
            "gap": {
                "id": gap_suite["suiteId"],
                "version": gap_suite["suiteVersion"],
                "digest": gap_suite["suiteDigest"],
            },
        },
        "candidate": {
            "id": "qwen3_4b",
            "sourceRunId": "qwen3_4b-R01",
            "gapRunId": RUN_ID,
            "outputDigest": candidate_digest,
        },
        "approval": {
            "status": "USER_APPROVED",
            "reference": review_reference,
            "sha256": P7T3.sha256_bytes(P7T3.canonical_bytes(review_sidecar)),
        },
        "evidenceReference": evidence_reference,
        "sourceCommit": source_commit,
        "caseResults": [
            {
                "evalCaseId": case["evalCaseId"],
                "result": record_by_case[case["evalCaseId"]]["overall"],
                "caseDigest": run_artifact["caseDigests"][case["evalCaseId"]],
                "evidenceSha256": record_by_case[case["evalCaseId"]]["candidateCaseDigest"],
                "sourceRecordReference": f"{source_reference}#/candidate/cases/{case['evalCaseId']}",
                "humanReviewStatus": "USER_APPROVED",
            }
            for case in execution_suite["caseInventory"]
        ],
        "executionCaseIds": sorted(selection or []),
        "governanceApproval": expected_governance_binding,
        "artifactIdentity": "",
    }
    evidence["artifactIdentity"] = P7T3.gap_evidence_identity(evidence)
    P7T3.validate_gap_evidence(
        evidence,
        gap_suite,
        baseline_evidence,
        base_suite,
        governance_request=governance_request,
        governance_approval=governance_approval,
    )
    return evidence


def execute_gap_cases(
    model_path: Path,
    device: str,
    execution_suite: dict[str, Any],
    locked_gap_suite: dict[str, Any],
    gap_lock: dict[str, Any],
    base_suite: dict[str, Any],
    benchmark_config: dict[str, Any],
    *,
    output_dir: Path,
    governance_approval: dict[str, Any] | None,
) -> dict[str, Any]:
    selected_ids = [case["evalCaseId"] for case in execution_suite["caseInventory"]]
    preflight = preflight_environment(
        model_path,
        locked_gap_suite,
        gap_lock,
        base_suite,
        benchmark_config,
        device=device,
        output_dir=output_dir,
        case_ids=selected_ids,
    )
    if preflight["status"] != "READY_FOR_TARGETED_EXECUTION":
        raise P7T3.ResearchDecisionError(preflight["diagnostics"])
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer

    if device.startswith("cuda") and not torch.cuda.is_available():
        raise P7T3.ResearchDecisionError("CUDA_RUNTIME_UNAVAILABLE")
    tokenizer = AutoTokenizer.from_pretrained(model_path, local_files_only=True)
    model = AutoModelForCausalLM.from_pretrained(
        model_path,
        local_files_only=True,
        torch_dtype="auto",
        low_cpu_mem_usage=True,
    ).to(device)
    model.eval()
    decode = benchmark_config["runtime"]["decode"]
    BENCHMARK.configure_determinism(torch, decode["seed"])
    generation = BENCHMARK.generation_kwargs(decode)
    profile = BENCHMARK.assistant_profile(benchmark_config, P7T3.ASSISTANT_KEY)
    template_candidate = {"id": "qwen3_4b", "qwen3": True}
    cases: list[dict[str, Any]] = []
    raw_outputs: list[dict[str, Any]] = []
    with torch.inference_mode():
        for case in execution_suite["caseInventory"]:
            messages = BENCHMARK.render_prompt(case, profile["systemInstruction"])
            inputs = tokenizer.apply_chat_template(
                messages,
                **BENCHMARK.template_kwargs(template_candidate),
            ).to(device)
            output = model.generate(**inputs, **generation)
            raw = tokenizer.decode(
                output[0][inputs["input_ids"].shape[-1] :],
                skip_special_tokens=True,
            )
            parsed, parse_error = BENCHMARK.parse_raw_response(case["evalCaseId"], raw)
            cases.append(BENCHMARK.scored_case(case["evalCaseId"], parsed, parse_error))
            raw_outputs.append(
                {
                    "evalCaseId": case["evalCaseId"],
                    "rawText": raw,
                    "rawTextDigest": P7T3.sha256_bytes(raw.encode("utf-8")),
                }
            )
    candidate = {
        "suiteId": execution_suite["suiteId"],
        "suiteVersion": execution_suite["suiteVersion"],
        "candidateRunId": RUN_ID,
        "modelMetadata": {
            "candidateId": "qwen3_4b",
            "sourceRunId": "qwen3_4b-R01",
            "identifier": P7T3.BASE_MODEL["identifier"],
            "revision": P7T3.BASE_MODEL["revision"],
        },
        "cases": cases,
    }
    return build_run_artifact(
        execution_suite,
        candidate,
        raw_outputs=raw_outputs,
        governance_approval=governance_approval,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--preflight", action="store_true")
    mode.add_argument("--run", action="store_true")
    mode.add_argument("--freeze", action="store_true")
    parser.add_argument("--model-path", type=Path)
    parser.add_argument("--device", choices=("cpu", "cuda:0"), default="cpu")
    parser.add_argument("--case-id", action="append", dest="case_ids")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "evidence" / "p7-t3-gap-run")
    parser.add_argument("--run-artifact", type=Path)
    parser.add_argument("--review", type=Path)
    parser.add_argument("--baseline-evidence", type=Path, default=BASELINE_EVIDENCE_PATH)
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "evidence" / "p7-t3-research-gap-evidence-v1.json",
    )
    parser.add_argument("--source-commit", default="UNAVAILABLE")
    parser.add_argument("--governance-request", type=Path, default=GOVERNANCE_REQUEST_PATH)
    parser.add_argument("--governance-approval", type=Path)
    args = parser.parse_args()
    try:
        base_suite = _load(BASE_SUITE_PATH)
        gap_suite = _load(GAP_SUITE_PATH)
        gap_lock = _load(GAP_LOCK_PATH)
        benchmark_config = _load(BENCHMARK_CONFIG_PATH)
        governance_request = _load(args.governance_request)
        GOVERNANCE.validate_request(
            governance_request,
            GOVERNANCE.load_document(GOVERNANCE.GOVERNANCE_PATH),
            GOVERNANCE.load_document(GOVERNANCE.FIXTURE_PATH),
            GOVERNANCE.load_document(GOVERNANCE.SCHEMA_PATH),
            gap_suite,
        )
        governance_approval = _load(args.governance_approval) if args.governance_approval else None
        selected_case_ids = args.case_ids or gap_suite["executionPolicy"]["caseIds"]
        execution_suite = select_execution_suite(
            gap_suite,
            selected_case_ids,
            governance_request=governance_request,
            governance_approval=governance_approval,
        )
        if args.preflight:
            result = preflight_environment(
                args.model_path,
                gap_suite,
                gap_lock,
                base_suite,
                benchmark_config,
                device=args.device,
                output_dir=args.output_dir,
                case_ids=selected_case_ids,
            )
            print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
            return 0 if result["status"] == "READY_FOR_TARGETED_EXECUTION" else 3
        if args.run:
            if args.model_path is None:
                raise P7T3.ResearchDecisionError("MODEL_SNAPSHOT_REQUIRED")
            run_path = args.output_dir / f"{RUN_ID}.json"
            review_path = args.output_dir / f"{RUN_ID}-review-input.json"
            P7T3._repository_reference(run_path)
            P7T3._repository_reference(review_path)
            run_artifact = execute_gap_cases(
                args.model_path,
                args.device,
                execution_suite,
                gap_suite,
                gap_lock,
                base_suite,
                benchmark_config,
                output_dir=args.output_dir,
                governance_approval=governance_approval,
            )
            review_input = build_review_input(gap_suite, run_artifact)
            if run_path.exists() or review_path.exists():
                raise P7T3.ResearchDecisionError("gap run outputs are append-only and must not already exist")
            _write_append_only(run_path, run_artifact)
            _write_append_only(review_path, review_input)
            print(
                json.dumps(
                    {
                        "status": "AWAITING_USER:HUMAN_EVALUATION",
                        "caseIds": run_artifact["caseSelection"],
                        "runArtifact": P7T3._repository_reference(run_path),
                        "reviewInput": P7T3._repository_reference(review_path),
                    },
                    ensure_ascii=False,
                    sort_keys=True,
                    separators=(",", ":"),
                )
            )
            return 3
        if args.run_artifact is None or args.review is None:
            raise P7T3.ResearchDecisionError("--freeze requires --run-artifact and --review")
        run_artifact = _load(args.run_artifact)
        review = _load(args.review)
        baseline_evidence = _load(args.baseline_evidence)
        frozen = freeze_gap_evidence(
            run_artifact,
            review,
            gap_suite,
            base_suite,
            baseline_evidence,
            source_reference=args.run_artifact.name,
            review_reference=args.review.name,
            evidence_reference=P7T3._repository_reference(args.output),
            source_commit=args.source_commit,
            governance_request=governance_request,
            governance_approval=governance_approval,
        )
        _write_append_only(args.output, frozen)
        print(json.dumps(frozen, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except (P7T3.ResearchDecisionError, GOVERNANCE.GovernanceError) as error:
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
