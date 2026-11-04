from __future__ import annotations

import ast
import base64
import errno
import hashlib
import io
import json
import os
from pathlib import Path
import runpy
import socket
import stat
import subprocess
import sys
import zipfile

import pytest


ROOT = Path(__file__).resolve().parents[2]
NOTEBOOK = ROOT / "AI_Service_Kaggle.ipynb"


def _cell(cell_id: str) -> str:
    notebook = json.loads(NOTEBOOK.read_text(encoding="utf-8"))
    return "".join(next(cell for cell in notebook["cells"] if cell.get("id") == cell_id)["source"])


def _helpers() -> dict:
    namespace: dict = {}
    exec(compile(_cell("bootstrap-helpers"), str(NOTEBOOK), "exec"), namespace)
    return namespace


def test_embedded_source_is_complete_and_matches_the_current_checkout() -> None:
    namespace: dict = {}
    exec(_cell("embedded-source"), namespace)
    archive = base64.b64decode(namespace["SOURCE_ZIP_B64"], validate=True)
    assert hashlib.sha256(archive).hexdigest() == namespace["SOURCE_ZIP_SHA256"]
    with zipfile.ZipFile(io.BytesIO(archive)) as bundle:
        assert bundle.testzip() is None
        manifest = json.loads(bundle.read("kaggle-source-manifest.json"))
        for name, digest in manifest["files"].items():
            content = bundle.read(name)
            assert hashlib.sha256(content).hexdigest() == digest
            # Git may check text out with CRLF on Windows and LF on Linux.
            # The archive and manifest checks above still verify exact bundle bytes.
            assert content.replace(b"\r\n", b"\n") == (ROOT / name).read_bytes().replace(b"\r\n", b"\n"), name
        for name in (
            "ai-service/app/main.py", "ai-service/config/assistant-profiles.json",
            "ai-service/config/model-artifacts.runtime-t4.json",
            "ai-service/config/schemas/structured-output-schemas.json",
            "ai-service/scripts/prepare_runtime_artifacts.py",
            "ai-service/requirements-runtime-t4.txt", "ai-service/requirements-api.txt",
            "contracts/ai-runtime-contract.json", "ai-service/tests/test_spring_contract.py",
        ):
            assert name in manifest["files"]
        assert not any("artifacts/" in name or ".env" in name or "__pycache__" in name for name in bundle.namelist())


def test_checkout_comparison_accepts_git_line_endings_but_rejects_source_edits(monkeypatch) -> None:
    original_read = Path.read_bytes
    target = ROOT / "ai-service/app/main.py"
    canonical = original_read(target).replace(b"\r\n", b"\n")

    def linux_checkout(path):
        return canonical if path == target else original_read(path)

    monkeypatch.setattr(Path, "read_bytes", linux_checkout)
    test_embedded_source_is_complete_and_matches_the_current_checkout()
    changed = canonical.replace(b"create_app", b"changed_app", 1)
    assert changed != canonical
    monkeypatch.setattr(Path, "read_bytes", lambda path: changed if path == target else original_read(path))
    with pytest.raises(AssertionError, match="ai-service/app/main.py"):
        test_embedded_source_is_complete_and_matches_the_current_checkout()


@pytest.mark.parametrize("name", ["../outside.txt", "/absolute.txt", "C:/outside.txt", "..\\outside.txt"])
def test_zip_extraction_rejects_unsafe_paths_before_writing(tmp_path: Path, name: str) -> None:
    archive = tmp_path / "unsafe.zip"
    with zipfile.ZipFile(archive, "w") as bundle:
        bundle.writestr("safe.txt", "safe")
        bundle.writestr(name, "unsafe")
    with pytest.raises(ValueError, match="Unsafe ZIP"):
        _helpers()["extract_zip"](archive, tmp_path / "output")
    assert not (tmp_path / "output" / "safe.txt").exists()
    assert not (tmp_path / "outside.txt").exists()


def test_zip_extraction_rejects_symlinks(tmp_path: Path) -> None:
    archive = tmp_path / "symlink.zip"
    info = zipfile.ZipInfo("link")
    info.create_system = 3
    info.external_attr = (stat.S_IFLNK | 0o777) << 16
    with zipfile.ZipFile(archive, "w") as bundle:
        bundle.writestr(info, "../outside")
    with pytest.raises(ValueError, match="Unsafe ZIP"):
        _helpers()["extract_zip"](archive, tmp_path / "output")


def test_zip_extraction_preserves_valid_file_contents(tmp_path: Path) -> None:
    archive = tmp_path / "valid.zip"
    with zipfile.ZipFile(archive, "w") as bundle:
        bundle.writestr("runtime-artifacts/shared-base/config.json", b'{"model": "test"}')
    output = _helpers()["extract_zip"](archive, tmp_path / "output")
    assert (output / "runtime-artifacts/shared-base/config.json").read_bytes() == b'{"model": "test"}'


def test_artifact_discovery_requires_a_unique_input(tmp_path: Path) -> None:
    one = tmp_path / "one"
    two = tmp_path / "two"
    helpers = _helpers()
    assert helpers["exactly_one"]([one, one], "Artifacts") == one.resolve()
    for paths in ([], [one, two]):
        with pytest.raises(RuntimeError, match="expected one"):
            helpers["exactly_one"](paths, "Artifacts")


def test_dataset_discovery_handles_nested_kaggle_mounts_and_prefers_extracted_files(tmp_path: Path) -> None:
    artifact_root = tmp_path / "datasets" / "owner" / "ai-service-dataset" / "runtime-artifacts"
    (artifact_root / "shared-base").mkdir(parents=True)
    (artifact_root / "research-assistant").mkdir()
    (tmp_path / "runtime-artifacts-kaggle.zip").touch()
    assert _helpers()["discover_artifacts"](tmp_path) == artifact_root.resolve()


@pytest.mark.parametrize("filename", ["runtime-artifacts.zip", "runtime-artifacts-colab.zip", "runtime-artifacts-kaggle.zip"])
def test_dataset_discovery_recognizes_runtime_export_archives(tmp_path: Path, filename: str) -> None:
    archive = tmp_path / filename
    archive.touch()
    assert _helpers()["discover_artifacts"](tmp_path) == archive.resolve()


def test_dataset_discovery_stops_on_missing_or_ambiguous_inputs(tmp_path: Path) -> None:
    discover = _helpers()["discover_artifacts"]
    with pytest.raises(RuntimeError, match="expected one"):
        discover(tmp_path)
    for name in ("dataset-one", "dataset-two"):
        root = tmp_path / name
        (root / "shared-base").mkdir(parents=True)
        (root / "research-assistant").mkdir()
    with pytest.raises(RuntimeError, match="expected one"):
        discover(tmp_path)


def test_readiness_does_not_accept_liveness_or_partially_loaded_models() -> None:
    check = _helpers()["require_ready"]
    valid = dict(status="READY", ready=True, profileLoaded=True, artifactValidated=True,
                 modelLoaded=True, adapterLoaded=True)
    check(valid)
    for field in ("ready", "profileLoaded", "artifactValidated", "modelLoaded", "adapterLoaded"):
        with pytest.raises(RuntimeError):
            check({**valid, field: False})
    with pytest.raises(RuntimeError):
        check({"status": "UP", "service": "ai-service"})


def test_run_all_keeps_service_and_tunnel_running() -> None:
    class RunningProcess:
        def poll(self):
            return None

        def terminate(self):
            pytest.fail("Run All must not stop the service")

    process = RunningProcess()
    namespace = dict(ai_process=process, tunnel_process=process, READY=True)
    exec(_cell("manual-stop"), namespace)
    assert namespace["READY"] is True


def test_smoke_fixtures_run_from_the_bundle_without_kernel_service_settings(tmp_path: Path) -> None:
    namespace: dict = {}
    exec(_cell("embedded-source"), namespace)
    with zipfile.ZipFile(io.BytesIO(base64.b64decode(namespace["SOURCE_ZIP_B64"]))) as bundle:
        bundle.extractall(tmp_path)
    assignments = ast.parse(_cell("public-smoke")).body
    code = next(ast.literal_eval(node.value) for node in assignments
                if isinstance(node, ast.Assign)
                and any(isinstance(target, ast.Name) and target.id == "fixture_code" for target in node.targets))
    environment = {key: value for key, value in os.environ.items() if not key.startswith("AI_")}
    result = subprocess.run([sys.executable, "-c", code], cwd=tmp_path / "ai-service",
                            env=environment, capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert {value["assistantKey"] for value in json.loads(result.stdout)} == {
        "ADMIN_ASSISTANT", "LAB_ASSISTANT", "RESEARCH_ASSISTANT",
    }


def test_notebook_code_is_plain_compilable_python_and_has_no_saved_outputs() -> None:
    notebook = json.loads(NOTEBOOK.read_text(encoding="utf-8"))
    for cell in notebook["cells"]:
        if cell["cell_type"] == "code":
            compile("".join(cell["source"]), f"{NOTEBOOK}:{cell['id']}", "exec")
            assert cell["outputs"] == []
            assert cell["execution_count"] is None


def test_artifact_export_repairs_only_bytes_matching_the_approved_hash(tmp_path: Path) -> None:
    source = tmp_path / "source"
    source.mkdir()
    original = b'{\r\n  "model": "fixture"\r\n}\r\n'
    approved = original.replace(b"\r\n", b"\n")
    (source / "config.json").write_bytes(original)
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps({"baseModel": {"artifact": {"files": [
        {"path": "config.json", "sha256": hashlib.sha256(approved).hexdigest()},
    ]}}, "assistantAdapters": {}}))
    export = runpy.run_path(str(ROOT / "scripts/prepare-ai-service-kaggle.py"))["artifact_zip"]
    output = tmp_path / "artifacts.zip"
    assert export(manifest, source, output) == 1
    assert (source / "config.json").read_bytes() == original
    with zipfile.ZipFile(output) as bundle:
        assert bundle.read("runtime-artifacts/config.json") == approved
        assert bundle.testzip() is None
    with pytest.raises(FileExistsError):
        export(manifest, source, output)
    (source / "config.json").write_bytes(b"unexpected model data")
    with pytest.raises(ValueError, match="differs from approved bytes"):
        export(manifest, source, tmp_path / "bad.zip")
    assert not (tmp_path / "bad.zip").exists()


def test_platform_port_probe_rejects_a_live_listener() -> None:
    check = runpy.run_path(str(ROOT / "scripts/verify-ai-kaggle-platform.py"))["assert_port_available"]
    with socket.socket() as server:
        server.bind(("127.0.0.1", 0))
        server.listen()
        with pytest.raises(OSError):
            check(server.getsockname()[1])


def test_platform_port_probe_allows_restart_after_a_closed_connection() -> None:
    check = runpy.run_path(str(ROOT / "scripts/verify-ai-kaggle-platform.py"))["assert_port_available"]
    with socket.socket() as server, socket.socket() as client:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("127.0.0.1", 0))
        port = server.getsockname()[1]
        server.listen()
        client.connect(("127.0.0.1", port))
        connection, _ = server.accept()
        with connection:
            connection.shutdown(socket.SHUT_WR)
            assert client.recv(1) == b""
    check(port)


def test_platform_artifact_mirror_crosses_filesystems_without_copying_weights(monkeypatch) -> None:
    probes = runpy.run_path(str(ROOT / "scripts/verify-ai-kaggle-platform.py"))
    linked = []

    def cross_filesystem(*_):
        raise OSError(errno.EXDEV, "different filesystems")

    monkeypatch.setattr(os, "link", cross_filesystem)
    monkeypatch.setattr(Path, "symlink_to", lambda destination, source: linked.append((destination, source)))
    source, destination = Path("readonly/model.safetensors"), Path("owned/model.safetensors")
    probes["link_artifact"](source, destination)
    assert linked == [(destination, source)]


def test_platform_artifact_mirror_preserves_permission_errors(monkeypatch) -> None:
    probes = runpy.run_path(str(ROOT / "scripts/verify-ai-kaggle-platform.py"))

    def denied(*_):
        raise PermissionError(errno.EACCES, "denied")

    monkeypatch.setattr(os, "link", denied)
    monkeypatch.setattr(Path, "symlink_to", lambda *_: pytest.fail("Do not bypass permission failures"))
    with pytest.raises(PermissionError):
        probes["link_artifact"](Path("source"), Path("target"))


def test_platform_checksum_streams_weights_instead_of_reading_the_entire_file(tmp_path: Path, monkeypatch) -> None:
    checksum = runpy.run_path(str(ROOT / "scripts/verify-ai-kaggle-platform.py"))["sha256_file"]
    content = b"fixture" * 500000
    weights = tmp_path / "model.safetensors"
    weights.write_bytes(content)
    monkeypatch.setattr(Path, "read_bytes", lambda *_: pytest.fail("Avoid materializing model weights"))
    assert checksum(weights) == hashlib.sha256(content).hexdigest()
