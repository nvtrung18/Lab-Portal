from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import tempfile
from pathlib import Path, PurePosixPath
from typing import Any


class ArtifactPreparationError(RuntimeError):
    pass


def _load_manifest(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ArtifactPreparationError(f"Cannot read runtime manifest: {path}") from exc
    if not isinstance(value, dict):
        raise ArtifactPreparationError("Runtime manifest must be a JSON object.")
    return value


def _safe_relative_path(value: object) -> PurePosixPath:
    if not isinstance(value, str):
        raise ArtifactPreparationError("Artifact path must be a string.")
    path = PurePosixPath(value)
    if path.is_absolute() or not path.parts or ".." in path.parts or "." in path.parts:
        raise ArtifactPreparationError(f"Unsafe artifact path: {value}")
    return path


def _files(manifest: dict[str, Any]) -> list[tuple[PurePosixPath, str]]:
    descriptors: list[object] = []
    base = manifest.get("baseModel")
    if isinstance(base, dict) and isinstance(base.get("artifact"), dict):
        descriptors.extend(base["artifact"].get("files", []))
    adapters = manifest.get("assistantAdapters")
    if isinstance(adapters, dict):
        for adapter in adapters.values():
            if isinstance(adapter, dict) and isinstance(adapter.get("artifact"), dict):
                descriptors.extend(adapter["artifact"].get("files", []))
    result: list[tuple[PurePosixPath, str]] = []
    for descriptor in descriptors:
        if not isinstance(descriptor, dict):
            raise ArtifactPreparationError("Artifact file descriptor must be an object.")
        digest = descriptor.get("sha256")
        if not isinstance(digest, str) or len(digest) != 64:
            raise ArtifactPreparationError("Artifact SHA-256 must contain 64 hexadecimal characters.")
        try:
            int(digest, 16)
        except ValueError as exc:
            raise ArtifactPreparationError("Artifact SHA-256 is not hexadecimal.") from exc
        result.append((_safe_relative_path(descriptor.get("path")), digest.lower()))
    if not result:
        raise ArtifactPreparationError("Runtime manifest contains no physical artifact files.")
    if len({str(path) for path, _digest in result}) != len(result):
        raise ArtifactPreparationError("Runtime manifest contains duplicate artifact paths.")
    return result


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify(manifest_path: Path, artifact_root: Path) -> int:
    manifest = _load_manifest(manifest_path)
    checked = 0
    for relative, expected in _files(manifest):
        candidate = artifact_root.joinpath(*relative.parts)
        if not candidate.is_file():
            raise ArtifactPreparationError(f"Missing artifact file: {relative}")
        actual = _sha256(candidate)
        if actual != expected:
            raise ArtifactPreparationError(f"Checksum mismatch: {relative}")
        checked += 1
    return checked


def assemble(manifest_path: Path, source_root: Path, output_root: Path) -> int:
    manifest = _load_manifest(manifest_path)
    files = _files(manifest)
    if output_root.exists():
        raise ArtifactPreparationError(f"Output root already exists: {output_root}")
    output_root.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f".{output_root.name}-", dir=output_root.parent))
    try:
        for relative, expected in files:
            source = source_root.joinpath(*relative.parts)
            if not source.is_file():
                raise ArtifactPreparationError(f"Missing source artifact file: {relative}")
            if _sha256(source) != expected:
                raise ArtifactPreparationError(f"Source checksum mismatch: {relative}")
            destination = staging.joinpath(*relative.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        shutil.copyfile(manifest_path, staging / "runtime-manifest.json")
        os.replace(staging, output_root)
        return len(files)
    except Exception:
        shutil.rmtree(staging, ignore_errors=True)
        raise


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Assemble and verify the immutable AI runtime artifact bundle.")
    parser.add_argument("--manifest", type=Path, default=Path("config/model-artifacts.runtime-t4.json"))
    subparsers = parser.add_subparsers(dest="command", required=True)
    verify_parser = subparsers.add_parser("verify")
    verify_parser.add_argument("--artifact-root", type=Path, required=True)
    assemble_parser = subparsers.add_parser("assemble")
    assemble_parser.add_argument("--source-root", type=Path, required=True)
    assemble_parser.add_argument("--output-root", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "verify":
            count = verify(args.manifest.resolve(), args.artifact_root.resolve())
            print(f"Verified {count} immutable artifact files.")
        else:
            count = assemble(args.manifest.resolve(), args.source_root.resolve(), args.output_root.resolve())
            print(f"Assembled {count} immutable artifact files at {args.output_root.resolve()}.")
        return 0
    except ArtifactPreparationError as exc:
        print(f"Artifact preparation failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
