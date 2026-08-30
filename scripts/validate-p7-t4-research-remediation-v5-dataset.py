#!/usr/bin/env python3
"""Validate the exact pending P7-T4 remediation v5 dataset artifacts."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v5.py"


class DatasetValidationError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def _load_builder():
    specification = importlib.util.spec_from_file_location(
        "p7_t4_remediation_v5_source_for_validator", BUILDER_PATH
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


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise DatasetValidationError(f"cannot load {path}: {error}") from error
    if not isinstance(value, dict):
        raise DatasetValidationError(f"object required: {path}")
    return value


def _load_jsonl(path: Path) -> list[dict[str, Any]]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
        values = [json.loads(line) for line in lines if line]
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise DatasetValidationError(f"cannot load {path}: {error}") from error
    if any(not isinstance(value, dict) for value in values):
        raise DatasetValidationError(f"object records required: {path}")
    return values


def validate(artifact_root: Path) -> dict[str, Any]:
    artifact_root = artifact_root.resolve()
    diagnostics: list[str] = []
    expected_artifacts = BUILDER.build_artifacts()
    for relative_path, expected in expected_artifacts.items():
        path = artifact_root / relative_path
        try:
            actual = path.read_bytes()
        except OSError:
            diagnostics.append(f"artifact missing: {relative_path}")
            continue
        if actual != expected:
            diagnostics.append(f"artifact bytes mismatch: {relative_path}")
    if diagnostics:
        raise DatasetValidationError(diagnostics)

    dataset_root = artifact_root / "datasets" / "p7-research-synthetic-training-dataset-v5"
    source_root = artifact_root / "datasets" / "p7-t4-research-remediation-source-v5"
    config_root = artifact_root / "config" / "p7-t4-research-remediation-governance-v5"
    source = _load_json(source_root / "source-export.json")
    provenance = _load_json(source_root / "provenance.json")
    contract = _load_json(source_root / "training-contract.json")
    manifest = _load_json(dataset_root / "manifest.json")
    card = _load_json(config_root / "training-dataset-card.pending.json")
    request = _load_json(config_root / "training-approval-request.json")
    records = source.get("records")
    if not isinstance(records, list):
        raise DatasetValidationError("source records required")
    try:
        BUILDER.validate_records(records)
    except BUILDER.SourceBuildError as error:
        raise DatasetValidationError(error.diagnostics) from error

    splits = {
        name: _load_jsonl(dataset_root / f"{name}.jsonl")
        for name in ("train", "validation", "evaluation")
    }
    expected_counts = {"train": 144, "validation": 24, "evaluation": 24}
    if {name: len(values) for name, values in splits.items()} != expected_counts:
        diagnostics.append("dataset split counts mismatch")
    content_ids: dict[str, set[str]] = {}
    for name, values in splits.items():
        ids: set[str] = set()
        for index, value in enumerate(values):
            content_id = value.get("contentId")
            unsigned = {key: item for key, item in value.items() if key != "contentId"}
            expected_content_id = BUILDER.sha256_bytes(BUILDER.canonical_bytes(unsigned))
            if content_id != expected_content_id:
                diagnostics.append(f"{name}/{index}: canonical contentId mismatch")
            if BUILDER.canonical_bytes(value) + b"\n" not in (
                dataset_root / f"{name}.jsonl"
            ).read_bytes():
                diagnostics.append(f"{name}/{index}: canonical JSONL bytes required")
            ids.add(content_id)
        if len(ids) != len(values):
            diagnostics.append(f"{name}: duplicate contentId")
        content_ids[name] = ids
    if (
        content_ids["train"] & content_ids["validation"]
        or content_ids["train"] & content_ids["evaluation"]
        or content_ids["validation"] & content_ids["evaluation"]
    ):
        diagnostics.append("dataset split contentIds must be disjoint")

    if any(
        document.get("trainingAuthorized") is not False
        for document in (provenance, contract, manifest, card, request)
    ):
        diagnostics.append("training must remain unauthorized")
    if (
        contract.get("externalTrainingAllowed") is not False
        or contract.get("runtimeNormalizationAllowed") is not False
        or request.get("externalTrainingAllowed") is not False
        or request.get("runtimeNormalizationAllowed") is not False
    ):
        diagnostics.append("external training and runtime normalization must remain disabled")
    if manifest.get("permitted_purposes") != ["DEVELOPMENT_TEST"]:
        diagnostics.append("dataset purpose must remain DEVELOPMENT_TEST only")
    if diagnostics:
        raise DatasetValidationError(diagnostics)
    return {
        "state": "PREPARED_AWAITING_TRAINING_APPROVAL",
        "datasetVersion": "5.0.0",
        "manifestIdentity": manifest["manifestIdentity"],
        "counts": expected_counts,
        "trainingAuthorized": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifact-root", type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        result = validate(args.artifact_root)
    except DatasetValidationError as error:
        print(json.dumps({"state": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True), file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
