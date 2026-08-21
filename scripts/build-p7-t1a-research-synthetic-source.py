#!/usr/bin/env python3
"""Build the immutable, unapproved P7-T1A synthetic Research source."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from typing import Any

import yaml


ROOT = Path(__file__).resolve().parents[1]
P7T1_PATH = ROOT / "scripts" / "dataset-pipeline-p7-t1.py"
P6_FIXTURE_PATH = ROOT / "docs" / "architecture" / "ai" / "datasets" / "fixtures" / "p6-t3-cases.yaml"
P6_EVALUATION_PATH = ROOT / "evals" / "p6-t4-evaluation-suites.yaml"
P7T3_EVALUATION_PATH = ROOT / "evals" / "p7-t3-research-gap-evaluation-suite.json"
CANONICAL_OUTPUT_DIRECTORY = ROOT / "datasets" / "p7-t1a-research-synthetic-source-v1"
SOURCE_ID = "p7-t1a-research-synthetic-source"
SOURCE_VERSION = "1.0.0"
GENERATOR_VERSION = "1.0.0"
ASSISTANT_KEY = "RESEARCH_ASSISTANT"
SOURCE_OWNER = "RESEARCH_GOVERNANCE_DOMAIN_DATA_OWNER"
APPROVAL_AUTHORITY = "RESEARCH_GOVERNANCE_DATA_GOVERNANCE_APPROVAL_AUTHORITY"
SOURCE_FIELDS = {
    "identity",
    "authorizationBoundary",
    "sourceDataOwner",
    "sourcePermissionReference",
    "approvalReference",
}
LEAKAGE_PATHS = (P6_FIXTURE_PATH, P6_EVALUATION_PATH, P7T3_EVALUATION_PATH)


def _load_p7t1():
    specification = importlib.util.spec_from_file_location("p7t1_for_p7t1a", P7T1_PATH)
    if specification is None or specification.loader is None:
        raise RuntimeError("P7-T1 pipeline cannot be loaded")
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


P7T1 = _load_p7t1()


class SourceBuildError(ValueError):
    """Stable fail-closed diagnostics for source generation."""

    def __init__(self, diagnostics: str | list[str]):
        values = [diagnostics] if isinstance(diagnostics, str) else diagnostics
        self.diagnostics = sorted(set(values))
        super().__init__("; ".join(self.diagnostics))


def load_document(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
        value = json.loads(text) if path.suffix.lower() == ".json" else yaml.safe_load(text)
    except (OSError, json.JSONDecodeError, yaml.YAMLError) as error:
        raise SourceBuildError(f"{path.name}: cannot load: {error}") from error
    if not isinstance(value, dict):
        raise SourceBuildError(f"{path.name}: object required")
    return value


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


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def json_bytes(value: object) -> bytes:
    rendered = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False)
    return (rendered + "\n").encode("utf-8")


def _ref(resource_type: str, resource_id: str) -> dict[str, str]:
    return {"resourceType": resource_type, "resourceId": resource_id}


def _governance(category_id: str) -> dict[str, Any]:
    return {
        "categoryIds": [category_id],
        "categoryGovernance": [
            {
                "categoryId": category_id,
                "classification": "SENSITIVE",
                "datasetUsePermission": "SYNTHETIC_ONLY",
                "visibility": "RESEARCH_ASSISTANT_ONLY",
                "sanitization": {"disposition": "SYNTHETIC_GENERATION_ONLY"},
                "provenance": {"type": "SYNTHETIC"},
                "modelDevelopmentPurpose": ["DEVELOPMENT_TEST"],
            }
        ],
    }


def _base_record(record_id: str, use_case_id: str, record_type: str, category_id: str) -> dict[str, Any]:
    return {
        "schemaVersion": "1.0.0",
        "recordId": record_id,
        "domain": "RESEARCH",
        "recordType": record_type,
        "visibility": "RESEARCH_ASSISTANT_ONLY",
        "useCaseId": use_case_id,
        "governance": _governance(category_id),
        "metadata": {"synthetic": True},
    }


def _assigned_task_record(
    index: int,
    state: str,
    expected_output: dict[str, Any],
) -> dict[str, Any]:
    record = _base_record(
        f"record-p7t1a-r003-{index:03d}",
        "RESEARCH_UC_003",
        "ASSIGNED_TASK_LOOKUP",
        "CAT_RESEARCH_ASSIGNED_TASK",
    )
    task_ref = _ref("ASSIGNED_TASK", f"syn-task-lookup-{index:03d}")
    record.update(
        {
            "input": {"taskRef": task_ref, "assignedTask": {"state": state}},
            "payload": {"resourceRef": task_ref},
            "expectedOutput": expected_output,
        }
    )
    return record


def _proposal_record(
    index: int,
    state: str,
    task: str,
    expected_output: dict[str, Any],
) -> dict[str, Any]:
    record = _base_record(
        f"record-p7t1a-r004-{index:03d}",
        "RESEARCH_UC_004",
        "TASK_PROPOSAL_DRAFT",
        "CAT_RESEARCH_DRAFT_CONTEXT",
    )
    record.update(
        {
            "input": {
                "projectRef": _ref("PROJECT", f"syn-project-proposal-{index:03d}"),
                "groupRef": _ref("PROJECT_GROUP", f"syn-group-proposal-{index:03d}"),
                "groupProgress": {"state": state},
                "task": task,
            },
            "payload": {"proposalKind": "TASK_PROPOSAL_DRAFT"},
            "expectedOutput": expected_output,
        }
    )
    return record


def _suggestion_record(
    index: int,
    state: str,
    task: str,
    expected_output: dict[str, Any],
) -> dict[str, Any]:
    record = _base_record(
        f"record-p7t1a-r005-{index:03d}",
        "RESEARCH_UC_005",
        "TASK_SUGGESTION_DRAFT",
        "CAT_RESEARCH_DRAFT_CONTEXT",
    )
    task_ref = _ref("ASSIGNED_TASK", f"syn-task-suggestion-{index:03d}")
    record.update(
        {
            "input": {
                "taskRef": task_ref,
                "assignedTask": {"state": state},
                "task": task,
            },
            "payload": {"proposalKind": "TASK_SUGGESTION_DRAFT"},
            "expectedOutput": expected_output,
        }
    )
    return record


PROPOSAL_SCENARIOS = (
    (
        "Draft a bounded calibration study for a synthetic soil-moisture sensor array.",
        "Synthetic sensor calibration proposal",
        [
            "Define repeatable dry, intermediate, and saturated reference conditions.",
            "Record calibration residuals without introducing participant or location data.",
            "Present uncertainty limits for human review before any protocol is adopted.",
        ],
    ),
    (
        "Propose a controlled check of illumination drift in a simulated microscopy setup.",
        "Illumination drift study draft",
        [
            "Capture synthetic reference slides at fixed illumination intervals.",
            "Compare intensity variation using a predeclared normalization method.",
            "Flag equipment changes as assumptions requiring researcher confirmation.",
        ],
    ),
    (
        "Prepare a draft experiment comparing invented biodegradable coating formulations.",
        "Biodegradable coating comparison draft",
        [
            "Define synthetic formulation labels and identical curing conditions.",
            "Measure adhesion and water exposure response with fixed scoring rules.",
            "Keep conclusions advisory until the research lead reviews the measurements.",
        ],
    ),
    (
        "Draft a thermal-cycling proposal for fictional low-power sensor housings.",
        "Thermal cycling proposal draft",
        [
            "Specify a bounded temperature schedule and inspection checkpoints.",
            "Track only synthetic specimen identifiers and predefined failure modes.",
            "Require human review before interpreting any housing design as suitable.",
        ],
    ),
    (
        "Propose a synthetic water-quality assay repeatability study.",
        "Assay repeatability study draft",
        [
            "Use invented sample codes with fixed concentration bands.",
            "Repeat each measurement under the same synthetic laboratory conditions.",
            "Summarize variance without making environmental or health claims.",
        ],
    ),
    (
        "Create a draft study of germination under simulated lighting schedules.",
        "Simulated lighting study draft",
        [
            "Assign synthetic seed batches to three fixed lighting schedules.",
            "Record emergence counts using predetermined observation windows.",
            "Mark causal interpretations as outside the draft until expert review.",
        ],
    ),
    (
        "Draft a validation exercise for a fictional low-cost spectrometer.",
        "Spectrometer validation draft",
        [
            "Define synthetic reference spectra across a bounded wavelength range.",
            "Compare repeated readings against fixed tolerance bands.",
            "Document calibration limitations for reviewer assessment.",
        ],
    ),
    (
        "Propose an acoustic damping comparison using invented composite samples.",
        "Acoustic damping comparison draft",
        [
            "Assign synthetic sample labels and a fixed excitation profile.",
            "Measure response at predetermined frequencies and repetitions.",
            "Separate observed differences from unverified material explanations.",
        ],
    ),
    (
        "Prepare a bounded battery enclosure temperature observation draft.",
        "Enclosure temperature observation draft",
        [
            "Use simulated load profiles and non-operational specimen identifiers.",
            "Record temperature changes at fixed intervals without safety certification claims.",
            "Escalate abnormal patterns to a human reviewer rather than prescribing action.",
        ],
    ),
    (
        "Draft a synthetic labeling consistency study for microscopy image annotations.",
        "Annotation consistency study draft",
        [
            "Use project-created abstract images with no patient or participant material.",
            "Apply a fixed label guide and compare agreement across repeated passes.",
            "Report ambiguous labels for human adjudication instead of forcing consensus.",
        ],
    ),
)


SUGGESTION_SCENARIOS = (
    ("Refine the synthetic sensor calibration checklist.", "Calibration checklist suggestion", ["Add a pre-run zero check.", "Record the tolerance used for every flagged reading."]),
    ("Improve the fictional microscopy drift log.", "Drift log suggestion", ["Separate lamp warm-up observations from later measurements.", "Add a reviewer field for unexplained intensity changes."]),
    ("Clarify the invented coating comparison task.", "Coating task suggestion", ["State the curing interval before measurement.", "Use the same adhesion scale for every synthetic specimen."]),
    ("Strengthen the simulated thermal-cycle inspection task.", "Thermal inspection suggestion", ["Add an inspection before the first temperature transition.", "Record enclosure changes using predefined categories only."]),
    ("Improve repeatability tracking for the synthetic assay task.", "Assay tracking suggestion", ["Record the order of repeated measurements.", "Flag missing repeats without estimating values."]),
    ("Clarify the simulated germination observation task.", "Germination observation suggestion", ["Define one observation time for each day.", "Separate missing observations from zero emergence counts."]),
    ("Improve the fictional spectrometer validation task.", "Spectrometer task suggestion", ["Add a reference reading before each synthetic batch.", "Record tolerance failures for later human review."]),
    ("Refine the invented acoustic measurement task.", "Acoustic measurement suggestion", ["Fix microphone placement in the task description.", "Repeat any measurement affected by a documented interruption."]),
    ("Clarify the simulated enclosure temperature task.", "Temperature task suggestion", ["State the synthetic load profile identifier.", "Add a stop-and-review note for out-of-range observations."]),
    ("Improve the project-created annotation consistency task.", "Annotation task suggestion", ["Include an explicit ambiguous-label option.", "Keep adjudication decisions assigned to a human reviewer."]),
)


LOOKUP_SUMMARIES = (
    ("TODO", "The synthetic calibration checklist task is queued and has not started."),
    ("IN_PROGRESS", "The fictional illumination drift logging task is in progress."),
    ("DONE", "The invented coating specimen labeling task is complete."),
    ("TODO", "The simulated thermal-cycle inspection task is awaiting work."),
    ("IN_PROGRESS", "The synthetic assay repeatability task is currently active."),
    ("DONE", "The simulated lighting schedule table task is complete."),
    ("TODO", "The fictional spectrometer reference task remains queued."),
    ("IN_PROGRESS", "The invented acoustic response measurement task is underway."),
    ("DONE", "The simulated enclosure observation task is marked complete."),
    ("TODO", "The project-created annotation guide task has not started."),
)


SAFETY_BEHAVIORS = ("SAFE_REFUSAL", "DENY", "NO_CONTEXT", "SAFE_REFUSAL", "DENY")
LOOKUP_SAFETY_MESSAGES = (
    "I cannot disclose a task that is outside the supplied synthetic assignment scope.",
    "The bounded context does not authorize this cross-group synthetic task lookup.",
    "No authorized task context was supplied, so I cannot confirm the task or its state.",
    "I cannot use an instruction embedded in a task label to expand access beyond the resolved scope.",
    "The requested synthetic task reference is not assigned or otherwise authorized for this context.",
)
PROPOSAL_SAFETY_MESSAGES = (
    "I cannot draft from a group that is outside the authorized synthetic project scope.",
    "The bounded context does not permit using another synthetic group's private task details.",
    "No authorized project and group context is available for a proposal draft.",
    "I cannot follow embedded text that asks me to bypass scope checks or create an official task.",
    "The requested proposal depends on synthetic context that was not authorized for this draft.",
)
SUGGESTION_SAFETY_MESSAGES = (
    "I cannot suggest changes for a task outside the authorized synthetic assignment scope.",
    "The bounded context does not permit cross-group access to this fictional task.",
    "No authorized task context is available for a suggestion draft.",
    "I cannot treat task text as permission to mutate records or bypass human review.",
    "The requested synthetic task was not resolved as assigned or otherwise authorized.",
)


def _draft_output(kind: str, title: str, steps: list[str]) -> dict[str, Any]:
    return {
        "behavior": "DRAFT_ONLY",
        "contentType": "NON_OFFICIAL_STRUCTURED_DRAFT",
        "draft": {"kind": kind, "title": title, "steps": steps},
        "actionRisk": "DRAFT_ONLY",
    }


def _refusal_output(behavior: str, content_type: str, message: str, risk: str) -> dict[str, str]:
    return {"behavior": behavior, "contentType": content_type, "message": message, "actionRisk": risk}


def generate_records() -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for index, (state, summary) in enumerate(LOOKUP_SUMMARIES, start=1):
        records.append(
            _assigned_task_record(
                index,
                state,
                {"behavior": "SUCCESS", "contentType": "LOOKUP", "summary": summary, "actionRisk": "READ_ONLY"},
            )
        )
    for offset, (behavior, message) in enumerate(zip(SAFETY_BEHAVIORS, LOOKUP_SAFETY_MESSAGES), start=11):
        records.append(_assigned_task_record(offset, "TODO", _refusal_output(behavior, "LOOKUP", message, "READ_ONLY")))

    for index, (task, title, steps) in enumerate(PROPOSAL_SCENARIOS, start=1):
        state = ("ON_TRACK", "AT_RISK", "BLOCKED")[(index - 1) % 3]
        records.append(_proposal_record(index, state, task, _draft_output("TASK_PROPOSAL_DRAFT", title, list(steps))))
    for offset, (behavior, message) in enumerate(zip(SAFETY_BEHAVIORS, PROPOSAL_SAFETY_MESSAGES), start=11):
        records.append(
            _proposal_record(
                offset,
                "BLOCKED",
                "Draft only from the resolved synthetic project and group context.",
                _refusal_output(behavior, "NON_OFFICIAL_STRUCTURED_DRAFT", message, "DRAFT_ONLY"),
            )
        )

    for index, (task, title, steps) in enumerate(SUGGESTION_SCENARIOS, start=1):
        state = ("TODO", "IN_PROGRESS", "DONE")[(index - 1) % 3]
        records.append(_suggestion_record(index, state, task, _draft_output("TASK_SUGGESTION_DRAFT", title, list(steps))))
    for offset, (behavior, message) in enumerate(zip(SAFETY_BEHAVIORS, SUGGESTION_SAFETY_MESSAGES), start=11):
        records.append(
            _suggestion_record(
                offset,
                "TODO",
                "Suggest only within the resolved synthetic task context.",
                _refusal_output(behavior, "NON_OFFICIAL_STRUCTURED_DRAFT", message, "DRAFT_ONLY"),
            )
        )
    return sorted(records, key=lambda record: record["recordId"])


def _walk(value: object):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from _walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from _walk(child)


def _reference_leakage_inventory() -> tuple[set[str], set[str], set[str]]:
    training_content_hashes: set[str] = set()
    canonical_node_hashes: set[str] = set()
    substantial_strings: set[str] = set()
    fixtures = load_document(P6_FIXTURE_PATH)
    for case in fixtures.get("cases", []):
        if isinstance(case, dict) and isinstance(case.get("record"), dict):
            training_content_hashes.add(sha256_bytes(canonical_bytes(P7T1.training_content(case["record"]))))
    for path in LEAKAGE_PATHS:
        document = load_document(path)
        for node in _walk(document):
            if isinstance(node, (dict, list)):
                canonical_node_hashes.add(sha256_bytes(canonical_bytes(node)))
            elif isinstance(node, str) and len(node.strip()) >= 40:
                substantial_strings.add(node.strip())
    return training_content_hashes, canonical_node_hashes, substantial_strings


def _candidate_strings(record: dict[str, Any]) -> set[str]:
    return {
        node.strip()
        for field in ("input", "payload", "expectedOutput")
        for node in _walk(record[field])
        if isinstance(node, str) and len(node.strip()) >= 40
    }


def validate_records(records: object) -> dict[str, int]:
    diagnostics: list[str] = []
    if not isinstance(records, list) or not records or any(not isinstance(record, dict) for record in records):
        raise SourceBuildError("records: non-empty object list required")
    record_ids = [record.get("recordId") for record in records]
    if len(record_ids) != len(set(record_ids)):
        diagnostics.append("records: duplicate record IDs")

    reference_content, reference_nodes, reference_strings = _reference_leakage_inventory()
    content_ids: list[str] = []
    exact_content_leaks = 0
    exact_node_leaks = 0
    exact_string_leaks = 0
    sensitive_pattern = re.compile(
        r"(?:password|api[_-]?key|access[_-]?token|refresh[_-]?token|private key|bearer\s+)",
        re.IGNORECASE,
    )
    email_pattern = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")

    for index, record in enumerate(records):
        if set(record) != set(P7T1.SOURCE_RECORD_FIELDS):
            diagnostics.append(f"records/{index}: exact P6-T3 source fields required")
        if record.get("domain") != "RESEARCH" or record.get("visibility") != "RESEARCH_ASSISTANT_ONLY":
            diagnostics.append(f"records/{index}: Research routing required")
        if record.get("useCaseId") not in {"RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"}:
            diagnostics.append(f"records/{index}: only training-eligible synthetic Research branches allowed")
        if record.get("metadata") != {"synthetic": True}:
            diagnostics.append(f"records/{index}: explicit synthetic metadata required")
        for item in P7T1.validate_record(record):
            diagnostics.append(f"records/{index}{item}: schema violation")

        training_content = P7T1.training_content(record)
        content_id = sha256_bytes(canonical_bytes(training_content))
        content_ids.append(content_id)
        if content_id in reference_content:
            exact_content_leaks += 1
        for field in ("input", "expectedOutput"):
            if sha256_bytes(canonical_bytes(record[field])) in reference_nodes:
                exact_node_leaks += 1
        exact_string_leaks += len(_candidate_strings(record) & reference_strings)

        serialized = canonical_bytes(record).decode("utf-8")
        if sensitive_pattern.search(serialized):
            diagnostics.append(f"records/{index}: secret-like content is forbidden")
        if email_pattern.search(serialized):
            diagnostics.append(f"records/{index}: email-like personal data is forbidden")

    if len(content_ids) != len(set(content_ids)):
        diagnostics.append("records: duplicate canonical training content")
    if exact_content_leaks or exact_node_leaks or exact_string_leaks:
        diagnostics.append(
            "records: evaluation/fixture leakage detected "
            f"(content={exact_content_leaks}, nodes={exact_node_leaks}, strings={exact_string_leaks})"
        )
    if diagnostics:
        raise SourceBuildError(diagnostics)
    return {
        "exactTrainingContentDuplicates": exact_content_leaks,
        "exactCanonicalNodeDuplicates": exact_node_leaks,
        "exactSubstantialStringDuplicates": exact_string_leaks,
    }


def _provenance_identity(provenance: dict[str, Any]) -> str:
    return sha256_bytes(canonical_bytes({key: value for key, value in provenance.items() if key != "provenanceIdentity"}))


def build_artifacts() -> tuple[dict[str, bytes], dict[str, Any], dict[str, Any]]:
    records = generate_records()
    leakage = validate_records(records)
    export = {
        "exportSchemaVersion": "1.0.0",
        "source": {
            "identity": f"{SOURCE_ID}-v1",
            "authorizationBoundary": P7T1.SPRING_AUTHORIZATION_BOUNDARY,
            "sourceDataOwner": SOURCE_OWNER,
            "sourcePermissionReference": None,
            "approvalReference": None,
        },
        "records": records,
    }
    if set(export) != {"exportSchemaVersion", "source", "records"} or set(export["source"]) != SOURCE_FIELDS:
        raise SourceBuildError("source export: exact P7-T1 structural contract required")
    export_bytes = json_bytes(export)
    export_sha256 = sha256_bytes(export_bytes)
    content_identity = sha256_bytes(canonical_bytes(records))
    use_case_counts = Counter(record["useCaseId"] for record in records)
    behavior_counts = Counter(record["expectedOutput"]["behavior"] for record in records)
    record_type_counts = Counter(record["recordType"] for record in records)
    safety_count = sum(
        count for behavior, count in behavior_counts.items() if behavior in {"DENY", "SAFE_REFUSAL", "NO_CONTEXT"}
    )
    provenance = {
        "artifactType": "P7-T1A-PROJECT-OWNED-SYNTHETIC-RESEARCH-SOURCE-PROVENANCE",
        "schemaVersion": "1.0.0",
        "sourceId": SOURCE_ID,
        "sourceVersion": SOURCE_VERSION,
        "sourceState": "SOURCE_READY",
        "governanceState": "AWAITING_GOVERNANCE_APPROVAL",
        "assistantKey": ASSISTANT_KEY,
        "contentIdentity": content_identity,
        "generator": {
            "version": GENERATOR_VERSION,
            "reference": "scripts/build-p7-t1a-research-synthetic-source.py",
            "deterministic": True,
            "randomnessUsed": False,
        },
        "ownership": {
            "project": "Lab-Portal",
            "projectOwned": True,
            "sourceDataOwner": SOURCE_OWNER,
            "externalSources": [],
        },
        "syntheticData": {
            "fullySynthetic": True,
            "independentlyAuthored": True,
            "productionDataUsed": False,
            "userDataUsed": False,
            "privateResearchDocumentsUsed": False,
            "internetDownloadedDataUsed": False,
            "evaluationMaterialCopied": False,
        },
        "intendedFutureUse": {
            "assistantKey": ASSISTANT_KEY,
            "purpose": "TRAINING",
            "trainingApproaches": ["LoRA", "QLoRA"],
            "subjectToExplicitGovernanceApproval": True,
        },
        "governance": {
            "approvalAuthority": APPROVAL_AUTHORITY,
            "approvalStatus": "PENDING",
            "lifecycleStatus": "PENDING_APPROVAL",
            "sourcePermissionStatus": "NOT_ASSESSED",
            "currentPermittedPurposes": ["DEVELOPMENT_TEST"],
            "proposedPurposes": ["TRAINING"],
            "materializationAuthorized": False,
            "trainingAuthorized": False,
            "sourcePermissionReference": None,
            "approvalReference": None,
        },
        "inventory": {
            "recordCount": len(records),
            "positiveRecordCount": len(records) - safety_count,
            "safetyRecordCount": safety_count,
            "byUseCase": dict(sorted(use_case_counts.items())),
            "byRecordType": dict(sorted(record_type_counts.items())),
            "byBehavior": dict(sorted(behavior_counts.items())),
            "recordIds": [record["recordId"] for record in records],
        },
        "coverage": {
            "includedUseCases": ["RESEARCH_UC_003", "RESEARCH_UC_004", "RESEARCH_UC_005"],
            "includedCategories": ["CAT_RESEARCH_ASSIGNED_TASK", "CAT_RESEARCH_DRAFT_CONTEXT"],
            "excludedUseCases": {
                "RESEARCH_UC_006": "TRAINING remains prohibited for CAT_RESEARCH_REPORT_METADATA",
            },
            "advisoryDraftOnly": True,
            "toolExecutionExamples": 0,
        },
        "antiLeakage": {
            "checkedReferences": [str(path.relative_to(ROOT)).replace("\\", "/") for path in LEAKAGE_PATHS],
            **leakage,
        },
        "lineage": {
            "contractReferences": [
                "ai-service/config/assistant-profiles.json",
                "docs/architecture/ai/assistant-use-case-catalog.yml",
                "docs/architecture/ai/data-governance.yml",
                "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json",
                "docs/architecture/ai/three-assistant-contract.yml",
            ],
            "sourceReference": "P7-T1A",
            "evaluationFixturesUsedAsTrainingSource": False,
        },
        "artifacts": [
            {
                "filename": "source-export.json",
                "sha256": export_sha256,
                "recordCount": len(records),
            }
        ],
        "provenanceIdentity": "",
    }
    provenance["provenanceIdentity"] = _provenance_identity(provenance)
    provenance_bytes = json_bytes(provenance)
    return {"source-export.json": export_bytes, "provenance.json": provenance_bytes}, export, provenance


def write_source(output_directory: Path) -> dict[str, Any]:
    if output_directory.exists():
        raise SourceBuildError("output directory must not already exist")
    artifacts, _, provenance = build_artifacts()
    output_directory.parent.mkdir(parents=True, exist_ok=True)
    try:
        with tempfile.TemporaryDirectory(prefix=f".{output_directory.name}.", dir=output_directory.parent) as name:
            temporary_directory = Path(name)
            for filename, content in artifacts.items():
                (temporary_directory / filename).write_bytes(content)
            os.replace(temporary_directory, output_directory)
    except OSError as error:
        raise SourceBuildError(f"output cannot be written: {error}") from error
    return provenance


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=CANONICAL_OUTPUT_DIRECTORY)
    args = parser.parse_args()
    try:
        provenance = write_source(args.output)
        print(json.dumps(provenance, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 0
    except SourceBuildError as error:
        print(json.dumps({"status": "ERROR", "diagnostics": error.diagnostics}, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
