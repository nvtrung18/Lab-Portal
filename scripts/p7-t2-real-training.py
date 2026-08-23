#!/usr/bin/env python3
"""Real single-GPU QLoRA backend and artifact validator for P7-T2."""
from __future__ import annotations

import hashlib
import importlib.metadata
import json
import math
import os
from pathlib import Path
import platform
import re
from typing import Any


MODEL_IDENTITY_FILENAME = "p7-t2-model-identity.json"
REAL_BACKEND = "REAL_QLORA"
REAL_QUALITY_EVIDENCE = "REAL_TRAINING_EXECUTION"
REAL_EVIDENCE_FILENAME = "real-training-evidence.json"
ADAPTER_MANIFEST_FILENAME = "adapter-manifest.json"
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
COMMIT_PATTERN = re.compile(r"^[0-9a-f]{40}$")
MINIMUM_CUDA_MEMORY_BYTES = 12 * 1024**3
SYSTEM_MESSAGE = (
    "You are RESEARCH_ASSISTANT. Return only the requested structured JSON result. "
    "Remain advisory, preserve authorization boundaries, and refuse unsafe or missing-context requests."
)
TRAINING_INPUT_FIELDS = (
    "schemaVersion",
    "domain",
    "recordType",
    "visibility",
    "useCaseId",
    "input",
    "payload",
)
EXPECTED_RUNTIME_VERSIONS = {
    "accelerate": "1.6.0",
    "bitsandbytes": "0.48.1",
    "numpy": "2.5.2",
    "peft": "0.15.2",
    "torch": "2.7.1+cu118",
    "transformers": "4.51.3",
}


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def _write_json(path: Path, value: object) -> None:
    path.write_bytes(_json_bytes(value))


def _identity(value: dict[str, Any], field: str) -> str:
    return sha256_bytes(canonical_bytes({key: item for key, item in value.items() if key != field}))


def _require_sha256(value: object, label: str) -> str:
    if not isinstance(value, str) or not SHA256_PATTERN.fullmatch(value):
        raise ValueError(f"{label}: lowercase SHA-256 required")
    return value


def training_config_identity(config: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes(config))


def training_run_identity(config: dict[str, Any]) -> str:
    document = {
        "schemaVersion": config["schemaVersion"],
        "assistantKey": config["assistantKey"],
        "baseModel": config["baseModel"],
        "datasetIdentity": config["dataset"]["identity"],
        "trainingConfigIdentity": training_config_identity(config),
        "seed": config["seed"],
        "adapterMethod": config["adapter"]["method"],
    }
    return sha256_bytes(canonical_bytes(document))


def training_messages(record: dict[str, Any]) -> list[dict[str, str]]:
    if not isinstance(record, dict):
        raise ValueError("training record: object required")
    missing = [field for field in (*TRAINING_INPUT_FIELDS, "expectedOutput") if field not in record]
    if missing:
        raise ValueError("training record: missing fields: " + ", ".join(missing))
    if record.get("domain") != "RESEARCH" or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY":
        raise ValueError("training record: Research-only boundary required")
    request = {field: record[field] for field in TRAINING_INPUT_FIELDS}
    expected_output = record.get("expectedOutput")
    if not isinstance(expected_output, dict):
        raise ValueError("training record/expectedOutput: object required")
    return [
        {"role": "system", "content": SYSTEM_MESSAGE},
        {"role": "user", "content": canonical_bytes(request).decode("utf-8")},
        {"role": "assistant", "content": canonical_bytes(expected_output).decode("utf-8")},
    ]


def _load_json(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"{label}: cannot load {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{label}: object required")
    return value


def _load_jsonl(path: Path, expected_count: int, label: str) -> list[dict[str, Any]]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as error:
        raise ValueError(f"{label}: cannot read: {error}") from error
    records: list[dict[str, Any]] = []
    for line_number, line in enumerate(lines, start=1):
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError(f"{label}:{line_number}: invalid JSON: {error}") from error
        if not isinstance(value, dict):
            raise ValueError(f"{label}:{line_number}: object required")
        training_messages(value)
        records.append(value)
    if len(records) != expected_count:
        raise ValueError(f"{label}: record count mismatch")
    content_ids = [record.get("contentId") for record in records]
    if any(not isinstance(item, str) or not SHA256_PATTERN.fullmatch(item) for item in content_ids):
        raise ValueError(f"{label}: canonical contentId required")
    if len(content_ids) != len(set(content_ids)):
        raise ValueError(f"{label}: duplicate contentId")
    return records


def load_training_inputs(manifest_path: Path, config: dict[str, Any]) -> dict[str, Any]:
    if config.get("splits") != {"training": "train", "evaluation": "validation"}:
        raise ValueError("real training requires exact train/validation split separation")
    manifest = _load_json(manifest_path, "dataset manifest")
    if manifest.get("checksum") != config.get("dataset", {}).get("identity"):
        raise ValueError("dataset manifest: configured identity mismatch")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list):
        raise ValueError("dataset manifest: artifact inventory required")
    by_name = {
        item.get("filename"): item
        for item in artifacts
        if isinstance(item, dict) and isinstance(item.get("filename"), str)
    }
    training_name = f"{config['splits']['training']}.jsonl"
    validation_name = f"{config['splits']['evaluation']}.jsonl"
    if training_name != "train.jsonl" or validation_name != "validation.jsonl":
        raise ValueError("real training: evaluation or frozen data cannot be optimization input")
    result: dict[str, Any] = {"manifest": manifest}
    for key, filename in (("training", training_name), ("validation", validation_name)):
        artifact = by_name.get(filename)
        if not isinstance(artifact, dict):
            raise ValueError(f"dataset manifest: missing {filename}")
        expected_sha256 = _require_sha256(artifact.get("sha256"), f"dataset/{filename}")
        path = manifest_path.parent / filename
        if not path.is_file() or sha256_bytes(path.read_bytes()) != expected_sha256:
            raise ValueError(f"dataset/{filename}: checksum mismatch")
        count = artifact.get("recordCount")
        if not isinstance(count, int) or isinstance(count, bool) or count <= 0:
            raise ValueError(f"dataset/{filename}: positive record count required")
        result[f"{key}Artifact"] = artifact
        result[f"{key}Records"] = _load_jsonl(path, count, f"dataset/{filename}")
    return result


def validate_model_snapshot(model_path: Path, config: dict[str, Any]) -> dict[str, str]:
    model_path = model_path.resolve()
    if not model_path.is_dir() or not (model_path / "config.json").is_file():
        raise ValueError("model snapshot: local config.json required")
    marker = _load_json(model_path / MODEL_IDENTITY_FILENAME, "model snapshot identity")
    expected = config.get("baseModel")
    if set(marker) != {"identifier", "revision"} or marker != expected:
        if marker.get("identifier") != expected.get("identifier"):
            raise ValueError("model snapshot identifier mismatch")
        raise ValueError("model snapshot revision mismatch")
    return {"identifier": marker["identifier"], "revision": marker["revision"]}


def validate_real_metadata_contract(metadata: object, config: dict[str, Any]) -> None:
    required = {
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
    }
    if not isinstance(metadata, dict) or not required.issubset(metadata):
        raise ValueError("real metadata: complete provenance required")
    if (
        metadata.get("status") != "COMPLETED"
        or metadata.get("backend") != REAL_BACKEND
        or metadata.get("realTraining") is not True
        or metadata.get("qualityEvidence") != REAL_QUALITY_EVIDENCE
    ):
        raise ValueError("real metadata: REAL_QLORA completed execution required")
    if metadata.get("adapterDisposition") != "CANDIDATE_ONLY":
        raise ValueError("real metadata: CANDIDATE_ONLY required")
    exact = {
        "assistantKey": config["assistantKey"],
        "baseModel": config["baseModel"],
        "datasetIdentity": config["dataset"]["identity"],
        "trainingConfigIdentity": training_config_identity(config),
        "trainingRunIdentity": training_run_identity(config),
        "seed": config["seed"],
        "adapterMethod": config["adapter"]["method"],
    }
    for field, value in exact.items():
        if metadata.get(field) != value:
            raise ValueError(f"real metadata/{field}: exact provenance mismatch")
    _require_sha256(metadata.get("candidateId"), "real metadata/candidateId")
    if not isinstance(metadata.get("checkpoints"), list) or not metadata["checkpoints"]:
        raise ValueError("real metadata: checkpoint provenance required")
    if not isinstance(metadata.get("exportedArtifacts"), list) or not metadata["exportedArtifacts"]:
        raise ValueError("real metadata: exported artifacts required")
    if not isinstance(metadata.get("sourceCommit"), str) or not COMMIT_PATTERN.fullmatch(metadata["sourceCommit"]):
        raise ValueError("real metadata: full source commit required")
    runtime = metadata.get("runtimeVersions")
    if not isinstance(runtime, dict):
        raise ValueError("real metadata: runtime versions required")
    for package, expected in EXPECTED_RUNTIME_VERSIONS.items():
        if runtime.get(package) != expected:
            raise ValueError(f"real metadata/runtimeVersions/{package}: pinned version required")
    metrics = metadata.get("metrics")
    if not isinstance(metrics, dict) or not _finite_number(metrics.get("trainLoss")):
        raise ValueError("real metadata: machine-produced train loss required")
    actual = metadata.get("actualTraining")
    if not isinstance(actual, dict) or actual.get("trainRecords") != 36 or actual.get("validationRecords") != 3:
        raise ValueError("real metadata: exact train/validation record counts required")
    if config["training"]["maxSteps"] is not None and actual.get("globalSteps") != config["training"]["maxSteps"]:
        raise ValueError("real metadata: configured maxSteps not completed")


def _finite_number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def _validate_cuda_runtime(torch: Any, precision: str) -> dict[str, Any]:
    if precision not in {"float16", "bfloat16", "float32"}:
        raise ValueError("real runtime requires a supported training precision")
    if not torch.cuda.is_available() or torch.cuda.device_count() < 1:
        raise ValueError("real runtime requires an NVIDIA CUDA GPU")
    if precision == "bfloat16" and not torch.cuda.is_bf16_supported():
        raise ValueError("real runtime requires native bfloat16 CUDA support")
    properties = torch.cuda.get_device_properties(0)
    if properties.total_memory < MINIMUM_CUDA_MEMORY_BYTES:
        raise ValueError("real runtime requires at least 12 GiB CUDA memory")
    return {
        "name": properties.name,
        "totalMemoryBytes": properties.total_memory,
        "cudaRuntime": str(torch.version.cuda),
        "device": "cuda:0",
    }


def _training_dtype(torch: Any, config: dict[str, Any]) -> Any:
    training_precision = config.get("training", {}).get("precision")
    compute_dtype = config.get("adapter", {}).get("quantization", {}).get("computeDtype")
    if training_precision != compute_dtype:
        raise ValueError("real training: quantization compute dtype must match training precision")
    mapping = {
        "float16": torch.float16,
        "bfloat16": torch.bfloat16,
        "float32": torch.float32,
    }
    if training_precision not in mapping:
        raise ValueError("real training: supported compute dtype required")
    return mapping[training_precision]


def _runtime_modules(precision: str = "bfloat16") -> dict[str, Any]:
    try:
        import accelerate
        import bitsandbytes
        import numpy
        import peft
        import torch
        import transformers
    except ImportError as error:
        raise ValueError(f"real runtime dependency unavailable: {error.name}") from error
    modules = {
        "accelerate": accelerate,
        "bitsandbytes": bitsandbytes,
        "numpy": numpy,
        "peft": peft,
        "torch": torch,
        "transformers": transformers,
    }
    observed = {name: importlib.metadata.version(name) for name in EXPECTED_RUNTIME_VERSIONS}
    if observed != EXPECTED_RUNTIME_VERSIONS:
        raise ValueError(f"real runtime version mismatch: expected={EXPECTED_RUNTIME_VERSIONS} observed={observed}")
    if platform.python_version_tuple()[:2] != ("3", "12"):
        raise ValueError("real runtime requires CPython 3.12")
    gpu = _validate_cuda_runtime(torch, precision)
    modules["versions"] = observed
    modules["gpu"] = gpu
    return modules


class _TokenizedDataset:
    def __init__(self, examples: list[dict[str, list[int]]]):
        self.examples = examples

    def __len__(self) -> int:
        return len(self.examples)

    def __getitem__(self, index: int) -> dict[str, list[int]]:
        return self.examples[index]


def _tokenize_records(records: list[dict[str, Any]], tokenizer: Any) -> tuple[_TokenizedDataset, dict[str, int]]:
    examples: list[dict[str, list[int]]] = []
    lengths: list[int] = []
    for record in records:
        messages = training_messages(record)
        prompt_ids = tokenizer.apply_chat_template(
            messages[:2], tokenize=True, add_generation_prompt=True
        )
        full_ids = tokenizer.apply_chat_template(
            messages, tokenize=True, add_generation_prompt=False
        )
        if not isinstance(prompt_ids, list) or not isinstance(full_ids, list) or full_ids[: len(prompt_ids)] != prompt_ids:
            raise ValueError("tokenizer chat template: assistant response boundary mismatch")
        labels = [-100] * len(prompt_ids) + full_ids[len(prompt_ids) :]
        if not full_ids or all(item == -100 for item in labels):
            raise ValueError("tokenizer chat template: supervised assistant tokens required")
        examples.append({"input_ids": full_ids, "attention_mask": [1] * len(full_ids), "labels": labels})
        lengths.append(len(full_ids))
    return _TokenizedDataset(examples), {
        "minimumTokens": min(lengths),
        "maximumTokens": max(lengths),
        "totalTokens": sum(lengths),
    }


def _data_collator(tokenizer: Any, torch: Any):
    pad_token_id = tokenizer.pad_token_id

    def collate(features: list[dict[str, list[int]]]) -> dict[str, Any]:
        width = max(len(item["input_ids"]) for item in features)
        batch = {"input_ids": [], "attention_mask": [], "labels": []}
        for item in features:
            padding = width - len(item["input_ids"])
            batch["input_ids"].append(item["input_ids"] + [pad_token_id] * padding)
            batch["attention_mask"].append(item["attention_mask"] + [0] * padding)
            batch["labels"].append(item["labels"] + [-100] * padding)
        return {key: torch.tensor(value, dtype=torch.long) for key, value in batch.items()}

    return collate


def _inventory(root: Path, *, excluded: set[str] | None = None) -> list[dict[str, Any]]:
    excluded = excluded or set()
    result = []
    for path in sorted((item for item in root.rglob("*") if item.is_file()), key=lambda item: item.relative_to(root).as_posix()):
        logical = path.relative_to(root).as_posix()
        if logical in excluded:
            continue
        payload = path.read_bytes()
        result.append({"filename": logical, "size": len(payload), "sha256": sha256_bytes(payload)})
    return result


def _checkpoint_documents(
    checkpoint_root: Path,
    config: dict[str, Any],
    config_identity: str,
    run_identity: str,
) -> list[dict[str, Any]]:
    checkpoints: list[dict[str, Any]] = []
    raw = []
    for path in checkpoint_root.glob("checkpoint-*"):
        match = re.fullmatch(r"checkpoint-([0-9]+)", path.name)
        if path.is_dir() and match:
            raw.append((int(match.group(1)), path))
    for global_step, original in sorted(raw):
        canonical = checkpoint_root / f"checkpoint-{global_step:08d}"
        if original != canonical:
            if canonical.exists():
                raise ValueError(f"checkpoint collision: {canonical.name}")
            original.rename(canonical)
        inventory = _inventory(canonical, excluded={"checkpoint-metadata.json"})
        if not inventory:
            raise ValueError(f"checkpoint {canonical.name}: state artifacts required")
        document = {
            "schemaVersion": "1.0.0",
            "pipelineVersion": config["pipelineVersion"],
            "backend": REAL_BACKEND,
            "trainingRunIdentity": run_identity,
            "trainingConfigIdentity": config_identity,
            "datasetIdentity": config["dataset"]["identity"],
            "baseModel": config["baseModel"],
            "assistantKey": config["assistantKey"],
            "adapterMethod": config["adapter"]["method"],
            "globalStep": global_step,
            "seed": config["seed"],
            "artifactInventory": inventory,
        }
        document["checkpointIdentity"] = _identity(document, "checkpointIdentity")
        _write_json(canonical / "checkpoint-metadata.json", document)
        checkpoints.append(
            {
                "checkpointName": canonical.name,
                "globalStep": global_step,
                "checkpointIdentity": document["checkpointIdentity"],
                "metadataSha256": sha256_bytes((canonical / "checkpoint-metadata.json").read_bytes()),
            }
        )
    if not checkpoints:
        raise ValueError("real training produced no resumable checkpoint")
    return checkpoints


def _adapter_documents(
    export_directory: Path,
    config: dict[str, Any],
    config_identity: str,
    run_identity: str,
    source_commit: str,
) -> tuple[str, list[dict[str, Any]]]:
    inventory = _inventory(export_directory, excluded={ADAPTER_MANIFEST_FILENAME})
    names = {item["filename"] for item in inventory}
    if "adapter_model.safetensors" not in names or "adapter_config.json" not in names:
        raise ValueError("real adapter: PEFT safetensors and config required")
    forbidden = [
        name
        for name in names
        if name in {"model.safetensors", "pytorch_model.bin"}
        or name.startswith("model-")
    ]
    if forbidden:
        raise ValueError("real adapter: base-model weights must not be exported")
    adapter_identity = sha256_bytes(canonical_bytes(inventory))
    candidate_id = sha256_bytes(
        canonical_bytes({"trainingRunIdentity": run_identity, "adapterIdentity": adapter_identity})
    )
    manifest = {
        "schemaVersion": "1.0.0",
        "pipelineVersion": config["pipelineVersion"],
        "backend": REAL_BACKEND,
        "realTraining": True,
        "adapterDisposition": "CANDIDATE_ONLY",
        "qualityEvidence": REAL_QUALITY_EVIDENCE,
        "assistantKey": config["assistantKey"],
        "baseModel": config["baseModel"],
        "datasetIdentity": config["dataset"]["identity"],
        "trainingConfigIdentity": config_identity,
        "trainingRunIdentity": run_identity,
        "candidateId": candidate_id,
        "adapterIdentity": adapter_identity,
        "seed": config["seed"],
        "sourceCommit": source_commit,
        "artifacts": inventory,
    }
    _write_json(export_directory / ADAPTER_MANIFEST_FILENAME, manifest)
    return candidate_id, _inventory(export_directory)


def _machine_metrics(values: dict[str, Any]) -> dict[str, float]:
    result: dict[str, float] = {}
    mapping = {
        "train_loss": "trainLoss",
        "train_runtime": "trainRuntimeSeconds",
        "train_samples_per_second": "trainSamplesPerSecond",
        "train_steps_per_second": "trainStepsPerSecond",
        "eval_loss": "validationLoss",
        "eval_runtime": "validationRuntimeSeconds",
        "eval_samples_per_second": "validationSamplesPerSecond",
        "eval_steps_per_second": "validationStepsPerSecond",
    }
    for source, destination in mapping.items():
        value = values.get(source)
        if _finite_number(value):
            result[destination] = float(value)
    if "trainLoss" not in result:
        raise ValueError("real training: Trainer did not produce train_loss")
    return result


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
        raise ValueError("real training requires ADAPTER_REQUIRED decision")
    if not COMMIT_PATTERN.fullmatch(source_commit):
        raise ValueError("real training requires a full source commit")
    validate_model_snapshot(model_path, config)
    inputs = load_training_inputs(dataset_manifest_path, config)
    if resume_from is not None:
        validate_resume_checkpoint(resume_from, config)
    runtime = _runtime_modules(config["training"]["precision"])
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
    torch.use_deterministic_algorithms(True, warn_only=True)

    quantization = config["adapter"]["quantization"]
    dtype = _training_dtype(torch, config)
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
        local_files_only=True,
        device_map={"": 0},
        torch_dtype=dtype,
        quantization_config=quantization_config,
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
    train_dataset, train_tokens = _tokenize_records(inputs["trainingRecords"], tokenizer)
    validation_dataset, validation_tokens = _tokenize_records(inputs["validationRecords"], tokenizer)

    checkpoint_root = output_directory / config["output"]["checkpointDirectory"]
    export_directory = output_directory / config["output"]["exportDirectory"]
    checkpoint_root.mkdir(parents=True, exist_ok=False)
    training = config["training"]
    arguments = transformers.TrainingArguments(
        output_dir=str(checkpoint_root),
        overwrite_output_dir=False,
        per_device_train_batch_size=training["batchSize"],
        per_device_eval_batch_size=training["batchSize"],
        gradient_accumulation_steps=training["gradientAccumulation"],
        learning_rate=training["learningRate"],
        num_train_epochs=float(training["epochs"] or 1),
        max_steps=training["maxSteps"] if training["maxSteps"] is not None else -1,
        bf16=training["precision"] == "bfloat16",
        fp16=training["precision"] == "float16",
        gradient_checkpointing=True,
        save_strategy="steps",
        save_steps=training["checkpointFrequency"],
        eval_strategy="no",
        logging_strategy="steps",
        logging_steps=1,
        seed=seed,
        data_seed=seed,
        report_to=[],
        remove_unused_columns=False,
        prediction_loss_only=True,
        save_safetensors=True,
    )
    trainer = transformers.Trainer(
        model=model,
        args=arguments,
        train_dataset=train_dataset,
        eval_dataset=validation_dataset,
        data_collator=_data_collator(tokenizer, torch),
        processing_class=tokenizer,
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
    checkpoints = _checkpoint_documents(checkpoint_root, config, config_identity, run_identity)
    candidate_id, exported_artifacts = _adapter_documents(
        export_directory, config, config_identity, run_identity, source_commit
    )
    metrics = _machine_metrics({**train_result.metrics, **validation_metrics})
    actual_training = {
        "globalSteps": trainer.state.global_step,
        "epochs": float(trainer.state.epoch) if trainer.state.epoch is not None else None,
        "trainRecords": len(inputs["trainingRecords"]),
        "validationRecords": len(inputs["validationRecords"]),
        "trainingSplit": "train",
        "validationSplit": "validation",
        "trainingArtifact": inputs["trainingArtifact"],
        "validationArtifact": inputs["validationArtifact"],
        "trainingTokenization": train_tokens,
        "validationTokenization": validation_tokens,
    }
    runtime_versions = {
        **runtime["versions"],
        "python": platform.python_version(),
    }
    evidence = {
        "schemaVersion": "1.0.0",
        "artifactType": "P7-T2-REAL-TRAINING-EXECUTION-EVIDENCE",
        "backend": REAL_BACKEND,
        "realTraining": True,
        "qualityEvidence": REAL_QUALITY_EVIDENCE,
        "candidateId": candidate_id,
        "trainingRunIdentity": run_identity,
        "trainingConfigIdentity": config_identity,
        "datasetIdentity": config["dataset"]["identity"],
        "baseModel": config["baseModel"],
        "seed": seed,
        "decision": decision,
        "decisionReference": decision_reference,
        "sourceCommit": source_commit,
        "actualTraining": actual_training,
        "metrics": metrics,
        "runtimeVersions": runtime_versions,
        "environment": {"platform": platform.platform(), "gpu": runtime["gpu"]},
        "checkpoints": checkpoints,
        "exportedArtifacts": exported_artifacts,
    }
    evidence["artifactIdentity"] = _identity(evidence, "artifactIdentity")
    _write_json(output_directory / REAL_EVIDENCE_FILENAME, evidence)
    metadata = {
        "schemaVersion": "1.0.0",
        "pipelineVersion": config["pipelineVersion"],
        "assistantKey": config["assistantKey"],
        "decision": decision,
        "decisionReference": decision_reference,
        "status": "COMPLETED",
        "backend": REAL_BACKEND,
        "realTraining": True,
        "qualityEvidence": REAL_QUALITY_EVIDENCE,
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
        "sourceCommit": source_commit,
        "metrics": metrics,
        "actualTraining": actual_training,
        "runtimeVersions": runtime_versions,
        "environment": evidence["environment"],
        "checkpoints": checkpoints,
        "resumeSource": str(resume_from.resolve()) if resume_from is not None else None,
        "exportedArtifacts": exported_artifacts,
        "realTrainingEvidence": {
            "filename": REAL_EVIDENCE_FILENAME,
            "sha256": sha256_bytes((output_directory / REAL_EVIDENCE_FILENAME).read_bytes()),
            "artifactIdentity": evidence["artifactIdentity"],
        },
    }
    validate_real_metadata_contract(metadata, config)
    _write_json(output_directory / config["output"]["metadataFilename"], metadata)
    return metadata


def _validate_inventory(
    root: Path,
    inventory: object,
    label: str,
    *,
    excluded: set[str] | None = None,
) -> None:
    if not isinstance(inventory, list) or not inventory:
        raise ValueError(f"{label}: non-empty inventory required")
    seen: set[str] = set()
    for item in inventory:
        if not isinstance(item, dict) or not isinstance(item.get("filename"), str):
            raise ValueError(f"{label}: malformed inventory entry")
        filename = item["filename"]
        if filename in seen or Path(filename).is_absolute() or ".." in Path(filename).parts:
            raise ValueError(f"{label}: safe unique filename required")
        seen.add(filename)
        expected = _require_sha256(item.get("sha256"), f"{label}/{filename}")
        path = root / filename
        if not path.is_file() or sha256_bytes(path.read_bytes()) != expected:
            raise ValueError(f"{label}/{filename}: checksum mismatch")
    if inventory != _inventory(root, excluded=excluded):
        raise ValueError(f"{label}: exact file inventory mismatch")


def validate_resume_checkpoint(
    checkpoint_directory: Path,
    config: dict[str, Any],
) -> dict[str, Any]:
    checkpoint_directory = checkpoint_directory.resolve()
    match = re.fullmatch(r"checkpoint-([0-9]+)", checkpoint_directory.name)
    if not checkpoint_directory.is_dir() or match is None:
        raise ValueError("real resume: canonical checkpoint directory required")
    document = _load_json(
        checkpoint_directory / "checkpoint-metadata.json", "real resume checkpoint metadata"
    )
    expected = {
        "backend": REAL_BACKEND,
        "trainingRunIdentity": training_run_identity(config),
        "trainingConfigIdentity": training_config_identity(config),
        "datasetIdentity": config["dataset"]["identity"],
        "baseModel": config["baseModel"],
        "assistantKey": config["assistantKey"],
        "adapterMethod": config["adapter"]["method"],
        "seed": config["seed"],
    }
    for field, value in expected.items():
        if document.get(field) != value:
            raise ValueError(f"real resume/{field}: checkpoint provenance mismatch")
    global_step = document.get("globalStep")
    if not isinstance(global_step, int) or isinstance(global_step, bool) or global_step <= 0:
        raise ValueError("real resume: positive global step required")
    if int(match.group(1)) != global_step:
        raise ValueError("real resume: directory/global step mismatch")
    if document.get("checkpointIdentity") != _identity(document, "checkpointIdentity"):
        raise ValueError("real resume: checkpoint identity mismatch")
    _validate_inventory(
        checkpoint_directory,
        document.get("artifactInventory"),
        "real resume/checkpoint",
        excluded={"checkpoint-metadata.json"},
    )
    return document


def validate_real_training_output(
    output_directory: Path,
    config: dict[str, Any],
    dataset_manifest_path: Path,
) -> dict[str, Any]:
    metadata_path = output_directory / config["output"]["metadataFilename"]
    metadata = _load_json(metadata_path, "real training metadata")
    validate_real_metadata_contract(metadata, config)
    inputs = load_training_inputs(dataset_manifest_path, config)
    actual = metadata["actualTraining"]
    if actual.get("trainingArtifact") != inputs["trainingArtifact"] or actual.get("validationArtifact") != inputs["validationArtifact"]:
        raise ValueError("real output: split artifact provenance mismatch")
    export_directory = output_directory / config["output"]["exportDirectory"]
    _validate_inventory(export_directory, metadata["exportedArtifacts"], "real output/adapter")
    adapter_manifest = _load_json(export_directory / ADAPTER_MANIFEST_FILENAME, "adapter manifest")
    _validate_inventory(
        export_directory,
        adapter_manifest.get("artifacts"),
        "adapter manifest/artifacts",
        excluded={ADAPTER_MANIFEST_FILENAME},
    )
    if adapter_manifest.get("trainingRunIdentity") != metadata["trainingRunIdentity"]:
        raise ValueError("adapter manifest: training run mismatch")
    adapter_identity = sha256_bytes(canonical_bytes(adapter_manifest["artifacts"]))
    expected_candidate = sha256_bytes(
        canonical_bytes(
            {"trainingRunIdentity": metadata["trainingRunIdentity"], "adapterIdentity": adapter_identity}
        )
    )
    if (
        adapter_manifest.get("adapterIdentity") != adapter_identity
        or adapter_manifest.get("candidateId") != expected_candidate
        or metadata["candidateId"] != expected_candidate
    ):
        raise ValueError("real output: adapter/candidate identity mismatch")
    checkpoint_root = output_directory / config["output"]["checkpointDirectory"]
    for checkpoint in metadata["checkpoints"]:
        name = checkpoint.get("checkpointName")
        document = _load_json(checkpoint_root / name / "checkpoint-metadata.json", "checkpoint metadata")
        if document.get("checkpointIdentity") != _identity(document, "checkpointIdentity"):
            raise ValueError(f"checkpoint {name}: identity mismatch")
        _validate_inventory(
            checkpoint_root / name,
            document.get("artifactInventory"),
            f"checkpoint {name}",
            excluded={"checkpoint-metadata.json"},
        )
        if checkpoint.get("checkpointIdentity") != document["checkpointIdentity"]:
            raise ValueError(f"checkpoint {name}: metadata binding mismatch")
    evidence_reference = metadata.get("realTrainingEvidence")
    if not isinstance(evidence_reference, dict):
        raise ValueError("real output: execution evidence reference required")
    evidence_path = output_directory / evidence_reference.get("filename", "")
    evidence = _load_json(evidence_path, "real training evidence")
    if sha256_bytes(evidence_path.read_bytes()) != evidence_reference.get("sha256"):
        raise ValueError("real output: execution evidence checksum mismatch")
    if evidence.get("artifactIdentity") != _identity(evidence, "artifactIdentity"):
        raise ValueError("real output: execution evidence identity mismatch")
    if evidence.get("candidateId") != metadata["candidateId"]:
        raise ValueError("real output: execution evidence candidate mismatch")
    allowed_files = {
        config["output"]["metadataFilename"],
        REAL_EVIDENCE_FILENAME,
    }
    for path in (item for item in output_directory.rglob("*") if item.is_file()):
        logical = path.relative_to(output_directory).as_posix()
        if (
            logical not in allowed_files
            and not logical.startswith(config["output"]["checkpointDirectory"] + "/")
            and not logical.startswith(config["output"]["exportDirectory"] + "/")
        ):
            raise ValueError(f"real output: ungoverned artifact present: {logical}")
    return metadata
