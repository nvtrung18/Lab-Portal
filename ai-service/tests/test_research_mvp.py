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
    "X-Internal-Service-Token": "phase-10-test-token",
    "X-Request-Id": "p10a-request-1",
}


class StubGenerationBackend:
    def __init__(self, output: str) -> None:
        self.output = output
        self.calls = 0

    def generate(self, assistant_key, messages, *, json_output):
        self.calls += 1
        assert assistant_key is AssistantKey.RESEARCH_ASSISTANT
        assert messages[0]["role"] == "system"
        assert "Spring-authorized" in messages[0]["content"]
        return RuntimeGeneration(
            text=self.output,
            prompt_tokens=23,
            completion_tokens=11,
        )


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
    settings = Settings(internal_service_token=SecretStr("phase-10-test-token"))
    application = create_app(settings, runtime_backend=backend)
    application.state.artifact_loader = ArtifactReadyLoader(application.state.artifact_loader)
    return TestClient(application, headers=INTERNAL_HEADERS)


def _request(tool_id: str, resource_type: str, resource_id: int, *, parent_id: int | None = None):
    resources = [{"resourceType": resource_type, "resourceId": resource_id}]
    if parent_id is not None:
        resources.append({"resourceType": "PROJECT", "resourceId": parent_id})
    return {
        "assistantKey": "RESEARCH_ASSISTANT",
        "input": "Use only the authorized context for this request.",
        "authorizedContext": {
            "domain": "RESEARCH",
            "contextVersion": "P5A-T5-v1",
            "context": {
                "research": {"project": {"id": parent_id or 20, "name": "Authorized project"}},
                "groups": {"items": [], "truncated": False},
                "milestones": {"items": [], "truncated": False},
                "tasks": {"items": [], "truncated": False},
                "selectedResourceId": resource_id,
                "draftOnly": ".draft" in tool_id,
            },
            "allowedTools": [
                {
                    "toolId": tool_id,
                    "schemaVersion": "v1",
                    "resourceType": resource_type,
                    "parentResourceType": "PROJECT" if parent_id is not None else None,
                    "argumentNames": ["parentResource", "resource"] if parent_id is not None else ["resource"],
                    "riskBoundary": "DRAFT_ONLY" if ".draft" in tool_id else "READ_ONLY",
                }
            ],
            "resources": resources,
        },
    }


@pytest.mark.parametrize(
    ("tool_id", "resource_type"),
    [
        ("research.project.summary", "PROJECT"),
        ("research.group.summary", "GROUP"),
        ("research.assigned.task.read", "TASK"),
    ],
)
def test_research_read_capabilities_return_grounded_chat_response(tool_id: str, resource_type: str) -> None:
    backend = StubGenerationBackend("Authorized group summary.")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request(tool_id, resource_type, 30),
    )

    assert response.status_code == 200
    assert response.json() == {
        "assistantKey": "RESEARCH_ASSISTANT",
        "answer": "Authorized group summary.",
        "promptTokens": 23,
        "completionTokens": 11,
        "metadata": {
            "resourceReferences": [{"resourceType": resource_type, "resourceId": 30}],
        },
    }


def test_research_task_proposal_accepts_integer_spring_resource_references() -> None:
    draft = {
        "kind": "RESEARCH_TASK_PROPOSAL_DRAFT",
        "projectRef": 20,
        "groupRef": 30,
        "taskTitle": "Validate the bounded experiment",
        "requiresHumanReview": True,
    }
    backend = StubGenerationBackend(json.dumps(draft))

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("research.task.proposal.draft", "GROUP", 30, parent_id=20),
    )

    assert response.status_code == 200
    assert json.loads(response.json()["answer"]) == draft
    assert response.json()["metadata"]["draftOnly"] is True


def test_research_task_suggestion_accepts_integer_spring_resource_reference() -> None:
    draft = {
        "kind": "RESEARCH_TASK_SUGGESTION_DRAFT",
        "taskRef": 31,
        "suggestion": "Split the experiment into one bounded validation step.",
        "requiresHumanReview": True,
    }
    backend = StubGenerationBackend(json.dumps(draft))

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("research.task.suggestion.draft", "TASK", 31),
    )

    assert response.status_code == 200
    assert json.loads(response.json()["answer"]) == draft
    assert response.json()["metadata"]["draftOnly"] is True


def test_invalid_model_draft_fails_closed_to_safe_refusal() -> None:
    backend = StubGenerationBackend('{"kind":"RESEARCH_TASK_PROPOSAL_DRAFT","approved":true}')

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("research.task.proposal.draft", "GROUP", 30, parent_id=20),
    )

    assert response.status_code == 200
    assert response.json()["answer"] == "I cannot provide that response from the authorized context available."
    assert response.json()["metadata"] == {"safeRefusal": True}


def test_missing_authorized_context_refuses_without_running_model() -> None:
    backend = StubGenerationBackend("Must not be returned")
    request = {
        "assistantKey": "RESEARCH_ASSISTANT",
        "input": "Summarize another group.",
        "authorizedContext": {},
    }

    response = _client(backend).post("/v1/assistants/chat", json=request)

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert response.json()["promptTokens"] == 0
    assert response.json()["completionTokens"] == 0
    assert backend.calls == 0


def test_report_review_refuses_while_report_content_is_source_limited() -> None:
    backend = StubGenerationBackend("Must not fabricate a review")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("research.report.review.draft", "REPORT", 40),
    )

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0


def test_phase_10b_lab_runtime_rejects_research_authorized_context() -> None:
    backend = StubGenerationBackend("Must not activate Lab yet")
    request = _request("research.group.summary", "GROUP", 30) | {"assistantKey": "LAB_ASSISTANT"}

    response = _client(backend).post("/v1/assistants/chat", json=request)

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0
