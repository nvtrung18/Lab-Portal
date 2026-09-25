#!/usr/bin/env python3
"""Validate the portable P7-T3 targeted-evidence bundle and run outputs."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import sys
from typing import Any


MANIFEST_NAME = "bundle-manifest.json"
MANIFEST_TYPE = "P7-T3-KAGGLE-TARGETED-EVIDENCE-BUNDLE-MANIFEST"
MANIFEST_VERSION = "1.0"
BUNDLE_VERSION = "1.0.0"
CASE_IDS = ["E-AUTH-011", "E-AUTH-012", "E-FUNC-RESEARCH-006"]
TARGET = {
    "candidateId": "qwen3_4b-R01",
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
MODEL_METADATA = {
    "candidateId": "qwen3_4b",
    "sourceRunId": TARGET["candidateId"],
    "identifier": TARGET["identifier"],
    "revision": TARGET["revision"],
}
APPROVAL_PATH = "evidence/p7-t3-research-report-eval-governance-approval-v2.json"
APPROVAL_IDENTITY = "435b5f8576ace851aadebeedc6ada8e40b529789734d27fe0f094a4c565b9518"
SUITE = {
    "id": "P7-T3-RESEARCH-GAP-EVALUATION",
    "version": "1.1.0",
    "digest": "95ff79e0ec38c6d8ebfbcca05f0320b49819bf4f61e99c5b3b5c52cd559c83a5",
}
EXPECTED_PAYLOAD_PATHS = {
    "README.md",
    "config/p6-t5-benchmark.yaml",
    "config/p7-t3-research-report-eval-governance-request.json",
    "docs/architecture/ai/data-governance.yml",
    "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
    "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml",
    "evals/human-eval-rubric.yaml",
    "evals/p6-t4-evaluation-suites.yaml",
    "evals/p7-t3-research-gap-evaluation-suite.json",
    "evals/p7-t3-research-gap-evaluation-suite.lock.json",
    APPROVAL_PATH,
    "requirements/p7-t3-kaggle-targeted-evidence-requirements.txt",
    "scripts/benchmark-p6-t5.py",
    "scripts/research-gap-evidence-p7-t3.py",
    "scripts/research-model-decision-p7-t3.py",
    "scripts/research-report-eval-governance-p7-t3.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-evaluation-suites.py",
    "scripts/validate-p7-t3-kaggle-targeted-evidence.py",
}
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


class DuplicateJsonKeyError(ValueError):
    """Raised when JSON contains an ambiguous duplicate key."""


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateJsonKeyError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"{path}: cannot load JSON: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{path}: JSON object required")
    return value


def _canonical_bytes(value: object) -> bytes:
    try:
        return json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as error:
        raise ValueError(f"canonical JSON required: {error}") from error


def _sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _load_runner(bundle_root: Path) -> Any:
    path = bundle_root / "scripts" / "research-gap-evidence-p7-t3.py"
    name = f"p7_t3_kaggle_runner_{hashlib.sha256(str(path).encode()).hexdigest()[:12]}"
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise ValueError(f"runner unavailable: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    previous_bytecode_setting = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        spec.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous_bytecode_setting
    return module


def _safe_payload_path(bundle_root: Path, logical_path: str) -> Path:
    logical = PurePosixPath(logical_path)
    if logical.is_absolute() or not logical.parts or ".." in logical.parts:
        raise ValueError(f"manifest path is not portable: {logical_path}")
    path = (bundle_root / Path(*logical.parts)).resolve()
    root = bundle_root.resolve()
    if path == root or root not in path.parents:
        raise ValueError(f"manifest path escapes bundle: {logical_path}")
    return path


def _validate_manifest(bundle_root: Path) -> dict[str, Any]:
    manifest = _load_json(bundle_root / MANIFEST_NAME)
    required_fields = {
        "artifactType",
        "manifestVersion",
        "bundleVersion",
        "sourceCommit",
        "target",
        "caseIds",
        "governanceApproval",
        "suite",
        "inventoryScope",
        "fileCount",
        "fileInventory",
        "bundleIdentity",
    }
    if set(manifest) != required_fields:
        raise ValueError("manifest: exact fields required")
    if (
        manifest["artifactType"] != MANIFEST_TYPE
        or manifest["manifestVersion"] != MANIFEST_VERSION
        or manifest["bundleVersion"] != BUNDLE_VERSION
    ):
        raise ValueError("manifest: supported type and versions required")
    source_commit = manifest["sourceCommit"]
    if not isinstance(source_commit, str) or not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise ValueError("manifest/sourceCommit: full Git commit required")
    if manifest["target"] != TARGET or manifest["caseIds"] != CASE_IDS:
        raise ValueError("manifest: exact target and three-case selection required")
    if manifest["suite"] != SUITE:
        raise ValueError("manifest/suite: exact locked suite identity required")
    if manifest["governanceApproval"] != {
        "path": APPROVAL_PATH,
        "artifactIdentity": APPROVAL_IDENTITY,
        "status": "APPROVED",
        "permittedPurposes": ["EVALUATION", "HUMAN_EVALUATION"],
    }:
        raise ValueError("manifest/governanceApproval: exact approval binding required")
    if manifest["inventoryScope"] != "ALL_PAYLOAD_FILES_EXCEPT_THIS_MANIFEST":
        raise ValueError("manifest/inventoryScope: unsupported scope")

    inventory = manifest["fileInventory"]
    if not isinstance(inventory, list):
        raise ValueError("manifest/fileInventory: list required")
    paths: list[str] = []
    for index, item in enumerate(inventory):
        if not isinstance(item, dict) or set(item) != {"path", "size", "sha256"}:
            raise ValueError(f"manifest/fileInventory/{index}: exact fields required")
        logical_path = item["path"]
        if not isinstance(logical_path, str):
            raise ValueError(f"manifest/fileInventory/{index}/path: string required")
        path = _safe_payload_path(bundle_root, logical_path)
        if not path.is_file():
            raise ValueError(f"manifest payload missing: {logical_path}")
        payload = path.read_bytes()
        if not isinstance(item["sha256"], str) or not SHA256_PATTERN.fullmatch(item["sha256"]):
            raise ValueError(f"manifest sha256 invalid: {logical_path}")
        if item["sha256"] != _sha256_bytes(payload):
            raise ValueError(f"manifest sha256 mismatch: {logical_path}")
        if item["size"] != len(payload):
            raise ValueError(f"manifest size mismatch: {logical_path}")
        paths.append(logical_path)
    if paths != sorted(paths) or len(paths) != len(set(paths)):
        raise ValueError("manifest/fileInventory: sorted unique paths required")
    if set(paths) != EXPECTED_PAYLOAD_PATHS:
        raise ValueError("manifest/fileInventory: exact minimal payload required")
    if manifest["fileCount"] != len(inventory) + 1:
        raise ValueError("manifest/fileCount: manifest-inclusive count required")
    identity = manifest["bundleIdentity"]
    if not isinstance(identity, str) or not SHA256_PATTERN.fullmatch(identity):
        raise ValueError("manifest/bundleIdentity: SHA-256 required")
    expected_identity = _sha256_bytes(
        _canonical_bytes({key: value for key, value in manifest.items() if key != "bundleIdentity"})
    )
    if identity != expected_identity:
        raise ValueError("manifest/bundleIdentity: canonical identity mismatch")
    return manifest


def _validate_governance_and_suite(bundle_root: Path) -> tuple[Any, dict[str, Any], dict[str, Any]]:
    runner = _load_runner(bundle_root)
    base_suite = runner._load(runner.BASE_SUITE_PATH)
    gap_suite = runner._load(runner.GAP_SUITE_PATH)
    gap_lock = runner._load(runner.GAP_LOCK_PATH)
    benchmark_config = runner._load(runner.BENCHMARK_CONFIG_PATH)
    request = runner._load(runner.GOVERNANCE_REQUEST_PATH)
    approval = runner._load(bundle_root / APPROVAL_PATH)
    runner.P7T3.validate_gap_suite_lock(
        gap_suite,
        runner.GAP_SUITE_PATH,
        gap_lock,
        base_suite,
        benchmark_config,
    )
    runner.GOVERNANCE.validate_request(
        request,
        runner.GOVERNANCE.load_document(runner.GOVERNANCE.GOVERNANCE_PATH),
        runner.GOVERNANCE.load_document(runner.GOVERNANCE.FIXTURE_PATH),
        runner.GOVERNANCE.load_document(runner.GOVERNANCE.SCHEMA_PATH),
        gap_suite,
    )
    runner.GOVERNANCE.validate_execution_authorization(request, approval, purpose="EVALUATION")
    runner.GOVERNANCE.validate_execution_authorization(
        request,
        approval,
        purpose="HUMAN_EVALUATION",
    )
    execution_suite = runner.select_execution_suite(
        gap_suite,
        CASE_IDS,
        governance_request=request,
        governance_approval=approval,
    )
    selected = sorted(case["evalCaseId"] for case in execution_suite["caseInventory"])
    if selected != CASE_IDS:
        raise ValueError("execution suite: exact three-case inventory required")
    if gap_suite["executionPolicy"].get("sourceRunId") != TARGET["candidateId"]:
        raise ValueError("execution suite: exact candidate required")
    if gap_suite["executionPolicy"].get("model") != {
        "identifier": TARGET["identifier"],
        "revision": TARGET["revision"],
    }:
        raise ValueError("execution suite: exact model identity required")
    if approval.get("artifactIdentity") != APPROVAL_IDENTITY:
        raise ValueError("governance approval: artifact identity mismatch")
    return runner, gap_suite, execution_suite


def validate_bundle(bundle_root: Path) -> dict[str, Any]:
    bundle_root = bundle_root.resolve()
    if not bundle_root.is_dir():
        raise ValueError(f"bundle root unavailable: {bundle_root}")
    manifest = _validate_manifest(bundle_root)
    _validate_governance_and_suite(bundle_root)
    return {
        "status": "VALID",
        "bundleIdentity": manifest["bundleIdentity"],
        "fileCount": manifest["fileCount"],
        "target": TARGET,
        "caseIds": CASE_IDS,
        "approvalArtifactIdentity": APPROVAL_IDENTITY,
        "suite": SUITE,
    }


def validate_run_artifacts(
    bundle_root: Path,
    run_artifact_path: Path,
    review_input_path: Path,
) -> dict[str, Any]:
    validate_bundle(bundle_root)
    runner, gap_suite, execution_suite = _validate_governance_and_suite(bundle_root.resolve())
    approval = runner._load(bundle_root.resolve() / APPROVAL_PATH)
    run_artifact = _load_json(run_artifact_path)
    review_input = _load_json(review_input_path)
    candidate = run_artifact.get("candidate")
    if not isinstance(candidate, dict):
        raise ValueError("run artifact: candidate object required")
    if candidate.get("candidateRunId") != runner.RUN_ID:
        raise ValueError("run artifact: exact candidate run ID required")
    if candidate.get("modelMetadata") != MODEL_METADATA:
        raise ValueError("run artifact: exact model metadata required")
    if run_artifact.get("caseSelection") != CASE_IDS:
        raise ValueError("run artifact: exact three-case selection required")
    if run_artifact.get("checkpoint") != "AWAITING_USER:HUMAN_EVALUATION":
        raise ValueError("run artifact: human-review checkpoint required")
    expected_approval_binding = {
        "requestIdentity": approval["requestIdentity"],
        "approvalIdentity": APPROVAL_IDENTITY,
    }
    if run_artifact.get("governanceApproval") != expected_approval_binding:
        raise ValueError("run artifact: governance approval binding mismatch")
    expected_run = runner.build_run_artifact(
        execution_suite,
        candidate,
        raw_outputs=run_artifact.get("rawOutputs", []),
        governance_approval=approval,
    )
    if run_artifact != expected_run:
        raise ValueError("run artifact: deterministic reconstruction mismatch")
    expected_review = runner.build_review_input(gap_suite, expected_run)
    if review_input != expected_review:
        raise ValueError("review input: deterministic reconstruction mismatch")
    review_text = json.dumps(review_input, ensure_ascii=False, sort_keys=True)
    if runner.LOCAL_PATH_PATTERN.search(review_text) or "/kaggle/" in review_text:
        raise ValueError("review input: machine-local path was not sanitized")
    return {
        "status": "AWAITING_USER:HUMAN_EVALUATION",
        "candidateRunId": runner.RUN_ID,
        "caseIds": CASE_IDS,
        "runArtifact": run_artifact_path.name,
        "reviewInput": review_input_path.name,
        "governanceApprovalIdentity": APPROVAL_IDENTITY,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle-root", type=Path, default=Path.cwd())
    parser.add_argument("--run-artifact", type=Path)
    parser.add_argument("--review-input", type=Path)
    args = parser.parse_args()
    try:
        if (args.run_artifact is None) != (args.review_input is None):
            raise ValueError("--run-artifact and --review-input must be supplied together")
        result = (
            validate_run_artifacts(
                args.bundle_root,
                args.run_artifact,
                args.review_input,
            )
            if args.run_artifact is not None and args.review_input is not None
            else validate_bundle(args.bundle_root)
        )
        print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except Exception as error:  # Keep the portable CLI fail-closed with one diagnostic format.
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
