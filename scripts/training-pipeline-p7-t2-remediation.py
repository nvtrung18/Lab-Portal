#!/usr/bin/env python3
"""Run the governed P7-T2 Research remediation training pipeline."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
BACKEND_PATH = ROOT / "scripts" / "p7-t2-real-training-remediation.py"
LEGACY_PIPELINE_PATH = ROOT / "scripts" / "training-pipeline-p7-t2.py"
SCHEMA_VERSION = "2.0.0"
PIPELINE_VERSION = "2.0.0"
DATASET_IDENTITY = "0409e9087efe7332e298d0c3812d11f2edac7cedf538a8db475776d9c190eb30"
TRAINING_APPROVAL_IDENTITY = "5565b0339f9745d3e0b9cb44353bb97a131824fcdd7511130d11d6742b13dbd0"
TRAINING_CONTRACT_IDENTITY = "89e49c43fd6488a6d47473141ad9070bd0dd785e309bbdaf26246e41d277a145"
DATASET_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v2/manifest.json"
)
DATASET_RECORD_SCHEMA_VERSION = "2.0.0"
GRADIENT_SCALER_INITIAL_SCALE: int | None = None
STABILITY_RETRY_APPROVAL_IDENTITY: str | None = None
STABILITY_RETRY_APPROVAL_REFERENCE: str | None = None
STABILITY_RETRY_REQUEST_IDENTITY: str | None = None
STABILITY_RETRY_INCIDENT_IDENTITY: str | None = None
BASE_MODEL = {
    "identifier": "Qwen/Qwen3-4B-Instruct-2507",
    "revision": "cdbee75f17c01a7cc42f958dc650907174af0554",
}
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
RECORD_FIELDS = {
    "schemaVersion",
    "assistantKey",
    "domain",
    "recordType",
    "visibility",
    "useCaseId",
    "trainingPrompt",
    "trainingTarget",
    "contentId",
}
OUTPUT_FIELDS = {
    "evalCaseId",
    "response",
    "observedBehavior",
    "observedActionRisk",
    "toolRequest",
    "structuredOutput",
    "referencedContextIds",
}
INCLUDED_USE_CASES = {"RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"}


class TrainingPipelineError(ValueError):
    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


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
        raise TrainingPipelineError(f"canonical JSON required: {error}") from error


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any], field: str) -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != field}))


def load_document(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise TrainingPipelineError(f"{label}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise TrainingPipelineError(f"{label}: object required")
    return value


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise TrainingPipelineError(f"runtime module unavailable: {path.name}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    try:
        sys.dont_write_bytecode = True
        specification.loader.exec_module(module)
    except (ImportError, OSError) as error:
        raise TrainingPipelineError(f"runtime module cannot load: {error}") from error
    finally:
        sys.dont_write_bytecode = previous
    return module


def _positive_int(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def _safe_reference(value: object) -> bool:
    if not isinstance(value, str) or not value or Path(value).is_absolute():
        return False
    return all(part not in {"", ".", ".."} for part in value.replace("\\", "/").split("/"))


def validate_training_config(config: object) -> None:
    diagnostics: list[str] = []
    root_fields = {
        "schemaVersion",
        "pipelineVersion",
        "assistantKey",
        "baseModel",
        "adapter",
        "seed",
        "dataset",
        "splits",
        "training",
        "contractGates",
        "output",
    }
    if not isinstance(config, dict) or set(config) != root_fields:
        raise TrainingPipelineError("config: exact remediation fields required")
    if config.get("schemaVersion") != SCHEMA_VERSION or config.get("pipelineVersion") != PIPELINE_VERSION:
        diagnostics.append("config: schemaVersion and pipelineVersion 2.0.0 required")
    if config.get("assistantKey") != "RESEARCH_ASSISTANT" or config.get("baseModel") != BASE_MODEL:
        diagnostics.append("config: exact Research assistant/base model required")
    if not isinstance(config.get("seed"), int) or isinstance(config.get("seed"), bool):
        diagnostics.append("config/seed: integer required")

    dataset = config.get("dataset")
    if not isinstance(dataset, dict) or set(dataset) != {"manifestReference", "identity"}:
        diagnostics.append("config/dataset: exact binding required")
    elif (
        dataset.get("identity") != DATASET_IDENTITY
        or dataset.get("manifestReference") != DATASET_MANIFEST_REFERENCE
    ):
        diagnostics.append("config/dataset: exact approved v2 dataset required")

    if config.get("splits") != {
        "training": "train",
        "validation": "validation",
        "contractHoldout": "evaluation",
    }:
        diagnostics.append("config/splits: independent train/validation/evaluation binding required")

    training = config.get("training")
    expected_training_fields = {
        "epochs",
        "maxSteps",
        "learningRate",
        "batchSize",
        "gradientAccumulation",
        "precision",
        "evaluationStrategy",
        "saveStrategy",
        "loadBestModelAtEnd",
        "metricForBestModel",
        "greaterIsBetter",
        "earlyStoppingPatience",
        "earlyStoppingThreshold",
        "saveTotalLimit",
    }
    stability_training_fields = expected_training_fields | {"gradientScalerInitialScale"}
    allowed_training_fields = {frozenset(expected_training_fields)}
    if GRADIENT_SCALER_INITIAL_SCALE is not None:
        allowed_training_fields.add(frozenset(stability_training_fields))
    if not isinstance(training, dict) or frozenset(training) not in allowed_training_fields:
        diagnostics.append("config/training: exact guarded schedule fields required")
    else:
        if not _positive_int(training.get("epochs")) or training.get("maxSteps") is not None:
            diagnostics.append("config/training: finite epoch schedule required; fixed maxSteps forbidden")
        if training.get("evaluationStrategy") != "epoch":
            diagnostics.append("config/training/evaluationStrategy: epoch required")
        if training.get("saveStrategy") != "epoch":
            diagnostics.append("config/training/saveStrategy: epoch required")
        if training.get("loadBestModelAtEnd") is not True:
            diagnostics.append("config/training/loadBestModelAtEnd: true required")
        if training.get("metricForBestModel") != "eval_loss" or training.get("greaterIsBetter") is not False:
            diagnostics.append("config/training: validation loss best-checkpoint policy required")
        if not _positive_int(training.get("earlyStoppingPatience")):
            diagnostics.append("config/training/earlyStoppingPatience: positive integer required")
        if not isinstance(training.get("earlyStoppingThreshold"), (int, float)) or isinstance(
            training.get("earlyStoppingThreshold"), bool
        ) or training["earlyStoppingThreshold"] < 0:
            diagnostics.append("config/training/earlyStoppingThreshold: non-negative number required")
        if not _positive_int(training.get("saveTotalLimit")):
            diagnostics.append("config/training/saveTotalLimit: positive integer required")
        for field in ("batchSize", "gradientAccumulation"):
            if not _positive_int(training.get(field)):
                diagnostics.append(f"config/training/{field}: positive integer required")
        if not isinstance(training.get("learningRate"), (int, float)) or isinstance(
            training.get("learningRate"), bool
        ) or training["learningRate"] <= 0:
            diagnostics.append("config/training/learningRate: positive number required")
        if training.get("precision") != "float16":
            diagnostics.append("config/training/precision: float16 required for Tesla T4")
        if (
            "gradientScalerInitialScale" in training
            and training.get("gradientScalerInitialScale") != GRADIENT_SCALER_INITIAL_SCALE
        ):
            diagnostics.append(
                "config/training/gradientScalerInitialScale: supported stability value required"
            )

    adapter = config.get("adapter")
    if not isinstance(adapter, dict) or adapter.get("method") != "QLORA":
        diagnostics.append("config/adapter: QLORA required")
    elif adapter.get("quantization", {}).get("computeDtype") != "float16":
        diagnostics.append("config/adapter: float16 QLoRA compute dtype required")

    gates = config.get("contractGates")
    required_gates = {
        "frozenEvaluationTrainingUseAllowed",
        "independentHoldout",
        "preparedRuntimeContract",
        "trainingApprovalIdentity",
        "trainingApprovalReference",
        "trainingContractIdentity",
        "trainingContractReference",
    }
    if not isinstance(gates, dict) or set(gates) != required_gates:
        diagnostics.append("config/contractGates: exact governed gates required")
    else:
        if gates.get("frozenEvaluationTrainingUseAllowed") is not False:
            diagnostics.append("config/contractGates: frozen evaluation training use forbidden")
        if gates.get("independentHoldout") != {
            "split": "evaluation",
            "useForBestCheckpointSelection": False,
            "useForEarlyStopping": False,
            "useForOptimization": False,
        }:
            diagnostics.append("config/contractGates: independent holdout isolation required")
        if gates.get("trainingApprovalIdentity") != TRAINING_APPROVAL_IDENTITY:
            diagnostics.append("config/contractGates: exact training approval required")
        if gates.get("trainingContractIdentity") != TRAINING_CONTRACT_IDENTITY:
            diagnostics.append("config/contractGates: exact training contract required")
        for field in ("trainingApprovalReference", "trainingContractReference"):
            if not _safe_reference(gates.get(field)):
                diagnostics.append(f"config/contractGates/{field}: safe reference required")

    output = config.get("output")
    if not isinstance(output, dict) or set(output) != {
        "checkpointDirectory",
        "exportDirectory",
        "metadataFilename",
    }:
        diagnostics.append("config/output: exact fields required")
    elif any(not _safe_reference(output.get(field)) for field in output):
        diagnostics.append("config/output: safe relative paths required")
    if diagnostics:
        raise TrainingPipelineError(diagnostics)


def training_config_identity(config: dict[str, Any]) -> str:
    validate_training_config(config)
    return sha256_bytes(canonical_bytes(config))


def training_run_identity(config: dict[str, Any]) -> str:
    return sha256_bytes(
        canonical_bytes(
            {
                "schemaVersion": SCHEMA_VERSION,
                "assistantKey": config["assistantKey"],
                "baseModel": config["baseModel"],
                "datasetIdentity": config["dataset"]["identity"],
                "trainingConfigIdentity": training_config_identity(config),
                "seed": config["seed"],
                "adapterMethod": config["adapter"]["method"],
            }
        )
    )


def _load_records(path: Path, expected: dict[str, Any]) -> list[dict[str, Any]]:
    if not path.is_file() or sha256_bytes(path.read_bytes()) != expected.get("sha256"):
        raise TrainingPipelineError(f"dataset/{path.name}: checksum mismatch")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
        values = [json.loads(line) for line in lines]
    except (UnicodeError, json.JSONDecodeError) as error:
        raise TrainingPipelineError(f"dataset/{path.name}: invalid JSONL: {error}") from error
    if len(values) != expected.get("recordCount") or not values:
        raise TrainingPipelineError(f"dataset/{path.name}: record count mismatch")
    for record in values:
        if not isinstance(record, dict) or set(record) != RECORD_FIELDS:
            raise TrainingPipelineError(f"dataset/{path.name}: exact training record fields required")
        if (
            record.get("schemaVersion") != DATASET_RECORD_SCHEMA_VERSION
            or record.get("assistantKey") != "RESEARCH_ASSISTANT"
            or record.get("domain") != "RESEARCH"
            or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
            or record.get("useCaseId") not in INCLUDED_USE_CASES
            or not isinstance(record.get("trainingPrompt"), dict)
            or not isinstance(record.get("trainingTarget"), dict)
            or set(record["trainingTarget"]) != OUTPUT_FIELDS
            or record["trainingTarget"].get("evalCaseId")
            != record["trainingPrompt"].get("evalCaseId")
            or record["trainingPrompt"].get("useCaseId") != record["useCaseId"]
        ):
            raise TrainingPipelineError(f"dataset/{path.name}: governed prompt/target contract mismatch")
    return values


def _validate_structured_output(value: object, bundle: dict[str, Any]) -> None:
    if value is None:
        return
    if not isinstance(value, dict):
        raise TrainingPipelineError("runtime schema: structured output object required")
    branches = bundle.get("schema", {}).get("oneOf")
    if not isinstance(branches, list):
        raise TrainingPipelineError("runtime schema: oneOf contract required")
    kind = value.get("kind")
    branch = next(
        (
            item
            for item in branches
            if isinstance(item, dict)
            and item.get("properties", {}).get("kind", {}).get("const") == kind
        ),
        None,
    )
    if not isinstance(branch, dict):
        raise TrainingPipelineError("runtime schema: unsupported Research structured output kind")
    required = branch.get("required")
    if not isinstance(required, list) or set(value) != set(required):
        raise TrainingPipelineError("runtime schema: exact structured output fields required")
    properties = branch.get("properties", {})
    for field in required:
        rule = properties.get(field, {})
        item = value[field]
        if "const" in rule and item != rule["const"]:
            raise TrainingPipelineError(f"runtime schema: {field} const mismatch")
        if rule.get("type") == "string" and (
            not isinstance(item, str)
            or len(item) < rule.get("minLength", 0)
            or len(item) > rule.get("maxLength", len(item))
            or ("pattern" in rule and re.fullmatch(rule["pattern"], item) is None)
        ):
            raise TrainingPipelineError(f"runtime schema: invalid {field}")


def validate_dataset_and_contract_gates(
    manifest_path: Path, config: dict[str, Any], repository_root: Path
) -> dict[str, Any]:
    validate_training_config(config)
    manifest = load_document(manifest_path, "dataset manifest")
    if (
        manifest.get("checksum") != DATASET_IDENTITY
        or artifact_identity(manifest, "checksum") != DATASET_IDENTITY
        or manifest.get("training_approval_identity") != TRAINING_APPROVAL_IDENTITY
        or manifest.get("training_contract_identity") != TRAINING_CONTRACT_IDENTITY
        or manifest.get("approval_status") != "APPROVED"
    ):
        raise TrainingPipelineError("dataset manifest: exact approved v2 identity required")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise TrainingPipelineError("dataset manifest: artifact inventory required")
    inventory = {
        item.get("filename"): item for item in artifacts if isinstance(item, dict)
    }
    records: dict[str, list[dict[str, Any]]] = {}
    for split in ("train", "validation", "evaluation"):
        item = inventory.get(f"{split}.jsonl")
        if not isinstance(item, dict):
            raise TrainingPipelineError(f"dataset manifest: missing {split}.jsonl")
        records[split] = _load_records(manifest_path.parent / f"{split}.jsonl", item)
    id_sets = {split: {item["contentId"] for item in values} for split, values in records.items()}
    if (
        id_sets["train"] & id_sets["validation"]
        or id_sets["train"] & id_sets["evaluation"]
        or id_sets["validation"] & id_sets["evaluation"]
    ):
        raise TrainingPipelineError("dataset: train/validation/contract holdout must be disjoint")

    gates = config["contractGates"]
    approval = load_document(repository_root / gates["trainingApprovalReference"], "training approval")
    if (
        approval.get("artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or artifact_identity(approval, "artifactIdentity") != TRAINING_APPROVAL_IDENTITY
        or approval.get("status") != "APPROVED"
        or approval.get("revocation", {}).get("status") != "ACTIVE"
        or approval.get("scope", {}).get("frozenEvaluationTrainingUseAllowed") is not False
    ):
        raise TrainingPipelineError("training approval: exact active narrow approval required")
    contract = load_document(repository_root / gates["trainingContractReference"], "training contract")
    if (
        contract.get("contractIdentity") != TRAINING_CONTRACT_IDENTITY
        or artifact_identity(contract, "contractIdentity") != TRAINING_CONTRACT_IDENTITY
        or contract.get("scope", {}).get("frozenEvaluationDerivedRecordsAllowed") is not False
        or set(contract.get("outputContract", {}).get("closedKeys", [])) != OUTPUT_FIELDS
    ):
        raise TrainingPipelineError("training contract: exact immutable contract required")

    runtime = gates.get("preparedRuntimeContract")
    if not isinstance(runtime, dict):
        raise TrainingPipelineError("runtime schema: prepared contract binding required")
    profile_path = repository_root / runtime.get("assistantProfileReference", "")
    schema_path = repository_root / runtime.get("schemaReference", "")
    if not profile_path.is_file() or sha256_bytes(profile_path.read_bytes()) != runtime.get(
        "assistantProfileSha256"
    ):
        raise TrainingPipelineError("runtime schema: assistant profile checksum mismatch")
    if not schema_path.is_file() or sha256_bytes(schema_path.read_bytes()) != runtime.get(
        "schemaSha256"
    ):
        raise TrainingPipelineError("runtime schema: schema checksum mismatch")
    profiles = load_document(profile_path, "runtime assistant profiles")
    research_profile = profiles.get("profiles", {}).get("RESEARCH_ASSISTANT", {})
    if research_profile.get("schemaBundle") != runtime.get("schemaBundle"):
        raise TrainingPipelineError("runtime schema: Research profile bundle mismatch")
    schemas = load_document(schema_path, "runtime schemas")
    bundles = schemas.get("schemas")
    bundle = next(
        (
            item
            for item in bundles or []
            if isinstance(item, dict) and item.get("schemaId") == runtime.get("schemaBundle")
        ),
        None,
    )
    if not isinstance(bundle, dict) or bundle.get("assistantKey") != "RESEARCH_ASSISTANT":
        raise TrainingPipelineError("runtime schema: Research bundle missing")
    for split_records in records.values():
        for record in split_records:
            _validate_structured_output(record["trainingTarget"]["structuredOutput"], bundle)

    stability_retry_approval = None
    if config["training"].get("gradientScalerInitialScale") is not None:
        if (
            STABILITY_RETRY_APPROVAL_IDENTITY is None
            or STABILITY_RETRY_APPROVAL_REFERENCE is None
            or STABILITY_RETRY_REQUEST_IDENTITY is None
            or STABILITY_RETRY_INCIDENT_IDENTITY is None
        ):
            raise TrainingPipelineError("stability retry approval: binding unavailable")
        retry_approval = load_document(
            repository_root / STABILITY_RETRY_APPROVAL_REFERENCE,
            "stability retry approval",
        )
        if (
            retry_approval.get("artifactIdentity") != STABILITY_RETRY_APPROVAL_IDENTITY
            or artifact_identity(retry_approval, "artifactIdentity")
            != STABILITY_RETRY_APPROVAL_IDENTITY
            or retry_approval.get("status") != "APPROVED"
            or retry_approval.get("approval", {}).get("decision") != "APPROVED"
            or retry_approval.get("revocation", {}).get("status") != "ACTIVE"
            or retry_approval.get("requestIdentity") != STABILITY_RETRY_REQUEST_IDENTITY
            or retry_approval.get("incident", {}).get("incidentIdentity")
            != STABILITY_RETRY_INCIDENT_IDENTITY
            or retry_approval.get("stabilityChange", {}).get("gradientScalerInitialScale")
            != GRADIENT_SCALER_INITIAL_SCALE
            or retry_approval.get("stabilityChange", {}).get("trainingConfigIdentity")
            != training_config_identity(config)
            or retry_approval.get("stabilityChange", {}).get("trainingRunIdentity")
            != training_run_identity(config)
            or retry_approval.get("scope", {}).get("maximumRuns") != 1
            or retry_approval.get("scope", {}).get("freshBaseModelStartRequired") is not True
            or retry_approval.get("scope", {}).get("resumeFromQuarantinedCheckpointAllowed")
            is not False
            or retry_approval.get("scope", {}).get("candidateDispositionAfterTraining")
            != "CANDIDATE_ONLY"
        ):
            raise TrainingPipelineError(
                "stability retry approval: exact active one-run approval required"
            )
        stability_retry_approval = {
            "state": "PASS",
            "approvalIdentity": STABILITY_RETRY_APPROVAL_IDENTITY,
            "maximumRuns": 1,
            "freshBaseModelStartRequired": True,
        }

    result = {
        "state": "PASS",
        "counts": {split: len(values) for split, values in records.items()},
        "contentIdsDisjoint": True,
        "contractHoldout": {
            "state": "PASS",
            "split": "evaluation",
            "recordCount": len(records["evaluation"]),
            "usedForOptimization": False,
        },
        "preparedRuntimeContract": {
            "state": "PASS",
            "schemaBundle": runtime["schemaBundle"],
        },
    }
    if stability_retry_approval is not None:
        result["stabilityRetryApproval"] = stability_retry_approval
    return result


def _source_commit() -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise TrainingPipelineError(f"source commit unavailable: {error}") from error
    commit = result.stdout.strip()
    if not COMMIT_PATTERN.fullmatch(commit):
        raise TrainingPipelineError("source commit: full Git commit required")
    return commit


def run_pipeline(
    *,
    config: dict[str, Any],
    decisions: dict[str, Any],
    dataset_manifest_path: Path,
    output_directory: Path,
    model_path: Path,
    source_commit: str | None = None,
    resume_from: Path | None = None,
) -> dict[str, Any]:
    validate_training_config(config)
    legacy = _load_module("p7_t2_legacy_pipeline_for_decision", LEGACY_PIPELINE_PATH)
    try:
        decision = legacy.resolve_decision(decisions, config["assistantKey"])
    except legacy.TrainingPipelineError as error:
        raise TrainingPipelineError(error.diagnostics) from error
    if decision != "ADAPTER_REQUIRED":
        raise TrainingPipelineError("remediation training requires ADAPTER_REQUIRED")
    validate_dataset_and_contract_gates(dataset_manifest_path, config, ROOT)
    resolved_commit = source_commit or _source_commit()
    if not COMMIT_PATTERN.fullmatch(resolved_commit):
        raise TrainingPipelineError("source commit: full Git commit required")
    if output_directory.exists():
        raise TrainingPipelineError("output directory must not already exist")
    backend = _load_module("p7_t2_remediation_real_backend", BACKEND_PATH)
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(
            prefix=f".{output_directory.name}.", dir=output_directory.parent
        ) as temporary:
            temporary_path = Path(temporary)
            metadata = backend.run_real_training(
                config=config,
                decision=decision,
                decision_reference=decisions["decisionReference"],
                dataset_manifest_path=dataset_manifest_path,
                output_directory=temporary_path,
                model_path=model_path,
                resume_from=resume_from,
                source_commit=resolved_commit,
            )
            backend.validate_real_training_output(temporary_path, config, dataset_manifest_path)
            os.replace(temporary_path, output_directory)
            return metadata
    except (OSError, ValueError) as error:
        raise TrainingPipelineError(f"P7-T2 remediation real training failed: {error}") from error


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--decisions", type=Path, required=True)
    parser.add_argument("--dataset-manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model-path", type=Path, required=True)
    parser.add_argument("--source-commit")
    parser.add_argument("--resume-from", type=Path)
    parser.add_argument("--preflight-only", action="store_true")
    args = parser.parse_args()
    try:
        config = load_document(args.config, "training config")
        decisions = load_document(args.decisions, "decision manifest")
        gates = validate_dataset_and_contract_gates(args.dataset_manifest, config, ROOT)
        backend = _load_module("p7_t2_remediation_real_backend_cli", BACKEND_PATH)
        backend.validate_model_snapshot(args.model_path, config)
        if args.preflight_only:
            runtime = backend.validate_runtime_preflight(config["training"]["precision"])
            print(
                json.dumps(
                    {"state": "PREFLIGHT_PASS", "contractGates": gates, "runtime": runtime},
                    sort_keys=True,
                    separators=(",", ":"),
                )
            )
            return 0
        result = run_pipeline(
            config=config,
            decisions=decisions,
            dataset_manifest_path=args.dataset_manifest,
            output_directory=args.output,
            model_path=args.model_path,
            source_commit=args.source_commit,
            resume_from=args.resume_from,
        )
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 0
    except TrainingPipelineError as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
