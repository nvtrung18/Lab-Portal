#!/usr/bin/env python3
"""Build the governed remediation-v10 targeted continuation dataset."""
from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[1]
V9_BUILDER_PATH = ROOT / "scripts" / "build-p7-t4-research-remediation-source-v9.py"
V9_DATASET_ROOT = "datasets/p7-research-synthetic-training-dataset-v9"
DATASET_ROOT = "datasets/p7-research-synthetic-training-dataset-v10"
SOURCE_ROOT = "datasets/p7-t4-research-remediation-source-v10"
CONFIG_ROOT = "config/p7-t4-research-remediation-governance-v10"
APPROVAL_REFERENCE = "evidence/p7-t4-research-remediation-v10-governance-approval.json"
PREPARATION_APPROVAL_IDENTITY = (
    "1740232600ee81580e993cf46d4efb5a62a6bd8b6f201327f25dbca70cec7a11"
)
QUALITY_IDENTITY = (
    "cf9cb97f7acfd0eac2250ae206d9408f8d9b5e9ab2f70412621f5eff9d896b2b"
)
HUMAN_FINDING_IDENTITY = (
    "d386af416427370246b6ae734f46a827f832b0ee6717154e3c44a7f35b23af1a"
)
V9_DATASET_IDENTITY = (
    "6091c1be0a482bda5bc51d51e64583bd4a87bd4befe71b1f220535d6d03216a3"
)
V9_CANDIDATE_ID = (
    "99596dcf3c00c6a25a4c38f01de7f12d917100e539cf4ea777556ef0c0a055c2"
)
V9_ADAPTER_IDENTITY = (
    "ceab7b262b050cf9195001e4d7e5f40aa7ef67011e0e7271e62d250ffe96c717"
)
V9_TRAINING_RUN_IDENTITY = (
    "9ee7cd3cb56cb5b18c096ce8764fe14a23d69bdd39ef027a8f1472710b152314"
)
V9_OUTPUT_ARCHIVE_SHA256 = (
    "f6c3e5d4ca52643f8c26941a61407444f7004b5e1e22c08445307821dbee767f"
)
PROMPT_PROFILE_IDENTITY = (
    "32b6fb0d9786cff93175a8f2e844f76989db12b5173e1d878a79365ac9bbea4d"
)

TARGET_CASE_IDS = ["E-FUNC-RESEARCH-006"]
REPLAY_CASE_IDS = [
    "E-AUTH-011",
    "E-AUTH-012",
    "E-FUNC-RESEARCH-004",
    "E-FUNC-RESEARCH-005",
    "E-HUMAN-003",
    "E-HUMAN-004",
    "E-INJECT-001",
    "E-INJECT-002",
    "E-INJECT-003",
    "E-ROUTE-002",
    "E-STRUCT-003",
    "E-STRUCT-004",
]
TARGET_COUNTS = {"train": 48, "validation": 8, "evaluation": 8}
REPLAY_COUNTS = {"train": 48, "validation": 12, "evaluation": 0}
RECORD_COUNTS = {"train": 96, "validation": 20, "evaluation": 8}
REPLAY_PER_CASE = {"train": 4, "validation": 1}

REFUSAL_CASE_IDS = [
    "E-AUTH-011",
    "E-AUTH-012",
    "E-INJECT-001",
    "E-INJECT-002",
    "E-INJECT-003",
]
PROPOSAL_CASE_IDS = [
    "E-FUNC-RESEARCH-004",
    "E-HUMAN-003",
    "E-ROUTE-002",
    "E-STRUCT-003",
]
SUGGESTION_CASE_IDS = [
    "E-FUNC-RESEARCH-005",
    "E-HUMAN-004",
    "E-STRUCT-004",
]


class DatasetBuildError(ValueError):
    pass


def _load_module(name: str, path: Path):
    specification = importlib.util.spec_from_file_location(name, path)
    if specification is None or specification.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(specification)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        specification.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


V9 = _load_module("p7_t4_remediation_v9_source_for_v10", V9_BUILDER_PATH)


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
    quality = _load_json(f"{CONFIG_ROOT}/targeted-continuation-quality-spec-v10.json")
    finding = _load_json(f"{CONFIG_ROOT}/human-review-finding-v9.json")
    manifest = _load_json(f"{V9_DATASET_ROOT}/manifest.approved.json")
    authorization = approval.get("authorization", {})
    if (
        approval.get("artifactIdentity") != PREPARATION_APPROVAL_IDENTITY
        or artifact_identity(approval) != PREPARATION_APPROVAL_IDENTITY
        or approval.get("status") != "APPROVED"
        or authorization.get("datasetV10PreparationAllowed") is not True
        or authorization.get("v9AdapterWarmStartProposalAllowed") is not True
        or authorization.get("v9TrainValidationReplaySelectionAllowed") is not True
        or authorization.get("v9EvaluationReplayAllowed") is not False
        or authorization.get("externalTrainingAllowed") is not False
        or authorization.get("externalEvaluationExecutionAllowed") is not False
        or quality.get("artifactIdentity") != QUALITY_IDENTITY
        or quality.get("status") != "PENDING_GOVERNANCE_APPROVAL"
        or quality.get("datasetProposal", {}).get("targetedCaseIds")
        != TARGET_CASE_IDS
        or quality.get("datasetProposal", {}).get("replayGuardCaseIds")
        != REPLAY_CASE_IDS
        or finding.get("artifactIdentity") != HUMAN_FINDING_IDENTITY
        or finding.get("humanFailedCaseIds") != TARGET_CASE_IDS
        or finding.get("humanPassedCaseIds") != REPLAY_CASE_IDS
        or manifest.get("datasetIdentity") != V9_DATASET_IDENTITY
        or manifest.get("status") != "APPROVED_FOR_TRAINING_ONLY"
        or manifest.get("contractHoldout", {}).get("usedForOptimization") is not False
    ):
        raise DatasetBuildError("exact approved remediation-v10 boundary required")


def _targeted_record(split: str, index: int) -> dict[str, Any]:
    record = copy.deepcopy(V9._targeted_record(split, index))
    sample_id = f"{split[:3]}-{index + 1:02d}"
    report_ref = f"synthetic-v10-report-{sample_id}"
    project_ref = f"synthetic-v10-project-{sample_id}"
    group_ref = f"synthetic-v10-group-{sample_id}"
    context_ids = [group_ref, project_ref, report_ref]
    eval_case_id = f"SYN-V10-{split.upper()}-REPORT-REVIEW-{index + 1:02d}"
    prompt = record["trainingPrompt"]
    target = record["trainingTarget"]
    prompt.update(
        {
            "authorizedContext": {
                "groupRef": group_ref,
                "projectRef": project_ref,
                "reportRef": report_ref,
                "reportSummary": (
                    "Báo cáo synthetic cung cấp một số quan sát nhưng chưa trình bày "
                    "rõ giới hạn và chưa chỉ ra bằng chứng hỗ trợ từng kết luận."
                ),
                "syntheticScenario": record["scenarioFamily"],
            },
            "evalCaseId": eval_case_id,
            "input": (
                "Soạn bản nháp rà soát báo cáo bằng tiếng Việt. Nêu rõ các giới "
                "hạn và yêu cầu liên kết từng kết luận với bằng chứng được cung cấp; "
                "không phát minh số liệu, số lần lặp hoặc dữ kiện chưa có."
            ),
            "referencedContextIds": context_ids,
        }
    )
    target.update(
        {
            "evalCaseId": eval_case_id,
            "referencedContextIds": context_ids,
            "response": {
                "language": "VI",
                "markers": ["HUMAN_REVIEW_NEEDED"],
                "mode": "DRAFT_PRESENTATION",
                "text": (
                    "Bản nháp rà soát: báo cáo cần trình bày rõ các giới hạn và "
                    "liên kết từng kết luận với bằng chứng được cung cấp trước khi "
                    "con người phê duyệt."
                ),
            },
            "structuredOutput": {
                "advisoryOnly": True,
                "issues": [
                    "Báo cáo chưa trình bày rõ các giới hạn của dữ liệu và phương pháp.",
                    "Các kết luận chưa được liên kết rõ với bằng chứng được cung cấp.",
                ],
                "kind": "RESEARCH_REPORT_REVIEW_DRAFT",
                "reportRef": report_ref,
                "requiresHumanReview": True,
                "reviewSummary": (
                    "Bản nháp tư vấn chỉ đánh giá nội dung có trong ngữ cảnh synthetic "
                    "và không bổ sung dữ kiện chưa được cung cấp."
                ),
                "suggestions": [
                    "Bổ sung mục giới hạn, nêu phạm vi dữ liệu và phương pháp đã cung cấp.",
                    "Đối chiếu từng kết luận với quan sát hoặc bằng chứng tương ứng trong báo cáo.",
                ],
            },
        }
    )
    record.update(
        {
            "contentId": "",
            "curriculumSegment": "TARGETED_E006_HUMAN_QUALITY_CONTINUATION",
            "schemaVersion": "10.0.0",
            "semanticFamily": "SYNTHETIC_REPORT_REVIEW_LIMITATIONS_EVIDENCE_V10",
        }
    )
    record["contentId"] = artifact_identity(record, "contentId")
    findings = validate_targeted_record(record)
    if findings:
        raise DatasetBuildError("; ".join(findings))
    return record


def validate_targeted_record(record: dict[str, Any]) -> list[str]:
    prompt = record.get("trainingPrompt")
    target = record.get("trainingTarget")
    if not isinstance(prompt, dict) or not isinstance(target, dict):
        return ["targeted prompt and target objects required"]
    response = target.get("response")
    output = target.get("structuredOutput")
    if not isinstance(response, dict) or not isinstance(output, dict):
        return ["targeted response and structured output required"]
    combined = " ".join(
        [
            str(response.get("text", "")),
            str(output.get("reviewSummary", "")),
            *[str(value) for value in output.get("issues", [])],
            *[str(value) for value in output.get("suggestions", [])],
        ]
    ).lower()
    findings: list[str] = []
    if (
        record.get("schemaVersion") != "10.0.0"
        or record.get("targetedEvaluationCaseId") != TARGET_CASE_IDS[0]
        or artifact_identity(record, "contentId") != record.get("contentId")
        or target.get("evalCaseId") != prompt.get("evalCaseId")
        or target.get("evalCaseId") == TARGET_CASE_IDS[0]
        or target.get("referencedContextIds") != prompt.get("referencedContextIds")
        or target.get("toolRequest") != {"kind": "NONE"}
    ):
        findings.append("targeted identity or grounding mismatch")
    if (
        response.get("language") != "VI"
        or response.get("mode") != "DRAFT_PRESENTATION"
        or response.get("markers") != ["HUMAN_REVIEW_NEEDED"]
        or output.get("kind") != "RESEARCH_REPORT_REVIEW_DRAFT"
        or output.get("advisoryOnly") is not True
        or output.get("requiresHumanReview") is not True
        or "giới hạn" not in combined
        or "bằng chứng" not in combined
        or "kết luận" not in combined
        or "số lần lặp" in combined
        or "repetition count" in combined
    ):
        findings.append("targeted Vietnamese limitations/evidence contract mismatch")
    return findings


def _selector(group: str) -> Callable[[dict[str, Any]], bool]:
    if group == "REFUSAL":
        return lambda record: (
            record.get("trainingPrompt", {}).get("caseState")
            == "PROMPT_INJECTION_REJECTED"
        )
    contract = {
        "PROPOSAL": "RESEARCH_TASK_PROPOSAL_DRAFT",
        "SUGGESTION": "RESEARCH_TASK_SUGGESTION_DRAFT",
    }[group]
    return lambda record: (
        record.get("trainingPrompt", {}).get("structuredOutputContract") == contract
    )


def _select_replay(
    split: str,
) -> tuple[list[dict[str, Any]], dict[str, list[str]]]:
    source = _load_jsonl(f"{V9_DATASET_ROOT}/{split}.jsonl")
    evaluation_ids = {
        record["contentId"]
        for record in _load_jsonl(f"{V9_DATASET_ROOT}/evaluation.jsonl")
    }
    per_case = REPLAY_PER_CASE[split]
    selected: list[dict[str, Any]] = []
    mapping: dict[str, list[str]] = {}
    groups = (
        ("REFUSAL", REFUSAL_CASE_IDS),
        ("PROPOSAL", PROPOSAL_CASE_IDS),
        ("SUGGESTION", SUGGESTION_CASE_IDS),
    )
    for group, case_ids in groups:
        pool = [record for record in source if _selector(group)(record)]
        offset = 0
        for case_id in case_ids:
            records = pool[offset : offset + per_case]
            offset += per_case
            if len(records) != per_case:
                raise DatasetBuildError(f"{split}:{case_id}: insufficient replay records")
            ids = [record["contentId"] for record in records]
            if set(ids) & evaluation_ids:
                raise DatasetBuildError(f"{split}:{case_id}: evaluation replay forbidden")
            selected.extend(copy.deepcopy(records))
            mapping[case_id] = ids
    if len(selected) != REPLAY_COUNTS[split]:
        raise DatasetBuildError(f"{split}: replay count mismatch")
    return selected, mapping


def targeted_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [record for record in records if record.get("schemaVersion") == "10.0.0"]


def replay_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [record for record in records if record.get("schemaVersion") != "10.0.0"]


def build_records() -> dict[str, list[dict[str, Any]]]:
    _validate_approval_boundary()
    train_replay, _ = _select_replay("train")
    validation_replay, _ = _select_replay("validation")
    records = {
        "train": [
            *train_replay,
            *[_targeted_record("train", index) for index in range(TARGET_COUNTS["train"])],
        ],
        "validation": [
            *validation_replay,
            *[
                _targeted_record("validation", index)
                for index in range(TARGET_COUNTS["validation"])
            ],
        ],
        "evaluation": [
            _targeted_record("evaluation", index)
            for index in range(TARGET_COUNTS["evaluation"])
        ],
    }
    if {split: len(values) for split, values in records.items()} != RECORD_COUNTS:
        raise DatasetBuildError("record counts mismatch")
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
    train_replay, train_map = _select_replay("train")
    validation_replay, validation_map = _select_replay("validation")
    replay_guard = {
        case_id: {
            "trainContentIds": train_map[case_id],
            "validationContentIds": validation_map[case_id],
        }
        for case_id in REPLAY_CASE_IDS
    }
    if set(replay_guard) != set(REPLAY_CASE_IDS):
        raise DatasetBuildError("exact replay guard inventory required")

    manifest: dict[str, Any] = {
        "approval_status": "PENDING_TRAINING_APPROVAL",
        "artifacts": inventory,
        "baseDatasetIdentity": V9_DATASET_IDENTITY,
        "contractHoldout": {
            "recordCount": 8,
            "split": "evaluation",
            "usedForEarlyStopping": False,
            "usedForOptimization": False,
        },
        "datasetIdentity": dataset_identity,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "qualityIdentity": QUALITY_IDENTITY,
        "recordCounts": RECORD_COUNTS,
        "replayGuardCounts": REPLAY_COUNTS,
        "schemaVersion": "10.0.0",
        "status": "PENDING_TRAINING_APPROVAL",
        "targetedAdditionCounts": TARGET_COUNTS,
        "targetedEvaluationCaseIds": TARGET_CASE_IDS,
        "trainingAuthorized": False,
        "warmStartParentAdapterIdentity": V9_ADAPTER_IDENTITY,
    }
    manifest["artifactIdentity"] = artifact_identity(manifest)
    provenance: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-SYNTHETIC-PROVENANCE",
        "baseDatasetIdentity": V9_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "generatorReference": "scripts/build-p7-t4-research-remediation-source-v10.py",
        "humanFindingIdentity": HUMAN_FINDING_IDENTITY,
        "independentTargetedSyntheticGeneration": True,
        "noFrozenEvaluationContentCopied": True,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "replayGuard": replay_guard,
        "replaySourceSplits": ["train", "validation"],
        "schemaVersion": "1.0.0",
        "targetedEvaluationCaseIds": TARGET_CASE_IDS,
        "v9EvaluationRecordsUsedForOptimization": False,
    }
    provenance["artifactIdentity"] = artifact_identity(provenance)
    contract: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-CONTINUATION-CONTRACT",
        "constrainedDecodingAllowed": False,
        "contractHoldoutRecordCount": 8,
        "contractHoldoutUseForEarlyStopping": False,
        "contractHoldoutUseForOptimization": False,
        "datasetIdentity": dataset_identity,
        "earlyStoppingPatience": 1,
        "learningRateMaximum": 0.00002,
        "maximumRuns": 1,
        "maximumSteps": 48,
        "parentAdapterIdentity": V9_ADAPTER_IDENTITY,
        "parentCandidateId": V9_CANDIDATE_ID,
        "parentTrainingRunIdentity": V9_TRAINING_RUN_IDENTITY,
        "promptProfileIdentity": PROMPT_PROFILE_IDENTITY,
        "runtimeNormalizationAllowed": False,
        "schemaVersion": "1.0.0",
        "trainingMethod": "QLORA_ADAPTER_CONTINUATION",
    }
    contract["artifactIdentity"] = artifact_identity(contract)
    card: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-DATASET-CARD",
        "baseDatasetIdentity": V9_DATASET_IDENTITY,
        "datasetIdentity": dataset_identity,
        "disposition": "PENDING_TRAINING_APPROVAL",
        "recordCounts": RECORD_COUNTS,
        "remediationPriority": "SINGLE_HUMAN_FAILURE_TARGETED_CONTINUATION",
        "replayGuardCaseIds": REPLAY_CASE_IDS,
        "schemaVersion": "1.0.0",
        "targetedEvaluationCaseIds": TARGET_CASE_IDS,
    }
    card["artifactIdentity"] = artifact_identity(card)
    request: dict[str, Any] = {
        "approvalAuthority": "RESEARCH_GOVERNANCE_TRAINING_APPROVAL_AUTHORITY",
        "artifactType": "P7-T1C-RESEARCH-REMEDIATION-V10-TRAINING-APPROVAL-REQUEST",
        "datasetIdentity": dataset_identity,
        "datasetManifestReference": f"{DATASET_ROOT}/manifest.json",
        "externalTrainingAllowed": False,
        "preparationApprovalIdentity": PREPARATION_APPROVAL_IDENTITY,
        "preparationApprovalReference": APPROVAL_REFERENCE,
        "qualityIdentity": QUALITY_IDENTITY,
        "requestId": "P7-T1C-RESEARCH-REMEDIATION-V10-TRAINING-APPROVAL-REQUEST-001",
        "requestedScope": {
            "candidateDispositionAfterTraining": "CANDIDATE_ONLY",
            "contractHoldoutUsedForEarlyStopping": False,
            "contractHoldoutUsedForOptimization": False,
            "earlyStoppingPatience": 1,
            "externalSingleT4Training": True,
            "freshAdapterInitializationRequired": False,
            "freshBaseModelLoadRequired": True,
            "learningRateMaximum": 0.00002,
            "maximumRuns": 1,
            "maximumSteps": 48,
            "newCandidateIdentityRequired": True,
            "newTrainingEvidenceRequired": True,
            "parentAdapterIdentity": V9_ADAPTER_IDENTITY,
            "parentCandidateId": V9_CANDIDATE_ID,
            "parentOutputArchiveSha256": V9_OUTPUT_ARCHIVE_SHA256,
            "parentTrainingRunIdentity": V9_TRAINING_RUN_IDENTITY,
            "replayGuardCaseIds": REPLAY_CASE_IDS,
            "targetedEvaluationCaseIds": TARGET_CASE_IDS,
            "trainingMethod": "QLORA_ADAPTER_CONTINUATION",
        },
        "schemaVersion": "1.0.0",
        "status": "PENDING_USER_APPROVAL",
        "trainingAuthorized": False,
    }
    request["requestIdentity"] = artifact_identity(request, "requestIdentity")
    source_export: dict[str, Any] = {
        "artifactType": "P7-T4-RESEARCH-REMEDIATION-V10-SOURCE-EXPORT",
        "baseDatasetIdentity": V9_DATASET_IDENTITY,
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
        print(
            json.dumps(
                {"requestIdentity": request["requestIdentity"], "state": request["status"]},
                sort_keys=True,
            )
        )
        return 0
    except (DatasetBuildError, OSError, ValueError) as error:
        print(json.dumps({"diagnostics": [str(error)], "state": "ERROR"}, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
