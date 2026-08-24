#!/usr/bin/env python3
"""Build the governed portable P7-T4 Research evaluation bundle."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import shutil
import zipfile
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BUNDLE_NAME = "p7-t4-research-evaluation"
SOURCE_FILES = (
    "config/p7-t4-research-independent-evaluation.json",
    "config/p6-t5-benchmark-kaggle-linux-cp312.yaml",
    "config/p7-t3-research-report-eval-governance-request.json",
    "requirements/p7-t2-real-training-t4-cp313-requirements.txt",
    "evals/p6-t4-evaluation-suites.yaml",
    "evals/evaluation-suite.schema.json",
    "evals/human-eval-rubric.yaml",
    "evals/p6-t4-evaluation-suite.lock.json",
    "evals/p6-t4-evaluation-freeze.binding.yaml",
    "evals/p7-t3-research-gap-evaluation-suite.json",
    "evals/p7-t3-research-gap-evaluation-suite.lock.json",
    "evals/fixtures/p6-t4/valid-suite.yaml",
    "evals/fixtures/p6-t4/valid-candidate.yaml",
    "evals/fixtures/p6-t4/valid-human-review.yaml",
    "evals/fixtures/p6-t4/pending-human-review.yaml",
    "evals/fixtures/p6-t4/invalid-cases.yaml",
    "evidence/p7-t1c-frozen-evaluation-governance-approval.json",
    "evidence/p7-t2-real-training/adapter-manifest.json",
    "evidence/p7-t2-real-training/real-training-evidence.json",
    "evidence/p7-t3-research-report-eval-governance-approval-v2.json",
    "docs/architecture/ai/data-governance.yml",
    "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
    "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml",
    "docs/architecture/ai/p7-t4-research-independent-evaluation-runbook.txt",
    "scripts/build-p7-t4-research-evaluation-bundle.py",
    "scripts/research-independent-evaluation-p7-t4.py",
    "scripts/validate-p7-t4-research-evaluation-bundle.py",
    "scripts/validate-evaluation-suites.py",
    "scripts/benchmark-p6-t5.py",
    "scripts/research-model-decision-p7-t3.py",
    "scripts/research-gap-evidence-p7-t3.py",
    "scripts/research-report-eval-governance-p7-t3.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/p7-t2-real-training.py",
)


class BundleBuildError(ValueError):
    pass


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise BundleBuildError(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


P7T4 = _load_module(
    "p7t4_for_bundle_builder", ROOT / "scripts" / "research-independent-evaluation-p7-t4.py"
)


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def manifest_identity(manifest: dict[str, Any]) -> str:
    return hashlib.sha256(
        canonical_bytes({key: value for key, value in manifest.items() if key != "bundleIdentity"})
    ).hexdigest()


def bundle_inventory(bundle_root: Path) -> list[dict[str, Any]]:
    inventory = []
    for path in sorted(
        (item for item in bundle_root.rglob("*") if item.is_file()),
        key=lambda item: item.relative_to(bundle_root).as_posix(),
    ):
        relative = path.relative_to(bundle_root).as_posix()
        if relative == "bundle-manifest.json":
            continue
        payload = path.read_bytes()
        inventory.append(
            {"path": relative, "sha256": hashlib.sha256(payload).hexdigest(), "size": len(payload)}
        )
    return inventory


def _write_json(path: Path, value: object) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n",
        encoding="utf-8",
    )


def _write_deterministic_zip(bundle_root: Path, archive_path: Path) -> None:
    with zipfile.ZipFile(archive_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(
            (item for item in bundle_root.rglob("*") if item.is_file()),
            key=lambda item: item.relative_to(bundle_root).as_posix(),
        ):
            relative = f"{bundle_root.name}/{path.relative_to(bundle_root).as_posix()}"
            information = zipfile.ZipInfo(relative, date_time=(1980, 1, 1, 0, 0, 0))
            information.compress_type = zipfile.ZIP_DEFLATED
            information.external_attr = 0o100644 << 16
            archive.writestr(information, path.read_bytes())


def build_bundle(root: Path, adapter_directory: Path, output_parent: Path) -> tuple[Path, Path, dict[str, Any]]:
    root = root.resolve()
    output_parent = output_parent.resolve()
    bundle_root = output_parent / BUNDLE_NAME
    archive_path = output_parent / f"{BUNDLE_NAME}.zip"
    if bundle_root.exists() or archive_path.exists():
        raise BundleBuildError("bundle output already exists; use a clean output parent")
    gate = P7T4.preflight(root, adapter_directory)
    missing = [relative for relative in SOURCE_FILES if not (root / relative).is_file()]
    if missing:
        raise BundleBuildError("bundle source unavailable: " + ", ".join(missing))
    output_parent.mkdir(parents=True, exist_ok=True)
    bundle_root.mkdir()
    try:
        for relative in SOURCE_FILES:
            destination = bundle_root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(root / relative, destination)
        shutil.copytree(adapter_directory.resolve(), bundle_root / "adapter")
        manifest: dict[str, Any] = {
            "artifactType": "P7-T4-RESEARCH-EVALUATION-BUNDLE",
            "schemaVersion": "1.0.0",
            "sourceCommit": P7T4._source_commit(root),
            "candidateId": gate["candidate"]["candidateId"],
            "adapterIdentity": gate["candidate"]["adapterIdentity"],
            "suite": gate["suite"],
            "files": bundle_inventory(bundle_root),
        }
        manifest["bundleIdentity"] = manifest_identity(manifest)
        _write_json(bundle_root / "bundle-manifest.json", manifest)
        _write_deterministic_zip(bundle_root, archive_path)
        return bundle_root, archive_path, manifest
    except Exception:
        if bundle_root.exists():
            shutil.rmtree(bundle_root)
        if archive_path.exists():
            archive_path.unlink()
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--adapter-directory", type=Path, required=True)
    parser.add_argument("--output-parent", type=Path, default=ROOT / ".artifacts" / "p7-t4-bundle")
    args = parser.parse_args()
    try:
        bundle_root, archive_path, manifest = build_bundle(
            args.root, args.adapter_directory, args.output_parent
        )
        print(
            json.dumps(
                {
                    "state": "BUILT",
                    "bundleRoot": str(bundle_root),
                    "archive": str(archive_path),
                    "archiveSha256": hashlib.sha256(archive_path.read_bytes()).hexdigest(),
                    "bundleIdentity": manifest["bundleIdentity"],
                }
            )
        )
        return 0
    except (BundleBuildError, P7T4.P7T4Error, OSError, ValueError) as error:
        print(json.dumps({"state": "FAILED", "error": str(error)}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
