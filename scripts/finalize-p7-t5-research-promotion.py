#!/usr/bin/env python3
"""Finalize the approved P7-T5 Research adapter promotion without loading it."""
from __future__ import annotations

import argparse
from copy import deepcopy
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t5-research-promotion-request.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t5-research-promotion.py"
APPROVAL_PATH = ROOT / "evidence" / "p7-t5-research-promotion-approval.json"
ACTIVE_DESCRIPTOR_PATH = ROOT / "ai-service" / "config" / "model-artifacts.json"
ACTIVE_PROFILES_PATH = ROOT / "ai-service" / "config" / "assistant-profiles.json"

OUTPUT_REFERENCES = {
    "model-manifest.json": "config/p7-t5-research-promotion/model-manifest.json",
    "model-registry.json": "config/p7-t5-research-promotion/model-registry.json",
    "rollback-manifest.json": "config/p7-t5-research-promotion/rollback-manifest.json",
    "decision.json": "evidence/p7-t5-research-promotion/decision.json",
    "model-artifacts.json": "ai-service/config/model-artifacts.json",
    "assistant-profiles.json": "ai-service/config/assistant-profiles.json",
}
IMMUTABLE_OUTPUT_NAMES = (
    "model-manifest.json",
    "model-registry.json",
    "rollback-manifest.json",
    "decision.json",
)
DECISION_REFERENCE = OUTPUT_REFERENCES["decision.json"]
REGISTRY_REFERENCE = OUTPUT_REFERENCES["model-registry.json"]
ADAPTER_IDENTIFIER = "research-assistant-adapter"
ADAPTER_VERSION = "1.0.0"


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


BUILDER = _load_module("p7_t5_promotion_builder_for_finalizer", BUILDER_PATH)
VALIDATOR = _load_module("p7_t5_promotion_validator_for_finalizer", VALIDATOR_PATH)


class PromotionFinalizationError(ValueError):
    pass


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PromotionFinalizationError(f"required artifact unreadable: {path.name}") from error
    if not isinstance(value, dict):
        raise PromotionFinalizationError(f"required artifact invalid: {path.name}")
    return value


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _approval_identity(value: dict[str, Any]) -> str:
    document = deepcopy(value)
    document.pop("artifactIdentity", None)
    return hashlib.sha256(BUILDER.canonical_bytes(document)).hexdigest()


def _validate_approval(request: dict[str, Any], approval: dict[str, Any]) -> None:
    try:
        VALIDATOR.validate_approval(request, approval)
    except VALIDATOR.PromotionValidationError as error:
        raise PromotionFinalizationError("exact promotion approval invalid") from error
    authorization = approval.get("authorization", {})
    if (
        approval.get("candidateId") != request.get("candidateId")
        or approval.get("artifactIdentity") != _approval_identity(approval)
        or approval.get("approval", {}).get("decision") != "APPROVED"
        or approval.get("revocation", {}).get("active") is not False
        or authorization.get("researchProfileActivationAllowed") is not True
    ):
        raise PromotionFinalizationError("exact promotion approval invalid")


def _approved_manifest(
    pending: dict[str, Any], approval: dict[str, Any], rollback: dict[str, Any]
) -> dict[str, Any]:
    manifest = deepcopy(pending)
    manifest["approvalStatus"] = "APPROVED"
    manifest["status"] = "APPROVED"
    manifest["approval"] = {
        "identity": approval["artifactIdentity"],
        "reference": "evidence/p7-t5-research-promotion-approval.json",
        "requestIdentity": approval["requestIdentity"],
    }
    manifest["rollback"] = {
        "manifestIdentity": rollback["artifactIdentity"],
        "reference": OUTPUT_REFERENCES["rollback-manifest.json"],
        "version": rollback["version"],
    }
    manifest["artifactIdentity"] = BUILDER.artifact_identity(manifest)
    return manifest


def _approved_rollback(
    pending: dict[str, Any], approval: dict[str, Any]
) -> dict[str, Any]:
    rollback = deepcopy(pending)
    rollback["status"] = "APPROVED"
    rollback["approvalIdentity"] = approval["artifactIdentity"]
    rollback["artifactIdentity"] = BUILDER.artifact_identity(rollback)
    return rollback


def _registry(
    manifest: dict[str, Any], rules: dict[str, Any], approval: dict[str, Any]
) -> dict[str, Any]:
    registry: dict[str, Any] = {
        "approvalIdentity": approval["artifactIdentity"],
        "artifactType": "P7-MODEL-REGISTRY",
        "entries": {
            "ADMIN_ASSISTANT": {
                "assistantKey": "ADMIN_ASSISTANT",
                "status": "NOT_AVAILABLE",
            },
            "LAB_ASSISTANT": {
                "assistantKey": "LAB_ASSISTANT",
                "status": "NOT_AVAILABLE",
            },
            "RESEARCH_ASSISTANT": {
                "adapterIdentity": manifest["adapterIdentity"],
                "assistantKey": "RESEARCH_ASSISTANT",
                "candidateId": manifest["candidateId"],
                "manifestIdentity": manifest["artifactIdentity"],
                "manifestReference": OUTPUT_REFERENCES["model-manifest.json"],
                "servingArtifactIdentity": manifest["artifact"]["servingArtifactIdentity"],
                "status": "APPROVED",
                "version": manifest["version"],
            },
        },
        "rulesIdentity": rules["artifactIdentity"],
        "rulesReference": "config/p7-t5-research-promotion/model-registry-rules.json",
        "schemaVersion": "1.0.0",
        "status": "APPROVED",
        "version": "1.0.0",
    }
    registry["artifactIdentity"] = BUILDER.artifact_identity(registry)
    return registry


def _decision(
    request: dict[str, Any],
    approval: dict[str, Any],
    manifest: dict[str, Any],
    registry: dict[str, Any],
    rollback: dict[str, Any],
) -> dict[str, Any]:
    decision: dict[str, Any] = {
        "approvalIdentity": approval["artifactIdentity"],
        "artifactType": "P7-T5-RESEARCH-PROMOTION-DECISION",
        "assistantKey": "RESEARCH_ASSISTANT",
        "candidateId": request["candidateId"],
        "modelManifestIdentity": manifest["artifactIdentity"],
        "outcome": "ADAPTER_APPROVED",
        "promotionAllowed": True,
        "registryIdentity": registry["artifactIdentity"],
        "requestIdentity": request["requestIdentity"],
        "rollbackManifestIdentity": rollback["artifactIdentity"],
        "schemaVersion": "1.0.0",
        "servingLoadAllowed": False,
        "state": "COMPLETE",
    }
    decision["artifactIdentity"] = BUILDER.artifact_identity(decision)
    return decision


def _active_descriptor(
    current: dict[str, Any], manifest: dict[str, Any], decision: dict[str, Any]
) -> dict[str, Any]:
    descriptor = deepcopy(current)
    physical = {
        "files": [
            {"path": entry["path"], "sha256": entry["sha256"]}
            for entry in manifest["artifact"]["files"]
        ],
        "identity": manifest["artifact"]["servingArtifactIdentity"],
    }
    source = {
        "identity": decision["artifactIdentity"],
        "outcome": decision["outcome"],
        "reference": DECISION_REFERENCE,
    }
    descriptor["assistantAdapters"]["RESEARCH_ASSISTANT"] = {
        "assistantKey": "RESEARCH_ASSISTANT",
        "identifier": ADAPTER_IDENTIFIER,
        "version": ADAPTER_VERSION,
        "baseModelIdentifier": manifest["baseModel"]["identifier"],
        "baseModelRevision": manifest["baseModel"]["revision"],
        "status": "APPROVED",
        "artifact": physical,
        "sourceDecision": source,
        "sourceRegistryReference": REGISTRY_REFERENCE,
    }
    descriptor["sourceDecision"] = source
    descriptor["sourceRegistryReference"] = REGISTRY_REFERENCE
    return descriptor


def _active_profiles(
    current: dict[str, Any], manifest: dict[str, Any]
) -> dict[str, Any]:
    profiles = deepcopy(current)
    research = profiles["profiles"]["RESEARCH_ASSISTANT"]
    research["profileVersion"] = "2.0.0"
    research["modelProfile"]["profileVersion"] = "2.0.0"
    research["adapter"] = {
        "identifier": ADAPTER_IDENTIFIER,
        "version": ADAPTER_VERSION,
        "artifactChecksum": manifest["artifact"]["servingArtifactIdentity"],
    }
    # The P7-T5 approval materializes metadata only; P8-T3 owns runtime loading.
    research["modelProfile"]["servingMode"] = "METADATA_ONLY"
    return profiles


def build_documents(
    *, approval: dict[str, Any] | None = None
) -> dict[str, dict[str, Any]]:
    proposals = BUILDER.build_documents()
    request = proposals["promotion-request.json"]
    approval = deepcopy(approval) if approval is not None else _load_json(APPROVAL_PATH)
    _validate_approval(request, approval)

    rollback = _approved_rollback(proposals["rollback-manifest.pending.json"], approval)
    manifest = _approved_manifest(
        proposals["model-manifest.pending.json"], approval, rollback
    )
    registry = _registry(manifest, proposals["model-registry-rules.json"], approval)
    decision = _decision(request, approval, manifest, registry, rollback)
    descriptor = _active_descriptor(_load_json(ACTIVE_DESCRIPTOR_PATH), manifest, decision)
    profiles = _active_profiles(_load_json(ACTIVE_PROFILES_PATH), manifest)
    return {
        "approval": approval,
        "model-manifest.json": manifest,
        "model-registry.json": registry,
        "rollback-manifest.json": rollback,
        "decision.json": decision,
        "model-artifacts.json": descriptor,
        "assistant-profiles.json": profiles,
    }


def build_artifacts() -> dict[str, bytes]:
    documents = build_documents()
    return {
        OUTPUT_REFERENCES[name]: BUILDER.json_bytes(documents[name])
        for name in IMMUTABLE_OUTPUT_NAMES
    }


def write_artifacts(*, check: bool) -> None:
    mismatches: list[str] = []
    documents = build_documents()
    for relative_path, content in build_artifacts().items():
        path = ROOT / relative_path
        if check:
            if not path.is_file() or path.read_bytes() != content:
                mismatches.append(relative_path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    for active_name in ("model-artifacts.json", "assistant-profiles.json"):
        path = ROOT / OUTPUT_REFERENCES[active_name]
        content = BUILDER.json_bytes(documents[active_name]).replace(b"\n", b"\r\n")
        if check:
            if not path.is_file() or path.read_bytes() != content:
                mismatches.append(OUTPUT_REFERENCES[active_name])
        else:
            path.write_bytes(content)
    if mismatches:
        raise PromotionFinalizationError("artifact mismatch: " + ", ".join(mismatches))


def materialize_adapter(
    manifest: dict[str, Any], source_root: Path, artifact_root: Path
) -> None:
    try:
        VALIDATOR.validate_adapter_root(manifest, source_root)
    except VALIDATOR.PromotionValidationError as error:
        raise PromotionFinalizationError("source adapter checksum validation failed") from error

    resolved_source = source_root.resolve(strict=True)
    artifact_root.mkdir(parents=True, exist_ok=True)
    resolved_artifact_root = artifact_root.resolve(strict=True)
    assistant_root = resolved_artifact_root / "research-assistant"
    if assistant_root.is_symlink() or (assistant_root.exists() and not assistant_root.is_dir()):
        raise PromotionFinalizationError("adapter target path invalid")
    assistant_root.mkdir(exist_ok=True)
    target = assistant_root / ADAPTER_VERSION
    if target.is_symlink() or (target.exists() and not target.is_dir()):
        raise PromotionFinalizationError("adapter target path invalid")
    target.mkdir(exist_ok=True)
    resolved_target = target.resolve(strict=True)
    if not resolved_target.is_relative_to(resolved_artifact_root):
        raise PromotionFinalizationError("adapter target path invalid")
    entries = manifest["artifact"]["files"]
    for entry in entries:
        logical = PurePosixPath(entry["path"])
        name = logical.name
        source = resolved_source / name
        destination = resolved_target / name
        if destination.is_symlink():
            raise PromotionFinalizationError("adapter target path invalid")
        if destination.exists():
            if (
                not destination.is_file()
                or destination.stat().st_size != entry["sizeBytes"]
                or _sha256(destination) != entry["sha256"]
            ):
                raise PromotionFinalizationError("existing adapter checksum mismatch")
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_name(f".{destination.name}.p7-t5.tmp")
        if temporary.exists() or temporary.is_symlink():
            raise PromotionFinalizationError("temporary adapter artifact already exists")
        try:
            shutil.copyfile(source, temporary)
            if (
                temporary.stat().st_size != entry["sizeBytes"]
                or _sha256(temporary) != entry["sha256"]
            ):
                raise PromotionFinalizationError("copied adapter checksum mismatch")
            os.replace(temporary, destination)
        finally:
            if temporary.exists():
                temporary.unlink()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter-root", type=Path)
    parser.add_argument("--artifact-root", type=Path, default=ROOT / "ai-service" / "artifacts")
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        documents = build_documents()
        write_artifacts(check=arguments.check)
        if arguments.check:
            VALIDATOR.validate_adapter_root(
                documents["model-manifest.json"],
                arguments.artifact_root / "research-assistant" / ADAPTER_VERSION,
            )
        elif arguments.adapter_root is not None:
            materialize_adapter(
                documents["model-manifest.json"],
                arguments.adapter_root,
                arguments.artifact_root,
            )
        decision = documents["decision.json"]
        print(
            json.dumps(
                {
                    "candidateId": decision["candidateId"],
                    "decisionIdentity": decision["artifactIdentity"],
                    "promotionMaterialized": arguments.check or arguments.adapter_root is not None,
                    "servingAllowed": decision["servingLoadAllowed"],
                    "state": decision["state"],
                },
                sort_keys=True,
            )
        )
        return 0
    except (
        PromotionFinalizationError,
        BUILDER.PromotionRequestError,
        VALIDATOR.PromotionValidationError,
    ) as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
