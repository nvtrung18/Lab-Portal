#!/usr/bin/env python3
"""Fail-closed P7-T4 Research shared-base versus adapter evaluation."""
from __future__ import annotations

import argparse
import gc
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from datetime import datetime
from pathlib import Path
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
SCHEMA_VERSION = "1.0.0"
SUITE_ID = "P7-T4-RESEARCH-INDEPENDENT-EVALUATION"
SUITE_VERSION = "1.0.0"
ASSISTANT_KEY = "RESEARCH_ASSISTANT"
REPETITIONS = ("R01", "R02", "R03")
RUN_ARTIFACT_TYPE = "P7-T4-RESEARCH-INDEPENDENT-EVALUATION-RUN"
COMPARISON_ARTIFACT_TYPE = "P7-T4-RESEARCH-INDEPENDENT-COMPARISON"
MODEL_VARIANTS = ("SHARED_BASE", "RESEARCH_ADAPTER")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
FINDING_CASE_PATTERN = re.compile(r"\((E-[A-Z0-9-]+)\)$")


class P7T4Error(ValueError):
    """Stable fail-closed error for invalid P7-T4 inputs or evidence."""


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise P7T4Error(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


P7T3 = _load_module("p7t3_for_p7t4", ROOT / "scripts" / "research-model-decision-p7-t3.py")
P7T3_GAP = _load_module("p7t3_gap_for_p7t4", ROOT / "scripts" / "research-gap-evidence-p7-t3.py")
P6_EVALUATOR = _load_module(
    "p6_evaluator_for_p7t4", ROOT / "scripts" / "validate-evaluation-suites.py"
)
P6_BENCHMARK = _load_module(
    "p6_benchmark_for_p7t4", ROOT / "scripts" / "benchmark-p6-t5.py"
)
P7T2 = _load_module("p7t2_for_p7t4", ROOT / "scripts" / "p7-t2-real-training.py")


def canonical_bytes(value: object) -> bytes:
    try:
        return json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as error:
        raise P7T4Error(f"canonical JSON required: {error}") from error


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise P7T4Error(f"JSON artifact invalid: {path}") from error
    if not isinstance(value, dict):
        raise P7T4Error(f"JSON object required: {path}")
    return value


def _load_yaml(path: Path) -> dict[str, Any]:
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as error:
        raise P7T4Error(f"YAML artifact invalid: {path}") from error
    if not isinstance(value, dict):
        raise P7T4Error(f"YAML object required: {path}")
    return value


def _atomic_write_json(path: Path, value: object, *, append_only: bool = True) -> None:
    if append_only and path.exists():
        raise P7T4Error(f"immutable evidence already exists: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.",
            suffix=".tmp", delete=False,
        ) as handle:
            temporary = Path(handle.name)
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def artifact_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != "artifactIdentity"}))


def request_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != "requestIdentity"}))


def _suite_identity(value: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != "suiteDigest"}))


def suite_binding(suite: dict[str, Any]) -> dict[str, str]:
    return {
        "id": suite["suiteId"],
        "version": suite["suiteVersion"],
        "digest": suite["suiteDigest"],
    }


def _adapter_inventory(adapter_directory: Path) -> list[dict[str, Any]]:
    result = []
    for path in sorted(
        (item for item in adapter_directory.iterdir() if item.is_file()),
        key=lambda item: item.name,
    ):
        if path.name == "adapter-manifest.json":
            continue
        payload = path.read_bytes()
        result.append(
            {"filename": path.name, "sha256": sha256_bytes(payload), "size": len(payload)}
        )
    return result


def validate_adapter_candidate(
    adapter_directory: Path, manifest: dict[str, Any]
) -> dict[str, str]:
    adapter_directory = adapter_directory.resolve()
    required = {
        "schemaVersion", "pipelineVersion", "backend", "realTraining", "adapterDisposition",
        "qualityEvidence", "assistantKey", "baseModel", "datasetIdentity",
        "trainingConfigIdentity", "trainingRunIdentity", "candidateId", "adapterIdentity",
        "seed", "sourceCommit", "artifacts",
    }
    if not adapter_directory.is_dir() or not isinstance(manifest, dict) or set(manifest) != required:
        raise P7T4Error("adapter candidate contract invalid")
    if (
        manifest.get("schemaVersion") != SCHEMA_VERSION
        or manifest.get("backend") != "REAL_QLORA"
        or manifest.get("realTraining") is not True
        or manifest.get("adapterDisposition") != "CANDIDATE_ONLY"
        or manifest.get("qualityEvidence") != "REAL_TRAINING_EXECUTION"
        or manifest.get("assistantKey") != ASSISTANT_KEY
    ):
        raise P7T4Error("adapter candidate must be real CANDIDATE_ONLY Research evidence")
    inventory = _adapter_inventory(adapter_directory)
    if manifest.get("artifacts") != inventory:
        raise P7T4Error("adapter candidate inventory mismatch")
    names = {item["filename"] for item in inventory}
    if not {"adapter_config.json", "adapter_model.safetensors"}.issubset(names):
        raise P7T4Error("adapter candidate inventory requires PEFT config and safetensors")
    if any(
        name in {"model.safetensors", "pytorch_model.bin"} or name.startswith("model-")
        for name in names
    ):
        raise P7T4Error("adapter candidate inventory must not contain base-model weights")
    for field in ("datasetIdentity", "trainingConfigIdentity", "trainingRunIdentity"):
        value = manifest.get(field)
        if not isinstance(value, str) or not SHA256_PATTERN.fullmatch(value):
            raise P7T4Error(f"adapter candidate {field} invalid")
    adapter_identity = sha256_bytes(canonical_bytes(inventory))
    candidate_id = sha256_bytes(
        canonical_bytes(
            {
                "trainingRunIdentity": manifest["trainingRunIdentity"],
                "adapterIdentity": adapter_identity,
            }
        )
    )
    if (
        manifest.get("adapterIdentity") != adapter_identity
        or manifest.get("candidateId") != candidate_id
    ):
        raise P7T4Error("adapter candidate identity mismatch")
    return {"candidateId": candidate_id, "adapterIdentity": adapter_identity}


def validate_evaluation_config(config: dict[str, Any], adapter_manifest: dict[str, Any]) -> None:
    base_fields = {
        "artifactType", "schemaVersion", "assistantKey", "baseModel", "adapter",
        "evaluationSources", "execution", "comparisonPolicy", "review",
    }
    if not isinstance(config, dict):
        raise P7T4Error("evaluation config fields are not closed")
    schema_version = config.get("schemaVersion")
    required = (
        base_fields
        if schema_version == SCHEMA_VERSION
        else base_fields | {"evaluationContract", "executionApproval", "runtimeControls"}
        if schema_version == "2.0.0"
        else set()
    )
    if set(config) != required:
        raise P7T4Error("evaluation config fields are not closed")
    if (
        config.get("artifactType") != "P7-T4-RESEARCH-INDEPENDENT-EVALUATION-CONFIG"
        or config.get("assistantKey") != ASSISTANT_KEY
        or config.get("baseModel") != adapter_manifest.get("baseModel")
    ):
        raise P7T4Error("evaluation config identity mismatch")
    adapter = config.get("adapter")
    if not isinstance(adapter, dict) or set(adapter) != {
        "candidateId", "adapterIdentity", "manifestReference", "evidenceReference", "disposition",
    }:
        raise P7T4Error("evaluation config adapter binding invalid")
    if (
        adapter.get("candidateId") != adapter_manifest.get("candidateId")
        or adapter.get("adapterIdentity") != adapter_manifest.get("adapterIdentity")
        or adapter.get("disposition") != "CANDIDATE_ONLY"
    ):
        raise P7T4Error("evaluation config adapter identity mismatch")
    sources = config.get("evaluationSources")
    expected_sources = {
        "baseSuite", "baseLock", "baseGovernanceApproval", "gapSuite", "gapLock",
        "reportGovernanceRequest", "reportGovernanceApproval",
    }
    if not isinstance(sources, dict) or set(sources) != expected_sources:
        raise P7T4Error("evaluation config source references invalid")
    references = [adapter["manifestReference"], adapter["evidenceReference"], *sources.values()]
    if any(
        not isinstance(reference, str)
        or not reference.strip()
        or Path(reference).is_absolute()
        or ".." in Path(reference).parts
        for reference in references
    ):
        raise P7T4Error("evaluation config references must be repository-relative")
    execution = config.get("execution")
    if not isinstance(execution, dict) or set(execution) != {
        "repetitions", "promptProfileReference", "requirementsReference", "device",
        "loadIn4Bit", "pythonVersion", "gpuModel",
    }:
        raise P7T4Error("evaluation config execution contract invalid")
    if (
        execution.get("repetitions") != list(REPETITIONS)
        or execution.get("promptProfileReference")
        != "config/p6-t5-benchmark-kaggle-linux-cp312.yaml"
        or execution.get("requirementsReference")
        != "requirements/p7-t2-real-training-t4-cp313-requirements.txt"
        or execution.get("device") != "cuda:0"
        or execution.get("loadIn4Bit") is not True
        or execution.get("pythonVersion") != "3.13.15"
        or execution.get("gpuModel") != "Tesla T4"
    ):
        raise P7T4Error("evaluation config execution values invalid")
    policy = config.get("comparisonPolicy")
    if not isinstance(policy, dict) or policy != {
        "allAdapterCasesMustPass": True,
        "basePassToAdapterFailIsRegression": True,
        "criticalSafetyRegressionAllowed": False,
        "resourceIncreaseThreshold": None,
    }:
        raise P7T4Error("evaluation comparison policy invalid")
    review = config.get("review")
    if not isinstance(review, dict) or review != {
        "humanEvaluationRequired": True,
        "independentReviewerRequired": True,
        "reviewRun": "R01",
        "promotionWithoutReviewAllowed": False,
    }:
        raise P7T4Error("evaluation review policy invalid")
    if schema_version == "2.0.0":
        contract = config.get("evaluationContract")
        if not isinstance(contract, dict) or set(contract) != {
            "approvalIdentity", "approvalReference", "evaluatorIdentity",
            "evaluatorReference", "evaluatorVersion", "suiteIdentity",
            "suiteReference", "suiteVersion",
        }:
            raise P7T4Error("evaluation v2 contract binding invalid")
        if (
            contract.get("evaluatorVersion") != "2.0.0"
            or contract.get("suiteVersion") != "2.0.0"
            or any(
                not isinstance(contract.get(field), str)
                or not SHA256_PATTERN.fullmatch(contract[field])
                for field in ("approvalIdentity", "evaluatorIdentity", "suiteIdentity")
            )
        ):
            raise P7T4Error("evaluation v2 contract identity invalid")
        approval = config.get("executionApproval")
        if not isinstance(approval, dict) or approval != {
            "approvalReference": "evidence/p7-t4-research-remediation-v5-external-evaluation-approval.json",
            "required": True,
        }:
            raise P7T4Error("external evaluation approval binding invalid")
        controls = config.get("runtimeControls")
        if controls != {
            "constrainedDecodingAllowed": False,
            "runtimeNormalizationAllowed": False,
        }:
            raise P7T4Error("unapproved evaluation runtime controls")
        for reference in (
            contract["approvalReference"], contract["evaluatorReference"],
            contract["suiteReference"], approval["approvalReference"],
        ):
            if Path(reference).is_absolute() or ".." in Path(reference).parts:
                raise P7T4Error("evaluation v2 references must be repository-relative")


def compose_research_evaluation_suite(
    base_suite: dict[str, Any],
    gap_suite: dict[str, Any],
    governance_request: dict[str, Any],
    governance_approval: dict[str, Any],
) -> dict[str, Any]:
    """Compose the approved Research-only view without mutating either frozen source."""
    try:
        P7T3.validate_research_suite(base_suite)
        P7T3.validate_gap_suite(gap_suite, base_suite)
        approved_gap = P7T3_GAP.select_execution_suite(
            gap_suite,
            gap_suite["executionPolicy"]["postApprovalCaseIds"],
            governance_request=governance_request,
            governance_approval=governance_approval,
        )
    except Exception as error:
        raise P7T4Error(f"governance-bound Research suite invalid: {error}") from error

    base_cases = [
        case
        for case in base_suite["caseInventory"]
        if (
            case.get("assistantKey") == ASSISTANT_KEY
            or "PROMPT_INJECTION" in case.get("suiteTags", [])
        )
        and case.get("caseState") != "DEFERRED_ASSERTION_ONLY"
    ]
    gap_cases = approved_gap["caseInventory"]
    cases = sorted([*base_cases, *gap_cases], key=lambda case: case["evalCaseId"])
    case_ids = [case["evalCaseId"] for case in cases]
    if len(case_ids) != len(set(case_ids)) or not cases:
        raise P7T4Error("Research evaluation case inventory must be non-empty and unique")

    observations: dict[str, Any] = {}
    gap_case_ids = {item["evalCaseId"] for item in gap_cases}
    for case in cases:
        source = approved_gap if case["evalCaseId"] in gap_case_ids else base_suite
        observation_id = case["expectedObservationId"]
        if observation_id in observations or observation_id not in source["expectedObservations"]:
            raise P7T4Error("Research evaluation observation inventory mismatch")
        observations[observation_id] = source["expectedObservations"][observation_id]

    human_profiles = {"DRAFT_RESEARCH": [], "REFUSAL": [], "NONE": []}
    for case in cases:
        profile = case.get("humanProfileId") or "NONE"
        if profile not in human_profiles:
            raise P7T4Error(f"unsupported Research human profile: {profile}")
        human_profiles[profile].append(case["evalCaseId"])
    for values in human_profiles.values():
        values.sort()

    suite: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-INDEPENDENT-EVALUATION-SUITE",
        "schemaVersion": SCHEMA_VERSION,
        "suiteId": SUITE_ID,
        "suiteVersion": SUITE_VERSION,
        "EVALUATION_ONLY": True,
        "TRAINING_PROHIBITED": True,
        "executionProfile": {
            "assistantKey": ASSISTANT_KEY,
            "frozenProfileOverrideCaseIds": sorted(
                case["evalCaseId"]
                for case in base_cases
                if case.get("assistantKey") != ASSISTANT_KEY
            ),
        },
        "sourceSuites": {
            "base": {
                "id": base_suite["suiteId"],
                "version": base_suite["suiteVersion"],
                "digest": gap_suite["baseSuite"]["digest"],
            },
            "gap": {
                "id": gap_suite["suiteId"],
                "version": gap_suite["suiteVersion"],
                "digest": gap_suite["suiteDigest"],
            },
        },
        "governance": {
            "reportEvaluationApprovalIdentity": governance_approval["artifactIdentity"],
            "purpose": "EVALUATION",
            "trainingAllowed": False,
        },
        "caseInventory": cases,
        "expectedObservations": observations,
        "matrices": {"humanApplicabilityBinding": human_profiles},
    }
    suite["suiteDigest"] = _suite_identity(suite)
    return suite


def evaluator_for_suite(root: Path, suite: dict[str, Any]):
    contract = suite.get("evaluatorContract")
    if contract is None:
        return P6_EVALUATOR
    if not isinstance(contract, dict) or set(contract) != {
        "id", "identity", "reference", "version",
    }:
        raise P7T4Error("evaluation suite evaluator binding invalid")
    reference = contract.get("reference")
    if (
        contract.get("id") != "P7-T4-RESEARCH-EVALUATOR"
        or contract.get("version") != "2.0.0"
        or not isinstance(contract.get("identity"), str)
        or not SHA256_PATTERN.fullmatch(contract["identity"])
        or not isinstance(reference, str)
        or Path(reference).is_absolute()
        or ".." in Path(reference).parts
    ):
        raise P7T4Error("evaluation suite evaluator identity invalid")
    evaluator = _load_module("p7t4_v2_evaluator_for_execution", root / "scripts/validate-p7-t4-research-evaluation-v2.py")
    if (
        evaluator.EVALUATOR_ID != contract["id"]
        or evaluator.EVALUATOR_VERSION != contract["version"]
    ):
        raise P7T4Error("evaluation v2 implementation identity invalid")
    return evaluator


def load_v2_evaluation_contract(
    root: Path,
    config: dict[str, Any],
    base_lock: dict[str, Any],
    gap_suite: dict[str, Any],
) -> tuple[dict[str, Any], object, dict[str, Any]]:
    contract = config["evaluationContract"]
    evaluator_contract = _load_json(root / contract["evaluatorReference"])
    suite = _load_json(root / contract["suiteReference"])
    amendment_approval = _load_json(root / contract["approvalReference"])
    execution_approval = _load_json(
        root / config["executionApproval"]["approvalReference"]
    )
    execution_request = _load_json(root / execution_approval["requestReference"])
    if (
        evaluator_contract.get("artifactIdentity") != artifact_identity(evaluator_contract)
        or evaluator_contract.get("artifactIdentity") != contract["evaluatorIdentity"]
        or evaluator_contract.get("evaluatorVersion") != contract["evaluatorVersion"]
        or evaluator_contract.get("status") != "APPROVED"
        or evaluator_contract.get("useAllowed") is not True
        or evaluator_contract.get("runtimeNormalizationAllowed") is not False
        or evaluator_contract.get("constrainedDecodingAllowed") is not False
    ):
        raise P7T4Error("approved evaluator v2 contract invalid")
    if (
        suite.get("suiteDigest") != _suite_identity(suite)
        or suite.get("suiteDigest") != contract["suiteIdentity"]
        or suite.get("suiteVersion") != contract["suiteVersion"]
        or suite.get("status") != "APPROVED"
        or suite.get("activationAllowed") is not True
        or suite.get("externalExecutionAllowed") is not False
        or suite.get("EVALUATION_ONLY") is not True
        or suite.get("TRAINING_PROHIBITED") is not True
        or suite.get("sourceSuites", {}).get("base", {}).get("digest")
        != base_lock.get("suiteDigest")
        or suite.get("sourceSuites", {}).get("gap", {}).get("digest")
        != gap_suite.get("suiteDigest")
    ):
        raise P7T4Error("approved evaluation suite v2 invalid")
    approved_artifacts = amendment_approval.get("approvedArtifacts", {})
    amendment_authorization = amendment_approval.get("authorization", {})
    if (
        amendment_approval.get("artifactIdentity") != artifact_identity(amendment_approval)
        or amendment_approval.get("artifactIdentity") != contract["approvalIdentity"]
        or amendment_approval.get("status") != "APPROVED"
        or approved_artifacts.get("evaluatorIdentity") != contract["evaluatorIdentity"]
        or approved_artifacts.get("suiteIdentity") != contract["suiteIdentity"]
        or amendment_authorization.get("evaluatorV2UseAllowed") is not True
        or amendment_authorization.get("suiteV2UseAllowed") is not True
        or amendment_authorization.get("externalEvaluationExecutionAllowed") is not False
    ):
        raise P7T4Error("evaluator and suite amendment approval invalid")
    authorization = execution_approval.get("authorization")
    if (
        execution_request.get("requestIdentity") != request_identity(execution_request)
        or execution_approval.get("artifactIdentity") != artifact_identity(execution_approval)
        or execution_approval.get("requestIdentity") != execution_request["requestIdentity"]
        or execution_approval.get("status") != "APPROVED"
        or execution_approval.get("approval", {}).get("decision") != "APPROVED"
        or execution_approval.get("approvedCandidate", {}).get("candidateId")
        != config["adapter"]["candidateId"]
        or execution_approval.get("approvedEvaluationContract", {}).get("evaluatorIdentity")
        != contract["evaluatorIdentity"]
        or execution_approval.get("approvedEvaluationContract", {}).get("suiteIdentity")
        != contract["suiteIdentity"]
        or authorization != {
            "constrainedDecodingAllowed": False,
            "externalEvaluationExecutionAllowed": True,
            "promotionAllowed": False,
            "runtimeNormalizationAllowed": False,
        }
    ):
        raise P7T4Error("external evaluation execution approval invalid")
    evaluator = evaluator_for_suite(root, suite)
    if suite["evaluatorContract"]["identity"] != evaluator_contract["artifactIdentity"]:
        raise P7T4Error("suite and evaluator v2 binding mismatch")
    return suite, evaluator, execution_approval


def preflight(root: Path, adapter_directory: Path) -> dict[str, Any]:
    """Validate governed sources and the exact real adapter without model loading."""
    root = root.resolve()
    config_path = root / "config" / "p7-t4-research-independent-evaluation.json"
    config = _load_json(config_path)
    sources = config.get("evaluationSources")
    if not isinstance(sources, dict):
        raise P7T4Error("evaluation source configuration invalid")
    manifest_path = root / config["adapter"]["manifestReference"]
    evidence_path = root / config["adapter"]["evidenceReference"]
    manifest = _load_json(manifest_path)
    evidence = _load_json(evidence_path)
    validate_evaluation_config(config, manifest)

    local_manifest_path = adapter_directory.resolve() / "adapter-manifest.json"
    local_manifest = _load_json(local_manifest_path)
    if local_manifest != manifest or local_manifest_path.read_bytes() != manifest_path.read_bytes():
        raise P7T4Error("adapter payload manifest differs from governed evidence")
    candidate = validate_adapter_candidate(adapter_directory, manifest)
    if (
        evidence.get("artifactType") != "P7-T2-REAL-TRAINING-EXECUTION-EVIDENCE"
        or evidence.get("schemaVersion") != SCHEMA_VERSION
        or evidence.get("backend") != "REAL_QLORA"
        or evidence.get("realTraining") is not True
        or evidence.get("qualityEvidence") != "REAL_TRAINING_EXECUTION"
        or evidence.get("candidateId") != candidate["candidateId"]
        or evidence.get("baseModel") != config["baseModel"]
        or evidence.get("artifactIdentity") != artifact_identity(evidence)
    ):
        raise P7T4Error("real-training execution evidence invalid")
    exported = []
    for path in sorted(
        (item for item in adapter_directory.resolve().iterdir() if item.is_file()),
        key=lambda item: item.name,
    ):
        exported.append(
            {"filename": path.name, "sha256": sha256_bytes(path.read_bytes()), "size": path.stat().st_size}
        )
    if evidence.get("exportedArtifacts") != exported:
        raise P7T4Error("real-training exported adapter inventory mismatch")

    base_path = root / sources["baseSuite"]
    gap_path = root / sources["gapSuite"]
    base_suite = _load_yaml(base_path)
    gap_suite = _load_json(gap_path)
    base_lock = _load_json(root / sources["baseLock"])
    gap_lock = _load_json(root / sources["gapLock"])
    base_approval = _load_json(root / sources["baseGovernanceApproval"])
    if (
        base_approval.get("artifactType")
        != "P7-T1C-FROZEN-EVALUATION-GOVERNANCE-APPROVAL"
        or base_approval.get("schemaVersion") != SCHEMA_VERSION
        or base_approval.get("status") != "APPROVED"
        or base_approval.get("purpose") != "EVALUATION"
        or base_approval.get("approval", {}).get("decision") != "APPROVED"
        or base_approval.get("suite", {}).get("suiteId") != base_suite.get("suiteId")
        or base_approval.get("suite", {}).get("suiteVersion") != base_suite.get("suiteVersion")
        or base_approval.get("suite", {}).get("suiteDigest") != base_lock.get("suiteDigest")
        or base_approval.get("artifactIdentity") != artifact_identity(base_approval)
    ):
        raise P7T4Error("frozen evaluation governance approval invalid")
    benchmark_config = _load_yaml(root / config["execution"]["promptProfileReference"])
    schema = _load_json(root / "evals" / "evaluation-suite.schema.json")
    rubric = _load_yaml(root / "evals" / "human-eval-rubric.yaml")
    governance_binding = _load_yaml(root / "evals" / "p6-t4-evaluation-freeze.binding.yaml")
    base_errors = P6_EVALUATOR.validate_suite(
        base_suite,
        schema,
        rubric,
        base_lock,
        governance_binding,
        P6_EVALUATOR.DATASET_MODEL_WORK_RELEASE,
        root,
    )
    if base_errors:
        raise P7T4Error("governed frozen evaluation invalid: " + "; ".join(base_errors))
    try:
        P7T3.validate_suite_lock(base_suite, base_path, base_lock, benchmark_config)
        P7T3.validate_gap_suite_lock(
            gap_suite, gap_path, gap_lock, base_suite, benchmark_config
        )
    except Exception as error:
        raise P7T4Error(f"evaluation suite lock invalid: {error}") from error
    if config["schemaVersion"] == "2.0.0":
        suite, _, execution_approval = load_v2_evaluation_contract(
            root, config, base_lock, gap_suite
        )
    else:
        suite = compose_research_evaluation_suite(
            base_suite,
            gap_suite,
            _load_json(root / sources["reportGovernanceRequest"]),
            _load_json(root / sources["reportGovernanceApproval"]),
        )
        execution_approval = None
    requirements_path = root / config["execution"]["requirementsReference"]
    try:
        requirements_text = requirements_path.read_text(encoding="utf-8")
    except OSError as error:
        raise P7T4Error("reviewed evaluation runtime lock unavailable") from error
    required_runtime = ("peft==0.15.2 ", "torch==2.7.1+cu118 ", "transformers==4.51.3 ")
    if any(value not in requirements_text for value in required_runtime):
        raise P7T4Error("reviewed evaluation runtime lock is incomplete")
    report: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-INDEPENDENT-EVALUATION-PREFLIGHT",
        "schemaVersion": SCHEMA_VERSION,
        "state": "PREFLIGHT_PASS",
        "candidate": candidate,
        "baseModel": config["baseModel"],
        "suite": suite_binding(suite),
        "caseCount": len(suite["caseInventory"]),
        "sourceIdentities": {
            "adapterManifest": sha256_bytes(manifest_path.read_bytes()),
            "realTrainingEvidence": evidence["artifactIdentity"],
            "baseSuite": sha256_bytes(base_path.read_bytes()),
            "baseGovernanceApproval": base_approval["artifactIdentity"],
            "gapSuite": gap_suite["suiteDigest"],
            "reportGovernanceApproval": suite["governance"]["reportEvaluationApprovalIdentity"],
            "runtimeLock": sha256_bytes(requirements_path.read_bytes()),
        },
        "composedSuite": suite,
    }
    if execution_approval is not None:
        report["sourceIdentities"].update(
            {
                "evaluatorContract": config["evaluationContract"]["evaluatorIdentity"],
                "evaluationSuite": config["evaluationContract"]["suiteIdentity"],
                "externalEvaluationApproval": execution_approval["artifactIdentity"],
            }
        )
    report["artifactIdentity"] = artifact_identity(report)
    return report


def _finding_cases(findings: list[str], prefixes: tuple[str, ...]) -> set[str]:
    result: set[str] = set()
    for finding in findings:
        if not isinstance(finding, str) or not finding.startswith(prefixes):
            continue
        match = FINDING_CASE_PATTERN.search(finding)
        if match:
            result.add(match.group(1))
    return result


def _validate_run(
    run: dict[str, Any], suite: dict[str, Any], variant: str, repetition: str
) -> dict[str, str]:
    required = {
        "artifactType", "schemaVersion", "state", "modelVariant", "repetition", "suite",
        "candidateRun", "automatic", "promptManifest", "rawOutputs", "findings", "metrics",
        "sourceCommit", "artifactIdentity",
    }
    if not isinstance(run, dict) or set(run) != required:
        raise P7T4Error("run artifact fields are not closed")
    if (
        run.get("artifactType") != RUN_ARTIFACT_TYPE
        or run.get("schemaVersion") != SCHEMA_VERSION
        or run.get("state") != "COMPLETE"
        or run.get("modelVariant") != variant
        or run.get("repetition") != repetition
        or run.get("suite") != suite_binding(suite)
        or run.get("artifactIdentity") != artifact_identity(run)
    ):
        raise P7T4Error("run artifact identity or state mismatch")
    automatic = run.get("automatic")
    report = automatic.get("automaticReport") if isinstance(automatic, dict) else None
    expected_ids = {case["evalCaseId"] for case in suite["caseInventory"]}
    if not isinstance(report, list) or len(report) != len(expected_ids):
        raise P7T4Error("run automatic report inventory mismatch")
    states: dict[str, str] = {}
    for item in report:
        if (
            not isinstance(item, dict)
            or set(item) != {"evalCaseId", "candidateCaseDigest", "automaticState"}
            or item.get("evalCaseId") in states
            or item.get("evalCaseId") not in expected_ids
            or item.get("automaticState") not in {"PASS", "FAIL"}
            or not isinstance(item.get("candidateCaseDigest"), str)
            or not SHA256_PATTERN.fullmatch(item["candidateCaseDigest"])
        ):
            raise P7T4Error("run automatic report record invalid")
        states[item["evalCaseId"]] = item["automaticState"]
    if set(states) != expected_ids:
        raise P7T4Error("run automatic report inventory mismatch")
    prompt_manifest = run.get("promptManifest")
    raw_outputs = run.get("rawOutputs")
    if (
        not isinstance(prompt_manifest, list)
        or not isinstance(raw_outputs, list)
        or {item.get("evalCaseId") for item in prompt_manifest if isinstance(item, dict)}
        != expected_ids
        or {item.get("evalCaseId") for item in raw_outputs if isinstance(item, dict)}
        != expected_ids
        or len(prompt_manifest) != len(expected_ids)
        or len(raw_outputs) != len(expected_ids)
    ):
        raise P7T4Error("run raw evidence inventory mismatch")
    for item in prompt_manifest:
        if (
            not isinstance(item, dict)
            or set(item) != {
                "evalCaseId", "sourceAssistantKey", "executionAssistantKey", "promptDigest"
            }
            or item.get("executionAssistantKey") != ASSISTANT_KEY
            or not isinstance(item.get("sourceAssistantKey"), str)
            or not isinstance(item.get("promptDigest"), str)
            or not SHA256_PATTERN.fullmatch(item["promptDigest"])
        ):
            raise P7T4Error("run prompt evidence invalid")
    for item in raw_outputs:
        raw_text = item.get("rawText") if isinstance(item, dict) else None
        if (
            not isinstance(item, dict)
            or set(item) != {"evalCaseId", "rawText", "rawTextDigest"}
            or not isinstance(raw_text, str)
            or item.get("rawTextDigest") != sha256_bytes(raw_text.encode("utf-8"))
        ):
            raise P7T4Error("run raw output evidence invalid")
    metrics = run.get("metrics")
    if (
        not isinstance(metrics, dict)
        or set(metrics) != {"generationLatencyNs", "peakVramBytes", "peakRssBytes"}
        or not isinstance(metrics["generationLatencyNs"], list)
        or not metrics["generationLatencyNs"]
        or any(not isinstance(value, int) or value < 0 for value in metrics["generationLatencyNs"])
        or any(not isinstance(metrics[key], int) or metrics[key] < 0 for key in ("peakVramBytes", "peakRssBytes"))
    ):
        raise P7T4Error("run resource metrics invalid")
    if not isinstance(run.get("findings"), list) or any(not isinstance(item, str) for item in run["findings"]):
        raise P7T4Error("run findings invalid")
    return states


def _resource_summary(runs: list[dict[str, Any]]) -> dict[str, int]:
    latencies = sorted(value for run in runs for value in run["metrics"]["generationLatencyNs"])
    index = max(0, (95 * len(latencies) + 99) // 100 - 1)
    return {
        "p95GenerationLatencyNs": latencies[index],
        "peakVramBytes": max(run["metrics"]["peakVramBytes"] for run in runs),
        "peakRssBytes": max(run["metrics"]["peakRssBytes"] for run in runs),
    }


def compare_model_runs(
    suite: dict[str, Any],
    base_runs: list[dict[str, Any]],
    adapter_runs: list[dict[str, Any]],
) -> dict[str, Any]:
    """Compare paired immutable repetitions and keep promotion human-gated."""
    if len(base_runs) != len(REPETITIONS) or len(adapter_runs) != len(REPETITIONS):
        raise P7T4Error("exact R01-R03 repetitions required for both variants")
    base_by_repetition = {run.get("repetition"): run for run in base_runs if isinstance(run, dict)}
    adapter_by_repetition = {run.get("repetition"): run for run in adapter_runs if isinstance(run, dict)}
    if set(base_by_repetition) != set(REPETITIONS) or set(adapter_by_repetition) != set(REPETITIONS):
        raise P7T4Error("exact R01-R03 repetitions required for both variants")

    case_tags = {case["evalCaseId"]: set(case.get("suiteTags", [])) for case in suite["caseInventory"]}
    base_states: dict[str, dict[str, str]] = {}
    adapter_states: dict[str, dict[str, str]] = {}
    regressions: set[str] = set()
    adapter_failures: set[str] = set()
    for repetition in REPETITIONS:
        base_run = base_by_repetition[repetition]
        adapter_run = adapter_by_repetition[repetition]
        base_states[repetition] = _validate_run(base_run, suite, "SHARED_BASE", repetition)
        adapter_states[repetition] = _validate_run(adapter_run, suite, "RESEARCH_ADAPTER", repetition)
        adapter_failures.update(
            case_id for case_id, state in adapter_states[repetition].items() if state == "FAIL"
        )
        regressions.update(
            case_id
            for case_id in base_states[repetition]
            if base_states[repetition][case_id] == "PASS"
            and adapter_states[repetition][case_id] == "FAIL"
        )

    improved = {
        case_id
        for case_id in case_tags
        if any(base_states[repetition][case_id] == "FAIL" for repetition in REPETITIONS)
        and all(adapter_states[repetition][case_id] == "PASS" for repetition in REPETITIONS)
    }
    base_reference = set().union(*(
        _finding_cases(run["findings"], ("EVAL-FORBIDDEN-REFERENCE",)) for run in base_runs
    ))
    adapter_reference = set().union(*(
        _finding_cases(run["findings"], ("EVAL-FORBIDDEN-REFERENCE",)) for run in adapter_runs
    ))
    base_tool = set().union(*(
        _finding_cases(run["findings"], ("EVAL-ROUTING-",)) for run in base_runs
    ))
    adapter_tool = set().union(*(
        _finding_cases(run["findings"], ("EVAL-ROUTING-",)) for run in adapter_runs
    ))
    regression_report = {
        "all": sorted(regressions),
        "hallucinatedIds": sorted(adapter_reference - base_reference),
        "toolValidity": sorted(adapter_tool - base_tool),
        "promptInjection": sorted(
            case_id for case_id in regressions if "PROMPT_INJECTION" in case_tags[case_id]
        ),
        "criticalSafety": sorted(
            case_id
            for case_id in regressions
            if case_tags[case_id].intersection({"AUTHORIZATION", "PROMPT_INJECTION", "CROSS_DOMAIN"})
        ),
    }
    automatic_pass = not adapter_failures and not any(regression_report.values())
    comparison: dict[str, Any] = {
        "artifactType": COMPARISON_ARTIFACT_TYPE,
        "schemaVersion": SCHEMA_VERSION,
        "state": "AWAITING_INDEPENDENT_REVIEW",
        "suite": suite_binding(suite),
        "runIdentities": {
            "SHARED_BASE": [base_by_repetition[value]["artifactIdentity"] for value in REPETITIONS],
            "RESEARCH_ADAPTER": [adapter_by_repetition[value]["artifactIdentity"] for value in REPETITIONS],
        },
        "automaticDecision": "AUTOMATIC_PASS" if automatic_pass else "AUTOMATIC_FAIL",
        "adapterFailedCaseIds": sorted(adapter_failures),
        "improvedCaseIds": sorted(improved),
        "regressions": regression_report,
        "resourceUse": {
            "SHARED_BASE": _resource_summary(base_runs),
            "RESEARCH_ADAPTER": _resource_summary(adapter_runs),
        },
        "humanEvaluation": {
            "required": True,
            "reviewRun": "R01",
            "state": "PENDING_HUMAN_REVIEW",
        },
        "promotionAllowed": False,
    }
    comparison["artifactIdentity"] = artifact_identity(comparison)
    return comparison


def _human_rubric() -> dict[str, Any]:
    value = yaml.safe_load(
        (ROOT / "evals" / "human-eval-rubric.yaml").read_text(encoding="utf-8")
    )
    if not isinstance(value, dict):
        raise P7T4Error("human rubric invalid")
    return value


def build_human_review_packet(
    suite: dict[str, Any], run: dict[str, Any]
) -> dict[str, Any]:
    """Create a bound blank packet; never manufacture human outcomes."""
    variant = run.get("modelVariant")
    repetition = run.get("repetition")
    if variant not in MODEL_VARIANTS or repetition != "R01":
        raise P7T4Error("human review packet requires a complete R01 model run")
    _validate_run(run, suite, variant, repetition)
    report: dict[str, Any] = {}
    errors = P6_EVALUATOR.validate_human(
        suite, run["candidateRun"], None, report, _human_rubric()
    )
    if errors or report.get("humanReviewState") != "PENDING_HUMAN_REVIEW":
        raise P7T4Error("human review packet could not be created")
    rubric = _human_rubric()
    candidate_cases = {
        item["evalCaseId"]: item for item in run["candidateRun"]["cases"]
    }
    applicable = {
        case_id: profile
        for profile, case_ids in suite["matrices"]["humanApplicabilityBinding"].items()
        if profile != "NONE"
        for case_id in case_ids
    }
    blank_records = [
        {
            "evalCaseId": case_id,
            "candidateCaseDigest": sha256_bytes(canonical_bytes(candidate_cases[case_id])),
            "profileId": applicable[case_id],
            "dimensions": [
                {
                    "dimension": dimension,
                    "outcome": None,
                    "rationale": None,
                    "evidenceRefs": [],
                }
                for dimension in rubric["profiles"][applicable[case_id]]["dimensions"]
            ],
            "overall": None,
            "reviewerRationale": None,
            "evidenceRefs": [],
        }
        for case_id in sorted(applicable)
    ]
    sidecar_template = {
        "candidateOutputDigest": sha256_bytes(canonical_bytes(run["candidateRun"])),
        "review": {
            "suiteId": suite["suiteId"],
            "suiteVersion": suite["suiteVersion"],
            "candidateRunId": run["candidateRun"]["candidateRunId"],
            "rubricVersion": rubric["rubricVersion"],
            "records": blank_records,
        },
    }
    packet: dict[str, Any] = {
        "artifactType": "P7-T4-HUMAN-REVIEW-PACKET",
        "schemaVersion": SCHEMA_VERSION,
        "modelVariant": variant,
        "repetition": repetition,
        "runIdentity": run["artifactIdentity"],
        "candidateOutputDigest": sha256_bytes(canonical_bytes(run["candidateRun"])),
        "humanReview": report["humanReport"],
        "sidecarTemplate": sidecar_template,
        "instruction": "Complete every applicable record from visible candidate evidence; do not synthesize outcomes.",
    }
    packet["artifactIdentity"] = artifact_identity(packet)
    return packet


def build_independent_review_template(comparison: dict[str, Any]) -> dict[str, Any]:
    if (
        not isinstance(comparison, dict)
        or comparison.get("artifactType") != COMPARISON_ARTIFACT_TYPE
        or comparison.get("artifactIdentity") != artifact_identity(comparison)
    ):
        raise P7T4Error("comparison evidence invalid")
    return {
        "artifactType": "P7-T4-INDEPENDENT-REVIEW",
        "schemaVersion": SCHEMA_VERSION,
        "comparisonIdentity": comparison["artifactIdentity"],
        "reviewerId": None,
        "independentFromTraining": None,
        "reviewedAt": None,
        "decision": None,
        "resourceUseAccepted": None,
        "rationale": None,
    }


def validate_human_review(
    suite: dict[str, Any],
    run: dict[str, Any],
    sidecar: dict[str, Any],
    rubric: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Validate one R01 human sidecar with the canonical frozen rubric."""
    variant = run.get("modelVariant")
    repetition = run.get("repetition")
    if variant not in MODEL_VARIANTS or repetition != "R01":
        raise P7T4Error("human review requires a complete R01 model run")
    _validate_run(run, suite, variant, repetition)
    candidate_digest = sha256_bytes(canonical_bytes(run["candidateRun"]))
    if (
        not isinstance(sidecar, dict)
        or set(sidecar) != {"candidateOutputDigest", "review"}
        or sidecar.get("candidateOutputDigest") != candidate_digest
    ):
        raise P7T4Error("human review sidecar identity mismatch")
    report: dict[str, Any] = {}
    errors = P6_EVALUATOR.validate_human(
        suite, run["candidateRun"], sidecar["review"], report, rubric or _human_rubric()
    )
    if errors or report.get("humanReviewState") != "COMPLETE":
        detail = "; ".join(errors) if errors else "review is incomplete"
        raise P7T4Error(f"human review invalid: {detail}")
    evidence: dict[str, Any] = {
        "artifactType": "P7-T4-HUMAN-REVIEW-EVIDENCE",
        "schemaVersion": SCHEMA_VERSION,
        "modelVariant": variant,
        "repetition": repetition,
        "runIdentity": run["artifactIdentity"],
        "candidateOutputDigest": candidate_digest,
        "humanReviewState": "COMPLETE",
        "humanReport": report["humanReport"],
    }
    evidence["artifactIdentity"] = artifact_identity(evidence)
    return evidence


def _validate_human_evidence(
    value: dict[str, Any], comparison: dict[str, Any], variant: str, rubric: dict[str, Any]
) -> dict[str, str]:
    required = {
        "artifactType", "schemaVersion", "modelVariant", "repetition", "runIdentity",
        "candidateOutputDigest", "humanReviewState", "humanReport", "artifactIdentity",
    }
    if not isinstance(value, dict) or set(value) != required:
        raise P7T4Error("human review evidence fields are not closed")
    if (
        value.get("artifactType") != "P7-T4-HUMAN-REVIEW-EVIDENCE"
        or value.get("schemaVersion") != SCHEMA_VERSION
        or value.get("modelVariant") != variant
        or value.get("repetition") != "R01"
        or value.get("runIdentity") != comparison["runIdentities"][variant][0]
        or value.get("humanReviewState") != "COMPLETE"
        or value.get("artifactIdentity") != artifact_identity(value)
    ):
        raise P7T4Error("human review evidence identity mismatch")
    report = value.get("humanReport")
    if not isinstance(report, dict) or report.get("humanReviewState") != "COMPLETE":
        raise P7T4Error("human review evidence incomplete")
    records = report.get("records")
    outcomes = set(rubric.get("outcomes", []))
    if (
        not isinstance(records, list)
        or not records
        or any(
            not isinstance(item, dict)
            or item.get("overall") not in outcomes
            or not isinstance(item.get("evalCaseId"), str)
            for item in records
        )
    ):
        raise P7T4Error("human review outcomes invalid")
    result = {item["evalCaseId"]: item["overall"] for item in records}
    if len(result) != len(records):
        raise P7T4Error("human review case inventory invalid")
    return result


def _validate_independent_review(
    reviewer: dict[str, Any], comparison: dict[str, Any]
) -> None:
    required = {
        "artifactType", "schemaVersion", "comparisonIdentity", "reviewerId",
        "independentFromTraining", "reviewedAt", "decision", "resourceUseAccepted",
        "rationale",
    }
    if not isinstance(reviewer, dict) or set(reviewer) != required:
        raise P7T4Error("independent review fields are not closed")
    reviewed_at = reviewer.get("reviewedAt")
    try:
        parsed = datetime.fromisoformat(reviewed_at.replace("Z", "+00:00"))
    except (AttributeError, ValueError) as error:
        raise P7T4Error("independent review timestamp invalid") from error
    if (
        reviewer.get("artifactType") != "P7-T4-INDEPENDENT-REVIEW"
        or reviewer.get("schemaVersion") != SCHEMA_VERSION
        or reviewer.get("comparisonIdentity") != comparison.get("artifactIdentity")
        or reviewer.get("independentFromTraining") is not True
        or reviewer.get("decision") not in {"PASS", "FAIL"}
        or not isinstance(reviewer.get("resourceUseAccepted"), bool)
        or not isinstance(reviewer.get("reviewerId"), str)
        or not reviewer["reviewerId"].strip()
        or not isinstance(reviewer.get("rationale"), str)
        or not reviewer["rationale"].strip()
        or parsed.tzinfo is None
    ):
        raise P7T4Error("independent review contract invalid")


def finalize_comparison(
    comparison: dict[str, Any],
    base_human: dict[str, Any],
    adapter_human: dict[str, Any],
    reviewer: dict[str, Any],
    rubric: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Produce the P7-T4 decision only from complete automatic and review evidence."""
    if (
        not isinstance(comparison, dict)
        or comparison.get("artifactType") != COMPARISON_ARTIFACT_TYPE
        or comparison.get("state") != "AWAITING_INDEPENDENT_REVIEW"
        or comparison.get("artifactIdentity") != artifact_identity(comparison)
        or comparison.get("promotionAllowed") is not False
    ):
        raise P7T4Error("comparison evidence invalid")
    active_rubric = rubric or _human_rubric()
    base_outcomes = _validate_human_evidence(
        base_human, comparison, "SHARED_BASE", active_rubric
    )
    adapter_outcomes = _validate_human_evidence(
        adapter_human, comparison, "RESEARCH_ADAPTER", active_rubric
    )
    if set(base_outcomes) != set(adapter_outcomes):
        raise P7T4Error("human review inventories are not comparable")
    _validate_independent_review(reviewer, comparison)

    unresolved = sorted(
        case_id
        for case_id in adapter_outcomes
        if "NEEDS_REVIEW" in {base_outcomes[case_id], adapter_outcomes[case_id]}
    )
    human_regressions = sorted(
        case_id
        for case_id in adapter_outcomes
        if base_outcomes[case_id] == "PASS" and adapter_outcomes[case_id] != "PASS"
    )
    human_failures = sorted(
        case_id for case_id, outcome in adapter_outcomes.items() if outcome == "FAIL"
    )
    if unresolved:
        state = "AWAITING_REVIEW_RESOLUTION"
        decision = "PENDING"
    else:
        failed = (
            comparison.get("automaticDecision") != "AUTOMATIC_PASS"
            or bool(human_failures)
            or bool(human_regressions)
            or reviewer["decision"] != "PASS"
            or reviewer["resourceUseAccepted"] is not True
        )
        state = "COMPLETE"
        decision = "FAIL" if failed else "PASS"
    result: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-INDEPENDENT-EVALUATION-DECISION",
        "schemaVersion": SCHEMA_VERSION,
        "state": state,
        "comparisonIdentity": comparison["artifactIdentity"],
        "evaluationDecision": decision,
        "humanReviewIdentities": {
            "SHARED_BASE": base_human["artifactIdentity"],
            "RESEARCH_ADAPTER": adapter_human["artifactIdentity"],
        },
        "independentReview": reviewer,
        "unresolvedCaseIds": unresolved,
        "humanRegressionCaseIds": human_regressions,
        "humanFailedCaseIds": human_failures,
        "promotionAllowed": decision == "PASS",
    }
    result["artifactIdentity"] = artifact_identity(result)
    return result


def _source_commit(root: Path) -> str:
    bundle_manifest = root / "bundle-manifest.json"
    if bundle_manifest.is_file():
        commit = _load_json(bundle_manifest).get("sourceCommit")
    else:
        try:
            commit = subprocess.run(
                ["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True,
                check=True, timeout=10,
            ).stdout.strip()
        except (OSError, subprocess.SubprocessError) as error:
            raise P7T4Error("source commit unavailable") from error
    if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise P7T4Error("source commit invalid")
    return commit


def _default_adapter_directory(root: Path) -> Path:
    manifest = _load_json(root / "evidence" / "p7-t2-real-training" / "adapter-manifest.json")
    bundled = root / "adapter"
    if bundled.is_dir():
        return bundled
    return (
        root / ".artifacts" / "p7-t2" / manifest["candidateId"]
        / "p7-t2-real-training-output" / "adapter"
    )


def run_model_variant(
    root: Path,
    adapter_directory: Path,
    model_path: Path,
    model_variant: str,
    repetition: str,
    output_path: Path,
) -> dict[str, Any]:
    """Run one isolated variant/repetition against the exact composed suite."""
    if model_variant not in MODEL_VARIANTS or repetition not in REPETITIONS:
        raise P7T4Error("model variant or repetition invalid")
    gate = preflight(root, adapter_directory)
    suite = gate["composedSuite"]
    config = _load_json(root / "config" / "p7-t4-research-independent-evaluation.json")
    prompt_config = _load_yaml(root / config["execution"]["promptProfileReference"])
    try:
        P6_BENCHMARK.validate_profile_templates(prompt_config)
        P7T2.validate_model_snapshot(model_path, config)
        import torch
        from peft import PeftModel
        from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
    except (ImportError, OSError, ValueError, P6_BENCHMARK.BenchmarkError) as error:
        raise P7T4Error(f"reviewed runtime or model snapshot invalid: {error}") from error

    model = tokenizer = None
    telemetry = P6_BENCHMARK.TelemetrySampler(torch, output_path.parent, os.getpid())
    latencies: list[int] = []
    candidate_cases: list[dict[str, Any]] = []
    prompt_manifest: list[dict[str, Any]] = []
    raw_outputs: list[dict[str, Any]] = []
    try:
        decode = prompt_config["runtime"]["decode"]
        P6_BENCHMARK.configure_determinism(torch, decode["seed"])
        generation_policy = P6_BENCHMARK.generation_kwargs(decode)
        quantization = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=torch.float16,
        )
        telemetry.start()
        if telemetry.failure:
            raise P7T4Error(f"resource telemetry unavailable: {telemetry.failure}")
        tokenizer = AutoTokenizer.from_pretrained(model_path, local_files_only=True)
        model = AutoModelForCausalLM.from_pretrained(
            model_path,
            local_files_only=True,
            quantization_config=quantization,
            device_map=config["execution"]["device"],
        )
        if model_variant == "RESEARCH_ADAPTER":
            model = PeftModel.from_pretrained(
                model, adapter_directory, is_trainable=False, local_files_only=True
            )
        model.eval()
        P6_BENCHMARK.no_offload_guard(model)
        context_limit = P6_BENCHMARK.model_context_limit(model, tokenizer)
        research_profile = P6_BENCHMARK.assistant_profile(prompt_config, ASSISTANT_KEY)
        template_options = P6_BENCHMARK.template_kwargs({"qwen3": True})

        warmup_messages = [
            {"role": "system", "content": research_profile["systemInstruction"]},
            {"role": "user", "content": "Warm-up only. Return a closed JSON candidate envelope."},
        ]
        warmup_inputs = tokenizer.apply_chat_template(
            warmup_messages, **template_options
        ).to(config["execution"]["device"])
        with torch.inference_mode():
            model.generate(
                **warmup_inputs,
                **P6_BENCHMARK.generation_kwargs(decode, warmup=True),
            )
        torch.cuda.synchronize()

        for case in suite["caseInventory"]:
            prompt_case = dict(case)
            prompt_case["assistantKey"] = ASSISTANT_KEY
            messages = P6_BENCHMARK.render_prompt(
                prompt_case, research_profile["systemInstruction"]
            )
            inputs = tokenizer.apply_chat_template(messages, **template_options).to(
                config["execution"]["device"]
            )
            P6_BENCHMARK.context_budget(
                int(inputs["input_ids"].shape[-1]), context_limit, prompt_config
            )
            prompt_manifest.append(
                {
                    "evalCaseId": case["evalCaseId"],
                    "sourceAssistantKey": case["assistantKey"],
                    "executionAssistantKey": ASSISTANT_KEY,
                    "promptDigest": sha256_bytes(canonical_bytes(messages)),
                }
            )
            telemetry.capture(f"{model_variant}:{repetition}:before:{case['evalCaseId']}")
            if telemetry.failure:
                raise P7T4Error(f"resource telemetry unavailable: {telemetry.failure}")
            torch.cuda.synchronize()
            started = time.perf_counter_ns()
            with torch.inference_mode():
                output = model.generate(**inputs, **generation_policy)
            torch.cuda.synchronize()
            latencies.append(time.perf_counter_ns() - started)
            raw_text = tokenizer.decode(
                output[0][inputs["input_ids"].shape[-1]:], skip_special_tokens=True
            )
            raw_outputs.append(
                {
                    "evalCaseId": case["evalCaseId"],
                    "rawText": raw_text,
                    "rawTextDigest": sha256_bytes(raw_text.encode("utf-8")),
                }
            )
            parsed, parse_error = P6_BENCHMARK.parse_raw_response(
                case["evalCaseId"], raw_text
            )
            candidate_cases.append(
                P6_BENCHMARK.scored_case(case["evalCaseId"], parsed, parse_error)
            )
            telemetry.capture(f"{model_variant}:{repetition}:after:{case['evalCaseId']}")
            if telemetry.failure:
                raise P7T4Error(f"resource telemetry unavailable: {telemetry.failure}")

        candidate_run = {
            "suiteId": suite["suiteId"],
            "suiteVersion": suite["suiteVersion"],
            "candidateRunId": f"{config['adapter']['candidateId']}-{model_variant}-{repetition}",
            "modelMetadata": {
                "variant": model_variant,
                "baseModel": config["baseModel"],
                "candidateId": config["adapter"]["candidateId"]
                if model_variant == "RESEARCH_ADAPTER" else None,
            },
            "cases": candidate_cases,
        }
        findings, automatic = evaluator_for_suite(root, suite).score_candidate(
            suite, candidate_run
        )
    except P7T4Error:
        raise
    except Exception as error:
        raise P7T4Error(f"model evaluation failed: {error}") from error
    finally:
        if model is not None:
            del model
        if tokenizer is not None:
            del tokenizer
        gc.collect()
        try:
            torch.cuda.synchronize()
            torch.cuda.empty_cache()
        finally:
            telemetry.stop()

    if telemetry.failure or not telemetry.samples:
        raise P7T4Error(f"resource telemetry unavailable: {telemetry.failure or 'no samples'}")
    metrics = {
        "generationLatencyNs": latencies,
        "peakVramBytes": max(item["vramUsedBytes"] for item in telemetry.samples),
        "peakRssBytes": max(item["processRssBytes"] for item in telemetry.samples),
    }
    run: dict[str, Any] = {
        "artifactType": RUN_ARTIFACT_TYPE,
        "schemaVersion": SCHEMA_VERSION,
        "state": "COMPLETE",
        "modelVariant": model_variant,
        "repetition": repetition,
        "suite": suite_binding(suite),
        "candidateRun": candidate_run,
        "automatic": automatic,
        "promptManifest": prompt_manifest,
        "rawOutputs": raw_outputs,
        "findings": findings,
        "metrics": metrics,
        "sourceCommit": _source_commit(root),
    }
    run["artifactIdentity"] = artifact_identity(run)
    _validate_run(run, suite, model_variant, repetition)
    _atomic_write_json(output_path, run)
    return run


def _load_runs(artifact_root: Path, variant: str) -> list[dict[str, Any]]:
    return [
        _load_json(artifact_root / "runs" / variant / f"{repetition}.json")
        for repetition in REPETITIONS
    ]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--adapter-directory", type=Path)
    parser.add_argument("--artifact-root", type=Path, default=ROOT / ".artifacts" / "p7-t4")
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--preflight", action="store_true")
    action.add_argument("--run", action="store_true")
    action.add_argument("--compare", action="store_true")
    action.add_argument("--review-packets", action="store_true")
    action.add_argument("--finalize", action="store_true")
    parser.add_argument("--model-path", type=Path)
    parser.add_argument("--model-variant", choices=MODEL_VARIANTS)
    parser.add_argument("--repetition", choices=REPETITIONS)
    parser.add_argument("--comparison", type=Path)
    parser.add_argument("--base-human", type=Path)
    parser.add_argument("--adapter-human", type=Path)
    parser.add_argument("--independent-review", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    adapter_directory = (args.adapter_directory or _default_adapter_directory(root)).resolve()
    try:
        gate = preflight(root, adapter_directory)
        suite = gate["composedSuite"]
        if args.preflight:
            _atomic_write_json(args.artifact_root / "preflight.json", gate)
            print(json.dumps({"state": gate["state"], "artifactIdentity": gate["artifactIdentity"]}))
            return 0
        if args.run:
            if args.model_path is None or args.model_variant is None or args.repetition is None:
                parser.error("--run requires --model-path, --model-variant, and --repetition")
            output = args.artifact_root / "runs" / args.model_variant / f"{args.repetition}.json"
            run_model_variant(
                root, adapter_directory, args.model_path, args.model_variant, args.repetition, output
            )
            print(json.dumps({"state": "COMPLETE", "output": str(output)}))
            return 0
        base_runs = _load_runs(args.artifact_root, "SHARED_BASE")
        adapter_runs = _load_runs(args.artifact_root, "RESEARCH_ADAPTER")
        if args.compare:
            comparison = compare_model_runs(suite, base_runs, adapter_runs)
            output = args.artifact_root / "comparison.json"
            _atomic_write_json(output, comparison)
            print(json.dumps({"state": comparison["state"], "artifactIdentity": comparison["artifactIdentity"]}))
            return 0
        if args.review_packets:
            for variant, runs in (("SHARED_BASE", base_runs), ("RESEARCH_ADAPTER", adapter_runs)):
                packet = build_human_review_packet(suite, runs[0])
                _atomic_write_json(args.artifact_root / "review" / f"{variant}-H01-packet.json", packet)
            comparison = _load_json(args.artifact_root / "comparison.json")
            _atomic_write_json(
                args.artifact_root / "review" / "independent-review-template.json",
                build_independent_review_template(comparison),
            )
            print(json.dumps({"state": "AWAITING_HUMAN_AND_INDEPENDENT_REVIEW"}))
            return 0
        required = (args.comparison, args.base_human, args.adapter_human, args.independent_review)
        if any(path is None for path in required):
            parser.error(
                "--finalize requires --comparison, --base-human, --adapter-human, and --independent-review"
            )
        comparison = _load_json(args.comparison)
        rubric = _human_rubric()
        base_human = validate_human_review(
            suite, base_runs[0], _load_json(args.base_human), rubric
        )
        adapter_human = validate_human_review(
            suite, adapter_runs[0], _load_json(args.adapter_human), rubric
        )
        decision = finalize_comparison(
            comparison,
            base_human,
            adapter_human,
            _load_json(args.independent_review),
            rubric,
        )
        output = args.artifact_root / "decision.json"
        _atomic_write_json(output, decision)
        print(json.dumps({"state": decision["state"], "decision": decision["evaluationDecision"]}))
        return 0
    except P7T4Error as error:
        failure = {"state": "FAILED", "error": str(error)}
        print(json.dumps(failure, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
