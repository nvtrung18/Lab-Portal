#!/usr/bin/env python3
"""Materialize the approved P7-T4 evaluator-v2 and suite-v2 amendment."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
PREPARATION_PATH = (
    ROOT / "scripts" / "build-p7-t4-research-remediation-v5-preparation.py"
)
REQUEST_IDENTITY = "4a9ceb3be319bc2fb96b3d856bfdcb2c6c263a325fab5863f45265b4fe52d93f"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_EVALUATION_APPROVAL_AUTHORITY"
APPROVED_AT = "2026-08-27"
APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v5-evaluator-governance-approval.json"
)
APPROVED_EVALUATOR_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v5/"
    "evaluator-contract-v2.approved.json"
)
APPROVED_SUITE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v5/"
    "evaluation-suite-v2.approved.json"
)


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
    "p7_t4_remediation_v5_preparation_for_finalization", PREPARATION_PATH
)


class FinalizationError(ValueError):
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
        raise FinalizationError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes(
            {key: item for key, item in value.items() if key != "artifactIdentity"}
        )
    )


def suite_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != "suiteDigest"})
    )


def _base_documents() -> dict[str, dict[str, Any]]:
    documents = PREPARATION.build_documents()
    request = documents["governance-amendment-request.json"]
    diagnostics: list[str] = []
    if (
        request.get("requestIdentity") != REQUEST_IDENTITY
        or PREPARATION.request_identity(request) != REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or request.get("approvalAuthority") != APPROVAL_AUTHORITY
        or request.get("currentState", {}).get("trainingAuthorized") is not False
        or request.get("currentState", {}).get("evaluationExecutionAuthorized")
        is not False
        or request.get("currentState", {}).get("runtimeNormalizationAuthorized")
        is not False
    ):
        diagnostics.append("request: exact approved pending identity required")
    for item in request.get("preservedFrozenInputs", []):
        reference = item.get("reference")
        path = ROOT / str(reference)
        if (
            item.get("unchanged") is not True
            or not path.is_file()
            or sha256_bytes(path.read_bytes()) != item.get("sha256")
        ):
            diagnostics.append(f"frozen input changed: {reference}")
    if diagnostics:
        raise FinalizationError(diagnostics)
    return documents


def _approved_evaluator(pending: dict[str, Any]) -> dict[str, Any]:
    document = copy.deepcopy(pending)
    document.update(
        {
            "status": "APPROVED",
            "useAllowed": True,
            "runtimeNormalizationAllowed": False,
            "constrainedDecodingAllowed": False,
            "approvalReference": APPROVAL_REFERENCE,
            "requestIdentity": REQUEST_IDENTITY,
            "artifactIdentity": "",
        }
    )
    document["artifactIdentity"] = artifact_identity(document)
    return document


def _approved_suite(
    pending: dict[str, Any], evaluator: dict[str, Any]
) -> dict[str, Any]:
    document = copy.deepcopy(pending)
    document.update(
        {
            "status": "APPROVED",
            "activationAllowed": True,
            "externalExecutionAllowed": False,
            "approvalReference": APPROVAL_REFERENCE,
            "requestIdentity": REQUEST_IDENTITY,
        }
    )
    document["evaluatorContract"] = {
        "id": evaluator["evaluatorId"],
        "version": evaluator["evaluatorVersion"],
        "identity": evaluator["artifactIdentity"],
        "reference": APPROVED_EVALUATOR_REFERENCE,
    }
    document["suiteDigest"] = suite_identity(document)
    return document


def _approval(
    request: dict[str, Any], evaluator: dict[str, Any], suite: dict[str, Any]
) -> dict[str, Any]:
    document: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V5-EVALUATOR-GOVERNANCE-APPROVAL",
        "schemaVersion": "1.0.0",
        "status": "APPROVED",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "requestIdentity": REQUEST_IDENTITY,
        "requestReference": (
            "config/p7-t4-research-remediation-governance-v5/"
            "governance-amendment-request.json"
        ),
        "approval": {
            "decision": "APPROVED",
            "approvedBy": APPROVAL_AUTHORITY,
            "approvedAt": APPROVED_AT,
        },
        "approvedArtifacts": {
            "pendingEvaluatorIdentity": request["requestedScope"][
                "evaluatorContractIdentity"
            ],
            "evaluatorReference": APPROVED_EVALUATOR_REFERENCE,
            "evaluatorIdentity": evaluator["artifactIdentity"],
            "pendingSuiteIdentity": request["requestedScope"][
                "evaluationSuiteIdentity"
            ],
            "suiteReference": APPROVED_SUITE_REFERENCE,
            "suiteIdentity": suite["suiteDigest"],
            "datasetQualityReference": request["requestedScope"][
                "datasetQualityReference"
            ],
            "datasetQualityIdentity": request["requestedScope"][
                "datasetQualityIdentity"
            ],
        },
        "authorization": {
            "evaluatorV2UseAllowed": True,
            "suiteV2UseAllowed": True,
            "datasetV5PreparationAllowed": True,
            "externalEvaluationExecutionAllowed": False,
            "externalTrainingAllowed": False,
            "runtimeNormalizationAllowed": False,
            "constrainedDecodingAllowed": False,
            "promotionAllowed": False,
            "separateTrainingApprovalRequired": True,
            "separateExternalEvaluationApprovalRequired": True,
        },
        "preservedFrozenInputs": copy.deepcopy(request["preservedFrozenInputs"]),
        "revocation": {"authority": APPROVAL_AUTHORITY, "status": "ACTIVE"},
        "artifactIdentity": "",
    }
    document["artifactIdentity"] = artifact_identity(document)
    return document


def build_documents() -> dict[str, dict[str, Any]]:
    pending = _base_documents()
    request = pending["governance-amendment-request.json"]
    evaluator = _approved_evaluator(pending["evaluator-contract-v2.pending.json"])
    suite = _approved_suite(pending["evaluation-suite-v2.pending.json"], evaluator)
    approval = _approval(request, evaluator, suite)
    documents = {
        APPROVED_EVALUATOR_REFERENCE: evaluator,
        APPROVED_SUITE_REFERENCE: suite,
        APPROVAL_REFERENCE: approval,
    }
    validate_documents(documents)
    return documents


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    expected = {
        APPROVED_EVALUATOR_REFERENCE,
        APPROVED_SUITE_REFERENCE,
        APPROVAL_REFERENCE,
    }
    if set(documents) != expected:
        raise FinalizationError("finalization: exact artifact inventory required")
    evaluator = documents[APPROVED_EVALUATOR_REFERENCE]
    suite = documents[APPROVED_SUITE_REFERENCE]
    approval = documents[APPROVAL_REFERENCE]
    diagnostics: list[str] = []
    if (
        evaluator.get("artifactIdentity") != artifact_identity(evaluator)
        or evaluator.get("status") != "APPROVED"
        or evaluator.get("evaluatorVersion") != "2.0.0"
        or evaluator.get("runtimeNormalizationAllowed") is not False
        or evaluator.get("constrainedDecodingAllowed") is not False
    ):
        diagnostics.append("evaluator: exact approved v2 contract required")
    if (
        suite.get("suiteDigest") != suite_identity(suite)
        or suite.get("status") != "APPROVED"
        or suite.get("suiteVersion") != "2.0.0"
        or suite.get("EVALUATION_ONLY") is not True
        or suite.get("TRAINING_PROHIBITED") is not True
        or suite.get("externalExecutionAllowed") is not False
        or suite.get("evaluatorContract", {}).get("identity")
        != evaluator.get("artifactIdentity")
    ):
        diagnostics.append("suite: exact approved evaluation-only v2 contract required")
    authorization = approval.get("authorization", {})
    if (
        approval.get("artifactIdentity") != artifact_identity(approval)
        or approval.get("requestIdentity") != REQUEST_IDENTITY
        or authorization.get("evaluatorV2UseAllowed") is not True
        or authorization.get("suiteV2UseAllowed") is not True
        or authorization.get("externalEvaluationExecutionAllowed") is not False
        or authorization.get("externalTrainingAllowed") is not False
        or authorization.get("runtimeNormalizationAllowed") is not False
        or authorization.get("promotionAllowed") is not False
    ):
        diagnostics.append("approval: exact fail-closed authorization required")
    for item in approval.get("preservedFrozenInputs", []):
        path = ROOT / str(item.get("reference"))
        if (
            item.get("unchanged") is not True
            or not path.is_file()
            or sha256_bytes(path.read_bytes()) != item.get("sha256")
        ):
            diagnostics.append(f"approval: frozen input changed: {item.get('reference')}")
    if diagnostics:
        raise FinalizationError(diagnostics)


def build_artifacts() -> dict[str, bytes]:
    return {path: json_bytes(value) for path, value in build_documents().items()}


def main() -> int:
    try:
        artifacts = build_artifacts()
        for relative_path, content in artifacts.items():
            path = ROOT / relative_path
            if path.exists():
                raise FinalizationError(f"output already exists: {relative_path}")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        approval = build_documents()[APPROVAL_REFERENCE]
        print(
            json.dumps(
                {
                    "status": approval["status"],
                    "requestIdentity": approval["requestIdentity"],
                    "approvalIdentity": approval["artifactIdentity"],
                    "evaluatorV2UseAllowed": True,
                    "suiteV2UseAllowed": True,
                    "externalTrainingAllowed": False,
                    "externalEvaluationExecutionAllowed": False,
                },
                sort_keys=True,
            )
        )
        return 0
    except FinalizationError as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
