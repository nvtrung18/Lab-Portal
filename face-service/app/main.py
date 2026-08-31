from __future__ import annotations

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings
from app.security import InternalSecurityMiddleware, error_response


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    application = FastAPI(title=resolved_settings.service_name, version="0.1.0")
    application.state.settings = resolved_settings

    @application.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP", "service": resolved_settings.service_name}

    async def validation_error_handler(request: Request, _exception: RequestValidationError) -> JSONResponse:
        return error_response(
            request.state.request_id,
            "FACE_INVALID_REQUEST",
            "Request validation failed.",
            status.HTTP_422_UNPROCESSABLE_CONTENT,
        )

    async def unexpected_error_handler(request: Request, _exception: Exception) -> JSONResponse:
        return error_response(
            request.state.request_id,
            "FACE_INTERNAL_ERROR",
            "An unexpected server error occurred.",
            status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    application.add_middleware(InternalSecurityMiddleware, settings=resolved_settings)
    application.add_exception_handler(RequestValidationError, validation_error_handler)
    application.add_exception_handler(Exception, unexpected_error_handler)
    return application


app = create_app()
