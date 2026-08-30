#!/usr/bin/env python3
"""Build the deterministic portable P7-T2 real QLoRA execution bundle."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Any
import zipfile


ROOT = Path(__file__).resolve().parents[1]
BUNDLE_NAME = "p7-t2-real-training"
T4_BUNDLE_NAME = "p7-t2-real-training-t4"
MANIFEST_NAME = "bundle-manifest.json"
DATASET_IDENTITY = "7bc78402046966f603f81c374ae68bafe13be2eb0b90de297d16461e38b970e4"
BASE_MODEL = {
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
PAYLOAD_MAPPINGS = (
    ("docs/architecture/ai/p7-t2-real-training-runbook.txt", "README.md"),
    ("config/p6-t6-adapter-decisions.json", "config/p6-t6-adapter-decisions.json"),
    (
        "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-approval-request.json",
        "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-approval-request.json",
    ),
    (
        "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-manifest.pending.json",
        "config/p7-t1b-research-governance-packet-v1/frozen-evaluation-manifest.pending.json",
    ),
    (
        "config/p7-t1b-research-governance-packet-v1/training-approval-request.json",
        "config/p7-t1b-research-governance-packet-v1/training-approval-request.json",
    ),
    (
        "config/p7-t1b-research-governance-packet-v1/training-dataset-card.pending.json",
        "config/p7-t1b-research-governance-packet-v1/training-dataset-card.pending.json",
    ),
    (
        "config/p7-t1c-research-governance-v1/frozen-evaluation-manifest.approved.json",
        "config/p7-t1c-research-governance-v1/frozen-evaluation-manifest.approved.json",
    ),
    (
        "config/p7-t1c-research-governance-v1/training-dataset-card.approved.json",
        "config/p7-t1c-research-governance-v1/training-dataset-card.approved.json",
    ),
    ("config/p7-t2-training-pipeline.json", "config/p7-t2-training-pipeline.json"),
    (
        "datasets/p7-t1a-research-synthetic-source-v1/provenance.json",
        "datasets/p7-t1a-research-synthetic-source-v1/provenance.json",
    ),
    (
        "datasets/p7-t1a-research-synthetic-source-v1/source-export.json",
        "datasets/p7-t1a-research-synthetic-source-v1/source-export.json",
    ),
    (
        "datasets/p7-research-synthetic-training-dataset-v1/evaluation.jsonl",
        "datasets/p7-research-synthetic-training-dataset-v1/evaluation.jsonl",
    ),
    (
        "datasets/p7-research-synthetic-training-dataset-v1/manifest.json",
        "datasets/p7-research-synthetic-training-dataset-v1/manifest.json",
    ),
    (
        "datasets/p7-research-synthetic-training-dataset-v1/rejections.jsonl",
        "datasets/p7-research-synthetic-training-dataset-v1/rejections.jsonl",
    ),
    (
        "datasets/p7-research-synthetic-training-dataset-v1/train.jsonl",
        "datasets/p7-research-synthetic-training-dataset-v1/train.jsonl",
    ),
    (
        "datasets/p7-research-synthetic-training-dataset-v1/validation.jsonl",
        "datasets/p7-research-synthetic-training-dataset-v1/validation.jsonl",
    ),
    (
        "evidence/p7-t1c-frozen-evaluation-governance-approval.json",
        "evidence/p7-t1c-frozen-evaluation-governance-approval.json",
    ),
    (
        "evidence/p7-t1c-research-training-governance-approval.json",
        "evidence/p7-t1c-research-training-governance-approval.json",
    ),
    (
        "requirements/p7-t2-real-training-requirements.txt",
        "requirements/p7-t2-real-training-requirements.txt",
    ),
    ("scripts/p7-t2-real-training.py", "scripts/p7-t2-real-training.py"),
    ("scripts/training-pipeline-p7-t2.py", "scripts/training-pipeline-p7-t2.py"),
    (
        "scripts/validate-p7-t2-real-training-bundle.py",
        "scripts/validate-p7-t2-real-training-bundle.py",
    ),
)
T4_SOURCE_OVERRIDES = {
    "README.md": "docs/architecture/ai/p7-t2-real-training-t4-runbook.txt",
    "config/p7-t2-training-pipeline.json": "config/p7-t2-training-pipeline-t4.json",
    "requirements/p7-t2-real-training-requirements.txt": (
        "requirements/p7-t2-real-training-t4-cp313-requirements.txt"
    ),
}


def _bundle_name(profile: str) -> str:
    if profile == "bf16":
        return BUNDLE_NAME
    if profile == "t4":
        return T4_BUNDLE_NAME
    raise ValueError("profile must be bf16 or t4")


def _payload_mappings(profile: str) -> tuple[tuple[str, str], ...]:
    _bundle_name(profile)
    if profile == "bf16":
        return PAYLOAD_MAPPINGS
    return tuple(
        (T4_SOURCE_OVERRIDES.get(destination, source), destination)
        for source, destination in PAYLOAD_MAPPINGS
    )


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise ValueError(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


def _source_commit(source_root: Path) -> str:
    completed = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=source_root,
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout.strip()


def _inventory(bundle_root: Path) -> list[dict[str, Any]]:
    result = []
    for path in sorted(
        (item for item in bundle_root.rglob("*") if item.is_file()),
        key=lambda item: item.relative_to(bundle_root).as_posix(),
    ):
        logical = path.relative_to(bundle_root).as_posix()
        if logical == MANIFEST_NAME:
            continue
        payload = path.read_bytes()
        result.append({"path": logical, "size": len(payload), "sha256": sha256_bytes(payload)})
    return result


def _manifest(bundle_root: Path, source_commit: str) -> dict[str, Any]:
    pipeline = _load_module("p7_t2_bundle_identity", bundle_root / "scripts/training-pipeline-p7-t2.py")
    config = json.loads(
        (bundle_root / "config/p7-t2-training-pipeline.json").read_text(encoding="utf-8")
    )
    inventory = _inventory(bundle_root)
    manifest: dict[str, Any] = {
        "artifactType": "P7-T2-REAL-TRAINING-BUNDLE-MANIFEST",
        "manifestVersion": "1.0",
        "bundleVersion": "1.0.0",
        "sourceCommit": source_commit,
        "baseModel": BASE_MODEL,
        "dataset": {
            "identity": DATASET_IDENTITY,
            "manifestReference": "datasets/p7-research-synthetic-training-dataset-v1/manifest.json",
        },
        "approvals": {
            "evaluation": "3ee4fa0f0dabee3ca8602e659c1c9b6a5fe0a30c523c88d99535a40036765479",
            "training": "cf809163ab031f0fb4730cf6137073eca510cfad5914c4c9292b5c5fbef48eb4",
        },
        "trainingConfigIdentity": pipeline.training_config_identity(config),
        "inventoryScope": "ALL_PAYLOAD_FILES_EXCEPT_THIS_MANIFEST",
        "fileCount": len(inventory) + 1,
        "fileInventory": inventory,
    }
    manifest["bundleIdentity"] = sha256_bytes(canonical_bytes(manifest))
    return manifest


def _write_json(path: Path, value: object) -> None:
    path.write_bytes(
        (
            json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
            + "\n"
        ).encode("utf-8")
    )


def _write_deterministic_zip(bundle_root: Path, zip_path: Path) -> None:
    with zipfile.ZipFile(
        zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9, strict_timestamps=True
    ) as archive:
        for path in sorted(
            (item for item in bundle_root.rglob("*") if item.is_file()),
            key=lambda item: item.relative_to(bundle_root).as_posix(),
        ):
            logical = path.relative_to(bundle_root).as_posix()
            info = zipfile.ZipInfo(logical, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(
                info,
                path.read_bytes(),
                compress_type=zipfile.ZIP_DEFLATED,
                compresslevel=9,
            )


def build_bundle(
    *,
    source_root: Path,
    output_dir: Path,
    zip_path: Path,
    source_commit: str,
    replace: bool = False,
    profile: str = "bf16",
) -> dict[str, Any]:
    source_root = source_root.resolve()
    output_dir = output_dir.resolve()
    zip_path = zip_path.resolve()
    bundle_name = _bundle_name(profile)
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise ValueError("source_commit must be a full lowercase Git commit")
    if (output_dir.exists() or zip_path.exists()) and not replace:
        raise FileExistsError("bundle output is append-only; directory and ZIP must not exist")
    if output_dir.parent != zip_path.parent:
        raise ValueError("bundle directory and ZIP must share a parent")
    if replace and (output_dir.name != bundle_name or zip_path.name != f"{bundle_name}.zip"):
        raise ValueError("replacement is restricted to the named P7-T2 bundle outputs")
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f".{bundle_name}.", dir=output_dir.parent) as temporary:
        staging = Path(temporary) / bundle_name
        staged_zip = Path(temporary) / f"{bundle_name}.zip"
        staging.mkdir()
        for source_name, destination_name in _payload_mappings(profile):
            source = source_root / source_name
            if not source.is_file():
                raise FileNotFoundError(f"required bundle source missing: {source_name}")
            destination = staging / destination_name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        manifest = _manifest(staging, source_commit)
        _write_json(staging / MANIFEST_NAME, manifest)
        validator = _load_module(
            "p7_t2_bundle_validator", source_root / "scripts/validate-p7-t2-real-training-bundle.py"
        )
        validator.validate_bundle(staging)
        _write_deterministic_zip(staging, staged_zip)
        if replace:
            if output_dir.exists():
                shutil.rmtree(output_dir)
            if zip_path.exists():
                zip_path.unlink()
        staging.replace(output_dir)
        staged_zip.replace(zip_path)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=ROOT)
    parser.add_argument("--profile", choices=("bf16", "t4"), default="bf16")
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--zip", dest="zip_path", type=Path)
    parser.add_argument("--source-commit")
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()
    try:
        bundle_name = _bundle_name(args.profile)
        output_dir = args.output_dir or ROOT / "dist" / bundle_name
        zip_path = args.zip_path or ROOT / "dist" / f"{bundle_name}.zip"
        source_commit = args.source_commit or _source_commit(args.source_root)
        manifest = build_bundle(
            source_root=args.source_root,
            output_dir=output_dir,
            zip_path=zip_path,
            source_commit=source_commit,
            replace=args.replace,
            profile=args.profile,
        )
        print(
            json.dumps(
                {
                    "status": "READY_FOR_EXTERNAL_REAL_TRAINING",
                    "bundleDirectory": str(output_dir),
                    "zip": str(zip_path),
                    "zipSha256": sha256_bytes(zip_path.read_bytes()),
                    "bundleIdentity": manifest["bundleIdentity"],
                    "trainingConfigIdentity": manifest["trainingConfigIdentity"],
                    "fileCount": manifest["fileCount"],
                },
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": [str(error)]}, sort_keys=True, separators=(",", ":")),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
