#!/usr/bin/env python3
"""Prepare fail-closed P7-T4 remediation-v9 dataset governance."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v9"
EVIDENCE_ROOT = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "automatic-pass-awaiting-review-remediation-v8"
)
ARCHIVE_NAME = "p7-t4-remediation-v8-automatic-pass-awaiting-review.zip"
V8_MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v8/manifest.json"
V8_TRAINING_APPROVAL_REFERENCE = (
    "evidence/p7-t1c-research-remediation-v8-training-governance-approval.json"
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

COMPARISON_IDENTITY = (
    "4ef7a6ee8b2f924bb2a3381b23a5396feede87fbe5e38aed0813403c8c73fa89"
)
CANDIDATE_ID = (
    "cb8c3e4addd20d6de84ed2c135a41baa2841666e631629617dceb3445db04403"
)
ARCHIVE_SHA256 = (
    "ac061b2b5055fac4680078ae0ce929dfc0b023eb58b5c5c873fb8312096068ab"
)
V8_DATASET_IDENTITY = (
    "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)
EXPECTED_SOURCE_COMMIT = "4b29fdb497a59d01828ad8302ae3781c2fb3b3d2"
TARGETED_CASE_IDS = ["E-FUNC-RESEARCH-006"]


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


def _validate_archive() -> None:
    archive = EVIDENCE_ROOT / ARCHIVE_NAME
    sidecar = (EVIDENCE_ROOT / f"{ARCHIVE_NAME}.sha256").read_text(
        encoding="ascii"
    ).split()[0]
    if sidecar != ARCHIVE_SHA256 or sha256_bytes(archive.read_bytes()) != ARCHIVE_SHA256:
        raise PreparationError("exact remediation-v8 automatic-pass archive required")


def _comparison_evidence() -> dict[str, Any]:
    _validate_archive()
    comparison = load_json(EVIDENCE_ROOT / "comparison.json")
    reports = comparison.get("pairedRuns", {})
    if (
        comparison.get("artifactIdentity") != COMPARISON_IDENTITY
        or artifact_identity(comparison) != COMPARISON_IDENTITY
        or comparison.get("automaticDecision") != "AUTOMATIC_PASS"
        or comparison.get("promotionAllowed") is not False
        or comparison.get("adapterFailedCaseIds") != []
        or any(comparison.get("regressions", {}).values())
        or not isinstance(reports, dict)
    ):
        raise PreparationError("exact remediation-v8 automatic-pass comparison required")
    return comparison


def _target_case(run: dict[str, Any]) -> dict[str, Any]:
    cases = run.get("candidateRun", {}).get("cases", [])
    case = next(
        (
            item
            for item in cases
            if isinstance(item, dict) and item.get("evalCaseId") == TARGETED_CASE_IDS[0]
        ),
        None,
    )
    if not isinstance(case, dict):
        raise PreparationError("targeted report-review case required")
    return case


def _failure_evidence() -> tuple[dict[str, Any], dict[str, Any]]:
    comparison = _comparison_evidence()
    automatic_pass_sets: list[set[str]] = []
    case_outputs: list[dict[str, Any]] = []
    run_bindings: dict[str, str] = {}

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
            != CANDIDATE_ID
        ):
            raise PreparationError(f"{repetition}: exact v8 adapter run required")
        states = {
            item.get("evalCaseId"): item.get("automaticState")
            for item in run.get("automatic", {}).get("automaticReport", [])
            if isinstance(item, dict)
        }
        passes = {case_id for case_id, state in states.items() if state == "PASS"}
        if len(states) != 18 or len(passes) != 18:
            raise PreparationError(f"{repetition}: all automatic cases must pass")
        case = _target_case(run)
        structured = case.get("structuredOutput")
        if (
            case.get("observedBehavior") != "SUCCESS"
            or case.get("observedActionRisk") != "DRAFT_ONLY"
            or not isinstance(structured, dict)
            or structured.get("kind") != "RESEARCH_REPORT_REVIEW_DRAFT"
            or structured.get("issues") != ["Báo cáo thiếu số lần lặp có giới hạn."]
            or structured.get("suggestions")
            != ["Bổ sung số lần lặp có giới hạn để con người rà soát."]
        ):
            raise PreparationError(f"{repetition}: exact semantic issue required")
        automatic_pass_sets.append(passes)
        case_outputs.append(case)
        run_bindings[repetition] = run["artifactIdentity"]

    semantic_projection = [
        {
            "issues": item["structuredOutput"]["issues"],
            "reviewSummary": item["structuredOutput"]["reviewSummary"],
            "suggestions": item["structuredOutput"]["suggestions"],
        }
        for item in case_outputs
    ]
    if len({canonical_bytes(value) for value in semantic_projection}) != 1:
        raise PreparationError("targeted semantic issue must repeat deterministically")

    analysis: dict[str, Any] = {
        "archiveSha256": ARCHIVE_SHA256,
        "archiveSha256Reference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-pass-awaiting-review-remediation-v8/"
            f"{ARCHIVE_NAME}.sha256"
        ),
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V9-QUALITY-ANALYSIS",
        "automaticDecision": comparison["automaticDecision"],
        "candidateId": CANDIDATE_ID,
        "comparisonIdentity": COMPARISON_IDENTITY,
        "comparisonReference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-pass-awaiting-review-remediation-v8/comparison.json"
        ),
        "deterministicRepetitions": 3,
        "formalHumanReviewCompleted": False,
        "observedSemanticProjection": semantic_projection[0],
        "preservedAutomaticPassCaseIds": sorted(set.intersection(*automatic_pass_sets)),
        "recommendedGroundedWording": (
            "Báo cáo cần trình bày rõ các giới hạn và liên kết các kết luận "
            "với bằng chứng được cung cấp."
        ),
        "researchAdapterRunIdentities": run_bindings,
        "reviewBasis": "USER_REQUESTED_DOMAIN_SEMANTIC_REMEDIATION",
        "sameSemanticIssueAcrossRepetitions": True,
        "schemaVersion": "1.0.0",
        "semanticIssueGroups": [
            "BOUNDED_INTERPRETED_AS_REPETITION",
            "EVIDENCE_LINK_FOCUS_MISSED",
        ],
        "status": "PROVISIONAL_QUALITY_FINDING_PENDING_GOVERNANCE_APPROVAL",
        "targetedCaseIds": TARGETED_CASE_IDS,
    }
    analysis["artifactIdentity"] = artifact_identity(analysis)
    return comparison, analysis


def _quality_spec(analysis: dict[str, Any]) -> dict[str, Any]:
    manifest = load_json(ROOT / V8_MANIFEST_REFERENCE)
    approval = load_json(ROOT / V8_TRAINING_APPROVAL_REFERENCE)
    if (
        manifest.get("datasetIdentity") != V8_DATASET_IDENTITY
        or manifest.get("schemaVersion") != "8.0.0"
        or manifest.get("recordCounts")
        != {"evaluation": 72, "train": 432, "validation": 72}
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
        or approval.get("status") != "APPROVED"
        or approval.get("datasetIdentity") != V8_DATASET_IDENTITY
        or approval.get("authorization", {}).get("externalTrainingAllowed") is not True
    ):
        raise PreparationError("exact governed v8 training dataset required")

    quality: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V9-DATA-QUALITY-SPEC",
        "evaluationBoundary": {
            "frozenCaseContentCopiedIntoTraining": False,
            "frozenEvaluationUseAllowed": False,
            "suiteVersion": "2.0.0",
        },
        "failureAnalysisIdentity": analysis["artifactIdentity"],
        "failureAnalysisReference": (
            "config/p7-t4-research-remediation-governance-v9/"
            "failure-analysis-v8.json"
        ),
        "plannedRecordCounts": {"evaluation": 80, "train": 480, "validation": 80},
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "promptProfileReference": PROMPT_PROFILE_REFERENCE,
        "remediationPriority": "SINGLE_HUMAN_SEMANTIC_QUALITY_FINDING_DATASET_ONLY",
        "runtimeControls": {
            "constrainedDecodingAllowed": False,
            "runtimeNormalizationAllowed": False,
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_GOVERNANCE_APPROVAL",
        "targetDatasetVersion": "9.0.0",
        "targetedAdditionCounts": {"evaluation": 8, "train": 48, "validation": 8},
        "targetedFailureControls": {
            "boundedMustNotImplyRepetition": True,
            "groundedReportReviewRequired": True,
            "limitationsAndEvidenceLinksRequired": True,
            "targetedCaseIds": analysis["targetedCaseIds"],
            "unknownMustNotBecomeConfirmedMissing": True,
            "verbatimFrozenEvaluationExamplesAllowed": False,
        },
        "targetedSyntheticDesign": {
            "assistantKey": "RESEARCH_ASSISTANT",
            "requiredStructuredOutputKind": "RESEARCH_REPORT_REVIEW_DRAFT",
            "scenarioFamilies": [
                "SYNTHETIC_ADVISORY_REPORT_REVIEW_GROUNDEDNESS",
                "SYNTHETIC_BOUNDED_OBSERVATION_NOT_REPETITION",
                "SYNTHETIC_LIMITATION_AND_EVIDENCE_LINK_REVIEW",
                "SYNTHETIC_UNKNOWN_VS_CONFIRMED_MISSING",
            ],
            "semanticFamily": "SYNTHETIC_REPORT_REVIEW_GROUNDEDNESS_REMEDIATION",
            "splitContentIdsDisjoint": True,
        },
        "v8RetentionControls": {
            "automaticPassCasesToProtect": analysis["preservedAutomaticPassCaseIds"],
            "preserveOriginalSplitAssignment": True,
            "retainApprovedV8SyntheticRecords": True,
            "retainedDatasetIdentity": V8_DATASET_IDENTITY,
            "retainedManifestReference": V8_MANIFEST_REFERENCE,
            "retainedRecordCounts": {"evaluation": 72, "train": 432, "validation": 72},
            "trainingApprovalReference": V8_TRAINING_APPROVAL_REFERENCE,
            "v8EvaluationRecordsMayEnterOptimization": False,
        },
    }
    quality["artifactIdentity"] = artifact_identity(quality)
    return quality


def build_documents() -> dict[str, dict[str, Any]]:
    _, analysis = _failure_evidence()
    quality = _quality_spec(analysis)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V9-GOVERNANCE-AMENDMENT-REQUEST",
        "currentState": {
            "datasetMaterializationAuthorized": False,
            "evaluationExecutionAuthorized": False,
            "promotionAllowed": False,
            "trainingAuthorized": False,
        },
        "preservedInputs": [
            _preserved_input(V8_MANIFEST_REFERENCE),
            _preserved_input(V8_TRAINING_APPROVAL_REFERENCE),
            _preserved_input(PROMPT_PROFILE_REFERENCE),
            _preserved_input(EVALUATOR_REFERENCE),
            _preserved_input(SUITE_REFERENCE),
        ],
        "remediationBinding": {
            "archiveSha256": ARCHIVE_SHA256,
            "candidateId": CANDIDATE_ID,
            "comparisonIdentity": COMPARISON_IDENTITY,
            "failureAnalysisIdentity": analysis["artifactIdentity"],
            "preservedAutomaticPassCaseIds": analysis["preservedAutomaticPassCaseIds"],
            "targetedCaseIds": analysis["targetedCaseIds"],
        },
        "requestId": "P7-T4-RESEARCH-REMEDIATION-V9-GOVERNANCE-AMENDMENT-REQUEST-001",
        "requestedScope": {
            "constrainedDecodingRequested": False,
            "datasetV9PreparationRequested": True,
            "evaluatorOrSuiteMutationRequested": False,
            "externalEvaluationExecutionRequested": False,
            "externalTrainingRequested": False,
            "frozenEvaluationContentMutationAllowed": False,
            "newPromptProfileRequested": False,
            "priorEvidenceMutationAllowed": False,
            "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
            "runtimeNormalizationRequested": False,
            "targetDatasetVersion": "9.0.0",
            "trainingDataQualityIdentity": quality["artifactIdentity"],
            "trainingDataQualityReference": (
                "config/p7-t4-research-remediation-governance-v9/"
                "training-data-quality-spec-v9.json"
            ),
        },
        "requiredNextApprovals": [
            "DATASET_V9_PREPARATION_APPROVAL",
            "DATASET_V9_TRAINING_APPROVAL",
            "EXTERNAL_P7_T4_V9_EVALUATION_APPROVAL",
        ],
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
    }
    request["requestIdentity"] = request_identity(request)
    return {
        "failure-analysis-v8.json": analysis,
        "governance-amendment-request.json": request,
        "training-data-quality-spec-v9.json": quality,
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
                {"requestIdentity": request["requestIdentity"], "state": request["status"]},
                sort_keys=True,
            )
        )
        return 0
    except PreparationError as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
