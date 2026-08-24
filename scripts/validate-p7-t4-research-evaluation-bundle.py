#!/usr/bin/env python3
"""Validate the portable P7-T4 Research evaluation bundle fail closed."""
from __future__ import annotations

import argparse
import importlib.util
import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


class BundleValidationError(ValueError):
    pass


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise BundleValidationError(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


BUILDER = _load_module(
    "p7t4_bundle_builder_for_validator",
    ROOT / "scripts" / "build-p7-t4-research-evaluation-bundle.py",
)
def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise BundleValidationError(f"JSON invalid: {path}") from error
    if not isinstance(value, dict):
        raise BundleValidationError(f"JSON object required: {path}")
    return value


def validate_bundle_inventory(bundle_root: Path, manifest: dict[str, Any]) -> None:
    bundle_root = bundle_root.resolve()
    if any(path.is_symlink() for path in bundle_root.rglob("*")):
        raise BundleValidationError("bundle inventory must not contain symbolic links")
    expected = manifest.get("files") if isinstance(manifest, dict) else None
    actual = BUILDER.bundle_inventory(bundle_root)
    if not isinstance(expected, list) or actual != expected:
        raise BundleValidationError("bundle inventory mismatch")
    if manifest.get("bundleIdentity") != BUILDER.manifest_identity(manifest):
        raise BundleValidationError("bundle inventory identity mismatch")


def validate_bundle(bundle_root: Path) -> dict[str, Any]:
    bundle_root = bundle_root.resolve()
    if not bundle_root.is_dir():
        raise BundleValidationError("bundle root unavailable")
    manifest = _load_json(bundle_root / "bundle-manifest.json")
    required = {
        "artifactType", "schemaVersion", "sourceCommit", "candidateId",
        "adapterIdentity", "suite", "files", "bundleIdentity",
    }
    if set(manifest) != required:
        raise BundleValidationError("bundle manifest fields are not closed")
    if (
        manifest.get("artifactType") != "P7-T4-RESEARCH-EVALUATION-BUNDLE"
        or manifest.get("schemaVersion") != "1.0.0"
        or not isinstance(manifest.get("sourceCommit"), str)
        or not re.fullmatch(r"[0-9a-f]{40}", manifest["sourceCommit"])
        or not isinstance(manifest.get("candidateId"), str)
        or not SHA256_PATTERN.fullmatch(manifest["candidateId"])
        or not isinstance(manifest.get("adapterIdentity"), str)
        or not SHA256_PATTERN.fullmatch(manifest["adapterIdentity"])
    ):
        raise BundleValidationError("bundle manifest identity invalid")
    validate_bundle_inventory(bundle_root, manifest)
    paths = {item["path"] for item in manifest["files"]}
    missing = sorted(set(BUILDER.SOURCE_FILES) - paths)
    if missing:
        raise BundleValidationError("bundle governed sources missing: " + ", ".join(missing))
    forbidden = [
        path for path in paths
        if path.startswith("datasets/")
        or "/checkpoints/" in f"/{path.lower()}/"
        or Path(path).name in {"model.safetensors", "pytorch_model.bin"}
        or Path(path).name.startswith("model-")
    ]
    if forbidden:
        raise BundleValidationError("bundle contains prohibited training or base-model payload")
    local_p7t4 = _load_module(
        "p7t4_from_validated_bundle",
        bundle_root / "scripts" / "research-independent-evaluation-p7-t4.py",
    )
    try:
        gate = local_p7t4.preflight(bundle_root, bundle_root / "adapter")
    except local_p7t4.P7T4Error as error:
        raise BundleValidationError(f"P7-T4 preflight failed: {error}") from error
    if (
        manifest.get("candidateId") != gate["candidate"]["candidateId"]
        or manifest.get("adapterIdentity") != gate["candidate"]["adapterIdentity"]
        or manifest.get("suite") != gate["suite"]
    ):
        raise BundleValidationError("bundle preflight identity mismatch")
    return {
        "state": "VALID",
        "bundleIdentity": manifest["bundleIdentity"],
        "candidateId": manifest["candidateId"],
        "suite": manifest["suite"],
        "fileCount": len(manifest["files"]),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle-root", type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        print(json.dumps(validate_bundle(args.bundle_root)))
        return 0
    except (BundleValidationError, OSError, ValueError) as error:
        print(json.dumps({"state": "INVALID", "error": str(error)}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
