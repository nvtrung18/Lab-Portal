from __future__ import annotations

import os
from pathlib import Path
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, SecretStr, StringConstraints


NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
DEFAULT_PROFILE_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "assistant-profiles.json"
DEFAULT_ARTIFACT_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "model-artifacts.json"
DEFAULT_ARTIFACT_ROOT = Path(__file__).resolve().parents[1] / "artifacts"


class Settings(BaseModel):
    """Environment-backed foundation settings.

    The internal token is only a compatibility hook in P8-T1. Authentication is
    intentionally deferred to P8-T5.
    """

    model_config = ConfigDict(extra="forbid", frozen=True)

    service_name: NonBlankText = "ai-service"
    environment: NonBlankText = "local"
    internal_service_token: SecretStr | None = None
    request_timeout_seconds: float = Field(default=5.0, gt=0, le=300)
    profile_config_path: Path = DEFAULT_PROFILE_CONFIG_PATH
    artifact_config_path: Path = DEFAULT_ARTIFACT_CONFIG_PATH
    artifact_root: Path = DEFAULT_ARTIFACT_ROOT

    @classmethod
    def from_env(cls) -> "Settings":
        values: dict[str, str] = {}
        environment_names = {
            "service_name": "AI_SERVICE_NAME",
            "environment": "AI_ENVIRONMENT",
            "internal_service_token": "AI_INTERNAL_SERVICE_TOKEN",
            "request_timeout_seconds": "AI_REQUEST_TIMEOUT_SECONDS",
            "profile_config_path": "AI_ASSISTANT_PROFILES_PATH",
            "artifact_config_path": "AI_MODEL_ARTIFACTS_PATH",
            "artifact_root": "AI_MODEL_ARTIFACT_ROOT",
        }
        for field_name, environment_name in environment_names.items():
            value = os.getenv(environment_name)
            if value is not None:
                values[field_name] = value
        return cls.model_validate(values)
