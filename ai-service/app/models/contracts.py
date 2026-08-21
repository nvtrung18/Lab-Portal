from __future__ import annotations

from enum import StrEnum
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, JsonValue, StringConstraints


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
ReadinessStatus = Literal["READY", "NOT_READY"]
ServiceStatus = Literal["READY", "NOT_READY"]
ModelStatus = Literal["NOT_LOADED", "LOADING", "READY", "ERROR"]


class AssistantRequest(ContractModel):
    assistant_key: AssistantKey
    input: RequestInput
    authorized_context: dict[str, JsonValue] = Field(default_factory=dict)


class HealthResponse(ContractModel):
    status: Literal["UP"] = "UP"
    service: str


class ReadinessResponse(ContractModel):
    status: ReadinessStatus = "NOT_READY"
    service: str
    service_status: ServiceStatus = "READY"
    model_status: ModelStatus = "NOT_LOADED"
    ready: bool = False


class ModelInfoResponse(ContractModel):
    status: ModelStatus = "NOT_LOADED"
    source: str = "FOUNDATION_STUB"
    model_name: str | None = None
    model_version: str | None = None
    adapter_name: str | None = None
    adapter_version: str | None = None
    ready: bool = False


class ErrorResponse(ContractModel):
    error_code: str
    message: str
    retryable: bool
