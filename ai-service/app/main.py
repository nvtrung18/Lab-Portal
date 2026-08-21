from __future__ import annotations

import logging

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.artifacts import ArtifactLoader
from app.config import Settings
from app.models import ErrorResponse
from app.profiles import ProfileLoader
from app.routes.foundation import router


logger = logging.getLogger(__name__)


def _safe_error(*, error_code: str, message: str, retryable: bool, status_code: int) -> JSONResponse:
    error = ErrorResponse(error_code=error_code, message=message, retryable=retryable)
    return JSONResponse(status_code=status_code, content=error.model_dump(by_alias=True, mode="json"))


async def validation_error_handler(_request: Request, _exception: RequestValidationError) -> JSONResponse:
    return _safe_error(
        error_code="AI_INVALID_REQUEST",
        message="Request validation failed.",
        retryable=False,
        status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
    )


async def unexpected_error_handler(request: Request, exception: Exception) -> JSONResponse:
    sanitized_exception = RuntimeError(type(exception).__name__)
    logger.error(
        "Unhandled exception for %s %s",
        request.method,
        request.url.path,
        exc_info=(RuntimeError, sanitized_exception, exception.__traceback__),
    )
    return _safe_error(
        error_code="AI_INTERNAL_ERROR",
        message="An unexpected server error occurred.",
        retryable=False,
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
    )


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    profile_loader = ProfileLoader.from_file(resolved_settings.profile_config_path)
    artifact_loader = ArtifactLoader.from_file(
        resolved_settings.artifact_config_path,
        resolved_settings.artifact_root,
        profile_loader,
    )
    application = FastAPI(title=resolved_settings.service_name, version="0.1.0")
    application.state.settings = resolved_settings
    application.state.profile_loader = profile_loader
    application.state.artifact_loader = artifact_loader
    application.include_router(router)
    application.add_exception_handler(RequestValidationError, validation_error_handler)
    application.add_exception_handler(Exception, unexpected_error_handler)
    return application


app = create_app()
