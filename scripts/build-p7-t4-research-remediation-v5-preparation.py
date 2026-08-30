#!/usr/bin/env python3
"""Prepare the fail-closed P7-T4 evaluator-v2 and dataset-v5 amendment."""
from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v5"
EVIDENCE_DIRECTORY = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "automatic-fail-remediation-v4"
)
APPROVED_SCHEMA_PATH = (
    ROOT
    / "config"
    / "p7-t4-research-remediation-governance-v4"
    / "structured-output-schema.approved.json"
)
FAILED_CANDIDATE_ID = (
    "e8b72a056c9d0a06268cf5ac825e304b60d4e4aaeac2b0030fc686ac33888241"
)
FAILED_COMPARISON_IDENTITY = (
    "b05b14c56986251b8c3e844ab90494fd877742487b5b107a841e26e37986fe94"
)
ARCHIVE_SHA256 = "3e4dd986624d824a06127c1f06183053af9288f1679b59675e8ae5e733624314"
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
FROZEN_INPUTS = (
    "evals/p6-t4-evaluation-suites.yaml",
    "evals/p6-t4-evaluation-suite.lock.json",
    "evals/p7-t3-research-gap-evaluation-suite.json",
    "evals/p7-t3-research-gap-evaluation-suite.lock.json",
    "evidence/p7-t1c-frozen-evaluation-governance-approval.json",
    "evidence/p7-t3-research-report-eval-governance-approval-v2.json",
)


class PreparationError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


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


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise PreparationError(f"cannot load module: {path}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


def _failure_binding() -> dict[str, Any]:
    comparison = load_json(EVIDENCE_DIRECTORY / "comparison.json")
    diagnostics: list[str] = []
    if (
        comparison.get("artifactIdentity") != FAILED_COMPARISON_IDENTITY
        or artifact_identity(comparison) != FAILED_COMPARISON_IDENTITY
        or comparison.get("automaticDecision") != "AUTOMATIC_FAIL"
        or comparison.get("promotionAllowed") is not False
        or len(comparison.get("adapterFailedCaseIds", [])) != 18
        or comparison.get("improvedCaseIds") != []
        or comparison.get("regressions", {}).get("all") != []
    ):
        diagnostics.append("comparison: exact remediation-v4 automatic failure required")
    run_identities: dict[str, dict[str, str]] = {
        "SHARED_BASE": {},
        "RESEARCH_ADAPTER": {},
    }
    for variant in run_identities:
        for repetition in ("R01", "R02", "R03"):
            run = load_json(EVIDENCE_DIRECTORY / "runs" / variant / f"{repetition}.json")
            identity = run.get("artifactIdentity")
            if (
                run.get("modelVariant") != variant
                or run.get("repetition") != repetition
                or not isinstance(identity, str)
                or SHA256_PATTERN.fullmatch(identity) is None
                or artifact_identity(run) != identity
                or (
                    variant == "RESEARCH_ADAPTER"
                    and run.get("candidateRun", {})
                    .get("modelMetadata", {})
                    .get("candidateId")
                    != FAILED_CANDIDATE_ID
                )
            ):
                diagnostics.append(f"run/{variant}/{repetition}: identity mismatch")
            run_identities[variant][repetition] = identity
    sidecar = EVIDENCE_DIRECTORY / "p7-t4-remediation-v4-automatic-fail.zip.sha256"
    try:
        sidecar_sha256 = sidecar.read_text(encoding="ascii").split()[0]
    except (OSError, UnicodeError, IndexError) as error:
        raise PreparationError(f"cannot load archive sidecar: {error}") from error
    if sidecar_sha256 != ARCHIVE_SHA256:
        diagnostics.append("archive sidecar: exact remediation-v4 SHA-256 required")
    if diagnostics:
        raise PreparationError(diagnostics)
    return {
        "archiveSha256": ARCHIVE_SHA256,
        "archiveSha256Reference": (
            "evidence/p7-t4-research-independent-evaluation/automatic-fail-remediation-v4/"
            "p7-t4-remediation-v4-automatic-fail.zip.sha256"
        ),
        "failedCandidateId": FAILED_CANDIDATE_ID,
        "failedCaseIds": comparison["adapterFailedCaseIds"],
        "failedComparisonIdentity": FAILED_COMPARISON_IDENTITY,
        "comparisonReference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v4/comparison.json"
        ),
        "runIdentities": run_identities,
    }


def _approved_report_contract() -> dict[str, Any]:
    schema = load_json(APPROVED_SCHEMA_PATH)
    research = next(
        (
            item
            for item in schema.get("schemas", [])
            if isinstance(item, dict)
            and item.get("schemaId") == "research-assistant-output-v2"
        ),
        None,
    )
    variants = research.get("schema", {}).get("oneOf", []) if isinstance(research, dict) else []
    report = next(
        (
            item
            for item in variants
            if item.get("properties", {}).get("kind", {}).get("const")
            == "RESEARCH_REPORT_REVIEW_DRAFT"
        ),
        None,
    )
    expected = {
        "kind",
        "reportRef",
        "reviewSummary",
        "issues",
        "suggestions",
        "requiresHumanReview",
        "advisoryOnly",
    }
    if (
        schema.get("status") != "APPROVED_FOR_DATASET_PREPARATION"
        or schema.get("runtimeActivationAllowed") is not False
        or not isinstance(report, dict)
        or set(report.get("required", [])) != expected
        or report.get("additionalProperties") is not False
    ):
        raise PreparationError("approved schema: exact non-active report contract required")
    return copy.deepcopy(report)


def _evaluator_contract() -> dict[str, Any]:
    report = _approved_report_contract()
    contract: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-EVALUATOR-CONTRACT",
        "schemaVersion": "1.0.0",
        "status": "PENDING_GOVERNANCE_APPROVAL",
        "evaluatorId": "P7-T4-RESEARCH-EVALUATOR",
        "evaluatorVersion": "2.0.0",
        "implementationReference": "scripts/validate-p7-t4-research-evaluation-v2.py",
        "basedOn": {
            "evaluatorReference": "scripts/validate-evaluation-suites.py",
            "evaluatorSha256": sha256_bytes(
                (ROOT / "scripts" / "validate-evaluation-suites.py").read_bytes()
            ),
            "approvedSchemaReference": (
                "config/p7-t4-research-remediation-governance-v4/"
                "structured-output-schema.approved.json"
            ),
            "approvedSchemaIdentity": load_json(APPROVED_SCHEMA_PATH)["artifactIdentity"],
        },
        "changeScope": "ADDITIVE_REPORT_REVIEW_VALIDATION_ONLY",
        "toolContracts": {
            "NONE": ["kind"],
            "REQUEST": ["group", "intent", "kind", "name"],
            "REJECTED": ["group", "intent", "kind", "name", "reason"],
        },
        "structuredOutputContracts": [
            {"kind": "RESEARCH_TASK_PROPOSAL_DRAFT"},
            {"kind": "RESEARCH_TASK_SUGGESTION_DRAFT"},
            {
                "kind": "RESEARCH_REPORT_REVIEW_DRAFT",
                "schema": report,
            },
        ],
        "comparisonPolicyUnchanged": True,
        "runtimeNormalizationAllowed": False,
        "constrainedDecodingAllowed": False,
        "artifactIdentity": "",
    }
    contract["artifactIdentity"] = artifact_identity(contract)
    return contract


def _pending_suite(evaluator: dict[str, Any]) -> dict[str, Any]:
    runner = _load_module(
        "p7_t4_runner_for_v5_preparation",
        ROOT / "scripts" / "research-independent-evaluation-p7-t4.py",
    )
    base_suite = runner._load_yaml(ROOT / FROZEN_INPUTS[0])
    gap_suite = load_json(ROOT / FROZEN_INPUTS[2])
    request = load_json(
        ROOT / "config" / "p7-t3-research-report-eval-governance-request.json"
    )
    approval = load_json(ROOT / FROZEN_INPUTS[5])
    suite = runner.compose_research_evaluation_suite(
        base_suite, gap_suite, request, approval
    )
    suite["artifactType"] = "P7-T4-RESEARCH-INDEPENDENT-EVALUATION-SUITE-V2"
    suite["suiteVersion"] = "2.0.0"
    suite["status"] = "PENDING_GOVERNANCE_APPROVAL"
    suite["evaluatorContract"] = {
        "id": evaluator["evaluatorId"],
        "version": evaluator["evaluatorVersion"],
        "identity": evaluator["artifactIdentity"],
        "reference": (
            "config/p7-t4-research-remediation-governance-v5/"
            "evaluator-contract-v2.pending.json"
        ),
    }
    suite["activationAllowed"] = False
    suite["suiteDigest"] = runner._suite_identity(suite)
    return suite


def _quality_spec(evidence: dict[str, Any], evaluator: dict[str, Any]) -> dict[str, Any]:
    specification: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V5-TRAINING-DATA-QUALITY-SPEC",
        "schemaVersion": "1.0.0",
        "state": "QUALITY_SPEC_READY_AWAITING_GOVERNANCE_APPROVAL",
        "assistantKey": "RESEARCH_ASSISTANT",
        "remediationBinding": copy.deepcopy(evidence),
        "plannedDataset": {
            "datasetId": "p7-research-synthetic-training-dataset",
            "proposedVersion": "5.0.0",
            "recordCount": 192,
            "byUseCase": {
                "RESEARCH_UC_003": 48,
                "RESEARCH_UC_004": 48,
                "RESEARCH_UC_005": 48,
                "RESEARCH_UC_006": 48,
            },
            "byLanguage": {"EN": 96, "VI": 96},
            "fullySynthetic": True,
            "evaluationDerived": False,
        },
        "requiredContrasts": [
            "AUTHORIZED_SUCCESS_VS_DENIED",
            "NO_CITATIONS_VS_EXPLICIT_CITATIONS",
            "NONE_VS_REQUEST_VS_REJECTED",
        ],
        "canonicalOutput": {
            "encoding": "UTF-8",
            "jsonSortKeys": True,
            "jsonSeparators": [",", ":"],
            "allowNaN": False,
            "duplicateKeysAllowed": False,
            "topLevelAdditionalPropertiesAllowed": False,
        },
        "closedVocabularies": {
            "toolGroups": sorted(
                [
                    "ADMIN_DRAFT",
                    "ADMIN_READ",
                    "LAB_DRAFT",
                    "LAB_READ",
                    "RESEARCH_DRAFT",
                    "RESEARCH_READ",
                    "UNKNOWN",
                ]
            ),
            "rejectionReasons": sorted(
                [
                    "APPROVAL_REQUIRED",
                    "CONFIRMATION_REQUIRED",
                    "PROHIBITED",
                    "UNKNOWN_TOOL",
                ]
            ),
            "responseMarkers": sorted(
                [
                    "APPROVAL_NEEDED",
                    "CONFIRMATION_NEEDED",
                    "CONTEXT_UNAVAILABLE",
                    "HUMAN_REVIEW_NEEDED",
                    "NO_DISCLOSURE",
                    "NO_EXECUTION",
                ]
            ),
        },
        "structuredOutputContracts": [
            "RESEARCH_TASK_PROPOSAL_DRAFT",
            "RESEARCH_TASK_SUGGESTION_DRAFT",
            "RESEARCH_REPORT_REVIEW_DRAFT",
        ],
        "antiLeakage": {
            "frozenEvaluationDerivedRecordsAllowed": False,
            "exactEvaluationCaseIdsAllowed": False,
            "exactEvaluationPromptsAllowed": False,
            "evaluationTrainingSources": [],
            "semanticContractUseOnly": True,
        },
        "acceptanceCriteria": {
            "canonicalTargetRatePercent": 100,
            "closedToolEnvelopeRatePercent": 100,
            "closedStructuredOutputRatePercent": 100,
            "forbiddenReferenceRatePercent": 0,
            "authorizationContrastRatePercent": 100,
            "developmentFailedCases": 0,
            "frozenEvaluationExecutedBeforeTraining": False,
        },
        "evaluatorContractIdentity": evaluator["artifactIdentity"],
        "trainingPolicy": {
            "freshCandidateRequired": True,
            "reuseFailedCandidateAllowed": False,
            "externalTrainingAllowedBeforeApproval": False,
            "runtimeNormalizationAllowed": False,
            "constrainedDecodingAllowed": False,
        },
        "artifactIdentity": "",
    }
    specification["artifactIdentity"] = artifact_identity(specification)
    return specification


def _amendment_request(
    evidence: dict[str, Any],
    evaluator: dict[str, Any],
    suite: dict[str, Any],
    quality: dict[str, Any],
) -> dict[str, Any]:
    preserved = [
        {
            "reference": relative,
            "sha256": sha256_bytes((ROOT / relative).read_bytes()),
            "unchanged": True,
        }
        for relative in FROZEN_INPUTS
    ]
    request: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V5-GOVERNANCE-AMENDMENT-REQUEST",
        "schemaVersion": "1.0.0",
        "requestId": "P7-T4-RESEARCH-REMEDIATION-V5-GOVERNANCE-AMENDMENT-REQUEST-001",
        "status": "PENDING_USER_APPROVAL",
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "remediationBinding": copy.deepcopy(evidence),
        "preservedFrozenInputs": preserved,
        "requestedScope": {
            "evaluatorContractReference": (
                "config/p7-t4-research-remediation-governance-v5/"
                "evaluator-contract-v2.pending.json"
            ),
            "evaluatorContractIdentity": evaluator["artifactIdentity"],
            "evaluationSuiteReference": (
                "config/p7-t4-research-remediation-governance-v5/"
                "evaluation-suite-v2.pending.json"
            ),
            "evaluationSuiteIdentity": suite["suiteDigest"],
            "datasetQualityReference": (
                "config/p7-t4-research-remediation-governance-v5/"
                "training-data-quality-spec-v5.json"
            ),
            "datasetQualityIdentity": quality["artifactIdentity"],
            "newSuiteVersion": "2.0.0",
            "newEvaluatorVersion": "2.0.0",
            "newDatasetVersion": "5.0.0",
            "frozenEvaluationContentMutationAllowed": False,
            "priorEvidenceMutationAllowed": False,
            "runtimeNormalizationRequested": False,
            "constrainedDecodingRequested": False,
        },
        "currentState": {
            "datasetPreparationAuthorized": True,
            "trainingAuthorized": False,
            "evaluationExecutionAuthorized": False,
            "runtimeNormalizationAuthorized": False,
            "promotionAllowed": False,
        },
        "requiredNextApprovals": [
            "EVALUATOR_V2_AND_SUITE_V2_APPROVAL",
            "DATASET_V5_TRAINING_APPROVAL",
            "EXTERNAL_T4_EXECUTION_APPROVAL",
        ],
        "requestIdentity": "",
    }
    request["requestIdentity"] = request_identity(request)
    return request


def build_documents() -> dict[str, dict[str, Any]]:
    evidence = _failure_binding()
    evaluator = _evaluator_contract()
    suite = _pending_suite(evaluator)
    quality = _quality_spec(evidence, evaluator)
    request = _amendment_request(evidence, evaluator, suite, quality)
    return {
        "evaluator-contract-v2.pending.json": evaluator,
        "evaluation-suite-v2.pending.json": suite,
        "governance-amendment-request.json": request,
        "training-data-quality-spec-v5.json": quality,
    }


def build_artifacts() -> dict[str, bytes]:
    return {name: json_bytes(value) for name, value in build_documents().items()}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=OUTPUT_DIRECTORY)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, content in build_artifacts().items():
        (args.output_dir / name).write_bytes(content)
    print(
        json.dumps(
            {
                "state": "PENDING_USER_APPROVAL",
                "requestIdentity": build_documents()[
                    "governance-amendment-request.json"
                ]["requestIdentity"],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
