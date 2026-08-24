#!/usr/bin/env python3
"""Validate the fail-closed remediation gate after a P7-T4 automatic failure."""
from __future__ import annotations

import argparse
from collections import Counter
import copy
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "config" / "p7-t4-research-remediation.json"
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
EXPECTED_REPETITIONS = ("R01", "R02", "R03")
EXPECTED_ENVELOPE_KEYS = (
    "evalCaseId",
    "observedActionRisk",
    "observedBehavior",
    "referencedContextIds",
    "response",
    "structuredOutput",
    "toolRequest",
)
FAILED_EVALUATION_ROOT = (
    "evidence/p7-t4-research-independent-evaluation/automatic-fail"
)
CURRENT_TRAINING_CONFIG_REFERENCE = "config/p7-t2-training-pipeline-t4.json"
SERVING_PROFILE_REFERENCE = "ai-service/config/assistant-profiles.json"
SERVING_SCHEMA_REFERENCE = (
    "ai-service/config/schemas/structured-output-schemas.json"
)
REMEDIATION_SOURCE_REFERENCE = (
    "datasets/p7-t4-research-remediation-source-v2/source-export.json"
)
REMEDIATION_PROVENANCE_REFERENCE = (
    "datasets/p7-t4-research-remediation-source-v2/provenance.json"
)
REMEDIATION_CONTRACT_REFERENCE = (
    "datasets/p7-t4-research-remediation-source-v2/training-contract.json"
)
REMEDIATION_REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v2/training-approval-request.json"
)
REMEDIATION_CARD_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v2/training-dataset-card.pending.json"
)
REMEDIATION_APPROVED_CARD_REFERENCE = (
    "config/p7-t1c-research-remediation-governance-v2/training-dataset-card.approved.json"
)
REMEDIATION_APPROVAL_REFERENCE = (
    "evidence/p7-t1c-research-remediation-training-governance-approval.json"
)
REMEDIATION_DATASET_MANIFEST_REFERENCE = (
    "datasets/p7-research-synthetic-training-dataset-v2/manifest.json"
)
REMEDIATION_TRAINING_CONFIG_REFERENCE = (
    "config/p7-t2-training-pipeline-t4-remediation.json"
)
REMEDIATION_TRAINING_PIPELINE_REFERENCE = (
    "scripts/training-pipeline-p7-t2-remediation.py"
)
REMEDIATION_REAL_TRAINING_REFERENCE = "scripts/p7-t2-real-training-remediation.py"
REMEDIATION_EVIDENCE_ROOT = "evidence/p7-t2-real-training/remediation-v2"
REMEDIATION_ADAPTER_MANIFEST_REFERENCE = (
    f"{REMEDIATION_EVIDENCE_ROOT}/adapter-manifest.json"
)
REMEDIATION_REAL_EVIDENCE_REFERENCE = (
    f"{REMEDIATION_EVIDENCE_ROOT}/real-training-evidence.json"
)
REMEDIATION_TRAINING_METADATA_REFERENCE = (
    f"{REMEDIATION_EVIDENCE_ROOT}/training-metadata.json"
)
REMEDIATION_ARCHIVE_SHA256_REFERENCE = (
    f"{REMEDIATION_EVIDENCE_ROOT}/p7-t2-research-remediation-output.zip.sha256"
)
REMEDIATION_EVALUATION_CONFIG_REFERENCE = (
    "config/p7-t4-research-independent-evaluation-remediation.json"
)
EXPECTED_REMEDIATION_CANDIDATE_ID = (
    "445a2c33e7cf7a7b9dc8b69c3ebe01ab0d7cf2565463ffb3d30920d9509baf61"
)
EXPECTED_REMEDIATION_ADAPTER_IDENTITY = (
    "feb512ff5783e0f9d959e1522be9171c746419bae7e657919e881c94578a3b14"
)
EXPECTED_REMEDIATION_ADAPTER_MANIFEST_SHA256 = (
    "e5b8991941f36dc799a0fc1b53ac09bf082665a2b2cc09b0a6463aa95b8b0287"
)
EXPECTED_REMEDIATION_TRAINING_RUN_IDENTITY = (
    "e8c3b34b22297d3ae52426d43d021445948742b4f752e8769d8317d42b9c42fc"
)
EXPECTED_REMEDIATION_EVIDENCE_IDENTITY = (
    "6ac24aa9e1079ad2e58a9b778a841b51855eb91a027f1b1559845dcff70b4945"
)
EXPECTED_REMEDIATION_EVIDENCE_SHA256 = (
    "ef51c3c252937744eee2e73d6856c48b3219511ac41098091e01c8919ad1c837"
)
EXPECTED_REMEDIATION_METADATA_SHA256 = (
    "eb949bd2eccd3e0e866fbe23a8aa61f94915cebf086afc491400bbc1247d452a"
)
EXPECTED_REMEDIATION_ARCHIVE_SHA256 = (
    "1f642aab913e705702bc463aff7a85d5f799d2f6214b6bd926904674a10a860d"
)
EXPECTED_REMEDIATION_SOURCE_SHA256 = (
    "6654416e24e190d6614fab7881e28ddd83a63284e08f1eef56c06b68de235bdb"
)
EXPECTED_REMEDIATION_CONTENT_IDENTITY = (
    "243352c24fb4aa5c95fad616a8cbd2e4062652854ec69619d3c426fb23f871af"
)
EXPECTED_REMEDIATION_CONTRACT_IDENTITY = (
    "89e49c43fd6488a6d47473141ad9070bd0dd785e309bbdaf26246e41d277a145"
)
EXPECTED_REMEDIATION_PROVENANCE_IDENTITY = (
    "359b5836a34eb7fc1a91249df60583d687dad82104ab3dfe701b6057f01f4cc5"
)
EXPECTED_REMEDIATION_REQUEST_IDENTITY = (
    "64692cc3d48aa10676c39242a42efa212e692c5b05be18a0a66445c1a8b2ae09"
)
EXPECTED_REMEDIATION_CARD_IDENTITY = (
    "cc8fae04f912d965a7d23ca55022b9ebb55a93589fea7bc3e022f09e3d0a8382"
)
EXPECTED_REMEDIATION_APPROVAL_IDENTITY = (
    "5565b0339f9745d3e0b9cb44353bb97a131824fcdd7511130d11d6742b13dbd0"
)
EXPECTED_REMEDIATION_DATASET_IDENTITY = (
    "0409e9087efe7332e298d0c3812d11f2edac7cedf538a8db475776d9c190eb30"
)
EXPECTED_REMEDIATION_TRAINING_CONFIG_IDENTITY = (
    "404e55cd16f6e56c317c973c666f6f4a42716db202323a626b29b6305116f608"
)
EVALUATION_FREEZE_REFERENCES = (
    "config/p6-t5-benchmark.yaml",
    "config/p6-t5-benchmark-kaggle-linux-cp312.yaml",
    "config/p7-t3-research-report-eval-governance-request.json",
    "config/p7-t4-research-independent-evaluation.json",
    "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
    "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml",
    "evals/evaluation-suite.schema.json",
    "evals/human-eval-rubric.yaml",
    "evals/p6-t4-evaluation-freeze.binding.yaml",
    "evals/p6-t4-evaluation-suite.lock.json",
    "evals/p6-t4-evaluation-suites.yaml",
    "evals/p7-t3-research-gap-evaluation-suite.json",
    "evals/p7-t3-research-gap-evaluation-suite.lock.json",
    "evidence/p7-t1c-frozen-evaluation-governance-approval.json",
    "evidence/p7-t3-research-report-eval-governance-approval-v2.json",
    "requirements/p7-t2-real-training-t4-cp313-requirements.txt",
    "scripts/benchmark-p6-t5.py",
    "scripts/p7-t2-real-training.py",
    "scripts/research-gap-evidence-p7-t3.py",
    "scripts/research-independent-evaluation-p7-t4.py",
    "scripts/research-model-decision-p7-t3.py",
    "scripts/research-report-eval-governance-p7-t3.py",
    "scripts/training-pipeline-p7-t2.py",
    "scripts/validate-evaluation-suites.py",
)


class RemediationValidationError(ValueError):
    """Stable diagnostics for invalid or prematurely advanced remediation state."""

    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def artifact_identity(document: dict[str, Any]) -> str:
    return hashlib.sha256(
        canonical_bytes({key: value for key, value in document.items() if key != "artifactIdentity"})
    ).hexdigest()


def _identity_without(document: dict[str, Any], field: str) -> str:
    return hashlib.sha256(
        canonical_bytes({key: value for key, value in document.items() if key != field})
    ).hexdigest()


def source_inventory(root: Path) -> dict[str, Any]:
    files = []
    for reference in EVALUATION_FREEZE_REFERENCES:
        path = _resolve_reference(root, reference, f"evaluation freeze/{reference}")
        files.append(
            {
                "reference": reference,
                "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            }
        )
    return {
        "algorithm": "SHA256_CANONICAL_FILE_INVENTORY_V1",
        "files": files,
        "identity": hashlib.sha256(canonical_bytes(files)).hexdigest(),
    }


def _load_json(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise RemediationValidationError(f"{label}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise RemediationValidationError(f"{label}: object required")
    return value


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RemediationValidationError(f"module unavailable: {path}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


def _resolve_reference(root: Path, reference: object, label: str) -> Path:
    if not isinstance(reference, str) or not reference or "#" in reference:
        raise RemediationValidationError(f"{label}: repository-relative file reference required")
    candidate = Path(reference)
    if candidate.is_absolute():
        raise RemediationValidationError(f"{label}: absolute paths are forbidden")
    resolved_root = root.resolve()
    resolved = (resolved_root / candidate).resolve()
    try:
        resolved.relative_to(resolved_root)
    except ValueError as error:
        raise RemediationValidationError(f"{label}: reference escapes repository root") from error
    if not resolved.is_file():
        raise RemediationValidationError(f"{label}: referenced file does not exist")
    return resolved


def _exact_fields(
    value: object,
    fields: set[str],
    label: str,
    diagnostics: list[str],
) -> bool:
    if not isinstance(value, dict):
        diagnostics.append(f"{label}: object required")
        return False
    if set(value) != fields:
        diagnostics.append(f"{label}: exact fields {', '.join(sorted(fields))} required")
        return False
    return True


def _validated_training_inputs(root: Path) -> tuple[dict[str, Any], dict[str, Any], Any]:
    training = _load_module(
        "p7_t2_for_p7_t4_remediation",
        root / "scripts" / "p7-t2-real-training.py",
    )
    config = _load_json(
        root / CURRENT_TRAINING_CONFIG_REFERENCE,
        "failed training config",
    )
    manifest_reference = config.get("dataset", {}).get("manifestReference")
    manifest_path = _resolve_reference(
        root, manifest_reference, "failed training dataset manifest"
    )
    try:
        inputs = training.load_training_inputs(manifest_path, config)
    except (OSError, TypeError, ValueError) as error:
        raise RemediationValidationError(
            f"failed training dataset: governed input validation failed: {error}"
        ) from error
    manifest = inputs["manifest"]
    training_contract = _load_module(
        "p7_t2_contract_for_p7_t4_remediation",
        root / "scripts" / "training-pipeline-p7-t2.py",
    )
    try:
        training_contract.validate_training_config(config)
        governed_manifest = training_contract.validate_dataset_manifest(
            manifest_path,
            config["dataset"]["identity"],
            config["assistantKey"],
            {"train", "validation", "evaluation"},
        )
    except (OSError, TypeError, ValueError) as error:
        raise RemediationValidationError(
            f"failed training dataset: governance validation failed: {error}"
        ) from error
    if governed_manifest != manifest:
        raise RemediationValidationError(
            "failed training dataset: manifest validation result mismatch"
        )
    artifacts = manifest.get("artifacts")
    expected_names = {
        "evaluation.jsonl",
        "rejections.jsonl",
        "train.jsonl",
        "validation.jsonl",
    }
    if (
        not isinstance(artifacts, list)
        or {item.get("filename") for item in artifacts if isinstance(item, dict)}
        != expected_names
        or len(artifacts) != len(expected_names)
    ):
        raise RemediationValidationError(
            "failed training dataset: exact governed artifact inventory required"
        )
    for artifact in artifacts:
        if not isinstance(artifact, dict) or set(artifact) != {
            "filename",
            "recordCount",
            "sha256",
        }:
            raise RemediationValidationError(
                "failed training dataset: closed artifact record required"
            )
        path = manifest_path.parent / artifact["filename"]
        count = artifact["recordCount"]
        if (
            not path.is_file()
            or not isinstance(artifact["sha256"], str)
            or not SHA256_PATTERN.fullmatch(artifact["sha256"])
            or hashlib.sha256(path.read_bytes()).hexdigest() != artifact["sha256"]
            or not isinstance(count, int)
            or isinstance(count, bool)
            or count < 0
            or len(path.read_text(encoding="utf-8").splitlines()) != count
        ):
            raise RemediationValidationError(
                f"failed training dataset: {artifact.get('filename')} inventory mismatch"
            )
    return inputs, config, training


def _validate_failed_evaluation(
    root: Path,
    freeze: object,
) -> tuple[dict[str, Any], list[dict[str, Any]], Any]:
    expected_freeze_fields = {
        "preflightIdentity",
        "preflightReference",
        "sourceInventory",
        "suite",
    }
    if not isinstance(freeze, dict) or set(freeze) != expected_freeze_fields:
        raise RemediationValidationError(
            "remediation/evaluationFreeze: exact frozen evidence binding required"
        )
    if freeze.get("sourceInventory") != source_inventory(root):
        raise RemediationValidationError(
            "remediation/evaluationFreeze: evaluator source inventory drift"
        )
    evaluation = _load_module(
        "p7_t4_for_p7_t4_remediation",
        root / "scripts" / "research-independent-evaluation-p7-t4.py",
    )
    preflight = _load_json(
        _resolve_reference(root, freeze.get("preflightReference"), "failed preflight"),
        "failed P7-T4 preflight",
    )
    if (
        preflight.get("artifactIdentity") != evaluation.artifact_identity(preflight)
        or preflight.get("artifactIdentity") != freeze.get("preflightIdentity")
        or preflight.get("state") != "PREFLIGHT_PASS"
        or preflight.get("suite") != freeze.get("suite")
        or preflight.get("caseCount") != 18
    ):
        raise RemediationValidationError(
            "remediation/evaluationFreeze: preflight identity or suite mismatch"
        )
    suite = preflight.get("composedSuite")
    if (
        not isinstance(suite, dict)
        or suite.get("EVALUATION_ONLY") is not True
        or suite.get("TRAINING_PROHIBITED") is not True
        or evaluation._suite_identity(suite) != suite.get("suiteDigest")
        or evaluation.suite_binding(suite) != freeze.get("suite")
    ):
        raise RemediationValidationError(
            "remediation/evaluationFreeze: composed frozen suite invalid"
        )

    evaluation_config = _load_json(
        root / "config" / "p7-t4-research-independent-evaluation.json",
        "P7-T4 evaluation config",
    )
    adapter_manifest_path = root / evaluation_config.get("adapter", {}).get(
        "manifestReference", ""
    )
    adapter_manifest = _load_json(adapter_manifest_path, "failed adapter manifest")
    try:
        evaluation.validate_evaluation_config(evaluation_config, adapter_manifest)
    except (OSError, TypeError, ValueError) as error:
        raise RemediationValidationError(
            f"remediation/evaluationFreeze: evaluation policy invalid: {error}"
        ) from error

    sources = evaluation_config["evaluationSources"]
    base_approval = _load_json(
        root / sources["baseGovernanceApproval"], "base evaluation approval"
    )
    gap_suite = _load_json(root / sources["gapSuite"], "Research gap suite")
    report_approval = _load_json(
        root / sources["reportGovernanceApproval"], "report evaluation approval"
    )
    evidence = _load_json(
        root / evaluation_config["adapter"]["evidenceReference"],
        "real training evidence",
    )
    current_source_identities = {
        "adapterManifest": hashlib.sha256(adapter_manifest_path.read_bytes()).hexdigest(),
        "realTrainingEvidence": evidence.get("artifactIdentity"),
        "baseSuite": hashlib.sha256(
            (root / sources["baseSuite"]).read_bytes()
        ).hexdigest(),
        "baseGovernanceApproval": base_approval.get("artifactIdentity"),
        "gapSuite": gap_suite.get("suiteDigest"),
        "reportGovernanceApproval": report_approval.get("artifactIdentity"),
        "runtimeLock": hashlib.sha256(
            (root / evaluation_config["execution"]["requirementsReference"]).read_bytes()
        ).hexdigest(),
    }
    if preflight.get("sourceIdentities") != current_source_identities:
        raise RemediationValidationError(
            "remediation/evaluationFreeze: preflight source identity drift"
        )
    for label, value in (
        ("base evaluation approval", base_approval),
        ("report evaluation approval", report_approval),
        ("real training evidence", evidence),
    ):
        if value.get("artifactIdentity") != evaluation.artifact_identity(value):
            raise RemediationValidationError(
                f"remediation/evaluationFreeze: {label} identity mismatch"
            )

    prompt_config = evaluation._load_yaml(
        root / evaluation_config["execution"]["promptProfileReference"]
    )
    try:
        evaluation.P6_BENCHMARK.validate_profile_templates(prompt_config)
        research_profile = evaluation.P6_BENCHMARK.assistant_profile(
            prompt_config, "RESEARCH_ASSISTANT"
        )
    except (TypeError, ValueError) as error:
        raise RemediationValidationError(
            f"remediation/evaluationFreeze: prompt profile invalid: {error}"
        ) from error
    expected_prompt_digests = {}
    for case in suite["caseInventory"]:
        prompt_case = dict(case)
        prompt_case["assistantKey"] = "RESEARCH_ASSISTANT"
        messages = evaluation.P6_BENCHMARK.render_prompt(
            prompt_case, research_profile["systemInstruction"]
        )
        expected_prompt_digests[case["evalCaseId"]] = hashlib.sha256(
            canonical_bytes(messages)
        ).hexdigest()

    run_root = root / FAILED_EVALUATION_ROOT / "runs"
    runs: dict[str, list[dict[str, Any]]] = {}
    for variant in ("SHARED_BASE", "RESEARCH_ADAPTER"):
        runs[variant] = []
        for repetition in EXPECTED_REPETITIONS:
            run = _load_json(
                run_root / variant / f"{repetition}.json",
                f"{variant} {repetition} run",
            )
            prompt_digests = {
                item.get("evalCaseId"): item.get("promptDigest")
                for item in run.get("promptManifest", [])
                if isinstance(item, dict)
            }
            candidate = run.get("candidateRun")
            metadata = candidate.get("modelMetadata") if isinstance(candidate, dict) else None
            expected_candidate_id = (
                evaluation_config["adapter"]["candidateId"]
                if variant == "RESEARCH_ADAPTER"
                else None
            )
            expected_run_id = (
                f"{evaluation_config['adapter']['candidateId']}-{variant}-{repetition}"
            )
            if (
                prompt_digests != expected_prompt_digests
                or not isinstance(candidate, dict)
                or candidate.get("candidateRunId") != expected_run_id
                or run.get("automatic", {}).get("candidateRunId") != expected_run_id
                or metadata
                != {
                    "variant": variant,
                    "baseModel": evaluation_config["baseModel"],
                    "candidateId": expected_candidate_id,
                }
            ):
                raise RemediationValidationError(
                    f"remediation/evaluationFreeze: {variant} {repetition} binding mismatch"
                )
            runs[variant].append(run)

    comparison = _load_json(
        root / FAILED_EVALUATION_ROOT / "comparison.json",
        "failed comparison",
    )
    try:
        recomputed = evaluation.compare_model_runs(
            suite,
            runs["SHARED_BASE"],
            runs["RESEARCH_ADAPTER"],
        )
    except (TypeError, ValueError) as error:
        raise RemediationValidationError(
            f"remediation/evaluationFreeze: run evidence invalid: {error}"
        ) from error
    if recomputed != comparison:
        raise RemediationValidationError(
            "remediation/evaluationFreeze: comparison is not reproducible from runs"
        )
    return comparison, runs["RESEARCH_ADAPTER"], evaluation.P6_BENCHMARK


def diagnose_current_failure(root: Path, evaluation_freeze: object) -> dict[str, Any]:
    """Reproduce the schema mismatch and overfit evidence without model execution."""
    root = root.resolve()
    _, adapter_runs, benchmark = _validate_failed_evaluation(root, evaluation_freeze)
    inputs, training_config, _ = _validated_training_inputs(root)
    target_key_union: set[str] = set()
    record_count = 0
    compatible_count = 0
    for split, records in (
        ("train", inputs["trainingRecords"]),
        ("validation", inputs["validationRecords"]),
    ):
        for line_number, record in enumerate(records, start=1):
            if not isinstance(record, dict) or not isinstance(record.get("expectedOutput"), dict):
                raise RemediationValidationError(
                    f"{split} dataset:{line_number}: expectedOutput object required"
                )
            content_id = record.get("contentId")
            if not isinstance(content_id, str) or not SHA256_PATTERN.fullmatch(content_id):
                raise RemediationValidationError(
                    f"{split} dataset:{line_number}: canonical contentId required"
                )
            target = record["expectedOutput"]
            target_key_union.update(target)
            raw = canonical_bytes(target).decode("utf-8")
            parsed, parse_error = benchmark.parse_raw_response(f"TRAIN-{content_id}", raw)
            if parsed is not None and parse_error is None:
                compatible_count += 1
            record_count += 1

    parse_errors: dict[str, dict[str, int]] = {}
    for repetition, run in zip(EXPECTED_REPETITIONS, adapter_runs, strict=True):
        raw_outputs = run.get("rawOutputs")
        if not isinstance(raw_outputs, list) or len(raw_outputs) != 18:
            raise RemediationValidationError(
                f"adapter {repetition} run: exact 18 raw outputs required"
            )
        counts: Counter[str] = Counter()
        for item in raw_outputs:
            if not isinstance(item, dict):
                raise RemediationValidationError(
                    f"adapter {repetition} run: raw output object required"
                )
            _, parse_error = benchmark.parse_raw_response(
                item.get("evalCaseId"), item.get("rawText")
            )
            counts[parse_error or "PARSED"] += 1
        parse_errors[repetition] = dict(sorted(counts.items()))

    execution_evidence = _load_json(
        root / "evidence" / "p7-t2-real-training" / "real-training-evidence.json",
        "failed real-training evidence",
    )
    actual = execution_evidence.get("actualTraining")
    parameters = training_config.get("training")
    metrics = execution_evidence.get("metrics")
    if not all(isinstance(value, dict) for value in (actual, parameters, metrics)):
        raise RemediationValidationError(
            "failed training evidence: actualTraining/config training/metrics required"
        )
    schedule = {
        "epochs": actual.get("epochs"),
        "globalSteps": actual.get("globalSteps"),
        "gradientAccumulation": parameters.get("gradientAccumulation"),
        "learningRate": parameters.get("learningRate"),
        "maxSteps": parameters.get("maxSteps"),
        "trainLoss": metrics.get("trainLoss"),
        "trainRecords": actual.get("trainRecords"),
        "validationLoss": metrics.get("validationLoss"),
    }
    if any(
        not isinstance(value, (int, float))
        or isinstance(value, bool)
        or not math.isfinite(float(value))
        for value in schedule.values()
    ):
        raise RemediationValidationError("failed training evidence: finite schedule evidence required")
    return {
        "adapterParseErrorsPerRepetition": parse_errors,
        "supervisedTargets": {
            "p7T4CompatibleCount": compatible_count,
            "recordCount": record_count,
            "topLevelKeyUnion": sorted(target_key_union),
        },
        "trainingSchedule": schedule,
    }


def _validate_pending_replacement_source(
    root: Path,
    replacement: dict[str, Any],
    diagnostics: list[str],
) -> None:
    try:
        source_path = _resolve_reference(
            root, replacement.get("sourceReference"), "replacement source"
        )
        provenance = _load_json(
            _resolve_reference(
                root, replacement.get("provenanceReference"), "replacement provenance"
            ),
            "replacement provenance",
        )
        contract = _load_json(
            _resolve_reference(
                root, replacement.get("trainingContractReference"), "replacement contract"
            ),
            "replacement training contract",
        )
        request = _load_json(
            _resolve_reference(
                root, replacement.get("governanceRequestReference"), "replacement request"
            ),
            "replacement governance request",
        )
        card = _load_json(
            _resolve_reference(
                root, replacement.get("pendingCardReference"), "replacement pending card"
            ),
            "replacement pending card",
        )
        source = _load_json(source_path, "replacement source")
    except RemediationValidationError as error:
        diagnostics.extend(error.diagnostics)
        return

    if (
        replacement.get("sourceReference") != REMEDIATION_SOURCE_REFERENCE
        or replacement.get("provenanceReference") != REMEDIATION_PROVENANCE_REFERENCE
        or replacement.get("trainingContractReference") != REMEDIATION_CONTRACT_REFERENCE
        or replacement.get("governanceRequestReference") != REMEDIATION_REQUEST_REFERENCE
        or replacement.get("pendingCardReference") != REMEDIATION_CARD_REFERENCE
    ):
        diagnostics.append(
            "remediation/replacementDataset: exact versioned source and request references required"
        )
    if (
        hashlib.sha256(source_path.read_bytes()).hexdigest()
        != EXPECTED_REMEDIATION_SOURCE_SHA256
        or replacement.get("sourceSha256") != EXPECTED_REMEDIATION_SOURCE_SHA256
    ):
        diagnostics.append("remediation/replacementDataset: source SHA-256 mismatch")
    records = source.get("records")
    if not isinstance(records, list) or len(records) != 45:
        diagnostics.append("remediation/replacementDataset: exact 45-record source required")
        records = []
    else:
        expected_record_fields = {
            "schemaVersion",
            "recordId",
            "assistantKey",
            "domain",
            "recordType",
            "visibility",
            "useCaseId",
            "input",
            "payload",
            "legacySemanticReference",
            "trainingPrompt",
            "trainingTarget",
            "governance",
            "metadata",
        }
        allowed_use_cases = {
            "RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"
        }
        case_ids: list[object] = []
        for index, record in enumerate(records):
            if not isinstance(record, dict) or set(record) != expected_record_fields:
                diagnostics.append(
                    f"remediation/replacementDataset/records/{index}: closed record required"
                )
                continue
            prompt = record.get("trainingPrompt")
            target = record.get("trainingTarget")
            if (
                record.get("assistantKey") != "RESEARCH_ASSISTANT"
                or record.get("domain") != "RESEARCH"
                or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
                or record.get("useCaseId") not in allowed_use_cases
                or record.get("metadata", {}).get("evaluationDerived") is not False
                or not isinstance(prompt, dict)
                or not isinstance(target, dict)
                or set(target) != set(EXPECTED_ENVELOPE_KEYS)
                or prompt.get("evalCaseId") != target.get("evalCaseId")
            ):
                diagnostics.append(
                    f"remediation/replacementDataset/records/{index}: contract-aligned independent Research record required"
                )
            if isinstance(prompt, dict):
                case_ids.append(prompt.get("evalCaseId"))
        if len(case_ids) != len(set(case_ids)):
            diagnostics.append("remediation/replacementDataset: unique training case IDs required")

    content_identity = hashlib.sha256(canonical_bytes(records)).hexdigest()
    if (
        content_identity != EXPECTED_REMEDIATION_CONTENT_IDENTITY
        or replacement.get("contentIdentity") != EXPECTED_REMEDIATION_CONTENT_IDENTITY
    ):
        diagnostics.append("remediation/replacementDataset: content identity mismatch")
    if (
        contract.get("contractIdentity") != _identity_without(contract, "contractIdentity")
        or contract.get("contractIdentity") != EXPECTED_REMEDIATION_CONTRACT_IDENTITY
        or replacement.get("trainingContractIdentity")
        != EXPECTED_REMEDIATION_CONTRACT_IDENTITY
        or contract.get("state") != "PROPOSED_FOR_GOVERNANCE"
        or contract.get("scope", {}).get("includedUseCases")
        != ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"]
        or contract.get("scope", {}).get("excludedUseCases")
        != {"RESEARCH_UC_006": "TRAINING_PROHIBITED_BY_CURRENT_GOVERNANCE"}
        or contract.get("scope", {}).get("frozenEvaluationDerivedRecordsAllowed") is not False
        or contract.get("lineage", {}).get("evaluationTrainingSources") != []
        or set(contract.get("outputContract", {}).get("closedKeys", []))
        != set(EXPECTED_ENVELOPE_KEYS)
    ):
        diagnostics.append("remediation/replacementDataset: proposed training contract mismatch")
    if (
        provenance.get("provenanceIdentity")
        != _identity_without(provenance, "provenanceIdentity")
        or provenance.get("provenanceIdentity")
        != EXPECTED_REMEDIATION_PROVENANCE_IDENTITY
        or replacement.get("provenanceIdentity")
        != EXPECTED_REMEDIATION_PROVENANCE_IDENTITY
        or provenance.get("contentIdentity") != EXPECTED_REMEDIATION_CONTENT_IDENTITY
        or provenance.get("contractIdentity") != EXPECTED_REMEDIATION_CONTRACT_IDENTITY
        or provenance.get("syntheticData", {}).get("evaluationMaterialCopied") is not False
        or provenance.get("lineage", {}).get("evaluationTrainingSources") != []
        or provenance.get("governance", {}).get("trainingAuthorized") is not False
    ):
        diagnostics.append("remediation/replacementDataset: pending provenance mismatch")
    if (
        request.get("requestIdentity") != _identity_without(request, "requestIdentity")
        or request.get("requestIdentity") != EXPECTED_REMEDIATION_REQUEST_IDENTITY
        or replacement.get("governanceRequestIdentity")
        != EXPECTED_REMEDIATION_REQUEST_IDENTITY
        or request.get("status") != "PENDING_USER_APPROVAL"
        or any(request.get(field) is not None for field in ("approval", "approvedBy", "approvedAt"))
        or request.get("currentState", {}).get("trainingAuthorized") is not False
        or request.get("source", {}).get("sourceSha256")
        != EXPECTED_REMEDIATION_SOURCE_SHA256
        or request.get("source", {}).get("contentIdentity")
        != EXPECTED_REMEDIATION_CONTENT_IDENTITY
        or request.get("source", {}).get("contractIdentity")
        != EXPECTED_REMEDIATION_CONTRACT_IDENTITY
        or request.get("requestedScope", {}).get("frozenEvaluationTrainingUseAllowed")
        is not False
    ):
        diagnostics.append("remediation/replacementDataset: pending governance request mismatch")
    card_identity = hashlib.sha256(canonical_bytes(card)).hexdigest()
    if (
        card_identity != EXPECTED_REMEDIATION_CARD_IDENTITY
        or replacement.get("pendingCardIdentity") != EXPECTED_REMEDIATION_CARD_IDENTITY
        or card.get("dataset_id") != "p7-research-synthetic-training-dataset"
        or card.get("dataset_version") != "2.0.0"
        or card.get("approval_status") != "PENDING"
        or card.get("lifecycle_status") != "PENDING_APPROVAL"
        or card.get("source_permission_status") != "NOT_ASSESSED"
        or card.get("permitted_purposes") != ["DEVELOPMENT_TEST"]
        or any(card.get(field) for field in (
            "approval_references", "source_permission_references", "approved_purposes"
        ))
    ):
        diagnostics.append("remediation/replacementDataset: pending dataset card mismatch")


def _validate_approved_materialization(
    root: Path, replacement: dict[str, Any], diagnostics: list[str]
) -> None:
    try:
        approval = _load_json(
            root / REMEDIATION_APPROVAL_REFERENCE, "remediation training approval"
        )
        approved_card = _load_json(
            root / REMEDIATION_APPROVED_CARD_REFERENCE,
            "remediation approved dataset card",
        )
        manifest = _load_json(
            root / REMEDIATION_DATASET_MANIFEST_REFERENCE,
            "remediation materialized dataset manifest",
        )
        config = _load_json(
            root / REMEDIATION_TRAINING_CONFIG_REFERENCE,
            "remediation training config",
        )
        pipeline = _load_module(
            "p7_t2_remediation_pipeline_for_gate",
            root / REMEDIATION_TRAINING_PIPELINE_REFERENCE,
        )
        pipeline.validate_training_config(config)
        pipeline.validate_dataset_and_contract_gates(
            root / REMEDIATION_DATASET_MANIFEST_REFERENCE, config, root
        )
    except (OSError, ValueError, RemediationValidationError) as error:
        diagnostics.append(
            f"remediation/replacementDataset: approved materialization invalid: {error}"
        )
        return
    if (
        approval.get("artifactIdentity") != EXPECTED_REMEDIATION_APPROVAL_IDENTITY
        or artifact_identity(approval) != EXPECTED_REMEDIATION_APPROVAL_IDENTITY
        or approval.get("requestIdentity") != EXPECTED_REMEDIATION_REQUEST_IDENTITY
        or approval.get("status") != "APPROVED"
        or approval.get("revocation", {}).get("status") != "ACTIVE"
    ):
        diagnostics.append(
            "remediation/replacementDataset: exact active training approval required"
        )
    if (
        approved_card.get("approval_status") != "APPROVED"
        or approved_card.get("approval_references") != [REMEDIATION_APPROVAL_REFERENCE]
        or approved_card.get("integrity", {}).get("checksum")
        != EXPECTED_REMEDIATION_SOURCE_SHA256
    ):
        diagnostics.append(
            "remediation/replacementDataset: exact approved card transition required"
        )
    if (
        manifest.get("checksum") != EXPECTED_REMEDIATION_DATASET_IDENTITY
        or manifest.get("training_approval_identity")
        != EXPECTED_REMEDIATION_APPROVAL_IDENTITY
        or manifest.get("training_contract_identity")
        != EXPECTED_REMEDIATION_CONTRACT_IDENTITY
    ):
        diagnostics.append(
            "remediation/replacementDataset: exact materialized dataset identity required"
        )
    if (
        pipeline.training_config_identity(config)
        != EXPECTED_REMEDIATION_TRAINING_CONFIG_IDENTITY
    ):
        diagnostics.append(
            "remediation/replacementTraining: exact guarded training config required"
        )
    if (
        replacement.get("trainingApprovalIdentity")
        != EXPECTED_REMEDIATION_APPROVAL_IDENTITY
        or replacement.get("trainingApprovalReference")
        != REMEDIATION_APPROVAL_REFERENCE
        or replacement.get("manifestIdentity")
        != EXPECTED_REMEDIATION_DATASET_IDENTITY
        or replacement.get("manifestReference")
        != REMEDIATION_DATASET_MANIFEST_REFERENCE
    ):
        diagnostics.append(
            "remediation/replacementDataset: approval and manifest binding mismatch"
        )


def _validate_completed_replacement_training(
    root: Path,
    failed_candidate: object,
    diagnostics: list[str],
) -> None:
    try:
        config = _load_json(
            _resolve_reference(
                root,
                REMEDIATION_TRAINING_CONFIG_REFERENCE,
                "remediation replacement training/config",
            ),
            "remediation replacement training config",
        )
        manifest_path = _resolve_reference(
            root,
            REMEDIATION_ADAPTER_MANIFEST_REFERENCE,
            "remediation replacement training/adapter manifest",
        )
        evidence_path = _resolve_reference(
            root,
            REMEDIATION_REAL_EVIDENCE_REFERENCE,
            "remediation replacement training/evidence",
        )
        metadata_path = _resolve_reference(
            root,
            REMEDIATION_TRAINING_METADATA_REFERENCE,
            "remediation replacement training/metadata",
        )
        archive_sha256_path = _resolve_reference(
            root,
            REMEDIATION_ARCHIVE_SHA256_REFERENCE,
            "remediation replacement training/archive digest",
        )
        evaluation_config = _load_json(
            _resolve_reference(
                root,
                REMEDIATION_EVALUATION_CONFIG_REFERENCE,
                "remediation reevaluation/config",
            ),
            "remediation reevaluation config",
        )
        manifest = _load_json(manifest_path, "remediation adapter manifest")
        evidence = _load_json(evidence_path, "remediation real-training evidence")
        metadata = _load_json(metadata_path, "remediation training metadata")
        training = _load_module(
            "p7_t2_real_training_remediation_for_completion_gate",
            root / REMEDIATION_REAL_TRAINING_REFERENCE,
        )
        evaluator = _load_module(
            "p7_t4_for_remediation_completion_gate",
            root / "scripts/research-independent-evaluation-p7-t4.py",
        )
        training.validate_real_metadata_contract(metadata, config)
        evaluator.validate_evaluation_config(evaluation_config, manifest)
    except (OSError, ValueError, RemediationValidationError) as error:
        diagnostics.append(
            f"remediation/replacementTraining: completed evidence invalid: {error}"
        )
        return

    manifest_artifacts = manifest.get("artifacts")
    adapter_identity = (
        hashlib.sha256(canonical_bytes(manifest_artifacts)).hexdigest()
        if isinstance(manifest_artifacts, list)
        else None
    )
    candidate_id = (
        hashlib.sha256(
            canonical_bytes(
                {
                    "trainingRunIdentity": manifest.get("trainingRunIdentity"),
                    "adapterIdentity": adapter_identity,
                }
            )
        ).hexdigest()
        if adapter_identity is not None
        else None
    )
    evidence_export = evidence.get("exportedArtifacts")
    evidence_adapter_artifacts = (
        [
            item
            for item in evidence_export
            if isinstance(item, dict) and item.get("filename") != "adapter-manifest.json"
        ]
        if isinstance(evidence_export, list)
        else None
    )
    evidence_reference = metadata.get("realTrainingEvidence")
    if (
        hashlib.sha256(manifest_path.read_bytes()).hexdigest()
        != EXPECTED_REMEDIATION_ADAPTER_MANIFEST_SHA256
        or manifest.get("schemaVersion") != "1.0.0"
        or manifest.get("backend") != "REAL_QLORA"
        or manifest.get("realTraining") is not True
        or manifest.get("adapterDisposition") != "CANDIDATE_ONLY"
        or manifest.get("adapterIdentity") != EXPECTED_REMEDIATION_ADAPTER_IDENTITY
        or manifest.get("adapterIdentity") != adapter_identity
        or manifest.get("candidateId") != EXPECTED_REMEDIATION_CANDIDATE_ID
        or manifest.get("candidateId") != candidate_id
        or manifest.get("trainingRunIdentity")
        != EXPECTED_REMEDIATION_TRAINING_RUN_IDENTITY
        or manifest.get("datasetIdentity") != EXPECTED_REMEDIATION_DATASET_IDENTITY
        or manifest.get("trainingConfigIdentity")
        != EXPECTED_REMEDIATION_TRAINING_CONFIG_IDENTITY
        or manifest.get("artifacts") != evidence_adapter_artifacts
    ):
        diagnostics.append(
            "remediation/replacementTraining: adapter identity or inventory mismatch"
        )
    if (
        evidence.get("artifactType")
        != "P7-T2-REMEDIATION-REAL-TRAINING-EXECUTION-EVIDENCE"
        or evidence.get("schemaVersion") != "2.0.0"
        or evidence.get("artifactIdentity") != EXPECTED_REMEDIATION_EVIDENCE_IDENTITY
        or evidence.get("artifactIdentity") != artifact_identity(evidence)
        or hashlib.sha256(evidence_path.read_bytes()).hexdigest()
        != EXPECTED_REMEDIATION_EVIDENCE_SHA256
        or evidence.get("candidateId") != EXPECTED_REMEDIATION_CANDIDATE_ID
        or evidence.get("trainingRunIdentity")
        != EXPECTED_REMEDIATION_TRAINING_RUN_IDENTITY
        or evidence.get("datasetIdentity") != EXPECTED_REMEDIATION_DATASET_IDENTITY
        or evidence.get("trainingConfigIdentity")
        != EXPECTED_REMEDIATION_TRAINING_CONFIG_IDENTITY
    ):
        diagnostics.append(
            "remediation/replacementTraining: real-training evidence identity mismatch"
        )
    if (
        hashlib.sha256(metadata_path.read_bytes()).hexdigest()
        != EXPECTED_REMEDIATION_METADATA_SHA256
        or metadata.get("candidateId") != EXPECTED_REMEDIATION_CANDIDATE_ID
        or metadata.get("trainingRunIdentity")
        != EXPECTED_REMEDIATION_TRAINING_RUN_IDENTITY
        or metadata.get("exportedArtifacts") != evidence_export
        or metadata.get("actualTraining") != evidence.get("actualTraining")
        or metadata.get("metrics") != evidence.get("metrics")
        or not isinstance(evidence_reference, dict)
        or evidence_reference.get("artifactIdentity")
        != EXPECTED_REMEDIATION_EVIDENCE_IDENTITY
        or evidence_reference.get("sha256") != EXPECTED_REMEDIATION_EVIDENCE_SHA256
    ):
        diagnostics.append(
            "remediation/replacementTraining: training metadata binding mismatch"
        )
    expected_archive_line = (
        f"{EXPECTED_REMEDIATION_ARCHIVE_SHA256}  "
        "p7-t2-research-remediation-output.zip\n"
    )
    if archive_sha256_path.read_text(encoding="utf-8") != expected_archive_line:
        diagnostics.append(
            "remediation/replacementTraining: output archive digest mismatch"
        )
    frozen_config = _load_json(
        root / "config/p7-t4-research-independent-evaluation.json",
        "frozen P7-T4 evaluation config",
    )
    expected_evaluation_config = copy.deepcopy(frozen_config)
    expected_evaluation_config["adapter"]["candidateId"] = (
        EXPECTED_REMEDIATION_CANDIDATE_ID
    )
    expected_evaluation_config["adapter"]["adapterIdentity"] = (
        EXPECTED_REMEDIATION_ADAPTER_IDENTITY
    )
    if evaluation_config != expected_evaluation_config:
        diagnostics.append(
            "remediation/reevaluation: frozen contract changed beyond candidate binding"
        )
    if (
        not isinstance(failed_candidate, dict)
        or failed_candidate.get("candidateId") == EXPECTED_REMEDIATION_CANDIDATE_ID
    ):
        diagnostics.append(
            "remediation/replacementTraining: distinct candidate identity required"
        )


def validate_document(root: Path, document: dict[str, Any]) -> dict[str, Any]:
    root = root.resolve()
    diagnostics: list[str] = []
    root_fields = {
        "artifactIdentity",
        "artifactType",
        "evaluationFreeze",
        "failedCandidate",
        "reevaluation",
        "remediationPolicy",
        "replacementDataset",
        "replacementTraining",
        "rootCause",
        "schemaVersion",
        "state",
    }
    if not _exact_fields(document, root_fields, "remediation", diagnostics):
        raise RemediationValidationError(diagnostics)
    if document.get("artifactType") != "P7-T4-RESEARCH-REMEDIATION":
        diagnostics.append("remediation/artifactType: unsupported contract")
    if document.get("schemaVersion") != "1.0.0":
        diagnostics.append("remediation/schemaVersion: unsupported version")
    if document.get("state") != "REMEDIATION_TRAINING_COMPLETE":
        diagnostics.append(
            "remediation/state: REMEDIATION_TRAINING_COMPLETE required"
        )
    if document.get("artifactIdentity") != artifact_identity(document):
        diagnostics.append("remediation/artifactIdentity: identity mismatch")

    failed_fields = {
        "automaticDecision",
        "candidateId",
        "comparisonIdentity",
        "comparisonReference",
        "disposition",
        "summaryReference",
        "trainingEvidenceReference",
        "trainingMetadataReference",
        "trainingMetadataSha256",
        "trainingRunIdentity",
    }
    failed = document.get("failedCandidate")
    if _exact_fields(failed, failed_fields, "remediation/failedCandidate", diagnostics):
        assert isinstance(failed, dict)
        if not isinstance(failed.get("candidateId"), str) or not SHA256_PATTERN.fullmatch(
            failed["candidateId"]
        ):
            diagnostics.append("remediation/failedCandidate/candidateId: SHA-256 required")
        if not isinstance(failed.get("trainingRunIdentity"), str) or not SHA256_PATTERN.fullmatch(
            failed["trainingRunIdentity"]
        ):
            diagnostics.append(
                "remediation/failedCandidate/trainingRunIdentity: SHA-256 required"
            )
        if not isinstance(failed.get("comparisonIdentity"), str) or not SHA256_PATTERN.fullmatch(
            failed["comparisonIdentity"]
        ):
            diagnostics.append("remediation/failedCandidate/comparisonIdentity: SHA-256 required")
        if failed.get("automaticDecision") != "AUTOMATIC_FAIL":
            diagnostics.append("remediation/failedCandidate: automatic failure required")
        if failed.get("disposition") != "CANDIDATE_NOT_PROMOTABLE_AUTOMATIC_FAIL":
            diagnostics.append("remediation/failedCandidate: non-promotable disposition required")

        try:
            summary = _load_json(
                _resolve_reference(root, failed["summaryReference"], "failed summary reference"),
                "failed summary",
            )
            comparison = _load_json(
                _resolve_reference(
                    root, failed["comparisonReference"], "failed comparison reference"
                ),
                "failed comparison",
            )
            training_evidence_path = _resolve_reference(
                root, failed["trainingEvidenceReference"], "failed training evidence reference"
            )
            training_evidence = _load_json(training_evidence_path, "failed training evidence")
            training_metadata_path = _resolve_reference(
                root,
                failed["trainingMetadataReference"],
                "failed training metadata reference",
            )
            training_metadata = _load_json(
                training_metadata_path,
                "failed training metadata",
            )
            evaluation_config = _load_json(
                root / "config" / "p7-t4-research-independent-evaluation.json",
                "P7-T4 evaluation config",
            )
            training_config = _load_json(
                root / CURRENT_TRAINING_CONFIG_REFERENCE,
                "failed training config",
            )
            training_module = _load_module(
                "p7_t2_provenance_for_p7_t4_remediation",
                root / "scripts" / "p7-t2-real-training.py",
            )
        except RemediationValidationError as error:
            diagnostics.extend(error.diagnostics)
        else:
            if (
                summary.get("artifactType") != "P7-T4-AUTOMATIC-FAIL-HANDOFF"
                or summary.get("automaticDecision") != "AUTOMATIC_FAIL"
                or summary.get("promotionAllowed") is not False
                or summary.get("disposition") != failed.get("disposition")
                or summary.get("comparisonIdentity") != failed.get("comparisonIdentity")
            ):
                diagnostics.append("remediation/failedCandidate: summary evidence mismatch")
            if (
                comparison.get("artifactIdentity") != artifact_identity(comparison)
                or comparison.get("artifactIdentity") != failed.get("comparisonIdentity")
                or comparison.get("automaticDecision") != "AUTOMATIC_FAIL"
                or comparison.get("promotionAllowed") is not False
                or len(comparison.get("adapterFailedCaseIds", [])) != 18
            ):
                diagnostics.append("remediation/failedCandidate: comparison evidence mismatch")
            if (
                training_evidence.get("artifactIdentity") != artifact_identity(training_evidence)
                or training_evidence.get("candidateId") != failed.get("candidateId")
                or training_evidence.get("trainingRunIdentity")
                != failed.get("trainingRunIdentity")
                or training_evidence.get("backend") != "REAL_QLORA"
                or training_evidence.get("realTraining") is not True
            ):
                diagnostics.append("remediation/failedCandidate: training evidence mismatch")
            evidence_reference = training_metadata.get("realTrainingEvidence")
            if (
                training_metadata.get("candidateId") != failed.get("candidateId")
                or training_metadata.get("trainingRunIdentity")
                != failed.get("trainingRunIdentity")
                or training_metadata.get("datasetIdentity")
                != training_config.get("dataset", {}).get("identity")
                or training_metadata.get("trainingConfigIdentity")
                != training_module.training_config_identity(training_config)
                or training_metadata.get("trainingRunIdentity")
                != training_module.training_run_identity(training_config)
                or failed.get("trainingMetadataSha256")
                != hashlib.sha256(training_metadata_path.read_bytes()).hexdigest()
                or training_metadata.get("actualTraining")
                != training_evidence.get("actualTraining")
                or training_metadata.get("metrics") != training_evidence.get("metrics")
                or training_metadata.get("trainingParameters")
                != training_config.get("training")
                or not isinstance(evidence_reference, dict)
                or evidence_reference.get("sha256")
                != hashlib.sha256(training_evidence_path.read_bytes()).hexdigest()
            ):
                diagnostics.append("remediation/failedCandidate: training metadata mismatch")
            if evaluation_config.get("adapter", {}).get("candidateId") != failed.get(
                "candidateId"
            ):
                diagnostics.append("remediation/failedCandidate: P7-T4 candidate binding mismatch")

    try:
        validated_comparison, _, _ = _validate_failed_evaluation(
            root, document.get("evaluationFreeze")
        )
    except RemediationValidationError as error:
        diagnostics.extend(error.diagnostics)
    else:
        if not isinstance(failed, dict) or (
            validated_comparison.get("artifactIdentity")
            != failed.get("comparisonIdentity")
        ):
            diagnostics.append(
                "remediation/evaluationFreeze: failed comparison binding mismatch"
            )

    expected_policy = {
        "automaticEvidenceMayBeRewritten": False,
        "frozenEvaluationTrainingUseAllowed": False,
        "frozenEvaluationUnchanged": True,
        "newCandidateIdentityRequired": True,
        "newTrainingApprovalRequired": True,
        "newTrainingDatasetRequired": True,
        "outputPostProcessingToPassAllowed": False,
    }
    if document.get("remediationPolicy") != expected_policy:
        diagnostics.append(
            "remediation/remediationPolicy: frozen evaluation and fresh approval policy required"
        )

    replacement = document.get("replacementDataset")
    replacement_fields = {
        "contentIdentity",
        "datasetId",
        "governanceRequestIdentity",
        "governanceRequestReference",
        "manifestIdentity",
        "manifestReference",
        "pendingCardIdentity",
        "pendingCardReference",
        "proposedVersion",
        "provenanceIdentity",
        "provenanceReference",
        "requiredContractDesign",
        "sourceReference",
        "sourceSha256",
        "state",
        "trainingContractIdentity",
        "trainingContractReference",
        "trainingApprovalIdentity",
        "trainingApprovalReference",
    }
    if _exact_fields(
        replacement, replacement_fields, "remediation/replacementDataset", diagnostics
    ):
        assert isinstance(replacement, dict)
        serving_profile_path = root / SERVING_PROFILE_REFERENCE
        serving_schema_path = root / SERVING_SCHEMA_REFERENCE
        try:
            serving_profiles = _load_json(serving_profile_path, "serving assistant profiles")
            serving_schemas = _load_json(serving_schema_path, "serving output schemas")
        except RemediationValidationError as error:
            diagnostics.extend(error.diagnostics)
            serving_profile_valid = False
        else:
            serving_profile_valid = (
                serving_profiles.get("profiles", {})
                .get("RESEARCH_ASSISTANT", {})
                .get("schemaBundle")
                == "research-assistant-output-v1"
                and any(
                    isinstance(item, dict)
                    and item.get("schemaId") == "research-assistant-output-v1"
                    and item.get("assistantKey") == "RESEARCH_ASSISTANT"
                    and item.get("outputType") == "STRUCTURED_DRAFT"
                    for item in serving_schemas.get("schemas", [])
                )
            )
            if not serving_profile_valid:
                diagnostics.append(
                    "remediation/replacementDataset: active Research serving contract invalid"
                )
        expected_contract_design = {
            "benchmarkCompatibility": {
                "closedKeys": list(EXPECTED_ENVELOPE_KEYS),
                "contractSource": "APPROVED_GOVERNED_TRAINING_SPECIFICATION",
                "scope": "P7_T4_V1_CANDIDATE_EVALUATION_ONLY",
            },
            "design": "PROMPT_SCOPED_DUAL_CONTRACT",
            "fixedLegacyTopLevelSchemaAllowed": False,
            "frozenEvaluationDerivedRecordsAllowed": False,
            "preparedRuntimeContract": {
                "activationState": "PREPARED_NOT_ACTIVE",
                "assistantProfileReference": SERVING_PROFILE_REFERENCE,
                "assistantProfileSha256": hashlib.sha256(
                    serving_profile_path.read_bytes()
                ).hexdigest()
                if serving_profile_path.is_file()
                else None,
                "schemaBundle": "research-assistant-output-v1",
                "schemaReference": SERVING_SCHEMA_REFERENCE,
                "schemaSha256": hashlib.sha256(serving_schema_path.read_bytes()).hexdigest()
                if serving_schema_path.is_file()
                else None,
            },
        }
        if replacement.get("requiredContractDesign") != expected_contract_design:
            diagnostics.append(
                "remediation/replacementDataset/requiredContractDesign: prompt-scoped serving-safe design required"
            )
        approved = {
            "contentIdentity": EXPECTED_REMEDIATION_CONTENT_IDENTITY,
            "datasetId": "p7-research-synthetic-training-dataset",
            "governanceRequestIdentity": EXPECTED_REMEDIATION_REQUEST_IDENTITY,
            "governanceRequestReference": REMEDIATION_REQUEST_REFERENCE,
            "manifestIdentity": EXPECTED_REMEDIATION_DATASET_IDENTITY,
            "manifestReference": REMEDIATION_DATASET_MANIFEST_REFERENCE,
            "pendingCardIdentity": EXPECTED_REMEDIATION_CARD_IDENTITY,
            "pendingCardReference": REMEDIATION_CARD_REFERENCE,
            "proposedVersion": "2.0.0",
            "provenanceIdentity": EXPECTED_REMEDIATION_PROVENANCE_IDENTITY,
            "provenanceReference": REMEDIATION_PROVENANCE_REFERENCE,
            "requiredContractDesign": expected_contract_design,
            "sourceReference": REMEDIATION_SOURCE_REFERENCE,
            "sourceSha256": EXPECTED_REMEDIATION_SOURCE_SHA256,
            "state": "APPROVED_AND_MATERIALIZED",
            "trainingContractIdentity": EXPECTED_REMEDIATION_CONTRACT_IDENTITY,
            "trainingContractReference": REMEDIATION_CONTRACT_REFERENCE,
            "trainingApprovalIdentity": EXPECTED_REMEDIATION_APPROVAL_IDENTITY,
            "trainingApprovalReference": REMEDIATION_APPROVAL_REFERENCE,
        }
        if replacement != approved:
            diagnostics.append(
                "remediation/replacementDataset: exact approved materialization binding required"
            )
        _validate_pending_replacement_source(root, replacement, diagnostics)
        _validate_approved_materialization(root, replacement, diagnostics)

    expected_training = {
        "adapterIdentity": EXPECTED_REMEDIATION_ADAPTER_IDENTITY,
        "adapterManifestReference": REMEDIATION_ADAPTER_MANIFEST_REFERENCE,
        "adapterManifestSha256": EXPECTED_REMEDIATION_ADAPTER_MANIFEST_SHA256,
        "archiveSha256": EXPECTED_REMEDIATION_ARCHIVE_SHA256,
        "archiveSha256Reference": REMEDIATION_ARCHIVE_SHA256_REFERENCE,
        "bestCheckpoint": "checkpoint-00000032",
        "bestCheckpointSelectionRequired": True,
        "candidateId": EXPECTED_REMEDIATION_CANDIDATE_ID,
        "datasetIdentity": EXPECTED_REMEDIATION_DATASET_IDENTITY,
        "earlyStoppingRequired": True,
        "evidenceArtifactIdentity": EXPECTED_REMEDIATION_EVIDENCE_IDENTITY,
        "evidenceReference": REMEDIATION_REAL_EVIDENCE_REFERENCE,
        "evidenceSha256": EXPECTED_REMEDIATION_EVIDENCE_SHA256,
        "finiteEpochScheduleRequired": True,
        "fixedThousandStepScheduleAllowed": False,
        "independentContractHoldoutRequired": True,
        "metadataReference": REMEDIATION_TRAINING_METADATA_REFERENCE,
        "metadataSha256": EXPECTED_REMEDIATION_METADATA_SHA256,
        "periodicValidationRequired": True,
        "preparedRuntimeContractRegressionGateRequired": True,
        "sourceTask": "P7-T2",
        "state": "REAL_TRAINING_COMPLETE",
        "trainingApprovalIdentity": EXPECTED_REMEDIATION_APPROVAL_IDENTITY,
        "trainingConfigIdentity": EXPECTED_REMEDIATION_TRAINING_CONFIG_IDENTITY,
        "trainingConfigReference": REMEDIATION_TRAINING_CONFIG_REFERENCE,
        "trainingRunIdentity": EXPECTED_REMEDIATION_TRAINING_RUN_IDENTITY,
    }
    if document.get("replacementTraining") != expected_training:
        diagnostics.append(
            "remediation/replacementTraining: exact configured remediation training required"
        )
    expected_reevaluation = {
        "allAdapterCasesMustPass": True,
        "automaticThresholdRelaxationAllowed": False,
        "candidateId": EXPECTED_REMEDIATION_CANDIDATE_ID,
        "evaluationConfigReference": REMEDIATION_EVALUATION_CONFIG_REFERENCE,
        "humanEvaluationRequired": True,
        "independentReviewerRequired": True,
        "repetitions": list(EXPECTED_REPETITIONS),
        "state": "READY_FOR_EXTERNAL_REEVALUATION",
        "task": "P7-T4",
    }
    if document.get("reevaluation") != expected_reevaluation:
        diagnostics.append("remediation/reevaluation: unchanged P7-T4 gate required")
    _validate_completed_replacement_training(root, failed, diagnostics)

    try:
        expected_root_cause = {
            "codes": [
                "TRAINING_TARGET_CONTRACT_MISMATCH",
                "TRAINING_SCHEDULE_OVERFIT_RISK",
            ],
            **diagnose_current_failure(root, document.get("evaluationFreeze")),
        }
    except RemediationValidationError as error:
        diagnostics.extend(error.diagnostics)
    else:
        if document.get("rootCause") != expected_root_cause:
            diagnostics.append("remediation/rootCause: current evidence binding mismatch")

    if diagnostics:
        raise RemediationValidationError(diagnostics)
    assert isinstance(failed, dict)
    return {
        "artifactIdentity": document["artifactIdentity"],
        "comparisonIdentity": failed["comparisonIdentity"],
        "failedCandidateId": failed["candidateId"],
        "governanceRequestIdentity": EXPECTED_REMEDIATION_REQUEST_IDENTITY,
        "nextAction": "COMMIT_AND_BUILD_P7_T4_REEVALUATION_BUNDLE",
        "promotionAllowed": False,
        "state": "REMEDIATION_TRAINING_COMPLETE",
        "trainingAllowed": False,
    }


def validate_remediation(root: Path = ROOT, config_path: Path = DEFAULT_CONFIG) -> dict[str, Any]:
    return validate_document(root, _load_json(config_path.resolve(), "remediation config"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    args = parser.parse_args()
    try:
        result = validate_remediation(args.root, args.config)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except RemediationValidationError as error:
        print(
            json.dumps(
                {"diagnostics": error.diagnostics, "state": "ERROR"},
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
