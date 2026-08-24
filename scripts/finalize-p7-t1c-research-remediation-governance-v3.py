#!/usr/bin/env python3
"""Finalize the approved P7-T4 remediation v3 request without widening scope."""
from __future__ import annotations

import argparse
import copy
import importlib.util
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
BASE_FINALIZER_PATH = (
    ROOT / "scripts" / "finalize-p7-t1c-research-remediation-governance.py"
)


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {path.name}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


BASE = _load_module("p7_t1c_remediation_v2_for_v3", BASE_FINALIZER_PATH)

TRAINING_REQUEST_PATH = (
    ROOT
    / "config"
    / "p7-t4-research-remediation-governance-v3"
    / "training-approval-request.json"
)
PENDING_CARD_PATH = (
    ROOT
    / "config"
    / "p7-t4-research-remediation-governance-v3"
    / "training-dataset-card.pending.json"
)
SOURCE_EXPORT_PATH = (
    ROOT / "datasets" / "p7-t4-research-remediation-source-v3" / "source-export.json"
)
PROVENANCE_PATH = (
    ROOT / "datasets" / "p7-t4-research-remediation-source-v3" / "provenance.json"
)
TRAINING_CONTRACT_PATH = (
    ROOT / "datasets" / "p7-t4-research-remediation-source-v3" / "training-contract.json"
)
P7T1_CONFIG_PATH = ROOT / "config" / "p7-t1-research-remediation-dataset-pipeline-v3.json"
TRAINING_APPROVAL_REFERENCE = (
    "evidence/p7-t1c-research-remediation-v3-training-governance-approval.json"
)
APPROVED_CARD_REFERENCE = (
    "config/p7-t1c-research-remediation-governance-v3/training-dataset-card.approved.json"
)
MATERIALIZED_DATASET_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v3"
TRAINING_REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v3/training-approval-request.json"
)
TRAINING_REQUEST_IDENTITY = (
    "6b98270d32015aaf1f8f04aa43089a18128baf5fd55a785f675f0d56698851d1"
)
SOURCE_SHA256 = "fa0e0ae6a10379e6e5b16a45a2fb8b1adc5963902b3ad94d6b573103622bb7bf"
CONTENT_IDENTITY = "5f0b65c3026f19e99058c7e15ed180aa304f1d91db8b69195ac828b65932b84c"
PROVENANCE_IDENTITY = "6f7ddabd49be23afccf19911b0ac9498fe4d8c71b4b2544f5a51a279d38e9361"
CONTRACT_IDENTITY = "4431a4dea11dc3e9f420cbc21070abb6d351db95a4507668e5c189f9040643ad"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"


for name, value in {
    "TRAINING_REQUEST_PATH": TRAINING_REQUEST_PATH,
    "PENDING_CARD_PATH": PENDING_CARD_PATH,
    "SOURCE_EXPORT_PATH": SOURCE_EXPORT_PATH,
    "PROVENANCE_PATH": PROVENANCE_PATH,
    "TRAINING_CONTRACT_PATH": TRAINING_CONTRACT_PATH,
    "P7T1_CONFIG_PATH": P7T1_CONFIG_PATH,
    "TRAINING_APPROVAL_REFERENCE": TRAINING_APPROVAL_REFERENCE,
    "APPROVED_CARD_REFERENCE": APPROVED_CARD_REFERENCE,
    "MATERIALIZED_DATASET_REFERENCE": MATERIALIZED_DATASET_REFERENCE,
    "TRAINING_REQUEST_REFERENCE": TRAINING_REQUEST_REFERENCE,
    "TRAINING_REQUEST_IDENTITY": TRAINING_REQUEST_IDENTITY,
    "SOURCE_SHA256": SOURCE_SHA256,
    "CONTENT_IDENTITY": CONTENT_IDENTITY,
    "PROVENANCE_IDENTITY": PROVENANCE_IDENTITY,
    "CONTRACT_IDENTITY": CONTRACT_IDENTITY,
    "APPROVAL_AUTHORITY": APPROVAL_AUTHORITY,
}.items():
    setattr(BASE, name, value)


def _approved_card_v3(pending: dict, approval: dict) -> dict:
    card = copy.deepcopy(pending)
    card.update(
        {
            "title": "Approved bilingual public-contract Research remediation dataset",
            "description": "Independent synthetic UC003-UC005 contract variants approved exclusively for P7-T1C/P7-T2 remediation TRAINING.",
            "permitted_purposes": ["DEVELOPMENT_TEST", "TRAINING"],
            "approved_purposes": ["TRAINING"],
            "source_permission_references": [TRAINING_APPROVAL_REFERENCE],
            "source_permission_status": "VERIFIED",
            "approval_references": [TRAINING_APPROVAL_REFERENCE],
            "approval_status": "APPROVED",
            "lifecycle_status": "APPROVED",
            "created_at_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval/approvedAt",
            "revocation_reference": f"{TRAINING_APPROVAL_REFERENCE}#/revocation",
        }
    )
    card["integrity"]["verified_at_reference"] = (
        f"{TRAINING_APPROVAL_REFERENCE}#/source/sourceSha256"
    )
    card["retention"].update(
        {
            "trigger_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval",
            "evidence_reference": "datasets/p7-t4-research-remediation-source-v3/provenance.json#/governance",
            "start_reference": f"{TRAINING_APPROVAL_REFERENCE}#/approval/approvedAt",
            "recheck_or_expiry_reference": f"{TRAINING_APPROVAL_REFERENCE}#/revocation",
        }
    )
    for decision in card["sanitization"]["field_decisions"]:
        decision["reviewer_reference"] = TRAINING_APPROVAL_REFERENCE
    card["sanitization"]["reviewer_reference"] = TRAINING_APPROVAL_REFERENCE
    return card


BASE._approved_card = _approved_card_v3

FinalizationError = BASE.FinalizationError
load_document = BASE.load_document
canonical_bytes = BASE.canonical_bytes
json_bytes = BASE.json_bytes
sha256_bytes = BASE.sha256_bytes
artifact_identity = BASE.artifact_identity
request_identity = BASE.request_identity
validate_documents = BASE.validate_documents
build_documents = BASE.build_documents
write_documents = BASE.write_documents
finalize_repository = BASE.finalize_repository


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request-identity", required=True)
    parser.add_argument("--approved-by", required=True)
    parser.add_argument("--approved-at", required=True)
    args = parser.parse_args()
    try:
        documents = finalize_repository(
            request_identity=args.request_identity,
            approved_by=args.approved_by,
            approved_at=args.approved_at,
        )
        approval = documents[TRAINING_APPROVAL_REFERENCE]
        print(
            json.dumps(
                {
                    "status": "APPROVED",
                    "requestIdentity": approval["requestIdentity"],
                    "trainingApprovalIdentity": approval["artifactIdentity"],
                    "trainingAllowed": True,
                },
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except FinalizationError as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
