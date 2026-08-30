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
    "X-Internal-Service-Token": "phase-10b-test-token",
    "X-Request-Id": "p10b-request-1",
}


class StubGenerationBackend:
    def __init__(self, output: str) -> None:
        self.output = output
        self.calls = 0

    def generate(self, assistant_key, messages, *, json_output):
        self.calls += 1
        assert assistant_key is AssistantKey.LAB_ASSISTANT
        assert messages[0]["role"] == "system"
        assert "Spring-authorized" in messages[0]["content"]
        return RuntimeGeneration(text=self.output, prompt_tokens=19, completion_tokens=7)


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
    settings = Settings(internal_service_token=SecretStr("phase-10b-test-token"))
    application = create_app(settings, runtime_backend=backend)
    application.state.artifact_loader = ArtifactReadyLoader(application.state.artifact_loader)
    return TestClient(application, headers=INTERNAL_HEADERS)


def _request(tool_id: str, resource_type: str, resource_id: int):
    lab_id = 10
    context = {
        "laboratory": {"id": lab_id, "name": "Authorized Lab", "status": "ACTIVE"},
        "slot": None,
        "booking": None,
        "managedSummary": None,
        "draftOnly": tool_id == "lab.booking.draft",
        "policyOrDraftEligibilityLabel": None,
    }
    if resource_type == "TIME_SLOT":
        context["slot"] = {
            "id": resource_id,
            "startTime": "2026-09-01T08:00:00Z",
            "endTime": "2026-09-01T09:00:00Z",
            "status": "AVAILABLE",
        }
    elif resource_type == "BOOKING":
        context["booking"] = {
            "id": resource_id,
            "status": "APPROVED",
            "slot": {
                "id": 17,
                "startTime": "2026-09-01T08:00:00Z",
                "endTime": "2026-09-01T09:00:00Z",
                "status": "BOOKED",
            },
        }
    if tool_id == "lab.managed.summary":
        context["managedSummary"] = {"activeSlotCount": 4, "activeBookingCount": 2}
    if tool_id == "lab.policy.read":
        context["policyOrDraftEligibilityLabel"] = "POLICY_INFORMATION_ONLY"
    if tool_id == "lab.booking.draft":
        context["policyOrDraftEligibilityLabel"] = "DRAFT_ONLY_NO_BOOKING_WRITE"

    return {
        "assistantKey": "LAB_ASSISTANT",
        "input": "Use only the authorized laboratory context.",
        "authorizedContext": {
            "domain": "LAB",
            "contextVersion": "P5A-T5-v1",
            "context": context,
            "allowedTools": [
                {
                    "toolId": tool_id,
                    "schemaVersion": "v1",
                    "resourceType": resource_type,
                    "parentResourceType": None,
                    "argumentNames": ["resource"],
                    "riskBoundary": "DRAFT_ONLY" if tool_id == "lab.booking.draft" else "READ_ONLY",
                }
            ],
            "resources": [{"resourceType": resource_type, "resourceId": resource_id}],
        },
    }


@pytest.mark.parametrize(
    ("tool_id", "resource_type", "resource_id"),
    [
        ("lab.slot.read", "TIME_SLOT", 17),
        ("lab.own.booking.read", "BOOKING", 21),
        ("lab.managed.summary", "LABORATORY", 10),
        ("lab.checkin.guidance", "BOOKING", 21),
    ],
)
def test_lab_read_capabilities_return_grounded_chat_response(
    tool_id: str,
    resource_type: str,
    resource_id: int,
) -> None:
    backend = StubGenerationBackend("Authorized laboratory guidance.")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request(tool_id, resource_type, resource_id),
    )

    assert response.status_code == 200
    assert response.json() == {
        "assistantKey": "LAB_ASSISTANT",
        "answer": "Authorized laboratory guidance.",
        "promptTokens": 19,
        "completionTokens": 7,
        "metadata": {
            "resourceReferences": [{"resourceType": resource_type, "resourceId": resource_id}],
        },
    }


def test_lab_booking_draft_accepts_integer_spring_context_references() -> None:
    draft = {
        "kind": "LAB_BOOKING_DRAFT",
        "labRef": 10,
        "slotRef": 17,
        "requestedPurpose": "Run the authorized validation session",
        "requiresHumanReview": True,
    }
    backend = StubGenerationBackend(json.dumps(draft))

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("lab.booking.draft", "TIME_SLOT", 17),
    )

    assert response.status_code == 200
    assert json.loads(response.json()["answer"]) == draft
    assert response.json()["metadata"] == {
        "resourceReferences": [
            {"resourceType": "TIME_SLOT", "resourceId": 17},
            {"resourceType": "LABORATORY", "resourceId": 10},
        ],
        "draftOnly": True,
    }


def test_lab_booking_draft_with_unapproved_lab_reference_fails_closed() -> None:
    draft = {
        "kind": "LAB_BOOKING_DRAFT",
        "labRef": 99,
        "slotRef": 17,
        "requestedPurpose": "Use another lab",
        "requiresHumanReview": True,
    }
    backend = StubGenerationBackend(json.dumps(draft))

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("lab.booking.draft", "TIME_SLOT", 17),
    )

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}


def test_policy_read_refuses_without_policy_content_and_does_not_run_model() -> None:
    backend = StubGenerationBackend("Must not invent policy content")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json=_request("lab.policy.read", "LABORATORY", 10),
    )

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0


def test_invalid_lab_context_refuses_without_running_model() -> None:
    backend = StubGenerationBackend("Must not be returned")

    response = _client(backend).post(
        "/v1/assistants/chat",
        json={"assistantKey": "LAB_ASSISTANT", "input": "Show another user's booking.", "authorizedContext": {}},
    )

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0


def test_capability_context_with_unrelated_booking_data_fails_closed() -> None:
    backend = StubGenerationBackend("Must not expose unrelated context")
    request = _request("lab.slot.read", "TIME_SLOT", 17)
    request["authorizedContext"]["context"]["booking"] = {
        "id": 99,
        "status": "APPROVED",
        "slot": request["authorizedContext"]["context"]["slot"],
    }

    response = _client(backend).post("/v1/assistants/chat", json=request)

    assert response.status_code == 200
    assert response.json()["metadata"] == {"safeRefusal": True}
    assert backend.calls == 0
