from __future__ import annotations

import asyncio
import logging
import re
import secrets
from time import perf_counter
from uuid import uuid4

from fastapi import Request, status
from fastapi.responses import JSONResponse
from starlette.datastructures import MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.config import Settings
from app.models import ErrorResponse


logger = logging.getLogger(__name__)

INTERNAL_TOKEN_HEADER = b"x-internal-service-token"
REQUEST_ID_HEADER = b"x-request-id"
AUTHORIZATION_HEADER = b"authorization"
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
_SAFE_LOG_PATH_PATTERN = re.compile(r"[^A-Za-z0-9/_.{}:-]")
_SAFE_LOG_METHOD_PATTERN = re.compile(r"[^A-Z]")


def request_id_for(request: Request) -> str:
    return request.state.request_id


def safe_error_response(
    request: Request,
    *,
    error_code: str,
    message: str,
    retryable: bool,
    status_code: int,
) -> JSONResponse:
    request.state.error_code = error_code
    request_id = request_id_for(request)
    error = ErrorResponse(
        error_code=error_code,
        message=message,
        retryable=retryable,
        request_id=request_id,
    )
    return JSONResponse(
        status_code=status_code,
        content=error.model_dump(by_alias=True, mode="json"),
        headers={"X-Request-Id": request_id},
    )


def _header_values(scope: Scope, name: bytes) -> list[str]:
    return [
        value.decode("latin-1")
        for key, value in scope.get("headers", ())
        if key.lower() == name
    ]


def _resolve_request_id(scope: Scope) -> tuple[str, bool]:
    generated = uuid4().hex
    values = _header_values(scope, REQUEST_ID_HEADER)
    if not values:
        return generated, True
    if len(values) != 1 or REQUEST_ID_PATTERN.fullmatch(values[0]) is None:
        return generated, False
    return values[0], True


def _safe_log_path(scope: Scope) -> str:
    path = str(scope.get("path") or "/")[:256]
    return _SAFE_LOG_PATH_PATTERN.sub("?", path)


def _safe_log_method(scope: Scope) -> str:
    method = str(scope.get("method") or "UNKNOWN").upper()[:16]
    return _SAFE_LOG_METHOD_PATTERN.sub("?", method)


class InternalSecurityMiddleware:
    """Authenticates the Spring caller without granting business authority."""

    def __init__(self, app: ASGIApp, settings: Settings) -> None:
        self._app = app
        self._expected_token = settings.internal_service_token.get_secret_value()
        self._timeout_seconds = settings.request_timeout_seconds

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self._app(scope, receive, send)
            return

        started_at = perf_counter()
        request_id, request_id_valid = _resolve_request_id(scope)
        scope.setdefault("state", {})["request_id"] = request_id
        response_status = status.HTTP_500_INTERNAL_SERVER_ERROR
        response_started = False

        async def send_with_request_id(message: Message) -> None:
            nonlocal response_started, response_status
            if message["type"] == "http.response.start":
                response_started = True
                response_status = message["status"]
                MutableHeaders(scope=message)["X-Request-Id"] = request_id
            await send(message)

        request = Request(scope, receive=receive)
        try:
            if not request_id_valid:
                response = safe_error_response(
                    request,
                    error_code="AI_INVALID_REQUEST_ID",
                    message="Request ID is invalid.",
                    retryable=False,
                    status_code=status.HTTP_400_BAD_REQUEST,
                )
                await response(scope, receive, send_with_request_id)
                return

            provided_tokens = _header_values(scope, INTERNAL_TOKEN_HEADER)
            forwarded_authorization = _header_values(scope, AUTHORIZATION_HEADER)
            authenticated = (
                len(provided_tokens) == 1
                and not forwarded_authorization
                and secrets.compare_digest(provided_tokens[0], self._expected_token)
            )
            if not authenticated:
                response = safe_error_response(
                    request,
                    error_code="AI_INTERNAL_AUTH_FAILED",
                    message="Internal service authentication failed.",
                    retryable=False,
                    status_code=status.HTTP_401_UNAUTHORIZED,
                )
                await response(scope, receive, send_with_request_id)
                return

            try:
                async with asyncio.timeout(self._timeout_seconds):
                    await self._app(scope, receive, send_with_request_id)
            except TimeoutError:
                scope["state"]["error_code"] = "AI_REQUEST_TIMEOUT"
                response_status = status.HTTP_504_GATEWAY_TIMEOUT
                if not response_started:
                    response = safe_error_response(
                        request,
                        error_code="AI_REQUEST_TIMEOUT",
                        message="AI request processing timed out.",
                        retryable=True,
                        status_code=response_status,
                    )
                    await response(scope, receive, send_with_request_id)
        except Exception:
            scope["state"]["error_code"] = "AI_INTERNAL_ERROR"
            raise
        finally:
            duration_ms = (perf_counter() - started_at) * 1000
            try:
                logger.info(
                    "ai_request requestId=%s method=%s path=%s status=%s durationMs=%.3f errorCode=%s",
                    request_id,
                    _safe_log_method(scope),
                    _safe_log_path(scope),
                    response_status,
                    duration_ms,
                    scope.get("state", {}).get("error_code", "NONE"),
                )
            except Exception:
                pass
