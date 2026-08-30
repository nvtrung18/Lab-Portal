#!/usr/bin/env python3
"""Build the fail-closed P7-T5 Research promotion request and registry proposal."""
from __future__ import annotations

import argparse
from copy import deepcopy
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CONFIG_ROOT = ROOT / "config" / "p7-t5-research-promotion"
P7_T4_ROOT = (
    ROOT
    / "evidence"
    / "p7-t4-research-independent-evaluation"
    / "final-pass-remediation-v10"
)
P7_T4_SUMMARY_PATH = P7_T4_ROOT / "summary.json"
P7_T4_DECISION_PATH = P7_T4_ROOT / "decision.json"
P7_T4_COMPARISON_PATH = P7_T4_ROOT / "comparison.json"
ADAPTER_MANIFEST_PATH = (
    ROOT
    / "evidence"
    / "p7-t2-real-training"
    / "remediation-v10"
    / "adapter-manifest.json"
)
EVALUATION_CONFIG_PATH = ROOT / "config" / "p7-t4-research-independent-evaluation.json"
TRAINING_CONFIG_PATH = ROOT / "config" / "p7-t2-training-pipeline-t4-remediation-v10.json"

OUTPUT_REFERENCES = {
    "model-manifest.pending.json": (
        "config/p7-t5-research-promotion/model-manifest.pending.json"
    ),
    "model-registry-rules.json": (
        "config/p7-t5-research-promotion/model-registry-rules.json"
    ),
    "promotion-request.json": (
        "config/p7-t5-research-promotion/promotion-request.json"
    ),
    "rollback-manifest.pending.json": (
        "config/p7-t5-research-promotion/rollback-manifest.pending.json"
    ),
}

BASE_MODEL_LICENSE = {
    "identifier": "Apache-2.0",
    "sha256": "832dd9e00a68dd83b3c3fb9f5588dad7dcf337a0db50f7d9483f310cd292e92e",
    "sizeBytes": 11343,
    "status": "VERIFIED",
    "source": (
        "https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507/blob/"
        "cdbee75f17c01a7cc42f958dc650907174af0554/LICENSE"
    ),
}


class PromotionRequestError(ValueError):
    pass


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def artifact_identity(value: dict[str, Any]) -> str:
    document = deepcopy(value)
    document.pop("artifactIdentity", None)
    return hashlib.sha256(canonical_bytes(document)).hexdigest()


def request_identity(value: dict[str, Any]) -> str:
    document = deepcopy(value)
    document.pop("requestIdentity", None)
    return hashlib.sha256(canonical_bytes(document)).hexdigest()


def _load(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise PromotionRequestError(f"required source unreadable: {path.name}") from error
    if not isinstance(value, dict):
        raise PromotionRequestError(f"required source invalid: {path.name}")
    return value


def _validate_sources(
    summary: dict[str, Any],
    decision: dict[str, Any],
    adapter: dict[str, Any],
) -> None:
    evaluation = summary.get("independentReview", {})
    if (
        summary.get("artifactType") != "P7-T4-FINAL-PASS-HANDOFF"
        or summary.get("automaticDecision") != "AUTOMATIC_PASS"
        or summary.get("evaluationDecision") != "PASS"
        or summary.get("promotionAllowed") is not True
        or summary.get("adapterFailedCaseIds") != []
        or summary.get("unresolvedCaseIds") != []
        or evaluation.get("decision") != "PASS"
        or evaluation.get("resourceUseAccepted") is not True
    ):
        raise PromotionRequestError("completed promotion-eligible P7-T4 evidence required")
    if (
        decision.get("state") != "COMPLETE"
        or decision.get("evaluationDecision") != "PASS"
        or decision.get("promotionAllowed") is not True
        or decision.get("unresolvedCaseIds") != []
        or decision.get("humanRegressionCaseIds") != []
        or decision.get("humanFailedCaseIds") != []
        or decision.get("artifactIdentity") != summary.get("decisionIdentity")
        or decision.get("comparisonIdentity") != summary.get("comparisonIdentity")
    ):
        raise PromotionRequestError("P7-T4 decision binding invalid")
    if adapter.get("candidateId") != summary.get("candidateId"):
        raise PromotionRequestError("candidate identity mismatch")
    if (
        adapter.get("adapterDisposition") != "CANDIDATE_ONLY"
        or adapter.get("assistantKey") != "RESEARCH_ASSISTANT"
        or adapter.get("backend") != "REAL_QLORA"
        or adapter.get("realTraining") is not True
        or adapter.get("pipelineVersion") != "10.0.0"
    ):
        raise PromotionRequestError("candidate adapter manifest invalid")
    artifacts = adapter.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != 9:
        raise PromotionRequestError("candidate artifact inventory invalid")
    names = [entry.get("filename") for entry in artifacts if isinstance(entry, dict)]
    if len(names) != len(set(names)) or any(not name for name in names):
        raise PromotionRequestError("candidate artifact inventory invalid")


def _registry_rules() -> dict[str, Any]:
    rules: dict[str, Any] = {
        "allowedLifecycleStates": ["CANDIDATE", "APPROVED", "REJECTED", "ROLLED_BACK"],
        "artifactType": "P7-MODEL-REGISTRY-RULES",
        "requirements": {
            "approvalEvidence": True,
            "assistantIsolation": True,
            "checksumValidation": True,
            "independentEvaluation": True,
            "immutableArtifacts": True,
            "rollbackRequired": True,
            "servingRuntimeCompatibility": True,
        },
        "schemaVersion": "1.0.0",
        "status": "PROPOSED_PENDING_P7_T5_APPROVAL",
        "supportedAssistantKeys": [
            "RESEARCH_ASSISTANT",
            "LAB_ASSISTANT",
            "ADMIN_ASSISTANT",
        ],
        "transitionRules": {
            "APPROVED": ["ROLLED_BACK"],
            "CANDIDATE": ["APPROVED", "REJECTED"],
            "REJECTED": [],
            "ROLLED_BACK": [],
        },
    }
    rules["artifactIdentity"] = artifact_identity(rules)
    return rules


def _rollback_manifest(summary: dict[str, Any]) -> dict[str, Any]:
    rollback: dict[str, Any] = {
        "action": "DISABLE_ASSISTANT",
        "artifactType": "P7-T5-RESEARCH-ROLLBACK-MANIFEST",
        "assistantKey": "RESEARCH_ASSISTANT",
        "candidateId": summary["candidateId"],
        "reason": "No previously approved Research adapter exists; rollback fails closed.",
        "restoredAdapterIdentity": None,
        "restoredAdapterStatus": "BLOCKED",
        "schemaVersion": "1.0.0",
        "servingAllowed": False,
        "status": "PENDING_APPROVAL",
        "version": "1.0.0",
    }
    rollback["artifactIdentity"] = artifact_identity(rollback)
    return rollback


def _model_manifest(
    summary: dict[str, Any],
    adapter: dict[str, Any],
    comparison: dict[str, Any],
    evaluation_config: dict[str, Any],
    training_config: dict[str, Any],
    rollback: dict[str, Any],
) -> dict[str, Any]:
    artifact_files = [
        {
            "path": f"research-assistant/1.0.0/{entry['filename']}",
            "sha256": entry["sha256"],
            "sizeBytes": entry["size"],
        }
        for entry in sorted(adapter["artifacts"], key=lambda item: item["filename"])
    ]
    physical_files = [
        {"path": entry["path"], "sha256": entry["sha256"]}
        for entry in artifact_files
    ]
    serving_identity = hashlib.sha256(canonical_bytes({"files": physical_files})).hexdigest()
    resources = comparison["resourceUse"]["RESEARCH_ADAPTER"]
    execution = evaluation_config["execution"]
    manifest: dict[str, Any] = {
        "adapterIdentity": adapter["adapterIdentity"],
        "approvalStatus": "PENDING_APPROVAL",
        "artifact": {
            "files": artifact_files,
            "servingArtifactIdentity": serving_identity,
            "storageRoot": "ai-service/artifacts",
        },
        "artifactType": "ADAPTER",
        "assistantKey": "RESEARCH_ASSISTANT",
        "baseModel": adapter["baseModel"],
        "candidateId": adapter["candidateId"],
        "dataset": {
            "identity": adapter["datasetIdentity"],
            "manifestReference": training_config["dataset"]["manifestReference"],
            "version": adapter["pipelineVersion"],
        },
        "evaluation": {
            "comparisonIdentity": summary["comparisonIdentity"],
            "decision": summary["evaluationDecision"],
            "decisionIdentity": summary["decisionIdentity"],
            "reference": (
                "evidence/p7-t4-research-independent-evaluation/"
                "final-pass-remediation-v10/decision.json"
            ),
            "suite": comparison["suite"],
        },
        "license": BASE_MODEL_LICENSE,
        "rollback": {
            "manifestIdentity": rollback["artifactIdentity"],
            "reference": OUTPUT_REFERENCES["rollback-manifest.pending.json"],
            "version": rollback["version"],
        },
        "schemaVersion": "1.0.0",
        "servingRuntime": {
            "device": execution["device"],
            "evaluatedGpu": execution["gpuModel"],
            "pythonVersion": execution["pythonVersion"],
            "quantization": training_config["adapter"]["quantization"],
            "resourceEstimate": {
                "p95GenerationLatencyNs": resources["p95GenerationLatencyNs"],
                "peakRssBytes": resources["peakRssBytes"],
                "peakVramBytes": resources["peakVramBytes"],
            },
        },
        "sourceCommit": adapter["sourceCommit"],
        "status": "CANDIDATE",
        "trainingConfig": {
            "identity": adapter["trainingConfigIdentity"],
            "reference": "config/p7-t2-training-pipeline-t4-remediation-v10.json",
            "version": adapter["pipelineVersion"],
        },
        "trainingRunIdentity": adapter["trainingRunIdentity"],
        "version": "1.0.0",
    }
    manifest["artifactIdentity"] = artifact_identity(manifest)
    return manifest


def build_documents(
    *,
    summary: dict[str, Any] | None = None,
    decision: dict[str, Any] | None = None,
    adapter: dict[str, Any] | None = None,
) -> dict[str, dict[str, Any]]:
    summary = deepcopy(summary) if summary is not None else _load(P7_T4_SUMMARY_PATH)
    decision = deepcopy(decision) if decision is not None else _load(P7_T4_DECISION_PATH)
    adapter = deepcopy(adapter) if adapter is not None else _load(ADAPTER_MANIFEST_PATH)
    _validate_sources(summary, decision, adapter)
    comparison = _load(P7_T4_COMPARISON_PATH)
    evaluation_config = _load(EVALUATION_CONFIG_PATH)
    training_config = _load(TRAINING_CONFIG_PATH)

    rules = _registry_rules()
    rollback = _rollback_manifest(summary)
    manifest = _model_manifest(
        summary,
        adapter,
        comparison,
        evaluation_config,
        training_config,
        rollback,
    )
    request: dict[str, Any] = {
        "artifactType": "P7-T5-RESEARCH-PROMOTION-REQUEST",
        "assistantKey": "RESEARCH_ASSISTANT",
        "candidateId": summary["candidateId"],
        "evaluationBinding": {
            "comparisonIdentity": summary["comparisonIdentity"],
            "decision": summary["evaluationDecision"],
            "decisionIdentity": summary["decisionIdentity"],
            "promotionAllowed": summary["promotionAllowed"],
            "unresolvedCaseIds": summary["unresolvedCaseIds"],
        },
        "modelManifest": {
            "identity": manifest["artifactIdentity"],
            "reference": OUTPUT_REFERENCES["model-manifest.pending.json"],
        },
        "registryRules": {
            "identity": rules["artifactIdentity"],
            "reference": OUTPUT_REFERENCES["model-registry-rules.json"],
        },
        "requestedAuthorization": {
            "activateResearchProfile": True,
            "copyValidatedAdapterIntoRegistry": True,
            "materializeModelRegistry": True,
            "promoteCandidate": True,
            "servingLoadAllowed": False,
        },
        "rollback": {
            "identity": rollback["artifactIdentity"],
            "reference": OUTPUT_REFERENCES["rollback-manifest.pending.json"],
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_APPROVER_GATE",
    }
    request["requestIdentity"] = request_identity(request)
    return {
        "model-manifest.pending.json": manifest,
        "model-registry-rules.json": rules,
        "promotion-request.json": request,
        "rollback-manifest.pending.json": rollback,
    }


def build_artifacts() -> dict[str, bytes]:
    return {
        OUTPUT_REFERENCES[name]: json_bytes(value)
        for name, value in build_documents().items()
    }


def write_artifacts(*, check: bool) -> None:
    mismatches: list[str] = []
    for relative_path, content in build_artifacts().items():
        path = ROOT / relative_path
        if check:
            if not path.is_file() or path.read_bytes() != content:
                mismatches.append(relative_path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    if mismatches:
        raise PromotionRequestError("artifact mismatch: " + ", ".join(mismatches))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        write_artifacts(check=arguments.check)
        request = build_documents()["promotion-request.json"]
        print(
            json.dumps(
                {
                    "candidateId": request["candidateId"],
                    "requestIdentity": request["requestIdentity"],
                    "state": request["status"],
                },
                sort_keys=True,
            )
        )
        return 0
    except PromotionRequestError as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
