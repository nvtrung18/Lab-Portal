from __future__ import annotations

import os
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, SecretStr, StringConstraints, field_validator


NonBlankText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class Settings(BaseModel):
    """Environment-backed settings with no business-database configuration."""

    model_config = ConfigDict(extra="forbid", frozen=True, hide_input_in_errors=True)

    service_name: NonBlankText = "face-service"
    environment: NonBlankText = "local"
    internal_service_token: SecretStr = Field(exclude=True, repr=False)
    challenge_secret: SecretStr | None = Field(default=None, exclude=True, repr=False)
    request_timeout_seconds: float = Field(default=3.0, gt=0, le=30)
    processor_mode: NonBlankText = "unavailable"
    detector_model_path: NonBlankText = "/app/models/face_detection_yunet_2023mar.onnx"
    recognizer_model_path: NonBlankText = "/app/models/face_recognition_sface_2021dec.onnx"
    liveness_model_path: NonBlankText = "/app/models/minifasnet_v2.onnx"
    liveness_threshold: float = Field(default=0.7, ge=0, le=1)

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
            "service_name": "FACE_SERVICE_NAME",
            "environment": "FACE_ENVIRONMENT",
            "internal_service_token": "FACE_INTERNAL_SERVICE_TOKEN",
            "challenge_secret": "FACE_CHALLENGE_SECRET",
            "request_timeout_seconds": "FACE_REQUEST_TIMEOUT_SECONDS",
            "processor_mode": "FACE_PROCESSOR_MODE",
            "detector_model_path": "FACE_DETECTOR_MODEL_PATH",
            "recognizer_model_path": "FACE_RECOGNIZER_MODEL_PATH",
            "liveness_model_path": "FACE_LIVENESS_MODEL_PATH",
            "liveness_threshold": "FACE_LIVENESS_THRESHOLD",
        }
        for field_name, environment_name in environment_names.items():
            value = os.getenv(environment_name)
            if value is not None:
                values[field_name] = value
        return cls.model_validate(values)
