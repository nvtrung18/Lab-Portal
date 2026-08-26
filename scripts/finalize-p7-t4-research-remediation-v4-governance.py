#!/usr/bin/env python3
"""Finalize the approved P7-T4 v4 amendment without authorizing training."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
PREPARATION_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-v4-preparation.py"
REQUEST_IDENTITY = "acb5c41075b2808c84b98f3179c68cc3519e261c6a8cb9bf06ee8669d2c74ac7"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
APPROVED_AT = "2026-08-26"
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v4-governance-approval.json"
GOVERNANCE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/data-governance-v2.approved.yml"
)
SCHEMA_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/"
    "structured-output-schema.approved.json"
)
PROPOSED_CATEGORY_ID = "CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC"


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


PREPARATION = _load_module("p7_t4_v4_preparation_for_finalization", PREPARATION_PATH)


class FinalizationError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def json_bytes(value: object) -> bytes:
    return canonical_bytes(value) + b"\n"


def yaml_bytes(value: object) -> bytes:
    return yaml.safe_dump(
        value,
        allow_unicode=True,
        sort_keys=False,
        default_flow_style=False,
        width=120,
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != "artifactIdentity"})
    )


def _base_documents() -> dict[str, dict[str, Any]]:
    documents = PREPARATION.build_documents()
    request = documents["data-governance-amendment-request.json"]
    if (
        request.get("requestIdentity") != REQUEST_IDENTITY
        or PREPARATION.request_identity(request) != REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or request.get("approvalEffect", {}).get("authorizeExternalTraining") is not False
    ):
        raise FinalizationError("governance request: exact approved pending request required")
    return documents


def _governance_document(request: dict[str, Any]) -> dict[str, Any]:
    category = {
        "category_id": PROPOSED_CATEGORY_ID,
        "display_label": "Synthetic research report review context",
        "source_partition": "RESEARCH_ASSISTANT",
        "source_data_owner": "RESEARCH_GOVERNANCE_DOMAIN_DATA_OWNER",
        "dataset_steward": "RESEARCH_GOVERNANCE_DATASET_STEWARD",
        "approval_authority": APPROVAL_AUTHORITY,
        "classification": "SENSITIVE",
        "use_decision": "SYNTHETIC_ONLY",
        "permitted_purposes": ["TRAINING", "DEVELOPMENT_TEST"],
        "prohibited_purposes": [],
        "retention_class": "EPHEMERAL_DEVELOPMENT",
        "visibility": "RESEARCH_ASSISTANT_ONLY",
        "sanitization_disposition": "SYNTHETIC_GENERATION_ONLY",
        "use_case_ids": ["RESEARCH_UC_006"],
    }
    preserved = copy.deepcopy(request["preservedCategory"])
    contract = {
        "contract_version": "2.0.0",
        "schema_version": "1.0.0",
        "amendment_mode": "ADDITIVE_VERSIONED_OVERLAY",
        "base_contract_reference": "docs/architecture/ai/data-governance.yml",
        "base_contract_sha256": request["currentGovernance"]["sha256"],
        "preserved_category_assertions": [
            {
                "category_id": preserved["categoryId"],
                "use_decision": preserved["useDecision"],
                "permitted_purposes": preserved["permittedPurposes"],
                "prohibited_purposes": preserved["prohibitedPurposes"],
                "sanitization_disposition": preserved["sanitizationDisposition"],
            }
        ],
        "data_governance_matrix_additions": [category],
        "p6_t1_traceability_overrides": [
            {
                "use_case_id": "RESEARCH_UC_006",
                "assistant_key": "RESEARCH_ASSISTANT",
                "category_ids": [
                    "CAT_RESEARCH_REPORT_METADATA",
                    PROPOSED_CATEGORY_ID,
                ],
            }
        ],
    }
    document: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-DATA-GOVERNANCE-CONTRACT",
        "schemaVersion": "1.0.0",
        "status": "APPROVED_FOR_V4_DATASET_PREPARATION",
        "approvalReference": APPROVAL_REFERENCE,
        "requestIdentity": REQUEST_IDENTITY,
        "basedOn": request["currentGovernance"],
        "contract": contract,
        "artifactIdentity": "",
    }
    document["artifactIdentity"] = artifact_identity(document)
    return document


def _schema_document(pending: dict[str, Any]) -> dict[str, Any]:
    document = copy.deepcopy(pending)
    document.update(
        {
            "status": "APPROVED_FOR_DATASET_PREPARATION",
            "activationAllowed": False,
            "runtimeActivationAllowed": False,
            "approvalReference": APPROVAL_REFERENCE,
            "requestIdentity": REQUEST_IDENTITY,
            "artifactIdentity": "",
        }
    )
    document["artifactIdentity"] = artifact_identity(document)
    return document


def _approval_document(
    request: dict[str, Any], governance: dict[str, Any], schema: dict[str, Any]
) -> dict[str, Any]:
    document: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V4-GOVERNANCE-AMENDMENT-APPROVAL",
        "schemaVersion": "1.0.0",
        "status": "APPROVED",
        "approvalAuthority": APPROVAL_AUTHORITY,
        "requestIdentity": REQUEST_IDENTITY,
        "requestReference": (
            "config/p7-t4-research-remediation-governance-v4/"
            "data-governance-amendment-request.json"
        ),
        "approval": {
            "decision": "APPROVED",
            "approvedBy": APPROVAL_AUTHORITY,
            "approvedAt": APPROVED_AT,
        },
        "approvedArtifacts": {
            "governanceReference": GOVERNANCE_REFERENCE,
            "governanceIdentity": governance["artifactIdentity"],
            "schemaReference": SCHEMA_REFERENCE,
            "schemaIdentity": schema["artifactIdentity"],
            "qualitySpecificationReference": request["qualitySpecification"]["reference"],
            "qualitySpecificationIdentity": request["qualitySpecification"]["identity"],
        },
        "authorization": {
            "governanceMaterializationAllowed": True,
            "datasetPreparationAllowed": True,
            "externalTrainingAllowed": False,
            "evaluationAllowed": False,
            "promotionAllowed": False,
            "runtimeSchemaActivationAllowed": False,
            "separateTrainingApprovalRequired": True,
            "separateEvaluationApprovalRequired": True,
        },
        "scope": copy.deepcopy(request["requestedScope"]),
        "revocation": {"authority": APPROVAL_AUTHORITY, "status": "ACTIVE"},
        "artifactIdentity": "",
    }
    document["artifactIdentity"] = artifact_identity(document)
    return document


def build_documents() -> dict[str, dict[str, Any]]:
    base = _base_documents()
    request = base["data-governance-amendment-request.json"]
    governance = _governance_document(request)
    schema = _schema_document(base["structured-output-schema.pending.json"])
    approval = _approval_document(request, governance, schema)
    documents = {
        GOVERNANCE_REFERENCE: governance,
        SCHEMA_REFERENCE: schema,
        APPROVAL_REFERENCE: approval,
    }
    validate_documents(documents)
    return documents


def validate_documents(documents: dict[str, dict[str, Any]]) -> None:
    diagnostics: list[str] = []
    if set(documents) != {GOVERNANCE_REFERENCE, SCHEMA_REFERENCE, APPROVAL_REFERENCE}:
        raise FinalizationError("finalization: exact artifact inventory required")
    governance = documents[GOVERNANCE_REFERENCE]
    schema = documents[SCHEMA_REFERENCE]
    approval = documents[APPROVAL_REFERENCE]
    if any(value.get("artifactIdentity") != artifact_identity(value) for value in documents.values()):
        diagnostics.append("finalization: canonical artifact identities required")
    categories = {
        item["category_id"]: item
        for item in governance.get("contract", {}).get("data_governance_matrix_additions", [])
    }
    preserved = governance.get("contract", {}).get("preserved_category_assertions", [{}])[0]
    synthetic = categories.get(PROPOSED_CATEGORY_ID, {})
    if (
        preserved.get("category_id") != "CAT_RESEARCH_REPORT_METADATA"
        or preserved.get("use_decision") != "DEFERRED"
        or "TRAINING" not in preserved.get("prohibited_purposes", [])
        or synthetic.get("use_decision") != "SYNTHETIC_ONLY"
    ):
        diagnostics.append("governance: real report deferral and synthetic-only amendment required")
    if (
        schema.get("status") != "APPROVED_FOR_DATASET_PREPARATION"
        or schema.get("runtimeActivationAllowed") is not False
        or approval.get("authorization", {}).get("externalTrainingAllowed") is not False
        or approval.get("authorization", {}).get("datasetPreparationAllowed") is not True
    ):
        diagnostics.append("approval: dataset-preparation-only scope required")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(approval.get("approval", {}).get("approvedAt", ""))):
        diagnostics.append("approval: exact calendar date required")
    if diagnostics:
        raise FinalizationError(diagnostics)


def build_artifacts() -> dict[str, bytes]:
    documents = build_documents()
    return {
        path: yaml_bytes(value) if path.endswith(".yml") else json_bytes(value)
        for path, value in documents.items()
    }


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
                    "datasetPreparationAllowed": True,
                    "trainingAllowed": False,
                },
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except FinalizationError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
