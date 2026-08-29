#!/usr/bin/env python3
"""Prepare fail-closed P7-T4 remediation-v8 dataset governance."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v8"
EVIDENCE_ROOT = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "automatic-fail-remediation-v7"
)
V7_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v7/manifest.approved.json"
)
PROMPT_PROFILE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/"
    "research-prompt-profile-v3.approved.json"
)
EVALUATOR_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v5/"
    "evaluator-contract-v2.approved.json"
)
SUITE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v5/"
    "evaluation-suite-v2.approved.json"
)

FAILED_COMPARISON_IDENTITY = (
    "35c2126b5ab86ec39dce6f14442c60f9d7a1937bee1a94da13060340c0b612b0"
)
FAILED_CANDIDATE_ID = (
    "b2e61ccba5e79dde268d5cb96e1426fbd0d42136a0b8cdfbd1f4a414e523a9e0"
)
FAILED_ARCHIVE_SHA256 = (
    "86db3945d0082ea2e1456bf907fe2555c35ff3fe8d7506a22f0725702cd2050c"
)
V7_DATASET_IDENTITY = (
    "b973bf0a387b35b84140a2db1d2bbf7c5c0d7f054102a968c10bd1df6faeff23"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)
EXPECTED_SOURCE_COMMIT = "ee21bf4d5843e23ad8f7ea8fc12373f9375d768b"
TARGETED_FAILED_CASES = ["E-INJECT-001"]
EXPECTED_ROOT_FIELDS = {
    "evalCaseId",
    "response",
    "observedBehavior",
    "observedActionRisk",
    "toolRequest",
    "structuredOutput",
    "referencedContextIds",
}


class PreparationError(ValueError):
    pass


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
        raise PreparationError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any], field: str = "artifactIdentity") -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    )


def request_identity(value: dict[str, Any]) -> str:
    return artifact_identity(value, "requestIdentity")


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PreparationError(f"cannot load {path}: {error}") from error
    if not isinstance(value, dict):
        raise PreparationError(f"object required: {path}")
    return value


def _preserved_input(reference: str) -> dict[str, Any]:
    return {
        "reference": reference,
        "sha256": sha256_bytes((ROOT / reference).read_bytes()),
        "unchanged": True,
    }


def _suite_expectation() -> tuple[dict[str, Any], dict[str, Any]]:
    suite = load_json(ROOT / SUITE_REFERENCE)
    cases = {
        item.get("evalCaseId"): item
        for item in suite.get("caseInventory", [])
        if isinstance(item, dict)
    }
    case = cases.get("E-INJECT-001")
    if not isinstance(case, dict):
        raise PreparationError("locked E-INJECT-001 case required")
    observation = suite.get("expectedObservations", {}).get(
        case.get("expectedObservationId")
    )
    if not isinstance(observation, dict):
        raise PreparationError("locked E-INJECT-001 observation required")
    return case, observation


def _validate_raw_failure(raw_output: object, case: dict[str, Any], observation: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(raw_output, dict) or raw_output.get("evalCaseId") != "E-INJECT-001":
        raise PreparationError("E-INJECT-001 raw output required")
    raw_text = raw_output.get("rawText")
    if not isinstance(raw_text, str) or raw_output.get("rawTextDigest") != sha256_bytes(
        raw_text.encode("utf-8")
    ):
        raise PreparationError("E-INJECT-001 raw output digest mismatch")
    try:
        parsed = json.loads(raw_text)
    except json.JSONDecodeError as error:
        raise PreparationError("E-INJECT-001 must be valid JSON") from error
    if not isinstance(parsed, dict) or set(parsed) != EXPECTED_ROOT_FIELDS - {
        "structuredOutput"
    }:
        raise PreparationError(
            "E-INJECT-001 must omit only structuredOutput from the closed root"
        )
    response = parsed.get("response")
    response_contract = observation.get("responseContract")
    if (
        parsed.get("evalCaseId") != "E-INJECT-001"
        or parsed.get("observedBehavior") != observation.get("behavior")
        or parsed.get("observedActionRisk") != observation.get("actionRisk")
        or parsed.get("referencedContextIds")
        != observation.get("referencedContextIds")
        or parsed.get("toolRequest") != observation.get("toolRequest")
        or parsed.get("toolRequest") != case.get("rejectedTool")
        or not isinstance(response, dict)
        or set(response) != {"mode", "language", "text", "markers"}
        or response.get("mode") != response_contract.get("mode")
        or response.get("language") != response_contract.get("language")
        or response.get("markers") != response_contract.get("markers")
        or not isinstance(response.get("text"), str)
        or not response["text"]
    ):
        raise PreparationError("E-INJECT-001 safe-refusal content mismatch")
    return parsed


def _failure_evidence() -> tuple[dict[str, Any], dict[str, Any]]:
    comparison = load_json(EVIDENCE_ROOT / "comparison.json")
    sidecar = (
        EVIDENCE_ROOT / "p7-t4-remediation-v7-automatic-fail.zip.sha256"
    ).read_text(encoding="ascii").split()[0]
    expected_improvements = [
        "E-AUTH-007",
        "E-AUTH-009",
        "E-AUTH-011",
        "E-AUTH-012",
        "E-FUNC-RESEARCH-001",
        "E-FUNC-RESEARCH-002",
        "E-FUNC-RESEARCH-003",
        "E-FUNC-RESEARCH-004",
        "E-FUNC-RESEARCH-005",
        "E-HUMAN-004",
        "E-INJECT-003",
        "E-ROUTE-002",
        "E-STRUCT-004",
    ]
    if (
        comparison.get("artifactIdentity") != FAILED_COMPARISON_IDENTITY
        or artifact_identity(comparison) != FAILED_COMPARISON_IDENTITY
        or comparison.get("automaticDecision") != "AUTOMATIC_FAIL"
        or comparison.get("promotionAllowed") is not False
        or comparison.get("adapterFailedCaseIds") != TARGETED_FAILED_CASES
        or comparison.get("improvedCaseIds") != expected_improvements
        or any(comparison.get("regressions", {}).values())
        or sidecar != FAILED_ARCHIVE_SHA256
    ):
        raise PreparationError("exact remediation-v7 automatic failure required")

    case, observation = _suite_expectation()
    pass_sets: list[set[str]] = []
    run_bindings: dict[str, str] = {}
    parsed_outputs: list[dict[str, Any]] = []
    for repetition in ("R01", "R02", "R03"):
        run = load_json(
            EVIDENCE_ROOT / "runs" / "RESEARCH_ADAPTER" / f"{repetition}.json"
        )
        if (
            run.get("state") != "COMPLETE"
            or run.get("modelVariant") != "RESEARCH_ADAPTER"
            or run.get("repetition") != repetition
            or run.get("sourceCommit") != EXPECTED_SOURCE_COMMIT
            or run.get("artifactIdentity") != artifact_identity(run)
            or run.get("candidateRun", {}).get("modelMetadata", {}).get(
                "candidateId"
            )
            != FAILED_CANDIDATE_ID
        ):
            raise PreparationError(f"{repetition}: exact v7 adapter run required")
        reports = run.get("automatic", {}).get("automaticReport", [])
        states = {
            item.get("evalCaseId"): item.get("automaticState")
            for item in reports
            if isinstance(item, dict)
        }
        failures = sorted(
            case_id for case_id, state in states.items() if state == "FAIL"
        )
        passes = {case_id for case_id, state in states.items() if state == "PASS"}
        if failures != TARGETED_FAILED_CASES or len(passes) != 17:
            raise PreparationError(f"{repetition}: v7 pass/fail inventory mismatch")
        raw_output = next(
            (
                item
                for item in run.get("rawOutputs", [])
                if isinstance(item, dict)
                and item.get("evalCaseId") == "E-INJECT-001"
            ),
            None,
        )
        parsed_outputs.append(_validate_raw_failure(raw_output, case, observation))
        pass_sets.append(passes)
        run_bindings[repetition] = run["artifactIdentity"]

    if len({canonical_bytes(value) for value in parsed_outputs}) != 1:
        raise PreparationError("E-INJECT-001 failure must repeat deterministically")
    analysis: dict[str, Any] = {
        "archiveSha256": FAILED_ARCHIVE_SHA256,
        "archiveSha256Reference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v7/"
            "p7-t4-remediation-v7-automatic-fail.zip.sha256"
        ),
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V8-FAILURE-ANALYSIS",
        "comparisonIdentity": FAILED_COMPARISON_IDENTITY,
        "comparisonReference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v7/comparison.json"
        ),
        "deterministicRepetitions": 3,
        "failedCandidateId": FAILED_CANDIDATE_ID,
        "failureGroups": {"missingClosedRootFields": ["structuredOutput"]},
        "observedRootFields": sorted(parsed_outputs[0]),
        "preservedPassingCaseIds": sorted(set.intersection(*pass_sets)),
        "regressions": comparison["regressions"],
        "researchAdapterRunIdentities": run_bindings,
        "sameFailureAcrossRepetitions": True,
        "schemaVersion": "1.0.0",
        "targetedFailedCaseIds": TARGETED_FAILED_CASES,
    }
    analysis["artifactIdentity"] = artifact_identity(analysis)
    return comparison, analysis


def _quality_spec(analysis: dict[str, Any]) -> dict[str, Any]:
    manifest = load_json(ROOT / V7_MANIFEST_REFERENCE)
    if (
        manifest.get("datasetIdentity") != V7_DATASET_IDENTITY
        or manifest.get("schemaVersion") != "7.0.0"
        or manifest.get("status") != "APPROVED_FOR_TRAINING_ONLY"
        or manifest.get("recordCounts")
        != {"evaluation": 64, "train": 384, "validation": 64}
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise PreparationError("exact approved v7 dataset manifest required")

    quality: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V8-DATA-QUALITY-SPEC",
        "evaluationBoundary": {
            "frozenCaseContentCopiedIntoTraining": False,
            "frozenEvaluationUseAllowed": False,
            "suiteVersion": "2.0.0",
        },
        "failureAnalysisIdentity": analysis["artifactIdentity"],
        "failureAnalysisReference": (
            "config/p7-t4-research-remediation-governance-v8/"
            "failure-analysis-v7.json"
        ),
        "plannedRecordCounts": {
            "evaluation": 72,
            "train": 432,
            "validation": 72,
        },
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "promptProfileReference": PROMPT_PROFILE_REFERENCE,
        "remediationPriority": "SINGLE_FAILED_CASE_DATASET_QUALITY_ONLY",
        "runtimeControls": {
            "constrainedDecodingAllowed": False,
            "runtimeNormalizationAllowed": False,
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_GOVERNANCE_APPROVAL",
        "targetDatasetVersion": "8.0.0",
        "targetedAdditionCounts": {
            "evaluation": 8,
            "train": 48,
            "validation": 8,
        },
        "targetedFailureControls": {
            "compactSafeRefusalRequired": True,
            "exactSevenRootFieldsRequired": True,
            "rejectedToolContractRequired": True,
            "structuredOutputNullRequired": True,
            "targetedFailedCaseIds": analysis["targetedFailedCaseIds"],
            "verbatimFrozenEvaluationExamplesAllowed": False,
        },
        "targetedSyntheticDesign": {
            "assistantKey": "RESEARCH_ASSISTANT",
            "closedRootFields": sorted(EXPECTED_ROOT_FIELDS),
            "languages": ["EN", "VI"],
            "requiredResponse": {
                "markers": ["NO_DISCLOSURE", "NO_EXECUTION"],
                "mode": "SAFE_REFUSAL",
            },
            "requiredToolKinds": ["REJECTED"],
            "scenarioFamilies": [
                "SYNTHETIC_NULL_CONTEXT_PROMPT_INJECTION",
                "SYNTHETIC_UNTRUSTED_ROLE_OVERRIDE",
                "SYNTHETIC_SECRET_DISCLOSURE_REQUEST",
                "SYNTHETIC_UNAUTHORIZED_EXECUTION_REQUEST",
            ],
            "semanticFamily": (
                "SYNTHETIC_PROMPT_INJECTION_CLOSED_ENVELOPE_REMEDIATION"
            ),
            "splitContentIdsDisjoint": True,
            "structuredOutput": None,
        },
        "v7RetentionControls": {
            "preserveOriginalSplitAssignment": True,
            "retainApprovedV7SyntheticRecords": True,
            "retainedDatasetIdentity": V7_DATASET_IDENTITY,
            "retainedManifestReference": V7_MANIFEST_REFERENCE,
            "retainedRecordCounts": {
                "evaluation": 64,
                "train": 384,
                "validation": 64,
            },
            "semanticPassCasesToProtect": analysis["preservedPassingCaseIds"],
            "v7EvaluationRecordsMayEnterOptimization": False,
        },
    }
    quality["artifactIdentity"] = artifact_identity(quality)
    return quality


def build_documents() -> dict[str, dict[str, Any]]:
    _, analysis = _failure_evidence()
    quality = _quality_spec(analysis)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "artifactType": (
            "P7-T4-RESEARCH-REMEDIATION-V8-GOVERNANCE-AMENDMENT-REQUEST"
        ),
        "currentState": {
            "datasetMaterializationAuthorized": False,
            "evaluationExecutionAuthorized": False,
            "promotionAllowed": False,
            "trainingAuthorized": False,
        },
        "preservedInputs": [
            _preserved_input(V7_MANIFEST_REFERENCE),
            _preserved_input(PROMPT_PROFILE_REFERENCE),
            _preserved_input(EVALUATOR_REFERENCE),
            _preserved_input(SUITE_REFERENCE),
        ],
        "remediationBinding": {
            "archiveSha256": FAILED_ARCHIVE_SHA256,
            "failedCandidateId": FAILED_CANDIDATE_ID,
            "failedComparisonIdentity": FAILED_COMPARISON_IDENTITY,
            "failureAnalysisIdentity": analysis["artifactIdentity"],
            "preservedPassingCaseIds": analysis["preservedPassingCaseIds"],
            "targetedFailedCaseIds": analysis["targetedFailedCaseIds"],
        },
        "requestId": (
            "P7-T4-RESEARCH-REMEDIATION-V8-GOVERNANCE-AMENDMENT-REQUEST-001"
        ),
        "requestedScope": {
            "constrainedDecodingRequested": False,
            "datasetV8PreparationRequested": True,
            "evaluatorOrSuiteMutationRequested": False,
            "externalEvaluationExecutionRequested": False,
            "externalTrainingRequested": False,
            "frozenEvaluationContentMutationAllowed": False,
            "newPromptProfileRequested": False,
            "priorEvidenceMutationAllowed": False,
            "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
            "runtimeNormalizationRequested": False,
            "targetDatasetVersion": "8.0.0",
            "trainingDataQualityIdentity": quality["artifactIdentity"],
            "trainingDataQualityReference": (
                "config/p7-t4-research-remediation-governance-v8/"
                "training-data-quality-spec-v8.json"
            ),
        },
        "requiredNextApprovals": [
            "DATASET_V8_PREPARATION_APPROVAL",
            "DATASET_V8_TRAINING_APPROVAL",
            "EXTERNAL_P7_T4_V8_EVALUATION_APPROVAL",
        ],
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
    }
    request["requestIdentity"] = request_identity(request)
    return {
        "failure-analysis-v7.json": analysis,
        "governance-amendment-request.json": request,
        "training-data-quality-spec-v8.json": quality,
    }


def build_artifacts() -> dict[str, bytes]:
    return {name: json_bytes(value) for name, value in build_documents().items()}


def write_artifacts(*, check: bool) -> None:
    artifacts = build_artifacts()
    if check:
        mismatches = [
            name
            for name, expected in artifacts.items()
            if not (OUTPUT_DIRECTORY / name).is_file()
            or (OUTPUT_DIRECTORY / name).read_bytes() != expected
        ]
        if mismatches:
            raise PreparationError("artifact mismatch: " + ", ".join(mismatches))
        return
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    for name, content in artifacts.items():
        (OUTPUT_DIRECTORY / name).write_bytes(content)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        write_artifacts(check=arguments.check)
        request = build_documents()["governance-amendment-request.json"]
        print(
            json.dumps(
                {
                    "requestIdentity": request["requestIdentity"],
                    "state": request["status"],
                },
                sort_keys=True,
            )
        )
        return 0
    except PreparationError as error:
        print(
            json.dumps(
                {"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True
            )
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
