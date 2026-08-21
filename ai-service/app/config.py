from __future__ import annotations

import os
from pathlib import Path
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, SecretStr, StringConstraints, field_validator


NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]
DEFAULT_PROFILE_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "assistant-profiles.json"
DEFAULT_ARTIFACT_CONFIG_PATH = Path(__file__).resolve().parents[1] / "config" / "model-artifacts.json"
DEFAULT_ARTIFACT_ROOT = Path(__file__).resolve().parents[1] / "artifacts"
DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH = (
    Path(__file__).resolve().parents[1] / "config" / "schemas" / "structured-output-schemas.json"
)


class Settings(BaseModel):
    """Environment-backed AI service settings."""

    model_config = ConfigDict(extra="forbid", frozen=True, hide_input_in_errors=True)

    service_name: NonBlankText = "ai-service"
    environment: NonBlankText = "local"
    internal_service_token: SecretStr = Field(exclude=True, repr=False)
    request_timeout_seconds: float = Field(default=5.0, gt=0, le=300)
    profile_config_path: Path = DEFAULT_PROFILE_CONFIG_PATH
    artifact_config_path: Path = DEFAULT_ARTIFACT_CONFIG_PATH
    artifact_root: Path = DEFAULT_ARTIFACT_ROOT
    output_schema_config_path: Path = DEFAULT_OUTPUT_SCHEMA_CONFIG_PATH

    @field_validator("internal_service_token")
    @classmethod
    def validate_internal_service_token(cls, value: SecretStr) -> SecretStr:
        token = value.get_secret_value()
        if not token or len(token) > 1024 or any(not 0x21 <= ord(character) <= 0x7E for character in token):
            raise ValueError("Internal service token configuration is invalid.")
        return value

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
            "output_schema_config_path": "AI_OUTPUT_SCHEMAS_PATH",
        }
        for field_name, environment_name in environment_names.items():
            value = os.getenv(environment_name)
            if value is not None:
                values[field_name] = value
        return cls.model_validate(values)
