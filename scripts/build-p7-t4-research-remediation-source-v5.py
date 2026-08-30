#!/usr/bin/env python3
"""Build the synthetic, evaluation-disjoint P7-T4 remediation v5 dataset."""
from __future__ import annotations

import argparse
import copy
from collections import Counter, defaultdict
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIRECTORY = ROOT / "datasets" / "p7-t4-research-remediation-source-v5"
DATASET_DIRECTORY = ROOT / "datasets" / "p7-research-synthetic-training-dataset-v5"
CONFIG_DIRECTORY = ROOT / "config" / "p7-t4-research-remediation-governance-v5"
GOVERNANCE_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v4-governance-approval.json"
)
QUALITY_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v5/"
    "training-data-quality-spec-v5.json"
)
EVALUATOR_REFERENCE = "scripts/validate-p7-t4-research-evaluation-v2.py"
EVALUATOR_SUITE_APPROVAL_REFERENCE = (
    "evidence/p7-t4-research-remediation-v5-evaluator-governance-approval.json"
)
SOURCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v5/source-export.json"
PROVENANCE_REFERENCE = "datasets/p7-t4-research-remediation-source-v5/provenance.json"
CONTRACT_REFERENCE = "datasets/p7-t4-research-remediation-source-v5/training-contract.json"
MANIFEST_REFERENCE = "datasets/p7-research-synthetic-training-dataset-v5/manifest.json"
CARD_REFERENCE = (
    "config/p7-t4-research-remediation-governance-v5/"
    "training-dataset-card.pending.json"
)
SCENARIO_COUNTS = {
    "authorizedNoToolNoCitations": 8,
    "authorizedNoToolWithCitations": 8,
    "authorizedDeclaredTool": 8,
    "authorizationRejected": 6,
    "authorizationNoTool": 4,
    "promptInjectionRejected": 6,
    "unsupportedRouteRejected": 4,
    "nullContext": 4,
}
TOPICS = {
    "EN": [
        "battery aging analysis",
        "plant growth imaging",
        "water quality sensing",
        "synthetic catalyst screening",
        "thermal cycle monitoring",
        "controlled soil moisture study",
        "spectral calibration review",
        "robotic sample handling",
        "material fatigue observation",
        "synthetic microscopy workflow",
        "bounded sensor drift study",
        "laboratory airflow mapping",
    ],
    "VI": [
        "phân tích lão hóa pin tổng hợp",
        "ảnh tăng trưởng thực vật tổng hợp",
        "cảm biến chất lượng nước tổng hợp",
        "sàng lọc xúc tác tổng hợp",
        "theo dõi chu kỳ nhiệt tổng hợp",
        "nghiên cứu độ ẩm đất tổng hợp",
        "rà soát hiệu chuẩn phổ tổng hợp",
        "xử lý mẫu bằng robot tổng hợp",
        "quan sát mỏi vật liệu tổng hợp",
        "quy trình hiển vi tổng hợp",
        "nghiên cứu trôi cảm biến tổng hợp",
        "lập bản đồ luồng khí phòng thí nghiệm tổng hợp",
    ],
}
USE_CASES = {
    "RESEARCH_UC_003": {
        "recordType": "ASSIGNED_TASK_SUMMARY",
        "refKey": "taskRef",
        "toolGroup": "RESEARCH_READ",
        "toolName": "research.synthetic.task.read",
        "toolIntent": "Read one authorized synthetic assigned task.",
        "outputKind": None,
        "category": "CAT_RESEARCH_TASKS",
    },
    "RESEARCH_UC_004": {
        "recordType": "TASK_PROPOSAL_DRAFT",
        "refKey": "projectRef",
        "toolGroup": "RESEARCH_DRAFT",
        "toolName": "research.synthetic.task-proposal.draft",
        "toolIntent": "Prepare one non-official synthetic task proposal draft.",
        "outputKind": "RESEARCH_TASK_PROPOSAL_DRAFT",
        "category": "CAT_RESEARCH_TASKS",
    },
    "RESEARCH_UC_005": {
        "recordType": "TASK_SUGGESTION_DRAFT",
        "refKey": "taskRef",
        "toolGroup": "RESEARCH_DRAFT",
        "toolName": "research.synthetic.task-suggestion.draft",
        "toolIntent": "Prepare one non-official synthetic task suggestion draft.",
        "outputKind": "RESEARCH_TASK_SUGGESTION_DRAFT",
        "category": "CAT_RESEARCH_TASKS",
    },
    "RESEARCH_UC_006": {
        "recordType": "REPORT_REVIEW_DRAFT",
        "refKey": "reportRef",
        "toolGroup": "RESEARCH_DRAFT",
        "toolName": "research.synthetic.report-review.draft",
        "toolIntent": "Prepare one advisory synthetic report review draft.",
        "outputKind": "RESEARCH_REPORT_REVIEW_DRAFT",
        "category": "CAT_RESEARCH_REPORT_REVIEW_SYNTHETIC",
    },
}


class SourceBuildError(ValueError):
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
        raise SourceBuildError(f"canonical JSON required: {error}") from error


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
    path = ROOT / relative_path
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise SourceBuildError(f"cannot load {relative_path}: {error}") from error
    if not isinstance(value, dict):
        raise SourceBuildError(f"object required: {relative_path}")
    return value


def _load_evaluator():
    path = ROOT / EVALUATOR_REFERENCE
    specification = importlib.util.spec_from_file_location(
        "p7_t4_evaluator_v2_for_dataset_v5", path
    )
    if specification is None or specification.loader is None:
        raise SourceBuildError(f"cannot load {EVALUATOR_REFERENCE}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


def _authorities() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    approval = load_json(GOVERNANCE_APPROVAL_REFERENCE)
    quality = load_json(QUALITY_REFERENCE)
    evaluator_suite_approval = load_json(EVALUATOR_SUITE_APPROVAL_REFERENCE)
    if (
        approval.get("status") != "APPROVED"
        or approval.get("authorization", {}).get("datasetPreparationAllowed") is not True
        or approval.get("authorization", {}).get("externalTrainingAllowed") is not False
        or approval.get("scope", {}).get("newSyntheticCategoryOnly") is not True
        or quality.get("state") != "QUALITY_SPEC_READY_AWAITING_GOVERNANCE_APPROVAL"
        or quality.get("plannedDataset", {}).get("proposedVersion") != "5.0.0"
        or quality.get("antiLeakage", {}).get("evaluationTrainingSources") != []
    ):
        raise SourceBuildError("authorities: exact dataset-preparation-only scope required")
    authorization = evaluator_suite_approval.get("authorization", {})
    approved_artifacts = evaluator_suite_approval.get("approvedArtifacts", {})
    evaluator = load_json(str(approved_artifacts.get("evaluatorReference", "")))
    suite = load_json(str(approved_artifacts.get("suiteReference", "")))
    if (
        evaluator_suite_approval.get("status") != "APPROVED"
        or evaluator_suite_approval.get("requestIdentity")
        != "4a9ceb3be319bc2fb96b3d856bfdcb2c6c263a325fab5863f45265b4fe52d93f"
        or evaluator_suite_approval.get("artifactIdentity")
        != artifact_identity(evaluator_suite_approval)
        or evaluator_suite_approval.get("revocation", {}).get("status") != "ACTIVE"
        or authorization.get("evaluatorV2UseAllowed") is not True
        or authorization.get("suiteV2UseAllowed") is not True
        or authorization.get("datasetV5PreparationAllowed") is not True
        or authorization.get("externalTrainingAllowed") is not False
        or authorization.get("externalEvaluationExecutionAllowed") is not False
        or authorization.get("runtimeNormalizationAllowed") is not False
        or authorization.get("constrainedDecodingAllowed") is not False
        or approved_artifacts.get("evaluatorIdentity")
        != evaluator.get("artifactIdentity")
        or approved_artifacts.get("suiteIdentity") != suite.get("suiteDigest")
        or evaluator.get("status") != "APPROVED"
        or evaluator.get("evaluatorVersion") != "2.0.0"
        or suite.get("status") != "APPROVED"
        or suite.get("suiteVersion") != "2.0.0"
        or suite.get("externalExecutionAllowed") is not False
    ):
        raise SourceBuildError(
            "authorities: exact active evaluator-v2 and suite-v2 approval required"
        )
    return approval, quality, evaluator_suite_approval


def _frozen_evaluation_contract() -> tuple[set[str], set[str]]:
    inventories: list[dict[str, Any]] = []
    base = load_json("evals/p6-t4-evaluation-suites.yaml")
    gap = load_json("evals/p7-t3-research-gap-evaluation-suite.json")
    inventories.extend(base.get("caseInventory", []))
    inventories.extend(gap.get("caseInventory", []))
    inventories.extend(gap.get("proposedCaseInventory", []))
    case_ids = {
        item["evalCaseId"]
        for item in inventories
        if isinstance(item, dict) and isinstance(item.get("evalCaseId"), str)
    }
    prompts = {
        item["input"]
        for item in inventories
        if isinstance(item, dict) and isinstance(item.get("input"), str)
    }
    return case_ids, prompts


def _reference(use_case: str, language: str, topic_index: int) -> str:
    return f"syn-v5-{use_case[-3:].lower()}-{language.lower()}-{topic_index + 1:02d}"


def _topic_index(scenario: str, local_index: int) -> int:
    offset = list(SCENARIO_COUNTS).index(scenario) * 3
    return (offset + local_index - 1) % len(TOPICS["EN"])


def _structured_output(
    use_case: str, reference: str, topic: str, language: str
) -> dict[str, Any] | None:
    if use_case == "RESEARCH_UC_003":
        return None
    if use_case == "RESEARCH_UC_004":
        return {
            "groupRef": f"{reference}-group",
            "kind": "RESEARCH_TASK_PROPOSAL_DRAFT",
            "projectRef": reference,
            "requiresHumanReview": True,
            "taskTitle": (
                f"Validate the {topic} protocol"
                if language == "EN"
                else f"Xác nhận quy trình {topic}"
            ),
        }
    if use_case == "RESEARCH_UC_005":
        return {
            "kind": "RESEARCH_TASK_SUGGESTION_DRAFT",
            "requiresHumanReview": True,
            "suggestion": (
                f"Add one controlled replicate for {topic} and request human review."
                if language == "EN"
                else f"Thêm một lần lặp có kiểm soát cho {topic} và yêu cầu con người rà soát."
            ),
            "taskRef": reference,
        }
    return {
        "advisoryOnly": True,
        "issues": [
            (
                "The fully synthetic methods section omits its replicate count."
                if language == "EN"
                else "Phần phương pháp hoàn toàn tổng hợp chưa nêu số lần lặp."
            )
        ],
        "kind": "RESEARCH_REPORT_REVIEW_DRAFT",
        "reportRef": reference,
        "requiresHumanReview": True,
        "reviewSummary": (
            f"Advisory review of the fully synthetic {topic} report."
            if language == "EN"
            else f"Rà soát tư vấn cho báo cáo hoàn toàn tổng hợp về {topic}."
        ),
        "suggestions": [
            (
                "State the replicate count and separate observation from interpretation."
                if language == "EN"
                else "Nêu số lần lặp và tách quan sát khỏi diễn giải."
            )
        ],
    }


def _tool(config: dict[str, Any], scenario: str, use_case: str) -> dict[str, Any]:
    declared = {
        "group": config["toolGroup"],
        "intent": config["toolIntent"],
        "kind": "REQUEST",
        "name": config["toolName"],
    }
    if scenario == "authorizedDeclaredTool":
        return declared
    if scenario in {
        "authorizedNoToolNoCitations",
        "authorizedNoToolWithCitations",
        "authorizationNoTool",
        "nullContext",
    }:
        return {"kind": "NONE"}
    if scenario == "authorizationRejected":
        return {
            **declared,
            "kind": "REJECTED",
            "reason": "PROHIBITED",
        }
    if scenario == "promptInjectionRejected":
        return {
            "group": "UNKNOWN",
            "intent": f"Reject one untrusted synthetic instruction for {use_case}.",
            "kind": "REJECTED",
            "name": "synthetic.input.instruction.reject",
            "reason": "PROHIBITED",
        }
    return {
        "group": "UNKNOWN",
        "intent": f"Reject one undeclared synthetic route for {use_case}.",
        "kind": "REJECTED",
        "name": "synthetic.route.reject",
        "reason": "UNKNOWN_TOOL",
    }


def _prompt_text(use_case: str, scenario: str, topic: str, language: str) -> str:
    action = {
        "RESEARCH_UC_003": ("summarize an assigned task", "tóm tắt một nhiệm vụ được giao"),
        "RESEARCH_UC_004": ("prepare a task proposal draft", "soạn một đề xuất nhiệm vụ nháp"),
        "RESEARCH_UC_005": ("prepare a task suggestion draft", "soạn một gợi ý nhiệm vụ nháp"),
        "RESEARCH_UC_006": ("prepare an advisory report review draft", "soạn một bản rà soát báo cáo tư vấn"),
    }[use_case][0 if language == "EN" else 1]
    suffix = {
        "authorizedNoToolNoCitations": (
            "from authorized context, with no tool and no citation list",
            "từ ngữ cảnh được cấp quyền, không dùng công cụ và không tạo danh sách trích dẫn",
        ),
        "authorizedNoToolWithCitations": (
            "from authorized context, with no tool and only the declared citation",
            "từ ngữ cảnh được cấp quyền, không dùng công cụ và chỉ dùng trích dẫn đã khai báo",
        ),
        "authorizedDeclaredTool": (
            "from authorized context using only the declared tool",
            "từ ngữ cảnh được cấp quyền và chỉ dùng công cụ đã khai báo",
        ),
        "authorizationRejected": (
            "while access is explicitly denied",
            "khi quyền truy cập bị từ chối rõ ràng",
        ),
        "authorizationNoTool": (
            "without authorization and without a tool",
            "khi không có quyền và không dùng công cụ",
        ),
        "promptInjectionRejected": (
            "while rejecting an embedded instruction to bypass authorization",
            "đồng thời từ chối chỉ dẫn nhúng yêu cầu bỏ qua phân quyền",
        ),
        "unsupportedRouteRejected": (
            "while rejecting an undeclared route",
            "đồng thời từ chối tuyến chưa khai báo",
        ),
        "nullContext": (
            "when no authorized context exists",
            "khi không có ngữ cảnh được cấp quyền",
        ),
    }[scenario][0 if language == "EN" else 1]
    return (
        f"Please {action} for {topic} {suffix}."
        if language == "EN"
        else f"Hãy {action} cho {topic} {suffix}."
    )


def _response(
    use_case: str, scenario: str, topic: str, language: str
) -> tuple[str, list[str], str, str]:
    success = scenario.startswith("authorizedNoTool") or scenario == "authorizedDeclaredTool"
    if success and use_case == "RESEARCH_UC_003":
        text = (
            f"The authorized synthetic assignment concerns {topic}."
            if language == "EN"
            else f"Nhiệm vụ tổng hợp được cấp quyền liên quan đến {topic}."
        )
        return "ANSWER", [], text, "SUCCESS"
    if success:
        text = (
            "Here is a non-official draft that requires human review."
            if language == "EN"
            else "Đây là bản nháp không chính thức cần con người rà soát."
        )
        return "DRAFT_PRESENTATION", ["HUMAN_REVIEW_NEEDED"], text, "SUCCESS"
    if scenario == "nullContext":
        text = (
            "Authorized context is unavailable, so no result is produced."
            if language == "EN"
            else "Không có ngữ cảnh được cấp quyền nên không tạo kết quả."
        )
        return "NO_CONTEXT_NOTICE", ["CONTEXT_UNAVAILABLE"], text, "NO_CONTEXT"
    text = (
        "The request is refused; no protected context is disclosed and no action is executed."
        if language == "EN"
        else "Yêu cầu bị từ chối; không tiết lộ ngữ cảnh được bảo vệ và không thực thi hành động."
    )
    return "SAFE_REFUSAL", ["NO_DISCLOSURE", "NO_EXECUTION"], text, "DENY"


def _record(use_case: str, scenario: str, language: str, local_index: int) -> dict[str, Any]:
    config = USE_CASES[use_case]
    topic_index = _topic_index(scenario, local_index)
    topic = TOPICS[language][topic_index]
    reference = _reference(use_case, language, topic_index)
    success = scenario.startswith("authorizedNoTool") or scenario == "authorizedDeclaredTool"
    context = None
    if scenario != "nullContext":
        context = {
            "authorization": {"state": "GRANTED" if success else "DENIED"},
            config["refKey"]: {
                "resourceId": reference,
                "resourceType": config["recordType"],
            },
            "syntheticTopic": topic,
        }
    cited = success and scenario != "authorizedNoToolNoCitations"
    references = [reference] if cited else []
    mode, markers, text, behavior = _response(use_case, scenario, topic, language)
    tool = _tool(config, scenario, use_case)
    structured = _structured_output(use_case, reference, topic, language) if success else None
    risk = "READ_ONLY" if use_case == "RESEARCH_UC_003" else "DRAFT_ONLY"
    if not success and scenario != "nullContext":
        risk = "PROHIBITED"
    case_id = f"TRAIN-P7T4R5-{use_case[-3:]}-{language}-{scenario}-{local_index:02d}"
    return {
        "assistantKey": "RESEARCH_ASSISTANT",
        "domain": "RESEARCH",
        "governance": {
            "categoryIds": [config["category"]],
            "fullySynthetic": True,
            "permittedPurpose": "DEVELOPMENT_TEST",
        },
        "metadata": {
            "evaluationDerived": False,
            "language": language,
            "scenario": scenario,
            "synthetic": True,
        },
        "recordId": f"record-p7t4r5-{use_case[-3:].lower()}-{language.lower()}-{scenario}-{local_index:02d}",
        "recordType": config["recordType"],
        "schemaVersion": "5.0.0",
        "trainingPrompt": {
            "allowedTool": (
                _tool(config, "authorizedDeclaredTool", use_case)
                if scenario == "authorizedDeclaredTool"
                else None
            ),
            "assistantKey": "RESEARCH_ASSISTANT",
            "authorizedContext": context,
            "caseState": scenario,
            "evalCaseId": case_id,
            "input": _prompt_text(use_case, scenario, topic, language),
            "referencedContextIds": references,
            "rejectedTool": tool if tool["kind"] == "REJECTED" else None,
            "responseContract": {
                "language": language,
                "markers": markers,
                "mode": mode,
            },
            "structuredOutputContract": config["outputKind"] if success else None,
            "useCaseId": use_case,
        },
        "trainingTarget": {
            "evalCaseId": case_id,
            "observedActionRisk": risk,
            "observedBehavior": behavior,
            "referencedContextIds": references,
            "response": {
                "language": language,
                "markers": markers,
                "mode": mode,
                "text": text,
            },
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
            for language in ("EN", "VI"):
                for local_index in range(1, total // 2 + 1):
                    records.append(_record(use_case, scenario, language, local_index))
    return records


def validate_records(records: list[dict[str, Any]]) -> None:
    evaluator = _load_evaluator()
    frozen_case_ids, frozen_prompts = _frozen_evaluation_contract()
    diagnostics: list[str] = []
    ids = [record.get("recordId") for record in records]
    if len(records) != 192 or len(ids) != len(set(ids)):
        diagnostics.append("records: exact unique 192-record inventory required")
    if Counter(record.get("useCaseId") for record in records) != Counter(
        {use_case: 48 for use_case in USE_CASES}
    ):
        diagnostics.append("records: exact 48-per-use-case distribution required")
    if Counter(record.get("metadata", {}).get("language") for record in records) != Counter(
        {"EN": 96, "VI": 96}
    ):
        diagnostics.append("records: exact bilingual balance required")
    scenarios: dict[str, Counter[str]] = defaultdict(Counter)
    for index, record in enumerate(records):
        prompt = record.get("trainingPrompt", {})
        target = record.get("trainingTarget", {})
        scenarios[record.get("useCaseId")][record.get("metadata", {}).get("scenario")] += 1
        if record.get("metadata", {}).get("evaluationDerived") is not False:
            diagnostics.append(f"records/{index}: evaluation-derived content forbidden")
        if prompt.get("evalCaseId") in frozen_case_ids or str(
            prompt.get("evalCaseId", "")
        ).startswith("E-"):
            diagnostics.append(f"records/{index}: frozen evaluation identifier forbidden")
        if prompt.get("input") in frozen_prompts:
            diagnostics.append(f"records/{index}: exact frozen evaluation prompt forbidden")
        if evaluator.validate_tool(target.get("toolRequest")):
            diagnostics.append(f"records/{index}: tool envelope invalid")
        if evaluator.validate_response(target.get("response"), prompt.get("responseContract")):
            diagnostics.append(f"records/{index}: response contract invalid")
        if evaluator.validate_output(
            target.get("structuredOutput"), prompt.get("structuredOutputContract")
        ):
            diagnostics.append(f"records/{index}: structured output invalid")
        if target.get("referencedContextIds") != prompt.get("referencedContextIds"):
            diagnostics.append(f"records/{index}: reference declaration mismatch")
        state = prompt.get("caseState")
        authorization = (prompt.get("authorizedContext") or {}).get("authorization", {}).get("state")
        if state.startswith("authorized") and authorization != "GRANTED":
            diagnostics.append(f"records/{index}: authorized contrast mislabeled")
        if state in {"authorizationRejected", "authorizationNoTool"} and authorization != "DENIED":
            diagnostics.append(f"records/{index}: denied contrast mislabeled")
    if any(dict(counts) != SCENARIO_COUNTS for counts in scenarios.values()):
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
    languages = {
        record["trainingPrompt"]["evalCaseId"]: record["metadata"]["language"]
        for record in records
    }
    for record in map(_dataset_record, records):
        groups[
            (record["useCaseId"], languages[record["trainingPrompt"]["evalCaseId"]])
        ].append(record)
    splits = {"train": [], "validation": [], "evaluation": []}
    for values in groups.values():
        ordered = sorted(values, key=lambda item: item["contentId"])
        splits["train"].extend(ordered[:18])
        splits["validation"].extend(ordered[18:21])
        splits["evaluation"].extend(ordered[21:24])
    for values in splits.values():
        values.sort(key=lambda item: item["contentId"])
    return splits


def build_documents() -> dict[str, Any]:
    approval, quality, evaluator_suite_approval = _authorities()
    approved_artifacts = evaluator_suite_approval["approvedArtifacts"]
    records = generate_records()
    validate_records(records)
    content_identity = sha256_bytes(canonical_bytes(records))
    contract: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V5-TRAINING-CONTRACT",
        "schemaVersion": "5.0.0",
        "state": "PREPARED_AWAITING_TRAINING_APPROVAL",
        "assistantKey": "RESEARCH_ASSISTANT",
        "datasetVersion": "5.0.0",
        "recordCount": 192,
        "evaluatorImplementationReference": EVALUATOR_REFERENCE,
        "evaluatorReference": approved_artifacts["evaluatorReference"],
        "evaluatorIdentity": approved_artifacts["evaluatorIdentity"],
        "evaluationSuiteReference": approved_artifacts["suiteReference"],
        "evaluationSuiteIdentity": approved_artifacts["suiteIdentity"],
        "evaluatorSuiteApprovalReference": EVALUATOR_SUITE_APPROVAL_REFERENCE,
        "evaluatorSuiteApprovalIdentity": evaluator_suite_approval[
            "artifactIdentity"
        ],
        "qualitySpecificationReference": QUALITY_REFERENCE,
        "trainingAuthorized": False,
        "externalTrainingAllowed": False,
        "frozenEvaluationUseAllowed": False,
        "runtimeNormalizationAllowed": False,
        "artifactIdentity": "",
    }
    contract["artifactIdentity"] = artifact_identity(contract)
    provenance: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V5-SOURCE-PROVENANCE",
        "schemaVersion": "5.0.0",
        "sourceId": "p7-t4-research-remediation-source",
        "sourceVersion": "5.0.0",
        "contentIdentity": content_identity,
        "recordCount": 192,
        "governanceApprovalReference": GOVERNANCE_APPROVAL_REFERENCE,
        "governanceApprovalIdentity": approval["artifactIdentity"],
        "evaluatorSuiteApprovalReference": EVALUATOR_SUITE_APPROVAL_REFERENCE,
        "evaluatorSuiteApprovalIdentity": evaluator_suite_approval[
            "artifactIdentity"
        ],
        "evaluatorIdentity": approved_artifacts["evaluatorIdentity"],
        "evaluationSuiteIdentity": approved_artifacts["suiteIdentity"],
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
        "exportSchemaVersion": "5.0.0",
        "source": {
            "sourceId": "p7-t4-research-remediation-source",
            "sourceVersion": "5.0.0",
            "status": "PREPARED_AWAITING_TRAINING_APPROVAL",
            "recordCount": 192,
            "contentIdentity": content_identity,
            "approvalReference": GOVERNANCE_APPROVAL_REFERENCE,
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
        "dataset_version": "5.0.0",
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
            "sourceRecords": 192,
            "acceptedRecords": 192,
            "rejectedRecords": 0,
            "splits": {name: len(values) for name, values in splits.items()},
        },
        "artifacts": [
            {
                "filename": f"{name}.jsonl",
                "recordCount": len(splits.get(name, [])),
                "sha256": sha256_bytes(content),
            }
            for name, content in sorted(split_bytes.items())
        ],
        "split_configuration": {
            "strategy": "CONTENT_ID_ORDERED_STRATIFIED",
            "perUseCaseLanguage": {"train": 18, "validation": 3, "evaluation": 3},
        },
        "contractHoldout": {
            "split": "evaluation",
            "usedForOptimization": False,
            "frozenP7T4Evaluation": False,
        },
        "manifestIdentity": "",
    }
    manifest["manifestIdentity"] = artifact_identity(manifest, "manifestIdentity")
    card = {
        "dataset_id": "p7-research-synthetic-training-dataset",
        "dataset_version": "5.0.0",
        "assistant_key": "RESEARCH_ASSISTANT",
        "title": "Pending P7-T4 remediation v5 synthetic Research dataset",
        "category_ids": sorted({config["category"] for config in USE_CASES.values()}),
        "use_decision": "SYNTHETIC_ONLY",
        "source_permission_status": "VERIFIED_FOR_DATASET_PREPARATION_ONLY",
        "source_permission_references": [GOVERNANCE_APPROVAL_REFERENCE],
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
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V5-TRAINING-GOVERNANCE-REQUEST",
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
        "approvalAuthority": "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY",
        "dataset": {
            "datasetId": "p7-research-synthetic-training-dataset",
            "datasetVersion": "5.0.0",
            "manifestReference": MANIFEST_REFERENCE,
            "manifestIdentity": manifest["manifestIdentity"],
            "recordCount": 192,
        },
        "source": {
            "reference": SOURCE_REFERENCE,
            "sha256": sha256_bytes(json_bytes(source)),
            "contentIdentity": content_identity,
            "provenanceIdentity": provenance["artifactIdentity"],
            "contractIdentity": contract["artifactIdentity"],
        },
        "evaluatorSuiteApprovalReference": EVALUATOR_SUITE_APPROVAL_REFERENCE,
        "evaluatorSuiteApprovalIdentity": evaluator_suite_approval[
            "artifactIdentity"
        ],
        "evaluatorReference": approved_artifacts["evaluatorReference"],
        "evaluatorIdentity": approved_artifacts["evaluatorIdentity"],
        "evaluationSuiteReference": approved_artifacts["suiteReference"],
        "evaluationSuiteIdentity": approved_artifacts["suiteIdentity"],
        "trainingAuthorized": False,
        "externalTrainingAllowed": False,
        "runtimeNormalizationAllowed": False,
        "requestIdentity": "",
    }
    request["requestIdentity"] = artifact_identity(request, "requestIdentity")
    return {
        "source": source,
        "provenance": provenance,
        "contract": contract,
        "manifest": manifest,
        "card": card,
        "trainingRequest": request,
        "splits": splits,
        "splitBytes": split_bytes,
    }


def build_artifacts() -> dict[str, bytes]:
    documents = build_documents()
    artifacts = {
        f"{SOURCE_REFERENCE}": json_bytes(documents["source"]),
        f"{PROVENANCE_REFERENCE}": json_bytes(documents["provenance"]),
        f"{CONTRACT_REFERENCE}": json_bytes(documents["contract"]),
        f"{MANIFEST_REFERENCE}": json_bytes(documents["manifest"]),
        f"{CARD_REFERENCE}": json_bytes(documents["card"]),
        (
            "config/p7-t4-research-remediation-governance-v5/"
            "training-approval-request.json"
        ): json_bytes(documents["trainingRequest"]),
    }
    for name, content in documents["splitBytes"].items():
        artifacts[
            f"datasets/p7-research-synthetic-training-dataset-v5/{name}.jsonl"
        ] = content
    return artifacts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.parse_args()
    artifacts = build_artifacts()
    for relative_path, content in artifacts.items():
        path = ROOT / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
    documents = build_documents()
    print(
        json.dumps(
            {
                "state": "PREPARED_AWAITING_TRAINING_APPROVAL",
                "datasetVersion": "5.0.0",
                "recordCount": 192,
                "manifestIdentity": documents["manifest"]["manifestIdentity"],
                "trainingRequestIdentity": documents["trainingRequest"]["requestIdentity"],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
