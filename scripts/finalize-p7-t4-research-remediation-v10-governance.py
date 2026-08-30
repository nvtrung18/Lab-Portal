#!/usr/bin/env python3
"""Materialize the approved P7-T4 remediation-v10 preparation scope."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PREPARATION_PATH = (
    ROOT / "scripts" / "build-p7-t4-research-remediation-v10-preparation.py"
)
REQUEST_IDENTITY = (
    "fa0914adea87c54c8fc53ce79738f196045bf3f1e0031cdced28530898ed4df7"
)
REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v10/"
    "governance-amendment-request.json"
)
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v10-governance-approval.json"


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
    "p7_t4_remediation_v10_preparation_for_finalizer", PREPARATION_PATH
)
artifact_identity = PREPARATION.artifact_identity
json_bytes = PREPARATION.json_bytes


class FinalizationError(ValueError):
    pass


def build_documents() -> dict[str, dict[str, Any]]:
    pending = PREPARATION.build_documents()
    request = pending["governance-amendment-request.json"]
    finding = pending["human-review-finding-v9.json"]
    quality = pending["targeted-continuation-quality-spec-v10.json"]
    scope = request.get("requestedScope", {})
    binding = request.get("remediationBinding", {})
    if (
        request.get("requestIdentity") != REQUEST_IDENTITY
        or PREPARATION.request_identity(request) != REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or scope.get("datasetV10PreparationRequested") is not True
        or scope.get("warmStartAmendmentRequested") is not True
        or scope.get("externalTrainingRequested") is not False
        or scope.get("externalEvaluationExecutionRequested") is not False
        or scope.get("evaluatorOrSuiteMutationRequested") is not False
        or scope.get("priorEvidenceMutationAllowed") is not False
        or scope.get("runtimeNormalizationRequested") is not False
        or scope.get("constrainedDecodingRequested") is not False
        or scope.get("targetedContinuationQualityIdentity")
        != quality.get("artifactIdentity")
        or binding.get("humanFindingIdentity") != finding.get("artifactIdentity")
        or binding.get("targetedCaseIds") != PREPARATION.TARGET_CASE_IDS
        or binding.get("humanPassedCaseIdsToProtect")
        != PREPARATION.REPLAY_CASE_IDS
        or binding.get("parentAdapterIdentity")
        != PREPARATION.V9_ADAPTER_IDENTITY
    ):
        raise FinalizationError("exact approved remediation-v10 request required")

    approval: dict[str, Any] = {
        "approval": {
            "approvedAt": "2026-08-30",
            "approvedBy": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
            "decision": "APPROVED",
        },
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "approvedArtifacts": {
            "humanFindingIdentity": finding["artifactIdentity"],
            "humanFindingReference": (
                "config/p7-t4-research-remediation-governance-v10/"
                "human-review-finding-v9.json"
            ),
            "targetedContinuationQualityIdentity": quality["artifactIdentity"],
            "targetedContinuationQualityReference": (
                "config/p7-t4-research-remediation-governance-v10/"
                "targeted-continuation-quality-spec-v10.json"
            ),
        },
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-GOVERNANCE-APPROVAL",
        "authorization": {
            "constrainedDecodingAllowed": False,
            "datasetV10PreparationAllowed": True,
            "evaluatorOrSuiteMutationAllowed": False,
            "externalEvaluationExecutionAllowed": False,
            "externalTrainingAllowed": False,
            "promotionAllowed": False,
            "runtimeNormalizationAllowed": False,
            "separateExternalEvaluationApprovalRequired": True,
            "separateTrainingApprovalRequired": True,
            "v9AdapterWarmStartProposalAllowed": True,
            "v9EvaluationReplayAllowed": False,
            "v9TrainValidationReplaySelectionAllowed": True,
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
    mismatches: list[str] = []
    for relative_path, content in build_artifacts().items():
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
