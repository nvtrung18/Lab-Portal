from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest
from app.models import AssistantRequest, ToolPlanningRequest

from scripts.verify_runtime import check_response, verify_source, select_cases, write_results


def test_runtime_judge_rejects_foreign_reference_and_refusal_despite_http_200():
    case = {"expected": {"kind": "ADMIN_ACCOUNT_DRAFT", "subject": "9",
                         "requiresHumanReview": True}, "modelRequired": True}
    body = {"assistantKey": "ADMIN_ASSISTANT", "answer": json.dumps({
        "kind": "ADMIN_ACCOUNT_DRAFT", "subject": "999", "requiresHumanReview": True}),
        "promptTokens": 8, "completionTokens": 3, "metadata": {}}
    assert any("subject" in error for error in check_response(case, 200, body))
    body["answer"] = json.dumps({**case["expected"]})
    body["metadata"] = {"safeRefusal": True}
    assert any("refusal" in error for error in check_response(case, 200, body))


def test_expected_response_metadata_is_checked_alongside_the_draft():
    case = {"expected": {"subject": "9"}, "responseExpected": {"metadata.draftOnly": True}}
    body = {"answer": '{"subject":"9"}', "metadata": {"draftOnly": False}}
    assert check_response(case, 200, body)


def test_expected_safe_refusal_is_checked_without_accepting_an_unexpected_draft():
    case = {"responseExpected": {"metadata.safeRefusal": True, "promptTokens": 0, "completionTokens": 0},
            "excludes": ["LAB_SHIFT_CREATE_DRAFT"]}
    body = {"answer": "Cannot safely use that capacity", "metadata": {"safeRefusal": True},
            "promptTokens": 0, "completionTokens": 0}
    assert check_response(case, 200, body) == []
    assert check_response(case, 200, {**body, "metadata": {}})
    assert check_response(case, 200, {**body, "metadata": {"safeRefusal": False}})
    assert check_response(case, 200, {**body, "answer": "LAB_SHIFT_CREATE_DRAFT"})


def test_expected_safe_refusal_still_requires_real_model_tokens_when_selected():
    case = {"responseExpected": {"metadata.safeRefusal": True}, "modelRequired": True}
    body = {"answer": "Cannot interpret that literal", "metadata": {"safeRefusal": True},
            "promptTokens": 0, "completionTokens": 0}
    assert check_response(case, 200, body) == ["Model generation tokens required"]
    assert check_response(case, 200, {**body, "promptTokens": 20, "completionTokens": 10}) == []


def test_workflow_judge_rejects_generic_clarification_and_requires_an_explicit_limitation():
    case = {"responseExpected": {"decision": "REFUSAL", "toolRequest": None},
            "messagePattern": r"(?iu)(không|chưa).*(hỗ trợ|công cụ|khả năng|thông tin|thể)"}
    body = {"decision": "CLARIFICATION", "toolRequest": None, "message": "Tôi chưa hiểu rõ. Bạn diễn đạt lại."}
    assert check_response(case, 200, body)
    body.update(decision="REFUSAL")
    assert check_response(case, 200, body) == ["Missing explicit workflow limitation"]
    body.update(message="Chưa có công cụ hỗ trợ phê duyệt booking qua chat.")
    assert check_response(case, 200, body) == []


def test_semantic_time_roles_do_not_depend_on_list_order():
    times = [{"role": "START", "hour": 15, "minute": 0}, {"role": "END", "hour": 17, "minute": 0}]
    case = {"expected": {"timeMentions": times}, "unorderedFields": ["timeMentions"]}
    assert check_response(case, 200, {"answer": json.dumps({"timeMentions": list(reversed(times))})}) == []


def test_snapshots_accept_zero_tokens_but_model_cases_require_generation():
    body = {"answer": "deadline 2026-10-09; progress 0", "promptTokens": 0,
            "completionTokens": 0, "metadata": {}}
    case = {"contains": ["2026-10-09", "progress 0"], "excludes": ["2026-10-08"]}
    assert check_response(case, 200, body) == []
    assert check_response({**case, "modelRequired": True}, 200, body)
    assert check_response(case, 504, {"errorCode": "AI_REQUEST_TIMEOUT"})


def test_runtime_corpus_selection_never_silently_skips_unknown_case():
    cases = [{"id": "admin-read", "group": "snapshot", "caseIds": ["A01"]}]
    assert select_cases(cases, ["snapshot"]) == cases
    assert select_cases(cases, ["A01"]) == cases
    with pytest.raises(ValueError, match="Unknown"):
        select_cases(cases, ["A999"])


def test_source_verification_detects_stale_bundle_without_echoing_file_contents(tmp_path: Path):
    source = tmp_path / "ai-service/app/main.py"
    source.parent.mkdir(parents=True)
    source.write_text("approved source")
    manifest = {"files": {"ai-service/app/main.py": hashlib.sha256(source.read_bytes()).hexdigest()}}
    (tmp_path / "kaggle-source-manifest.json").write_text(json.dumps(manifest))
    assert verify_source(tmp_path)["fileCount"] == 1
    source.write_text("changed source")
    with pytest.raises(ValueError, match="checksum"):
        verify_source(tmp_path)


def test_source_zip_identity_is_portable_between_windows_and_linux(tmp_path: Path, monkeypatch):
    import zipfile
    import scripts.verify_runtime as verifier
    source = tmp_path / "main.py"
    source.write_bytes(b"portable source")
    (tmp_path / "kaggle-source-manifest.json").write_text(json.dumps({
        "files": {"main.py": hashlib.sha256(source.read_bytes()).hexdigest()}}), encoding="utf-8")
    expected = verify_source(tmp_path)["sourceZipSha256"]
    original = zipfile.ZipInfo

    class OtherPlatformZipInfo(original):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, **kwargs)
            self.create_system = 3 if self.create_system == 0 else 0

    monkeypatch.setattr(verifier.zipfile, "ZipInfo", OtherPlatformZipInfo)
    assert verify_source(tmp_path)["sourceZipSha256"] == expected


def test_reports_preserve_first_failure_and_do_not_count_variants_as_business_cases(tmp_path: Path):
    results = [{"id": "draft", "caseIds": ["A09"], "passed": False, "seconds": 0.1,
                "errors": ["Wrong subject"], "repeat": 1},
               {"id": "draft", "caseIds": ["A09"], "passed": True, "seconds": 0.1,
                "errors": [], "repeat": 2}]
    write_results(tmp_path, {"backend": "REAL_MODEL"}, results)
    saved = json.loads((tmp_path / "runtime-results.json").read_text())
    assert saved["failed"] == 1 and saved["passed"] == 1
    assert saved["results"][0]["errors"] == ["Wrong subject"]
    assert 'failures="1"' in (tmp_path / "runtime-results.xml").read_text()


def test_blocked_preflight_is_an_error_in_junit_not_an_empty_green_suite(tmp_path: Path):
    import xml.etree.ElementTree as ET
    write_results(tmp_path, {"blocked": "ValueError", "blockedReason": "Source checksum mismatch"}, [])
    suite = ET.parse(tmp_path / "runtime-results.xml").getroot()
    assert suite.attrib["errors"] == "1"
    assert suite.find("testcase/error") is not None


CORPUS = json.loads((Path(__file__).parent / "fixtures/runtime-corpus.json").read_text(encoding="utf-8"))["cases"]


@pytest.mark.parametrize("case", CORPUS, ids=lambda case: case["id"])
def test_runtime_corpus_matches_the_real_wire_models(case):
    model = ToolPlanningRequest if case.get("path", "").endswith("tool-request") else AssistantRequest
    model.model_validate(case["request"])


@pytest.mark.parametrize("case", [case for case in CORPUS if case["group"] == "snapshot"], ids=lambda case: case["id"])
def test_runtime_snapshot_oracles_use_the_real_python_response(case):
    from fastapi.testclient import TestClient
    import runpy
    loader = runpy.run_path(str(Path(__file__).parent / "test_admin_mvp.py"))
    client = loader["_client"](loader["StubGenerationBackend"]("Must not generate snapshot facts"))
    response = client.post("/v1/assistants/chat", json=case["request"])
    assert check_response(case, response.status_code, response.json()) == []
