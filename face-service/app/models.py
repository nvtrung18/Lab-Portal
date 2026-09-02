from __future__ import annotations

import base64
import binascii
import math
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


Embedding = Annotated[list[float], Field(min_length=1, max_length=4096)]
ChallengeAction = Literal["TURN_LEFT", "TURN_RIGHT", "OBSERVE"]
FaceResult = Literal[
    "OK",
    "MATCH",
    "NO_FACE",
    "MULTIPLE_FACES",
    "LOW_QUALITY",
    "NO_MATCH",
    "SPOOF_DETECTED",
    "CHALLENGE_MISSING",
    "CHALLENGE_INVALID",
    "CHALLENGE_FACE_INVALID",
    "CHALLENGE_START_NOT_FRONTAL",
    "CHALLENGE_TURN_NOT_DETECTED",
    "OBSERVATION_TOO_SHORT",
    "OBSERVATION_FACE_INVALID",
    "OBSERVATION_NOT_FRONTAL",
    "OBSERVATION_NOT_LIVE",
    "SERVICE_ERROR",
]
FailureReason = Literal[
    "NO_FACE",
    "MULTIPLE_FACES",
    "LOW_QUALITY",
    "NO_MATCH",
    "SPOOF_DETECTED",
    "CHALLENGE_MISSING",
    "CHALLENGE_INVALID",
    "CHALLENGE_FACE_INVALID",
    "CHALLENGE_START_NOT_FRONTAL",
    "CHALLENGE_TURN_NOT_DETECTED",
    "OBSERVATION_TOO_SHORT",
    "OBSERVATION_FACE_INVALID",
    "OBSERVATION_NOT_FRONTAL",
    "OBSERVATION_NOT_LIVE",
    "SERVICE_ERROR",
]


class ImageRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    image_base64: str = Field(alias="imageBase64", min_length=1, max_length=14_000_000)
    content_type: Literal["image/jpeg", "image/png"] = Field(alias="contentType")
    liveness_required: bool = Field(default=True, alias="livenessRequired")
    challenge_token: str | None = Field(default=None, alias="challengeToken", max_length=4096)
    challenge_frames: list["ChallengeFrame"] = Field(default_factory=list, alias="challengeFrames", max_length=8)

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


class ChallengeFrame(BaseModel):
    model_config = ConfigDict(extra="forbid")

    image_base64: str = Field(alias="imageBase64", min_length=1, max_length=14_000_000)
    content_type: Literal["image/jpeg", "image/png"] = Field(alias="contentType")

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


class ChallengeStartResponse(BaseModel):
    challenge_token: str = Field(alias="challengeToken")
    action: ChallengeAction
    expires_at: int = Field(alias="expiresAt")


class MatchRequest(ImageRequest):
    reference_embedding: Embedding | None = Field(default=None, alias="referenceEmbedding")
    reference_embeddings: list[Embedding] | None = Field(
        default=None, alias="referenceEmbeddings", min_length=1, max_length=3
    )
    confidence_threshold: float = Field(alias="confidenceThreshold", ge=0, le=1)
    liveness_threshold: float = Field(alias="livenessThreshold", ge=0, le=1)

    @field_validator("reference_embedding")
    @classmethod
    def finite_embedding(cls, embedding: list[float] | None) -> list[float] | None:
        if embedding is None:
            return None
        if any(not math.isfinite(value) for value in embedding):
            raise ValueError("Reference embedding must contain finite values")
        return embedding

    @field_validator("reference_embeddings")
    @classmethod
    def finite_embeddings(cls, embeddings: list[list[float]] | None) -> list[list[float]] | None:
        if embeddings is not None and any(
            not math.isfinite(value) for embedding in embeddings for value in embedding
        ):
            raise ValueError("Reference embeddings must contain finite values")
        return embeddings

    @model_validator(mode="after")
    def exactly_one_reference_shape(self) -> "MatchRequest":
        if (self.reference_embedding is None) == (self.reference_embeddings is None):
            raise ValueError("Provide exactly one reference embedding shape")
        return self

    def all_reference_embeddings(self) -> list[list[float]]:
        return self.reference_embeddings or [self.reference_embedding or []]


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
    active_liveness_passed: bool = Field(default=False, alias="activeLivenessPassed")
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")


class MatchResponse(BaseModel):
    result: FaceResult
    confidence_score: float | None = Field(default=None, alias="confidenceScore", ge=0, le=1)
    liveness_score: float | None = Field(default=None, alias="livenessScore", ge=0, le=1)
    active_liveness_passed: bool = Field(default=False, alias="activeLivenessPassed")
    passive_liveness_passed: bool = Field(default=False, alias="passiveLivenessPassed")
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")


class LivenessResponse(BaseModel):
    result: FaceResult
    confidence_score: float | None = Field(default=None, alias="confidenceScore", ge=0, le=1)
    liveness_score: float | None = Field(default=None, alias="livenessScore", ge=0, le=1)
    failure_reason: FailureReason | None = Field(default=None, alias="failureReason")


class GuidanceResponse(BaseModel):
    detected_faces: int = Field(alias="detectedFaces", ge=0)
    single_face: bool = Field(alias="singleFace")
    face_in_guide: bool = Field(alias="faceInGuide")
    facing_forward: bool = Field(alias="facingForward")
    landmarks_visible: bool = Field(alias="landmarksVisible")
    lighting_good: bool = Field(alias="lightingGood")
    sharpness_good: bool = Field(alias="sharpnessGood")
    center_x: float | None = Field(default=None, alias="centerX", ge=0, le=1)
    center_y: float | None = Field(default=None, alias="centerY", ge=0, le=1)
    face_width_ratio: float | None = Field(default=None, alias="faceWidthRatio", ge=0, le=1)
    face_height_ratio: float | None = Field(default=None, alias="faceHeightRatio", ge=0, le=1)
    failure_reason: str | None = Field(default=None, alias="failureReason")
