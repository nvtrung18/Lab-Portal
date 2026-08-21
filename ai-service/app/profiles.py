from __future__ import annotations

import hashlib
import json
from pathlib import Path
from types import MappingProxyType
from typing import Annotated, Literal, Mapping

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, ValidationError, field_validator

from app.models.contracts import AssistantKey, to_camel


SemanticVersion = Annotated[
    str,
    StringConstraints(pattern=r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$"),
]
ProfileIdentifier = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z0-9]+(?:[._-][a-z0-9]+)*$", min_length=1, max_length=128),
]
RetrievalNamespace = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$", min_length=1, max_length=64),
]
NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=4096)]
Sha256Digest = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{64}$")]
ModelRevision = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{40}$")]


TOOL_SCHEMAS_BY_ASSISTANT: Mapping[AssistantKey, frozenset[str]] = MappingProxyType(
    {
        AssistantKey.ADMIN_ASSISTANT: frozenset(
            {
                "admin.system.summary",
                "admin.audit.summary",
                "admin.user.status.lookup",
                "admin.config.draft",
                "admin.account.action.draft",
            }
        ),
        AssistantKey.LAB_ASSISTANT: frozenset(
            {
                "lab.policy.read",
                "lab.slot.read",
                "lab.own.booking.read",
                "lab.managed.summary",
                "lab.booking.draft",
                "lab.checkin.guidance",
            }
        ),
        AssistantKey.RESEARCH_ASSISTANT: frozenset(
            {
                "research.project.summary",
                "research.group.summary",
                "research.assigned.task.read",
                "research.task.proposal.draft",
                "research.task.suggestion.draft",
                "research.report.review.draft",
            }
        ),
    }
)


class ProfileConfigurationError(RuntimeError):
    def __init__(self, code: str, assistant_key: AssistantKey | None = None) -> None:
        self.code = code
        self.assistant_key = assistant_key
        profile = f": {assistant_key.value}" if assistant_key is not None else ""
        super().__init__(f"Assistant profile configuration is invalid ({code}{profile}).")


class UnknownAssistantProfileError(LookupError):
    code = "UNKNOWN_ASSISTANT_PROFILE"

    def __init__(self) -> None:
        super().__init__("Unknown assistant profile.")


class ProfileModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, extra="forbid", frozen=True, populate_by_name=True)


class PromptProfile(ProfileModel):
    version: ProfileIdentifier
    system_prompt: NonBlankText


class ModelProfile(ProfileModel):
    base_model_identifier: NonBlankText
    base_model_revision: ModelRevision
    profile_version: SemanticVersion
    runtime_profile: ProfileIdentifier
    serving_mode: Literal["METADATA_ONLY"]


class AdapterReference(ProfileModel):
    identifier: ProfileIdentifier
    version: SemanticVersion
    artifact_checksum: Sha256Digest


class SafetySettings(ProfileModel):
    model_output_trusted: Literal[False]
    tool_execution_enabled: Literal[False]
    retrieval_enabled: Literal[False]


class ProfileDefinition(ProfileModel):
    assistant_key: AssistantKey
    profile_version: SemanticVersion
    prompt: PromptProfile
    model_profile: ModelProfile
    adapter: AdapterReference | None = None
    retrieval_namespace: RetrievalNamespace
    allowed_tool_schemas: tuple[ProfileIdentifier, ...] = Field(min_length=1)
    schema_bundle: ProfileIdentifier
    safety: SafetySettings

    @field_validator("allowed_tool_schemas")
    @classmethod
    def tool_schemas_must_be_unique(cls, value: tuple[str, ...]) -> tuple[str, ...]:
        if len(value) != len(set(value)):
            raise ValueError("tool schema names must be unique")
        return value


class AssistantProfile(ProfileDefinition):
    config_version: SemanticVersion
    profile_identity: Sha256Digest


class ProfileBundle(ProfileModel):
    schema_version: Literal["1.0.0"]
    status: Literal["APPROVED"]
    profiles: dict[str, ProfileDefinition]


class _DuplicateJsonKey(ValueError):
    pass


def _unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateJsonKey
        result[key] = value
    return result


def _profile_identity(bundle: ProfileBundle, profile: ProfileDefinition) -> str:
    canonical = {
        "schemaVersion": bundle.schema_version,
        "status": bundle.status,
        "profile": profile.model_dump(by_alias=True, mode="json"),
    }
    rendered = json.dumps(
        canonical,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")
    return hashlib.sha256(rendered).hexdigest()


class ProfileLoader:
    def __init__(self, profiles: Mapping[AssistantKey, AssistantProfile]) -> None:
        self._profiles = MappingProxyType(dict(profiles))

    @property
    def profiles(self) -> Mapping[AssistantKey, AssistantProfile]:
        return self._profiles

    @classmethod
    def from_file(cls, path: str | Path) -> "ProfileLoader":
        try:
            raw = Path(path).read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            raise ProfileConfigurationError("PROFILE_CONFIG_UNREADABLE") from None

        try:
            data = json.loads(raw, object_pairs_hook=_unique_object)
        except _DuplicateJsonKey:
            raise ProfileConfigurationError("PROFILE_CONFIG_DUPLICATE_KEY") from None
        except (json.JSONDecodeError, TypeError):
            raise ProfileConfigurationError("PROFILE_CONFIG_INVALID_JSON") from None

        try:
            bundle = ProfileBundle.model_validate(data)
        except ValidationError:
            raise ProfileConfigurationError("PROFILE_SCHEMA_INVALID") from None

        expected_keys = {assistant_key.value for assistant_key in AssistantKey}
        if set(bundle.profiles) != expected_keys:
            raise ProfileConfigurationError("PROFILE_SET_INVALID")

        loaded: dict[AssistantKey, AssistantProfile] = {}
        for map_key, definition in bundle.profiles.items():
            if map_key != definition.assistant_key.value:
                raise ProfileConfigurationError("PROFILE_KEY_MISMATCH", definition.assistant_key)
            allowed_schemas = TOOL_SCHEMAS_BY_ASSISTANT[definition.assistant_key]
            if not set(definition.allowed_tool_schemas).issubset(allowed_schemas):
                raise ProfileConfigurationError("PROFILE_TOOL_SCHEMA_INVALID", definition.assistant_key)
            profile = AssistantProfile.model_validate(
                {
                    **definition.model_dump(by_alias=True, mode="json"),
                    "configVersion": bundle.schema_version,
                    "profileIdentity": _profile_identity(bundle, definition),
                }
            )
            loaded[definition.assistant_key] = profile

        if len({profile.prompt.version for profile in loaded.values()}) != len(loaded):
            raise ProfileConfigurationError("PROFILE_PROMPT_IDENTITY_DUPLICATE")
        if len({profile.prompt.system_prompt for profile in loaded.values()}) != len(loaded):
            raise ProfileConfigurationError("PROFILE_PROMPT_CONTENT_DUPLICATE")
        if len({profile.retrieval_namespace for profile in loaded.values()}) != len(loaded):
            raise ProfileConfigurationError("PROFILE_RETRIEVAL_NAMESPACE_DUPLICATE")
        return cls(loaded)

    def get_profile(self, assistant_key: AssistantKey | str) -> AssistantProfile:
        try:
            normalized_key = AssistantKey(assistant_key)
            return self._profiles[normalized_key]
        except (ValueError, TypeError, KeyError):
            raise UnknownAssistantProfileError from None
