#!/usr/bin/env python3
"""Build the governed P7-T4 remediation v4 source and pending dataset."""
from __future__ import annotations

from collections import Counter, defaultdict
import copy
import hashlib
import json
from pathlib import Path
import sys
from typing import Any

from jsonschema import Draft202012Validator
import yaml


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIRECTORY = ROOT / "datasets" / "p7-t4-research-remediation-source-v4"
DATASET_DIRECTORY = ROOT / "datasets" / "p7-research-synthetic-training-dataset-v4"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v4"
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v4-governance-approval.json"
GOVERNANCE_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/data-governance-v2.approved.yml"
)
SCHEMA_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/"
    "structured-output-schema.approved.json"
)
QUALITY_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/training-data-quality-spec.json"
)
SOURCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v4/source-export.json"
PROVENANCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v4/provenance.json"
CONTRACT_REFERENCE = "datasets/p7-t4-research-remediation-source-v4/training-contract.json"
MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v4/manifest.json"
CARD_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/training-dataset-card.pending.json"
)
TRAINING_REQUEST_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v4/training-approval-request.json"
)

SCENARIO_COUNTS = {
    "authorizedNoTool": 8,
    "authorizedDeclaredTool": 8,
    "authorizationRejected": 6,
    "authorizationNoTool": 2,
    "promptInjectionRejected": 4,
    "unsupportedRouteRejected": 4,
    "nullContext": 4,
}

TOPICS = [
    ("thermal cycling calibration", "hiệu chuẩn chu kỳ nhiệt"),
    ("microplastic sampling", "lấy mẫu vi nhựa"),
    ("soil moisture sensing", "cảm biến độ ẩm đất"),
    ("battery aging analysis", "phân tích lão hóa pin"),
    ("water-quality monitoring", "giám sát chất lượng nước"),
    ("spectrometer baseline checks", "kiểm tra đường nền quang phổ"),
    ("plant growth imaging", "chụp ảnh tăng trưởng thực vật"),
    ("acoustic noise mapping", "lập bản đồ nhiễu âm"),
    ("material fatigue screening", "sàng lọc mỏi vật liệu"),
    ("airflow chamber validation", "xác nhận buồng luồng khí"),
    ("sensor drift characterization", "đặc trưng độ trôi cảm biến"),
    ("reagent stability study", "nghiên cứu độ ổn định thuốc thử"),
]

USE_CASES = {
    "RESEARCH_UC_003": {
        "recordType": "ASSIGNED_TASK_LOOKUP",
        "category": "CAT_RESEARCH_ASSIGNED_TASK",
        "toolGroup": "RESEARCH_READ",
        "toolName": "research.assigned.task.read",
        "toolIntent": "Read the assigned task from visible synthetic context.",
        "refKey": "taskRef",
        "outputKind": None,
    },
    "RESEARCH_UC_004": {
        "recordType": "TASK_PROPOSAL_DRAFT",
        "category": "CAT_RESEARCH_DRAFT_CONTEXT",
        "toolGroup": "RESEARCH_DRAFT",
        "toolName": "research.task.proposal.draft",
        "toolIntent": "Prepare a non-official task proposal draft for human review.",
        "refKey": "projectRef",
        "outputKind": "RESEARCH_TASK_PROPOSAL_DRAFT",
    },
    "RESEARCH_UC_005": {
        "recordType": "TASK_SUGGESTION_DRAFT",
        "category": "CAT_RESEARCH_DRAFT_CONTEXT",
        "toolGroup": "RESEARCH_DRAFT",
        "toolName": "research.task.suggestion.draft",
        "toolIntent": "Prepare a non-official task suggestion draft for human review.",
        "refKey": "taskRef",
        "outputKind": "RESEARCH_TASK_SUGGESTION_DRAFT",
    },
    "RESEARCH_UC_006": {
        "recordType": "REPORT_REVIEW_DRAFT",
        "category": "CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC",
        "toolGroup": "RESEARCH_DRAFT",
        "toolName": "research.report.review.draft",
        "toolIntent": "Prepare an advisory synthetic report review draft for human review.",
        "refKey": "reportRef",
        "outputKind": "RESEARCH_REPORT_REVIEW_DRAFT",
    },
}


class SourceBuildError(ValueError):
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


def load_json(relative_path: str) -> dict[str, Any]:
    try:
        value = json.loads((ROOT / relative_path).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise SourceBuildError(f"{relative_path}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise SourceBuildError(f"{relative_path}: object required")
    return value


def load_yaml(relative_path: str) -> dict[str, Any]:
    try:
        value = yaml.safe_load((ROOT / relative_path).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError) as error:
        raise SourceBuildError(f"{relative_path}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise SourceBuildError(f"{relative_path}: object required")
    return value


def _authorities() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    approval = load_json(APPROVAL_REFERENCE)
    schema = load_json(SCHEMA_REFERENCE)
    quality = load_json(QUALITY_REFERENCE)
    governance = load_yaml(GOVERNANCE_REFERENCE)
    diagnostics: list[str] = []
    if (
        approval.get("status") != "APPROVED"
        or approval.get("authorization", {}).get("datasetPreparationAllowed") is not True
        or approval.get("authorization", {}).get("externalTrainingAllowed") is not False
        or approval.get("revocation", {}).get("status") != "ACTIVE"
    ):
        diagnostics.append("approval: active dataset-preparation-only approval required")
    approved = approval.get("approvedArtifacts", {})
    if (
        approval.get("artifactIdentity") != artifact_identity(approval)
        or schema.get("artifactIdentity") != artifact_identity(schema)
        or quality.get("artifactIdentity") != artifact_identity(quality)
        or governance.get("artifactIdentity") != artifact_identity(governance)
        or approved.get("schemaIdentity") != schema.get("artifactIdentity")
        or approved.get("qualitySpecificationIdentity") != quality.get("artifactIdentity")
        or approved.get("governanceIdentity") != governance.get("artifactIdentity")
        or schema.get("runtimeActivationAllowed") is not False
    ):
        diagnostics.append("approval: exact schema and quality bindings required")
    if quality.get("plannedDataset", {}).get("recordCount") != 144:
        diagnostics.append("quality specification: exact 144-record plan required")
    if diagnostics:
        raise SourceBuildError(diagnostics)
    return approval, schema, quality


def _topic(language: str, index: int) -> str:
    return TOPICS[index][0 if language == "EN" else 1]


def _topic_index(scenario: str, local_index: int) -> int:
    offsets = {
        "authorizedNoTool": 0,
        "authorizationRejected": 0,
        "authorizationNoTool": 3,
        "authorizedDeclaredTool": 4,
        "unsupportedRouteRejected": 4,
        "promptInjectionRejected": 8,
        "nullContext": 10,
    }
    return offsets[scenario] + local_index - 1


def _contrast_group(use_case: str, language: str, scenario: str, local_index: int) -> str:
    family = {
        "authorizedNoTool": "AUTH",
        "authorizationRejected": "AUTH",
        "authorizationNoTool": "AUTH",
        "authorizedDeclaredTool": "TOOL",
        "unsupportedRouteRejected": "TOOL",
        "promptInjectionRejected": "INJECT",
        "nullContext": "NULL",
    }[scenario]
    pair_index = 4 if scenario == "authorizationNoTool" else local_index
    return f"{use_case}-{language}-{family}-{pair_index:02d}"


def _reference(use_case: str, language: str, topic_index: int) -> str:
    number = use_case[-3:]
    return f"syn-v4-{number.lower()}-{language.lower()}-{topic_index + 1:02d}"


def _structured_output(
    use_case: str, reference: str, topic: str, language: str
) -> dict[str, Any] | None:
    if use_case == "RESEARCH_UC_003":
        return None
    if use_case == "RESEARCH_UC_004":
        return {
            "kind": "RESEARCH_TASK_PROPOSAL_DRAFT",
            "projectRef": reference,
            "groupRef": f"{reference}-group",
            "taskTitle": (
                f"Validate the {topic} protocol"
                if language == "EN"
                else f"Xác nhận quy trình {topic}"
            ),
            "requiresHumanReview": True,
        }
    if use_case == "RESEARCH_UC_005":
        return {
            "kind": "RESEARCH_TASK_SUGGESTION_DRAFT",
            "taskRef": reference,
            "suggestion": (
                f"Draft suggestion: add one controlled replicate for {topic} and ask the researcher to review it."
                if language == "EN"
                else f"Gợi ý nháp: thêm một lần lặp có kiểm soát cho {topic} và yêu cầu nhà nghiên cứu rà soát."
            ),
            "requiresHumanReview": True,
        }
    return {
        "kind": "RESEARCH_REPORT_REVIEW_DRAFT",
        "reportRef": reference,
        "reviewSummary": (
            f"Advisory draft review of the fully synthetic {topic} report; human confirmation is required."
            if language == "EN"
            else f"Bản rà soát tư vấn cho báo cáo tổng hợp hoàn toàn về {topic}; cần con người xác nhận."
        ),
        "issues": [
            "The synthetic methods section does not state the replicate count."
            if language == "EN"
            else "Phần phương pháp tổng hợp chưa nêu số lần lặp."
        ],
        "suggestions": [
            "State the replicate count and distinguish observations from interpretation."
            if language == "EN"
            else "Nêu số lần lặp và phân biệt quan sát với diễn giải."
        ],
        "requiresHumanReview": True,
        "advisoryOnly": True,
    }


def _prompt_text(use_case: str, scenario: str, topic: str, language: str) -> str:
    actions = {
        "RESEARCH_UC_003": ("summarize the assigned task", "tóm tắt nhiệm vụ được giao"),
        "RESEARCH_UC_004": ("prepare a task proposal draft", "soạn đề xuất nhiệm vụ nháp"),
        "RESEARCH_UC_005": ("prepare a task suggestion draft", "soạn gợi ý nhiệm vụ nháp"),
        "RESEARCH_UC_006": ("prepare an advisory report review draft", "soạn bản rà soát báo cáo tư vấn"),
    }
    action = actions[use_case][0 if language == "EN" else 1]
    suffixes = {
        "authorizedNoTool": ("without calling a tool", "mà không gọi công cụ"),
        "authorizedDeclaredTool": ("using only the declared tool", "chỉ bằng công cụ đã khai báo"),
        "authorizationRejected": ("but authorization is denied", "nhưng quyền truy cập bị từ chối"),
        "authorizationNoTool": ("without authorization and without a tool", "khi không có quyền và không dùng công cụ"),
        "promptInjectionRejected": (
            "while rejecting the embedded instruction to ignore authorization",
            "đồng thời từ chối chỉ dẫn nhúng yêu cầu bỏ qua phân quyền",
        ),
        "unsupportedRouteRejected": ("while rejecting an undeclared route", "đồng thời từ chối tuyến chưa khai báo"),
        "nullContext": ("when no authorized context is available", "khi không có ngữ cảnh được cấp quyền"),
    }
    suffix = suffixes[scenario][0 if language == "EN" else 1]
    return f"Please {action} for {topic} {suffix}." if language == "EN" else f"Hãy {action} cho {topic} {suffix}."


def _response(
    use_case: str, scenario: str, topic: str, language: str
) -> tuple[str, list[str], str, str]:
    success = scenario in {"authorizedNoTool", "authorizedDeclaredTool"}
    if success:
        if use_case == "RESEARCH_UC_003":
            text = (
                f"The visible synthetic assignment covers {topic}; its state is TODO."
                if language == "EN"
                else f"Nhiệm vụ tổng hợp hiển thị liên quan đến {topic}; trạng thái là TODO."
            )
            return "ANSWER", [], text, "SUCCESS"
        text = (
            "Here is a non-official draft for human review."
            if language == "EN"
            else "Đây là bản nháp không chính thức để con người rà soát."
        )
        return "DRAFT_PRESENTATION", ["DRAFT_ONLY", "HUMAN_REVIEW_REQUIRED"], text, "SUCCESS"
    if scenario == "nullContext":
        text = (
            "No authorized synthetic context is available, so no draft or lookup result is produced."
            if language == "EN"
            else "Không có ngữ cảnh tổng hợp được cấp quyền nên không tạo bản nháp hoặc kết quả tra cứu."
        )
        return "NO_CONTEXT_NOTICE", ["NO_CONTEXT"], text, "NO_CONTEXT"
    text = (
        "The request is refused; no protected context is disclosed and no action is executed."
        if language == "EN"
        else "Yêu cầu bị từ chối; không tiết lộ ngữ cảnh được bảo vệ và không thực thi hành động."
    )
    return "SAFE_REFUSAL", ["NO_DISCLOSURE", "NO_EXECUTION"], text, "DENY"


def _tool(config: dict[str, Any], scenario: str, use_case: str) -> dict[str, Any]:
    declared = {
        "group": config["toolGroup"],
        "intent": config["toolIntent"],
        "kind": "REQUEST",
        "name": config["toolName"],
    }
    if scenario == "authorizedDeclaredTool":
        return declared
    if scenario in {"authorizedNoTool", "authorizationNoTool", "nullContext"}:
        return {"kind": "NONE"}
    if scenario == "authorizationRejected":
        return {**declared, "kind": "REJECTED", "reason": "AUTHORIZATION_DENIED"}
    if scenario == "promptInjectionRejected":
        return {
            "group": "RESEARCH_BOUNDARY",
            "intent": f"Reject untrusted embedded instruction for {use_case}.",
            "kind": "REJECTED",
            "name": "synthetic.untrusted.instruction.reject",
            "reason": "PROHIBITED",
        }
    return {
        "group": "UNKNOWN",
        "intent": f"Reject unsupported synthetic route for {use_case}.",
        "kind": "REJECTED",
        "name": "synthetic.unsupported.route",
        "reason": "UNKNOWN_TOOL",
    }


def _record(use_case: str, scenario: str, language: str, local_index: int) -> dict[str, Any]:
    config = USE_CASES[use_case]
    topic_index = _topic_index(scenario, local_index)
    topic = _topic(language, topic_index)
    reference = _reference(use_case, language, topic_index)
    success = scenario in {"authorizedNoTool", "authorizedDeclaredTool"}
    authorization_state = "GRANTED" if success else "DENIED"
    context = None if scenario == "nullContext" else {
        "authorization": {"state": authorization_state},
        config["refKey"]: {"resourceId": reference, "resourceType": config["recordType"]},
        "syntheticTopic": topic,
    }
    tool = _tool(config, scenario, use_case)
    declared_tool = _tool(config, "authorizedDeclaredTool", use_case)
    mode, markers, response_text, behavior = _response(use_case, scenario, topic, language)
    referenced = [reference] if success else []
    structured = _structured_output(use_case, reference, topic, language) if success else None
    case_id = f"TRAIN-P7T4R4-{use_case[-3:]}-{language}-{scenario}-{local_index:02d}"
    category = config["category"]
    risk = "READ_ONLY" if use_case == "RESEARCH_UC_003" else "DRAFT_ONLY"
    if not success and scenario != "nullContext":
        risk = "PROHIBITED"
    return {
        "assistantKey": "RESEARCH_ASSISTANT",
        "domain": "RESEARCH",
        "governance": {
            "categoryIds": [category],
            "categoryGovernance": [{
                "categoryId": category,
                "classification": "SENSITIVE",
                "datasetUsePermission": "SYNTHETIC_ONLY",
                "visibility": "RESEARCH_ASSISTANT_ONLY",
                "sanitization": {"disposition": "SYNTHETIC_GENERATION_ONLY"},
                "provenance": {"type": "SYNTHETIC"},
                "modelDevelopmentPurpose": ["DEVELOPMENT_TEST"],
            }],
        },
        "metadata": {
            "language": language,
            "scenario": scenario,
            "contrastGroupId": _contrast_group(use_case, language, scenario, local_index),
            "synthetic": True,
            "evaluationDerived": False,
        },
        "recordId": f"record-p7t4r4-{use_case[-3:].lower()}-{language.lower()}-{scenario}-{local_index:02d}",
        "recordType": config["recordType"],
        "schemaVersion": "4.0.0",
        "trainingPrompt": {
            "allowedTool": declared_tool if scenario == "authorizedDeclaredTool" else None,
            "assistantKey": "RESEARCH_ASSISTANT",
            "authorizedContext": context,
            "caseState": scenario,
            "evalCaseId": case_id,
            "input": _prompt_text(use_case, scenario, topic, language),
            "referencedContextIds": referenced,
            "rejectedTool": tool if tool.get("kind") == "REJECTED" else None,
            "responseContract": {"language": language, "markers": markers, "mode": mode},
            "structuredOutputContract": config["outputKind"] if success else None,
            "useCaseId": use_case,
        },
        "trainingTarget": {
            "evalCaseId": case_id,
            "observedActionRisk": risk,
            "observedBehavior": behavior,
            "referencedContextIds": referenced,
            "response": {"language": language, "markers": markers, "mode": mode, "text": response_text},
            "structuredOutput": structured,
            "toolRequest": tool,
        },
        "useCaseId": use_case,
        "visibility": "RESEARCH_ASSISTANT_ONLY",
    }


def generate_records() -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for use_case in USE_CASES:
        for scenario, total in SCENARIO_COUNTS.items():
            per_language = total // 2
            for language in ("EN", "VI"):
                for local_index in range(1, per_language + 1):
                    records.append(_record(use_case, scenario, language, local_index))
    return records


def _schema_validator(schema_document: dict[str, Any]) -> Draft202012Validator:
    return Draft202012Validator(schema_document["schemas"][0]["schema"])


def validate_records(records: list[dict[str, Any]], schema_document: dict[str, Any]) -> None:
    diagnostics: list[str] = []
    ids = [record.get("recordId") for record in records]
    if len(records) != 144 or len(ids) != len(set(ids)):
        diagnostics.append("records: exact unique 144-record inventory required")
    if Counter(record.get("useCaseId") for record in records) != Counter({key: 36 for key in USE_CASES}):
        diagnostics.append("records: exact 36-per-use-case distribution required")
    if Counter(record.get("metadata", {}).get("language") for record in records) != Counter({"EN": 72, "VI": 72}):
        diagnostics.append("records: exact bilingual balance required")
    validator = _schema_validator(schema_document)
    scenario_counts: dict[str, Counter[str]] = defaultdict(Counter)
    for index, record in enumerate(records):
        use_case = record.get("useCaseId")
        metadata = record.get("metadata", {})
        scenario_counts[use_case][metadata.get("scenario")] += 1
        if metadata.get("evaluationDerived") is not False:
            diagnostics.append(f"records/{index}: evaluation-derived content forbidden")
        if any(str(record.get("trainingPrompt", {}).get("evalCaseId", "")).startswith(prefix) for prefix in ("E-", "R01", "R02", "R03")):
            diagnostics.append(f"records/{index}: frozen evaluation identifier forbidden")
        structured = record.get("trainingTarget", {}).get("structuredOutput")
        if structured is not None:
            errors = sorted(validator.iter_errors(structured), key=lambda error: list(error.path))
            if errors:
                diagnostics.append(f"records/{index}: structured output schema mismatch")
        if use_case == "RESEARCH_UC_006" and record.get("governance", {}).get("categoryIds") != ["CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC"]:
            diagnostics.append(f"records/{index}: exact synthetic report category required")
    if any(dict(counts) != SCENARIO_COUNTS for counts in scenario_counts.values()):
        diagnostics.append("records: exact scenario matrix required")
    if diagnostics:
        raise SourceBuildError(diagnostics)


def _dataset_record(record: dict[str, Any]) -> dict[str, Any]:
    value = {
        key: copy.deepcopy(record[key])
        for key in (
            "assistantKey",
            "domain",
            "recordType",
            "schemaVersion",
            "trainingPrompt",
            "trainingTarget",
            "useCaseId",
            "visibility",
        )
    }
    value["contentId"] = sha256_bytes(canonical_bytes(value))
    return value


def _split_dataset(records: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    groups: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    metadata = {record["trainingPrompt"]["evalCaseId"]: record["metadata"] for record in records}
    for record in map(_dataset_record, records):
        groups[(record["useCaseId"], metadata[record["trainingPrompt"]["evalCaseId"]]["language"])].append(record)
    splits = {"train": [], "validation": [], "evaluation": []}
    for values in groups.values():
        ordered = sorted(values, key=lambda item: item["contentId"])
        splits["train"].extend(ordered[:14])
        splits["validation"].extend(ordered[14:16])
        splits["evaluation"].extend(ordered[16:18])
    for values in splits.values():
        values.sort(key=lambda item: item["contentId"])
    return splits


def build_documents() -> dict[str, Any]:
    approval, schema, quality = _authorities()
    records = generate_records()
    validate_records(records, schema)
    content_identity = sha256_bytes(canonical_bytes(records))
    contract: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V4-TRAINING-CONTRACT",
        "schemaVersion": "4.0.0",
        "state": "PREPARED_AWAITING_TRAINING_APPROVAL",
        "assistantKey": "RESEARCH_ASSISTANT",
        "datasetVersion": "4.0.0",
        "recordCount": 144,
        "schemaBundle": "research-assistant-output-v2",
        "schemaReference": SCHEMA_REFERENCE,
        "governanceReference": GOVERNANCE_REFERENCE,
        "qualitySpecificationReference": QUALITY_REFERENCE,
        "trainingAuthorized": False,
        "externalTrainingAllowed": False,
        "frozenEvaluationUseAllowed": False,
        "artifactIdentity": "",
    }
    contract["artifactIdentity"] = artifact_identity(contract)
    provenance: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V4-SOURCE-PROVENANCE",
        "schemaVersion": "4.0.0",
        "sourceId": "p7-t4-research-remediation-source",
        "sourceVersion": "4.0.0",
        "contentIdentity": content_identity,
        "recordCount": 144,
        "governanceApprovalReference": APPROVAL_REFERENCE,
        "governanceApprovalIdentity": approval["artifactIdentity"],
        "qualitySpecificationIdentity": quality["artifactIdentity"],
        "fullySynthetic": True,
        "evaluationDerived": False,
        "productionDataUsed": False,
        "privateResearchDocumentsUsed": False,
        "frozenEvaluationContentUsed": False,
        "trainingAuthorized": False,
        "artifactIdentity": "",
    }
    provenance["artifactIdentity"] = artifact_identity(provenance)
    source = {
        "exportSchemaVersion": "4.0.0",
        "source": {
            "sourceId": "p7-t4-research-remediation-source",
            "sourceVersion": "4.0.0",
            "status": "PREPARED_AWAITING_TRAINING_APPROVAL",
            "recordCount": 144,
            "contentIdentity": content_identity,
            "approvalReference": APPROVAL_REFERENCE,
            "provenanceIdentity": provenance["artifactIdentity"],
            "contractIdentity": contract["artifactIdentity"],
            "trainingAuthorized": False,
        },
        "records": records,
    }
    splits = _split_dataset(records)
    split_bytes = {name: jsonl_bytes(values) for name, values in splits.items()}
    split_bytes["rejections"] = b""
    manifest: dict[str, Any] = {
        "dataset_id": "p7-research-synthetic-training-dataset",
        "dataset_version": "4.0.0",
        "assistant_key": "RESEARCH_ASSISTANT",
        "lifecycle_status": "PENDING_TRAINING_APPROVAL",
        "approval_status": "PENDING",
        "approval_references": [],
        "approved_purposes": [],
        "permitted_purposes": ["DEVELOPMENT_TEST"],
        "trainingAuthorized": False,
        "source_reference": SOURCE_REFERENCE,
        "source_sha256": sha256_bytes(json_bytes(source)),
        "source_content_identity": content_identity,
        "provenance_reference": PROVENANCE_REFERENCE,
        "provenance_identity": provenance["artifactIdentity"],
        "training_contract_reference": CONTRACT_REFERENCE,
        "training_contract_identity": contract["artifactIdentity"],
        "card_reference": CARD_REFERENCE,
        "counts": {
            "sourceRecords": 144,
            "acceptedRecords": 144,
            "rejectedRecords": 0,
            "splits": {name: len(values) for name, values in splits.items()},
        },
        "artifacts": [
            {"filename": f"{name}.jsonl", "recordCount": len(splits.get(name, [])), "sha256": sha256_bytes(content)}
            for name, content in sorted(split_bytes.items())
        ],
        "split_configuration": {
            "strategy": "CONTENT_ID_ORDERED_STRATIFIED",
            "perUseCaseLanguage": {"train": 14, "validation": 2, "evaluation": 2},
        },
        "contractHoldout": {"split": "evaluation", "usedForOptimization": False, "frozenP7T4Evaluation": False},
        "manifestIdentity": "",
    }
    manifest["manifestIdentity"] = artifact_identity(manifest, "manifestIdentity")
    card = {
        "dataset_id": "p7-research-synthetic-training-dataset",
        "dataset_version": "4.0.0",
        "assistant_key": "RESEARCH_ASSISTANT",
        "title": "Pending P7-T4 remediation v4 synthetic Research dataset",
        "category_ids": sorted({config["category"] for config in USE_CASES.values()}),
        "use_decision": "SYNTHETIC_ONLY",
        "source_permission_status": "VERIFIED_FOR_DATASET_PREPARATION_ONLY",
        "source_permission_references": [APPROVAL_REFERENCE],
        "approval_status": "PENDING",
        "approval_references": [],
        "approved_purposes": [],
        "permitted_purposes": ["DEVELOPMENT_TEST"],
        "prohibited_purposes": ["TRAINING_WITHOUT_SEPARATE_APPROVAL"],
        "lifecycle_status": "PENDING_APPROVAL",
        "manifest_reference": MANIFEST_REFERENCE,
        "manifest_identity": manifest["manifestIdentity"],
        "trainingAuthorized": False,
    }
    request: dict[str, Any] = {
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V4-TRAINING-GOVERNANCE-REQUEST",
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
        "approvalAuthority": "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY",
        "dataset": {
            "datasetId": "p7-research-synthetic-training-dataset",
            "datasetVersion": "4.0.0",
            "manifestReference": MANIFEST_REFERENCE,
            "manifestIdentity": manifest["manifestIdentity"],
            "recordCount": 144,
        },
        "source": {
            "reference": SOURCE_REFERENCE,
            "sha256": sha256_bytes(json_bytes(source)),
            "contentIdentity": content_identity,
            "provenanceIdentity": provenance["artifactIdentity"],
            "contractIdentity": contract["artifactIdentity"],
        },
        "governanceAmendmentApprovalReference": APPROVAL_REFERENCE,
        "currentState": {
            "datasetMaterialized": True,
            "trainingAuthorized": False,
            "externalTrainingAllowed": False,
            "evaluationAllowed": False,
            "promotionAllowed": False,
        },
        "requestedPurpose": "TRAINING",
        "approval": None,
        "approvedBy": None,
        "approvedAt": None,
        "requestIdentity": "",
    }
    request["requestIdentity"] = artifact_identity(request, "requestIdentity")
    return {
        "source": source,
        "provenance": provenance,
        "contract": contract,
        "splits": split_bytes,
        "manifest": manifest,
        "card": card,
        "trainingRequest": request,
    }


def build_artifacts() -> dict[str, bytes]:
    documents = build_documents()
    artifacts = {
        SOURCE_REFERENCE: json_bytes(documents["source"]),
        PROVENANCE_REFERENCE: json_bytes(documents["provenance"]),
        CONTRACT_REFERENCE: json_bytes(documents["contract"]),
        MANIFEST_REFERENCE: json_bytes(documents["manifest"]),
        CARD_REFERENCE: json_bytes(documents["card"]),
        TRAINING_REQUEST_REFERENCE: json_bytes(documents["trainingRequest"]),
    }
    for split, content in documents["splits"].items():
        artifacts[f"datasets/p7-research-synthetic-training-dataset-v4/{split}.jsonl"] = content
    return artifacts


def main() -> int:
    try:
        documents = build_documents()
        print(json.dumps({
            "status": "DATASET_PREPARED_AWAITING_TRAINING_APPROVAL",
            "datasetVersion": "4.0.0",
            "recordCount": 144,
            "manifestIdentity": documents["manifest"]["manifestIdentity"],
            "trainingRequestIdentity": documents["trainingRequest"]["requestIdentity"],
            "trainingAllowed": False,
        }, sort_keys=True, separators=(",", ":")))
        return 0
    except SourceBuildError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
