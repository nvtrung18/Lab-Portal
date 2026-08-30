from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path, PurePosixPath, PureWindowsPath
from types import MappingProxyType
from typing import Annotated, Literal, Mapping, Protocol

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, ValidationError, field_validator

from app.models.contracts import AssistantKey, to_camel
from app.profiles import ProfileLoader, UnknownAssistantProfileError


SemanticVersion = Annotated[
    str,
    StringConstraints(pattern=r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$"),
]
Identifier = Annotated[
    str,
    StringConstraints(pattern=r"^[a-zA-Z0-9]+(?:[._/-][a-zA-Z0-9]+)*$", min_length=1, max_length=256),
]
LogicalReference = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=512)]
Sha256Digest = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{64}$")]
ModelRevision = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{40}$")]
ArtifactStatus = Literal[
    "APPROVED",
    "CANDIDATE_ONLY",
    "BLOCKED",
    "PENDING",
    "METADATA_ONLY",
    "NOT_AVAILABLE",
]


class ArtifactConfigurationError(RuntimeError):
    def __init__(self, code: str, assistant_key: AssistantKey | None = None) -> None:
        self.code = code
        self.assistant_key = assistant_key
        assistant = f": {assistant_key.value}" if assistant_key is not None else ""
        super().__init__(f"Serving artifact configuration is invalid ({code}{assistant}).")


class UnknownAssistantArtifactError(LookupError):
    code = "UNKNOWN_ASSISTANT_ARTIFACT"

    def __init__(self) -> None:
        super().__init__("Unknown assistant artifact state.")


class RuntimeArtifactBackend(Protocol):
    def load_base_model(self, artifact_path: Path, identifier: str, revision: str) -> None: ...

    def load_adapter(self, assistant_key: AssistantKey, artifact_path: Path, identifier: str) -> None: ...


class ArtifactModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, extra="forbid", frozen=True, populate_by_name=True)


class ArtifactFile(ArtifactModel):
    path: LogicalReference
    sha256: Sha256Digest


class PhysicalArtifact(ArtifactModel):
    identity: Sha256Digest
    files: tuple[ArtifactFile, ...] = Field(min_length=1)

    @field_validator("files")
    @classmethod
    def files_must_be_unique_and_ordered(cls, value: tuple[ArtifactFile, ...]) -> tuple[ArtifactFile, ...]:
        paths = [entry.path for entry in value]
        if len(paths) != len(set(paths)):
            raise ValueError("artifact paths must be unique")
        return tuple(sorted(value, key=lambda entry: entry.path))


class SourceDecision(ArtifactModel):
    reference: LogicalReference
    identity: Sha256Digest | None
    outcome: LogicalReference


class BaseModelDescriptor(ArtifactModel):
    identifier: Identifier
    revision: ModelRevision
    runtime_profile: Identifier
    status: ArtifactStatus
    artifact: PhysicalArtifact | None


class AdapterDescriptor(ArtifactModel):
    assistant_key: AssistantKey
    identifier: Identifier | None
    version: SemanticVersion | None
    base_model_identifier: Identifier
    base_model_revision: ModelRevision
    status: ArtifactStatus
    artifact: PhysicalArtifact | None
    source_decision: SourceDecision | None
    source_registry_reference: LogicalReference | None


class ArtifactBundle(ArtifactModel):
    schema_version: Literal["1.0.0"]
    artifact_version: SemanticVersion
    status: Literal["APPROVED"]
    base_model: BaseModelDescriptor
    assistant_adapters: dict[str, AdapterDescriptor]
    source_decision: SourceDecision | None
    source_registry_reference: LogicalReference | None


class AssistantArtifactState(ArtifactModel):
    assistant_key: AssistantKey
    profile_version: SemanticVersion
    adapter_status: ArtifactStatus
    adapter_identifier: Identifier | None
    adapter_version: SemanticVersion | None
    adapter_identity: Sha256Digest | None
    adapter_artifact_validated: bool
    adapter_loaded: bool = False
    ready: bool = False


class _DuplicateJsonKey(ValueError):
    pass


def _unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateJsonKey
        result[key] = value
    return result


def _canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def _sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _is_placeholder_digest(value: str) -> bool:
    return len(set(value)) == 1


def _validate_logical_reference(value: str) -> bool:
    normalized = value.replace("\\", "/")
    windows_path = PureWindowsPath(value)
    return (
        "\\" not in value
        and not PurePosixPath(value).is_absolute()
        and not windows_path.is_absolute()
        and not windows_path.drive
        and all(part not in {"", ".", ".."} for part in normalized.split("/"))
    )


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as artifact_file:
        for chunk in iter(lambda: artifact_file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _artifact_manifest_identity(artifact: PhysicalArtifact) -> str:
    files = [entry.model_dump(by_alias=True, mode="json") for entry in artifact.files]
    return _sha256_bytes(_canonical_bytes({"files": files}))


def _descriptor_identity(bundle: ArtifactBundle) -> str:
    return _sha256_bytes(_canonical_bytes(bundle.model_dump(by_alias=True, mode="json")))


def _validate_physical_artifact(artifact: PhysicalArtifact, artifact_root: Path) -> None:
    if _is_placeholder_digest(artifact.identity) or any(
        _is_placeholder_digest(entry.sha256) for entry in artifact.files
    ):
        raise ArtifactConfigurationError("ARTIFACT_PLACEHOLDER_DIGEST")
    if artifact.identity != _artifact_manifest_identity(artifact):
        raise ArtifactConfigurationError("ARTIFACT_IDENTITY_MISMATCH")

    for entry in artifact.files:
        if not _validate_logical_reference(entry.path):
            raise ArtifactConfigurationError("ARTIFACT_PATH_INVALID")

    try:
        resolved_root = artifact_root.resolve(strict=True)
    except OSError:
        raise ArtifactConfigurationError("ARTIFACT_FILE_MISSING") from None
    if not resolved_root.is_dir():
        raise ArtifactConfigurationError("ARTIFACT_ROOT_INVALID")

    for entry in artifact.files:
        try:
            candidate = (resolved_root / entry.path).resolve(strict=True)
        except OSError:
            raise ArtifactConfigurationError("ARTIFACT_FILE_MISSING") from None
        if not candidate.is_relative_to(resolved_root) or not candidate.is_file():
            raise ArtifactConfigurationError("ARTIFACT_PATH_INVALID")
        try:
            actual_digest = _file_sha256(candidate)
        except OSError:
            raise ArtifactConfigurationError("ARTIFACT_FILE_UNREADABLE") from None
        if actual_digest != entry.sha256:
            raise ArtifactConfigurationError("ARTIFACT_CHECKSUM_MISMATCH")


def _physical_artifact_directory(artifact: PhysicalArtifact, artifact_root: Path) -> Path:
    parents = [(artifact_root / entry.path).resolve(strict=True).parent for entry in artifact.files]
    return Path(os.path.commonpath([str(parent) for parent in parents]))


class ArtifactLoader:
    def __init__(
        self,
        bundle: ArtifactBundle,
        artifact_identity: str,
        artifact_root: Path,
        profile_loader: ProfileLoader,
    ) -> None:
        self._bundle = bundle
        self._artifact_identity = artifact_identity
        self._artifact_root = artifact_root.resolve()
        self._profile_loader = profile_loader
        self._base_artifact_validated = False
        self._model_loaded = False
        self._runtime_activation_failed = False

        if bundle.base_model.status == "APPROVED" and bundle.base_model.artifact is None:
            raise ArtifactConfigurationError("ARTIFACT_APPROVAL_INVALID")
        if bundle.base_model.artifact is not None:
            _validate_physical_artifact(bundle.base_model.artifact, artifact_root)
            self._base_artifact_validated = bundle.base_model.status == "APPROVED"

        states: dict[AssistantKey, AssistantArtifactState] = {}
        for assistant_key in AssistantKey:
            try:
                profile = profile_loader.get_profile(assistant_key)
            except UnknownAssistantProfileError:
                raise ArtifactConfigurationError("ARTIFACT_PROFILE_MISSING", assistant_key) from None
            if profile.model_profile.base_model_identifier != bundle.base_model.identifier:
                raise ArtifactConfigurationError("ARTIFACT_MODEL_MISMATCH", assistant_key)
            if profile.model_profile.base_model_revision != bundle.base_model.revision:
                raise ArtifactConfigurationError("ARTIFACT_MODEL_REVISION_MISMATCH", assistant_key)
            if profile.model_profile.runtime_profile != bundle.base_model.runtime_profile:
                raise ArtifactConfigurationError("ARTIFACT_RUNTIME_PROFILE_MISMATCH", assistant_key)

            adapter = bundle.assistant_adapters.get(assistant_key.value)
            if adapter is None:
                states[assistant_key] = AssistantArtifactState(
                    assistant_key=assistant_key,
                    profile_version=profile.profile_version,
                    adapter_status="NOT_AVAILABLE",
                    adapter_identifier=None,
                    adapter_version=None,
                    adapter_identity=None,
                    adapter_artifact_validated=False,
                )
                continue
            states[assistant_key] = self._validate_adapter(adapter, assistant_key, profile, bundle, artifact_root)
        self._states: Mapping[AssistantKey, AssistantArtifactState] = MappingProxyType(states)

    @staticmethod
    def _validate_adapter(adapter, assistant_key, profile, bundle, artifact_root) -> AssistantArtifactState:
        if adapter.assistant_key is not assistant_key:
            raise ArtifactConfigurationError("ADAPTER_ASSISTANT_MISMATCH", assistant_key)
        if adapter.base_model_identifier != bundle.base_model.identifier:
            raise ArtifactConfigurationError("ADAPTER_MODEL_MISMATCH", assistant_key)
        if adapter.base_model_revision != bundle.base_model.revision:
            raise ArtifactConfigurationError("ADAPTER_MODEL_REVISION_MISMATCH", assistant_key)

        physical_validated = False
        if adapter.artifact is not None:
            _validate_physical_artifact(adapter.artifact, artifact_root)
            physical_validated = adapter.status == "APPROVED"
        if adapter.status == "APPROVED":
            if adapter.artifact is None or adapter.identifier is None or adapter.version is None:
                raise ArtifactConfigurationError("ADAPTER_APPROVAL_INVALID", assistant_key)
            if adapter.source_decision is None and adapter.source_registry_reference is None:
                raise ArtifactConfigurationError("ADAPTER_APPROVAL_EVIDENCE_MISSING", assistant_key)
            if adapter.source_decision is not None and any(
                marker in adapter.source_decision.outcome for marker in ("CANDIDATE", "BLOCKED", "PENDING", "BASE_ONLY")
            ):
                raise ArtifactConfigurationError("ADAPTER_APPROVAL_CONFLICT", assistant_key)
            if profile.adapter is None:
                raise ArtifactConfigurationError("ADAPTER_PROFILE_MISMATCH", assistant_key)
            if (
                profile.adapter.identifier != adapter.identifier
                or profile.adapter.version != adapter.version
                or profile.adapter.artifact_checksum != adapter.artifact.identity
            ):
                raise ArtifactConfigurationError("ADAPTER_PROFILE_MISMATCH", assistant_key)

        return AssistantArtifactState(
            assistant_key=assistant_key,
            profile_version=profile.profile_version,
            adapter_status=adapter.status,
            adapter_identifier=adapter.identifier,
            adapter_version=adapter.version,
            adapter_identity=adapter.artifact.identity if adapter.artifact is not None else None,
            adapter_artifact_validated=physical_validated,
        )

    @classmethod
    def from_file(
        cls,
        descriptor_path: str | Path,
        artifact_root: str | Path,
        profile_loader: ProfileLoader,
    ) -> "ArtifactLoader":
        try:
            raw = Path(descriptor_path).read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            raise ArtifactConfigurationError("ARTIFACT_CONFIG_UNREADABLE") from None
        try:
            data = json.loads(raw, object_pairs_hook=_unique_object)
        except _DuplicateJsonKey:
            raise ArtifactConfigurationError("ARTIFACT_CONFIG_DUPLICATE_KEY") from None
        except (json.JSONDecodeError, TypeError):
            raise ArtifactConfigurationError("ARTIFACT_CONFIG_INVALID_JSON") from None
        try:
            bundle = ArtifactBundle.model_validate(data)
        except ValidationError:
            raise ArtifactConfigurationError("ARTIFACT_SCHEMA_INVALID") from None

        expected_keys = {key.value for key in AssistantKey}
        if not set(bundle.assistant_adapters).issubset(expected_keys):
            raise ArtifactConfigurationError("ADAPTER_SET_INVALID")
        for map_key, adapter in bundle.assistant_adapters.items():
            if map_key != adapter.assistant_key.value:
                raise ArtifactConfigurationError("ADAPTER_ASSISTANT_MISMATCH", adapter.assistant_key)
        for source in [bundle.source_decision, *(entry.source_decision for entry in bundle.assistant_adapters.values())]:
            if source is not None and not _validate_logical_reference(source.reference):
                raise ArtifactConfigurationError("ARTIFACT_SOURCE_REFERENCE_INVALID")
        registry_references = [
            bundle.source_registry_reference,
            *(entry.source_registry_reference for entry in bundle.assistant_adapters.values()),
        ]
        if any(reference is not None and not _validate_logical_reference(reference) for reference in registry_references):
            raise ArtifactConfigurationError("ARTIFACT_SOURCE_REFERENCE_INVALID")
        return cls(bundle, _descriptor_identity(bundle), Path(artifact_root), profile_loader)

    @property
    def artifact_identity(self) -> str:
        return self._artifact_identity

    @property
    def descriptor_status(self) -> str:
        return self._bundle.status

    @property
    def artifact_version(self) -> str:
        return self._bundle.artifact_version

    @property
    def base_model_identifier(self) -> str:
        return self._bundle.base_model.identifier

    @property
    def base_model_revision(self) -> str:
        return self._bundle.base_model.revision

    @property
    def base_artifact_status(self) -> str:
        return self._bundle.base_model.status

    @property
    def base_artifact_identity(self) -> str | None:
        artifact = self._bundle.base_model.artifact
        return artifact.identity if artifact is not None else None

    @property
    def profile_loaded(self) -> bool:
        return len(self._states) == len(AssistantKey)

    @property
    def artifact_validated(self) -> bool:
        return self._base_artifact_validated

    @property
    def model_loaded(self) -> bool:
        return self._model_loaded

    @property
    def model_status(self) -> Literal["NOT_LOADED", "READY", "ERROR"]:
        if self._model_loaded:
            return "READY"
        if self._runtime_activation_failed:
            return "ERROR"
        return "NOT_LOADED"

    @property
    def adapter_loaded(self) -> bool:
        return any(state.adapter_loaded for state in self._states.values())

    @property
    def ready(self) -> bool:
        return self.model_loaded and all(state.ready for state in self._states.values())

    def activate(self, backend: RuntimeArtifactBackend) -> bool:
        if self.ready:
            return True
        base_artifact = self._bundle.base_model.artifact
        if not self._base_artifact_validated or base_artifact is None:
            self._runtime_activation_failed = True
            return False
        if any(
            state.adapter_status not in {"APPROVED", "NOT_AVAILABLE"}
            or (state.adapter_status == "APPROVED" and not state.adapter_artifact_validated)
            for state in self._states.values()
        ):
            self._runtime_activation_failed = True
            return False

        try:
            backend.load_base_model(
                _physical_artifact_directory(base_artifact, self._artifact_root),
                self.base_model_identifier,
                self.base_model_revision,
            )
            for assistant_key, state in self._states.items():
                if state.adapter_status != "APPROVED":
                    continue
                adapter = self._bundle.assistant_adapters[assistant_key.value]
                if adapter.artifact is None or adapter.identifier is None:
                    raise ArtifactConfigurationError("ADAPTER_APPROVAL_INVALID", assistant_key)
                backend.load_adapter(
                    assistant_key,
                    _physical_artifact_directory(adapter.artifact, self._artifact_root),
                    adapter.identifier,
                )
        except Exception:
            self._runtime_activation_failed = True
            return False

        self._model_loaded = True
        self._runtime_activation_failed = False
        self._states = MappingProxyType(
            {
                assistant_key: state.model_copy(
                    update={
                        "adapter_loaded": state.adapter_status == "APPROVED",
                        "ready": state.adapter_status in {"APPROVED", "NOT_AVAILABLE"},
                    }
                )
                for assistant_key, state in self._states.items()
            }
        )
        return self.ready

    @property
    def states(self) -> Mapping[AssistantKey, AssistantArtifactState]:
        return self._states

    def get_state(self, assistant_key: AssistantKey | str) -> AssistantArtifactState:
        try:
            return self._states[AssistantKey(assistant_key)]
        except (ValueError, TypeError, KeyError):
            raise UnknownAssistantArtifactError from None
