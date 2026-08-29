#!/usr/bin/env python3
"""Build the governed report-review groundedness remediation-v9 dataset."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
V8_DATASET_ROOT = "datasets/p7-research-synthetic-training-dataset-v8"
DATASET_ROOT = "datasets/p7-research-synthetic-training-dataset-v9"
SOURCE_ROOT = "datasets/p7-t4-research-remediation-source-v9"
CONFIG_ROOT = "config/p7-t4-research-remediation-governance-v9"
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v9-governance-approval.json"
PREPARATION_APPROVAL_IDENTITY = "a0987f0dc499b489838b854d0ca01f5aaa25fb144235c75e5577d0e548638440"
QUALITY_IDENTITY = "2f49d629fd1620183c35ef33bcbeb154dee32b998b3d1775de720cacee3268fc"
V8_DATASET_IDENTITY = "44fb5a05d9a13c4f6e8d39fe7b8fe5a9189dc077b9850da700a17ea76e609b3d"
PROMPT_PROFILE_IDENTITY = "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
RETAINED_COUNTS = {"evaluation": 72, "train": 432, "validation": 72}
ADDITION_COUNTS = {"evaluation": 8, "train": 48, "validation": 8}
RECORD_COUNTS = {"evaluation": 80, "train": 480, "validation": 80}
SCENARIO_FAMILIES = (
    "SYNTHETIC_ADVISORY_REPORT_REVIEW_GROUNDEDNESS",
    "SYNTHETIC_BOUNDED_OBSERVATION_NOT_REPETITION",
    "SYNTHETIC_LIMITATION_AND_EVIDENCE_LINK_REVIEW",
    "SYNTHETIC_UNKNOWN_VS_CONFIRMED_MISSING",
)
TOPICS = (
    "quan sát phổ có giới hạn",
    "ghi chú hiệu chuẩn synthetic",
    "tổng hợp sai số được kiểm soát",
    "quy trình lấy mẫu mô phỏng",
    "nhật ký quan sát có phạm vi",
    "bản ghi ngưỡng synthetic",
)
RECORD_FIELDS = {
    "assistantKey", "contentId", "curriculumSegment", "domain", "recordType",
    "scenarioFamily", "schemaVersion", "semanticFamily",
    "targetedEvaluationCaseId", "trainingPrompt", "trainingTarget", "useCaseId",
    "visibility",
}
TARGET_FIELDS = {
    "evalCaseId", "observedActionRisk", "observedBehavior",
    "referencedContextIds", "response", "structuredOutput", "toolRequest",
}


class DatasetBuildError(ValueError):
    pass


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
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


def _load_json(relative_path: str) -> dict[str, Any]:
    value = json.loads((ROOT / relative_path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise DatasetBuildError(f"object required: {relative_path}")
    return value


def _load_jsonl(relative_path: str) -> list[dict[str, Any]]:
    values = [
        json.loads(line)
        for line in (ROOT / relative_path).read_text(encoding="utf-8").splitlines()
    ]
    if any(not isinstance(value, dict) for value in values):
        raise DatasetBuildError(f"object records required: {relative_path}")
    return values


def _validate_approval_boundary() -> None:
    approval = _load_json(APPROVAL_REFERENCE)
    quality = _load_json(f"{CONFIG_ROOT}/training-data-quality-spec-v9.json")
    manifest = _load_json(f"{V8_DATASET_ROOT}/manifest.json")
    authorization = approval.get("authorization", {})
    if (
        approval.get("artifactIdentity") != PREPARATION_APPROVAL_IDENTITY
        or artifact_identity(approval) != PREPARATION_APPROVAL_IDENTITY
        or approval.get("status") != "APPROVED"
        or authorization.get("datasetV9PreparationAllowed") is not True
        or authorization.get("approvedV8RetentionReuseAllowed") is not True
        or authorization.get("externalTrainingAllowed") is not False
        or authorization.get("externalEvaluationExecutionAllowed") is not False
        or quality.get("artifactIdentity") != QUALITY_IDENTITY
        or quality.get("status") != "PENDING_GOVERNANCE_APPROVAL"
        or quality.get("targetedFailureControls", {}).get("targetedCaseIds")
        != ["E-FUNC-RESEARCH-006"]
        or manifest.get("datasetIdentity") != V8_DATASET_IDENTITY
        or manifest.get("recordCounts") != RETAINED_COUNTS
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise DatasetBuildError("exact approved remediation-v9 boundary required")
    inventory = {item["filename"]: item for item in manifest.get("artifacts", [])}
    for split in ("train", "validation", "evaluation"):
        path = ROOT / V8_DATASET_ROOT / f"{split}.jsonl"
        item = inventory.get(f"{split}.jsonl", {})
        if (
            item.get("recordCount") != RETAINED_COUNTS[split]
            or item.get("sha256") != sha256_bytes(path.read_bytes())
        ):
            raise DatasetBuildError(f"v8 {split} retention source mismatch")


def _targeted_record(split: str, index: int) -> dict[str, Any]:
    scenario = SCENARIO_FAMILIES[index % len(SCENARIO_FAMILIES)]
    topic = TOPICS[(index // 2) % len(TOPICS)]
    sample_id = f"{split[:3]}-{index + 1:02d}"
    eval_case_id = f"SYN-V9-{split.upper()}-REPORT-REVIEW-{index + 1:02d}"
    report_ref = f"synthetic-v9-report-{sample_id}"
    project_ref = f"synthetic-v9-project-{sample_id}"
    group_ref = f"synthetic-v9-group-{sample_id}"
    context_ids = [group_ref, project_ref, report_ref]
    prompt = {
        "allowedTool": None,
        "assistantKey": "RESEARCH_ASSISTANT",
        "authorizedContext": {
            "groupRef": group_ref,
            "projectRef": project_ref,
            "reportRef": report_ref,
            "reportSummary": (
                "Báo cáo synthetic mô tả các quan sát có phạm vi và yêu cầu "
                "làm rõ giới hạn cùng liên kết bằng chứng."
            ),
            "syntheticScenario": scenario,
        },
        "caseState": "ACTIVE",
        "evalCaseId": eval_case_id,
        "input": (
            f"Soạn bản nháp nhận xét tư vấn cho báo cáo synthetic về {topic}. "
            "Chỉ dùng ngữ cảnh được cấp; không suy diễn số lượng phép đo hoặc "
            "thông tin chưa được cung cấp."
        ),
        "p6t3Root": "research",
        "referencedContextIds": context_ids,
        "rejectedTool": None,
        "responseContract": {
            "language": "VI",
            "markers": ["HUMAN_REVIEW_NEEDED"],
            "mode": "DRAFT_PRESENTATION",
        },
        "structuredOutputContract": "RESEARCH_REPORT_REVIEW_DRAFT",
        "suiteTags": ["FUNCTIONAL", "STRUCTURED_OUTPUT", "HUMAN_EVAL"],
        "useCaseId": "RESEARCH_UC_006",
    }
    target = {
        "evalCaseId": eval_case_id,
        "observedActionRisk": "DRAFT_ONLY",
        "observedBehavior": "SUCCESS",
        "referencedContextIds": context_ids,
        "response": {
            "language": "VI",
            "markers": ["HUMAN_REVIEW_NEEDED"],
            "mode": "DRAFT_PRESENTATION",
            "text": (
                "Bản nháp tư vấn xác định phần giới hạn và liên kết bằng chứng "
                "cần được làm rõ trước khi con người phê duyệt."
            ),
        },
        "structuredOutput": {
            "advisoryOnly": True,
            "issues": [
                "Báo cáo chưa trình bày rõ các giới hạn và mối liên kết giữa "
                "kết luận với bằng chứng được cung cấp."
            ],
            "kind": "RESEARCH_REPORT_REVIEW_DRAFT",
            "reportRef": report_ref,
            "requiresHumanReview": True,
            "reviewSummary": (
                "Bản nháp tư vấn chỉ ghi nhận những điểm được hỗ trợ bởi ngữ "
                "cảnh synthetic đã cung cấp."
            ),
            "suggestions": [
                "Trình bày rõ các giới hạn và liên kết từng kết luận với bằng "
                "chứng được cung cấp."
            ],
        },
        "toolRequest": {"kind": "NONE"},
    }
    record: dict[str, Any] = {
        "assistantKey": "RESEARCH_ASSISTANT",
        "contentId": "",
        "curriculumSegment": "HUMAN_SEMANTIC_QUALITY_REMEDIATION",
        "domain": "RESEARCH",
        "recordType": "REPORT_REVIEW_DRAFT",
        "scenarioFamily": scenario,
        "schemaVersion": "9.0.0",
        "semanticFamily": "SYNTHETIC_REPORT_REVIEW_GROUNDEDNESS_REMEDIATION",
        "targetedEvaluationCaseId": "E-FUNC-RESEARCH-006",
        "trainingPrompt": prompt,
        "trainingTarget": target,
        "useCaseId": "RESEARCH_UC_006",
        "visibility": "RESEARCH_ASSISTANT_ONLY",
    }
    record["contentId"] = artifact_identity(record, "contentId")
    return record


def validate_record(record: dict[str, Any]) -> list[str]:
    if set(record) != RECORD_FIELDS:
        return ["record fields are not closed"]
    prompt = record.get("trainingPrompt")
    target = record.get("trainingTarget")
    findings: list[str] = []
    if (
        record.get("schemaVersion") != "9.0.0"
        or record.get("assistantKey") != "RESEARCH_ASSISTANT"
        or record.get("domain") != "RESEARCH"
        or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY"
        or record.get("useCaseId") != "RESEARCH_UC_006"
        or record.get("targetedEvaluationCaseId") != "E-FUNC-RESEARCH-006"
        or record.get("scenarioFamily") not in SCENARIO_FAMILIES
        or artifact_identity(record, "contentId") != record.get("contentId")
    ):
        findings.append("record identity or boundary mismatch")
    if not isinstance(prompt, dict) or not isinstance(target, dict) or set(target) != TARGET_FIELDS:
        return findings + ["prompt/target closed contract mismatch"]
    output = target.get("structuredOutput")
    if (
        target.get("evalCaseId") != prompt.get("evalCaseId")
        or target.get("evalCaseId") == "E-FUNC-RESEARCH-006"
        or target.get("referencedContextIds") != prompt.get("referencedContextIds")
        or target.get("toolRequest") != {"kind": "NONE"}
        or prompt.get("allowedTool") is not None
        or prompt.get("rejectedTool") is not None
        or prompt.get("structuredOutputContract") != "RESEARCH_REPORT_REVIEW_DRAFT"
    ):
        findings.append("grounded report-review binding mismatch")
    response = target.get("response")
    if (
        not isinstance(response, dict)
        or {key: response.get(key) for key in ("language", "markers", "mode")}
        != prompt.get("responseContract")
        or response.get("mode") != "DRAFT_PRESENTATION"
        or response.get("markers") != ["HUMAN_REVIEW_NEEDED"]
        or target.get("observedBehavior") != "SUCCESS"
        or target.get("observedActionRisk") != "DRAFT_ONLY"
    ):
        findings.append("draft response mismatch")
    output_fields = {
        "advisoryOnly", "issues", "kind", "reportRef", "requiresHumanReview",
        "reviewSummary", "suggestions",
    }
    if not isinstance(output, dict) or set(output) != output_fields:
        findings.append("report-review output fields mismatch")
    else:
        combined = " ".join(
            [output["reviewSummary"], *output["issues"], *output["suggestions"]]
        ).lower()
        if (
            output.get("kind") != "RESEARCH_REPORT_REVIEW_DRAFT"
            or output.get("advisoryOnly") is not True
            or output.get("requiresHumanReview") is not True
            or output.get("reportRef") != prompt["authorizedContext"]["reportRef"]
            or "giới hạn" not in combined
            or "bằng chứng" not in combined
            or "số lần lặp" in combined
            or "repetition count" in combined
        ):
            findings.append("report-review groundedness mismatch")
    return findings


def build_records() -> dict[str, list[dict[str, Any]]]:
    _validate_approval_boundary()
    records: dict[str, list[dict[str, Any]]] = {}
    for split in ("train", "validation", "evaluation"):
        retained = _load_jsonl(f"{V8_DATASET_ROOT}/{split}.jsonl")
        additions = [_targeted_record(split, index) for index in range(ADDITION_COUNTS[split])]
        for record in additions:
            findings = validate_record(record)
            if findings:
                raise DatasetBuildError("; ".join(findings))
        records[split] = retained + additions
    all_ids = [record["contentId"] for values in records.values() for record in values]
    if len(all_ids) != len(set(all_ids)):
        raise DatasetBuildError("split content IDs must be disjoint")
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
            "recordCount": len(records.get(Path(path).stem, [])),
            "sha256": sha256_bytes(content),
        }
        for path, content in sorted(split_artifacts.items())
    ]
    dataset_identity = sha256_bytes(canonical_bytes(inventory))
    manifest: dict[str, Any] = {
        "approval_status": "PENDING_TRAINING_APPROVAL",
        "artifacts": inventory,
        "baseDatasetIdentity": V8_DATASET_IDENTITY,
        "contractHoldout": {"recordCount": 80, "split": "evaluation", "usedForOptimization": False},
        "datasetIdentity": dataset_identity,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "qualityIdentity": QUALITY_IDENTITY,
        "recordCounts": RECORD_COUNTS,
        "retainedRecordCounts": RETAINED_COUNTS,
        "schemaVersion": "9.0.0",
        "status": "PENDING_TRAINING_APPROVAL",
        "targetedAdditionCounts": ADDITION_COUNTS,
        "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"],
        "trainingAuthorized": False,
    }
    manifest["artifactIdentity"] = artifact_identity(manifest)
    provenance: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V9-SYNTHETIC-PROVENANCE",
        "baseDatasetIdentity": V8_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "generatorReference": "scripts/build-p7-t4-research-remediation-source-v9.py",
        "independentTargetedSyntheticGeneration": True,
        "noFrozenEvaluationContentCopied": True,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "retainedV8RecordsUnchanged": True,
        "schemaVersion": "1.0.0",
        "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"],
    }
    provenance["artifactIdentity"] = artifact_identity(provenance)
    contract: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V9-TRAINING-CONTRACT",
        "baseDatasetIdentity": V8_DATASET_IDENTITY,
        "constrainedDecodingAllowed": False,
        "datasetIdentity": dataset_identity,
        "evaluationRecordCount": 80,
        "frozenEvaluationUseAllowed": False,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "runtimeNormalizationAllowed": False,
        "schemaVersion": "1.0.0",
        "trainingRecordCount": 480,
        "validationRecordCount": 80,
    }
    contract["artifactIdentity"] = artifact_identity(contract)
    card: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V9-DATASET-CARD",
        "baseDatasetIdentity": V8_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "disposition": "PENDING_TRAINING_APPROVAL",
        "recordCounts": RECORD_COUNTS,
        "remediationPriority": "SINGLE_HUMAN_SEMANTIC_QUALITY_FINDING_DATASET_ONLY",
        "schemaVersion": "1.0.0",
        "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"],
    }
    card["artifactIdentity"] = artifact_identity(card)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY",
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V9-TRAINING-APPROVAL-REQUEST",
        "datasetIdentity": dataset_identity,
        "datasetManifestReference": f"{DATASET_ROOT}/manifest.json",
        "externalTrainingAllowed": False,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "preparationApprovalReference": APPROVAL_REFERENCE,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "qualityIdentity": QUALITY_IDENTITY,
        "requestId": "P7-T1C-RESEARCH-REMEDIATION-V9-TRAINING-APPROVAL-REQUEST-001",
        "requestedScope": {
            "datasetVersion": "9.0.0",
            "externalSingleT4Training": True,
            "freshBaseModelStartRequired": True,
            "retainedV8RecordsUnchanged": True,
            "syntheticHoldoutInferenceGateRequired": True,
            "targetedEvaluationCaseIds": ["E-FUNC-RESEARCH-006"],
            "trainingMethod": "QLORA",
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
        "trainingAuthorized": False,
    }
    request["requestIdentity"] = artifact_identity(request, "requestIdentity")
    source_export: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V9-SOURCE-EXPORT",
        "baseDatasetIdentity": V8_DATASET_IDENTITY,
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
    return artifacts


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
        print(json.dumps({"requestIdentity": request["requestIdentity"], "state": request["status"]}, sort_keys=True))
        return 0
    except (DatasetBuildError, OSError, ValueError) as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
