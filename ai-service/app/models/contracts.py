from __future__ import annotations

from enum import StrEnum
import json
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, JsonValue, StringConstraints, model_validator


def to_camel(value: str) -> str:
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class ContractModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, extra="forbid", populate_by_name=True)


class AssistantKey(StrEnum):
    ADMIN_ASSISTANT = "ADMIN_ASSISTANT"
    LAB_ASSISTANT = "LAB_ASSISTANT"
    RESEARCH_ASSISTANT = "RESEARCH_ASSISTANT"


RequestInput = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=32_768)]
RuntimeInput = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=262_144)]
ReadinessStatus = Literal["READY", "NOT_READY"]
ServiceStatus = Literal["READY", "NOT_READY"]
SupportedLocale = Literal["vi", "en"]
ModelStatus = Literal["NOT_LOADED", "LOADING", "READY", "ERROR"]
ArtifactStatus = Literal[
    "APPROVED",
    "CANDIDATE_ONLY",
    "BLOCKED",
    "PENDING",
    "METADATA_ONLY",
    "NOT_AVAILABLE",
]


class RuntimeRequest(ContractModel):
    input: RuntimeInput

    @model_validator(mode="after")
    def validate_message_budget(self):
        try:
            envelope = json.loads(self.input)
        except (ValueError, TypeError):
            envelope = None
        if isinstance(envelope, dict) and envelope.get("dialogueVersion") == 1:
            message = envelope.get("message")
            history = envelope.get("history", [])
            if not isinstance(message, str) or not message.strip() or len(message) > 32768:
                raise ValueError("invalid public message length")
            if not isinstance(history, list) or len(history) > 6:
                raise ValueError("invalid dialogue history bound")
            if any(not isinstance(item, dict) or not isinstance(item.get("content"), str)
                   or len(item["content"]) > 1500 for item in history):
                raise ValueError("invalid dialogue history message")
        elif len(self.input) > 32768:
            raise ValueError("plain input exceeds public message limit")
        return self


class AssistantRequest(RuntimeRequest):
    assistant_key: AssistantKey
    locale: SupportedLocale = "vi"
    authorized_context: dict[str, JsonValue] = Field(default_factory=dict)


class ToolResourceReference(ContractModel):
    resource_type: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=50)]
    resource_id: int | None = Field(gt=0)


class ToolCandidate(ContractModel):
    assistant_key: AssistantKey
    schema_version: Literal["v1"]
    tool_id: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=128)]
    description: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=512)]
    resource: ToolResourceReference
    parent_resource: ToolResourceReference | None


class ToolPlanningRequest(RuntimeRequest):
    locale: SupportedLocale = "vi"
    candidates: tuple[ToolCandidate, ...] = Field(min_length=1, max_length=200)


class PlannedToolRequest(ContractModel):
    assistant_key: AssistantKey
    schema_version: Literal["v1"]
    tool_id: str
    arguments: dict[str, JsonValue]


class ToolPlanningResponse(ContractModel):
    decision: Literal["TOOL_REQUEST", "CLARIFICATION", "REFUSAL", "CANCEL_PENDING", "ANSWER"]
    message: str | None
    tool_request: PlannedToolRequest | None
    prompt_tokens: int = Field(ge=0)
    completion_tokens: int = Field(ge=0)


class ChatResponse(ContractModel):
    assistant_key: AssistantKey
    answer: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=32_768)]
    prompt_tokens: int = Field(ge=0)
    completion_tokens: int = Field(ge=0)
    metadata: dict[str, JsonValue]


class HealthResponse(ContractModel):
    status: Literal["UP"] = "UP"
    service: str


class ReadinessResponse(ContractModel):
    status: ReadinessStatus = "NOT_READY"
    service: str
    service_status: ServiceStatus = "READY"
    model_status: ModelStatus = "NOT_LOADED"
    profile_loaded: bool = False
    artifact_validated: bool = False
    model_loaded: bool = False
    adapter_loaded: bool = False
    ready: bool = False


class AdapterArtifactInfoResponse(ContractModel):
    status: ArtifactStatus
    identifier: str | None
    version: str | None
    artifact_identity: str | None
    artifact_validated: bool
    adapter_loaded: bool


class ModelInfoResponse(ContractModel):
    status: ModelStatus = "NOT_LOADED"
    source: str = "SERVING_ARTIFACT_DESCRIPTOR"
    model_name: str
    model_version: str
    model_revision: str
    adapter_name: str | None = None
    adapter_version: str | None = None
    artifact_version: str
    artifact_state: ArtifactStatus
    artifact_identity: str | None
    descriptor_identity: str
    profile_versions: dict[AssistantKey, str]
    assistant_adapters: dict[AssistantKey, AdapterArtifactInfoResponse]
    profile_loaded: bool
    artifact_validated: bool
    model_loaded: bool
    adapter_loaded: bool
    ready: bool = False


class ErrorResponse(ContractModel):
    error_code: str
    message: str
    retryable: bool
    request_id: str
