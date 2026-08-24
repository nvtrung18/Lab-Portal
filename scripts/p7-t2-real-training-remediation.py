#!/usr/bin/env python3
"""Real single-T4 QLoRA backend for the governed P7-T4 remediation candidate."""
from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import platform
import re
from typing import Any


LEGACY_PATH = Path(__file__).with_name("p7-t2-real-training.py")
OUTPUT_FIELDS = {
    "evalCaseId",
    "response",
    "observedBehavior",
    "observedActionRisk",
    "toolRequest",
    "structuredOutput",
    "referencedContextIds",
}
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
SYSTEM_MESSAGE = (
    "You are RESEARCH_ASSISTANT. Follow the visible prompt contract exactly. "
    "Return only one canonical JSON object with the requested closed keys. "
    "Use only supplied context, preserve authorization boundaries, keep drafts non-official, "
    "and never claim a tool or business action was executed."
)


def _load_legacy():
    specification = importlib.util.spec_from_file_location(
        "p7_t2_real_training_legacy_helpers", LEGACY_PATH
    )
    if specification is None or specification.loader is None:
        raise ValueError("legacy real-training helpers unavailable")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


legacy = _load_legacy()
canonical_bytes = legacy.canonical_bytes
sha256_bytes = legacy.sha256_bytes
training_config_identity = legacy.training_config_identity
training_run_identity = legacy.training_run_identity
validate_model_snapshot = legacy.validate_model_snapshot


def training_messages(record: dict[str, Any]) -> list[dict[str, str]]:
    if not isinstance(record, dict) or set(record) != RECORD_FIELDS:
        raise ValueError("training record: exact governed v2 fields required")
    prompt = record.get("trainingPrompt")
    target = record.get("trainingTarget")
    if (
        record.get("schemaVersion") != "2.0.0"
        or record.get("assistantKey") != "RESEARCH_ASSISTANT"
        or record.get("domain") != "RESEARCH"
        or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
        or not isinstance(prompt, dict)
        or not isinstance(target, dict)
        or set(target) != OUTPUT_FIELDS
        or target.get("evalCaseId") != prompt.get("evalCaseId")
        or prompt.get("useCaseId") != record.get("useCaseId")
    ):
        raise ValueError("training record: governed prompt/target contract mismatch")
    return [
        {"role": "system", "content": SYSTEM_MESSAGE},
        {"role": "user", "content": canonical_bytes(prompt).decode("utf-8")},
        {"role": "assistant", "content": canonical_bytes(target).decode("utf-8")},
    ]


def _load_jsonl(path: Path, expected_count: int, label: str) -> list[dict[str, Any]]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
        values = [json.loads(line) for line in lines]
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"{label}: cannot load JSONL: {error}") from error
    if len(values) != expected_count or not values:
        raise ValueError(f"{label}: record count mismatch")
    content_ids: list[str] = []
    for value in values:
        training_messages(value)
        content_id = value.get("contentId")
        if not isinstance(content_id, str) or legacy.SHA256_PATTERN.fullmatch(content_id) is None:
            raise ValueError(f"{label}: canonical contentId required")
        content_ids.append(content_id)
    if len(content_ids) != len(set(content_ids)):
        raise ValueError(f"{label}: duplicate contentId")
    return values


def load_training_inputs(manifest_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    if config.get("splits") != {
        "training": "train",
        "validation": "validation",
        "contractHoldout": "evaluation",
    }:
        raise ValueError("real remediation training requires isolated train/validation/evaluation splits")
    manifest = legacy._load_json(manifest_path, "dataset manifest")
    if manifest.get("checksum") != config.get("dataset", {}).get("identity"):
        raise ValueError("dataset manifest: configured identity mismatch")
    inventory = {
        item.get("filename"): item
        for item in manifest.get("artifacts", [])
        if isinstance(item, dict) and isinstance(item.get("filename"), str)
    }
    result: dict[str, Any] = {"manifest": manifest}
    for key, filename in (
        ("training", "train.jsonl"),
        ("validation", "validation.jsonl"),
        ("contractHoldout", "evaluation.jsonl"),
    ):
        artifact = inventory.get(filename)
        if not isinstance(artifact, dict):
            raise ValueError(f"dataset manifest: missing {filename}")
        path = manifest_path.parent / filename
        expected_sha256 = legacy._require_sha256(artifact.get("sha256"), f"dataset/{filename}")
        if not path.is_file() or sha256_bytes(path.read_bytes()) != expected_sha256:
            raise ValueError(f"dataset/{filename}: checksum mismatch")
        count = artifact.get("recordCount")
        if not isinstance(count, int) or isinstance(count, bool) or count <= 0:
            raise ValueError(f"dataset/{filename}: positive record count required")
        result[f"{key}Artifact"] = artifact
        result[f"{key}Records"] = _load_jsonl(path, count, f"dataset/{filename}")
    ids = {
        key: {item["contentId"] for item in result[f"{key}Records"]}
        for key in ("training", "validation", "contractHoldout")
    }
    if (
        ids["training"] & ids["validation"]
        or ids["training"] & ids["contractHoldout"]
        or ids["validation"] & ids["contractHoldout"]
    ):
        raise ValueError("dataset: optimization, validation, and contract holdout overlap")
    return result


def training_argument_values(config: dict[str, Any], checkpoint_root: Path) -> dict[str, Any]:
    training = config["training"]
    return {
        "output_dir": str(checkpoint_root),
        "overwrite_output_dir": False,
        "per_device_train_batch_size": training["batchSize"],
        "per_device_eval_batch_size": training["batchSize"],
        "gradient_accumulation_steps": training["gradientAccumulation"],
        "learning_rate": training["learningRate"],
        "num_train_epochs": float(training["epochs"]),
        "max_steps": -1,
        "bf16": False,
        "fp16": True,
        "gradient_checkpointing": True,
        "save_strategy": training["saveStrategy"],
        "eval_strategy": training["evaluationStrategy"],
        "load_best_model_at_end": training["loadBestModelAtEnd"],
        "metric_for_best_model": training["metricForBestModel"],
        "greater_is_better": training["greaterIsBetter"],
        "save_total_limit": training["saveTotalLimit"],
        "logging_strategy": "steps",
        "logging_steps": 1,
        "seed": config["seed"],
        "data_seed": config["seed"],
        "report_to": [],
        "remove_unused_columns": False,
        "prediction_loss_only": True,
        "save_safetensors": True,
    }


def canonical_checkpoint_name(value: str) -> str:
    name = Path(value).name
    match = re.fullmatch(r"checkpoint-([0-9]+)", name)
    if match is None or int(match.group(1)) <= 0:
        raise ValueError("real remediation training: valid best checkpoint required")
    return f"checkpoint-{int(match.group(1)):08d}"


def validate_runtime_preflight(precision: str) -> dict[str, Any]:
    legacy._configure_deterministic_runtime()
    runtime = legacy._runtime_modules(precision)
    return {
        "versions": runtime["versions"],
        "gpu": runtime["gpu"],
        "python": platform.python_version(),
    }


def validate_real_metadata_contract(metadata: object, config: dict[str, Any]) -> None:
    legacy_required = {
        "status",
        "backend",
        "realTraining",
        "qualityEvidence",
        "adapterDisposition",
        "assistantKey",
        "baseModel",
        "datasetIdentity",
        "trainingConfigIdentity",
        "trainingRunIdentity",
        "candidateId",
        "seed",
        "adapterMethod",
        "checkpoints",
        "exportedArtifacts",
        "sourceCommit",
        "metrics",
        "actualTraining",
        "runtimeVersions",
        "contractGates",
    }
    if not isinstance(metadata, dict) or not legacy_required.issubset(metadata):
        raise ValueError("real metadata: complete remediation provenance required")
    if (
        metadata.get("status") != "COMPLETED"
        or metadata.get("backend") != legacy.REAL_BACKEND
        or metadata.get("realTraining") is not True
        or metadata.get("qualityEvidence") != legacy.REAL_QUALITY_EVIDENCE
        or metadata.get("adapterDisposition") != "CANDIDATE_ONLY"
    ):
        raise ValueError("real metadata: completed candidate-only REAL_QLORA required")
    expected = {
        "assistantKey": config["assistantKey"],
        "baseModel": config["baseModel"],
        "datasetIdentity": config["dataset"]["identity"],
        "trainingConfigIdentity": training_config_identity(config),
        "trainingRunIdentity": training_run_identity(config),
        "seed": config["seed"],
        "adapterMethod": config["adapter"]["method"],
        "contractGates": config["contractGates"],
    }
    for field, value in expected.items():
        if metadata.get(field) != value:
            raise ValueError(f"real metadata/{field}: exact provenance mismatch")
    legacy._require_sha256(metadata.get("candidateId"), "real metadata/candidateId")
    if not isinstance(metadata.get("checkpoints"), list) or not metadata["checkpoints"]:
        raise ValueError("real metadata: checkpoint provenance required")
    if not isinstance(metadata.get("exportedArtifacts"), list) or not metadata["exportedArtifacts"]:
        raise ValueError("real metadata: exported artifacts required")
    if not isinstance(metadata.get("sourceCommit"), str) or legacy.COMMIT_PATTERN.fullmatch(
        metadata["sourceCommit"]
    ) is None:
        raise ValueError("real metadata: full source commit required")
    runtime = metadata.get("runtimeVersions")
    if not isinstance(runtime, dict):
        raise ValueError("real metadata: runtime versions required")
    for package, expected_version in legacy.EXPECTED_RUNTIME_VERSIONS.items():
        if runtime.get(package) != expected_version:
            raise ValueError(f"real metadata/runtimeVersions/{package}: pinned version required")
    metrics = metadata.get("metrics")
    if not isinstance(metrics, dict) or not legacy._finite_number(metrics.get("trainLoss")):
        raise ValueError("real metadata: machine-produced train loss required")
    actual = metadata.get("actualTraining")
    if (
        not isinstance(actual, dict)
        or actual.get("trainRecords") != 38
        or actual.get("validationRecords") != 4
        or actual.get("contractHoldoutRecords") != 3
        or actual.get("contractHoldoutUsedForOptimization") is not False
        or actual.get("validationSplit") != "validation"
        or actual.get("contractHoldoutSplit") != "evaluation"
    ):
        raise ValueError("real metadata: exact isolated remediation splits required")
    if not legacy._finite_number(actual.get("bestValidationMetric")):
        raise ValueError("real metadata: best validation checkpoint metric required")
    checkpoint_names = {
        item.get("checkpointName")
        for item in metadata["checkpoints"]
        if isinstance(item, dict)
    }
    if actual.get("bestCheckpoint") not in checkpoint_names:
        raise ValueError("real metadata: selected best checkpoint is not preserved")


def run_real_training(
    *,
    config: dict[str, Any],
    dataset_manifest_path: Path,
    output_directory: Path,
    model_path: Path,
    resume_from: Path | None,
    source_commit: str,
    decision: str,
    decision_reference: str,
) -> dict[str, Any]:
    if decision != "ADAPTER_REQUIRED":
        raise ValueError("real remediation training requires ADAPTER_REQUIRED")
    if legacy.COMMIT_PATTERN.fullmatch(source_commit) is None:
        raise ValueError("real remediation training requires a full source commit")
    legacy._configure_deterministic_runtime()
    validate_model_snapshot(model_path, config)
    inputs = load_training_inputs(dataset_manifest_path, config)
    if resume_from is not None:
        legacy.validate_resume_checkpoint(resume_from, config)
    runtime = legacy._runtime_modules(config["training"]["precision"])
    torch = runtime["torch"]
    transformers = runtime["transformers"]
    peft = runtime["peft"]
    numpy = runtime["numpy"]
    seed = config["seed"]
    os.environ["PYTHONHASHSEED"] = str(seed)
    os.environ["TOKENIZERS_PARALLELISM"] = "false"
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)
    numpy.random.seed(seed)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False
    torch.use_deterministic_algorithms(True, warn_only=False)

    quantization = config["adapter"]["quantization"]
    dtype = legacy._training_dtype(torch, config)
    quantization_config = transformers.BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type=quantization["quantType"],
        bnb_4bit_use_double_quant=quantization["doubleQuantization"],
        bnb_4bit_compute_dtype=dtype,
    )
    tokenizer = transformers.AutoTokenizer.from_pretrained(model_path, local_files_only=True)
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    model = transformers.AutoModelForCausalLM.from_pretrained(
        model_path,
        **legacy._model_loading_options(dtype, quantization_config),
    )
    model.config.use_cache = False
    model = peft.prepare_model_for_kbit_training(model, use_gradient_checkpointing=True)
    lora = config["adapter"]["lora"]
    model = peft.get_peft_model(
        model,
        peft.LoraConfig(
            task_type="CAUSAL_LM",
            inference_mode=False,
            r=lora["rank"],
            lora_alpha=lora["alpha"],
            lora_dropout=lora["dropout"],
            bias=lora["bias"],
            target_modules=lora["targetModules"],
        ),
    )
    legacy.training_messages = training_messages
    train_dataset, train_tokens = legacy._tokenize_records(inputs["trainingRecords"], tokenizer)
    validation_dataset, validation_tokens = legacy._tokenize_records(
        inputs["validationRecords"], tokenizer
    )
    checkpoint_root = output_directory / config["output"]["checkpointDirectory"]
    export_directory = output_directory / config["output"]["exportDirectory"]
    checkpoint_root.mkdir(parents=True, exist_ok=False)
    arguments = transformers.TrainingArguments(
        **training_argument_values(config, checkpoint_root)
    )
    callback = transformers.EarlyStoppingCallback(
        early_stopping_patience=config["training"]["earlyStoppingPatience"],
        early_stopping_threshold=config["training"]["earlyStoppingThreshold"],
    )
    trainer = transformers.Trainer(
        model=model,
        args=arguments,
        train_dataset=train_dataset,
        eval_dataset=validation_dataset,
        data_collator=legacy._data_collator(tokenizer, torch),
        processing_class=tokenizer,
        callbacks=[callback],
    )
    train_result = trainer.train(
        resume_from_checkpoint=str(resume_from.resolve()) if resume_from is not None else None
    )
    validation_metrics = trainer.evaluate(eval_dataset=validation_dataset)
    export_directory.mkdir(parents=True, exist_ok=False)
    model.save_pretrained(export_directory, safe_serialization=True)
    tokenizer.save_pretrained(export_directory)

    config_identity = training_config_identity(config)
    run_identity = training_run_identity(config)
    checkpoints = legacy._checkpoint_documents(
        checkpoint_root, config, config_identity, run_identity
    )
    candidate_id, exported_artifacts = legacy._adapter_documents(
        export_directory, config, config_identity, run_identity, source_commit
    )
    metrics = legacy._machine_metrics({**train_result.metrics, **validation_metrics})
    best_metric = trainer.state.best_metric
    if not legacy._finite_number(best_metric):
        raise ValueError("real remediation training: best validation metric missing")
    best_checkpoint = trainer.state.best_model_checkpoint
    if not isinstance(best_checkpoint, str) or not best_checkpoint:
        raise ValueError("real remediation training: best checkpoint missing")
    best_checkpoint_name = canonical_checkpoint_name(best_checkpoint)
    if best_checkpoint_name not in {item["checkpointName"] for item in checkpoints}:
        raise ValueError("real remediation training: best checkpoint was not preserved")
    actual_training = {
        "globalSteps": trainer.state.global_step,
        "epochs": float(trainer.state.epoch) if trainer.state.epoch is not None else None,
        "configuredEpochs": config["training"]["epochs"],
        "earlyStopped": bool(
            trainer.state.epoch is not None
            and float(trainer.state.epoch) < float(config["training"]["epochs"])
        ),
        "bestCheckpoint": best_checkpoint_name,
        "bestValidationMetric": float(best_metric),
        "bestMetricName": config["training"]["metricForBestModel"],
        "trainRecords": len(inputs["trainingRecords"]),
        "validationRecords": len(inputs["validationRecords"]),
        "contractHoldoutRecords": len(inputs["contractHoldoutRecords"]),
        "trainingSplit": "train",
        "validationSplit": "validation",
        "contractHoldoutSplit": "evaluation",
        "contractHoldoutUsedForOptimization": False,
        "trainingArtifact": inputs["trainingArtifact"],
        "validationArtifact": inputs["validationArtifact"],
        "contractHoldoutArtifact": inputs["contractHoldoutArtifact"],
        "trainingTokenization": train_tokens,
        "validationTokenization": validation_tokens,
    }
    runtime_versions = {**runtime["versions"], "python": platform.python_version()}
    evidence = {
        "schemaVersion": "2.0.0",
        "artifactType": "P7-T2-REMEDIATION-REAL-TRAINING-EXECUTION-EVIDENCE",
        "backend": legacy.REAL_BACKEND,
        "realTraining": True,
        "qualityEvidence": legacy.REAL_QUALITY_EVIDENCE,
        "candidateId": candidate_id,
        "trainingRunIdentity": run_identity,
        "trainingConfigIdentity": config_identity,
        "datasetIdentity": config["dataset"]["identity"],
        "baseModel": config["baseModel"],
        "seed": seed,
        "decision": decision,
        "decisionReference": decision_reference,
        "sourceCommit": source_commit,
        "contractGates": config["contractGates"],
        "actualTraining": actual_training,
        "metrics": metrics,
        "runtimeVersions": runtime_versions,
        "environment": {"platform": platform.platform(), "gpu": runtime["gpu"]},
        "checkpoints": checkpoints,
        "exportedArtifacts": exported_artifacts,
    }
    evidence["artifactIdentity"] = legacy._identity(evidence, "artifactIdentity")
    legacy._write_json(output_directory / legacy.REAL_EVIDENCE_FILENAME, evidence)
    metadata = {
        "schemaVersion": "2.0.0",
        "pipelineVersion": config["pipelineVersion"],
        "assistantKey": config["assistantKey"],
        "decision": decision,
        "decisionReference": decision_reference,
        "status": "COMPLETED",
        "backend": legacy.REAL_BACKEND,
        "realTraining": True,
        "qualityEvidence": legacy.REAL_QUALITY_EVIDENCE,
        "adapterDisposition": "CANDIDATE_ONLY",
        "baseModel": config["baseModel"],
        "datasetManifestReference": config["dataset"]["manifestReference"],
        "datasetIdentity": config["dataset"]["identity"],
        "trainingConfigIdentity": config_identity,
        "trainingRunIdentity": run_identity,
        "candidateId": candidate_id,
        "seed": seed,
        "adapterMethod": config["adapter"]["method"],
        "adapterParameters": config["adapter"],
        "trainingParameters": config["training"],
        "contractGates": config["contractGates"],
        "sourceCommit": source_commit,
        "metrics": metrics,
        "actualTraining": actual_training,
        "runtimeVersions": runtime_versions,
        "environment": evidence["environment"],
        "checkpoints": checkpoints,
        "resumeSource": str(resume_from.resolve()) if resume_from is not None else None,
        "exportedArtifacts": exported_artifacts,
        "realTrainingEvidence": {
            "filename": legacy.REAL_EVIDENCE_FILENAME,
            "sha256": sha256_bytes(
                (output_directory / legacy.REAL_EVIDENCE_FILENAME).read_bytes()
            ),
            "artifactIdentity": evidence["artifactIdentity"],
        },
    }
    validate_real_metadata_contract(metadata, config)
    legacy._write_json(output_directory / config["output"]["metadataFilename"], metadata)
    return metadata


def validate_real_training_output(
    output_directory: Path, config: dict[str, Any], dataset_manifest_path: Path
) -> dict[str, Any]:
    legacy.load_training_inputs = load_training_inputs
    legacy.validate_real_metadata_contract = validate_real_metadata_contract
    return legacy.validate_real_training_output(output_directory, config, dataset_manifest_path)
