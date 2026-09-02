from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
import time
from dataclasses import dataclass


CHALLENGE_TTL_SECONDS = 180
CHALLENGE_ACTIONS = ("TURN_LEFT", "TURN_RIGHT")
OBSERVATION_ACTION = "OBSERVE"


@dataclass(frozen=True)
class Challenge:
    token: str
    action: str
    expires_at: int


def create_challenge(secret: str) -> Challenge:
    expires_at = int(time.time()) + CHALLENGE_TTL_SECONDS
    payload = {
        "action": secrets.choice(CHALLENGE_ACTIONS),
        "expiresAt": expires_at,
        "nonce": secrets.token_urlsafe(18),
    }
    encoded = _encode(payload)
    signature = hmac.new(secret.encode("utf-8"), encoded.encode("ascii"), hashlib.sha256).hexdigest()
    return Challenge(f"{encoded}.{signature}", payload["action"], expires_at)


def create_observation_session(secret: str) -> Challenge:
    issued_at = int(time.time())
    expires_at = issued_at + CHALLENGE_TTL_SECONDS
    payload = {
        "action": OBSERVATION_ACTION,
        "issuedAt": issued_at,
        "expiresAt": expires_at,
        "nonce": secrets.token_urlsafe(18),
    }
    encoded = _encode(payload)
    signature = hmac.new(secret.encode("utf-8"), encoded.encode("ascii"), hashlib.sha256).hexdigest()
    return Challenge(f"{encoded}.{signature}", OBSERVATION_ACTION, expires_at)


def verify_challenge(token: str, secret: str) -> dict[str, object] | None:
    try:
        encoded, provided_signature = token.split(".", 1)
        expected_signature = hmac.new(
            secret.encode("utf-8"), encoded.encode("ascii"), hashlib.sha256
        ).hexdigest()
        if not hmac.compare_digest(provided_signature, expected_signature):
            return None
        payload = json.loads(_decode(encoded))
        if not isinstance(payload, dict) or payload.get("action") not in (*CHALLENGE_ACTIONS, OBSERVATION_ACTION):
            return None
        if int(payload.get("expiresAt", 0)) < int(time.time()):
            return None
        return payload
    except (ValueError, TypeError, json.JSONDecodeError, UnicodeDecodeError):
        return None


def _encode(payload: dict[str, object]) -> str:
    return base64.urlsafe_b64encode(
        json.dumps(payload, separators=(",", ":")).encode("utf-8")
    ).decode("ascii").rstrip("=")


def _decode(encoded: str) -> str:
    return base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)).decode("utf-8")
