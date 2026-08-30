from __future__ import annotations

from fastapi import APIRouter, Request, status
from fastapi.responses import JSONResponse

from app.config import Settings
from app.models import (
    AdapterArtifactInfoResponse,
    AssistantKey,
    AssistantRequest,
    ChatResponse,
    ErrorResponse,
    HealthResponse,
    ModelInfoResponse,
    ReadinessResponse,
)
from app.security import safe_error_response


router = APIRouter()


def _settings(request: Request) -> Settings:
    return request.app.state.settings


def _error_response(
    request: Request,
    *,
    error_code: str,
    message: str,
    retryable: bool,
    status_code: int,
) -> JSONResponse:
    return safe_error_response(
        request,
        error_code=error_code,
        message=message,
        retryable=retryable,
        status_code=status_code,
    )


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    return HealthResponse(service=_settings(request).service_name)


@router.get(
    "/ready",
    response_model=ReadinessResponse,
    responses={status.HTTP_503_SERVICE_UNAVAILABLE: {"model": ReadinessResponse}},
)
def ready(request: Request) -> ReadinessResponse | JSONResponse:
    artifacts = request.app.state.artifact_loader
    response = ReadinessResponse(
        status="READY" if artifacts.ready else "NOT_READY",
        service=_settings(request).service_name,
        model_status=artifacts.model_status,
        profile_loaded=artifacts.profile_loaded,
        artifact_validated=artifacts.artifact_validated,
        model_loaded=artifacts.model_loaded,
        adapter_loaded=artifacts.adapter_loaded,
        ready=artifacts.ready,
    )
    if artifacts.ready:
        return response
    return JSONResponse(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        content=response.model_dump(by_alias=True, mode="json"),
    )


@router.get("/model-info", response_model=ModelInfoResponse)
def model_info(request: Request) -> ModelInfoResponse:
    artifacts = request.app.state.artifact_loader
    return ModelInfoResponse(
        status=artifacts.model_status,
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
    response_model=ChatResponse,
    responses={status.HTTP_503_SERVICE_UNAVAILABLE: {"model": ErrorResponse}},
)
def chat(request: Request, payload: AssistantRequest) -> ChatResponse | JSONResponse:
    request.app.state.profile_loader.get_profile(payload.assistant_key)
    artifact_state = request.app.state.artifact_loader.get_state(payload.assistant_key)
    admin_mvp = request.app.state.admin_mvp
    lab_mvp = request.app.state.lab_mvp
    research_mvp = request.app.state.research_mvp
    if artifact_state.ready and payload.assistant_key is AssistantKey.ADMIN_ASSISTANT and admin_mvp is not None:
        return admin_mvp.respond(payload)
    if artifact_state.ready and payload.assistant_key is AssistantKey.LAB_ASSISTANT and lab_mvp is not None:
        return lab_mvp.respond(payload)
    if artifact_state.ready and payload.assistant_key is AssistantKey.RESEARCH_ASSISTANT and research_mvp is not None:
        return research_mvp.respond(payload)
    if artifact_state.ready:
        return _error_response(
            request,
            error_code="AI_SERVICE_NOT_READY",
            message="AI chat generation is not available.",
            retryable=False,
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        )
    return _error_response(
        request,
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
        request,
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
        request,
        error_code="AI_SERVICE_NOT_READY",
        message="AI suggestions are not available.",
        retryable=False,
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
    )
