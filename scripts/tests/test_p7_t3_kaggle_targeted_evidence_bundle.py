import hashlib
import importlib.util
import json
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BUILDER_PATH = ROOT / "scripts" / "build-p7-t3-kaggle-targeted-evidence.py"
VALIDATOR_PATH = ROOT / "scripts" / "validate-p7-t3-kaggle-targeted-evidence.py"

EXPECTED_CASES = ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-006"]
EXPECTED_MODEL = {
    "candidateId": "qwen3_4b-R01",
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
EXPECTED_APPROVAL_IDENTITY = (
    "435b5f8576ace851aadebeedc6ada8e40b529789734d27fe0f094a4c565b9518"
)
EXPECTED_FILES = {
    "README.md",
    "bundle-manifest.json",
    "config/p6-t5-benchmark.yaml",
    "config/p7-t3-research-report-eval-governance-request.json",
    "docs/architecture/ai/data-governance.yml",
    "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
    "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml",
    "evals/human-eval-rubric.yaml",
    "evals/p6-t4-evaluation-suites.yaml",
    "evals/p7-t3-research-gap-evaluation-suite.json",
    "evals/p7-t3-research-gap-evaluation-suite.lock.json",
    "evidence/p7-t3-research-report-eval-governance-approval-v2.json",
    "requirements/p7-t3-kaggle-targeted-evidence-requirements.txt",
    "scripts/benchmark-p6-t5.py",
    "scripts/research-gap-evidence-p7-t3.py",
    "scripts/research-model-decision-p7-t3.py",
    "scripts/research-report-eval-governance-p7-t3.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-evaluation-suites.py",
    "scripts/validate-p7-t3-kaggle-targeted-evidence.py",
}


def _load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def _build(tmp_path: Path, suffix: str):
    builder = _load_module(f"p7_t3_bundle_builder_{suffix}", BUILDER_PATH)
    output_dir = tmp_path / suffix / "p7-t3-kaggle-targeted-evidence"
    zip_path = tmp_path / suffix / "p7-t3-kaggle-targeted-evidence.zip"
    output_dir.parent.mkdir(parents=True)
    manifest = builder.build_bundle(
        source_root=ROOT,
        output_dir=output_dir,
        zip_path=zip_path,
        source_commit="1fb5a7e7f353a534b12e33104d0735229afe6367",
    )
    return builder, output_dir, zip_path, manifest


def test_build_produces_exact_payload_and_three_case_manifest(tmp_path: Path):
    _, output_dir, zip_path, manifest = _build(tmp_path, "first")

    actual_files = {
        path.relative_to(output_dir).as_posix()
        for path in output_dir.rglob("*")
        if path.is_file()
    }
    assert actual_files == EXPECTED_FILES
    assert zip_path.is_file()
    assert manifest["manifestVersion"] == "1.0"
    assert manifest["sourceCommit"] == "1fb5a7e7f353a534b12e33104d0735229afe6367"
    assert manifest["target"] == EXPECTED_MODEL
    assert manifest["caseIds"] == EXPECTED_CASES
    assert manifest["governanceApproval"]["artifactIdentity"] == EXPECTED_APPROVAL_IDENTITY
    assert manifest["fileCount"] == len(EXPECTED_FILES)
    assert len(manifest["fileInventory"]) == len(EXPECTED_FILES) - 1

    for item in manifest["fileInventory"]:
        payload = (output_dir / item["path"]).read_bytes()
        assert item["size"] == len(payload)
        assert item["sha256"] == hashlib.sha256(payload).hexdigest()


def test_bundle_and_zip_are_deterministic(tmp_path: Path):
    _, first_dir, first_zip, first_manifest = _build(tmp_path, "first")
    _, second_dir, second_zip, second_manifest = _build(tmp_path, "second")

    assert first_manifest == second_manifest
    assert first_zip.read_bytes() == second_zip.read_bytes()
    assert (first_dir / "bundle-manifest.json").read_bytes() == (
        second_dir / "bundle-manifest.json"
    ).read_bytes()

    with zipfile.ZipFile(first_zip) as archive:
        assert archive.namelist() == sorted(archive.namelist())
        assert set(archive.namelist()) == EXPECTED_FILES


def test_bundle_validator_accepts_committed_approval_and_payload(tmp_path: Path):
    _, output_dir, _, _ = _build(tmp_path, "valid")
    validator = _load_module("p7_t3_bundle_validator_valid", VALIDATOR_PATH)
    before = {
        path.relative_to(output_dir).as_posix()
        for path in output_dir.rglob("*")
        if path.is_file()
    }

    result = validator.validate_bundle(output_dir)

    assert result["status"] == "VALID"
    assert result["caseIds"] == EXPECTED_CASES
    assert result["target"] == EXPECTED_MODEL
    assert result["approvalArtifactIdentity"] == EXPECTED_APPROVAL_IDENTITY
    after = {
        path.relative_to(output_dir).as_posix()
        for path in output_dir.rglob("*")
        if path.is_file()
    }
    assert after == before


def test_bundle_validator_rejects_tampering(tmp_path: Path):
    _, output_dir, _, _ = _build(tmp_path, "tampered")
    validator = _load_module("p7_t3_bundle_validator_tampered", VALIDATOR_PATH)
    requirements = (
        output_dir
        / "requirements"
        / "p7-t3-kaggle-targeted-evidence-requirements.txt"
    )
    requirements.write_text(requirements.read_text(encoding="utf-8") + "# tampered\n")

    try:
        validator.validate_bundle(output_dir)
    except ValueError as exc:
        assert "sha256" in str(exc)
    else:
        raise AssertionError("Tampered bundle unexpectedly validated")


def test_runbook_is_targeted_and_stops_before_decision(tmp_path: Path):
    _, output_dir, _, manifest = _build(tmp_path, "runbook")
    readme = (output_dir / "README.md").read_text(encoding="utf-8")

    assert "!nvidia-smi" in readme
    assert "snapshot_download(" in readme
    assert EXPECTED_MODEL["identifier"] in readme
    assert EXPECTED_MODEL["revision"] in readme
    assert "model-identity.json" in readme
    assert "%env HF_HUB_OFFLINE=1" in readme
    assert "%env TRANSFORMERS_OFFLINE=1" in readme
    assert "scripts/research-gap-evidence-p7-t3.py --run" in readme
    assert "p7-t3-research-report-eval-governance-approval-v2.json" in readme
    assert "qwen3_4b-R01-P7T3-GAP01.json" in readme
    assert "qwen3_4b-R01-P7T3-GAP01-review-input.json" in readme
    assert "--freeze" not in readme
    assert "--decide" not in readme
    assert "training-pipeline-p7-t2.py --" not in readme
    assert "P7-T4" not in readme
    assert "P7-T5" not in readme
    assert manifest["caseIds"] == EXPECTED_CASES


def test_manifest_has_no_machine_specific_paths(tmp_path: Path):
    _, _, _, manifest = _build(tmp_path, "portable")
    serialized = json.dumps(manifest, sort_keys=True)

    assert "D:\\" not in serialized
    assert "C:\\" not in serialized
    assert "/kaggle/" not in serialized
