#!/usr/bin/env python3
"""Validate the exact prepared or training-approved P7-T4 v4 dataset."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v4.py"
FINALIZER_PATH = ROOT / "scripts" / "finalize-p7-t1c-research-remediation-governance-v4.py"


def _load_builder():
    specification = importlib.util.spec_from_file_location("p7_t4_v4_source_validator", BUILDER_PATH)
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


def _load_finalizer():
    specification = importlib.util.spec_from_file_location("p7_t4_v4_training_finalizer", FINALIZER_PATH)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {FINALIZER_PATH}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


FINALIZER = _load_finalizer()


def _load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"{path}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{path}: object required")
    return value


def validate(artifact_root: Path) -> dict:
    artifact_root = artifact_root.resolve()
    diagnostics: list[str] = []
    expected = BUILDER.build_artifacts()
    for relative_path, expected_bytes in expected.items():
        path = artifact_root / relative_path
        try:
            actual = path.read_bytes()
        except OSError:
            diagnostics.append(f"missing artifact: {relative_path}")
            continue
        if actual != expected_bytes:
            diagnostics.append(f"artifact mismatch: {relative_path}")

    for directory in (
        "datasets/p7-t4-research-remediation-source-v4",
        "datasets/p7-research-synthetic-training-dataset-v4",
    ):
        expected_files = {
            Path(path).name
            for path in expected
            if Path(path).parent.as_posix() == directory
        }
        actual_directory = artifact_root / directory
        actual_files = {
            path.name for path in actual_directory.iterdir() if path.is_file()
        } if actual_directory.is_dir() else set()
        allowed_files = set(expected_files)
        if directory == "datasets/p7-research-synthetic-training-dataset-v4":
            allowed_files.add(Path(FINALIZER.APPROVED_MANIFEST_REFERENCE).name)
        if actual_files not in (expected_files, allowed_files):
            diagnostics.append(f"inventory mismatch: {directory}")

    manifest_path = artifact_root / BUILDER.MANIFEST_REFERENCE
    request_path = artifact_root / BUILDER.TRAINING_REQUEST_REFERENCE
    try:
        manifest = _load_json(manifest_path)
        request = _load_json(request_path)
        if (
            manifest.get("manifestIdentity") != BUILDER.artifact_identity(manifest, "manifestIdentity")
            or manifest.get("lifecycle_status") != "PENDING_TRAINING_APPROVAL"
            or manifest.get("trainingAuthorized") is not False
            or manifest.get("approval_references") != []
        ):
            diagnostics.append("manifest: exact pending fail-closed state required")
        if (
            request.get("requestIdentity") != BUILDER.artifact_identity(request, "requestIdentity")
            or request.get("status") != "PENDING_USER_APPROVAL"
            or request.get("currentState", {}).get("trainingAuthorized") is not False
            or any(request.get(field) is not None for field in ("approval", "approvedBy", "approvedAt"))
        ):
            diagnostics.append("training request: exact pending state required")
    except ValueError as error:
        diagnostics.append(str(error))
        manifest = {}
        request = {}

    approved_manifest_path = artifact_root / FINALIZER.APPROVED_MANIFEST_REFERENCE
    training_approved = approved_manifest_path.is_file()
    approved_manifest: dict = {}
    approval: dict = {}
    if training_approved:
        try:
            approval = _load_json(artifact_root / FINALIZER.TRAINING_APPROVAL_REFERENCE)
            approved_card = _load_json(artifact_root / FINALIZER.APPROVED_CARD_REFERENCE)
            approved_manifest = _load_json(approved_manifest_path)
            FINALIZER.validate_documents(
                {
                    FINALIZER.TRAINING_APPROVAL_REFERENCE: approval,
                    FINALIZER.APPROVED_CARD_REFERENCE: approved_card,
                    FINALIZER.APPROVED_MANIFEST_REFERENCE: approved_manifest,
                }
            )
        except (ValueError, FINALIZER.FinalizationError) as error:
            diagnostics.append(f"training approval: {error}")

    if diagnostics:
        raise ValueError("; ".join(sorted(set(diagnostics))))
    if training_approved:
        return {
            "state": "VALID_TRAINING_APPROVED",
            "datasetVersion": approved_manifest["dataset_version"],
            "manifestIdentity": approved_manifest["checksum"],
            "preparedManifestIdentity": manifest["manifestIdentity"],
            "trainingRequestIdentity": request["requestIdentity"],
            "trainingApprovalIdentity": approval["artifactIdentity"],
            "recordCount": approved_manifest["counts"]["acceptedRecords"],
            "trainingAllowed": True,
            "externalTrainingAllowed": True,
            "evaluationAllowed": False,
            "promotionAllowed": False,
        }
    return {
        "state": "VALID_AWAITING_TRAINING_APPROVAL",
        "datasetVersion": manifest["dataset_version"],
        "manifestIdentity": manifest["manifestIdentity"],
        "trainingRequestIdentity": request["requestIdentity"],
        "recordCount": manifest["counts"]["acceptedRecords"],
        "trainingAllowed": False,
        "externalTrainingAllowed": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-root", type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        print(json.dumps(validate(args.artifact_root), sort_keys=True, separators=(",", ":")))
        return 0
    except (ValueError, RuntimeError) as error:
        print(
            json.dumps(
                {"state": "INVALID", "diagnostics": sorted(set(str(error).split("; ")))},
                sort_keys=True,
                separators=(",", ":"),
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
