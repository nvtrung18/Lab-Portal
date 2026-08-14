#!/usr/bin/env python3
"""Offline, deterministic validation for the approved P6-T3 schema bundle."""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import sys

import yaml
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATH = ROOT / "docs/architecture/ai/datasets/domain-dataset-schemas.schema.json"
CASES_PATH = ROOT / "docs/architecture/ai/datasets/fixtures/p6-t3-cases.yaml"
SCHEMA_ID = "https://lab-portal.local/schemas/p6-t3/domain-dataset-schemas/1.0.0"
ACTIVE_PERMISSION = {"ELIGIBLE_AFTER_APPROVAL", "SYNTHETIC_ONLY"}
ACTIVE_DISPOSITION = {"SANITIZED_DERIVATIVE_REQUIRED", "SYNTHETIC_GENERATION_ONLY"}
BRANCHES = {
    "ADMIN_UC_001": ("ADMIN", "ADMIN_ASSISTANT_ONLY", "SYSTEM_STATUS_SUMMARY", "SUMMARY"),
    "ADMIN_UC_002": ("ADMIN", "ADMIN_ASSISTANT_ONLY", "AUDIT_SUMMARY", "SUMMARY"),
    "ADMIN_UC_003": ("ADMIN", "ADMIN_ASSISTANT_ONLY", "USER_STATUS_LOOKUP", "LOOKUP"),
    "ADMIN_UC_004": ("ADMIN", "ADMIN_ASSISTANT_ONLY", "CONFIGURATION_CHANGE_DRAFT", "DRAFT"),
    "ADMIN_UC_005": ("ADMIN", "ADMIN_ASSISTANT_ONLY", "ACCOUNT_ACTION_DRAFT", "DRAFT"),
    "LAB_UC_001": ("LAB", "LAB_ASSISTANT_ONLY", "POLICY_GUIDANCE", "GUIDANCE"),
    "LAB_UC_002": ("LAB", "LAB_ASSISTANT_ONLY", "SLOT_AVAILABILITY", "LOOKUP"),
    "LAB_UC_003": ("LAB", "LAB_ASSISTANT_ONLY", "OWN_BOOKING_LOOKUP", "LOOKUP"),
    "LAB_UC_004": ("LAB", "LAB_ASSISTANT_ONLY", "MANAGED_LAB_SUMMARY", "SUMMARY"),
    "LAB_UC_005": ("LAB", "LAB_ASSISTANT_ONLY", "BOOKING_DRAFT", "DRAFT"),
    "LAB_UC_006": ("LAB", "LAB_ASSISTANT_ONLY", "CHECKIN_GUIDANCE", "GUIDANCE"),
    "RESEARCH_UC_001": ("RESEARCH", "RESEARCH_ASSISTANT_ONLY", "PROJECT_PROGRESS_SUMMARY", "SUMMARY"),
    "RESEARCH_UC_002": ("RESEARCH", "RESEARCH_ASSISTANT_ONLY", "GROUP_PROGRESS_SUMMARY", "SUMMARY"),
    "RESEARCH_UC_003": ("RESEARCH", "RESEARCH_ASSISTANT_ONLY", "ASSIGNED_TASK_LOOKUP", "LOOKUP"),
    "RESEARCH_UC_004": ("RESEARCH", "RESEARCH_ASSISTANT_ONLY", "TASK_PROPOSAL_DRAFT", "DRAFT"),
    "RESEARCH_UC_005": ("RESEARCH", "RESEARCH_ASSISTANT_ONLY", "TASK_SUGGESTION_DRAFT", "DRAFT"),
}
INPUT_KEYS = {
    "ADMIN_UC_001": {"systemStatus"}, "ADMIN_UC_002": {"auditSummary"},
    "ADMIN_UC_003": {"userRef", "userStatus"}, "ADMIN_UC_004": {"task"},
    "ADMIN_UC_005": {"userRef", "userStatus", "task"},
    "LAB_UC_001": {"labRef", "policy", "task"}, "LAB_UC_002": {"labRef", "slotRef", "slotAvailability"},
    "LAB_UC_003": {"bookingRef", "bookingStatus"}, "LAB_UC_004": {"labRef", "managedLab"},
    "LAB_UC_005": {"labRef", "slotRef", "slotAvailability", "task"},
    "LAB_UC_006": {"bookingRef", "checkInPolicy", "task"},
    "RESEARCH_UC_001": {"projectRef", "projectProgress"},
    "RESEARCH_UC_002": {"projectRef", "groupRef", "groupProgress"},
    "RESEARCH_UC_003": {"taskRef", "assignedTask"},
    "RESEARCH_UC_004": {"projectRef", "groupRef", "groupProgress", "task"},
    "RESEARCH_UC_005": {"taskRef", "assignedTask", "task"},
}
PAYLOAD_KEYS = {
    "ADMIN_UC_001": {"resourceRef", "subjectKind"}, "ADMIN_UC_002": {"resourceRef", "subjectKind"},
    "ADMIN_UC_003": {"resourceRef", "subjectKind"}, "ADMIN_UC_004": {"draftKind"},
    "ADMIN_UC_005": {"resourceRef", "draftKind"}, "LAB_UC_001": {"resourceRef"},
    "LAB_UC_002": {"resourceRef"}, "LAB_UC_003": {"resourceRef"}, "LAB_UC_004": {"resourceRef"},
    "LAB_UC_005": {"resourceRef", "draftBookingKind"}, "LAB_UC_006": {"resourceRef"},
    "RESEARCH_UC_001": {"resourceRef"}, "RESEARCH_UC_002": {"resourceRef"},
    "RESEARCH_UC_003": {"resourceRef"}, "RESEARCH_UC_004": {"proposalKind"},
    "RESEARCH_UC_005": {"proposalKind"},
}
EXPECTED_CATEGORY_IDS = {
    "ADMIN_UC_001": ["CAT_ADMIN_SYSTEM_STATUS"], "ADMIN_UC_002": ["CAT_ADMIN_AUDIT_SUMMARY"],
    "ADMIN_UC_003": ["CAT_IDENTITY_USER_STATUS"], "ADMIN_UC_004": ["CAT_ADMIN_DRAFT_TEMPLATE"],
    "ADMIN_UC_005": ["CAT_IDENTITY_USER_STATUS", "CAT_ADMIN_ACCOUNT_POLICY_SYNTHETIC"],
    "LAB_UC_001": ["CAT_LAB_POLICY_GUIDANCE"], "LAB_UC_002": ["CAT_LAB_SLOT_AVAILABILITY"],
    "LAB_UC_003": ["CAT_BOOKING_OWNERSHIP"], "LAB_UC_004": ["CAT_LAB_MANAGED_SUMMARY"],
    "LAB_UC_005": ["CAT_LAB_SLOT_AVAILABILITY", "CAT_SYNTHETIC_LAB_BOOKING_DRAFT"],
    "LAB_UC_006": ["CAT_CHECKIN_POLICY"], "RESEARCH_UC_001": ["CAT_RESEARCH_PROJECT_PROGRESS"],
    "RESEARCH_UC_002": ["CAT_RESEARCH_GROUP_PROGRESS"], "RESEARCH_UC_003": ["CAT_RESEARCH_ASSIGNED_TASK"],
    "RESEARCH_UC_004": ["CAT_RESEARCH_DRAFT_CONTEXT"], "RESEARCH_UC_005": ["CAT_RESEARCH_DRAFT_CONTEXT"],
}
CATEGORY_RULES = {
    "CAT_ADMIN_SYSTEM_STATUS": ("INTERNAL", "ELIGIBLE_AFTER_APPROVAL", "ADMIN_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_ADMIN_AUDIT_SUMMARY": ("SENSITIVE", "ELIGIBLE_AFTER_APPROVAL", "ADMIN_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_IDENTITY_USER_STATUS": ("SENSITIVE", "ELIGIBLE_AFTER_APPROVAL", "ADMIN_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_ADMIN_DRAFT_TEMPLATE": ("INTERNAL", "SYNTHETIC_ONLY", "ADMIN_ASSISTANT_ONLY", "SYNTHETIC_GENERATION_ONLY"),
    "CAT_ADMIN_ACCOUNT_POLICY_SYNTHETIC": ("INTERNAL", "SYNTHETIC_ONLY", "ADMIN_ASSISTANT_ONLY", "SYNTHETIC_GENERATION_ONLY"),
    "CAT_LAB_POLICY_GUIDANCE": ("INTERNAL", "ELIGIBLE_AFTER_APPROVAL", "LAB_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_LAB_SLOT_AVAILABILITY": ("SENSITIVE", "ELIGIBLE_AFTER_APPROVAL", "LAB_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_BOOKING_OWNERSHIP": ("SENSITIVE", "SYNTHETIC_ONLY", "LAB_ASSISTANT_ONLY", "SYNTHETIC_GENERATION_ONLY"),
    "CAT_LAB_MANAGED_SUMMARY": ("SENSITIVE", "ELIGIBLE_AFTER_APPROVAL", "LAB_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_SYNTHETIC_LAB_BOOKING_DRAFT": ("INTERNAL", "SYNTHETIC_ONLY", "LAB_ASSISTANT_ONLY", "SYNTHETIC_GENERATION_ONLY"),
    "CAT_CHECKIN_POLICY": ("INTERNAL", "ELIGIBLE_AFTER_APPROVAL", "LAB_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_RESEARCH_PROJECT_PROGRESS": ("SENSITIVE", "ELIGIBLE_AFTER_APPROVAL", "RESEARCH_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_RESEARCH_GROUP_PROGRESS": ("SENSITIVE", "ELIGIBLE_AFTER_APPROVAL", "RESEARCH_ASSISTANT_ONLY", "SANITIZED_DERIVATIVE_REQUIRED"),
    "CAT_RESEARCH_ASSIGNED_TASK": ("SENSITIVE", "SYNTHETIC_ONLY", "RESEARCH_ASSISTANT_ONLY", "SYNTHETIC_GENERATION_ONLY"),
    "CAT_RESEARCH_DRAFT_CONTEXT": ("SENSITIVE", "SYNTHETIC_ONLY", "RESEARCH_ASSISTANT_ONLY", "SYNTHETIC_GENERATION_ONLY"),
}


def pointer(parts: list[object]) -> str:
    return "/" + "/".join(str(part).replace("~", "~0").replace("/", "~1") for part in parts)


def local_refs(value: object, path: list[object] | None = None) -> list[str]:
    path = path or []
    errors: list[str] = []
    if isinstance(value, dict):
        if "$id" in value and value["$id"] != SCHEMA_ID:
            errors.append(f"{pointer(path + ['$id'])}: unexpected $id")
        if "$ref" in value and (not isinstance(value["$ref"], str) or not value["$ref"].startswith("#/")):
            errors.append(f"{pointer(path + ['$ref'])}: non-local $ref")
        for key, child in value.items():
            errors.extend(local_refs(child, path + [key]))
    elif isinstance(value, list):
        for index, child in enumerate(value):
            errors.extend(local_refs(child, path + [index]))
    return errors


def branch_errors(record: object) -> list[str]:
    if not isinstance(record, dict):
        return ["/: record must be an object"]
    if record.get("domain") == "SHARED":
        if "useCaseId" in record:
            return ["/useCaseId: shared records forbid useCaseId"]
        return []
    use_case = record.get("useCaseId")
    if use_case not in BRANCHES:
        return ["/useCaseId: inactive, unknown, or deferred branch"]
    domain, visibility, record_type, output_kind = BRANCHES[use_case]
    errors: list[str] = []
    for key, expected in (("domain", domain), ("visibility", visibility), ("recordType", record_type)):
        if record.get(key) != expected:
            errors.append(f"/{key}: expected {expected!r}")
    if set(record.get("input", {})) != INPUT_KEYS[use_case]:
        errors.append("/input: exact branch projection required")
    if set(record.get("payload", {})) != PAYLOAD_KEYS[use_case]:
        errors.append("/payload: exact branch payload required")
    governance = record.get("governance")
    if not isinstance(governance, dict):
        return errors + ["/governance: required object"]
    tuples = governance.get("categoryGovernance")
    category_ids = governance.get("categoryIds")
    if not isinstance(tuples, list) or not isinstance(category_ids, list) or len(tuples) != len(category_ids):
        return errors + ["/governance: category arrays must have equal non-zero length"]
    if category_ids != EXPECTED_CATEGORY_IDS[use_case]:
        errors.append("/governance/categoryIds: exact ordered P6-T2 categories required")
    for index, item in enumerate(tuples):
        if not isinstance(item, dict):
            errors.append(f"/governance/categoryGovernance/{index}: object required")
            continue
        if item.get("categoryId") != category_ids[index]:
            errors.append(f"/governance/categoryGovernance/{index}/categoryId: ordered category mismatch")
        rule = CATEGORY_RULES.get(item.get("categoryId"))
        if rule and (item.get("classification"), item.get("datasetUsePermission"), item.get("visibility"),
                     item.get("sanitization", {}).get("disposition") if isinstance(item.get("sanitization"), dict) else None) != rule:
            errors.append(f"/governance/categoryGovernance/{index}: exact P6-T2 tuple required")
        if item.get("datasetUsePermission") not in ACTIVE_PERMISSION:
            errors.append(f"/governance/categoryGovernance/{index}/datasetUsePermission: inactive permission")
        disposition = item.get("sanitization", {}).get("disposition") if isinstance(item.get("sanitization"), dict) else None
        if disposition not in ACTIVE_DISPOSITION:
            errors.append(f"/governance/categoryGovernance/{index}/sanitization/disposition: inactive disposition")
        provenance = item.get("provenance", {}).get("type") if isinstance(item.get("provenance"), dict) else None
        expected_provenance = "INTERNAL_SANITIZED" if disposition == "SANITIZED_DERIVATIVE_REQUIRED" else "SYNTHETIC"
        if provenance != expected_provenance:
            errors.append(f"/governance/categoryGovernance/{index}/provenance/type: disposition mismatch")
        if item.get("modelDevelopmentPurpose") != ["DEVELOPMENT_TEST"]:
            errors.append(f"/governance/categoryGovernance/{index}/modelDevelopmentPurpose: exact purpose required")
    output = record.get("expectedOutput")
    if isinstance(output, dict):
        is_draft = output_kind == "DRAFT"
        if output.get("actionRisk") != ("DRAFT_ONLY" if is_draft else "READ_ONLY"):
            errors.append("/expectedOutput/actionRisk: branch action risk mismatch")
        if is_draft and output.get("contentType") != "NON_OFFICIAL_STRUCTURED_DRAFT":
            errors.append("/expectedOutput/contentType: draft content type required")
        if not is_draft and output.get("contentType") != output_kind:
            errors.append("/expectedOutput/contentType: branch content type mismatch")
    if use_case == "ADMIN_UC_001" and record.get("input", {}).get("systemStatus", {}).get("health") not in {"HEALTHY", "DEGRADED", "UNAVAILABLE"}:
        errors.append("/input/systemStatus/health: bounded observation required")
    return errors


def collection_errors(cases_document: dict, mutation: dict) -> list[str]:
    """Apply a fixture-declared mutation and prove collection integrity fails."""
    mutated = copy.deepcopy(cases_document)
    kind = mutation.get("kind")
    target_id = mutation.get("targetCaseId")
    cases = mutated.get("cases")
    required = mutated.get("manifest", {}).get("requiredCaseIds")
    if not isinstance(cases, list) or not isinstance(required, list) or not isinstance(target_id, str):
        return ["/collectionMutation: malformed mutation declaration"]
    source = next((item for item in cases if item.get("id") == target_id), None)
    if kind == "removeManifestId" and target_id in required:
        required.remove(target_id)
    elif kind == "duplicateCaseId" and isinstance(source, dict):
        cases.append(copy.deepcopy(source))
    elif kind == "appendUnlistedCase" and isinstance(source, dict):
        unlisted = copy.deepcopy(source)
        unlisted["id"] = f"UNLISTED-{target_id}"
        cases.append(unlisted)
    else:
        return ["/collectionMutation: unsupported mutation or missing target"]
    ids = [item.get("id") for item in cases if isinstance(item, dict)]
    if len(ids) != len(set(ids)) or set(ids) != set(required) or len(required) != len(set(required)):
        return ["/collection: mutated manifest requiredCaseIds must equal unique case IDs exactly"]
    return []


def mutated_record(cases_document: dict, mutation: dict) -> object:
    """Build one isolated record mutation from a positive fixture."""
    source_id = mutation.get("sourceCaseId")
    path = mutation.get("path")
    operation = mutation.get("operation")
    source = next((item for item in cases_document.get("cases", []) if item.get("id") == source_id), None)
    if not isinstance(source, dict) or not isinstance(source.get("record"), dict):
        raise ValueError("recordMutation source case must contain a record")
    if operation not in {"add", "replace"} or not isinstance(path, list) or not path or not all(isinstance(part, str) for part in path):
        raise ValueError("recordMutation must declare add/replace and a non-empty path")
    record = copy.deepcopy(source["record"])
    target = record
    for part in path[:-1]:
        if isinstance(target, dict) and part in target:
            target = target[part]
        elif isinstance(target, list) and part.isdigit() and int(part) < len(target):
            target = target[int(part)]
        else:
            raise ValueError("recordMutation path must traverse existing objects or arrays")
    if isinstance(target, dict):
        if operation == "replace" and path[-1] not in target:
            raise ValueError("recordMutation replace path must exist")
        target[path[-1]] = mutation.get("value")
    elif isinstance(target, list) and path[-1].isdigit() and int(path[-1]) < len(target):
        if operation != "replace":
            raise ValueError("recordMutation array changes must replace an existing item")
        target[int(path[-1])] = mutation.get("value")
    else:
        raise ValueError("recordMutation target must be an object property or existing array item")
    return record


def validate_case(validator: Draft202012Validator | None, case: dict, cases_document: dict) -> list[str]:
    if "collectionMutation" in case:
        return collection_errors(cases_document, case["collectionMutation"])
    if validator is None:
        return ["/schema: unknown schema selector"]
    record = mutated_record(cases_document, case["recordMutation"]) if "recordMutation" in case else case["record"]
    errors = [f"{pointer(list(error.absolute_path))}: {error.message}" for error in validator.iter_errors(record)]
    errors.extend(branch_errors(record))
    return sorted(set(errors))


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--all", action="store_true")
    group.add_argument("--case")
    args = parser.parse_args()
    try:
        schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
        cases_document = yaml.safe_load(CASES_PATH.read_text(encoding="utf-8"))
        ref_errors = local_refs(schema)
        if schema.get("$id") != SCHEMA_ID or ref_errors:
            raise ValueError("; ".join(["schema $id is missing or unexpected", *ref_errors]))
        registry = Registry().with_resource(SCHEMA_ID, Resource.from_contents(schema))
        roots = {
            "shared": Draft202012Validator({"$ref": f"{SCHEMA_ID}#/$defs/sharedRecord"}, registry=registry),
            "admin": Draft202012Validator({"$ref": f"{SCHEMA_ID}#/$defs/adminRecord"}, registry=registry),
            "lab": Draft202012Validator({"$ref": f"{SCHEMA_ID}#/$defs/labRecord"}, registry=registry),
            "research": Draft202012Validator({"$ref": f"{SCHEMA_ID}#/$defs/researchRecord"}, registry=registry),
        }
        cases = cases_document["cases"]
        required = cases_document["manifest"]["requiredCaseIds"]
        ids = [case["id"] for case in cases]
        if len(ids) != len(set(ids)) or set(ids) != set(required) or len(required) != len(set(required)):
            raise ValueError("manifest requiredCaseIds must equal unique case IDs exactly")
        selected = [case for case in cases if args.all or case["id"] == args.case]
        if not selected:
            raise ValueError("unknown case selector")
        failed = False
        for case in selected:
            actual = not validate_case(roots.get(case["schema"]), case, cases_document)
            expected = bool(case["expected"])
            if actual != expected:
                failed = True
            print(f"{'PASS' if actual == expected else 'FAIL'} {case['id']} {case['schema']} expected={expected} actual={actual} assertion={case['assertion']}")
            if actual != expected:
                for diagnostic in validate_case(roots.get(case["schema"]), case, cases_document):
                    print(f"  {diagnostic}")
        return 1 if failed else 0
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, yaml.YAMLError) as error:
        print(f"ERROR {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
