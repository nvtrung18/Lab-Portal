"""Refresh the Kaggle notebook's embedded source from this checkout, without model weights."""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import runpy
import shutil
import subprocess
import textwrap
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NOTEBOOK = ROOT / "docs/testing/AI_Service_Kaggle.ipynb"


def artifact_zip(manifest_path: Path, source: Path, output: Path) -> int:
    """Package approved bytes; repair CRLF only when the result matches the approved hash."""
    preparation = runpy.run_path(str(ROOT / "ai-service/scripts/prepare_runtime_artifacts.py"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    files = preparation["_files"](manifest)
    prepared = []
    for relative, expected in files:
        path = source.joinpath(*relative.parts)
        content = None
        if preparation["_sha256"](path) != expected:
            if path.suffix in {".json", ".txt", ".md"} and path.stat().st_size < 32 * 1024**2:
                content = path.read_bytes().replace(b"\r\n", b"\n")
            if content is None or hashlib.sha256(content).hexdigest() != expected:
                raise ValueError(f"Artifact differs from approved bytes: {relative}")
        prepared.append((relative, path, content))
    if output.exists():
        raise FileExistsError(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive creation protects previous exports; partial output is never published as a final ZIP.
    partial = output.with_name(output.name + ".partial")
    try:
        with partial.open("xb") as target, zipfile.ZipFile(target, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as bundle:
            for relative, path, content in prepared:
                name = "runtime-artifacts/" + str(relative)
                if content is not None:
                    bundle.writestr(name, content)
                else:
                    with path.open("rb") as input_file, bundle.open(name, "w", force_zip64=True) as destination:
                        shutil.copyfileobj(input_file, destination, length=1024 * 1024)
            bundle.writestr("runtime-artifacts/runtime-manifest.json", manifest_path.read_bytes())
        partial.replace(output)
    except Exception:
        # Leave any partial file for diagnosis; never replace an earlier export on failure.
        raise
    return len(prepared)


def source_bundle(root: Path) -> tuple[bytes, dict]:
    paths = []
    for directory in ("app", "config", "tests"):
        paths.extend(
            path for path in (root / "ai-service" / directory).rglob("*")
            if path.is_file() and path.suffix in {".py", ".json", ".md"}
            and "__pycache__" not in path.parts and path.name != "test_kaggle_notebook.py"
        )
    paths.extend(root / "ai-service" / name for name in (
        "requirements.txt", "requirements-api.txt", "requirements-runtime-t4.txt",
        "scripts/__init__.py", "scripts/prepare_runtime_artifacts.py", "scripts/verify_runtime.py",
    ))
    paths.append(root / "contracts/ai-runtime-contract.json")
    contents = {path.relative_to(root).as_posix(): path.read_bytes() for path in sorted(set(paths))}
    revision = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    manifest = {
        "gitRevision": revision,
        "source": "working-tree snapshot; per-file hashes are authoritative",
        "files": {name: hashlib.sha256(content).hexdigest() for name, content in contents.items()},
    }
    contents["kaggle-source-manifest.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for name, content in sorted(contents.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.create_system = 0  # Keep source identity independent of the packaging host OS.
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            bundle.writestr(info, content)
    return output.getvalue(), manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=NOTEBOOK)
    parser.add_argument("--artifact-source", type=Path, help="Existing runtime-artifacts directory; original files remain unchanged")
    parser.add_argument("--artifact-zip", type=Path, help="New ZIP64 output path; requires --artifact-source")
    args = parser.parse_args()
    if bool(args.artifact_source) != bool(args.artifact_zip):
        parser.error("--artifact-source and --artifact-zip must be provided together")
    notebook = json.loads(NOTEBOOK.read_text(encoding="utf-8"))
    archive, manifest = source_bundle(ROOT)
    digest = hashlib.sha256(archive).hexdigest()
    encoded = base64.b64encode(archive).decode("ascii")
    embedded_cell = next(cell for cell in notebook["cells"] if cell.get("id") == "embedded-source")
    code = (
        "# Generated by scripts/prepare-ai-service-kaggle.py; contains source/config/tests, never model weights.\n"
        f'SOURCE_GIT_REVISION = "{manifest["gitRevision"]}"\n'
        f'SOURCE_ZIP_SHA256 = "{digest}"\n'
        'SOURCE_ZIP_B64 = (\n'
        + "".join(f'    "{line}"\n' for line in textwrap.wrap(encoded, width=120))
        + ')\nprint("Embedded source:", SOURCE_GIT_REVISION[:12], "SHA256:", SOURCE_ZIP_SHA256)\n'
    )
    embedded_cell["source"] = code.splitlines(keepends=True)
    notebook["metadata"]["lab_portal_source"] = {
        "gitRevision": manifest["gitRevision"], "sha256": digest, "fileCount": len(manifest["files"]),
    }
    for cell in notebook["cells"]:
        if cell["cell_type"] == "code":
            compile("".join(cell["source"]), f"notebook:{cell['id']}", "exec")
            cell.update(execution_count=None, outputs=[])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(notebook, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Prepared {args.output}: {len(manifest['files'])} files, source ZIP {len(archive):,} bytes; SHA256 {digest}")
    if args.artifact_source:
        count = artifact_zip(ROOT / "ai-service/config/model-artifacts.runtime-t4.json", args.artifact_source, args.artifact_zip)
        print(f"Prepared {args.artifact_zip}: {count} verified artifact files; original files unchanged")


if __name__ == "__main__":
    main()
