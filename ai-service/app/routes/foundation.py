from __future__ import annotations

from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse

from app.config import Settings
from app.models import (
    AdapterArtifactInfoResponse,
    AssistantRequest,
    ErrorResponse,
    HealthResponse,
    ModelInfoResponse,
    ReadinessResponse,
)


router = APIRouter()


def _settings(request: Request) -> Settings:
    return request.app.state.settings


def _error_response(
    *, error_code: str, message: str, retryable: bool, status_code: int
) -> JSONResponse:
    error = ErrorResponse(error_code=error_code, message=message, retryable=retryable)
    return JSONResponse(status_code=status_code, content=error.model_dump(by_alias=True, mode="json"))


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    return HealthResponse(service=_settings(request).service_name)


@router.get(
    "/ready",
    response_model=ReadinessResponse,
    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
)
def ready(request: Request) -> ReadinessResponse:
    artifacts = request.app.state.artifact_loader
    return ReadinessResponse(
        service=_settings(request).service_name,
        profile_loaded=artifacts.profile_loaded,
        artifact_validated=artifacts.artifact_validated,
        model_loaded=artifacts.model_loaded,
        adapter_loaded=artifacts.adapter_loaded,
        ready=artifacts.ready,
    )


@router.get("/model-info", response_model=ModelInfoResponse)
def model_info(request: Request) -> ModelInfoResponse:
    artifacts = request.app.state.artifact_loader
    return ModelInfoResponse(
        model_name=artifacts.base_model_identifier,
        model_version=artifacts.base_model_revision,
        model_revision=artifacts.base_model_revision,
        artifact_version=artifacts.artifact_version,
        artifact_state=artifacts.base_artifact_status,
        artifact_identity=artifacts.base_artifact_identity,
        descriptor_identity=artifacts.artifact_identity,
        profile_versions={key: state.profile_version for key, state in artifacts.states.items()},
        assistant_adapters={
            key: AdapterArtifactInfoResponse(
                status=state.adapter_status,
                identifier=state.adapter_identifier,
                version=state.adapter_version,
                artifact_identity=state.adapter_identity,
                artifact_validated=state.adapter_artifact_validated,
                adapter_loaded=state.adapter_loaded,
            )
            for key, state in artifacts.states.items()
        },
        profile_loaded=artifacts.profile_loaded,
        artifact_validated=artifacts.artifact_validated,
        model_loaded=artifacts.model_loaded,
        adapter_loaded=artifacts.adapter_loaded,
        ready=artifacts.ready,
    )


@router.post(
    "/v1/assistants/chat",
    response_model=ErrorResponse,
    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
)
def chat(request: Request, payload: AssistantRequest) -> JSONResponse:
    request.app.state.profile_loader.get_profile(payload.assistant_key)
    request.app.state.artifact_loader.get_state(payload.assistant_key)
    return _error_response(
        error_code="AI_MODEL_NOT_READY",
        message="AI model is not loaded.",
        retryable=True,
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
    )


@router.post(
    "/v1/assistants/tool-request",
    response_model=ErrorResponse,
    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
)
def tool_request(request: Request, payload: AssistantRequest) -> JSONResponse:
    request.app.state.profile_loader.get_profile(payload.assistant_key)
    request.app.state.artifact_loader.get_state(payload.assistant_key)
    return _error_response(
        error_code="AI_SERVICE_NOT_READY",
        message="AI tool requests are not available.",
        retryable=False,
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
    )


@router.post(
    "/v1/assistants/suggestions",
    response_model=ErrorResponse,
    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
)
@router.post(
    "/v1/research/suggestions",
    response_model=ErrorResponse,
    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
    include_in_schema=False,
)
def suggestions(request: Request, payload: AssistantRequest) -> JSONResponse:
    request.app.state.profile_loader.get_profile(payload.assistant_key)
    request.app.state.artifact_loader.get_state(payload.assistant_key)
    return _error_response(
        error_code="AI_SERVICE_NOT_READY",
        message="AI suggestions are not available.",
        retryable=False,
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
    )
