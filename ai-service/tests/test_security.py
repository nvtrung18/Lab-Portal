from __future__ import annotations

import asyncio
from concurrent.futures import ThreadPoolExecutor
import json
import logging
from pathlib import Path
import re

import pytest
from fastapi import Request
from fastapi.testclient import TestClient
from pydantic import SecretStr, ValidationError

from app.config import Settings
from app.main import create_app
from app.models import AssistantKey
from app.output_validation import OutputSchemaRegistry, StructuredOutputValidator
from app.profiles import ProfileLoader


INTERNAL_TOKEN = "test-only-internal-service-token"
INTERNAL_HEADERS = {"X-Internal-Service-Token": INTERNAL_TOKEN}
VALID_REQUEST_ID = "spring-request-123"
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
VALID_REQUEST = {
    "assistantKey": "LAB_ASSISTANT",
    "input": "Summarize the authorized lab context.",
    "authorizedContext": {"lab": {"id": 17, "name": "Chemistry Lab"}},
}


def _settings(*, timeout: float = 5.0) -> Settings:
    return Settings(
        internal_service_token=SecretStr(INTERNAL_TOKEN),
        request_timeout_seconds=timeout,
    )


def _client(*, timeout: float = 5.0, authenticated: bool = True) -> TestClient:
    headers = INTERNAL_HEADERS if authenticated else None
    return TestClient(create_app(_settings(timeout=timeout)), headers=headers)


def test_security_configuration_requires_a_nonblank_header_safe_token() -> None:
    with pytest.raises(ValidationError):
        Settings.model_validate({})
    for unsafe_token in ("", "   ", "contains space", "line\nbreak"):
        with pytest.raises(ValidationError):
            Settings(internal_service_token=SecretStr(unsafe_token))


def test_settings_never_repr_or_serialize_the_internal_token() -> None:
    settings = _settings()

    assert INTERNAL_TOKEN not in repr(settings)
    assert "internal_service_token" not in settings.model_dump()
    assert INTERNAL_TOKEN not in settings.model_dump_json()


def test_valid_internal_service_token_reaches_health_endpoint() -> None:
    response = _client().get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "ai-service"}


@pytest.mark.parametrize(
    ("path", "method"),
    [
        ("/health", "GET"),
        ("/ready", "GET"),
        ("/model-info", "GET"),
        ("/v1/assistants/chat", "POST"),
        ("/v1/assistants/tool-request", "POST"),
        ("/v1/assistants/suggestions", "POST"),
        ("/v1/research/suggestions", "POST"),
    ],
)
def test_missing_token_fails_closed_for_every_internal_endpoint(path: str, method: str) -> None:
    client = _client(authenticated=False)

    response = (
        client.get(path)
        if method == "GET"
        else client.post(path, json=VALID_REQUEST)
    )

    assert response.status_code == 401
    assert response.json()["errorCode"] == "AI_INTERNAL_AUTH_FAILED"
    assert response.json()["message"] == "Internal service authentication failed."
    assert response.json()["retryable"] is False
    assert response.json()["requestId"] == response.headers["X-Request-Id"]


@pytest.mark.parametrize(
    "headers",
    [
        {"X-Internal-Service-Token": "incorrect-token"},
        {"Authorization": "Bearer user.jwt.value"},
        INTERNAL_HEADERS | {"Authorization": "Bearer user.jwt.value"},
        INTERNAL_HEADERS | {"Authorization": "Basic forwarded-user-credential"},
    ],
)
def test_invalid_token_or_forwarded_authorization_uses_one_fail_closed_error(
    headers: dict[str, str],
) -> None:
    response = _client(authenticated=False).get(
        "/health",
        headers=headers | {"X-Request-Id": VALID_REQUEST_ID},
    )

    assert response.status_code == 401
    assert response.json() == {
        "errorCode": "AI_INTERNAL_AUTH_FAILED",
        "message": "Internal service authentication failed.",
        "retryable": False,
        "requestId": VALID_REQUEST_ID,
    }
    assert response.headers["X-Request-Id"] == VALID_REQUEST_ID
    assert "user.jwt.value" not in response.text


def test_authentication_runs_before_body_or_assistant_validation() -> None:
    response = _client(authenticated=False).post(
        "/v1/assistants/chat",
        content="not-json-with-user.jwt.value",
        headers={"content-type": "application/json", "X-Request-Id": VALID_REQUEST_ID},
    )

    assert response.status_code == 401
    assert response.json()["errorCode"] == "AI_INTERNAL_AUTH_FAILED"
    assert "user.jwt.value" not in response.text


def test_valid_request_id_is_echoed_and_correlates_error_body() -> None:
    response = _client().post(
        "/v1/assistants/chat",
        json=VALID_REQUEST,
        headers={"X-Request-Id": VALID_REQUEST_ID},
    )

    assert response.status_code == 503
    assert response.headers["X-Request-Id"] == VALID_REQUEST_ID
    assert response.json()["requestId"] == VALID_REQUEST_ID
    assert response.json()["errorCode"] == "AI_MODEL_NOT_READY"


def test_missing_request_id_generates_a_safe_opaque_response_header() -> None:
    response = _client().get("/health")

    request_id = response.headers["X-Request-Id"]
    assert REQUEST_ID_PATTERN.fullmatch(request_id)
    assert request_id not in json.dumps(response.json())


@pytest.mark.parametrize("request_id", ["", "bad/request", "bad,second", "x" * 129])
def test_malformed_request_id_is_rejected_without_echoing_it(request_id: str) -> None:
    response = _client().get("/health", headers={"X-Request-Id": request_id})

    assert response.status_code == 400
    assert response.json()["errorCode"] == "AI_INVALID_REQUEST_ID"
    assert response.json()["requestId"] == response.headers["X-Request-Id"]
    assert REQUEST_ID_PATTERN.fullmatch(response.headers["X-Request-Id"])
    assert response.headers["X-Request-Id"] != request_id


def test_concurrent_requests_do_not_leak_request_ids() -> None:
    request_ids = [f"concurrent-request-{index}" for index in range(12)]
    with _client() as client:
        def call(request_id: str) -> tuple[str, int, str]:
            response = client.get("/health", headers={"X-Request-Id": request_id})
            return request_id, response.status_code, response.headers["X-Request-Id"]

        with ThreadPoolExecutor(max_workers=6) as executor:
            results = list(executor.map(call, request_ids))

    assert results == [(request_id, 200, request_id) for request_id in request_ids]


def test_request_id_does_not_change_configuration_identities() -> None:
    test_app = create_app(_settings())
    identities_before = (
        tuple(
            profile.profile_identity
            for profile in test_app.state.profile_loader.profiles.values()
        ),
        test_app.state.artifact_loader.artifact_identity,
        test_app.state.output_schema_registry.registry_identity,
    )
    client = TestClient(test_app, headers=INTERNAL_HEADERS)

    client.get("/health", headers={"X-Request-Id": "first-request"})
    client.get("/health", headers={"X-Request-Id": "second-request"})

    identities_after = (
        tuple(
            profile.profile_identity
            for profile in test_app.state.profile_loader.profiles.values()
        ),
        test_app.state.artifact_loader.artifact_identity,
        test_app.state.output_schema_registry.registry_identity,
    )
    assert identities_after == identities_before


def test_timeout_is_enforced_with_sanitized_correlated_error() -> None:
    test_app = create_app(_settings(timeout=0.01))

    @test_app.get("/_test/slow")
    async def slow() -> dict[str, bool]:
        await asyncio.sleep(0.2)
        return {"completed": True}

    response = TestClient(test_app, headers=INTERNAL_HEADERS).get(
        "/_test/slow",
        headers={"X-Request-Id": VALID_REQUEST_ID, "X-Request-Timeout": "999"},
    )

    assert response.status_code == 504
    assert response.headers["X-Request-Id"] == VALID_REQUEST_ID
    assert response.json() == {
        "errorCode": "AI_REQUEST_TIMEOUT",
        "message": "AI request processing timed out.",
        "retryable": True,
        "requestId": VALID_REQUEST_ID,
    }
    assert "TimeoutError" not in response.text
    assert "CancelledError" not in response.text


def test_validation_error_keeps_stable_envelope_and_request_id() -> None:
    response = _client().post(
        "/v1/assistants/chat",
        json=VALID_REQUEST | {"assistantKey": "UNKNOWN_ASSISTANT"},
        headers={"X-Request-Id": VALID_REQUEST_ID},
    )

    assert response.status_code == 422
    assert response.json() == {
        "errorCode": "AI_INVALID_REQUEST",
        "message": "Request validation failed.",
        "retryable": False,
        "requestId": VALID_REQUEST_ID,
    }


def test_unexpected_exception_response_and_log_are_sanitized(caplog) -> None:
    private_value = "private-value-that-must-not-escape"
    test_app = create_app(_settings())

    @test_app.get("/_test/boom")
    def boom() -> None:
        raise RuntimeError(f"failure at C:\\private\\model.bin using {private_value}")

    caplog.set_level(logging.INFO, logger="app.security")
    response = TestClient(
        test_app,
        headers=INTERNAL_HEADERS,
        raise_server_exceptions=False,
    ).get("/_test/boom", headers={"X-Request-Id": VALID_REQUEST_ID})

    assert response.status_code == 500
    assert response.json() == {
        "errorCode": "AI_INTERNAL_ERROR",
        "message": "An unexpected server error occurred.",
        "retryable": False,
        "requestId": VALID_REQUEST_ID,
    }
    exposed = response.text + caplog.text
    assert private_value not in exposed
    assert "C:\\private" not in exposed
    assert "Traceback" not in exposed
    assert "RuntimeError" not in exposed


def test_request_logging_contains_only_safe_operational_fields(caplog) -> None:
    prompt = "prompt-value-that-must-not-be-logged"
    query_secret = "query-value-that-must-not-be-logged"
    authorization = "Bearer authorization-value-that-must-not-be-logged"
    cookie = "cookie-value-that-must-not-be-logged"
    caplog.set_level(logging.INFO, logger="app.security")
    client = _client()

    response = client.post(
        f"/v1/assistants/chat?api_key={query_secret}",
        json=VALID_REQUEST | {"input": prompt},
        headers={
            "X-Request-Id": VALID_REQUEST_ID,
            "Authorization": authorization,
            "Cookie": f"session={cookie}",
        },
    )

    assert response.status_code == 401
    assert VALID_REQUEST_ID in caplog.text
    assert "GET" not in caplog.text
    assert "POST" in caplog.text
    assert "/v1/assistants/chat" in caplog.text
    for sensitive in (INTERNAL_TOKEN, prompt, query_secret, authorization, cookie, "api_key"):
        assert sensitive not in caplog.text


def test_token_never_appears_in_success_or_error_responses_or_logs(caplog) -> None:
    caplog.set_level(logging.INFO, logger="app.security")
    client = _client()
    responses = [
        client.get("/health"),
        client.get("/ready"),
        client.get("/model-info"),
        client.post("/v1/assistants/chat", json=VALID_REQUEST),
        _client(authenticated=False).get(
            "/health",
            headers={"X-Internal-Service-Token": INTERNAL_TOKEN + "-incorrect"},
        ),
    ]

    assert INTERNAL_TOKEN not in json.dumps(
        [{"body": response.text, "headers": dict(response.headers)} for response in responses]
    )
    assert INTERNAL_TOKEN not in caplog.text


def test_health_readiness_and_model_info_truth_remain_unchanged_behind_authentication() -> None:
    client = _client()

    health = client.get("/health")
    readiness = client.get("/ready")
    model_info = client.get("/model-info")

    assert health.status_code == 200
    assert health.json() == {"status": "UP", "service": "ai-service"}
    assert readiness.status_code == 503
    assert readiness.json()["ready"] is False
    assert readiness.json()["modelStatus"] == "NOT_LOADED"
    assert model_info.status_code == 200
    assert model_info.json()["modelLoaded"] is False
    assert model_info.json()["ready"] is False


def test_architecture_has_no_jwt_database_sql_or_dynamic_execution_dependencies() -> None:
    service_root = Path(__file__).resolve().parents[1]
    requirements = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted(service_root.glob("requirements*.txt"))
    ).lower()
    app_source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted((service_root / "app").rglob("*.py"))
    ).lower()

    forbidden_dependencies = (
        "pyjwt",
        "python-jose",
        "oauthlib",
        "sqlalchemy",
        "pymysql",
        "mysqlclient",
        "mysql-connector",
        "asyncmy",
        "psycopg",
    )
    assert not any(dependency in requirements for dependency in forbidden_dependencies)
    assert not re.search(r"^(?:from|import)\s+(?:jwt|jose|oauth|sqlalchemy|pymysql|mysql|asyncmy|psycopg)\b", app_source, re.MULTILINE)
    assert not re.search(r"\b(?:select|insert|update|delete)\s+(?:from|into|set)\b", app_source)
    assert not re.search(r"\b(?:eval|exec)\s*\(|\bimportlib\b|\bsubprocess\b", app_source)


def test_p8_t4_tool_validation_remains_advisory_and_non_executable() -> None:
    profiles = ProfileLoader.from_file(_settings().profile_config_path)
    registry = OutputSchemaRegistry.from_file(_settings().output_schema_config_path, profiles)
    validator = StructuredOutputValidator(registry)
    profile = profiles.get_profile(AssistantKey.LAB_ASSISTANT)
    resource = {"resourceType": "TIME_SLOT", "resourceId": "slot-17"}
    candidate = json.dumps(
        {
            "assistantKey": "LAB_ASSISTANT",
            "schemaVersion": "v1",
            "toolId": "lab.slot.read",
            "arguments": {"resource": resource},
        }
    )

    result = validator.validate(profile, "TOOL_REQUEST", candidate, [resource])

    assert result.validation_status == "VALID"
    assert result.execution_eligibility == "REQUIRES_SPRING_AUTHORIZATION"
    assert result.executable is False
