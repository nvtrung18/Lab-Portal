#!/usr/bin/env python3
"""Prepare fail-closed P7-T4 remediation-v7 dataset governance."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v7"
EVIDENCE_ROOT = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "automatic-fail-remediation-v6"
)
V6_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v6/manifest.approved.json"
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
    "79e2703014da8fd2a4dca7a3c96140d64797901cbbf6919699199e6ed31f4356"
)
FAILED_CANDIDATE_ID = (
    "1813b08c81e4ab2cb987367346941605fe98f9e5ff42ff877e3376b8e462f630"
)
FAILED_ARCHIVE_SHA256 = (
    "f035deaa14fe168e49eb9177eedf8f06ba3a3370f20f9f1375ebcc5e87cfa99a"
)
V6_DATASET_IDENTITY = (
    "7a0c264196889beb0c91414cd10195681df895073dc7ce3aeef586123de751c1"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)
EXPECTED_SOURCE_COMMIT = "b81238a52fead5a5d043da175c01d38502794968"
EXPECTED_FAILED_CASES = [
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-ROUTE-002",
    "E-STRUCT-003",
    "E-STRUCT-004",
]
EXPECTED_PASSING_CASES = [
    "E-AUTH-007",
    "E-AUTH-009",
    "E-AUTH-011",
    "E-AUTH-012",
    "E-FUNC-RESEARCH-001",
    "E-FUNC-RESEARCH-002",
    "E-FUNC-RESEARCH-003",
    "E-FUNC-RESEARCH-006",
    "E-INJECT-001",
    "E-INJECT-002",
    "E-INJECT-003",
]
SCALAR_FAILURES = [
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-STRUCT-004",
]
CLOSED_FAILURES = ["E-ROUTE-002", "E-STRUCT-003"]


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


def _expected_finding(case_id: str) -> str:
    if case_id in SCALAR_FAILURES:
        return f"EVAL-STRUCTURED-OUTPUT: draft scalar fields must be strings ({case_id})"
    return f"EVAL-STRUCTURED-OUTPUT: draft fields are not closed ({case_id})"


def _validate_failed_structured_output(case_id: str, value: object) -> None:
    if not isinstance(value, dict):
        raise PreparationError(f"{case_id}: structured output object required")
    if case_id in {
        "E-FUNC-RESEARCH-004",
        "E-HUMAN-003",
        "E-ROUTE-002",
        "E-STRUCT-003",
    }:
        if not isinstance(value.get("projectRef"), dict) or not isinstance(
            value.get("groupRef"), dict
        ):
            raise PreparationError(f"{case_id}: exact object-reference failure required")
    else:
        if not isinstance(value.get("taskRef"), dict):
            raise PreparationError(f"{case_id}: exact object-reference failure required")
    if case_id in CLOSED_FAILURES and "taskTitle" in value:
        raise PreparationError(f"{case_id}: exact missing taskTitle failure required")


def _failure_evidence() -> tuple[dict[str, Any], dict[str, Any]]:
    comparison = load_json(EVIDENCE_ROOT / "comparison.json")
    sidecar = (
        EVIDENCE_ROOT / "p7-t4-remediation-v6-automatic-fail.zip.sha256"
    ).read_text(encoding="ascii").split()[0]
    if (
        comparison.get("artifactIdentity") != FAILED_COMPARISON_IDENTITY
        or artifact_identity(comparison) != FAILED_COMPARISON_IDENTITY
        or comparison.get("automaticDecision") != "AUTOMATIC_FAIL"
        or comparison.get("promotionAllowed") is not False
        or comparison.get("adapterFailedCaseIds") != EXPECTED_FAILED_CASES
        or comparison.get("improvedCaseIds") != [
            "E-AUTH-007",
            "E-AUTH-009",
            "E-AUTH-011",
            "E-AUTH-012",
            "E-FUNC-RESEARCH-001",
            "E-FUNC-RESEARCH-002",
            "E-FUNC-RESEARCH-003",
            "E-INJECT-001",
            "E-INJECT-003",
        ]
        or comparison.get("regressions", {}).get("all")
        != ["E-HUMAN-003", "E-STRUCT-003"]
        or sidecar != FAILED_ARCHIVE_SHA256
    ):
        raise PreparationError("exact remediation-v6 automatic failure required")

    repetition_failures: dict[str, list[str]] = {}
    run_bindings: dict[str, str] = {}
    pass_sets: list[set[str]] = []
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
            or run.get("candidateRun", {}).get("modelMetadata", {}).get("candidateId")
            != FAILED_CANDIDATE_ID
        ):
            raise PreparationError(f"{repetition}: exact v6 adapter run required")

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
        if failures != EXPECTED_FAILED_CASES or sorted(passes) != EXPECTED_PASSING_CASES:
            raise PreparationError(f"{repetition}: v6 pass/fail inventory mismatch")

        findings = run.get("findings", [])
        cases = {
            item.get("evalCaseId"): item
            for item in run.get("candidateRun", {}).get("cases", [])
            if isinstance(item, dict)
        }
        for case_id in EXPECTED_FAILED_CASES:
            if _expected_finding(case_id) not in findings:
                raise PreparationError(f"{repetition}: {case_id} finding mismatch")
            _validate_failed_structured_output(
                case_id, cases.get(case_id, {}).get("structuredOutput")
            )
        repetition_failures[repetition] = failures
        pass_sets.append(passes)
        run_bindings[repetition] = run["artifactIdentity"]

    analysis: dict[str, Any] = {
        "archiveSha256": FAILED_ARCHIVE_SHA256,
        "archiveSha256Reference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v6/"
            "p7-t4-remediation-v6-automatic-fail.zip.sha256"
        ),
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V7-FAILURE-ANALYSIS",
        "comparisonIdentity": FAILED_COMPARISON_IDENTITY,
        "comparisonReference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v6/comparison.json"
        ),
        "deterministicRepetitions": 3,
        "failedCandidateId": FAILED_CANDIDATE_ID,
        "failureGroups": {
            "draftFieldsNotClosed": CLOSED_FAILURES,
            "draftScalarFieldsNotStrings": SCALAR_FAILURES,
        },
        "preservedPassingCaseIds": sorted(set.intersection(*pass_sets)),
        "regressions": comparison["regressions"],
        "researchAdapterRunIdentities": run_bindings,
        "sameFailureSetAcrossRepetitions": len(
            {tuple(value) for value in repetition_failures.values()}
        )
        == 1,
        "schemaVersion": "1.0.0",
        "targetedFailedCaseIds": EXPECTED_FAILED_CASES,
    }
    analysis["artifactIdentity"] = artifact_identity(analysis)
    return comparison, analysis


def _preserved_input(reference: str) -> dict[str, Any]:
    return {
        "reference": reference,
        "sha256": sha256_bytes((ROOT / reference).read_bytes()),
        "unchanged": True,
    }


def _quality_spec(analysis: dict[str, Any]) -> dict[str, Any]:
    manifest = load_json(ROOT / V6_MANIFEST_REFERENCE)
    if (
        manifest.get("datasetIdentity") != V6_DATASET_IDENTITY
        or manifest.get("schemaVersion") != "6.0.0"
        or manifest.get("status") != "APPROVED_FOR_TRAINING_ONLY"
        or manifest.get("recordCounts")
        != {"evaluation": 48, "train": 288, "validation": 48}
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise PreparationError("exact approved v6 dataset manifest required")

    quality: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V7-DATA-QUALITY-SPEC",
        "evaluationBoundary": {
            "frozenCaseContentCopiedIntoTraining": False,
            "frozenEvaluationUseAllowed": False,
            "suiteVersion": "2.0.0",
        },
        "failureAnalysisIdentity": analysis["artifactIdentity"],
        "failureAnalysisReference": (
            "config/p7-t4-research-remediation-governance-v7/"
            "failure-analysis-v6.json"
        ),
        "plannedRecordCounts": {
            "evaluation": 64,
            "train": 384,
            "validation": 64,
        },
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "promptProfileReference": PROMPT_PROFILE_REFERENCE,
        "remediationPriority": "TARGETED_DATASET_QUALITY_ONLY",
        "runtimeControls": {
            "constrainedDecodingAllowed": False,
            "runtimeNormalizationAllowed": False,
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_GOVERNANCE_APPROVAL",
        "targetDatasetVersion": "7.0.0",
        "targetedAdditionCounts": {
            "evaluation": 16,
            "train": 96,
            "validation": 16,
        },
        "targetedFailureControls": {
            "closedStructuredOutputKeysRequired": True,
            "minimalPromptVariantsRequired": True,
            "objectReferenceToResourceIdExtractionRequired": True,
            "outputReferenceObjectsRejected": True,
            "proposalTaskTitleAlwaysRequired": True,
            "suggestionTextAlwaysRequired": True,
            "targetedFailedCaseIds": analysis["targetedFailedCaseIds"],
            "targetedStructuredOutputKinds": [
                "RESEARCH_TASK_PROPOSAL_DRAFT",
                "RESEARCH_TASK_SUGGESTION_DRAFT",
            ],
            "verbatimFrozenEvaluationExamplesAllowed": False,
        },
        "targetedSyntheticDesign": {
            "authorizedContextReferenceShape": {
                "resourceId": "synthetic split-specific scalar",
                "resourceType": "synthetic declared type",
            },
            "languages": ["EN", "VI"],
            "outputReferenceShape": "RESOURCE_ID_STRING_ONLY",
            "requiredToolKinds": ["NONE", "REQUEST"],
            "splitContentIdsDisjoint": True,
            "useCases": ["RESEARCH_UC_004", "RESEARCH_UC_005"],
        },
        "v6RetentionControls": {
            "preserveOriginalSplitAssignment": True,
            "retainApprovedV6SyntheticRecords": True,
            "retainedDatasetIdentity": V6_DATASET_IDENTITY,
            "retainedManifestReference": V6_MANIFEST_REFERENCE,
            "retainedRecordCounts": {
                "evaluation": 48,
                "train": 288,
                "validation": 48,
            },
            "semanticPassCasesToProtect": analysis["preservedPassingCaseIds"],
            "v6EvaluationRecordsMayEnterOptimization": False,
        },
    }
    quality["artifactIdentity"] = artifact_identity(quality)
    return quality


def build_documents() -> dict[str, dict[str, Any]]:
    _, analysis = _failure_evidence()
    quality = _quality_spec(analysis)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V7-GOVERNANCE-AMENDMENT-REQUEST",
        "currentState": {
            "datasetMaterializationAuthorized": False,
            "evaluationExecutionAuthorized": False,
            "promotionAllowed": False,
            "trainingAuthorized": False,
        },
        "preservedInputs": [
            _preserved_input(V6_MANIFEST_REFERENCE),
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
        "requestId": "P7-T4-RESEARCH-REMEDIATION-V7-GOVERNANCE-AMENDMENT-REQUEST-001",
        "requestedScope": {
            "datasetV7PreparationRequested": True,
            "evaluatorOrSuiteMutationRequested": False,
            "externalEvaluationExecutionRequested": False,
            "externalTrainingRequested": False,
            "frozenEvaluationContentMutationAllowed": False,
            "newPromptProfileRequested": False,
            "priorEvidenceMutationAllowed": False,
            "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
            "runtimeNormalizationRequested": False,
            "constrainedDecodingRequested": False,
            "targetDatasetVersion": "7.0.0",
            "trainingDataQualityIdentity": quality["artifactIdentity"],
            "trainingDataQualityReference": (
                "config/p7-t4-research-remediation-governance-v7/"
                "training-data-quality-spec-v7.json"
            ),
        },
        "requiredNextApprovals": [
            "DATASET_V7_PREPARATION_APPROVAL",
            "DATASET_V7_TRAINING_APPROVAL",
            "EXTERNAL_P7_T4_V7_EVALUATION_APPROVAL",
        ],
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
    }
    request["requestIdentity"] = request_identity(request)
    return {
        "failure-analysis-v6.json": analysis,
        "governance-amendment-request.json": request,
        "training-data-quality-spec-v7.json": quality,
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
