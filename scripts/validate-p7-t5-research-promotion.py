#!/usr/bin/env python3
"""Validate the P7-T5 proposal, exact approval, and physical adapter inventory."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t5-research-promotion-request.py"
APPROVAL_PATH = ROOT / "evidence" / "p7-t5-research-promotion-approval.json"


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


BUILDER = _load_module("p7_t5_promotion_builder_for_validator", BUILDER_PATH)


class PromotionValidationError(ValueError):
    pass


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PromotionValidationError(f"artifact unreadable: {path.name}") from error
    if not isinstance(value, dict):
        raise PromotionValidationError(f"artifact invalid: {path.name}")
    return value


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_adapter_root(manifest: dict[str, Any], adapter_root: Path) -> None:
    try:
        resolved_root = adapter_root.resolve(strict=True)
    except OSError:
        raise PromotionValidationError("adapter root missing") from None
    if not resolved_root.is_dir():
        raise PromotionValidationError("adapter root invalid")
    entries = manifest.get("artifact", {}).get("files")
    if not isinstance(entries, list) or not entries:
        raise PromotionValidationError("adapter inventory missing")

    source_names: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            raise PromotionValidationError("adapter inventory invalid")
        logical_path = entry.get("path")
        if not isinstance(logical_path, str):
            raise PromotionValidationError("adapter inventory invalid")
        parts = PurePosixPath(logical_path).parts
        if len(parts) != 3 or parts[:2] != ("research-assistant", "1.0.0"):
            raise PromotionValidationError("adapter logical path invalid")
        name = parts[-1]
        if name in source_names:
            raise PromotionValidationError("adapter inventory duplicate")
        source_names.add(name)
        try:
            candidate = (resolved_root / name).resolve(strict=True)
        except OSError:
            raise PromotionValidationError("adapter artifact missing") from None
        if not candidate.is_relative_to(resolved_root) or not candidate.is_file():
            raise PromotionValidationError("adapter artifact path invalid")
        expected_size = entry.get("sizeBytes")
        expected_checksum = entry.get("sha256")
        if (
            not isinstance(expected_size, int)
            or expected_size < 0
            or not isinstance(expected_checksum, str)
            or len(expected_checksum) != 64
        ):
            raise PromotionValidationError("adapter checksum metadata invalid")
        if candidate.stat().st_size != expected_size or _sha256(candidate) != expected_checksum:
            raise PromotionValidationError("adapter checksum or size mismatch")


def validate_approval(request: dict[str, Any], approval: dict[str, Any]) -> None:
    authorization = approval.get("authorization", {})
    if (
        approval.get("artifactType") != "P7-T5-RESEARCH-PROMOTION-APPROVAL"
        or approval.get("schemaVersion") != "1.0.0"
        or approval.get("status") != "APPROVED"
        or approval.get("requestIdentity") != request.get("requestIdentity")
        or authorization.get("promotionAllowed") is not True
        or authorization.get("registryMaterializationAllowed") is not True
        or authorization.get("adapterCopyAllowed") is not True
        or authorization.get("servingLoadAllowed") is not False
    ):
        raise PromotionValidationError("exact approval for P7-T5 request required")


def validate(*, adapter_root: Path | None = None) -> dict[str, Any]:
    expected = BUILDER.build_artifacts()
    for relative_path, content in expected.items():
        path = ROOT / relative_path
        if not path.is_file() or path.read_bytes() != content:
            raise PromotionValidationError(f"proposal artifact mismatch: {relative_path}")

    documents = BUILDER.build_documents()
    request = documents["promotion-request.json"]
    manifest = documents["model-manifest.pending.json"]
    if adapter_root is not None:
        validate_adapter_root(manifest, adapter_root)

    if not APPROVAL_PATH.is_file():
        return {
            "candidateId": request["candidateId"],
            "promotionMaterialized": False,
            "requestIdentity": request["requestIdentity"],
            "servingAllowed": False,
            "state": "PENDING_APPROVER_GATE",
        }

    approval = _load_json(APPROVAL_PATH)
    validate_approval(request, approval)
    if adapter_root is None:
        return {
            "candidateId": request["candidateId"],
            "promotionMaterialized": False,
            "requestIdentity": request["requestIdentity"],
            "servingAllowed": False,
            "state": "APPROVED_PENDING_ADAPTER_VERIFICATION",
        }
    return {
        "candidateId": request["candidateId"],
        "promotionMaterialized": False,
        "requestIdentity": request["requestIdentity"],
        "servingAllowed": False,
        "state": "READY_FOR_PROMOTION_MATERIALIZATION",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter-root", type=Path)
    arguments = parser.parse_args()
    try:
        print(json.dumps(validate(adapter_root=arguments.adapter_root), sort_keys=True))
        return 0
    except (PromotionValidationError, BUILDER.PromotionRequestError) as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
