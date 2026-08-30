from __future__ import annotations

import json
from pathlib import Path

from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.main import app, create_app


VALID_REQUEST = {
    "assistantKey": "LAB_ASSISTANT",
    "input": "Summarize the authorized lab context.",
    "authorizedContext": {"lab": {"id": 17, "name": "Chemistry Lab"}},
}
TEST_REQUEST_ID = "test-request-123"
INTERNAL_HEADERS = {
    "X-Internal-Service-Token": "test-only-internal-service-token",
    "X-Request-Id": TEST_REQUEST_ID,
}


def test_application_imports_and_starts() -> None:
    with TestClient(app, headers=INTERNAL_HEADERS) as client:
        assert client.app.state.settings.service_name == "ai-service"


def test_settings_load_foundation_values_from_environment(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_NAME", "ai-service-test")
    monkeypatch.setenv("AI_ENVIRONMENT", "test")
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", "configured-token")
    monkeypatch.setenv("AI_REQUEST_TIMEOUT_SECONDS", "7.5")
    monkeypatch.setenv("AI_MODEL_ARTIFACTS_PATH", "runtime/model-artifacts.json")
    monkeypatch.setenv("AI_MODEL_ARTIFACT_ROOT", "runtime/artifacts")

    settings = Settings.from_env()

    assert settings.service_name == "ai-service-test"
    assert settings.environment == "test"
    assert settings.internal_service_token is not None
    assert settings.internal_service_token.get_secret_value() == "configured-token"
    assert settings.request_timeout_seconds == 7.5
    assert settings.artifact_config_path == Path("runtime/model-artifacts.json")
    assert settings.artifact_root == Path("runtime/artifacts")


def test_required_routes_are_registered() -> None:
    schema = TestClient(app, headers=INTERNAL_HEADERS).get("/openapi.json").json()

    assert {
        "/health",
        "/ready",
        "/model-info",
        "/v1/assistants/chat",
        "/v1/assistants/tool-request",
        "/v1/assistants/suggestions",
    }.issubset(schema["paths"])
    assert "/v1/research/suggestions" not in schema["paths"]
    for path in ("/v1/assistants/tool-request", "/v1/assistants/suggestions"):
        responses = schema["paths"][path]["post"]["responses"]
        assert "503" in responses
        assert "200" not in responses
    chat_responses = schema["paths"]["/v1/assistants/chat"]["post"]["responses"]
    assert {"200", "503"}.issubset(chat_responses)


def test_health_is_up_independently_of_model_readiness() -> None:
    client = TestClient(app, headers=INTERNAL_HEADERS)

    health = client.get("/health")
    readiness = client.get("/ready")

    assert health.status_code == 200
    assert health.json() == {"status": "UP", "service": "ai-service"}
    assert readiness.status_code == 503
    assert readiness.json()["modelStatus"] == "NOT_LOADED"


def test_ready_is_truthful_and_deterministic() -> None:
    response = TestClient(app, headers=INTERNAL_HEADERS).get("/ready")

    assert response.status_code == 503
    assert response.json() == {
        "status": "NOT_READY",
        "service": "ai-service",
        "serviceStatus": "READY",
        "modelStatus": "NOT_LOADED",
        "profileLoaded": True,
        "artifactValidated": False,
        "modelLoaded": False,
        "adapterLoaded": False,
        "ready": False,
    }


def test_model_info_reports_validated_metadata_without_claiming_a_loaded_model() -> None:
    response = TestClient(app, headers=INTERNAL_HEADERS).get("/model-info")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "NOT_LOADED"
    assert body["source"] == "SERVING_ARTIFACT_DESCRIPTOR"
    assert body["modelName"] == "Qwen/Qwen3-4B-Instruct-2507"
    assert body["modelVersion"] == "cdbee75f17c01a7cc42f958dc650907174af0554"
    assert body["modelRevision"] == "cdbee75f17c01a7cc42f958dc650907174af0554"
    assert body["artifactState"] == "METADATA_ONLY"
    assert body["artifactIdentity"] is None
    assert len(body["descriptorIdentity"]) == 64
    assert body["profileLoaded"] is True
    assert body["artifactValidated"] is False
    assert body["modelLoaded"] is False
    assert body["adapterLoaded"] is False
    assert body["ready"] is False
    assert {key: value["status"] for key, value in body["assistantAdapters"].items()} == {
        "ADMIN_ASSISTANT": "NOT_AVAILABLE",
        "LAB_ASSISTANT": "NOT_AVAILABLE",
        "RESEARCH_ASSISTANT": "APPROVED",
    }
    research = body["assistantAdapters"]["RESEARCH_ASSISTANT"]
    assert research["artifactValidated"] is True
    assert research["adapterLoaded"] is False


def test_chat_accepts_authorized_contract_and_returns_model_not_ready() -> None:
    response = TestClient(app, headers=INTERNAL_HEADERS).post(
        "/v1/assistants/chat",
        json=VALID_REQUEST,
    )

    assert response.status_code == 503
    assert response.json() == {
        "errorCode": "AI_MODEL_NOT_READY",
        "message": "AI model is not loaded.",
        "retryable": True,
        "requestId": TEST_REQUEST_ID,
    }


def test_tool_request_is_advisory_and_never_executes() -> None:
    request = VALID_REQUEST | {
        "input": "Run this function.",
        "authorizedContext": {"tool": {"name": "arbitrary.function"}},
    }

    response = TestClient(app, headers=INTERNAL_HEADERS).post("/v1/assistants/tool-request", json=request)

    assert response.status_code == 503
    assert response.json() == {
        "errorCode": "AI_SERVICE_NOT_READY",
        "message": "AI tool requests are not available.",
        "retryable": False,
        "requestId": TEST_REQUEST_ID,
    }


def test_suggestions_never_write_or_fabricate_output() -> None:
    response = TestClient(app, headers=INTERNAL_HEADERS).post("/v1/assistants/suggestions", json=VALID_REQUEST)

    assert response.status_code == 503
    assert response.json() == {
        "errorCode": "AI_SERVICE_NOT_READY",
        "message": "AI suggestions are not available.",
        "retryable": False,
        "requestId": TEST_REQUEST_ID,
    }


def test_legacy_suggestions_route_preserves_active_spring_compatibility() -> None:
    response = TestClient(app, headers=INTERNAL_HEADERS).post("/v1/research/suggestions", json=VALID_REQUEST)

    assert response.status_code == 503
    assert response.json()["errorCode"] == "AI_SERVICE_NOT_READY"


def test_unknown_assistant_key_is_rejected_with_safe_validation_error() -> None:
    request = VALID_REQUEST | {"assistantKey": "UNKNOWN_ASSISTANT"}

    response = TestClient(app, headers=INTERNAL_HEADERS).post("/v1/assistants/chat", json=request)

    assert response.status_code == 422
    assert response.json() == {
        "errorCode": "AI_INVALID_REQUEST",
        "message": "Request validation failed.",
        "retryable": False,
        "requestId": TEST_REQUEST_ID,
    }


def test_malformed_and_authorization_shaped_inputs_are_rejected_safely() -> None:
    client = TestClient(app, headers=INTERNAL_HEADERS)

    malformed = client.post("/v1/assistants/chat", content="not-json", headers={"content-type": "application/json"})
    prohibited = client.post(
        "/v1/assistants/chat",
        json=VALID_REQUEST | {"actorRole": "ADMIN", "userJwt": "secret.jwt.value"},
    )

    for response in (malformed, prohibited):
        assert response.status_code == 422
        assert response.json() == {
            "errorCode": "AI_INVALID_REQUEST",
            "message": "Request validation failed.",
            "retryable": False,
            "requestId": TEST_REQUEST_ID,
        }
        assert "secret.jwt.value" not in response.text


def test_unexpected_exception_response_is_sanitized(caplog) -> None:
    secret = "internal-token-that-must-never-escape"
    test_app = create_app(Settings(internal_service_token=SecretStr(secret)))

    @test_app.get("/_test/boom")
    def boom() -> None:
        raise RuntimeError(f"private failure at C:\\private\\model.bin using {secret}")

    response = TestClient(
        test_app,
        headers={"X-Internal-Service-Token": secret, "X-Request-Id": TEST_REQUEST_ID},
        raise_server_exceptions=False,
    ).get("/_test/boom")

    assert response.status_code == 500
    assert response.json() == {
        "errorCode": "AI_INTERNAL_ERROR",
        "message": "An unexpected server error occurred.",
        "retryable": False,
        "requestId": TEST_REQUEST_ID,
    }
    assert "Traceback" not in response.text
    assert "C:\\private" not in response.text
    assert secret not in response.text
    assert secret not in caplog.text


def test_responses_never_expose_the_configured_internal_service_token() -> None:
    secret = "configured-internal-service-token"
    test_app = create_app(Settings(internal_service_token=SecretStr(secret)))
    client = TestClient(
        test_app,
        headers={"X-Internal-Service-Token": secret, "X-Request-Id": TEST_REQUEST_ID},
    )

    responses = [
        client.get("/health"),
        client.get("/ready"),
        client.get("/model-info"),
        client.post("/v1/assistants/chat", json=VALID_REQUEST),
        client.post("/v1/assistants/tool-request", json=VALID_REQUEST),
        client.post("/v1/assistants/suggestions", json=VALID_REQUEST),
    ]

    assert secret not in json.dumps([response.json() for response in responses])
