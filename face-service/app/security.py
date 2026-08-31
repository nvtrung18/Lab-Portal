from __future__ import annotations

import secrets
import re
from uuid import uuid4

from fastapi import Request, status
from fastapi.responses import JSONResponse
from starlette.datastructures import MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.config import Settings


INTERNAL_TOKEN_HEADER = b"x-internal-service-token"
REQUEST_ID_HEADER = b"x-request-id"
AUTHORIZATION_HEADER = b"authorization"
REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")


def _header_values(scope: Scope, name: bytes) -> list[str]:
    return [value.decode("latin-1") for key, value in scope.get("headers", ()) if key.lower() == name]


def error_response(
    request_id: str,
    error_code: str,
    message: str,
    status_code: int,
    *,
    retryable: bool = False,
) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "errorCode": error_code,
            "message": message,
            "retryable": retryable,
            "requestId": request_id,
        },
        headers={"X-Request-Id": request_id},
    )


class InternalSecurityMiddleware:
    """Authenticates only the trusted Spring caller; it grants no business authority."""

    def __init__(self, app: ASGIApp, settings: Settings) -> None:
        self._app = app
        self._expected_token = settings.internal_service_token.get_secret_value()

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self._app(scope, receive, send)
            return

        request_ids = _header_values(scope, REQUEST_ID_HEADER)
        request_id = (
            request_ids[0]
            if len(request_ids) == 1 and REQUEST_ID_PATTERN.fullmatch(request_ids[0]) is not None
            else uuid4().hex
        )
        scope.setdefault("state", {})["request_id"] = request_id

        async def send_with_request_id(message: Message) -> None:
            if message["type"] == "http.response.start":
                MutableHeaders(scope=message)["X-Request-Id"] = request_id
            await send(message)

        provided_tokens = _header_values(scope, INTERNAL_TOKEN_HEADER)
        forwarded_authorization = _header_values(scope, AUTHORIZATION_HEADER)
        authenticated = (
            len(provided_tokens) == 1
            and not forwarded_authorization
            and secrets.compare_digest(provided_tokens[0], self._expected_token)
        )
        if not authenticated:
            response = error_response(
                request_id,
                "FACE_INTERNAL_AUTH_FAILED",
                "Internal service authentication failed.",
                status.HTTP_401_UNAUTHORIZED,
            )
            await response(scope, receive, send_with_request_id)
            return

        await self._app(scope, receive, send_with_request_id)
