from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.config import DEFAULT_PROFILE_CONFIG_PATH
from app.main import create_app
from app.models import AssistantKey
from app.profiles import ProfileConfigurationError, ProfileLoader, UnknownAssistantProfileError


def _config() -> dict:
    return json.loads(DEFAULT_PROFILE_CONFIG_PATH.read_text(encoding="utf-8"))


def _load_from(tmp_path: Path, config: dict) -> ProfileLoader:
    path = tmp_path / "assistant-profiles.json"
    path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    return ProfileLoader.from_file(path)


def _assert_invalid(tmp_path: Path, config: dict, expected_code: str) -> None:
    with pytest.raises(ProfileConfigurationError) as error:
        _load_from(tmp_path, config)
    assert error.value.code == expected_code
    assert str(tmp_path) not in str(error.value)


@pytest.mark.parametrize("assistant_key", list(AssistantKey))
def test_each_required_assistant_profile_loads(assistant_key: AssistantKey) -> None:
    profile = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH).get_profile(assistant_key)

    assert profile.assistant_key is assistant_key
    expected_version = "2.0.0" if assistant_key is AssistantKey.RESEARCH_ASSISTANT else "1.0.0"
    assert profile.profile_version == expected_version
    assert profile.prompt.system_prompt
    assert profile.model_profile.base_model_identifier == "Qwen/Qwen3-4B-Instruct-2507"
    assert profile.model_profile.base_model_revision == "cdbee75f17c01a7cc42f958dc650907174af0554"
    assert profile.model_profile.serving_mode == "METADATA_ONLY"


def test_profiles_load_independently_with_distinct_prompt_and_profile_identities() -> None:
    loader = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)
    profiles = [loader.get_profile(key) for key in AssistantKey]

    assert len({profile.assistant_key for profile in profiles}) == 3
    assert len({profile.prompt.version for profile in profiles}) == 3
    assert len({profile.prompt.system_prompt for profile in profiles}) == 3
    assert len({profile.profile_identity for profile in profiles}) == 3
    assert all(left is not right for index, left in enumerate(profiles) for right in profiles[index + 1 :])


def test_unknown_assistant_key_fails_without_fallback() -> None:
    loader = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)

    with pytest.raises(UnknownAssistantProfileError, match="Unknown assistant profile"):
        loader.get_profile("UNKNOWN_ASSISTANT")


def test_duplicate_profile_definition_fails_closed(tmp_path: Path) -> None:
    config = _config()
    entries = list(config["profiles"].items())
    entries.insert(1, entries[0])
    profile_json = ",".join(f"{json.dumps(key)}:{json.dumps(value)}" for key, value in entries)
    raw = (
        f'{{"schemaVersion":"{config["schemaVersion"]}",'
        f'"status":"{config["status"]}","profiles":{{{profile_json}}}}}'
    )
    path = tmp_path / "duplicate-profiles.json"
    path.write_text(raw, encoding="utf-8")

    with pytest.raises(ProfileConfigurationError) as error:
        ProfileLoader.from_file(path)

    assert error.value.code == "PROFILE_CONFIG_DUPLICATE_KEY"
    assert str(path) not in str(error.value)


def test_profile_map_key_and_embedded_assistant_key_must_match(tmp_path: Path) -> None:
    config = _config()
    config["profiles"]["ADMIN_ASSISTANT"]["assistantKey"] = "LAB_ASSISTANT"

    _assert_invalid(tmp_path, config, "PROFILE_KEY_MISMATCH")


def test_unknown_or_partial_profile_sets_fail_atomically(tmp_path: Path) -> None:
    partial = _config()
    partial["profiles"].pop("ADMIN_ASSISTANT")
    _assert_invalid(tmp_path, partial, "PROFILE_SET_INVALID")

    unknown = _config()
    unknown["profiles"]["UNKNOWN_ASSISTANT"] = unknown["profiles"].pop("ADMIN_ASSISTANT")
    unknown["profiles"]["UNKNOWN_ASSISTANT"]["assistantKey"] = "UNKNOWN_ASSISTANT"
    _assert_invalid(tmp_path, unknown, "PROFILE_SCHEMA_INVALID")


@pytest.mark.parametrize("version", ["1", "v1", "1.0", "1.0.0-beta"])
def test_invalid_profile_version_fails_closed(tmp_path: Path, version: str) -> None:
    config = _config()
    config["profiles"]["LAB_ASSISTANT"]["profileVersion"] = version

    _assert_invalid(tmp_path, config, "PROFILE_SCHEMA_INVALID")


def test_unsupported_bundle_schema_version_fails_closed(tmp_path: Path) -> None:
    config = _config()
    config["schemaVersion"] = "2.0.0"

    _assert_invalid(tmp_path, config, "PROFILE_SCHEMA_INVALID")


def test_missing_prompt_or_model_profile_fails_closed(tmp_path: Path) -> None:
    missing_prompt = _config()
    missing_prompt["profiles"]["ADMIN_ASSISTANT"].pop("prompt")
    _assert_invalid(tmp_path, missing_prompt, "PROFILE_SCHEMA_INVALID")

    missing_model = _config()
    missing_model["profiles"]["LAB_ASSISTANT"].pop("modelProfile")
    _assert_invalid(tmp_path, missing_model, "PROFILE_SCHEMA_INVALID")


def test_invalid_retrieval_namespace_fails_without_exposing_value(tmp_path: Path) -> None:
    config = _config()
    machine_path = "C:\\private\\research-knowledge"
    config["profiles"]["RESEARCH_ASSISTANT"]["retrievalNamespace"] = machine_path

    with pytest.raises(ProfileConfigurationError) as error:
        _load_from(tmp_path, config)

    assert error.value.code == "PROFILE_SCHEMA_INVALID"
    assert machine_path not in str(error.value)


def test_cross_domain_retrieval_namespace_is_rejected(tmp_path: Path) -> None:
    config = _config()
    config["profiles"]["LAB_ASSISTANT"]["retrievalNamespace"] = "research-knowledge"

    with pytest.raises(ProfileConfigurationError) as error:
        _load_from(tmp_path, config)

    assert error.value.code == "PROFILE_RETRIEVAL_NAMESPACE_MISMATCH"


def test_invalid_or_duplicate_tool_allowlist_fails_closed(tmp_path: Path) -> None:
    invalid = _config()
    invalid["profiles"]["LAB_ASSISTANT"]["allowedToolSchemas"].append("admin.system.summary")
    _assert_invalid(tmp_path, invalid, "PROFILE_TOOL_SCHEMA_INVALID")

    duplicate = _config()
    duplicate["profiles"]["LAB_ASSISTANT"]["allowedToolSchemas"].append("lab.policy.read")
    _assert_invalid(tmp_path, duplicate, "PROFILE_SCHEMA_INVALID")

    empty = _config()
    empty["profiles"]["LAB_ASSISTANT"]["allowedToolSchemas"] = [""]
    _assert_invalid(tmp_path, empty, "PROFILE_SCHEMA_INVALID")


def test_optional_adapter_reference_may_be_absent(tmp_path: Path) -> None:
    config = _config()
    config["profiles"]["ADMIN_ASSISTANT"].pop("adapter")

    profile = _load_from(tmp_path, config).get_profile(AssistantKey.ADMIN_ASSISTANT)

    assert profile.adapter is None


def test_malformed_adapter_reference_fails_closed(tmp_path: Path) -> None:
    config = _config()
    config["profiles"]["LAB_ASSISTANT"]["adapter"] = {
        "identifier": "../unapproved-adapter",
        "version": "candidate",
        "artifactChecksum": "not-a-checksum",
    }

    _assert_invalid(tmp_path, config, "PROFILE_SCHEMA_INVALID")


def test_research_profile_references_approved_adapter_but_remains_metadata_only() -> None:
    profile = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH).get_profile(AssistantKey.RESEARCH_ASSISTANT)

    assert profile.adapter is not None
    assert profile.adapter.identifier == "research-assistant-adapter"
    assert profile.adapter.version == "1.0.0"
    assert profile.adapter.artifact_checksum == "8c080cac001798a0826d5c72b553b791c31b7e32f9b10d4dd8b93d4f4f92830d"
    assert profile.model_profile.serving_mode == "METADATA_ONLY"


def test_loaded_profiles_are_immutable_and_read_only() -> None:
    loader = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)
    profile = loader.get_profile(AssistantKey.ADMIN_ASSISTANT)

    with pytest.raises(ValidationError):
        profile.profile_version = "9.9.9"
    with pytest.raises(TypeError):
        loader.profiles[AssistantKey.ADMIN_ASSISTANT] = profile


@pytest.mark.parametrize(
    ("path", "expected_error"),
    [
        ("/v1/assistants/chat", "AI_MODEL_NOT_READY"),
        ("/v1/assistants/tool-request", "AI_SERVICE_NOT_READY"),
        ("/v1/assistants/suggestions", "AI_SERVICE_NOT_READY"),
    ],
)
def test_assistant_requests_resolve_profile_then_remain_not_ready(path: str, expected_error: str) -> None:
    application = create_app()
    delegate = application.state.profile_loader
    resolved: list[AssistantKey] = []

    class RecordingLoader:
        def get_profile(self, assistant_key: AssistantKey):
            resolved.append(assistant_key)
            return delegate.get_profile(assistant_key)

    application.state.profile_loader = RecordingLoader()
    response = TestClient(
        application,
        headers={"X-Internal-Service-Token": "test-only-internal-service-token"},
    ).post(
        path,
        json={"assistantKey": "LAB_ASSISTANT", "input": "Use bounded context.", "authorizedContext": {}},
    )

    assert resolved == [AssistantKey.LAB_ASSISTANT]
    assert response.status_code == 503
    assert response.json()["errorCode"] == expected_error


def test_profile_loading_does_not_mark_model_or_serving_ready() -> None:
    application = create_app()
    client = TestClient(
        application,
        headers={"X-Internal-Service-Token": "test-only-internal-service-token"},
    )

    assert len(application.state.profile_loader.profiles) == 3
    assert client.get("/model-info").json()["status"] == "NOT_LOADED"
    readiness = client.get("/ready")
    assert readiness.status_code == 503
    assert readiness.json()["ready"] is False
    assert readiness.json()["modelStatus"] == "NOT_LOADED"


def test_profiles_contain_no_jwt_or_role_authorization_runtime_rules() -> None:
    loader = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)
    prohibited_phrases = ("jwt", "actorrole", "require admin", "admin users may", "role ==", "role in")

    for profile in loader.profiles.values():
        serialized = profile.model_dump_json(by_alias=True).lower()
        assert all(phrase not in serialized for phrase in prohibited_phrases)
        assert profile.safety.tool_execution_enabled is False
        assert profile.safety.retrieval_enabled is True


def test_profile_identity_is_deterministic() -> None:
    first = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)
    second = ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)

    assert {
        key: first.get_profile(key).profile_identity for key in AssistantKey
    } == {
        key: second.get_profile(key).profile_identity for key in AssistantKey
    }


def test_profile_identity_is_independent_of_absolute_config_path(tmp_path: Path) -> None:
    config = _config()
    first_path = tmp_path / "machine-a" / "config"
    second_path = tmp_path / "machine-b" / "different-config"
    first_path.mkdir(parents=True)
    second_path.mkdir(parents=True)

    first = _load_from(first_path, deepcopy(config))
    second = _load_from(second_path, deepcopy(config))

    for key in AssistantKey:
        first_identity = first.get_profile(key).profile_identity
        second_identity = second.get_profile(key).profile_identity
        assert first_identity == second_identity
        assert len(first_identity) == 64
        assert str(tmp_path) not in first_identity
