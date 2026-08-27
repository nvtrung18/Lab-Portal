#!/usr/bin/env python3
"""Materialize the approved P7-T4 remediation-v6 preparation scope."""
from __future__ import annotations

import argparse
import copy
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PREPARATION_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v6-preparation.py"
REQUEST_IDENTITY = "4e48dea45598bfbb07aefe238b81f08d677375362907ffa2d4a834e30f4e461d"
REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/governance-amendment-request.json"
)
PROFILE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v6/"
    "research-prompt-profile-v3.approved.json"
)
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v6-governance-approval.json"


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


PREPARATION = _load_module("p7_t4_remediation_v6_preparation_for_finalizer", PREPARATION_PATH)
artifact_identity = PREPARATION.artifact_identity
json_bytes = PREPARATION.json_bytes


class FinalizationError(ValueError):
    pass


def build_documents() -> dict[str, dict[str, Any]]:
    pending = PREPARATION.build_documents()
    request = pending["governance-amendment-request.json"]
    pending_profile = pending["research-prompt-profile-v3.pending.json"]
    quality = pending["training-data-quality-spec-v6.json"]
    lessons = pending["cross-version-lessons-v1-v5.json"]
    if (
        request.get("requestIdentity") != REQUEST_IDENTITY
        or PREPARATION.request_identity(request) != REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or request.get("requestedScope", {}).get("remediationPriority")
        != "DATASET_QUALITY_PRIMARY"
        or request.get("requestedScope", {}).get("promptProfileIdentity")
        != pending_profile.get("artifactIdentity")
        or request.get("requestedScope", {}).get("datasetQualityIdentity")
        != quality.get("artifactIdentity")
        or request.get("requestedScope", {}).get("crossVersionLessonsIdentity")
        != lessons.get("artifactIdentity")
    ):
        raise FinalizationError("exact approved remediation-v6 request required")

    profile = copy.deepcopy(pending_profile)
    profile["status"] = "APPROVED"
    profile["approvalBinding"] = {
        "approvalReference": APPROVAL_REFERENCE,
        "requestIdentity": REQUEST_IDENTITY,
    }
    profile["artifactIdentity"] = artifact_identity(profile)

    approval: dict[str, Any] = {
        "approval": {
            "approvedAt": "2026-08-27",
            "approvedBy": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
            "decision": "APPROVED",
        },
        "approvalAuthority": "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY",
        "approvedArtifacts": {
            "crossVersionLessonsIdentity": lessons["artifactIdentity"],
            "crossVersionLessonsReference": (
                "config/p7-t4-research-remediation-governance-v6/"
                "cross-version-lessons-v1-v5.json"
            ),
            "datasetQualityIdentity": quality["artifactIdentity"],
            "datasetQualityReference": (
                "config/p7-t4-research-remediation-governance-v6/"
                "training-data-quality-spec-v6.json"
            ),
            "promptProfileIdentity": profile["artifactIdentity"],
            "promptProfileReference": PROFILE_REFERENCE,
            "pendingPromptProfileIdentity": pending_profile["artifactIdentity"],
        },
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V6-GOVERNANCE-APPROVAL",
        "authorization": {
            "constrainedDecodingAllowed": False,
            "datasetV6PreparationAllowed": True,
            "externalEvaluationExecutionAllowed": False,
            "externalTrainingAllowed": False,
            "promotionAllowed": False,
            "promptProfileV3UseAllowed": True,
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
    return {PROFILE_REFERENCE: profile, APPROVAL_REFERENCE: approval}


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
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
