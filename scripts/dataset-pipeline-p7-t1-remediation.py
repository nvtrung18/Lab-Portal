#!/usr/bin/env python3
"""Materialize the approved P7-T4 remediation training dataset deterministically."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from typing import Any


PIPELINE_SCHEMA_VERSION = "2.0.0"
PIPELINE_VERSION = "2.0.0"
SUPPORTED_SOURCE_RECORD_COUNTS = {"2.0.0": 45, "3.0.0": 270}
SERIALIZATION_VERSION = "canonical-jsonl-utf8-lf-v1"
SPLIT_STRATEGY = "SHA256_CONTENT_BUCKET"
OUTPUT_KEYS = (
    "evalCaseId",
    "response",
    "observedBehavior",
    "observedActionRisk",
    "toolRequest",
    "structuredOutput",
    "referencedContextIds",
)
TRAINING_FIELDS = (
    "schemaVersion",
    "assistantKey",
    "domain",
    "recordType",
    "visibility",
    "useCaseId",
    "trainingPrompt",
    "trainingTarget",
)
SOURCE_RECORD_FIELDS = {
    "schemaVersion",
    "recordId",
    "assistantKey",
    "domain",
    "recordType",
    "visibility",
    "useCaseId",
    "input",
    "payload",
    "legacySemanticReference",
    "trainingPrompt",
    "trainingTarget",
    "governance",
    "metadata",
}
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


class DatasetPipelineError(ValueError):
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
        raise DatasetPipelineError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def jsonl_bytes(records: list[dict[str, Any]]) -> bytes:
    if not records:
        return b""
    return b"\n".join(canonical_bytes(record) for record in records) + b"\n"


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any], field: str) -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    )


def dataset_identity(manifest: dict[str, Any]) -> str:
    return artifact_identity(manifest, "checksum")


def load_document(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise DatasetPipelineError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise DatasetPipelineError(f"{path.name}: object required")
    return value


def _validate_config(config: object) -> dict[str, Any]:
    fields = {
        "schemaVersion",
        "pipelineVersion",
        "cardReference",
        "approvalReference",
        "sourceReference",
        "contractReference",
        "split",
    }
    if not isinstance(config, dict) or set(config) != fields:
        raise DatasetPipelineError("config: exact P7-T1 remediation fields required")
    split = config.get("split")
    if (
        config.get("schemaVersion") != PIPELINE_SCHEMA_VERSION
        or config.get("pipelineVersion") != PIPELINE_VERSION
        or not isinstance(split, dict)
        or set(split)
        != {"strategy", "seed", "trainWeight", "validationWeight", "evaluationWeight"}
        or split.get("strategy") != SPLIT_STRATEGY
        or not isinstance(split.get("seed"), str)
        or not split["seed"]
        or any(
            not isinstance(split.get(field), int) or isinstance(split.get(field), bool)
            for field in ("trainWeight", "validationWeight", "evaluationWeight")
        )
        or sum(split[field] for field in ("trainWeight", "validationWeight", "evaluationWeight"))
        != 100
        or any(split[field] <= 0 for field in ("trainWeight", "validationWeight", "evaluationWeight"))
    ):
        raise DatasetPipelineError("config: valid versioned split configuration required")
    return config


def _validate_approval(
    source: dict[str, Any],
    contract: dict[str, Any],
    card: object,
    approval: object,
    config: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    diagnostics: list[str] = []
    if not isinstance(card, dict) or card.get("approval_status") != "APPROVED":
        diagnostics.append("card: APPROVED required")
    if not isinstance(approval, dict) or approval.get("status") != "APPROVED":
        diagnostics.append("source approval: APPROVED required")
    if diagnostics:
        raise DatasetPipelineError(diagnostics)
    assert isinstance(card, dict)
    assert isinstance(approval, dict)
    source_schema_version = source.get("exportSchemaVersion")
    dataset_version = card.get("dataset_version")
    if approval.get("artifactIdentity") != artifact_identity(approval, "artifactIdentity"):
        diagnostics.append("source approval: artifact identity mismatch")
    expected_reference = config.get("approvalReference")
    if (
        card.get("dataset_id") != "p7-research-synthetic-training-dataset"
        or dataset_version not in SUPPORTED_SOURCE_RECORD_COUNTS
        or source_schema_version != dataset_version
        or card.get("assistant_key") != "RESEARCH_ASSISTANT"
        or card.get("approved_purposes") != ["TRAINING"]
        or card.get("source_permission_status") != "VERIFIED"
        or card.get("approval_references") != [expected_reference]
        or card.get("source_permission_references") != [expected_reference]
        or card.get("category_ids")
        != ["CAT_RESEARCH_ASSIGNED_TASK", "CAT_RESEARCH_DRAFT_CONTEXT"]
    ):
        diagnostics.append("card: exact approved Research remediation scope required")
    source_bytes = json_bytes(source)
    source_identity = sha256_bytes(source_bytes)
    source_block = approval.get("source")
    if (
        not isinstance(source_block, dict)
        or source_block.get("sourceSha256") != source_identity
        or card.get("integrity", {}).get("checksum") != source_identity
        or source.get("source", {}).get("sourcePermissionReference") is not None
        or source.get("source", {}).get("approvalReference") is not None
    ):
        diagnostics.append("source approval: immutable source SHA-256 mismatch")
    if (
        contract.get("contractIdentity") != artifact_identity(contract, "contractIdentity")
        or source.get("source", {}).get("trainingContractIdentity")
        != contract.get("contractIdentity")
        or source_block.get("contractIdentity") != contract.get("contractIdentity")
        or approval.get("scope", {}).get("frozenEvaluationTrainingUseAllowed") is not False
        or approval.get("scope", {}).get("includedUseCases")
        != ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"]
        or approval.get("scope", {}).get("excludedUseCases")
        != {"RESEARCH_UC_006": "TRAINING_PROHIBITED_BY_CURRENT_GOVERNANCE"}
    ):
        diagnostics.append("source approval: exact contract and use-case scope required")
    if diagnostics:
        raise DatasetPipelineError(diagnostics)
    return card, approval


def prepare_records(
    source: dict[str, Any], contract: dict[str, Any]
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    records = source.get("records")
    source_schema_version = source.get("exportSchemaVersion")
    expected_count = SUPPORTED_SOURCE_RECORD_COUNTS.get(source_schema_version)
    if not isinstance(records, list) or expected_count is None or len(records) != expected_count:
        raise DatasetPipelineError("source: exact versioned record inventory required")
    accepted: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    seen: set[str] = set()
    allowed_use_cases = set(contract.get("scope", {}).get("includedUseCases", []))
    for index, record in enumerate(records):
        diagnostics: list[str] = []
        if not isinstance(record, dict) or set(record) != SOURCE_RECORD_FIELDS:
            diagnostics.append("closed source record required")
        else:
            prompt = record.get("trainingPrompt")
            target = record.get("trainingTarget")
            if (
                record.get("schemaVersion") != source_schema_version
                or record.get("assistantKey") != "RESEARCH_ASSISTANT"
                or record.get("domain") != "RESEARCH"
                or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
                or record.get("useCaseId") not in allowed_use_cases
                or record.get("metadata", {}).get("synthetic") is not True
                or record.get("metadata", {}).get("evaluationDerived") is not False
                or not isinstance(prompt, dict)
                or not isinstance(target, dict)
                or set(target) != set(OUTPUT_KEYS)
                or prompt.get("evalCaseId") != target.get("evalCaseId")
            ):
                diagnostics.append("contract-aligned independent Research record required")
        if diagnostics:
            rejected.append({"sourceIndex": index, "diagnostics": diagnostics})
            continue
        materialized = {
            field: copy.deepcopy(record[field]) for field in TRAINING_FIELDS
        }
        content_id = sha256_bytes(canonical_bytes(materialized))
        if content_id in seen:
            rejected.append({"sourceIndex": index, "diagnostics": ["duplicate training content"]})
            continue
        seen.add(content_id)
        materialized["contentId"] = content_id
        accepted.append(materialized)
    return sorted(accepted, key=lambda item: item["contentId"]), rejected


def split_records(
    records: list[dict[str, Any]], split: dict[str, Any]
) -> dict[str, list[dict[str, Any]]]:
    weights = (
        ("train", split["trainWeight"]),
        ("validation", split["validationWeight"]),
        ("evaluation", split["evaluationWeight"]),
    )
    result = {name: [] for name, _ in weights}
    seed = split["seed"].encode("utf-8")
    for record in records:
        bucket = int.from_bytes(
            hashlib.sha256(seed + b"\n" + record["contentId"].encode("ascii")).digest()[:8],
            "big",
        ) % 100
        upper = 0
        for name, weight in weights:
            upper += weight
            if bucket < upper:
                result[name].append(record)
                break
    for values in result.values():
        values.sort(key=lambda item: item["contentId"])
    if any(not values for values in result.values()):
        raise DatasetPipelineError("split: train, validation, and evaluation must all be non-empty")
    return result


def _manifest(
    source: dict[str, Any],
    contract: dict[str, Any],
    card: dict[str, Any],
    approval: dict[str, Any],
    config: dict[str, Any],
    artifacts: list[dict[str, Any]],
    counts: dict[str, Any],
) -> dict[str, Any]:
    manifest = {
        "pipeline_schema_version": PIPELINE_SCHEMA_VERSION,
        "pipeline_version": PIPELINE_VERSION,
        "dataset_id": card["dataset_id"],
        "dataset_version": card["dataset_version"],
        "assistant_key": card["assistant_key"],
        "partition": card["partition"],
        "visibility": card["visibility"],
        "category_ids": copy.deepcopy(card["category_ids"]),
        "classification": card["classification"],
        "use_decision": card["use_decision"],
        "model_development_purpose": card["model_development_purpose"],
        "model_development_operation": card["model_development_operation"],
        "approved_purposes": copy.deepcopy(card["approved_purposes"]),
        "permitted_purposes": copy.deepcopy(card["permitted_purposes"]),
        "prohibited_purposes": copy.deepcopy(card["prohibited_purposes"]),
        "approval_status": card["approval_status"],
        "approval_authority": card["approval_authority"],
        "approval_references": copy.deepcopy(card["approval_references"]),
        "source_permission_status": card["source_permission_status"],
        "source_permission_references": copy.deepcopy(card["source_permission_references"]),
        "dataset_steward": card["dataset_steward"],
        "source_data_owner": card["source_data_owner"],
        "lifecycle_status": card["lifecycle_status"],
        "freeze_status": card["freeze_status"],
        "retention": copy.deepcopy(card["retention"]),
        "sanitization": copy.deepcopy(card["sanitization"]),
        "provenance": copy.deepcopy(card["provenance"]),
        "lineage": copy.deepcopy(card["lineage"]),
        "integrity": copy.deepcopy(card["integrity"]),
        "evaluation_freeze_prerequisite": copy.deepcopy(card["evaluation_freeze_prerequisite"]),
        "revocation_reference": card["revocation_reference"],
        "card_reference": config["cardReference"],
        "training_approval_identity": approval["artifactIdentity"],
        "training_contract_reference": config["contractReference"],
        "training_contract_identity": contract["contractIdentity"],
        "source_export": {
            "reference": config["sourceReference"],
            "sha256": approval["source"]["sourceSha256"],
            "contentIdentity": approval["source"]["contentIdentity"],
            "provenanceIdentity": approval["source"]["provenanceIdentity"],
        },
        "split_configuration": copy.deepcopy(config["split"]),
        "pipeline_configuration": copy.deepcopy(config),
        "pipeline_configuration_sha256": sha256_bytes(canonical_bytes(config)),
        "serialization": {
            "format": "JSONL",
            "encoding": "UTF-8",
            "newline": "LF",
            "version": SERIALIZATION_VERSION,
        },
        "counts": counts,
        "artifacts": artifacts,
        "manifest_created_at_reference": "NOT_RECORDED_REPRODUCIBLE_BUILD",
        "checksum_algorithm": "SHA-256",
        "checksum": "",
    }
    manifest["checksum"] = dataset_identity(manifest)
    return manifest


def build_dataset(
    source: dict[str, Any],
    contract: dict[str, Any],
    card: object,
    approval: object,
    config: object,
    output_directory: Path,
) -> dict[str, Any]:
    validated_config = _validate_config(config)
    approved_card, approved = _validate_approval(
        source, contract, card, approval, validated_config
    )
    accepted, rejected = prepare_records(source, contract)
    if rejected:
        raise DatasetPipelineError(
            [
                f"source/{item['sourceIndex']}: {', '.join(item['diagnostics'])}"
                for item in rejected
            ]
        )
    splits = split_records(accepted, validated_config["split"])
    artifact_bytes = {
        "train.jsonl": jsonl_bytes(splits["train"]),
        "validation.jsonl": jsonl_bytes(splits["validation"]),
        "evaluation.jsonl": jsonl_bytes(splits["evaluation"]),
        "rejections.jsonl": jsonl_bytes(rejected),
    }
    artifacts = [
        {
            "filename": filename,
            "recordCount": (
                len(rejected)
                if filename == "rejections.jsonl"
                else len(splits[filename.removesuffix(".jsonl")])
            ),
            "sha256": sha256_bytes(content),
        }
        for filename, content in sorted(artifact_bytes.items())
    ]
    counts = {
        "sourceRecords": len(source["records"]),
        "acceptedRecords": len(accepted),
        "rejectedRecords": len(rejected),
        "duplicatesRemoved": 0,
        "splits": {name: len(values) for name, values in splits.items()},
    }
    manifest = _manifest(
        source,
        contract,
        approved_card,
        approved,
        validated_config,
        artifacts,
        counts,
    )
    artifact_bytes["manifest.json"] = json_bytes(manifest)
    if output_directory.exists():
        raise DatasetPipelineError("output directory must not already exist")
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(
            prefix=f".{output_directory.name}.", dir=output_directory.parent
        ) as name:
            temporary = Path(name)
            for filename, content in artifact_bytes.items():
                (temporary / filename).write_bytes(content)
            os.replace(temporary, output_directory)
    except OSError as error:
        raise DatasetPipelineError(f"output cannot be written: {error}") from error
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--contract", type=Path, required=True)
    parser.add_argument("--card", type=Path, required=True)
    parser.add_argument("--approval", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        manifest = build_dataset(
            load_document(args.source),
            load_document(args.contract),
            load_document(args.card),
            load_document(args.approval),
            load_document(args.config),
            args.output,
        )
        print(
            json.dumps(
                {"status": "MATERIALIZED", "datasetIdentity": manifest["checksum"]},
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except DatasetPipelineError as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
