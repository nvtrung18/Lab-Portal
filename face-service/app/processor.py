from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from app.models import DetectionResponse, EmbeddingResponse, LivenessResponse, MatchResponse, QualityResponse


@dataclass(frozen=True)
class FaceImage:
    content: bytes
    content_type: str
    liveness_required: bool


class FaceProcessor(Protocol):
    def detect(self, image: FaceImage) -> DetectionResponse: ...
    def quality(self, image: FaceImage) -> QualityResponse: ...
    def embed(self, image: FaceImage) -> EmbeddingResponse: ...
    def match(
        self,
        image: FaceImage,
        reference_embedding: list[float],
        confidence_threshold: float,
        liveness_threshold: float,
    ) -> MatchResponse: ...
    def liveness(self, image: FaceImage) -> LivenessResponse: ...


class FaceProcessorUnavailable(RuntimeError):
    pass


class UnavailableFaceProcessor:
    """Fail-closed production default until an approved model backend is configured."""

    def _unavailable(self) -> None:
        raise FaceProcessorUnavailable("Face model backend is not configured")

    def detect(self, _image: FaceImage) -> DetectionResponse:
        self._unavailable()

    def quality(self, _image: FaceImage) -> QualityResponse:
        self._unavailable()

    def embed(self, _image: FaceImage) -> EmbeddingResponse:
        self._unavailable()

    def match(
        self,
        _image: FaceImage,
        _reference_embedding: list[float],
        _confidence_threshold: float,
        _liveness_threshold: float,
    ) -> MatchResponse:
        self._unavailable()

    def liveness(self, _image: FaceImage) -> LivenessResponse:
        self._unavailable()
