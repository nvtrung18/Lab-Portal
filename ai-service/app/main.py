from __future__ import annotations

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.artifacts import ArtifactLoader, RuntimeArtifactBackend
from app.config import Settings
from app.output_validation import OutputSchemaRegistry, StructuredOutputValidator
from app.profiles import ProfileLoader
from app.routes.foundation import router
from app.security import InternalSecurityMiddleware, safe_error_response


async def validation_error_handler(request: Request, _exception: RequestValidationError) -> JSONResponse:
    return safe_error_response(
        request,
        error_code="AI_INVALID_REQUEST",
        message="Request validation failed.",
        retryable=False,
        status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
    )


async def unexpected_error_handler(request: Request, _exception: Exception) -> JSONResponse:
    return safe_error_response(
        request,
        error_code="AI_INTERNAL_ERROR",
        message="An unexpected server error occurred.",
        retryable=False,
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
    )


def create_app(
    settings: Settings | None = None,
    runtime_backend: RuntimeArtifactBackend | None = None,
) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    profile_loader = ProfileLoader.from_file(resolved_settings.profile_config_path)
    artifact_loader = ArtifactLoader.from_file(
        resolved_settings.artifact_config_path,
        resolved_settings.artifact_root,
        profile_loader,
    )
    if resolved_settings.runtime_load_enabled:
        if runtime_backend is None:
            from app.runtime import TransformersRuntimeBackend

            runtime_backend = TransformersRuntimeBackend(device=resolved_settings.runtime_device)
        artifact_loader.activate(runtime_backend)
    output_schema_registry = OutputSchemaRegistry.from_file(
        resolved_settings.output_schema_config_path,
        profile_loader,
    )
    output_validator = StructuredOutputValidator(output_schema_registry)
    application = FastAPI(title=resolved_settings.service_name, version="0.1.0")
    application.state.settings = resolved_settings
    application.state.profile_loader = profile_loader
    application.state.artifact_loader = artifact_loader
    application.state.output_schema_registry = output_schema_registry
    application.state.output_validator = output_validator
    application.include_router(router)
    application.add_middleware(InternalSecurityMiddleware, settings=resolved_settings)
    application.add_exception_handler(RequestValidationError, validation_error_handler)
    application.add_exception_handler(Exception, unexpected_error_handler)
    return application


app = create_app()
