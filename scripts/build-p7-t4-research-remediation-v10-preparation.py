#!/usr/bin/env python3
"""Prepare fail-closed P7-T4 remediation-v10 continuation governance."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v10"

V9_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v9/manifest.approved.json"
)
V9_TRAINING_EVIDENCE_REFERENCE = (
    "evidence/p7-t2-real-training/remediation-v9/real-training-evidence.json"
)
V9_ADAPTER_MANIFEST_REFERENCE = (
    "evidence/p7-t2-real-training/remediation-v9/adapter-manifest.json"
)
V9_OUTPUT_SHA256_REFERENCE = (
    "evidence/p7-t2-real-training/remediation-v9/"
    "p7-t2-research-remediation-v9-output.zip.sha256"
)
V9_EXTERNAL_EVALUATION_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v9-external-evaluation-approval.json"
)
V9_EVALUATION_CONFIG_REFERENCE = (
    "config/p7-t4-research-independent-evaluation-remediation-v9.json"
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

V9_CANDIDATE_ID = (
    "99596dcf3c00c6a25a4c38f01de7f12d917100e539cf4ea777556ef0c0a055c2"
)
V9_ADAPTER_IDENTITY = (
    "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
)
V9_TRAINING_RUN_IDENTITY = (
    "9ee7cd3cb56cb5b18c096ce8764fe14a23d69bdd39ef027a8f1472710b152314"
)
V9_TRAINING_EVIDENCE_IDENTITY = (
    "dc8622368cfc2301625abee45271b5f101eab36db536f184c5f2e9d025317c2a"
)
V9_DATASET_IDENTITY = (
    "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
)
V9_OUTPUT_ARCHIVE_SHA256 = (
    "f6c3e5d4ca52643f8c26941a61407444f7004b5e1e22c08445307821dbee767f"
)
V9_COMPARISON_IDENTITY = (
    "9574856a11645f97d6891362850d2d6145584599fafe32863c644700983ed8c9"
)
V9_AUTOMATIC_ARCHIVE_SHA256 = (
    "04c9fa765454ee9838d644e7a22bf61644b61da248d7b6558f8958c23ba8cf43"
)
V9_HUMAN_REVIEW_ARCHIVE_SHA256 = (
    "fa666f9456cfd1a3b8641b97593a7a3d410dfc67b288e239018237aaa3f80c13"
)
V9_ADAPTER_HUMAN_EVIDENCE_IDENTITY = (
    "2c5944c76cf2d33d8bd814cb78a6c34505df1e9ef778e787fa38443a2247145e"
)
V9_ADAPTER_R01_OUTPUT_DIGEST = (
    "f7d18d5f6081dd92faf2a86e6195056bf51eab5661e7809349f72c35a66bd714"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)
EVALUATOR_IDENTITY = (
    "99230c674b9064f1e06247dedd014f6e3da0714ca679017c07b3d877f1e285d3"
)
SUITE_IDENTITY = (
    "65c87149ec97bf34a04257a80af0cba1114b48fe9702f6b3cacb253b573931a8"
)

TARGET_CASE_IDS = ["E-FUNC-RESEARCH-006"]
REPLAY_CASE_IDS = [
    "E-AUTH-011",
    "E-AUTH-012",
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-INJECT-001",
    "E-INJECT-002",
    "E-INJECT-003",
    "E-ROUTE-002",
    "E-STRUCT-003",
    "E-STRUCT-004",
]


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


def load_json(reference: str) -> dict[str, Any]:
    path = ROOT / reference
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PreparationError(f"cannot load {reference}: {error}") from error
    if not isinstance(value, dict):
        raise PreparationError(f"object required: {reference}")
    return value


def _preserved_input(reference: str) -> dict[str, Any]:
    path = ROOT / reference
    return {
        "reference": reference,
        "sha256": sha256_bytes(path.read_bytes()),
        "unchanged": True,
    }


def _validate_v9_parent() -> None:
    manifest = load_json(V9_MANIFEST_REFERENCE)
    evidence = load_json(V9_TRAINING_EVIDENCE_REFERENCE)
    adapter = load_json(V9_ADAPTER_MANIFEST_REFERENCE)
    approval = load_json(V9_EXTERNAL_EVALUATION_APPROVAL_REFERENCE)
    evaluation = load_json(V9_EVALUATION_CONFIG_REFERENCE)

    sidecar = (ROOT / V9_OUTPUT_SHA256_REFERENCE).read_text(
        encoding="ascii"
    ).split()[0]
    if sidecar != V9_OUTPUT_ARCHIVE_SHA256:
        raise PreparationError("exact remediation-v9 output archive sidecar required")

    if (
        manifest.get("datasetIdentity") != V9_DATASET_IDENTITY
        or manifest.get("schemaVersion") != "9.0.0"
        or manifest.get("status") != "APPROVED_FOR_TRAINING_ONLY"
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
        or manifest.get("targetedEvaluationCaseIds") != TARGET_CASE_IDS
    ):
        raise PreparationError("exact approved remediation-v9 dataset required")

    if (
        evidence.get("artifactIdentity") != V9_TRAINING_EVIDENCE_IDENTITY
        or artifact_identity(evidence) != V9_TRAINING_EVIDENCE_IDENTITY
        or evidence.get("candidateId") != V9_CANDIDATE_ID
        or evidence.get("trainingRunIdentity") != V9_TRAINING_RUN_IDENTITY
        or evidence.get("datasetIdentity") != V9_DATASET_IDENTITY
        or evidence.get("realTraining") is not True
        or evidence.get("actualTraining", {}).get(
            "contractHoldoutUsedForOptimization"
        )
        is not False
    ):
        raise PreparationError("exact remediation-v9 real training evidence required")

    if (
        adapter.get("candidateId") != V9_CANDIDATE_ID
        or adapter.get("adapterIdentity") != V9_ADAPTER_IDENTITY
        or adapter.get("trainingRunIdentity") != V9_TRAINING_RUN_IDENTITY
        or adapter.get("adapterDisposition") != "CANDIDATE_ONLY"
        or adapter.get("realTraining") is not True
    ):
        raise PreparationError("exact remediation-v9 adapter manifest required")

    if (
        approval.get("status") != "APPROVED"
        or approval.get("approvedCandidate", {}).get("candidateId")
        != V9_CANDIDATE_ID
        or approval.get("approvedCandidate", {}).get("adapterIdentity")
        != V9_ADAPTER_IDENTITY
        or approval.get("authorization", {}).get("promotionAllowed") is not False
    ):
        raise PreparationError("exact remediation-v9 evaluation approval required")

    if (
        evaluation.get("adapter", {}).get("candidateId") != V9_CANDIDATE_ID
        or evaluation.get("adapter", {}).get("adapterIdentity")
        != V9_ADAPTER_IDENTITY
        or evaluation.get("evaluationContract", {}).get("suiteIdentity")
        != SUITE_IDENTITY
        or evaluation.get("evaluationContract", {}).get("evaluatorIdentity")
        != EVALUATOR_IDENTITY
        or evaluation.get("execution", {}).get("promptProfileReference")
        != PROMPT_PROFILE_REFERENCE
    ):
        raise PreparationError("exact remediation-v9 evaluation contract required")


def _human_finding() -> dict[str, Any]:
    finding: dict[str, Any] = {
        "adapterHumanEvidenceIdentity": V9_ADAPTER_HUMAN_EVIDENCE_IDENTITY,
        "adapterR01OutputDigest": V9_ADAPTER_R01_OUTPUT_DIGEST,
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-HUMAN-QUALITY-FINDING",
        "automaticDecision": "AUTOMATIC_PASS",
        "automaticEvaluationArchiveSha256": V9_AUTOMATIC_ARCHIVE_SHA256,
        "candidateId": V9_CANDIDATE_ID,
        "comparisonIdentity": V9_COMPARISON_IDENTITY,
        "failedDimensions": [
            "TASK_CORRECTNESS",
            "USEFULNESS",
            "VIETNAMESE_QUALITY",
        ],
        "humanFailCount": 1,
        "humanFailedCaseIds": TARGET_CASE_IDS,
        "humanPassCount": len(REPLAY_CASE_IDS),
        "humanPassedCaseIds": REPLAY_CASE_IDS,
        "humanReviewArchiveSha256": V9_HUMAN_REVIEW_ARCHIVE_SHA256,
        "humanReviewState": "COMPLETE",
        "observedDefects": [
            "VIETNAMESE_CONTRACT_BUT_ENGLISH_OUTPUT",
            "LIMITATIONS_NOT_EXPLICITLY_REVIEWED",
            "CONCLUSIONS_NOT_MAPPED_TO_SUPPLIED_EVIDENCE",
        ],
        "reviewBasis": "USER_DELEGATED_HUMAN_REVIEW_OF_RESEARCH_ADAPTER_R01",
        "schemaVersion": "1.0.0",
        "status": "QUALITY_FINDING_REQUIRES_TARGETED_REMEDIATION",
        "targetSemantics": {
            "inventedFactsAllowed": False,
            "limitationsMustBeExplicit": True,
            "responseLanguage": "VI",
            "structuredOutputKind": "RESEARCH_REPORT_REVIEW_DRAFT",
            "suggestionsMustLinkConclusionsToSuppliedEvidence": True,
            "summary": (
                "Báo cáo cần trình bày rõ các giới hạn và liên kết các kết luận "
                "với bằng chứng được cung cấp."
            ),
        },
    }
    finding["artifactIdentity"] = artifact_identity(finding)
    return finding


def _quality_spec(finding: dict[str, Any]) -> dict[str, Any]:
    spec: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-TARGETED-CONTINUATION-SPEC",
        "datasetProposal": {
            "contractHoldoutCounts": {"evaluation": 8},
            "frozenSuiteContentCopiedIntoTraining": False,
            "plannedRecordCounts": {"evaluation": 8, "train": 96, "validation": 20},
            "replayGuardCaseIds": REPLAY_CASE_IDS,
            "replayGuardSelectionCounts": {"train": 48, "validation": 12},
            "splitContentIdsDisjoint": True,
            "syntheticOnly": True,
            "targetedAdditionCounts": {
                "evaluation": 8,
                "train": 48,
                "validation": 8,
            },
            "targetedCaseIds": TARGET_CASE_IDS,
            "v9EvaluationRecordsAllowedForOptimization": False,
            "v9ReplaySourceSplits": "TRAIN_AND_VALIDATION_ONLY",
        },
        "evaluationContract": {
            "evaluatorIdentity": EVALUATOR_IDENTITY,
            "evaluatorReference": EVALUATOR_REFERENCE,
            "evaluatorVersion": "2.0.0",
            "suiteIdentity": SUITE_IDENTITY,
            "suiteReference": SUITE_REFERENCE,
            "suiteVersion": "2.0.0",
        },
        "humanFindingIdentity": finding["artifactIdentity"],
        "humanFindingReference": (
            "config/p7-t4-research-remediation-governance-v10/"
            "human-review-finding-v9.json"
        ),
        "promptProfile": {
            "identity": PROMPT_PROFILE_IDENTITY,
            "reference": PROMPT_PROFILE_REFERENCE,
            "version": "3.0.0",
        },
        "runtimeControls": {
            "constrainedDecodingAllowed": False,
            "runtimeNormalizationAllowed": False,
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_GOVERNANCE_APPROVAL",
        "trainingProposal": {
            "candidateDispositionAfterTraining": "CANDIDATE_ONLY",
            "contractHoldoutSplit": "evaluation",
            "contractHoldoutUsedForEarlyStopping": False,
            "contractHoldoutUsedForOptimization": False,
            "earlyStoppingPatience": 1,
            "earlyStoppingSplit": "validation",
            "learningRateMaximum": 0.00002,
            "maximumRuns": 1,
            "maximumSteps": 48,
            "newCandidateIdentityRequired": True,
            "newTrainingEvidenceRequired": True,
        },
        "warmStartProposal": {
            "authorized": False,
            "freshAdapterInitializationRequired": False,
            "freshBaseModelLoadRequired": True,
            "method": "QLORA_ADAPTER_CONTINUATION",
            "parentAdapterIdentity": V9_ADAPTER_IDENTITY,
            "parentCandidateId": V9_CANDIDATE_ID,
            "parentOutputArchiveSha256": V9_OUTPUT_ARCHIVE_SHA256,
            "parentOutputSha256Reference": V9_OUTPUT_SHA256_REFERENCE,
            "parentTrainingEvidenceIdentity": V9_TRAINING_EVIDENCE_IDENTITY,
            "parentTrainingEvidenceReference": V9_TRAINING_EVIDENCE_REFERENCE,
            "parentTrainingRunIdentity": V9_TRAINING_RUN_IDENTITY,
        },
    }
    spec["artifactIdentity"] = artifact_identity(spec)
    return spec


def build_documents() -> dict[str, dict[str, Any]]:
    _validate_v9_parent()
    finding = _human_finding()
    quality = _quality_spec(finding)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-GOVERNANCE-AMENDMENT-REQUEST",
        "currentState": {
            "datasetMaterializationAuthorized": False,
            "evaluationExecutionAuthorized": False,
            "promotionAllowed": False,
            "trainingAuthorized": False,
            "warmStartAuthorized": False,
        },
        "preservedInputs": [
            _preserved_input(V9_MANIFEST_REFERENCE),
            _preserved_input(V9_TRAINING_EVIDENCE_REFERENCE),
            _preserved_input(V9_ADAPTER_MANIFEST_REFERENCE),
            _preserved_input(V9_OUTPUT_SHA256_REFERENCE),
            _preserved_input(V9_EXTERNAL_EVALUATION_APPROVAL_REFERENCE),
            _preserved_input(V9_EVALUATION_CONFIG_REFERENCE),
            _preserved_input(PROMPT_PROFILE_REFERENCE),
            _preserved_input(EVALUATOR_REFERENCE),
            _preserved_input(SUITE_REFERENCE),
        ],
        "remediationBinding": {
            "candidateId": V9_CANDIDATE_ID,
            "comparisonIdentity": V9_COMPARISON_IDENTITY,
            "humanFindingIdentity": finding["artifactIdentity"],
            "humanPassedCaseIdsToProtect": REPLAY_CASE_IDS,
            "parentAdapterIdentity": V9_ADAPTER_IDENTITY,
            "targetedCaseIds": TARGET_CASE_IDS,
        },
        "requestId": "P7-T4-RESEARCH-REMEDIATION-V10-GOVERNANCE-AMENDMENT-REQUEST-001",
        "requestedScope": {
            "constrainedDecodingRequested": False,
            "datasetV10PreparationRequested": True,
            "evaluatorOrSuiteMutationRequested": False,
            "externalEvaluationExecutionRequested": False,
            "externalTrainingRequested": False,
            "frozenEvaluationContentMutationAllowed": False,
            "newPromptProfileRequested": False,
            "priorEvidenceMutationAllowed": False,
            "runtimeNormalizationRequested": False,
            "targetedContinuationQualityIdentity": quality["artifactIdentity"],
            "targetedContinuationQualityReference": (
                "config/p7-t4-research-remediation-governance-v10/"
                "targeted-continuation-quality-spec-v10.json"
            ),
            "warmStartAmendmentRequested": True,
        },
        "requiredNextApprovals": [
            "DATASET_V10_PREPARATION_AND_WARM_START_AMENDMENT_APPROVAL",
            "DATASET_V10_TARGETED_CONTINUATION_TRAINING_APPROVAL",
            "EXTERNAL_P7_T4_V10_EVALUATION_APPROVAL",
        ],
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
    }
    request["requestIdentity"] = request_identity(request)
    return {
        "governance-amendment-request.json": request,
        "human-review-finding-v9.json": finding,
        "targeted-continuation-quality-spec-v10.json": quality,
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
