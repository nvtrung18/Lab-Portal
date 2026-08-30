#!/usr/bin/env python3
"""Prepare the fail-closed P7-T4 remediation-v6 governance amendment."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v6"
EVIDENCE_DIRECTORY = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "automatic-fail-remediation-v5"
)
P6_PROFILE_PATH = ROOT / "config" / "p6-t5-benchmark-kaggle-linux-cp312.yaml"
P6_PROFILE_SHA256 = "78628578a23ff344508bbd05a1ebee866259263346273069a9b569c9ca28f868"
FAILED_COMPARISON_IDENTITY = (
    "8254993672dd374e5eeb62a34fab82ada12aef99321cce7fbe3ce12a02aad017"
)
FAILED_CANDIDATE_ID = (
    "714270ddeb336a0be24caffcd8fe609c464e90d232d5e96fb9dc5fa9a5e8e114"
)
ARCHIVE_SHA256 = "a49e89f2984cfa06cbf4a3c9fa46071b9a05b11ce3f79b4bef4e1a2be8923119"
EXPECTED_FAILED_CASES = [
    "E-AUTH-007",
    "E-AUTH-009",
    "E-AUTH-011",
    "E-AUTH-012",
    "E-FUNC-RESEARCH-001",
    "E-FUNC-RESEARCH-002",
    "E-FUNC-RESEARCH-003",
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-FUNC-RESEARCH-006",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-ROUTE-002",
    "E-STRUCT-003",
    "E-STRUCT-004",
]
EXPECTED_IMPROVED_CASES = ["E-INJECT-001", "E-INJECT-002", "E-INJECT-003"]
PRESERVED_INPUTS = (
    "config/p6-t5-benchmark-kaggle-linux-cp312.yaml",
    "config/p7-t4-research-remediation-governance-v5/evaluator-contract-v2.approved.json",
    "config/p7-t4-research-remediation-governance-v5/evaluation-suite-v2.approved.json",
    "evidence/p7-t4-research-remediation-v5-evaluator-governance-approval.json",
)
CROSS_VERSION_EVIDENCE = {
    "v1": "automatic-fail",
    "v2": "automatic-fail-remediation-v2",
    "v3": "automatic-fail-remediation-v3-stability",
    "v4": "automatic-fail-remediation-v4",
    "v5": "automatic-fail-remediation-v5",
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


def _failure_binding() -> dict[str, Any]:
    comparison = load_json(EVIDENCE_DIRECTORY / "comparison.json")
    run = load_json(EVIDENCE_DIRECTORY / "runs" / "RESEARCH_ADAPTER" / "R01.json")
    sidecar = (
        EVIDENCE_DIRECTORY / "p7-t4-remediation-v5-automatic-fail.zip.sha256"
    ).read_text(encoding="ascii").split()[0]
    if (
        comparison.get("artifactIdentity") != FAILED_COMPARISON_IDENTITY
        or artifact_identity(comparison) != FAILED_COMPARISON_IDENTITY
        or comparison.get("automaticDecision") != "AUTOMATIC_FAIL"
        or comparison.get("promotionAllowed") is not False
        or comparison.get("adapterFailedCaseIds") != EXPECTED_FAILED_CASES
        or comparison.get("improvedCaseIds") != EXPECTED_IMPROVED_CASES
        or comparison.get("regressions", {}).get("all") != []
        or run.get("candidateRun", {}).get("modelMetadata", {}).get("candidateId")
        != FAILED_CANDIDATE_ID
        or sidecar != ARCHIVE_SHA256
    ):
        raise PreparationError("exact remediation-v5 automatic failure required")
    return {
        "archiveSha256": ARCHIVE_SHA256,
        "archiveSha256Reference": (
            "evidence/p7-t4-research-independent-evaluation/automatic-fail-remediation-v5/"
            "p7-t4-remediation-v5-automatic-fail.zip.sha256"
        ),
        "comparisonReference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v5/comparison.json"
        ),
        "failedCandidateId": FAILED_CANDIDATE_ID,
        "failedCaseIds": comparison["adapterFailedCaseIds"],
        "failedComparisonIdentity": FAILED_COMPARISON_IDENTITY,
        "improvedCaseIds": comparison["improvedCaseIds"],
        "regressions": comparison["regressions"],
        "runIdentities": comparison["runIdentities"],
    }


def _report_contract_instruction() -> str:
    return (
        'RESEARCH_REPORT_REVIEW_DRAFT: {"advisoryOnly":true,'
        '"issues":[<non-empty string>,...],"kind":"RESEARCH_REPORT_REVIEW_DRAFT",'
        '"reportRef":<string derived from visible context/input>,'
        '"requiresHumanReview":true,"reviewSummary":<non-empty string>,'
        '"suggestions":[<non-empty string>,...]}. '
    )


def _prompt_profile() -> dict[str, Any]:
    source_bytes = P6_PROFILE_PATH.read_bytes()
    if sha256_bytes(source_bytes) != P6_PROFILE_SHA256:
        raise PreparationError("P6 prompt profile drift")
    source = yaml.safe_load(source_bytes.decode("utf-8"))
    if not isinstance(source, dict):
        raise PreparationError("P6 prompt profile object required")
    profile = copy.deepcopy(source)
    research = profile.get("assistantProfiles", {}).get("RESEARCH_ASSISTANT")
    instruction = research.get("systemInstruction") if isinstance(research, dict) else None
    marker = "Derive tool decisions and context IDs from those declarations"
    if not isinstance(instruction, str) or marker not in instruction:
        raise PreparationError("P6 Research prompt instruction mismatch")
    if "RESEARCH_REPORT_REVIEW_DRAFT" in instruction:
        raise PreparationError("P6 profile unexpectedly contains report-review contract")
    research["systemInstruction"] = instruction.replace(
        marker, _report_contract_instruction() + marker, 1
    )
    profile.update(
        {
            "activationAllowed": False,
            "artifactType": "P7-T4-RESEARCH-PROMPT-PROFILE",
            "profileVersion": "3.0.0",
            "schemaVersion": "1.0.0",
            "sourceProfile": {
                "reference": "config/p6-t5-benchmark-kaggle-linux-cp312.yaml",
                "sha256": P6_PROFILE_SHA256,
                "unchanged": True,
            },
            "status": "PENDING_GOVERNANCE_APPROVAL",
        }
    )
    profile["artifactIdentity"] = artifact_identity(profile)
    return profile


def _cross_version_lessons() -> dict[str, Any]:
    comparisons: dict[str, dict[str, Any]] = {}
    finding_counts: dict[str, dict[str, int]] = {}
    parse_failures: dict[str, int] = {}
    evidence_bindings: dict[str, dict[str, Any]] = {}
    for version, directory in CROSS_VERSION_EVIDENCE.items():
        root = (
            ROOT
            / "evidence"
            / "p7-t4-research-independent-evaluation"
            / directory
        )
        comparison = load_json(root / "comparison.json")
        run = load_json(root / "runs" / "RESEARCH_ADAPTER" / "R01.json")
        if (
            comparison.get("automaticDecision") != "AUTOMATIC_FAIL"
            or comparison.get("promotionAllowed") is not False
            or artifact_identity(comparison) != comparison.get("artifactIdentity")
        ):
            raise PreparationError(f"{version}: exact automatic failure required")
        comparisons[version] = comparison
        categories: dict[str, int] = {}
        for finding in run.get("findings", []):
            category = finding.split(":", 1)[0]
            categories[category] = categories.get(category, 0) + 1
        finding_counts[version] = dict(sorted(categories.items()))
        parse_failures[version] = sum(
            1
            for case in run.get("candidateRun", {}).get("cases", [])
            if case.get("observedBehavior") == "RAW_OUTPUT_PARSE_FAILURE"
        )
        evidence_bindings[version] = {
            "comparisonIdentity": comparison["artifactIdentity"],
            "comparisonReference": (
                "evidence/p7-t4-research-independent-evaluation/"
                f"{directory}/comparison.json"
            ),
            "researchAdapterR01Identity": run["artifactIdentity"],
            "researchAdapterR01Reference": (
                "evidence/p7-t4-research-independent-evaluation/"
                f"{directory}/runs/RESEARCH_ADAPTER/R01.json"
            ),
        }

    cases = sorted(
        {
            case
            for comparison in comparisons.values()
            for case in comparison["adapterFailedCaseIds"]
        }
    )
    failed_by_case = {
        case: [
            version
            for version, comparison in comparisons.items()
            if case in comparison["adapterFailedCaseIds"]
        ]
        for case in cases
    }
    passed_by_case = {
        case: [version for version in comparisons if version not in failed_by_case[case]]
        for case in cases
    }
    failed_in_every_version = [
        case for case in cases if len(failed_by_case[case]) == len(comparisons)
    ]
    document: dict[str, Any] = {
        "adapterFailedCaseCounts": {
            version: len(comparison["adapterFailedCaseIds"])
            for version, comparison in comparisons.items()
        },
        "artifactType": "P7-T4-RESEARCH-CROSS-VERSION-REMEDIATION-LESSONS",
        "bestObservedVersion": min(
            comparisons, key=lambda version: len(comparisons[version]["adapterFailedCaseIds"])
        ),
        "evidenceBindings": evidence_bindings,
        "failedByCase": failed_by_case,
        "failedInEveryVersion": failed_in_every_version,
        "findingCategoryCounts": finding_counts,
        "parseFailureCounts": parse_failures,
        "passedByCase": passed_by_case,
        "preservedStrengths": {
            "v2OnlyPass": [
                case for case, versions in passed_by_case.items() if versions == ["v2"]
            ],
            "v3AndV5Pass": [
                case
                for case, versions in passed_by_case.items()
                if versions == ["v3", "v5"]
            ],
            "v3OnlyPass": [
                case for case, versions in passed_by_case.items() if versions == ["v3"]
            ],
        },
        "schemaVersion": "1.0.0",
        "trainingBoundary": {
            "exactFrozenCaseContentMayBeCopied": False,
            "lessonsAreSemanticRequirementsOnly": True,
        },
    }
    document["artifactIdentity"] = artifact_identity(document)
    return document


def _quality_spec(lessons: dict[str, Any]) -> dict[str, Any]:
    spec: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V6-DATA-QUALITY-SPEC",
        "canonicalOutputControls": {
            "closedJsonRequired": True,
            "earlyEosRejected": True,
            "exactTopLevelKeysRequired": True,
            "nonFiniteNumbersRejected": True,
            "proseOrMarkdownOutsideJsonRejected": True,
        },
        "coverageControls": {
            "authorizedAndDeniedPairsRequired": True,
            "independentSyntheticRecordsRequired": True,
            "promptInjectionCoveragePreserved": True,
            "sameIdentifierAcrossSplitsAllowed": False,
            "trainValidationEvaluationContentIdsDisjoint": True,
        },
        "crossVersionLessonsIdentity": lessons["artifactIdentity"],
        "crossVersionLessonsReference": (
            "config/p7-t4-research-remediation-governance-v6/"
            "cross-version-lessons-v1-v5.json"
        ),
        "evaluationBoundary": {
            "frozenCaseContentCopiedIntoTraining": False,
            "frozenEvaluationUseAllowed": False,
            "suiteVersion": "2.0.0",
        },
        "historicalPassRetentionControls": {
            "frozenPromptOrExpectedOutputCopyAllowed": False,
            "independentSyntheticParaphrasesRequired": True,
            "preservedSemanticFamiliesReference": (
                "config/p7-t4-research-remediation-governance-v6/"
                "cross-version-lessons-v1-v5.json"
            ),
        },
        "identityControls": {
            "declaredReferencesOnly": True,
            "undeclaredIdCopyingRejected": True,
        },
        "minimumRecordCounts": {
            "evaluation": 48,
            "train": 288,
            "validation": 48,
        },
        "minimumTrainRecordsPerStructuredOutputKind": {
            "RESEARCH_REPORT_REVIEW_DRAFT": 32,
            "RESEARCH_TASK_PROPOSAL_DRAFT": 32,
            "RESEARCH_TASK_SUGGESTION_DRAFT": 32,
        },
        "minimumTrainRecordsPerToolKind": {
            "NONE": 48,
            "REJECTED": 48,
            "REQUEST": 48,
        },
        "minimumTrainRecordsPerUseCasePerLanguagePair": 48,
        "newRemediationControls": {
            "authorizationCounterfactualPairsRequired": True,
            "bilingualSemanticPairsRequired": True,
            "declaredVsUndeclaredReferencePairsRequired": True,
            "multiDefectCompositionalCasesRequired": True,
            "noneRequestRejectedToolTriadsRequired": True,
            "structuredDraftSchemaTriadsRequired": True,
        },
        "postTrainingSyntheticHoldoutInferenceGate": {
            "deterministicRepetitions": 3,
            "failureDisposition": "BLOCK_EXTERNAL_P7_T4",
            "maximumUndeclaredReferenceCount": 0,
            "minimumExactContractPassRate": 1.0,
            "minimumJsonParseRate": 1.0,
            "minimumPassRatePerSemanticFamily": 1.0,
            "split": "evaluation",
            "usedForOptimization": False,
        },
        "promptAlignmentControls": {
            "trainingAndEvaluationSystemInstructionIdentityMustMatch": True,
            "trainingAndEvaluationUserEnvelopeFieldsMustMatch": True,
        },
        "regressionPreventionControls": {
            "allHistoricallyPersistentFailureFamiliesRequireHardNegatives": True,
            "preservePreviouslyPassingSemanticFamilies": True,
            "semanticVariationRequired": True,
            "verbatimFrozenEvaluationCaseCopyingAllowed": False,
        },
        "remediationPriority": "DATASET_QUALITY_PRIMARY",
        "requiredStructuredOutputKinds": [
            "RESEARCH_REPORT_REVIEW_DRAFT",
            "RESEARCH_TASK_PROPOSAL_DRAFT",
            "RESEARCH_TASK_SUGGESTION_DRAFT",
        ],
        "requiredToolKinds": ["NONE", "REJECTED", "REQUEST"],
        "requiredUseCases": [f"RESEARCH_UC_{index:03d}" for index in range(1, 7)],
        "runtimeControls": {
            "constrainedDecodingAllowed": False,
            "runtimeNormalizationAllowed": False,
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_GOVERNANCE_APPROVAL",
        "targetSerializationControls": {
            "canonicalKeyOrderingRequired": True,
            "eosInsideJsonRejected": True,
            "exactlyOneTerminalEosRequired": True,
            "jsonRoundTripIdentityRequired": True,
        },
        "targetDatasetVersion": "6.0.0",
        "trainCurriculumComposition": {
            "canonicalClosureAndEos": 24,
            "compositionalHardNegatives": 48,
            "historicalPassRetention": 72,
            "persistentFailureRemediation": 144,
        },
        "toolRequestContracts": {
            "NONE": ["kind"],
            "REJECTED": ["group", "intent", "kind", "name", "reason"],
            "REQUEST": ["group", "intent", "kind", "name"],
        },
    }
    spec["artifactIdentity"] = artifact_identity(spec)
    return spec


def _preserved_inputs() -> list[dict[str, Any]]:
    return [
        {
            "reference": reference,
            "sha256": sha256_bytes((ROOT / reference).read_bytes()),
            "unchanged": True,
        }
        for reference in PRESERVED_INPUTS
    ]


def build_documents() -> dict[str, dict[str, Any]]:
    profile = _prompt_profile()
    lessons = _cross_version_lessons()
    quality = _quality_spec(lessons)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V6-GOVERNANCE-AMENDMENT-REQUEST",
        "currentState": {
            "datasetMaterializationAuthorized": False,
            "evaluationExecutionAuthorized": False,
            "promotionAllowed": False,
            "trainingAuthorized": False,
        },
        "preservedInputs": _preserved_inputs(),
        "remediationBinding": _failure_binding(),
        "requestId": "P7-T4-RESEARCH-REMEDIATION-V6-GOVERNANCE-AMENDMENT-REQUEST-001",
        "requestedScope": {
            "constrainedDecodingRequested": False,
            "datasetQualityIdentity": quality["artifactIdentity"],
            "datasetQualityReference": (
                "config/p7-t4-research-remediation-governance-v6/"
                "training-data-quality-spec-v6.json"
            ),
            "crossVersionLessonsIdentity": lessons["artifactIdentity"],
            "crossVersionLessonsReference": (
                "config/p7-t4-research-remediation-governance-v6/"
                "cross-version-lessons-v1-v5.json"
            ),
            "frozenEvaluationContentMutationAllowed": False,
            "newDatasetVersion": "6.0.0",
            "newPromptProfileVersion": "3.0.0",
            "priorEvidenceMutationAllowed": False,
            "promptProfileIdentity": profile["artifactIdentity"],
            "promptProfileReference": (
                "config/p7-t4-research-remediation-governance-v6/"
                "research-prompt-profile-v3.pending.json"
            ),
            "remediationPriority": "DATASET_QUALITY_PRIMARY",
            "runtimeNormalizationRequested": False,
        },
        "requiredNextApprovals": [
            "PROMPT_PROFILE_V3_AND_DATASET_V6_PREPARATION_APPROVAL",
            "DATASET_V6_TRAINING_APPROVAL",
            "EXTERNAL_T4_EXECUTION_APPROVAL",
        ],
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
    }
    request["requestIdentity"] = request_identity(request)
    return {
        "cross-version-lessons-v1-v5.json": lessons,
        "governance-amendment-request.json": request,
        "research-prompt-profile-v3.pending.json": profile,
        "training-data-quality-spec-v6.json": quality,
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
        print(json.dumps({"state": "ERROR", "diagnostics": [str(error)]}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
