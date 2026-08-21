from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.artifacts import ArtifactConfigurationError, ArtifactLoader
from app.config import DEFAULT_ARTIFACT_CONFIG_PATH, DEFAULT_PROFILE_CONFIG_PATH
from app.main import create_app
from app.models import AssistantKey
from app.profiles import ProfileLoader


BASE_MODEL = "Qwen/Qwen3-4B-Instruct-2507"
BASE_REVISION = "cdbee75f17c01a7cc42f958dc650907174af0554"


def _config() -> dict:
    return json.loads(DEFAULT_ARTIFACT_CONFIG_PATH.read_text(encoding="utf-8"))


def _profiles() -> ProfileLoader:
    return ProfileLoader.from_file(DEFAULT_PROFILE_CONFIG_PATH)


def _profile_config() -> dict:
    return json.loads(DEFAULT_PROFILE_CONFIG_PATH.read_text(encoding="utf-8"))


def _load_from(tmp_path: Path, config: dict) -> ArtifactLoader:
    descriptor_path = tmp_path / "model-artifacts.json"
    descriptor_path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    artifact_root = tmp_path / "artifacts"
    artifact_root.mkdir(exist_ok=True)
    return ArtifactLoader.from_file(descriptor_path, artifact_root, _profiles())


def _assert_invalid(tmp_path: Path, config: dict, expected_code: str) -> None:
    with pytest.raises(ArtifactConfigurationError) as error:
        _load_from(tmp_path, config)
    assert error.value.code == expected_code
    assert str(tmp_path) not in str(error.value)


def _physical_artifact(
    artifact_root: Path,
    relative_path: str = "base/model.bin",
    content: bytes = b"approved-serving-artifact",
) -> dict:
    path = artifact_root / relative_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    checksum = hashlib.sha256(content).hexdigest()
    files = [{"path": relative_path, "sha256": checksum}]
    identity = hashlib.sha256(
        json.dumps(
            {"files": files},
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    ).hexdigest()
    return {"identity": identity, "files": files}


def _load_with_root(tmp_path: Path, config: dict) -> ArtifactLoader:
    descriptor_path = tmp_path / "model-artifacts.json"
    descriptor_path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    return ArtifactLoader.from_file(descriptor_path, tmp_path / "artifacts", _profiles())


def test_checked_in_descriptor_loads_deterministically() -> None:
    first = ArtifactLoader.from_file(DEFAULT_ARTIFACT_CONFIG_PATH, DEFAULT_ARTIFACT_CONFIG_PATH.parent, _profiles())
    second = ArtifactLoader.from_file(DEFAULT_ARTIFACT_CONFIG_PATH, DEFAULT_ARTIFACT_CONFIG_PATH.parent, _profiles())

    assert first.artifact_identity == second.artifact_identity
    assert len(first.artifact_identity) == 64
    assert first.base_model_identifier == BASE_MODEL
    assert first.base_model_revision == BASE_REVISION


def test_metadata_only_base_model_is_valid_metadata_but_not_loaded() -> None:
    loader = ArtifactLoader.from_file(
        DEFAULT_ARTIFACT_CONFIG_PATH,
        DEFAULT_ARTIFACT_CONFIG_PATH.parent,
        _profiles(),
    )

    assert loader.descriptor_status == "APPROVED"
    assert loader.base_artifact_status == "METADATA_ONLY"
    assert loader.profile_loaded is True
    assert loader.artifact_validated is False
    assert loader.model_loaded is False
    assert loader.ready is False


@pytest.mark.parametrize(
    ("assistant_key", "expected_status"),
    [
        (AssistantKey.ADMIN_ASSISTANT, "NOT_AVAILABLE"),
        (AssistantKey.LAB_ASSISTANT, "NOT_AVAILABLE"),
        (AssistantKey.RESEARCH_ASSISTANT, "BLOCKED"),
    ],
)
def test_each_assistant_has_truthful_optional_adapter_state(
    assistant_key: AssistantKey,
    expected_status: str,
) -> None:
    loader = ArtifactLoader.from_file(
        DEFAULT_ARTIFACT_CONFIG_PATH,
        DEFAULT_ARTIFACT_CONFIG_PATH.parent,
        _profiles(),
    )

    state = loader.get_state(assistant_key)

    assert state.adapter_status == expected_status
    assert state.adapter_identity is None
    assert state.adapter_artifact_validated is False
    assert state.adapter_loaded is False
    assert state.ready is False


def test_research_descriptor_does_not_claim_a_promoted_adapter() -> None:
    config = _config()
    research = config["assistantAdapters"]["RESEARCH_ASSISTANT"]

    assert research["status"] == "BLOCKED"
    assert research["artifact"] is None
    assert research["sourceDecision"]["outcome"] == "ADAPTER_REQUIRED+CANDIDATE_BUILD_BLOCKED"


@pytest.mark.parametrize("status", ["CANDIDATE_ONLY", "BLOCKED", "PENDING"])
def test_unapproved_adapter_status_never_becomes_serving_ready(tmp_path: Path, status: str) -> None:
    config = _config()
    config["assistantAdapters"]["RESEARCH_ASSISTANT"]["status"] = status

    loader = _load_from(tmp_path, config)
    state = loader.get_state(AssistantKey.RESEARCH_ASSISTANT)

    assert state.adapter_status == status
    assert state.adapter_loaded is False
    assert state.ready is False


def test_unknown_artifact_status_fails_closed(tmp_path: Path) -> None:
    config = _config()
    config["baseModel"]["status"] = "DOWNLOADED_MAYBE"

    _assert_invalid(tmp_path, config, "ARTIFACT_SCHEMA_INVALID")


def test_profile_model_identifier_mismatch_fails_closed(tmp_path: Path) -> None:
    config = _config()
    config["baseModel"]["identifier"] = "unapproved/model"

    _assert_invalid(tmp_path, config, "ARTIFACT_MODEL_MISMATCH")


def test_profile_model_revision_mismatch_fails_closed(tmp_path: Path) -> None:
    config = _config()
    config["baseModel"]["revision"] = "1" * 40

    _assert_invalid(tmp_path, config, "ARTIFACT_MODEL_REVISION_MISMATCH")


def test_approved_physical_base_artifact_is_checksum_validated_but_not_runtime_loaded(tmp_path: Path) -> None:
    config = _config()
    artifact_root = tmp_path / "artifacts"
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = _physical_artifact(artifact_root)

    loader = _load_with_root(tmp_path, config)

    assert loader.artifact_validated is True
    assert loader.model_loaded is False
    assert loader.ready is False


def test_artifact_checksum_mismatch_fails_closed(tmp_path: Path) -> None:
    config = _config()
    artifact_root = tmp_path / "artifacts"
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = _physical_artifact(artifact_root)
    config["baseModel"]["artifact"]["files"][0]["sha256"] = hashlib.sha256(b"different").hexdigest()
    files = config["baseModel"]["artifact"]["files"]
    config["baseModel"]["artifact"]["identity"] = hashlib.sha256(
        json.dumps({"files": files}, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()

    _assert_invalid(tmp_path, config, "ARTIFACT_CHECKSUM_MISMATCH")


def test_artifact_manifest_identity_mismatch_fails_closed(tmp_path: Path) -> None:
    config = _config()
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = _physical_artifact(tmp_path / "artifacts")
    config["baseModel"]["artifact"]["identity"] = hashlib.sha256(b"wrong manifest").hexdigest()

    _assert_invalid(tmp_path, config, "ARTIFACT_IDENTITY_MISMATCH")


def test_approved_artifact_with_missing_checksum_fails_closed(tmp_path: Path) -> None:
    config = _config()
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = _physical_artifact(tmp_path / "artifacts")
    config["baseModel"]["artifact"]["files"][0].pop("sha256")

    _assert_invalid(tmp_path, config, "ARTIFACT_SCHEMA_INVALID")


def test_placeholder_checksum_or_identity_cannot_be_approved(tmp_path: Path) -> None:
    config = _config()
    artifact_root = tmp_path / "artifacts"
    payload = _physical_artifact(artifact_root)
    payload["identity"] = "0" * 64
    payload["files"][0]["sha256"] = "1" * 64
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = payload

    _assert_invalid(tmp_path, config, "ARTIFACT_PLACEHOLDER_DIGEST")


def test_missing_referenced_artifact_fails_closed(tmp_path: Path) -> None:
    config = _config()
    artifact_root = tmp_path / "artifacts"
    payload = _physical_artifact(artifact_root)
    (artifact_root / payload["files"][0]["path"]).unlink()
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = payload

    _assert_invalid(tmp_path, config, "ARTIFACT_FILE_MISSING")


@pytest.mark.parametrize("unsafe_path", ["../outside.bin", "nested/../../outside.bin", "C:/private/model.bin"])
def test_unsafe_artifact_path_is_rejected_without_path_leakage(tmp_path: Path, unsafe_path: str) -> None:
    config = _config()
    content = b"outside"
    checksum = hashlib.sha256(content).hexdigest()
    files = [{"path": unsafe_path, "sha256": checksum}]
    identity = hashlib.sha256(
        json.dumps({"files": files}, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = {"identity": identity, "files": files}

    with pytest.raises(ArtifactConfigurationError) as error:
        _load_with_root(tmp_path, config)

    assert error.value.code == "ARTIFACT_PATH_INVALID"
    assert unsafe_path not in str(error.value)
    assert str(tmp_path) not in str(error.value)


def test_symlink_escape_is_rejected_when_symlinks_are_available(tmp_path: Path) -> None:
    artifact_root = tmp_path / "artifacts"
    artifact_root.mkdir()
    outside = tmp_path / "outside.bin"
    outside.write_bytes(b"outside")
    link = artifact_root / "escape.bin"
    try:
        os.symlink(outside, link)
    except OSError:
        pytest.skip("symlink creation is not available")
    checksum = hashlib.sha256(outside.read_bytes()).hexdigest()
    files = [{"path": "escape.bin", "sha256": checksum}]
    identity = hashlib.sha256(
        json.dumps({"files": files}, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    config = _config()
    config["baseModel"]["status"] = "APPROVED"
    config["baseModel"]["artifact"] = {"identity": identity, "files": files}

    _assert_invalid(tmp_path, config, "ARTIFACT_PATH_INVALID")


def test_adapter_map_key_and_assistant_key_must_match(tmp_path: Path) -> None:
    config = _config()
    config["assistantAdapters"]["ADMIN_ASSISTANT"]["assistantKey"] = "LAB_ASSISTANT"

    _assert_invalid(tmp_path, config, "ADAPTER_ASSISTANT_MISMATCH")


def test_adapter_base_model_identifier_must_match(tmp_path: Path) -> None:
    config = _config()
    config["assistantAdapters"]["LAB_ASSISTANT"]["baseModelIdentifier"] = "unapproved/model"

    _assert_invalid(tmp_path, config, "ADAPTER_MODEL_MISMATCH")


def test_adapter_base_model_revision_must_match(tmp_path: Path) -> None:
    config = _config()
    config["assistantAdapters"]["LAB_ASSISTANT"]["baseModelRevision"] = "2" * 40

    _assert_invalid(tmp_path, config, "ADAPTER_MODEL_REVISION_MISMATCH")


def test_duplicate_adapter_entry_fails_closed(tmp_path: Path) -> None:
    config = _config()
    adapters = list(config["assistantAdapters"].items())
    adapters.insert(1, adapters[0])
    adapters_json = ",".join(f"{json.dumps(key)}:{json.dumps(value)}" for key, value in adapters)
    raw = (
        f'{{"schemaVersion":{json.dumps(config["schemaVersion"])},'
        f'"artifactVersion":{json.dumps(config["artifactVersion"])},'
        f'"status":{json.dumps(config["status"])},'
        f'"baseModel":{json.dumps(config["baseModel"])},'
        f'"assistantAdapters":{{{adapters_json}}},'
        f'"sourceDecision":{json.dumps(config["sourceDecision"])},'
        f'"sourceRegistryReference":null}}'
    )
    path = tmp_path / "duplicate-adapters.json"
    path.write_text(raw, encoding="utf-8")

    with pytest.raises(ArtifactConfigurationError) as error:
        ArtifactLoader.from_file(path, tmp_path / "artifacts", _profiles())

    assert error.value.code == "ARTIFACT_CONFIG_DUPLICATE_KEY"
    assert str(path) not in str(error.value)


def test_candidate_decision_cannot_be_presented_as_approved_adapter(tmp_path: Path) -> None:
    config = _config()
    research = config["assistantAdapters"]["RESEARCH_ASSISTANT"]
    research["status"] = "APPROVED"
    research["identifier"] = "research-adapter"
    research["version"] = "1.0.0"
    research["artifact"] = _physical_artifact(tmp_path / "artifacts", "research/adapter.bin")

    _assert_invalid(tmp_path, config, "ADAPTER_APPROVAL_CONFLICT")


def test_future_approved_adapter_is_validated_only_for_its_matching_profile(tmp_path: Path) -> None:
    artifact_root = tmp_path / "artifacts"
    payload = _physical_artifact(artifact_root, "research/adapter.bin")
    config = _config()
    research = config["assistantAdapters"]["RESEARCH_ASSISTANT"]
    research.update(
        {
            "status": "APPROVED",
            "identifier": "research-adapter",
            "version": "1.0.0",
            "artifact": payload,
            "sourceDecision": {
                "reference": "registry/research-promotion.json",
                "identity": hashlib.sha256(b"promotion decision").hexdigest(),
                "outcome": "PROMOTED",
            },
        }
    )
    profiles = _profile_config()
    profiles["profiles"]["RESEARCH_ASSISTANT"]["adapter"] = {
        "identifier": "research-adapter",
        "version": "1.0.0",
        "artifactChecksum": payload["identity"],
    }
    profile_path = tmp_path / "assistant-profiles.json"
    profile_path.write_text(json.dumps(profiles), encoding="utf-8")
    descriptor_path = tmp_path / "model-artifacts.json"
    descriptor_path.write_text(json.dumps(config), encoding="utf-8")

    loader = ArtifactLoader.from_file(
        descriptor_path,
        artifact_root,
        ProfileLoader.from_file(profile_path),
    )

    research_state = loader.get_state(AssistantKey.RESEARCH_ASSISTANT)
    assert research_state.adapter_artifact_validated is True
    assert research_state.adapter_loaded is False
    assert research_state.ready is False
    assert loader.get_state(AssistantKey.ADMIN_ASSISTANT).adapter_status == "NOT_AVAILABLE"


def test_descriptor_identity_is_independent_of_absolute_paths_and_temporary_directory(tmp_path: Path) -> None:
    config = _config()
    first = tmp_path / "machine-a"
    second = tmp_path / "machine-b" / "different"
    first.mkdir(parents=True)
    second.mkdir(parents=True)

    first_loader = _load_from(first, deepcopy(config))
    second_loader = _load_from(second, deepcopy(config))

    assert first_loader.artifact_identity == second_loader.artifact_identity
    assert str(tmp_path) not in first_loader.artifact_identity


def test_malformed_descriptor_and_unsupported_schema_fail_with_stable_errors(tmp_path: Path) -> None:
    malformed = tmp_path / "malformed.json"
    malformed.write_text("{not-json", encoding="utf-8")
    with pytest.raises(ArtifactConfigurationError) as malformed_error:
        ArtifactLoader.from_file(malformed, tmp_path / "artifacts", _profiles())
    assert malformed_error.value.code == "ARTIFACT_CONFIG_INVALID_JSON"

    unsupported = _config()
    unsupported["schemaVersion"] = "2.0.0"
    _assert_invalid(tmp_path, unsupported, "ARTIFACT_SCHEMA_INVALID")


def test_source_decision_reference_must_be_safe_and_relative(tmp_path: Path) -> None:
    config = _config()
    config["sourceDecision"]["reference"] = "../../private/decision.json"

    _assert_invalid(tmp_path, config, "ARTIFACT_SOURCE_REFERENCE_INVALID")


def test_adapter_registry_reference_must_be_safe_and_relative(tmp_path: Path) -> None:
    config = _config()
    config["assistantAdapters"]["LAB_ASSISTANT"]["sourceRegistryReference"] = "C:/private/registry.json"

    _assert_invalid(tmp_path, config, "ARTIFACT_SOURCE_REFERENCE_INVALID")


def test_missing_profile_fails_closed(tmp_path: Path) -> None:
    profiles = _profiles()
    incomplete = ProfileLoader(
        {key: profile for key, profile in profiles.profiles.items() if key is not AssistantKey.RESEARCH_ASSISTANT}
    )
    descriptor = tmp_path / "model-artifacts.json"
    descriptor.write_text(json.dumps(_config()), encoding="utf-8")

    with pytest.raises(ArtifactConfigurationError) as error:
        ArtifactLoader.from_file(descriptor, tmp_path / "artifacts", incomplete)

    assert error.value.code == "ARTIFACT_PROFILE_MISSING"
    assert error.value.assistant_key is AssistantKey.RESEARCH_ASSISTANT


def test_current_application_readiness_remains_truthfully_unavailable() -> None:
    application = create_app()
    response = TestClient(
        application,
        headers={"X-Internal-Service-Token": "test-only-internal-service-token"},
    ).get("/ready")

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


def test_health_remains_up_independently_of_artifact_state() -> None:
    response = TestClient(
        create_app(),
        headers={"X-Internal-Service-Token": "test-only-internal-service-token"},
    ).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "ai-service"}


@pytest.mark.parametrize(
    "path",
    [
        "/v1/assistants/chat",
        "/v1/assistants/tool-request",
        "/v1/assistants/suggestions",
    ],
)
def test_assistant_request_resolves_artifact_state_then_returns_safe_not_ready(path: str) -> None:
    application = create_app()
    delegate = application.state.artifact_loader
    resolved: list[AssistantKey] = []

    class RecordingLoader:
        def get_state(self, assistant_key: AssistantKey):
            resolved.append(assistant_key)
            return delegate.get_state(assistant_key)

    application.state.artifact_loader = RecordingLoader()
    response = TestClient(
        application,
        headers={"X-Internal-Service-Token": "test-only-internal-service-token"},
    ).post(
        path,
        json={"assistantKey": "LAB_ASSISTANT", "input": "Use bounded context.", "authorizedContext": {}},
    )

    assert resolved == [AssistantKey.LAB_ASSISTANT]
    assert response.status_code == 503
    assert response.json()["errorCode"] in {"AI_MODEL_NOT_READY", "AI_SERVICE_NOT_READY"}


def test_model_info_exposes_no_local_artifact_path_or_environment_value(tmp_path: Path) -> None:
    secret_path_fragment = str(tmp_path)
    application = create_app()
    response = TestClient(
        application,
        headers={"X-Internal-Service-Token": "test-only-internal-service-token"},
    ).get("/model-info")

    assert response.status_code == 200
    serialized = response.text
    assert secret_path_fragment not in serialized
    assert "AI_MODEL_ARTIFACT_ROOT" not in serialized
    assert "model-artifacts.json" not in serialized
