#!/usr/bin/env python3
"""Validate the governed single-failure Research remediation-v9 dataset."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v9.py"


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


BUILDER = _load_module("p7_t4_remediation_v9_source_for_validator", BUILDER_PATH)
validate_record = BUILDER.validate_record


def validate_checked_in_dataset() -> dict[str, object]:
    artifacts = BUILDER.build_artifacts()
    mismatches = [
        relative_path
        for relative_path, expected in artifacts.items()
        if not (ROOT / relative_path).is_file()
        or (ROOT / relative_path).read_bytes() != expected
    ]
    if mismatches:
        raise BUILDER.DatasetBuildError("artifact mismatch: " + ", ".join(mismatches))

    records = BUILDER.build_records()
    content_ids = {
        split: {record["contentId"] for record in values}
        for split, values in records.items()
    }
    if (
        {split: len(values) for split, values in records.items()}
        != BUILDER.RECORD_COUNTS
        or any(len(content_ids[split]) != len(records[split]) for split in records)
        or content_ids["train"] & content_ids["validation"]
        or content_ids["train"] & content_ids["evaluation"]
        or content_ids["validation"] & content_ids["evaluation"]
    ):
        raise BUILDER.DatasetBuildError("dataset split identity gate failed")

    manifest = BUILDER.build_documents()[f"{BUILDER.DATASET_ROOT}/manifest.json"]
    request = BUILDER.build_documents()[
        f"{BUILDER.CONFIG_ROOT}/training-approval-request.json"
    ]
    if (
        manifest.get("status") != "PENDING_TRAINING_APPROVAL"
        or manifest.get("trainingAuthorized") is not False
        or manifest.get("targetedEvaluationCaseIds") != ["E-FUNC-RESEARCH-006"]
        or request.get("status") != "PENDING_USER_APPROVAL"
        or request.get("trainingAuthorized") is not False
        or request.get("externalTrainingAllowed") is not False
    ):
        raise BUILDER.DatasetBuildError("training approval gate failed")
    return {
        "datasetIdentity": manifest["datasetIdentity"],
        "recordCounts": manifest["recordCounts"],
        "requestIdentity": request["requestIdentity"],
        "state": "VALID_PENDING_TRAINING_APPROVAL",
        "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"],
    }


def main() -> int:
    try:
        print(json.dumps(validate_checked_in_dataset(), sort_keys=True))
        return 0
    except BUILDER.DatasetBuildError as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
