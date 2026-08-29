#!/usr/bin/env python3
"""Materialize the approved P7-T4 remediation-v8 dataset preparation scope."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PREPARATION_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v8-preparation.py"
REQUEST_IDENTITY = "d5f66d6287dbf14c2ca27620705ca4567a13e1af4211b5dceecebcba8fef5889"
REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v8/"
    "governance-amendment-request.json"
)
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v8-governance-approval.json"


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


PREPARATION = _load_module(
    "p7_t4_remediation_v8_preparation_for_finalizer", PREPARATION_PATH
)
artifact_identity = PREPARATION.artifact_identity
json_bytes = PREPARATION.json_bytes


class FinalizationError(ValueError):
    pass


def build_documents() -> dict[str, dict[str, Any]]:
    pending = PREPARATION.build_documents()
    request = pending["governance-amendment-request.json"]
    analysis = pending["failure-analysis-v7.json"]
    quality = pending["training-data-quality-spec-v8.json"]
    requested_scope = request.get("requestedScope", {})
    if (
        request.get("requestIdentity") != REQUEST_IDENTITY
        or PREPARATION.request_identity(request) != REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or requested_scope.get("datasetV8PreparationRequested") is not True
        or requested_scope.get("externalTrainingRequested") is not False
        or requested_scope.get("externalEvaluationExecutionRequested") is not False
        or requested_scope.get("evaluatorOrSuiteMutationRequested") is not False
        or requested_scope.get("newPromptProfileRequested") is not False
        or requested_scope.get("runtimeNormalizationRequested") is not False
        or requested_scope.get("constrainedDecodingRequested") is not False
        or requested_scope.get("trainingDataQualityIdentity")
        != quality.get("artifactIdentity")
        or request.get("remediationBinding", {}).get("failureAnalysisIdentity")
        != analysis.get("artifactIdentity")
        or request.get("remediationBinding", {}).get("targetedFailedCaseIds")
        != ["E-INJECT-001"]
        or len(
            request.get("remediationBinding", {}).get("preservedPassingCaseIds", [])
        )
        != 17
    ):
        raise FinalizationError("exact approved remediation-v8 request required")

    approval: dict[str, Any] = {
        "approval": {
            "approvedAt": "2026-08-29",
            "approvedBy": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
            "decision": "APPROVED",
        },
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "approvedArtifacts": {
            "failureAnalysisIdentity": analysis["artifactIdentity"],
            "failureAnalysisReference": (
                "config/p7-t4-research-remediation-governance-v8/"
                "failure-analysis-v7.json"
            ),
            "trainingDataQualityIdentity": quality["artifactIdentity"],
            "trainingDataQualityReference": (
                "config/p7-t4-research-remediation-governance-v8/"
                "training-data-quality-spec-v8.json"
            ),
        },
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V8-GOVERNANCE-APPROVAL",
        "authorization": {
            "approvedV7RetentionReuseAllowed": True,
            "constrainedDecodingAllowed": False,
            "datasetV8PreparationAllowed": True,
            "evaluatorOrSuiteMutationAllowed": False,
            "externalEvaluationExecutionAllowed": False,
            "externalTrainingAllowed": False,
            "promotionAllowed": False,
            "promptProfileV3ReuseAllowed": True,
            "runtimeNormalizationAllowed": False,
            "separateExternalEvaluationApprovalRequired": True,
            "separateTrainingApprovalRequired": True,
        },
        "preservedInputs": request["preservedInputs"],
        "requestIdentity": REQUEST_IDENTITY,
        "requestReference": REQUEST_REFERENCE,
        "revocation": {
            "authority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
            "status": "ACTIVE",
        },
        "schemaVersion": "1.0.0",
        "status": "APPROVED",
    }
    approval["artifactIdentity"] = artifact_identity(approval)
    return {APPROVAL_REFERENCE: approval}


def build_artifacts() -> dict[str, bytes]:
    return {path: json_bytes(value) for path, value in build_documents().items()}


def write_artifacts(*, check: bool) -> None:
    artifacts = build_artifacts()
    mismatches: list[str] = []
    for relative_path, content in artifacts.items():
        path = ROOT / relative_path
        if check:
            if not path.is_file() or path.read_bytes() != content:
                mismatches.append(relative_path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if mismatches:
        raise FinalizationError("artifact mismatch: " + ", ".join(mismatches))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        write_artifacts(check=arguments.check)
        approval = build_documents()[APPROVAL_REFERENCE]
        print(
            json.dumps(
                {
                    "approvalIdentity": approval["artifactIdentity"],
                    "requestIdentity": approval["requestIdentity"],
                    "state": approval["status"],
                },
                sort_keys=True,
            )
        )
        return 0
    except (FinalizationError, PREPARATION.PreparationError) as error:
        print(
            json.dumps(
                {"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True
            )
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
