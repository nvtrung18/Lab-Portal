from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH, DEFAULT_PROFILE_CONFIG_PATH
from app.models import AssistantKey
from app.output_validation import (
    OutputSchemaConfigurationError,
    OutputSchemaRegistry,
    StructuredOutputValidator,
    UnknownOutputSchemaError,
)
from app.profiles import ProfileLoader


def _schema_config() -> dict:
    return json.loads(DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH.read_text(encoding="utf-8"))


def _profile_config() -> dict:
    return json.loads(DEFAULT_PROFILE_CONFIG_PATH.read_text(encoding="utf-8"))


def _profiles() -> ProfileLoader:
    return ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)


def _validator() -> StructuredOutputValidator:
    profiles = _profiles()
    registry = OutputSchemaRegistry.from_file(DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH, profiles)
    return StructuredOutputValidator(registry)


def _profile(assistant_key: AssistantKey):
    return _profiles().get_profile(assistant_key)


def _ref(resource_type: str, resource_id: object) -> dict:
    return {"resourceType": resource_type, "resourceId": resource_id}


def _tool_request(
    assistant_key: AssistantKey,
    tool_id: str,
    resource: dict,
    parent_resource: dict | None = None,
) -> str:
    arguments = {"resource": resource}
    if parent_resource is not None:
        arguments["parentResource"] = parent_resource
    return json.dumps(
        {
            "assistantKey": assistant_key.value,
            "schemaVersion": "v1",
            "toolId": tool_id,
            "arguments": arguments,
        }
    )


def _load_registry(tmp_path: Path, config: dict, profiles: ProfileLoader | None = None) -> OutputSchemaRegistry:
    path = tmp_path / "structured-output-schemas.json"
    path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    return OutputSchemaRegistry.from_file(path, profiles or _profiles())


def _assert_config_invalid(tmp_path: Path, config: dict, expected_code: str) -> None:
    with pytest.raises(OutputSchemaConfigurationError) as error:
        _load_registry(tmp_path, config)
    assert error.value.code == expected_code
    assert str(tmp_path) not in str(error.value)


def test_checked_in_schema_registry_loads_all_profile_bundles_and_tools() -> None:
    registry = OutputSchemaRegistry.from_file(DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH, _profiles())

    assert registry.schema_version == "1.0.0"
    assert len(registry.schemas) == 5
    assert len(registry.tools) == 17
    for assistant_key in AssistantKey:
        profile = _profiles().get_profile(assistant_key)
        schema = registry.get_schema(profile.schema_bundle)
        assert schema.assistant_key is assistant_key
        assert schema.output_type == "STRUCTURED_DRAFT"
        assert len(schema.schema_identity) == 64


def test_profile_tool_allowlists_resolve_to_matching_known_tool_schemas() -> None:
    profiles = _profiles()
    registry = OutputSchemaRegistry.from_file(DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH, profiles)

    for assistant_key in AssistantKey:
        profile = profiles.get_profile(assistant_key)
        for tool_id in profile.allowed_tool_schemas:
            tool = registry.get_tool(tool_id)
            assert tool.assistant_key is assistant_key
            assert tool.schema_version == "v1"


def test_unknown_schema_fails_closed_with_sanitized_error() -> None:
    registry = OutputSchemaRegistry.from_file(DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH, _profiles())

    with pytest.raises(UnknownOutputSchemaError) as error:
        registry.get_schema("private/C:/schema.json")

    assert error.value.code == "AI_UNKNOWN_SCHEMA"
    assert "C:/" not in str(error.value)


def test_schema_identities_are_deterministic_and_path_independent(tmp_path: Path) -> None:
    config = _schema_config()
    first_directory = tmp_path / "machine-a"
    second_directory = tmp_path / "machine-b" / "different"
    first_directory.mkdir(parents=True)
    second_directory.mkdir(parents=True)

    first = _load_registry(first_directory, deepcopy(config))
    second = _load_registry(second_directory, deepcopy(config))

    assert first.registry_identity == second.registry_identity
    assert {
        schema_id: schema.schema_identity for schema_id, schema in first.schemas.items()
    } == {
        schema_id: schema.schema_identity for schema_id, schema in second.schemas.items()
    }
    assert str(tmp_path) not in first.registry_identity


def test_malformed_json_and_unsupported_registry_version_fail_closed(tmp_path: Path) -> None:
    malformed = tmp_path / "malformed.json"
    malformed.write_text("{not-json", encoding="utf-8")

    with pytest.raises(OutputSchemaConfigurationError) as error:
        OutputSchemaRegistry.from_file(malformed, _profiles())

    assert error.value.code == "OUTPUT_SCHEMA_CONFIG_INVALID_JSON"
    assert str(malformed) not in str(error.value)

    unsupported = _schema_config()
    unsupported["schemaVersion"] = "2.0.0"
    _assert_config_invalid(tmp_path, unsupported, "OUTPUT_SCHEMA_CONFIG_INVALID")


def test_malformed_json_schema_definition_fails_closed(tmp_path: Path) -> None:
    config = _schema_config()
    config["schemas"][0]["schema"]["type"] = 17

    _assert_config_invalid(tmp_path, config, "OUTPUT_JSON_SCHEMA_INVALID")


def test_common_schema_id_cannot_be_rebound_to_another_output_type(tmp_path: Path) -> None:
    config = _schema_config()
    config["schemas"][0]["outputType"] = "TOOL_REQUEST"

    _assert_config_invalid(tmp_path, config, "OUTPUT_SCHEMA_BINDING_INVALID")


def test_external_json_schema_reference_is_rejected_without_network_resolution(tmp_path: Path) -> None:
    config = _schema_config()
    config["schemas"][0]["schema"]["properties"]["answer"] = {
        "$ref": "https://unapproved.example/schema.json"
    }

    _assert_config_invalid(tmp_path, config, "OUTPUT_JSON_SCHEMA_INVALID")


def test_unresolved_local_json_schema_reference_fails_at_registry_load(tmp_path: Path) -> None:
    config = _schema_config()
    config["schemas"][0]["schema"]["properties"]["answer"] = {
        "$ref": "#/$defs/missing"
    }

    _assert_config_invalid(tmp_path, config, "OUTPUT_JSON_SCHEMA_INVALID")


def test_duplicate_schema_ids_fail_closed(tmp_path: Path) -> None:
    config = _schema_config()
    duplicate = deepcopy(config["schemas"][0])
    config["schemas"].append(duplicate)

    _assert_config_invalid(tmp_path, config, "OUTPUT_SCHEMA_ID_DUPLICATE")


def test_profile_unknown_schema_bundle_fails_closed(tmp_path: Path) -> None:
    profiles_config = _profile_config()
    profiles_config["profiles"]["LAB_ASSISTANT"]["schemaBundle"] = "missing-output-v1"
    profile_path = tmp_path / "assistant-profiles.json"
    profile_path.write_text(json.dumps(profiles_config), encoding="utf-8")

    with pytest.raises(OutputSchemaConfigurationError) as error:
        _load_registry(tmp_path, _schema_config(), ProfileLoader.from_file(profile_path))

    assert error.value.code == "PROFILE_OUTPUT_SCHEMA_UNKNOWN"
    assert error.value.assistant_key is AssistantKey.LAB_ASSISTANT


def test_missing_profile_tool_schema_fails_closed(tmp_path: Path) -> None:
    config = _schema_config()
    config["tools"] = [tool for tool in config["tools"] if tool["toolId"] != "lab.booking.draft"]

    _assert_config_invalid(tmp_path, config, "PROFILE_TOOL_SCHEMA_UNKNOWN")


def test_valid_chat_response_passes_schema_validation_but_is_not_executable() -> None:
    candidate = json.dumps(
        {
            "assistantKey": "LAB_ASSISTANT",
            "answer": "The authorized slot is available.",
            "promptTokens": 10,
            "completionTokens": 8,
            "metadata": {},
        }
    )

    result = _validator().validate(_profile(AssistantKey.LAB_ASSISTANT), "CHAT_RESPONSE", candidate)

    assert result.validation_status == "VALID"
    assert result.execution_eligibility == "NOT_EXECUTABLE"
    assert result.executable is False
    assert result.diagnostics == ()


@pytest.mark.parametrize(
    ("candidate", "expected_code"),
    [
        ("{not-json", "AI_OUTPUT_INVALID_JSON"),
        ('{"answer":"ok"} trailing', "AI_OUTPUT_INVALID_JSON"),
        (b'\xff\xfe', "AI_OUTPUT_INVALID_JSON"),
        ("\ud800", "AI_OUTPUT_INVALID_JSON"),
        ('{"assistantKey":"LAB_ASSISTANT","answer":"one","answer":"two","promptTokens":1,"completionTokens":1,"metadata":{}}', "AI_OUTPUT_INVALID_JSON"),
    ],
)
def test_malformed_trailing_invalid_utf8_or_duplicate_json_fails_closed(candidate, expected_code: str) -> None:
    result = _validator().validate(_profile(AssistantKey.LAB_ASSISTANT), "CHAT_RESPONSE", candidate)

    assert result.validation_status == "INVALID"
    assert result.diagnostics == (expected_code,)
    assert result.execution_eligibility == "NOT_EXECUTABLE"
    assert result.executable is False


@pytest.mark.parametrize(
    "mutation",
    [
        lambda value: value.update({"unexpected": True}),
        lambda value: value.pop("answer"),
        lambda value: value.update({"promptTokens": "ten"}),
    ],
)
def test_unknown_missing_or_wrong_typed_chat_field_fails_schema_validation(mutation) -> None:
    candidate = {
        "assistantKey": "ADMIN_ASSISTANT",
        "answer": "Bounded response.",
        "promptTokens": 1,
        "completionTokens": 1,
        "metadata": {},
    }
    mutation(candidate)

    result = _validator().validate(
        _profile(AssistantKey.ADMIN_ASSISTANT),
        "CHAT_RESPONSE",
        json.dumps(candidate),
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_OUTPUT_SCHEMA_INVALID",)
    assert result.executable is False


def test_unknown_expected_output_type_fails_closed() -> None:
    result = _validator().validate(
        _profile(AssistantKey.ADMIN_ASSISTANT),
        "PRIVATE_SCHEMA",
        "{}",
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_UNKNOWN_SCHEMA",)
    assert result.executable is False


def test_unknown_tool_is_rejected_without_fuzzy_fallback() -> None:
    candidate = _tool_request(
        AssistantKey.LAB_ASSISTANT,
        "lab.slot.reed",
        _ref("TIME_SLOT", "slot-1"),
    )

    result = _validator().validate(
        _profile(AssistantKey.LAB_ASSISTANT),
        "TOOL_REQUEST",
        candidate,
        [_ref("TIME_SLOT", "slot-1")],
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_UNKNOWN_TOOL",)
    assert result.executable is False


@pytest.mark.parametrize(
    ("assistant_key", "foreign_tool", "resource"),
    [
        (AssistantKey.RESEARCH_ASSISTANT, "admin.system.summary", _ref("SYSTEM", None)),
        (AssistantKey.LAB_ASSISTANT, "research.project.summary", _ref("PROJECT", "project-1")),
        (AssistantKey.ADMIN_ASSISTANT, "lab.slot.read", _ref("TIME_SLOT", "slot-1")),
    ],
)
def test_known_cross_assistant_tool_is_not_allowed(
    assistant_key: AssistantKey,
    foreign_tool: str,
    resource: dict,
) -> None:
    candidate = _tool_request(assistant_key, foreign_tool, resource)

    result = _validator().validate(
        _profile(assistant_key),
        "TOOL_REQUEST",
        candidate,
        [resource],
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_TOOL_NOT_ALLOWED",)
    assert result.executable is False


def test_valid_allowed_tool_requires_spring_authorization_and_is_never_executable_in_python() -> None:
    resource = _ref("TIME_SLOT", "slot-17")
    candidate = _tool_request(AssistantKey.LAB_ASSISTANT, "lab.slot.read", resource)

    result = _validator().validate(
        _profile(AssistantKey.LAB_ASSISTANT),
        "TOOL_REQUEST",
        candidate,
        [resource],
    )

    assert result.validation_status == "VALID"
    assert result.execution_eligibility == "REQUIRES_SPRING_AUTHORIZATION"
    assert result.executable is False
    assert result.diagnostics == ()


@pytest.mark.parametrize(
    "arguments",
    [
        {},
        {"resource": _ref("TIME_SLOT", "slot-17"), "unknown": "value"},
        {"resource": _ref("TIME_SLOT", "slot-17"), "parentResource": _ref("LABORATORY", "lab-1")},
    ],
)
def test_invalid_tool_argument_shape_fails_closed(arguments: dict) -> None:
    candidate = json.dumps(
        {
            "assistantKey": "LAB_ASSISTANT",
            "schemaVersion": "v1",
            "toolId": "lab.slot.read",
            "arguments": arguments,
        }
    )

    result = _validator().validate(
        _profile(AssistantKey.LAB_ASSISTANT),
        "TOOL_REQUEST",
        candidate,
        [_ref("TIME_SLOT", "slot-17")],
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_INVALID_TOOL_ARGUMENTS",)
    assert result.executable is False


def test_tool_resource_reference_in_authorized_set_passes() -> None:
    group = _ref("GROUP", "group-9")
    project = _ref("PROJECT", "project-3")
    candidate = _tool_request(
        AssistantKey.RESEARCH_ASSISTANT,
        "research.task.proposal.draft",
        group,
        project,
    )

    result = _validator().validate(
        _profile(AssistantKey.RESEARCH_ASSISTANT),
        "TOOL_REQUEST",
        candidate,
        [project, group],
    )

    assert result.validation_status == "VALID"
    assert result.execution_eligibility == "REQUIRES_SPRING_AUTHORIZATION"
    assert result.executable is False


@pytest.mark.parametrize(
    ("candidate_resource", "authorized"),
    [
        (_ref("TASK", "invented-task"), [_ref("TASK", "task-1")]),
        (_ref("PROJECT", "task-1"), [_ref("TASK", "task-1")]),
        ({"resourceType": "TASK", "resourceId": "../task"}, [_ref("TASK", "task-1")]),
    ],
)
def test_invented_mismatched_or_malformed_resource_reference_fails(
    candidate_resource: dict,
    authorized: list[dict],
) -> None:
    candidate = _tool_request(
        AssistantKey.RESEARCH_ASSISTANT,
        "research.assigned.task.read",
        candidate_resource,
    )

    result = _validator().validate(
        _profile(AssistantKey.RESEARCH_ASSISTANT),
        "TOOL_REQUEST",
        candidate,
        authorized,
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_INVALID_RESOURCE_REFERENCE",)
    assert result.executable is False


def test_duplicate_authorized_resource_envelope_fails_closed() -> None:
    resource = _ref("BOOKING", 19)
    candidate = _tool_request(AssistantKey.LAB_ASSISTANT, "lab.own.booking.read", resource)

    result = _validator().validate(
        _profile(AssistantKey.LAB_ASSISTANT),
        "TOOL_REQUEST",
        candidate,
        [resource, deepcopy(resource)],
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_INVALID_RESOURCE_REFERENCE",)
    assert result.executable is False


@pytest.mark.parametrize(
    ("assistant_key", "candidate", "authorized"),
    [
        (
            AssistantKey.ADMIN_ASSISTANT,
            {"kind": "ADMIN_ACCOUNT_DRAFT", "subject": "user-8", "actions": ["Suspend draft"], "requiresHumanReview": True},
            [],
        ),
        (
            AssistantKey.LAB_ASSISTANT,
            {"kind": "LAB_BOOKING_DRAFT", "labRef": "lab-2", "slotRef": "slot-7", "requestedPurpose": "Microscopy", "requiresHumanReview": True},
            [_ref("LABORATORY", "lab-2"), _ref("TIME_SLOT", "slot-7")],
        ),
        (
            AssistantKey.RESEARCH_ASSISTANT,
            {"kind": "RESEARCH_TASK_PROPOSAL_DRAFT", "projectRef": "project-2", "groupRef": "group-4", "taskTitle": "Draft task", "requiresHumanReview": True},
            [_ref("PROJECT", "project-2"), _ref("GROUP", "group-4")],
        ),
        (
            AssistantKey.RESEARCH_ASSISTANT,
            {"kind": "RESEARCH_TASK_SUGGESTION_DRAFT", "taskRef": "task-5", "suggestion": "Consider a smaller experiment.", "requiresHumanReview": True},
            [_ref("TASK", "task-5")],
        ),
    ],
)
def test_established_phase6_structured_drafts_validate_as_non_writing_drafts(
    assistant_key: AssistantKey,
    candidate: dict,
    authorized: list[dict],
) -> None:
    result = _validator().validate(
        _profile(assistant_key),
        "STRUCTURED_DRAFT",
        json.dumps(candidate),
        authorized,
    )

    assert result.validation_status == "VALID"
    assert result.execution_eligibility == "NOT_EXECUTABLE"
    assert result.executable is False


def test_cross_assistant_structured_draft_fails_profile_schema() -> None:
    research_draft = {
        "kind": "RESEARCH_TASK_SUGGESTION_DRAFT",
        "taskRef": "task-5",
        "suggestion": "Cross-domain draft.",
        "requiresHumanReview": True,
    }

    result = _validator().validate(
        _profile(AssistantKey.LAB_ASSISTANT),
        "STRUCTURED_DRAFT",
        json.dumps(research_draft),
        [_ref("TASK", "task-5")],
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_OUTPUT_SCHEMA_INVALID",)
    assert result.executable is False


def test_structured_draft_cannot_expand_beyond_authorized_references() -> None:
    candidate = {
        "kind": "LAB_BOOKING_DRAFT",
        "labRef": "lab-2",
        "slotRef": "invented-slot",
        "requestedPurpose": "Microscopy",
        "requiresHumanReview": True,
    }

    result = _validator().validate(
        _profile(AssistantKey.LAB_ASSISTANT),
        "STRUCTURED_DRAFT",
        json.dumps(candidate),
        [_ref("LABORATORY", "lab-2"), _ref("TIME_SLOT", "slot-7")],
    )

    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_INVALID_RESOURCE_REFERENCE",)
    assert result.executable is False


def test_validation_diagnostics_never_include_parser_or_local_path_details(tmp_path: Path) -> None:
    candidate = f'{{"privatePath":{json.dumps(str(tmp_path))},'
    result = _validator().validate(_profile(AssistantKey.ADMIN_ASSISTANT), "CHAT_RESPONSE", candidate)

    serialized = result.model_dump_json()
    assert result.validation_status == "INVALID"
    assert result.diagnostics == ("AI_OUTPUT_INVALID_JSON",)
    assert str(tmp_path) not in serialized
    assert "JSONDecodeError" not in serialized
    assert "Traceback" not in serialized


def test_output_schema_path_loads_from_environment(monkeypatch, tmp_path: Path) -> None:
    from app.config import Settings

    configured_path = tmp_path / "schemas.json"
    monkeypatch.setenv("AI_OUTPUT_SCHEMAS_PATH", str(configured_path))

    settings = Settings.from_env()

    assert settings.output_schema_config_path == configured_path


def test_application_starts_with_internal_output_validator_and_no_debug_endpoint() -> None:
    from app.main import create_app

    application = create_app()
    schema = TestClient(
        application,
        headers={"X-Internal-Service-Token": "test-only-internal-service-token"},
    ).get("/openapi.json").json()

    assert application.state.output_schema_registry.registry_identity
    assert isinstance(application.state.output_validator, StructuredOutputValidator)
    assert all("validate" not in path and "schema" not in path for path in schema["paths"])


def test_health_readiness_and_model_not_ready_routes_remain_compatible() -> None:
    from app.main import create_app

    client = TestClient(
        create_app(),
        headers={
            "X-Internal-Service-Token": "test-only-internal-service-token",
            "X-Request-Id": "test-request-123",
        },
    )
    health = client.get("/health")
    readiness = client.get("/ready")
    chat = client.post(
        "/v1/assistants/chat",
        json={"assistantKey": "LAB_ASSISTANT", "input": "Bounded request.", "authorizedContext": {}},
    )

    assert health.status_code == 200
    assert health.json() == {"status": "UP", "service": "ai-service"}
    assert readiness.status_code == 503
    assert readiness.json()["ready"] is False
    assert readiness.json()["modelLoaded"] is False
    assert chat.status_code == 503
    assert chat.json() == {
        "errorCode": "AI_MODEL_NOT_READY",
        "message": "AI model is not loaded.",
        "retryable": True,
        "requestId": "test-request-123",
    }
    serialized = json.dumps([health.json(), readiness.json(), chat.json()])
    assert "structured-output-schemas.json" not in serialized
    assert "SchemaError" not in serialized
