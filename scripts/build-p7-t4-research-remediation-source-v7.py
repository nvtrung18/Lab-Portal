#!/usr/bin/env python3
"""Build the governed retention-first Research remediation-v7 dataset."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
V6_DATASET_ROOT = "datasets/p7-research-synthetic-training-dataset-v6"
DATASET_ROOT = "datasets/p7-research-synthetic-training-dataset-v7"
SOURCE_ROOT = "datasets/p7-t4-research-remediation-source-v7"
CONFIG_ROOT = "config/p7-t4-research-remediation-governance-v7"
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v7-governance-approval.json"
APPROVAL_IDENTITY = "5bd1863605ea5b929c832864bcff168afae91eee3643a44516fe8301e68e54b5"
QUALITY_IDENTITY = "b90d95f677140cd88b8be5dc79236a10ac40de0cc61b384039a1ba5ff804a96a"
V6_DATASET_IDENTITY = "7a0c264196889beb0c91414cd10195681df895073dc7ce3aeef586123de751c1"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
RETAINED_COUNTS = {"evaluation": 48, "train": 288, "validation": 48}
ADDITION_COUNTS = {"evaluation": 16, "train": 96, "validation": 16}
RECORD_COUNTS = {"evaluation": 64, "train": 384, "validation": 64}
RECORD_FIELDS = {
    "assistantKey",
    "contentId",
    "curriculumSegment",
    "domain",
    "recordType",
    "schemaVersion",
    "semanticFamily",
    "trainingPrompt",
    "trainingTarget",
    "useCaseId",
    "visibility",
}
TARGET_FIELDS = {
    "evalCaseId",
    "observedActionRisk",
    "observedBehavior",
    "referencedContextIds",
    "response",
    "structuredOutput",
    "toolRequest",
}
TOPICS = (
    "synthetic phase alignment",
    "bounded signal review",
    "mock sample reconciliation",
    "controlled threshold analysis",
    "synthetic calibration planning",
    "bounded replicate assessment",
    "mock variance inspection",
    "controlled metadata review",
)


class DatasetBuildError(ValueError):
    pass


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
        raise DatasetBuildError(f"canonical JSON required: {error}") from error


def json_bytes(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
        + "\n"
    ).encode("utf-8")


def jsonl_bytes(values: list[dict[str, Any]]) -> bytes:
    return b"".join(canonical_bytes(value) + b"\n" for value in values)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def artifact_identity(value: dict[str, Any], field: str = "artifactIdentity") -> str:
    return sha256_bytes(
        canonical_bytes({key: item for key, item in value.items() if key != field})
    )


def _load_json(relative_path: str) -> dict[str, Any]:
    path = ROOT / relative_path
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise DatasetBuildError(f"cannot load {relative_path}: {error}") from error
    if not isinstance(value, dict):
        raise DatasetBuildError(f"object required: {relative_path}")
    return value


def _load_jsonl(relative_path: str) -> list[dict[str, Any]]:
    path = ROOT / relative_path
    try:
        values = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise DatasetBuildError(f"cannot load {relative_path}: {error}") from error
    if any(not isinstance(value, dict) for value in values):
        raise DatasetBuildError(f"object records required: {relative_path}")
    return values


def _validate_approval_boundary() -> None:
    approval = _load_json(APPROVAL_REFERENCE)
    quality = _load_json(f"{CONFIG_ROOT}/training-data-quality-spec-v7.json")
    manifest = _load_json(f"{V6_DATASET_ROOT}/manifest.approved.json")
    authorization = approval.get("authorization", {})
    if (
        approval.get("artifactIdentity") != APPROVAL_IDENTITY
        or artifact_identity(approval) != APPROVAL_IDENTITY
        or approval.get("status") != "APPROVED"
        or authorization.get("datasetV7PreparationAllowed") is not True
        or authorization.get("approvedV6RetentionReuseAllowed") is not True
        or authorization.get("externalTrainingAllowed") is not False
        or quality.get("artifactIdentity") != QUALITY_IDENTITY
        or quality.get("status") != "PENDING_GOVERNANCE_APPROVAL"
        or manifest.get("datasetIdentity") != V6_DATASET_IDENTITY
        or manifest.get("status") != "APPROVED_FOR_TRAINING_ONLY"
        or manifest.get("recordCounts") != RETAINED_COUNTS
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise DatasetBuildError("exact approved remediation-v7 preparation boundary required")

    inventory = {item["filename"]: item for item in manifest.get("artifacts", [])}
    for split in ("train", "validation", "evaluation"):
        path = ROOT / V6_DATASET_ROOT / f"{split}.jsonl"
        item = inventory.get(f"{split}.jsonl", {})
        if (
            item.get("recordCount") != RETAINED_COUNTS[split]
            or item.get("sha256") != sha256_bytes(path.read_bytes())
        ):
            raise DatasetBuildError(f"v6 {split} retention source mismatch")


def _tool_contract(kind: str, use_case: str, sample_id: str) -> tuple[object, dict[str, Any]]:
    if kind == "NONE":
        return None, {"kind": "NONE"}
    request = {
        "group": "RESEARCH_DRAFT",
        "intent": f"Prepare a bounded synthetic draft for {use_case}.",
        "kind": "REQUEST",
        "name": f"synthetic.v7.{use_case.lower()}.{sample_id}.draft",
    }
    return request, dict(request)


def _targeted_record(split: str, use_case: str, index: int) -> dict[str, Any]:
    language = "EN" if index % 2 == 0 else "VI"
    tool_kind = "NONE" if (index // 2) % 2 == 0 else "REQUEST"
    sample_id = f"{split[:3]}-{use_case[-3:].lower()}-{index + 1:02d}-{language.lower()}"
    topic = TOPICS[index % len(TOPICS)]
    project_id = f"syn-v7-project-{sample_id}"
    group_id = f"syn-v7-group-{sample_id}"
    task_id = f"syn-v7-task-{sample_id}"
    allowed_tool, target_tool = _tool_contract(tool_kind, use_case, sample_id)

    if use_case == "RESEARCH_UC_004":
        record_type = "RESEARCH_TASK_PROPOSAL_DRAFT"
        context_input = {
            "groupRef": {"resourceId": group_id, "resourceType": "PROJECT_GROUP"},
            "projectRef": {"resourceId": project_id, "resourceType": "PROJECT"},
            "task": f"Prepare a bounded synthetic proposal about {topic}.",
        }
        structured = {
            "groupRef": group_id,
            "kind": "RESEARCH_TASK_PROPOSAL_DRAFT",
            "projectRef": project_id,
            "requiresHumanReview": True,
            "taskTitle": (
                f"Synthetic proposal review for {topic}"
                if language == "EN"
                else f"Rà soát đề xuất tổng hợp về {topic}"
            ),
        }
        tags = ["FUNCTIONAL", "HUMAN_EVAL", "STRUCTURED_OUTPUT"]
    else:
        record_type = "RESEARCH_TASK_SUGGESTION_DRAFT"
        context_input = {
            "assignedTask": {"state": "TODO"},
            "task": f"Prepare a bounded synthetic suggestion about {topic}.",
            "taskRef": {"resourceId": task_id, "resourceType": "ASSIGNED_TASK"},
        }
        structured = {
            "kind": "RESEARCH_TASK_SUGGESTION_DRAFT",
            "requiresHumanReview": True,
            "suggestion": (
                f"Record a bounded synthetic suggestion for {topic}."
                if language == "EN"
                else f"Ghi đề xuất tổng hợp có giới hạn về {topic}."
            ),
            "taskRef": task_id,
        }
        tags = ["FUNCTIONAL", "HUMAN_EVAL", "STRUCTURED_OUTPUT"]
    if tool_kind == "REQUEST":
        tags.append("TOOL_ROUTING")

    if language == "EN":
        input_text = (
            f"OBJECT_REFERENCE_INPUT; prepare an independent bounded draft about {topic}; "
            "extract only resourceId strings into the closed structured output."
        )
        response_text = f"A bounded synthetic draft about {topic} requires human review."
    else:
        input_text = (
            f"OBJECT_REFERENCE_INPUT; tạo bản nháp tổng hợp độc lập về {topic}; "
            "chỉ trích chuỗi resourceId vào structured output đóng."
        )
        response_text = f"Bản nháp tổng hợp có giới hạn về {topic} cần con người rà soát."

    eval_case_id = f"SYN-V7-{split.upper()}-{use_case[-3:]}-{index + 1:02d}-{language}"
    prompt = {
        "allowedTool": allowed_tool,
        "assistantKey": "RESEARCH_ASSISTANT",
        "authorizedContext": {
            "domain": "RESEARCH",
            "input": context_input,
            "payload": {"syntheticTopic": topic},
            "recordType": record_type,
            "useCaseId": use_case,
        },
        "caseState": "ACTIVE",
        "evalCaseId": eval_case_id,
        "input": input_text,
        "p6t3Root": "research",
        "referencedContextIds": [],
        "rejectedTool": None,
        "responseContract": {
            "language": language,
            "markers": ["HUMAN_REVIEW_NEEDED"],
            "mode": "DRAFT_PRESENTATION",
        },
        "structuredOutputContract": record_type,
        "suiteTags": tags,
        "useCaseId": use_case,
    }
    target = {
        "evalCaseId": eval_case_id,
        "observedActionRisk": "DRAFT_ONLY",
        "observedBehavior": "SUCCESS",
        "referencedContextIds": [],
        "response": {
            "language": language,
            "markers": ["HUMAN_REVIEW_NEEDED"],
            "mode": "DRAFT_PRESENTATION",
            "text": response_text,
        },
        "structuredOutput": structured,
        "toolRequest": target_tool,
    }
    record: dict[str, Any] = {
        "assistantKey": "RESEARCH_ASSISTANT",
        "contentId": "",
        "curriculumSegment": "TARGETED_FAILED_CASE_REMEDIATION",
        "domain": "RESEARCH",
        "recordType": record_type,
        "schemaVersion": "7.0.0",
        "semanticFamily": "TARGETED_OBJECT_REFERENCE_EXTRACTION",
        "trainingPrompt": prompt,
        "trainingTarget": target,
        "useCaseId": use_case,
        "visibility": "RESEARCH_ASSISTANT_ONLY",
    }
    record["contentId"] = artifact_identity(record, "contentId")
    return record


def validate_record(record: dict[str, Any]) -> list[str]:
    findings: list[str] = []
    if set(record) != RECORD_FIELDS:
        return ["record fields are not closed"]
    if (
        record.get("schemaVersion") != "7.0.0"
        or record.get("assistantKey") != "RESEARCH_ASSISTANT"
        or record.get("domain") != "RESEARCH"
        or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
        or record.get("useCaseId") not in {"RESEARCH_UC_004", "RESEARCH_UC_005"}
        or artifact_identity(record, "contentId") != record.get("contentId")
    ):
        findings.append("record identity or boundary mismatch")
    prompt = record.get("trainingPrompt")
    target = record.get("trainingTarget")
    if not isinstance(prompt, dict) or not isinstance(target, dict) or set(target) != TARGET_FIELDS:
        return findings + ["prompt/target closed contract mismatch"]
    if (
        target.get("evalCaseId") != prompt.get("evalCaseId")
        or target.get("referencedContextIds") != []
        or prompt.get("referencedContextIds") != []
    ):
        findings.append("prompt/target binding mismatch")
    tool = target.get("toolRequest")
    allowed = prompt.get("allowedTool")
    expected_tool_fields = {
        "NONE": {"kind"},
        "REQUEST": {"group", "intent", "kind", "name"},
    }
    if (
        not isinstance(tool, dict)
        or set(tool) != expected_tool_fields.get(tool.get("kind"))
        or (tool.get("kind") == "NONE" and allowed is not None)
        or (tool.get("kind") == "REQUEST" and tool != allowed)
        or prompt.get("rejectedTool") is not None
    ):
        findings.append("tool contract mismatch")
    response = target.get("response")
    contract = prompt.get("responseContract")
    if (
        not isinstance(response, dict)
        or set(response) != {"language", "markers", "mode", "text"}
        or not isinstance(response.get("text"), str)
        or not response["text"].strip()
        or not isinstance(contract, dict)
        or {key: response.get(key) for key in ("language", "markers", "mode")} != contract
    ):
        findings.append("response contract mismatch")
    structured = target.get("structuredOutput")
    context_input = prompt.get("authorizedContext", {}).get("input", {})
    if prompt.get("structuredOutputContract") == "RESEARCH_TASK_PROPOSAL_DRAFT":
        expected_fields = {"groupRef", "kind", "projectRef", "requiresHumanReview", "taskTitle"}
        mappings = (("projectRef", "projectRef"), ("groupRef", "groupRef"))
    else:
        expected_fields = {"kind", "requiresHumanReview", "suggestion", "taskRef"}
        mappings = (("taskRef", "taskRef"),)
    if not isinstance(structured, dict) or set(structured) != expected_fields:
        findings.append("structured output fields mismatch")
    else:
        if structured.get("requiresHumanReview") is not True:
            findings.append("human review binding mismatch")
        for output_key, input_key in mappings:
            source = context_input.get(input_key)
            if (
                not isinstance(source, dict)
                or not isinstance(source.get("resourceId"), str)
                or structured.get(output_key) != source["resourceId"]
                or not isinstance(structured.get(output_key), str)
            ):
                findings.append("object reference extraction mismatch")
    return findings


def build_records() -> dict[str, list[dict[str, Any]]]:
    _validate_approval_boundary()
    records: dict[str, list[dict[str, Any]]] = {}
    additions_per_use_case = {"evaluation": 8, "train": 48, "validation": 8}
    for split in ("train", "validation", "evaluation"):
        retained = _load_jsonl(f"{V6_DATASET_ROOT}/{split}.jsonl")
        additions = [
            _targeted_record(split, use_case, index)
            for use_case in ("RESEARCH_UC_004", "RESEARCH_UC_005")
            for index in range(additions_per_use_case[split])
        ]
        for record in additions:
            findings = validate_record(record)
            if findings:
                raise DatasetBuildError("; ".join(findings))
        records[split] = retained + additions
    all_ids = [record["contentId"] for values in records.values() for record in values]
    if len(all_ids) != len(set(all_ids)):
        raise DatasetBuildError("train/validation/evaluation content IDs must be disjoint")
    return records


def _dataset_artifacts(records: dict[str, list[dict[str, Any]]]) -> dict[str, bytes]:
    return {
        f"{DATASET_ROOT}/train.jsonl": jsonl_bytes(records["train"]),
        f"{DATASET_ROOT}/validation.jsonl": jsonl_bytes(records["validation"]),
        f"{DATASET_ROOT}/evaluation.jsonl": jsonl_bytes(records["evaluation"]),
        f"{DATASET_ROOT}/rejections.jsonl": b"",
    }


def build_documents() -> dict[str, dict[str, Any]]:
    records = build_records()
    split_artifacts = _dataset_artifacts(records)
    inventory = [
        {
            "filename": Path(path).name,
            "recordCount": len(records[Path(path).stem]) if Path(path).stem in records else 0,
            "sha256": sha256_bytes(content),
        }
        for path, content in sorted(split_artifacts.items())
    ]
    dataset_identity = sha256_bytes(canonical_bytes(inventory))
    manifest: dict[str, Any] = {
        "approval_status": "PENDING_TRAINING_APPROVAL",
        "artifacts": inventory,
        "baseDatasetIdentity": V6_DATASET_IDENTITY,
        "contractHoldout": {
            "recordCount": 64,
            "split": "evaluation",
            "usedForOptimization": False,
        },
        "datasetIdentity": dataset_identity,
        "preparationApprovalIdentity": APPROVAL_IDENTITY,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "qualityIdentity": QUALITY_IDENTITY,
        "recordCounts": RECORD_COUNTS,
        "retainedRecordCounts": RETAINED_COUNTS,
        "schemaVersion": "7.0.0",
        "status": "PENDING_TRAINING_APPROVAL",
        "targetedAdditionCounts": ADDITION_COUNTS,
        "trainingAuthorized": False,
    }
    manifest["artifactIdentity"] = artifact_identity(manifest)
    provenance: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V7-SYNTHETIC-PROVENANCE",
        "baseDatasetIdentity": V6_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "generatorReference": "scripts/build-p7-t4-research-remediation-source-v7.py",
        "independentTargetedSyntheticGeneration": True,
        "noFrozenEvaluationContentCopied": True,
        "preparationApprovalIdentity": APPROVAL_IDENTITY,
        "retainedV6RecordsUnchanged": True,
        "schemaVersion": "1.0.0",
    }
    provenance["artifactIdentity"] = artifact_identity(provenance)
    contract: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V7-TRAINING-CONTRACT",
        "baseDatasetIdentity": V6_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "evaluationRecordCount": 64,
        "frozenEvaluationUseAllowed": False,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "runtimeNormalizationAllowed": False,
        "schemaVersion": "1.0.0",
        "trainingRecordCount": 384,
        "validationRecordCount": 64,
    }
    contract["artifactIdentity"] = artifact_identity(contract)
    card: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V7-DATASET-CARD",
        "baseDatasetIdentity": V6_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "disposition": "PENDING_TRAINING_APPROVAL",
        "recordCounts": RECORD_COUNTS,
        "remediationPriority": "TARGETED_DATASET_QUALITY_ONLY",
        "schemaVersion": "1.0.0",
    }
    card["artifactIdentity"] = artifact_identity(card)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY",
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V7-TRAINING-APPROVAL-REQUEST",
        "datasetIdentity": dataset_identity,
        "datasetManifestReference": f"{DATASET_ROOT}/manifest.json",
        "externalTrainingAllowed": False,
        "preparationApprovalIdentity": APPROVAL_IDENTITY,
        "preparationApprovalReference": APPROVAL_REFERENCE,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "qualityIdentity": QUALITY_IDENTITY,
        "requestId": "P7-T1C-RESEARCH-REMEDIATION-V7-TRAINING-APPROVAL-REQUEST-001",
        "requestedScope": {
            "datasetVersion": "7.0.0",
            "externalSingleT4Training": True,
            "freshBaseModelStartRequired": True,
            "retainedV6RecordsUnchanged": True,
            "syntheticHoldoutInferenceGateRequired": True,
            "trainingMethod": "QLORA",
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
        "trainingAuthorized": False,
    }
    request["requestIdentity"] = artifact_identity(request, "requestIdentity")
    source_export: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V7-SOURCE-EXPORT",
        "baseDatasetIdentity": V6_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "records": records,
        "schemaVersion": "1.0.0",
    }
    source_export["artifactIdentity"] = artifact_identity(source_export)
    return {
        f"{DATASET_ROOT}/manifest.json": manifest,
        f"{SOURCE_ROOT}/provenance.json": provenance,
        f"{SOURCE_ROOT}/source-export.json": source_export,
        f"{SOURCE_ROOT}/training-contract.json": contract,
        f"{CONFIG_ROOT}/training-dataset-card.pending.json": card,
        f"{CONFIG_ROOT}/training-approval-request.json": request,
    }


def build_artifacts() -> dict[str, bytes]:
    records = build_records()
    artifacts = _dataset_artifacts(records)
    artifacts.update({path: json_bytes(value) for path, value in build_documents().items()})
    return dict(sorted(artifacts.items()))


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
        raise DatasetBuildError("artifact mismatch: " + ", ".join(mismatches))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        write_artifacts(check=arguments.check)
        request = build_documents()[f"{CONFIG_ROOT}/training-approval-request.json"]
        print(
            json.dumps(
                {
                    "datasetIdentity": request["datasetIdentity"],
                    "requestIdentity": request["requestIdentity"],
                    "state": request["status"],
                },
                sort_keys=True,
            )
        )
        return 0
    except DatasetBuildError as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
