#!/usr/bin/env python3
"""Validate the governed Research remediation-v6 synthetic dataset."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v6.py"


def _load_builder():
    specification = importlib.util.spec_from_file_location(
        "p7_t4_remediation_v6_source_for_validator", BUILDER_PATH
    )
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {BUILDER_PATH}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


BUILDER = _load_builder()
validate_record = BUILDER.validate_record


def validate_checked_in_dataset() -> dict[str, object]:
    BUILDER.write_artifacts(check=True)
    records = BUILDER.build_records()
    findings = [
        finding
        for values in records.values()
        for record in values
        for finding in validate_record(record)
    ]
    if findings:
        raise BUILDER.DatasetBuildError("; ".join(sorted(set(findings))))
    manifest = BUILDER.build_documents()[f"{BUILDER.DATASET_ROOT}/manifest.json"]
    return {
        "datasetIdentity": manifest["datasetIdentity"],
        "recordCounts": manifest["recordCounts"],
        "state": "VALID_PENDING_TRAINING_APPROVAL",
    }


def main() -> int:
    argparse.ArgumentParser().parse_args()
    try:
        print(json.dumps(validate_checked_in_dataset(), sort_keys=True))
        return 0
    except BUILDER.DatasetBuildError as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
