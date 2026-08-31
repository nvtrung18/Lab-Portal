from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.main import create_app
from app.models import AssistantKey
from app.runtime import RuntimeGeneration


INTERNAL_HEADERS = {
    "X-Internal-Service-Token": "phase-10c-test-token",
    "X-Request-Id": "p10c-request-1",
}


class StubGenerationBackend:
    def __init__(self, output: str) -> None:
        self.output = output
        self.calls = 0

    def generate(self, assistant_key, messages, *, json_output):
        self.calls += 1
        assert assistant_key is AssistantKey.ADMIN_ASSISTANT
        assert messages[0]["role"] == "system"
        assert "Spring-authorized" in messages[0]["content"]
        return RuntimeGeneration(text=self.output, prompt_tokens=17, completion_tokens=6)


class ArtifactReadyLoader:
    def __init__(self, delegate) -> None:
        self._delegate = delegate
        self.states = {
            key: state.model_copy(
                update={
                    "adapter_loaded": state.adapter_status == "APPROVED",
                    "ready": state.adapter_status in {"APPROVED", "NOT_AVAILABLE"},
                }
            )
            for key, state in delegate.states.items()
        }
        self.ready = True

    def get_state(self, assistant_key):
        return self.states[AssistantKey(assistant_key)]

    def __getattr__(self, name):
        return getattr(self._delegate, name)


def _client(backend: StubGenerationBackend) -> TestClient:
    settings = Settings(internal_service_token=SecretStr("phase-10c-test-token"))
    application = create_app(settings, runtime_backend=backend)
    application.state.artifact_loader = ArtifactReadyLoader(application.state.artifact_loader)
    return TestClient(application, headers=INTERNAL_HEADERS)


def _request(tool_id: str, resource_type: str, resource_id: int | None):
    target_user = None
    if resource_type == "USER_ACCOUNT":
        target_user = {"id": resource_id, "status": "ACTIVE", "active": True}
    audit_buckets = {"items": [], "truncated": False}
    if tool_id == "admin.audit.summary":
        audit_buckets = {
            "items": [
                {"day": "2026-08-31", "module": "AUTH", "action": "LOGIN", "count": 3}
            ],
            "truncated": False,
        }
    return {
        "assistantKey": "ADMIN_ASSISTANT",
        "input": "Use only the authorized administrative context.",
        "authorizedContext": {
            "domain": "ADMIN",
            "contextVersion": "P5A-T5-v1",
            "context": {
                "systemSummary": {"activeUserCount": 8, "registeredUserCount": 10},
                "targetUser": target_user,
                "auditBuckets": audit_buckets,
                "draftOnly": tool_id in {"admin.config.draft", "admin.account.action.draft"},
            },
            "allowedTools": [
                {
                    "toolId": tool_id,
                    "schemaVersion": "v1",
                    "resourceType": resource_type,
                    "parentResourceType": None,
                    "argumentNames": ["resource"],
                    "riskBoundary": (
                        "DRAFT_ONLY"
                        if tool_id in {"admin.config.draft", "admin.account.action.draft"}
                        else "READ_ONLY"
                    ),
                }
            ],
            "resources": [{"resourceType": resource_type, "resourceId": resource_id}],
            "authorizedRetrieval": {"namespace": "admin-knowledge", "chunks": []},
        },
    }


def test_admin_system_summary_returns_grounded_chat_response() -> None:
    backend = StubGenerationBackend("Eight of ten registered users are active.")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("admin.system.summary", "SYSTEM", None),
    )

    assert response.status_code == 200
    assert response.json() == {
        "assistantKey": "ADMIN_ASSISTANT",
        "answer": "Eight of ten registered users are active.",
        "promptTokens": 17,
        "completionTokens": 6,
        "metadata": {
            "resourceReferences": [{"resourceType": "SYSTEM", "resourceId": None}],
        },
    }


@pytest.mark.parametrize(
    ("tool_id", "resource_type", "resource_id"),
    [
        ("admin.audit.summary", "AUDIT_LOG", None),
        ("admin.user.status.lookup", "USER_ACCOUNT", 42),
    ],
)
def test_admin_other_read_capabilities_return_grounded_chat_response(
    tool_id: str,
    resource_type: str,
    resource_id: int | None,
) -> None:
    backend = StubGenerationBackend("Authorized administrative summary.")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request(tool_id, resource_type, resource_id),
    )

    assert response.status_code == 200
    assert response.json()["answer"] == "Authorized administrative summary."
    assert response.json()["metadata"] == {
        "resourceReferences": [{"resourceType": resource_type, "resourceId": resource_id}],
    }


@pytest.mark.parametrize(
    ("tool_id", "resource_type", "resource_id", "subject"),
    [
        ("admin.config.draft", "SYSTEM_CONFIG", None, "SYSTEM_CONFIG"),
        ("admin.account.action.draft", "USER_ACCOUNT", 42, "42"),
    ],
)
def test_admin_drafts_are_non_executable_and_bound_to_authorized_subject(
    tool_id: str,
    resource_type: str,
    resource_id: int | None,
    subject: str,
) -> None:
    draft = {
        "kind": "ADMIN_ACCOUNT_DRAFT",
        "subject": subject,
        "actions": ["Prepare a human-reviewed change request; do not execute it."],
        "requiresHumanReview": True,
    }
    backend = StubGenerationBackend(json.dumps(draft))

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request(tool_id, resource_type, resource_id),
    )

    assert response.status_code == 200
    assert json.loads(response.json()["answer"]) == draft
    assert response.json()["metadata"] == {
        "resourceReferences": [{"resourceType": resource_type, "resourceId": resource_id}],
        "draftOnly": True,
    }


def test_admin_account_draft_for_another_subject_fails_closed() -> None:
    draft = {
        "kind": "ADMIN_ACCOUNT_DRAFT",
        "subject": "99",
        "actions": ["Suspend the other account."],
        "requiresHumanReview": True,
    }
    backend = StubGenerationBackend(json.dumps(draft))

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("admin.account.action.draft", "USER_ACCOUNT", 42),
    )

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}


@pytest.mark.parametrize(
    "mutate",
    [
        lambda request: request["authorizedContext"].update({"domain": "LAB"}),
        lambda request: request["authorizedContext"]["context"].update({"draftOnly": True}),
        lambda request: request["authorizedContext"]["context"]["systemSummary"].update(
            {"activeUserCount": 11}
        ),
        lambda request: request["authorizedContext"]["resources"][0].update(
            {"resourceType": "AUDIT_LOG"}
        ),
    ],
)
def test_admin_rejects_mismatched_authority_without_running_model(mutate) -> None:
    backend = StubGenerationBackend("Must not be returned")
    request = _request("admin.system.summary", "SYSTEM", None)
    mutate(request)

    response = _client(backend).post("/v1/assistants/chat", json=request)

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0


def test_admin_rejects_audit_context_beyond_spring_bound_without_running_model() -> None:
    backend = StubGenerationBackend("Must not be returned")
    request = _request("admin.audit.summary", "AUDIT_LOG", None)
    request["authorizedContext"]["context"]["auditBuckets"]["items"] = [
        {"day": "2026-08-31", "module": "AUTH", "action": "LOGIN", "count": index}
        for index in range(15)
    ]

    response = _client(backend).post("/v1/assistants/chat", json=request)

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0


def test_admin_missing_authorized_context_refuses_without_running_model() -> None:
    backend = StubGenerationBackend("Must not be returned")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json={
            "assistantKey": "ADMIN_ASSISTANT",
            "input": "Show protected system details.",
            "authorizedContext": {},
        },
    )

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0
