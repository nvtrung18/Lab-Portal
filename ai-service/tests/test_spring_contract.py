from __future__ import annotations

import asyncio
import json
from pathlib import Path
import re

import pytest
from fastapi.testclient import TestClient
from pydantic import SecretStr, ValidationError

from app.config import Settings
from app.main import create_app
from app.models import AssistantKey, AssistantRequest, ToolPlanningRequest, ToolPlanningResponse
from app.output_validation import OutputSchemaRegistry, StructuredOutputValidator
from app.profiles import ProfileLoader
from app.runtime import RuntimeGeneration


CONTRACT_PATH = Path(__file__).resolve().parents[2] / "contracts" / "ai-runtime-contract.json"
CONTRACT = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
INTERNAL_TOKEN = "contract-test-only-token"
REQUEST_ID = "contract-request-123"


def _settings(*, timeout: float = 5.0) -> Settings:
    return Settings(
        internal_service_token=SecretStr(INTERNAL_TOKEN),
        request_timeout_seconds=timeout,
    )


def _client(*, timeout: float = 5.0, authenticated: bool = True) -> TestClient:
    headers = None
    if authenticated:
        headers = {CONTRACT["headers"]["internalServiceToken"]: INTERNAL_TOKEN}
    return TestClient(create_app(_settings(timeout=timeout)), headers=headers)


def _artifact_ready_client() -> TestClient:
    class GenerationBackend:
        def generate(self, assistant_key, messages, *, json_output):
            assert assistant_key is AssistantKey.RESEARCH_ASSISTANT
            assert json_output is False
            return RuntimeGeneration("Authorized group summary.", 23, 11)

    app = create_app(_settings(), runtime_backend=GenerationBackend())
    delegate = app.state.artifact_loader

    class ArtifactReadyLoader:
        model_status = "READY"
        base_artifact_status = "APPROVED"
        base_artifact_identity = "596f707d711e040f5bd1cbab1ab0370e3ce1ba3314072bf76e73cb1cc2677706"
        artifact_validated = True
        model_loaded = True
        adapter_loaded = True
        ready = True
        states = {
            key: state.model_copy(
                update={
                    "adapter_loaded": state.adapter_status == "APPROVED",
                    "ready": state.adapter_status in {"APPROVED", "NOT_AVAILABLE"},
                }
            )
            for key, state in delegate.states.items()
        }

        def __getattr__(self, name):
            return getattr(delegate, name)

        def get_state(self, assistant_key):
            return self.states[AssistantKey(assistant_key)]

    app.state.artifact_loader = ArtifactReadyLoader()
    return TestClient(
        app,
        headers={CONTRACT["headers"]["internalServiceToken"]: INTERNAL_TOKEN},
    )


def _error_case(error_code: str) -> dict[str, object]:
    return next(
        case
        for case in CONTRACT["errorEnvelope"]["cases"]
        if case["body"]["errorCode"] == error_code
    )


def _assert_error(response, error_code: str, *, echoed_request_id: str | None = REQUEST_ID) -> None:
    case = _error_case(error_code)
    expected_body = case["body"]
    body = response.json()

    assert response.status_code == case["statusCode"]
    assert set(body) == set(CONTRACT["errorEnvelope"]["requiredFields"])
    assert body["errorCode"] == expected_body["errorCode"]
    assert body["message"] in case.get("allowedMessages", [expected_body["message"]])
    assert body["retryable"] is expected_body["retryable"]
    assert body["requestId"] == response.headers[CONTRACT["headers"]["requestId"]]
    if echoed_request_id is not None:
        assert body["requestId"] == echoed_request_id


def test_shared_manifest_matches_python_assistant_and_tool_catalogs() -> None:
    settings = _settings()
    profiles = ProfileLoader.from_file(settings.profile_config_path)
    registry = OutputSchemaRegistry.from_file(settings.output_schema_config_path, profiles)

    assert CONTRACT["schemaVersion"] == "1.3.0"
    assert set(CONTRACT["assistantKeys"]) == {key.value for key in AssistantKey}
    assert set(CONTRACT["assistantKeys"]) == {key.value for key in profiles.profiles}
    assert set(CONTRACT["toolIds"]) == set(registry.tools)


def test_spring_request_fixture_matches_python_camel_case_model() -> None:
    request_contract = CONTRACT["assistantRequest"]
    schema = AssistantRequest.model_json_schema(by_alias=True)
    request = AssistantRequest.model_validate(request_contract["example"])

    assert set(schema["required"]) == set(request_contract["requiredFields"])
    assert set(schema["properties"]) == (
        set(request_contract["requiredFields"]) | set(request_contract["optionalFields"])
    )
    assert request.model_dump(by_alias=True, mode="json") == request_contract["example"]
    assert AssistantRequest.model_validate(
        {"assistantKey": "LAB_ASSISTANT", "input": "Use Spring-projected context only."}
    ).authorized_context == {}

    for forbidden_field in request_contract["forbiddenAuthorityFields"]:
        assert forbidden_field not in schema["properties"]
        with pytest.raises(ValidationError):
            AssistantRequest.model_validate(request_contract["example"] | {forbidden_field: "unsafe"})


def test_tool_planning_contract_matches_python_camel_case_models() -> None:
    request_contract = CONTRACT["toolPlanningRequest"]
    response_contract = CONTRACT["toolPlanningResponse"]

    request = ToolPlanningRequest.model_validate(request_contract["example"])
    response = ToolPlanningResponse.model_validate(response_contract["example"])

    assert set(ToolPlanningRequest.model_json_schema(by_alias=True)["required"]) == set(
        request_contract["requiredFields"]
    )
    assert set(ToolPlanningResponse.model_json_schema(by_alias=True)["required"]) == set(
        response_contract["requiredFields"]
    )
    assert request.model_dump(by_alias=True, mode="json") == request_contract["example"]
    assert response.model_dump(by_alias=True, mode="json") == response_contract["example"]


def test_required_paths_and_hidden_legacy_alias_match_manifest() -> None:
    client = _client()
    openapi_paths = client.get("/openapi.json").json()["paths"]

    for route in CONTRACT["routes"].values():
        if route["openApi"]:
            assert route["path"] in openapi_paths
            assert route["method"].lower() in openapi_paths[route["path"]]
        else:
            assert route["path"] not in openapi_paths

    legacy = CONTRACT["routes"]["legacySuggestions"]
    response = client.request(
        legacy["method"],
        legacy["path"],
        json=CONTRACT["assistantRequest"]["example"],
        headers={CONTRACT["headers"]["requestId"]: REQUEST_ID},
    )
    _assert_error(response, CONTRACT["postErrors"]["legacySuggestions"])


def test_internal_headers_and_request_id_behavior_match_spring_contract() -> None:
    token_header = CONTRACT["headers"]["internalServiceToken"]
    request_id_header = CONTRACT["headers"]["requestId"]
    authorization_header = CONTRACT["headers"]["userAuthorization"]

    missing = _client(authenticated=False).get("/health", headers={request_id_header: REQUEST_ID})
    invalid = _client(authenticated=False).get(
        "/health",
        headers={token_header: "incorrect", request_id_header: REQUEST_ID},
    )
    forwarded_jwt = _client().get(
        "/health",
        headers={authorization_header: "Bearer user.jwt.value", request_id_header: REQUEST_ID},
    )
    for response in (missing, invalid, forwarded_jwt):
        _assert_error(response, "AI_INTERNAL_AUTH_FAILED")

    echoed = _client().get("/health", headers={request_id_header: REQUEST_ID})
    generated = _client().get("/health")
    assert echoed.status_code == 200
    assert echoed.headers[request_id_header] == REQUEST_ID
    assert generated.status_code == 200
    assert re.fullmatch(CONTRACT["requestIdPattern"], generated.headers[request_id_header])
    assert generated.headers[request_id_header] != REQUEST_ID


@pytest.mark.parametrize("assistant_key", CONTRACT["assistantKeys"])
def test_each_assistant_key_routes_without_becoming_authorization(assistant_key: str) -> None:
    request = CONTRACT["assistantRequest"]["example"] | {"assistantKey": assistant_key}
    response = _client().post(
        CONTRACT["routes"]["chat"]["path"],
        json=request,
        headers={CONTRACT["headers"]["requestId"]: REQUEST_ID},
    )

    _assert_error(response, "AI_MODEL_NOT_READY")
    assert CONTRACT["authority"] == {
        "businessOwner": "SPRING",
        "assistantKeyAuthorizes": False,
        "pythonAuthorizesResources": False,
        "pythonExecutesTools": False,
    }


def test_unknown_assistant_and_malformed_payload_fail_without_fallback() -> None:
    headers = {CONTRACT["headers"]["requestId"]: REQUEST_ID}
    unknown = CONTRACT["assistantRequest"]["example"] | {"assistantKey": "UNKNOWN_ASSISTANT"}
    unknown_response = _client().post(CONTRACT["routes"]["chat"]["path"], json=unknown, headers=headers)
    malformed_response = _client().post(
        CONTRACT["routes"]["chat"]["path"],
        content="not-json",
        headers=headers | {"content-type": "application/json"},
    )

    _assert_error(unknown_response, "AI_INVALID_REQUEST")
    _assert_error(malformed_response, "AI_INVALID_REQUEST")


def test_current_post_routes_return_only_truthful_not_ready_errors() -> None:
    client = _client()
    headers = {CONTRACT["headers"]["requestId"]: REQUEST_ID}

    for route_name, error_code in CONTRACT["postErrors"].items():
        route = CONTRACT["routes"][route_name]
        response = client.request(
            route["method"],
            route["path"],
            json=CONTRACT["assistantRequest"]["example"],
            headers=headers,
        )
        _assert_error(response, error_code)
        assert response.status_code == 503


def test_health_ready_and_model_info_keep_liveness_separate_from_readiness() -> None:
    client = _client()

    for route_name in ("health", "ready", "modelInfo"):
        route = CONTRACT["routes"][route_name]
        expected = CONTRACT["runtimeStates"][route_name]
        response = client.request(route["method"], route["path"])

        assert response.status_code == expected["statusCode"]
        assert expected["expectedFields"].items() <= response.json().items()

    assert CONTRACT["runtimeStates"]["health"]["statusCode"] == 200
    assert CONTRACT["runtimeStates"]["ready"]["statusCode"] == 503
    assert CONTRACT["runtimeStates"]["modelInfo"]["expectedFields"]["artifactState"] == "METADATA_ONLY"


def test_artifact_ready_responses_match_the_shared_spring_contract() -> None:
    client = _artifact_ready_client()
    expected = CONTRACT["runtimeStates"]["artifactReady"]

    for route_name in ("ready", "modelInfo"):
        route = CONTRACT["routes"][route_name]
        response = client.request(route["method"], route["path"])

        assert response.status_code == expected[route_name]["statusCode"]
        assert expected[route_name]["expectedFields"].items() <= response.json().items()


def test_loaded_research_model_returns_the_shared_mvp_chat_response() -> None:
    response = _artifact_ready_client().post(
        CONTRACT["routes"]["chat"]["path"],
        json=CONTRACT["researchMvpRequest"],
        headers={CONTRACT["headers"]["requestId"]: REQUEST_ID},
    )

    assert response.status_code == CONTRACT["artifactReadyPostResponses"]["chat"]["statusCode"]
    assert response.json() == CONTRACT["chatResponse"]["example"]


def test_all_current_error_statuses_use_the_shared_safe_envelope() -> None:
    request_id_header = CONTRACT["headers"]["requestId"]
    valid_headers = {request_id_header: REQUEST_ID}
    client = _client()
    app = create_app(_settings())

    @app.get("/_contract/boom")
    def boom() -> None:
        raise RuntimeError("private implementation detail")

    invalid_request_id = client.get("/health", headers={request_id_header: "bad/request-id"})
    missing_token = _client(authenticated=False).get("/health", headers=valid_headers)
    malformed = client.post(CONTRACT["routes"]["chat"]["path"], json={}, headers=valid_headers)
    model_not_ready = client.post(
        CONTRACT["routes"]["chat"]["path"],
        json=CONTRACT["assistantRequest"]["example"],
        headers=valid_headers,
    )
    service_not_ready = client.post(
        CONTRACT["routes"]["suggestions"]["path"],
        json=CONTRACT["assistantRequest"]["example"],
        headers=valid_headers,
    )
    internal_error = TestClient(
        app,
        headers={CONTRACT["headers"]["internalServiceToken"]: INTERNAL_TOKEN},
        raise_server_exceptions=False,
    ).get("/_contract/boom", headers=valid_headers)

    _assert_error(invalid_request_id, "AI_INVALID_REQUEST_ID", echoed_request_id=None)
    _assert_error(missing_token, "AI_INTERNAL_AUTH_FAILED")
    _assert_error(malformed, "AI_INVALID_REQUEST")
    _assert_error(model_not_ready, "AI_MODEL_NOT_READY")
    _assert_error(service_not_ready, "AI_SERVICE_NOT_READY")
    _assert_error(internal_error, "AI_INTERNAL_ERROR")
    assert "private implementation detail" not in internal_error.text


def test_python_timeout_matches_spring_retryable_error_contract() -> None:
    app = create_app(_settings(timeout=0.001))

    @app.get("/_contract/slow")
    async def slow() -> dict[str, bool]:
        await asyncio.sleep(0.05)
        return {"completed": True}

    response = TestClient(
        app,
        headers={CONTRACT["headers"]["internalServiceToken"]: INTERNAL_TOKEN},
    ).get("/_contract/slow", headers={CONTRACT["headers"]["requestId"]: REQUEST_ID})

    _assert_error(response, "AI_REQUEST_TIMEOUT")
    assert "TimeoutError" not in response.text


def test_structured_tool_result_is_reference_checked_and_never_authoritative() -> None:
    settings = _settings()
    profiles = ProfileLoader.from_file(settings.profile_config_path)
    registry = OutputSchemaRegistry.from_file(settings.output_schema_config_path, profiles)
    validator = StructuredOutputValidator(registry)
    contract = CONTRACT["toolValidation"]
    candidate = json.dumps(contract["candidate"])
    profile = profiles.get_profile(AssistantKey(contract["candidate"]["assistantKey"]))

    valid = validator.validate(profile, "TOOL_REQUEST", candidate, contract["authorizedResources"])
    invented_reference = validator.validate(profile, "TOOL_REQUEST", candidate, [])

    valid_body = valid.model_dump(by_alias=True, mode="json")
    assert contract["expected"].items() <= valid_body.items()
    assert invented_reference.validation_status == "INVALID"
    assert invented_reference.execution_eligibility == "NOT_EXECUTABLE"
    assert invented_reference.executable is False
    serialized = json.dumps(valid_body)
    assert all(state not in serialized for state in contract["forbiddenApprovalStates"])
