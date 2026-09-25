from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest

from scripts.prepare_runtime_artifacts import ArtifactPreparationError, assemble, verify


def _manifest(path: Path, relative: str, content: bytes, *, digest: str | None = None) -> Path:
    manifest = {
        "baseModel": {
            "artifact": {
                "files": [{"path": relative, "sha256": digest or hashlib.sha256(content).hexdigest()}]
            }
        },
        "assistantAdapters": {},
    }
    path.write_text(json.dumps(manifest), encoding="utf-8")
    return path


def test_assemble_copies_only_manifest_files_and_verifies_checksums(tmp_path: Path) -> None:
    content = b"approved-model-content"
    relative = "shared-base/revision/model.safetensors"
    source_root = tmp_path / "source"
    source = source_root / relative
    source.parent.mkdir(parents=True)
    source.write_bytes(content)
    manifest = _manifest(tmp_path / "manifest.json", relative, content)
    output = tmp_path / "bundle"

    assert assemble(manifest, source_root, output) == 1
    assert verify(manifest, output) == 1
    assert (output / relative).read_bytes() == content
    assert (output / "runtime-manifest.json").is_file()


def test_assemble_fails_without_leaving_partial_output_on_checksum_mismatch(tmp_path: Path) -> None:
    relative = "research-assistant/1.0.0/adapter_model.safetensors"
    source_root = tmp_path / "source"
    source = source_root / relative
    source.parent.mkdir(parents=True)
    source.write_bytes(b"tampered")
    manifest = _manifest(tmp_path / "manifest.json", relative, b"approved")
    output = tmp_path / "bundle"

    with pytest.raises(ArtifactPreparationError, match="checksum mismatch"):
        assemble(manifest, source_root, output)

    assert not output.exists()


def test_manifest_path_traversal_is_rejected(tmp_path: Path) -> None:
    manifest = _manifest(tmp_path / "manifest.json", "../secret", b"content")

    with pytest.raises(ArtifactPreparationError, match="Unsafe artifact path"):
        verify(manifest, tmp_path)
