#!/usr/bin/env python3
"""Prepare the fail-closed governance and quality inputs for Research remediation v4."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
GOVERNANCE_PATH = ROOT / "docs" / "architecture" / "ai" / "data-governance.yml"
APPROVED_SCHEMA_PATH = (
    ROOT / "ai-service" / "config" / "schemas" / "structured-output-schemas.json"
)
EVIDENCE_DIRECTORY = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "automatic-fail-remediation-v3-stability"
)
CANONICAL_OUTPUT_DIRECTORY = (
    ROOT / "config" / "p7-t4-research-remediation-governance-v4"
)
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
FAILED_CANDIDATE_ID = (
    "34e3d50b8bf91d27569305fff47247feaf0de487f9b4e78fd94f7ed64dbc62bd"
)
FAILED_COMPARISON_IDENTITY = (
    "489ee4e22d5402ecfed280036e19bdf5dbe23bd41ea6854d1d0a4dc7639d4be4"
)
ARCHIVE_SHA256 = "c5b9167cb6a5daf7c4fa134042e55417dadf8c40c162e891c489b13ce2a81db9"
PROPOSED_CATEGORY_ID = "CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC"
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
EXPECTED_FAILURES = {
    "E-AUTH-007",
    "E-AUTH-009",
    "E-AUTH-011",
    "E-AUTH-012",
    "E-FUNC-RESEARCH-001",
    "E-FUNC-RESEARCH-002",
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-FUNC-RESEARCH-006",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-ROUTE-002",
    "E-STRUCT-003",
    "E-STRUCT-004",
}
FAILURE_GROUPS = {
    "AUTHORIZATION_CONTRAST": [
        "E-AUTH-007",
        "E-AUTH-009",
        "E-AUTH-011",
        "E-AUTH-012",
    ],
    "CLOSED_JSON_ENVELOPE": [
        "E-FUNC-RESEARCH-001",
        "E-FUNC-RESEARCH-002",
        "E-HUMAN-003",
        "E-HUMAN-004",
    ],
    "EXACT_TOOL_ROUTING": [
        "E-AUTH-011",
        "E-AUTH-012",
        "E-FUNC-RESEARCH-002",
        "E-HUMAN-003",
        "E-HUMAN-004",
        "E-ROUTE-002",
    ],
    "DRAFT_RESPONSE_MODE": [
        "E-FUNC-RESEARCH-004",
        "E-FUNC-RESEARCH-005",
        "E-HUMAN-003",
        "E-HUMAN-004",
        "E-ROUTE-002",
        "E-STRUCT-003",
        "E-STRUCT-004",
    ],
    "REPORT_REVIEW_SCHEMA": ["E-FUNC-RESEARCH-006"],
}


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


def request_identity(request: dict[str, Any]) -> str:
    return artifact_identity(request, "requestIdentity")


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PreparationError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise PreparationError(f"{path.name}: object required")
    return value


def load_yaml(path: Path) -> dict[str, Any]:
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError) as error:
        raise PreparationError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise PreparationError(f"{path.name}: object required")
    return value


def _repository_head() -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    value = result.stdout.strip()
    if result.returncode != 0 or not re.fullmatch(r"[0-9a-f]{40}", value):
        raise PreparationError("repository HEAD unavailable")
    return value


def _evidence_binding() -> dict[str, Any]:
    comparison_path = EVIDENCE_DIRECTORY / "comparison.json"
    comparison = load_json(comparison_path)
    diagnostics: list[str] = []
    failed = comparison.get("adapterFailedCaseIds")
    if (
        comparison.get("automaticDecision") != "AUTOMATIC_FAIL"
        or comparison.get("promotionAllowed") is not False
        or comparison.get("artifactIdentity") != FAILED_COMPARISON_IDENTITY
        or comparison.get("artifactIdentity") != artifact_identity(comparison)
        or set(failed or []) != EXPECTED_FAILURES
        or comparison.get("regressions", {}).get("all") != []
    ):
        diagnostics.append("comparison: exact stable automatic-fail evidence required")
    run_identities: dict[str, dict[str, str]] = {
        "SHARED_BASE": {},
        "RESEARCH_ADAPTER": {},
    }
    for variant in run_identities:
        for repetition in ("R01", "R02", "R03"):
            run_path = EVIDENCE_DIRECTORY / "runs" / variant / f"{repetition}.json"
            run = load_json(run_path)
            run_failures = {
                item.get("evalCaseId")
                for item in run.get("automatic", {}).get("automaticReport", [])
                if isinstance(item, dict) and item.get("automaticState") == "FAIL"
            }
            candidate_id_valid = (
                variant != "RESEARCH_ADAPTER"
                or run.get("candidateRun", {}).get("modelMetadata", {}).get("candidateId")
                == FAILED_CANDIDATE_ID
            )
            failures_valid = variant != "RESEARCH_ADAPTER" or run_failures == EXPECTED_FAILURES
            if (
                not candidate_id_valid
                or run.get("modelVariant") != variant
                or run.get("repetition") != repetition
                or not failures_valid
                or not SHA256_PATTERN.fullmatch(str(run.get("artifactIdentity", "")))
                or run.get("artifactIdentity") != artifact_identity(run)
            ):
                diagnostics.append(
                    f"run/{variant}/{repetition}: exact deterministic evidence required"
                )
            run_identities[variant][repetition] = run.get("artifactIdentity")
    expected_run_identities = comparison.get("runIdentities", {})
    for variant, repetitions in run_identities.items():
        if expected_run_identities.get(variant) != list(repetitions.values()):
            diagnostics.append(f"comparison: {variant} run identities mismatch")
    sidecar = EVIDENCE_DIRECTORY / "p7-t4-backup-automatic-fail.zip.sha256"
    try:
        sidecar_value = sidecar.read_text(encoding="ascii").split()[0]
    except (OSError, UnicodeError, IndexError) as error:
        raise PreparationError(f"archive sidecar: cannot load: {error}") from error
    if sidecar_value != ARCHIVE_SHA256:
        diagnostics.append("archive sidecar: exact SHA-256 required")
    if diagnostics:
        raise PreparationError(diagnostics)
    return {
        "failedCandidateId": FAILED_CANDIDATE_ID,
        "failedComparisonIdentity": FAILED_COMPARISON_IDENTITY,
        "comparisonReference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v3-stability/comparison.json"
        ),
        "archiveSha256": ARCHIVE_SHA256,
        "archiveSha256Reference": (
            "evidence/p7-t4-research-independent-evaluation/"
            "automatic-fail-remediation-v3-stability/"
            "p7-t4-backup-automatic-fail.zip.sha256"
        ),
        "runIdentities": run_identities,
        "adapterFailedCaseIds": sorted(EXPECTED_FAILURES),
        "improvedCaseIds": comparison["improvedCaseIds"],
        "regressions": comparison["regressions"],
    }


def _report_category() -> dict[str, Any]:
    governance = load_yaml(GOVERNANCE_PATH).get("contract", {})
    categories = governance.get("data_governance_matrix", [])
    report = next(
        (
            item
            for item in categories
            if isinstance(item, dict)
            and item.get("category_id") == "CAT_RESEARCH_REPORT_METADATA"
        ),
        None,
    )
    if (
        not isinstance(report, dict)
        or report.get("use_decision") != "DEFERRED"
        or "TRAINING" not in report.get("prohibited_purposes", [])
        or report.get("sanitization_disposition") != "DEFERRED_NO_EXPORT"
    ):
        raise PreparationError("governance: exact deferred report category required")
    return report


def _report_review_variant() -> dict[str, Any]:
    bounded_text = {"type": "string", "minLength": 1, "maxLength": 300}
    return {
        "type": "object",
        "required": [
            "kind",
            "reportRef",
            "reviewSummary",
            "issues",
            "suggestions",
            "requiresHumanReview",
            "advisoryOnly",
        ],
        "additionalProperties": False,
        "properties": {
            "kind": {"const": "RESEARCH_REPORT_REVIEW_DRAFT"},
            "reportRef": {
                "type": "string",
                "pattern": "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$",
            },
            "reviewSummary": {"type": "string", "minLength": 1, "maxLength": 800},
            "issues": {
                "type": "array",
                "minItems": 1,
                "maxItems": 5,
                "items": copy.deepcopy(bounded_text),
            },
            "suggestions": {
                "type": "array",
                "minItems": 1,
                "maxItems": 5,
                "items": copy.deepcopy(bounded_text),
            },
            "requiresHumanReview": {"const": True},
            "advisoryOnly": {"const": True},
        },
    }


def _pending_schema() -> dict[str, Any]:
    approved = load_json(APPROVED_SCHEMA_PATH)
    research = next(
        (
            item
            for item in approved.get("schemas", [])
            if isinstance(item, dict)
            and item.get("schemaId") == "research-assistant-output-v1"
        ),
        None,
    )
    if (
        approved.get("status") != "APPROVED"
        or not isinstance(research, dict)
        or len(research.get("schema", {}).get("oneOf", [])) != 2
    ):
        raise PreparationError("runtime schema: exact approved Research v1 bundle required")
    candidate = copy.deepcopy(research)
    candidate["schemaId"] = "research-assistant-output-v2"
    candidate["schemaVersion"] = "2.0.0"
    candidate["schema"]["$id"] = (
        "https://lab-portal.local/schemas/p8/research-structured-draft/2.0.0"
    )
    candidate["schema"]["oneOf"].append(_report_review_variant())
    document: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-STRUCTURED-OUTPUT-SCHEMA-AMENDMENT",
        "schemaVersion": "1.0.0",
        "status": "PENDING_GOVERNANCE_APPROVAL",
        "activationAllowed": False,
        "basedOn": {
            "reference": "ai-service/config/schemas/structured-output-schemas.json",
            "sha256": sha256_bytes(APPROVED_SCHEMA_PATH.read_bytes()),
            "schemaId": "research-assistant-output-v1",
            "schemaVersion": research["schemaVersion"],
        },
        "changeScope": "ADDITIVE_REPORT_REVIEW_VARIANT_ONLY",
        "schemas": [candidate],
        "artifactIdentity": "",
    }
    document["artifactIdentity"] = artifact_identity(document)
    return document


def _quality_spec(evidence: dict[str, Any], schema: dict[str, Any]) -> dict[str, Any]:
    specification: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V4-TRAINING-DATA-QUALITY-SPEC",
        "schemaVersion": "1.0.0",
        "state": "QUALITY_SPEC_READY_AWAITING_GOVERNANCE_APPROVAL",
        "assistantKey": "RESEARCH_ASSISTANT",
        "remediationBinding": copy.deepcopy(evidence),
        "qualityDimensions": list(FAILURE_GROUPS),
        "failureCoverage": {
            "caseIds": sorted(EXPECTED_FAILURES),
            "groups": copy.deepcopy(FAILURE_GROUPS),
            "deterministicAcrossRepetitions": True,
        },
        "plannedDataset": {
            "datasetId": "p7-research-synthetic-training-dataset",
            "proposedVersion": "4.0.0",
            "recordCount": 144,
            "byUseCase": {
                "RESEARCH_UC_003": 36,
                "RESEARCH_UC_004": 36,
                "RESEARCH_UC_005": 36,
                "RESEARCH_UC_006": 36,
            },
            "byLanguage": {"EN": 72, "VI": 72},
            "scenarioMatrixPerUseCase": {
                "authorizedNoTool": 8,
                "authorizedDeclaredTool": 8,
                "authorizationRejected": 6,
                "authorizationNoTool": 2,
                "promptInjectionRejected": 4,
                "unsupportedRouteRejected": 4,
                "nullContext": 4,
            },
            "hardContrastPairsRequired": True,
            "canonicalClosedTargetsRequired": True,
            "toolDecisionKinds": ["NONE", "REQUEST", "REJECTED"],
            "responseModes": [
                "ANSWER",
                "DRAFT_PRESENTATION",
                "SAFE_REFUSAL",
                "NO_CONTEXT_NOTICE",
            ],
        },
        "contract": {
            "topLevelKeys": [
                "evalCaseId",
                "observedActionRisk",
                "observedBehavior",
                "referencedContextIds",
                "response",
                "structuredOutput",
                "toolRequest",
            ],
            "responseKeys": ["language", "markers", "mode", "text"],
            "schemaBundle": "research-assistant-output-v2",
            "schemaCandidateIdentity": schema["artifactIdentity"],
            "duplicateJsonKeysAllowed": False,
            "additionalPropertiesAllowed": False,
        },
        "antiLeakage": {
            "frozenEvaluationDerivedRecordsAllowed": False,
            "exactEvaluationCaseIdsAllowed": False,
            "exactEvaluationPromptsAllowed": False,
            "evaluationTrainingSources": [],
            "semanticContractUseOnly": True,
        },
        "acceptanceCriteria": {
            "sourceValidationState": "PASS",
            "developmentFailedCases": 0,
            "deterministicRepetitions": 3,
            "jsonParseRatePercent": 100,
            "closedSchemaRatePercent": 100,
            "authorizationContrastRatePercent": 100,
            "toolRoutingRatePercent": 100,
            "promptInjectionRegressionCount": 0,
            "frozenEvaluationExecutedBeforeTraining": False,
        },
        "trainingPolicy": {
            "repeatV3DatasetAllowed": False,
            "increaseEpochsWithoutDevelopmentPassAllowed": False,
            "freshCandidateRequired": True,
            "externalTrainingAllowedBeforeApproval": False,
        },
        "artifactIdentity": "",
    }
    specification["artifactIdentity"] = artifact_identity(specification)
    return specification


def _request(
    evidence: dict[str, Any], schema: dict[str, Any], quality: dict[str, Any]
) -> dict[str, Any]:
    report = _report_category()
    request: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V4-GOVERNANCE-AMENDMENT-REQUEST",
        "schemaVersion": "1.0.0",
        "requestId": "P7-T4-RESEARCH-REMEDIATION-V4-GOVERNANCE-AMENDMENT-REQUEST-001",
        "status": "PENDING_USER_APPROVAL",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "currentGovernance": {
            "reference": "docs/architecture/ai/data-governance.yml",
            "sha256": sha256_bytes(GOVERNANCE_PATH.read_bytes()),
            "contractVersion": "1.0.0",
            "amendmentRule": (
                "A governance amendment creates a new governance version and never edits "
                "prior recorded evidence."
            ),
        },
        "preservedCategory": {
            "categoryId": report["category_id"],
            "useDecision": report["use_decision"],
            "permittedPurposes": report["permitted_purposes"],
            "prohibitedPurposes": report["prohibited_purposes"],
            "sanitizationDisposition": report["sanitization_disposition"],
            "unchanged": True,
        },
        "proposedCategory": {
            "categoryId": PROPOSED_CATEGORY_ID,
            "useCaseIds": ["RESEARCH_UC_006"],
            "useDecision": "SYNTHETIC_ONLY",
            "permittedPurposes": ["TRAINING", "DEVELOPMENT_TEST"],
            "prohibitedPurposes": [],
            "sanitizationDisposition": "SYNTHETIC_GENERATION_ONLY",
        },
        "schemaAmendment": {
            "reference": (
                "config/p7-t4-research-remediation-governance-v4/"
                "structured-output-schema.pending.json"
            ),
            "identity": schema["artifactIdentity"],
            "activationAllowed": False,
        },
        "qualitySpecification": {
            "reference": (
                "config/p7-t4-research-remediation-governance-v4/"
                "training-data-quality-spec.json"
            ),
            "identity": quality["artifactIdentity"],
            "plannedDatasetVersion": quality["plannedDataset"]["proposedVersion"],
        },
        "remediationBinding": copy.deepcopy(evidence),
        "currentState": {
            "trainingAuthorized": False,
            "datasetMaterialized": False,
            "schemaActivated": False,
            "governanceAmendmentMaterialized": False,
            "candidatePromotable": False,
        },
        "requestedScope": {
            "assistantKey": "RESEARCH_ASSISTANT",
            "includedUseCases": [
                "RESEARCH_UC_003",
                "RESEARCH_UC_004",
                "RESEARCH_UC_005",
                "RESEARCH_UC_006",
            ],
            "newSyntheticCategoryOnly": True,
            "realReportMetadataUseAllowed": False,
            "productionDataUseAllowed": False,
            "privateResearchDocumentUseAllowed": False,
            "frozenEvaluationTrainingUseAllowed": False,
            "externalSharingAllowed": False,
            "productionPromptingAllowed": False,
        },
        "approvalEffect": {
            "materializeNewGovernanceVersion": True,
            "authorizeV4SourceAndDatasetPreparation": True,
            "authorizeExternalTraining": False,
            "authorizeEvaluation": False,
            "authorizePromotion": False,
            "separateTrainingApprovalRequired": True,
            "separateEvaluationApprovalRequired": True,
        },
        "repositoryBaseCommit": _repository_head(),
        "requester": "P7_T4_REMEDIATION_V4_IMPLEMENTATION_AGENT",
        "approval": None,
        "approvedBy": None,
        "approvedAt": None,
        "requestIdentity": "",
    }
    request["requestIdentity"] = request_identity(request)
    return request


def build_documents() -> dict[str, dict[str, Any]]:
    evidence = _evidence_binding()
    schema = _pending_schema()
    quality = _quality_spec(evidence, schema)
    documents = {
        "data-governance-amendment-request.json": _request(evidence, schema, quality),
        "structured-output-schema.pending.json": schema,
        "training-data-quality-spec.json": quality,
    }
    validate_documents(documents)
    return documents


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    required = {
        "data-governance-amendment-request.json",
        "structured-output-schema.pending.json",
        "training-data-quality-spec.json",
    }
    if set(documents) != required:
        raise PreparationError("preparation packet: exact artifact inventory required")
    request = documents["data-governance-amendment-request.json"]
    schema = documents["structured-output-schema.pending.json"]
    quality = documents["training-data-quality-spec.json"]
    diagnostics: list[str] = []
    expected_request = _request(_evidence_binding(), schema, quality)
    if request != expected_request:
        diagnostics.append("governance request: exact evidence-bound request required")
    if (
        request.get("status") != "PENDING_USER_APPROVAL"
        or request.get("currentState", {}).get("trainingAuthorized") is not False
        or any(request.get(field) is not None for field in ("approval", "approvedBy", "approvedAt"))
        or request.get("requestIdentity") != request_identity(request)
    ):
        diagnostics.append("governance request: pending fail-closed state required")
    if schema.get("artifactIdentity") != artifact_identity(schema):
        diagnostics.append("schema amendment: canonical identity mismatch")
    if quality.get("artifactIdentity") != artifact_identity(quality):
        diagnostics.append("quality specification: canonical identity mismatch")
    report_variants = schema.get("schemas", [{}])[0].get("schema", {}).get("oneOf", [])
    report_variant = next(
        (
            item
            for item in report_variants
            if isinstance(item, dict)
            and item.get("properties", {}).get("kind", {}).get("const")
            == "RESEARCH_REPORT_REVIEW_DRAFT"
        ),
        None,
    )
    if report_variant != _report_review_variant():
        diagnostics.append("schema amendment: exact report-review contract required")
    if set(quality.get("failureCoverage", {}).get("caseIds", [])) != EXPECTED_FAILURES:
        diagnostics.append("quality specification: exact reproducible failure coverage required")
    if (
        quality.get("plannedDataset", {}).get("recordCount") != 144
        or sum(quality.get("plannedDataset", {}).get("byUseCase", {}).values()) != 144
        or sum(
            quality.get("plannedDataset", {})
            .get("scenarioMatrixPerUseCase", {})
            .values()
        )
        != 36
    ):
        diagnostics.append("quality specification: exact focused dataset inventory required")
    rendered = canonical_bytes(documents).lower()
    for forbidden in (
        b"evals/p7-t3-research-gap-evaluation-suite",
        b"evidence/p7-t3-research-report-eval-governance-approval",
    ):
        if forbidden in rendered:
            diagnostics.append("preparation packet: frozen evaluation source reuse is forbidden")
    if diagnostics:
        raise PreparationError(diagnostics)


def build_artifacts() -> dict[str, bytes]:
    return {filename: json_bytes(value) for filename, value in build_documents().items()}


def write_packet(output_directory: Path) -> dict[str, dict[str, Any]]:
    if output_directory.exists():
        raise PreparationError("output directory must not already exist")
    documents = build_documents()
    artifacts = {filename: json_bytes(value) for filename, value in documents.items()}
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(
            prefix=f".{output_directory.name}.", dir=output_directory.parent
        ) as name:
            temporary = Path(name)
            for filename, content in artifacts.items():
                (temporary / filename).write_bytes(content)
            os.replace(temporary, output_directory)
    except OSError as error:
        raise PreparationError(f"output cannot be written: {error}") from error
    return documents


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=CANONICAL_OUTPUT_DIRECTORY)
    args = parser.parse_args()
    try:
        documents = write_packet(args.output)
        request = documents["data-governance-amendment-request.json"]
        print(
            json.dumps(
                {
                    "status": request["status"],
                    "requestIdentity": request["requestIdentity"],
                    "plannedDatasetVersion": "4.0.0",
                    "plannedRecordCount": 144,
                    "trainingAllowed": False,
                    "externalTrainingAllowed": False,
                },
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except PreparationError as error:
        print(
            json.dumps(
                {"diagnostics": error.diagnostics, "status": "ERROR"}, sort_keys=True
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
