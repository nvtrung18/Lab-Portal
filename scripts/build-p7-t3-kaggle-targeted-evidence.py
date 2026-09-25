#!/usr/bin/env python3
"""Build the deterministic portable P7-T3 Kaggle targeted-evidence bundle."""
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
BUNDLE_NAME = "p7-t3-kaggle-targeted-evidence"
MANIFEST_NAME = "bundle-manifest.json"
PAYLOAD_MAPPINGS = (
    ("docs/architecture/ai/p7-t3-kaggle-targeted-evidence-runbook.txt", "README.md"),
    ("config/p6-t5-benchmark.yaml", "config/p6-t5-benchmark.yaml"),
    (
        "config/p7-t3-research-report-eval-governance-request.json",
        "config/p7-t3-research-report-eval-governance-request.json",
    ),
    ("docs/architecture/ai/data-governance.yml", "docs/architecture/ai/data-governance.yml"),
    (
        "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
        "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
    ),
    (
        "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml",
        "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml",
    ),
    ("evals/human-eval-rubric.yaml", "evals/human-eval-rubric.yaml"),
    ("evals/p6-t4-evaluation-suites.yaml", "evals/p6-t4-evaluation-suites.yaml"),
    (
        "evals/p7-t3-research-gap-evaluation-suite.json",
        "evals/p7-t3-research-gap-evaluation-suite.json",
    ),
    (
        "evals/p7-t3-research-gap-evaluation-suite.lock.json",
        "evals/p7-t3-research-gap-evaluation-suite.lock.json",
    ),
    (
        "evidence/p7-t3-research-report-eval-governance-approval-v2.json",
        "evidence/p7-t3-research-report-eval-governance-approval-v2.json",
    ),
    (
        "requirements/p7-t3-kaggle-targeted-evidence-requirements.txt",
        "requirements/p7-t3-kaggle-targeted-evidence-requirements.txt",
    ),
    ("scripts/benchmark-p6-t5.py", "scripts/benchmark-p6-t5.py"),
    ("scripts/research-gap-evidence-p7-t3.py", "scripts/research-gap-evidence-p7-t3.py"),
    ("scripts/research-model-decision-p7-t3.py", "scripts/research-model-decision-p7-t3.py"),
    (
        "scripts/research-report-eval-governance-p7-t3.py",
        "scripts/research-report-eval-governance-p7-t3.py",
    ),
    ("scripts/training-pipeline-p7-t2.py", "scripts/training-pipeline-p7-t2.py"),
    ("scripts/validate-evaluation-suites.py", "scripts/validate-evaluation-suites.py"),
    (
        "scripts/validate-p7-t3-kaggle-targeted-evidence.py",
        "scripts/validate-p7-t3-kaggle-targeted-evidence.py",
    ),
)
TARGET = {
    "candidateId": "qwen3_4b-R01",
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
CASE_IDS = ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-006"]
APPROVAL = {
    "path": "evidence/p7-t3-research-report-eval-governance-approval-v2.json",
    "artifactIdentity": "435b5f8576ace851aadebeedc6ada8e40b529789734d27fe0f094a4c565b9518",
    "status": "APPROVED",
    "permittedPurposes": ["EVALUATION", "HUMAN_EVALUATION"],
}
SUITE = {
    "id": "P7-T3-RESEARCH-GAP-EVALUATION",
    "version": "1.1.0",
    "digest": "95ff79e0ec38c6d8ebfbcca05f0320b49819bf4f61e99c5b3b5c52cd559c83a5",
}


def _canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def _sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _load_validator(source_root: Path) -> Any:
    path = source_root / "scripts" / "validate-p7-t3-kaggle-targeted-evidence.py"
    name = f"p7_t3_bundle_validator_{hashlib.sha256(str(path).encode()).hexdigest()[:12]}"
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise ValueError(f"bundle validator unavailable: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
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


def _inventory(bundle_root: Path) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    files = [item for item in bundle_root.rglob("*") if item.is_file()]
    for path in sorted(files, key=lambda item: item.relative_to(bundle_root).as_posix()):
        logical_path = path.relative_to(bundle_root).as_posix()
        if logical_path == MANIFEST_NAME:
            continue
        payload = path.read_bytes()
        result.append(
            {
                "path": logical_path,
                "size": len(payload),
                "sha256": _sha256_bytes(payload),
            }
        )
    return result


def _manifest(bundle_root: Path, source_commit: str) -> dict[str, Any]:
    inventory = _inventory(bundle_root)
    manifest: dict[str, Any] = {
        "artifactType": "P7-T3-KAGGLE-TARGETED-EVIDENCE-BUNDLE-MANIFEST",
        "manifestVersion": "1.0",
        "bundleVersion": "1.0.0",
        "sourceCommit": source_commit,
        "target": TARGET,
        "caseIds": CASE_IDS,
        "governanceApproval": APPROVAL,
        "suite": SUITE,
        "inventoryScope": "ALL_PAYLOAD_FILES_EXCEPT_THIS_MANIFEST",
        "fileCount": len(inventory) + 1,
        "fileInventory": inventory,
    }
    manifest["bundleIdentity"] = _sha256_bytes(_canonical_bytes(manifest))
    return manifest


def _write_manifest(bundle_root: Path, manifest: dict[str, Any]) -> None:
    rendered = json.dumps(
        manifest,
        ensure_ascii=False,
        sort_keys=True,
        indent=2,
        allow_nan=False,
    ) + "\n"
    (bundle_root / MANIFEST_NAME).write_text(rendered, encoding="utf-8", newline="\n")


def _write_deterministic_zip(bundle_root: Path, zip_path: Path) -> None:
    with zipfile.ZipFile(
        zip_path,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=9,
        strict_timestamps=True,
    ) as archive:
        files = [item for item in bundle_root.rglob("*") if item.is_file()]
        for path in sorted(files, key=lambda item: item.relative_to(bundle_root).as_posix()):
            logical_path = path.relative_to(bundle_root).as_posix()
            info = zipfile.ZipInfo(logical_path, date_time=(1980, 1, 1, 0, 0, 0))
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
) -> dict[str, Any]:
    source_root = source_root.resolve()
    output_dir = output_dir.resolve()
    zip_path = zip_path.resolve()
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise ValueError("source_commit must be a full lowercase Git commit")
    if (output_dir.exists() or zip_path.exists()) and not replace:
        raise FileExistsError("bundle output is append-only; directory and ZIP must not exist")
    if output_dir.parent != zip_path.parent:
        raise ValueError("bundle directory and ZIP must share a parent")
    if replace and (
        output_dir.name != BUNDLE_NAME or zip_path.name != f"{BUNDLE_NAME}.zip"
    ):
        raise ValueError("replacement is restricted to the named P7-T3 bundle outputs")
    output_dir.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix=f".{BUNDLE_NAME}.", dir=output_dir.parent) as temporary:
        staging = Path(temporary) / BUNDLE_NAME
        staged_zip = Path(temporary) / f"{BUNDLE_NAME}.zip"
        staging.mkdir()
        for source_name, destination_name in PAYLOAD_MAPPINGS:
            source = source_root / source_name
            if not source.is_file():
                raise FileNotFoundError(f"required bundle source missing: {source_name}")
            destination = staging / destination_name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        manifest = _manifest(staging, source_commit)
        _write_manifest(staging, manifest)
        validator = _load_validator(source_root)
        previous_bytecode_setting = sys.dont_write_bytecode
        sys.dont_write_bytecode = True
        try:
            validator.validate_bundle(staging)
        finally:
            sys.dont_write_bytecode = previous_bytecode_setting
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
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ROOT / "dist" / BUNDLE_NAME,
    )
    parser.add_argument(
        "--zip",
        dest="zip_path",
        type=Path,
        default=ROOT / "dist" / f"{BUNDLE_NAME}.zip",
    )
    parser.add_argument("--source-commit")
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()
    try:
        source_commit = args.source_commit or _source_commit(args.source_root)
        manifest = build_bundle(
            source_root=args.source_root,
            output_dir=args.output_dir,
            zip_path=args.zip_path,
            source_commit=source_commit,
            replace=args.replace,
        )
        result = {
            "status": "READY_FOR_KAGGLE",
            "bundleDirectory": str(args.output_dir),
            "zip": str(args.zip_path),
            "zipSha256": _sha256_bytes(args.zip_path.read_bytes()),
            "bundleIdentity": manifest["bundleIdentity"],
            "fileCount": manifest["fileCount"],
        }
        print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(
            json.dumps(
                {"status": "ERROR", "diagnostics": [str(error)]},
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
