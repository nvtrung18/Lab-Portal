#!/usr/bin/env python3
"""Run deterministic P7-T2 adapter training infrastructure.

P7-T2 deliberately provides only an offline smoke backend. It validates the
same configuration, approved P7-T1 dataset manifest, decision gate,
checkpoint, resume, and export contracts that a later real backend must use,
without loading or downloading model weights.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import re
import subprocess
import sys
import tempfile
from typing import Any


CONFIG_SCHEMA_VERSION = "1.0.0"
PIPELINE_VERSION = "1.0.0"
DECISION_SCHEMA_VERSION = "1.0.0"
METADATA_SCHEMA_VERSION = "1.0.0"
CHECKPOINT_SCHEMA_VERSION = "1.0.0"
ADAPTER_MANIFEST_SCHEMA_VERSION = "1.0.0"
P7_T1_SCHEMA_VERSION = "1.0.0"
P7_T1_PIPELINE_VERSION = "1.0.0"
SUPPORTED_ASSISTANTS = {"RESEARCH_ASSISTANT", "LAB_ASSISTANT", "ADMIN_ASSISTANT"}
SUPPORTED_DECISIONS = {"ADAPTER_REQUIRED", "BASE_ONLY_APPROVED"}
SUPPORTED_ADAPTER_METHODS = {"LORA", "QLORA"}
SUPPORTED_PRECISIONS = {"float32", "float16", "bfloat16"}
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
WINDOWS_ABSOLUTE_PATTERN = re.compile(r"^[A-Za-z]:[\\/]")


class TrainingPipelineError(ValueError):
    """Fail-closed P7-T2 diagnostic."""

    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def canonical_bytes(value: object) -> bytes:
    try:
        rendered = json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        )
    except (TypeError, ValueError) as error:
        raise TrainingPipelineError(f"value is not canonical JSON: {error}") from error
    return rendered.encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def dataset_identity(manifest: dict[str, Any]) -> str:
    """Apply the P7-T1 manifest identity contract (checksum field excluded)."""
    return sha256_bytes(canonical_bytes({key: value for key, value in manifest.items() if key != "checksum"}))


def _is_nonempty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _is_positive_int(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def _is_number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def _is_absolute_reference(value: str) -> bool:
    return value.startswith(("/", "\\")) or bool(WINDOWS_ABSOLUTE_PATTERN.match(value))


def _validate_logical_reference(value: object, path: str, diagnostics: list[str]) -> None:
    if not _is_nonempty_string(value):
        diagnostics.append(f"{path}: non-empty logical reference required")
    elif _is_absolute_reference(value):
        diagnostics.append(f"{path}: absolute paths are forbidden")


def _validate_relative_path(value: object, path: str, diagnostics: list[str]) -> None:
    _validate_logical_reference(value, path, diagnostics)
    if not _is_nonempty_string(value) or _is_absolute_reference(value):
        return
    normalized = value.replace("\\", "/")
    if any(part in {"", ".", ".."} for part in normalized.split("/")):
        diagnostics.append(f"{path}: safe relative path required")


def _require_exact_fields(value: object, fields: set[str], path: str, diagnostics: list[str]) -> bool:
    if not isinstance(value, dict):
        diagnostics.append(f"{path}: object required")
        return False
    if set(value) != fields:
        diagnostics.append(f"{path}: exact fields {', '.join(sorted(fields))} required")
        return False
    return True


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
        "output",
    }
    if not _require_exact_fields(config, root_fields, "config", diagnostics):
        raise TrainingPipelineError(diagnostics)
    assert isinstance(config, dict)

    if config.get("schemaVersion") != CONFIG_SCHEMA_VERSION:
        diagnostics.append("config/schemaVersion: unsupported version")
    if config.get("pipelineVersion") != PIPELINE_VERSION:
        diagnostics.append("config/pipelineVersion: unsupported version")
    if config.get("assistantKey") not in SUPPORTED_ASSISTANTS:
        diagnostics.append("config/assistantKey: supported assistant key required")

    base_model = config.get("baseModel")
    if _require_exact_fields(base_model, {"identifier", "revision"}, "config/baseModel", diagnostics):
        _validate_logical_reference(base_model.get("identifier"), "config/baseModel/identifier", diagnostics)
        _validate_logical_reference(base_model.get("revision"), "config/baseModel/revision", diagnostics)

    adapter = config.get("adapter")
    if _require_exact_fields(adapter, {"method", "lora", "quantization"}, "config/adapter", diagnostics):
        method = adapter.get("method")
        if method not in SUPPORTED_ADAPTER_METHODS:
            diagnostics.append("config/adapter/method: LORA or QLORA required")
        lora = adapter.get("lora")
        lora_fields = {"rank", "alpha", "dropout", "bias", "targetModules"}
        if _require_exact_fields(lora, lora_fields, "config/adapter/lora", diagnostics):
            if not _is_positive_int(lora.get("rank")):
                diagnostics.append("config/adapter/lora/rank: positive integer required")
            if not _is_positive_int(lora.get("alpha")):
                diagnostics.append("config/adapter/lora/alpha: positive integer required")
            dropout = lora.get("dropout")
            if not _is_number(dropout) or not 0 <= dropout < 1:
                diagnostics.append("config/adapter/lora/dropout: number in [0, 1) required")
            if lora.get("bias") not in {"none", "all", "lora_only"}:
                diagnostics.append("config/adapter/lora/bias: supported bias mode required")
            targets = lora.get("targetModules")
            if (
                not isinstance(targets, list)
                or not targets
                or any(not _is_nonempty_string(item) for item in targets)
                or len(targets) != len(set(targets))
            ):
                diagnostics.append("config/adapter/lora/targetModules: unique non-empty list required")
        quantization = adapter.get("quantization")
        if method == "LORA":
            if quantization is not None:
                diagnostics.append("config/adapter/quantization: null required for LORA")
        elif method == "QLORA" and _require_exact_fields(
            quantization,
            {"bits", "quantType", "doubleQuantization", "computeDtype"},
            "config/adapter/quantization",
            diagnostics,
        ):
            if quantization.get("bits") != 4:
                diagnostics.append("config/adapter/quantization/bits: 4 required for QLORA")
            if quantization.get("quantType") not in {"nf4", "fp4"}:
                diagnostics.append("config/adapter/quantization/quantType: nf4 or fp4 required")
            if not isinstance(quantization.get("doubleQuantization"), bool):
                diagnostics.append("config/adapter/quantization/doubleQuantization: boolean required")
            if quantization.get("computeDtype") not in SUPPORTED_PRECISIONS:
                diagnostics.append("config/adapter/quantization/computeDtype: supported precision required")

    seed = config.get("seed")
    if not isinstance(seed, int) or isinstance(seed, bool) or not 0 <= seed <= 0xFFFFFFFF:
        diagnostics.append("config/seed: explicit integer in [0, 4294967295] required")

    dataset = config.get("dataset")
    if _require_exact_fields(dataset, {"manifestReference", "identity"}, "config/dataset", diagnostics):
        _validate_logical_reference(dataset.get("manifestReference"), "config/dataset/manifestReference", diagnostics)
        if not isinstance(dataset.get("identity"), str) or not SHA256_PATTERN.fullmatch(dataset["identity"]):
            diagnostics.append("config/dataset/identity: lowercase SHA-256 required")

    splits = config.get("splits")
    if _require_exact_fields(splits, {"training", "evaluation"}, "config/splits", diagnostics):
        supported_splits = {"train", "validation", "evaluation"}
        if splits.get("training") not in supported_splits:
            diagnostics.append("config/splits/training: known P7-T1 split required")
        if splits.get("evaluation") not in supported_splits:
            diagnostics.append("config/splits/evaluation: known P7-T1 split required")
        if splits.get("training") == splits.get("evaluation"):
            diagnostics.append("config/splits: training and evaluation splits must differ")

    training = config.get("training")
    training_fields = {
        "epochs",
        "maxSteps",
        "learningRate",
        "batchSize",
        "gradientAccumulation",
        "precision",
        "checkpointFrequency",
    }
    if _require_exact_fields(training, training_fields, "config/training", diagnostics):
        epochs = training.get("epochs")
        max_steps = training.get("maxSteps")
        if not ((_is_positive_int(epochs) and max_steps is None) or (epochs is None and _is_positive_int(max_steps))):
            diagnostics.append("config/training: exactly one positive epochs or maxSteps value required")
        if not _is_number(training.get("learningRate")) or training["learningRate"] <= 0:
            diagnostics.append("config/training/learningRate: positive number required")
        for field in ("batchSize", "gradientAccumulation", "checkpointFrequency"):
            if not _is_positive_int(training.get(field)):
                diagnostics.append(f"config/training/{field}: positive integer required")
        if training.get("precision") not in SUPPORTED_PRECISIONS:
            diagnostics.append("config/training/precision: supported precision required")

    output = config.get("output")
    output_fields = {"checkpointDirectory", "exportDirectory", "metadataFilename"}
    if _require_exact_fields(output, output_fields, "config/output", diagnostics):
        for field in sorted(output_fields):
            _validate_relative_path(output.get(field), f"config/output/{field}", diagnostics)
        if output.get("checkpointDirectory") == output.get("exportDirectory"):
            diagnostics.append("config/output: checkpoint and export directories must differ")

    if diagnostics:
        raise TrainingPipelineError(diagnostics)


def training_config_identity(config: dict[str, Any]) -> str:
    validate_training_config(config)
    return sha256_bytes(canonical_bytes(config))


def training_run_identity(config: dict[str, Any]) -> str:
    config_identity = training_config_identity(config)
    identity_document = {
        "schemaVersion": CONFIG_SCHEMA_VERSION,
        "assistantKey": config["assistantKey"],
        "baseModel": config["baseModel"],
        "datasetIdentity": config["dataset"]["identity"],
        "trainingConfigIdentity": config_identity,
        "seed": config["seed"],
        "adapterMethod": config["adapter"]["method"],
    }
    return sha256_bytes(canonical_bytes(identity_document))


def validate_decision_manifest(manifest: object) -> None:
    diagnostics: list[str] = []
    fields = {"schemaVersion", "decisionRecordVersion", "decisionReference", "status", "decisions"}
    if not _require_exact_fields(manifest, fields, "decisions", diagnostics):
        raise TrainingPipelineError(diagnostics)
    assert isinstance(manifest, dict)
    if manifest.get("schemaVersion") != DECISION_SCHEMA_VERSION:
        diagnostics.append("decisions/schemaVersion: unsupported version")
    if not _is_nonempty_string(manifest.get("decisionRecordVersion")):
        diagnostics.append("decisions/decisionRecordVersion: non-empty version required")
    _validate_logical_reference(manifest.get("decisionReference"), "decisions/decisionReference", diagnostics)
    if manifest.get("status") != "APPROVED":
        diagnostics.append("decisions/status: APPROVED required")
    decisions = manifest.get("decisions")
    if not isinstance(decisions, dict) or set(decisions) != SUPPORTED_ASSISTANTS:
        diagnostics.append("decisions/decisions: exact supported assistant mapping required")
    else:
        for assistant_key, decision in sorted(decisions.items()):
            if decision not in SUPPORTED_DECISIONS:
                diagnostics.append(f"decisions/decisions/{assistant_key}: unsupported decision")
    if diagnostics:
        raise TrainingPipelineError(diagnostics)


def resolve_decision(manifest: dict[str, Any], assistant_key: str) -> str:
    validate_decision_manifest(manifest)
    decision = manifest["decisions"].get(assistant_key)
    if decision not in SUPPORTED_DECISIONS:
        raise TrainingPipelineError(f"decision for {assistant_key}: unsupported decision")
    return decision


def _load_json(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise TrainingPipelineError(f"{label}: cannot load {path.name}: {error}") from error
    if not isinstance(value, dict):
        raise TrainingPipelineError(f"{label}: object required")
    return value


def _artifact_path(manifest_directory: Path, filename: object) -> Path:
    diagnostics: list[str] = []
    _validate_relative_path(filename, "dataset/artifact/filename", diagnostics)
    if diagnostics:
        raise TrainingPipelineError(diagnostics)
    assert isinstance(filename, str)
    candidate = (manifest_directory / filename).resolve()
    root = manifest_directory.resolve()
    if candidate.parent != root:
        raise TrainingPipelineError("dataset/artifact/filename: nested or escaping paths are forbidden")
    return candidate


def validate_dataset_manifest(
    manifest_path: Path,
    expected_identity: str,
    assistant_key: str,
    required_splits: set[str],
) -> dict[str, Any]:
    manifest = _load_json(manifest_path, "dataset manifest")
    diagnostics: list[str] = []
    if manifest.get("pipeline_schema_version") != P7_T1_SCHEMA_VERSION:
        diagnostics.append("dataset manifest: unsupported P7-T1 schema version")
    if manifest.get("pipeline_version") != P7_T1_PIPELINE_VERSION:
        diagnostics.append("dataset manifest: unsupported P7-T1 pipeline version")
    if manifest.get("approval_status") != "APPROVED":
        diagnostics.append("dataset manifest: APPROVED status required")
    if manifest.get("model_development_purpose") != "TRAINING":
        diagnostics.append("dataset manifest: TRAINING purpose required")
    if manifest.get("checksum_algorithm") != "SHA-256":
        diagnostics.append("dataset manifest: SHA-256 checksum algorithm required")
    actual_identity = dataset_identity(manifest)
    if manifest.get("checksum") != actual_identity:
        diagnostics.append("dataset manifest identity mismatch")
    if expected_identity != actual_identity:
        diagnostics.append("configured dataset identity mismatch")

    partition = manifest.get("partition")
    if partition == "SHARED":
        consumers = manifest.get("consumer_assistant_keys")
        if not isinstance(consumers, list) or assistant_key not in consumers:
            diagnostics.append("dataset manifest: assistant is not an approved shared consumer")
    elif partition != assistant_key or manifest.get("assistant_key") != assistant_key:
        diagnostics.append("dataset manifest: assistant partition mismatch")

    artifacts = manifest.get("artifacts")
    artifact_names: set[str] = set()
    if not isinstance(artifacts, list) or not artifacts:
        diagnostics.append("dataset manifest: non-empty artifact inventory required")
    else:
        for index, artifact in enumerate(artifacts):
            if not isinstance(artifact, dict):
                diagnostics.append(f"dataset manifest/artifacts/{index}: object required")
                continue
            filename = artifact.get("filename")
            if filename in artifact_names:
                diagnostics.append(f"dataset manifest/artifacts/{index}: duplicate filename")
                continue
            if isinstance(filename, str):
                artifact_names.add(filename)
            try:
                artifact_path = _artifact_path(manifest_path.parent, filename)
            except TrainingPipelineError as error:
                diagnostics.extend(error.diagnostics)
                continue
            expected_sha256 = artifact.get("sha256")
            if not isinstance(expected_sha256, str) or not SHA256_PATTERN.fullmatch(expected_sha256):
                diagnostics.append(f"dataset manifest/artifacts/{index}: lowercase SHA-256 required")
                continue
            try:
                actual_sha256 = sha256_bytes(artifact_path.read_bytes())
            except OSError as error:
                diagnostics.append(f"dataset artifact {filename}: cannot read: {error}")
                continue
            if actual_sha256 != expected_sha256:
                diagnostics.append(f"dataset artifact checksum mismatch: {filename}")
    for split_name in sorted(required_splits):
        if f"{split_name}.jsonl" not in artifact_names:
            diagnostics.append(f"dataset manifest: required split artifact missing: {split_name}.jsonl")

    if diagnostics:
        raise TrainingPipelineError(diagnostics)
    return manifest


def seed_everything(seed: int, optional_modules: dict[str, object] | None = None) -> dict[str, Any]:
    if not isinstance(seed, int) or isinstance(seed, bool) or not 0 <= seed <= 0xFFFFFFFF:
        raise TrainingPipelineError("seed: explicit integer in [0, 4294967295] required")
    random.seed(seed)
    os.environ["PYTHONHASHSEED"] = str(seed)
    modules = optional_modules or {}
    seeded_libraries = ["python"]
    runtime_versions = {"python": platform.python_version()}

    numpy = modules.get("numpy")
    if numpy is not None:
        numpy.random.seed(seed)
        seeded_libraries.append("numpy")
        runtime_versions["numpy"] = str(getattr(numpy, "__version__", "UNKNOWN"))

    torch = modules.get("torch")
    if torch is not None:
        torch.manual_seed(seed)
        if torch.cuda.is_available():
            torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.deterministic = True
        torch.backends.cudnn.benchmark = False
        torch.use_deterministic_algorithms(True, warn_only=True)
        seeded_libraries.append("torch")
        runtime_versions["torch"] = str(getattr(torch, "__version__", "UNKNOWN"))

    return {
        "seed": seed,
        "pythonHashSeed": str(seed),
        "seededLibraries": seeded_libraries,
        "runtimeVersions": runtime_versions,
    }


def checkpoint_name(global_step: int) -> str:
    if not _is_positive_int(global_step):
        raise TrainingPipelineError("checkpoint global step: positive integer required")
    return f"checkpoint-{global_step:08d}"


def _json_bytes(value: object) -> bytes:
    rendered = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
    return (rendered + "\n").encode("utf-8")


def _write_json(path: Path, value: object) -> None:
    path.write_bytes(_json_bytes(value))


def _source_commit() -> str:
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=Path(__file__).resolve().parents[1],
            check=True,
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return "UNAVAILABLE"
    commit = completed.stdout.strip()
    return commit if commit else "UNAVAILABLE"


def _checkpoint_metadata(
    config: dict[str, Any],
    config_identity: str,
    run_identity: str,
    global_step: int,
    state_sha256: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": CHECKPOINT_SCHEMA_VERSION,
        "pipelineVersion": PIPELINE_VERSION,
        "backend": "SMOKE",
        "trainingRunIdentity": run_identity,
        "trainingConfigIdentity": config_identity,
        "datasetIdentity": config["dataset"]["identity"],
        "baseModel": config["baseModel"],
        "assistantKey": config["assistantKey"],
        "adapterMethod": config["adapter"]["method"],
        "globalStep": global_step,
        "seed": config["seed"],
        "stateArtifact": {"filename": "smoke-state.json", "sha256": state_sha256},
    }


def _checkpoint_inventory_entry(checkpoint_directory: Path, metadata: dict[str, Any]) -> dict[str, Any]:
    metadata_path = checkpoint_directory / "checkpoint-metadata.json"
    state_path = checkpoint_directory / metadata["stateArtifact"]["filename"]
    return {
        "checkpointName": checkpoint_directory.name,
        "globalStep": metadata["globalStep"],
        "metadataSha256": sha256_bytes(metadata_path.read_bytes()),
        "stateSha256": sha256_bytes(state_path.read_bytes()),
    }


def validate_resume_checkpoint(
    checkpoint_directory: Path,
    config: dict[str, Any],
    config_identity: str,
    run_identity: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    metadata_path = checkpoint_directory / "checkpoint-metadata.json"
    if not metadata_path.is_file():
        raise TrainingPipelineError("resume checkpoint metadata is missing")
    metadata = _load_json(metadata_path, "resume checkpoint metadata")
    required_fields = {
        "schemaVersion",
        "pipelineVersion",
        "backend",
        "trainingRunIdentity",
        "trainingConfigIdentity",
        "datasetIdentity",
        "baseModel",
        "assistantKey",
        "adapterMethod",
        "globalStep",
        "seed",
        "stateArtifact",
    }
    if set(metadata) != required_fields:
        raise TrainingPipelineError("resume checkpoint metadata has an incompatible schema")
    if metadata.get("schemaVersion") != CHECKPOINT_SCHEMA_VERSION or metadata.get("pipelineVersion") != PIPELINE_VERSION:
        raise TrainingPipelineError("resume checkpoint metadata has an unsupported version")
    if metadata.get("assistantKey") != config["assistantKey"]:
        raise TrainingPipelineError("resume assistant key mismatch")
    checkpoint_base = metadata.get("baseModel")
    if not isinstance(checkpoint_base, dict) or checkpoint_base.get("identifier") != config["baseModel"]["identifier"]:
        raise TrainingPipelineError("resume base model identifier mismatch")
    if checkpoint_base.get("revision") != config["baseModel"]["revision"]:
        raise TrainingPipelineError("resume base model revision mismatch")
    if metadata.get("datasetIdentity") != config["dataset"]["identity"]:
        raise TrainingPipelineError("resume dataset identity mismatch")
    if metadata.get("trainingConfigIdentity") != config_identity:
        raise TrainingPipelineError("resume config identity mismatch")
    if metadata.get("trainingRunIdentity") != run_identity:
        raise TrainingPipelineError("resume training run identity mismatch")
    if metadata.get("seed") != config["seed"]:
        raise TrainingPipelineError("resume seed mismatch")
    if metadata.get("adapterMethod") != config["adapter"]["method"]:
        raise TrainingPipelineError("resume adapter method mismatch")
    if metadata.get("backend") != "SMOKE":
        raise TrainingPipelineError("resume backend mismatch")

    global_step = metadata.get("globalStep")
    if not _is_positive_int(global_step) or checkpoint_directory.name != checkpoint_name(global_step):
        raise TrainingPipelineError("resume checkpoint name/global step mismatch")
    state_artifact = metadata.get("stateArtifact")
    if not isinstance(state_artifact, dict) or set(state_artifact) != {"filename", "sha256"}:
        raise TrainingPipelineError("resume checkpoint state metadata is malformed")
    if state_artifact.get("filename") != "smoke-state.json":
        raise TrainingPipelineError("resume checkpoint state filename mismatch")
    state_path = checkpoint_directory / "smoke-state.json"
    if not state_path.is_file():
        raise TrainingPipelineError("resume checkpoint state is missing")
    actual_state_sha256 = sha256_bytes(state_path.read_bytes())
    if state_artifact.get("sha256") != actual_state_sha256:
        raise TrainingPipelineError("resume checkpoint state checksum mismatch")

    return metadata, {
        "checkpointName": checkpoint_directory.name,
        "globalStep": global_step,
        "metadataSha256": sha256_bytes(metadata_path.read_bytes()),
        "stateSha256": actual_state_sha256,
    }


class TrainingBackend:
    """Small backend boundary used by the deterministic pipeline contract."""

    name = "UNIMPLEMENTED"
    quality_evidence = "NONE"

    def write_checkpoint(
        self,
        checkpoint_directory: Path,
        config: dict[str, Any],
        config_identity: str,
        run_identity: str,
        global_step: int,
    ) -> dict[str, Any]:
        raise NotImplementedError

    def export_adapter(
        self,
        export_directory: Path,
        config: dict[str, Any],
        config_identity: str,
        run_identity: str,
        source_commit: str,
    ) -> list[dict[str, Any]]:
        raise NotImplementedError


class SmokeTrainingBackend(TrainingBackend):
    """Deterministic stub; its artifacts are explicitly not model evidence."""

    name = "SMOKE"
    quality_evidence = "SMOKE_ONLY_NO_MODEL_QUALITY_EVIDENCE"

    def write_checkpoint(
        self,
        checkpoint_directory: Path,
        config: dict[str, Any],
        config_identity: str,
        run_identity: str,
        global_step: int,
    ) -> dict[str, Any]:
        checkpoint_directory.mkdir(parents=True, exist_ok=False)
        state = {
            "format": "P7_T2_DETERMINISTIC_SMOKE_STATE",
            "trainingRunIdentity": run_identity,
            "globalStep": global_step,
            "seed": config["seed"],
        }
        state_path = checkpoint_directory / "smoke-state.json"
        _write_json(state_path, state)
        metadata = _checkpoint_metadata(
            config,
            config_identity,
            run_identity,
            global_step,
            sha256_bytes(state_path.read_bytes()),
        )
        _write_json(checkpoint_directory / "checkpoint-metadata.json", metadata)
        return _checkpoint_inventory_entry(checkpoint_directory, metadata)

    def export_adapter(
        self,
        export_directory: Path,
        config: dict[str, Any],
        config_identity: str,
        run_identity: str,
        source_commit: str,
    ) -> list[dict[str, Any]]:
        export_directory.mkdir(parents=True, exist_ok=False)
        adapter_config = {
            "format": "P7_T2_SMOKE_ADAPTER_CONFIG",
            "adapterMethod": config["adapter"]["method"],
            "adapterParameters": config["adapter"],
            "baseModel": config["baseModel"],
        }
        adapter_config_path = export_directory / "adapter_config.json"
        _write_json(adapter_config_path, adapter_config)

        smoke_payload = b"P7-T2 SMOKE PLACEHOLDER - NOT MODEL WEIGHTS\n" + canonical_bytes(
            {
                "trainingRunIdentity": run_identity,
                "trainingConfigIdentity": config_identity,
                "datasetIdentity": config["dataset"]["identity"],
                "seed": config["seed"],
            }
        ) + b"\n"
        smoke_path = export_directory / "smoke-adapter.bin"
        smoke_path.write_bytes(smoke_payload)

        adapter_artifacts = [
            {"filename": path.name, "sha256": sha256_bytes(path.read_bytes())}
            for path in (adapter_config_path, smoke_path)
        ]
        adapter_manifest = {
            "schemaVersion": ADAPTER_MANIFEST_SCHEMA_VERSION,
            "pipelineVersion": PIPELINE_VERSION,
            "backend": self.name,
            "adapterDisposition": "CANDIDATE_ONLY",
            "qualityEvidence": self.quality_evidence,
            "assistantKey": config["assistantKey"],
            "baseModel": config["baseModel"],
            "datasetIdentity": config["dataset"]["identity"],
            "trainingConfigIdentity": config_identity,
            "trainingRunIdentity": run_identity,
            "seed": config["seed"],
            "sourceCommit": source_commit,
            "artifacts": adapter_artifacts,
        }
        adapter_manifest_path = export_directory / "adapter-manifest.json"
        _write_json(adapter_manifest_path, adapter_manifest)
        return adapter_artifacts + [
            {"filename": adapter_manifest_path.name, "sha256": sha256_bytes(adapter_manifest_path.read_bytes())}
        ]


def _base_metadata(
    config: dict[str, Any],
    decision_manifest: dict[str, Any],
    decision: str,
    config_identity: str,
    run_identity: str,
    source_commit: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": METADATA_SCHEMA_VERSION,
        "pipelineVersion": PIPELINE_VERSION,
        "assistantKey": config["assistantKey"],
        "decision": decision,
        "decisionReference": decision_manifest["decisionReference"],
        "baseModel": config["baseModel"],
        "datasetManifestReference": config["dataset"]["manifestReference"],
        "datasetIdentity": config["dataset"]["identity"],
        "trainingConfigIdentity": config_identity,
        "trainingRunIdentity": run_identity,
        "seed": config["seed"],
        "adapterMethod": config["adapter"]["method"],
        "adapterParameters": config["adapter"],
        "trainingParameters": config["training"],
        "sourceCommit": source_commit,
    }


def _write_output_atomically(output_directory: Path, writer) -> dict[str, Any]:
    if output_directory.exists():
        raise TrainingPipelineError("output directory must not already exist")
    try:
        output_directory.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix=f".{output_directory.name}.", dir=output_directory.parent) as name:
            temporary_directory = Path(name)
            result = writer(temporary_directory)
            os.replace(temporary_directory, output_directory)
        return result
    except OSError as error:
        raise TrainingPipelineError(f"output: cannot write artifacts: {error}") from error


def _write_skipped_run(
    output_directory: Path,
    config: dict[str, Any],
    decision_manifest: dict[str, Any],
    config_identity: str,
    run_identity: str,
    source_commit: str,
) -> dict[str, Any]:
    def writer(temporary_directory: Path) -> dict[str, Any]:
        metadata = _base_metadata(
            config,
            decision_manifest,
            "BASE_ONLY_APPROVED",
            config_identity,
            run_identity,
            source_commit,
        )
        metadata.update(
            {
                "status": "SKIPPED",
                "skipReason": "BASE_ONLY_APPROVED",
                "backend": "NONE",
                "qualityEvidence": "NOT_APPLICABLE",
                "adapterDisposition": "NOT_BUILT",
                "seedState": {"seed": config["seed"], "applied": False},
                "runtimeVersions": {"python": platform.python_version()},
                "checkpoints": [],
                "resumeSource": None,
                "exportedArtifacts": [],
            }
        )
        _write_json(temporary_directory / config["output"]["metadataFilename"], metadata)
        return metadata

    return _write_output_atomically(output_directory, writer)


def _resolved_smoke_steps(config: dict[str, Any]) -> int:
    training = config["training"]
    return training["maxSteps"] if training["maxSteps"] is not None else training["epochs"]


def _checkpoint_steps(total_steps: int, frequency: int, resume_step: int) -> list[int]:
    steps = [step for step in range(frequency, total_steps + 1, frequency) if step > resume_step]
    if total_steps > resume_step and total_steps not in steps:
        steps.append(total_steps)
    return steps


def run_pipeline(
    config: dict[str, Any],
    decision_manifest: dict[str, Any],
    dataset_manifest_path: Path,
    output_directory: Path,
    *,
    smoke: bool,
    resume_from: Path | None = None,
    optional_seed_modules: dict[str, object] | None = None,
    source_commit: str | None = None,
) -> dict[str, Any]:
    validate_training_config(config)
    config_identity = training_config_identity(config)
    run_identity = training_run_identity(config)
    decision = resolve_decision(decision_manifest, config["assistantKey"])
    resolved_source_commit = source_commit if source_commit is not None else _source_commit()
    if not _is_nonempty_string(resolved_source_commit):
        raise TrainingPipelineError("source commit: non-empty value required")

    if decision == "BASE_ONLY_APPROVED":
        return _write_skipped_run(
            output_directory,
            config,
            decision_manifest,
            config_identity,
            run_identity,
            resolved_source_commit,
        )
    if not smoke:
        raise TrainingPipelineError("P7-T2 has no real training backend; --smoke is required")

    validate_dataset_manifest(
        dataset_manifest_path,
        config["dataset"]["identity"],
        config["assistantKey"],
        {config["splits"]["training"], config["splits"]["evaluation"]},
    )
    seed_state = seed_everything(config["seed"], optional_seed_modules)
    backend = SmokeTrainingBackend()
    total_steps = _resolved_smoke_steps(config)
    resume_source = None
    resume_step = 0
    if resume_from is not None:
        _, resume_source = validate_resume_checkpoint(
            resume_from,
            config,
            config_identity,
            run_identity,
        )
        resume_step = resume_source["globalStep"]
        if resume_step > total_steps:
            raise TrainingPipelineError("resume global step exceeds configured training steps")

    def writer(temporary_directory: Path) -> dict[str, Any]:
        checkpoint_root = temporary_directory / config["output"]["checkpointDirectory"]
        checkpoint_root.mkdir(parents=True, exist_ok=False)
        checkpoints = []
        for global_step in _checkpoint_steps(
            total_steps,
            config["training"]["checkpointFrequency"],
            resume_step,
        ):
            checkpoints.append(
                backend.write_checkpoint(
                    checkpoint_root / checkpoint_name(global_step),
                    config,
                    config_identity,
                    run_identity,
                    global_step,
                )
            )
        exported_artifacts = backend.export_adapter(
            temporary_directory / config["output"]["exportDirectory"],
            config,
            config_identity,
            run_identity,
            resolved_source_commit,
        )
        metadata = _base_metadata(
            config,
            decision_manifest,
            decision,
            config_identity,
            run_identity,
            resolved_source_commit,
        )
        metadata.update(
            {
                "status": "COMPLETED",
                "backend": backend.name,
                "qualityEvidence": backend.quality_evidence,
                "adapterDisposition": "CANDIDATE_ONLY",
                "seedState": {
                    "seed": seed_state["seed"],
                    "pythonHashSeed": seed_state["pythonHashSeed"],
                    "seededLibraries": seed_state["seededLibraries"],
                },
                "runtimeVersions": seed_state["runtimeVersions"],
                "checkpoints": checkpoints,
                "resumeSource": resume_source,
                "exportedArtifacts": exported_artifacts,
            }
        )
        _write_json(temporary_directory / config["output"]["metadataFilename"], metadata)
        return metadata

    return _write_output_atomically(output_directory, writer)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path, help="versioned P7-T2 training config (JSON)")
    parser.add_argument("--decisions", required=True, type=Path, help="approved P6-T6 decision manifest (JSON)")
    parser.add_argument("--dataset-manifest", required=True, type=Path, help="approved P7-T1 manifest.json")
    parser.add_argument("--output", required=True, type=Path, help="new run artifact directory")
    parser.add_argument("--resume-from", type=Path, help="explicit deterministic checkpoint directory")
    parser.add_argument("--smoke", action="store_true", help="use offline deterministic smoke backend")
    args = parser.parse_args()
    try:
        result = run_pipeline(
            _load_json(args.config, "training config"),
            _load_json(args.decisions, "decision manifest"),
            args.dataset_manifest,
            args.output,
            smoke=args.smoke,
            resume_from=args.resume_from,
        )
        print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except TrainingPipelineError as error:
        print(
            json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True, separators=(",", ":")),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
