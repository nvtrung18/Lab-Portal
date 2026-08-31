from __future__ import annotations

import base64
import binascii
import math
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


Embedding = Annotated[list[float], Field(min_length=1, max_length=4096)]
FaceResult = Literal[
    "OK",
    "MATCH",
    "NO_FACE",
    "MULTIPLE_FACES",
    "LOW_QUALITY",
    "NO_MATCH",
    "SPOOF_DETECTED",
    "SERVICE_ERROR",
]
FailureReason = Literal[
    "NO_FACE",
    "MULTIPLE_FACES",
    "LOW_QUALITY",
    "NO_MATCH",
    "SPOOF_DETECTED",
    "SERVICE_ERROR",
]


class ImageRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    image_base64: str = Field(alias="imageBase64", min_length=1, max_length=14_000_000)
    content_type: Literal["image/jpeg", "image/png"] = Field(alias="contentType")
    liveness_required: bool = Field(default=True, alias="livenessRequired")

    @field_validator("image_base64")
    @classmethod
    def valid_image_base64(cls, image_base64: str) -> str:
        try:
            decoded = base64.b64decode(image_base64, validate=True)
        except (ValueError, binascii.Error) as exception:
            raise ValueError("Image must be valid base64") from exception
        if not decoded or len(decoded) > 10_000_000:
            raise ValueError("Decoded image size is invalid")
        return image_base64

    def image_bytes(self) -> bytes:
        return base64.b64decode(self.image_base64, validate=True)


class MatchRequest(ImageRequest):
    reference_embedding: Embedding = Field(alias="referenceEmbedding")
    confidence_threshold: float = Field(alias="confidenceThreshold", ge=0, le=1)
    liveness_threshold: float = Field(alias="livenessThreshold", ge=0, le=1)

    @field_validator("reference_embedding")
    @classmethod
    def finite_embedding(cls, embedding: list[float]) -> list[float]:
        if any(not math.isfinite(value) for value in embedding):
            raise ValueError("Reference embedding must contain finite values")
        return embedding


class QualityResult(BaseModel):
    passed: bool
    reason: str | None = None


class DetectionResponse(BaseModel):
    result: FaceResult
    detected_faces: int = Field(alias="detectedFaces", ge=0)
    confidence_score: float | None = Field(default=None, alias="confidenceScore", ge=0, le=1)
    liveness_score: float | None = Field(default=None, alias="livenessScore", ge=0, le=1)
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")


class QualityResponse(BaseModel):
    result: FaceResult
    quality: QualityResult
    confidence_score: float | None = Field(default=None, alias="confidenceScore", ge=0, le=1)
    liveness_score: float | None = Field(default=None, alias="livenessScore", ge=0, le=1)
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")


class EmbeddingResponse(BaseModel):
    result: FaceResult
    embedding: Embedding | None = None
    embedding_model: str | None = Field(default=None, alias="embeddingModel")
    quality: QualityResult
    confidence_score: float | None = Field(default=None, alias="confidenceScore", ge=0, le=1)
    liveness_score: float | None = Field(default=None, alias="livenessScore", ge=0, le=1)
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")


class MatchResponse(BaseModel):
    result: FaceResult
    confidence_score: float | None = Field(default=None, alias="confidenceScore", ge=0, le=1)
    liveness_score: float | None = Field(default=None, alias="livenessScore", ge=0, le=1)
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")


class LivenessResponse(BaseModel):
    result: FaceResult
    confidence_score: float | None = Field(default=None, alias="confidenceScore", ge=0, le=1)
    liveness_score: float | None = Field(default=None, alias="livenessScore", ge=0, le=1)
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")
