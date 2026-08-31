from __future__ import annotations

import base64

from fastapi.testclient import TestClient
from pydantic import SecretStr

from app.config import Settings
from app.main import create_app
from app.models import (
    DetectionResponse,
    EmbeddingResponse,
    LivenessResponse,
    MatchResponse,
    QualityResponse,
    QualityResult,
)
from app.processor import FaceImage


HEADERS = {
    "X-Internal-Service-Token": "processing-test-token",
    "X-Request-Id": "face-contract-test",
}
IMAGE = {
    "imageBase64": base64.b64encode(b"test-image-bytes").decode("ascii"),
    "contentType": "image/jpeg",
    "livenessRequired": True,
}


class ContractProcessor:
    def detect(self, image: FaceImage) -> DetectionResponse:
        assert image.content == b"test-image-bytes"
        return DetectionResponse(result="OK", detectedFaces=1, confidenceScore=0.98)

    def quality(self, _image: FaceImage) -> QualityResponse:
        return QualityResponse(result="OK", quality=QualityResult(passed=True), confidenceScore=0.96)

    def embed(self, _image: FaceImage) -> EmbeddingResponse:
        return EmbeddingResponse(
            result="OK",
            embedding=[0.1, 0.2],
            embeddingModel="face-model-v1",
            quality=QualityResult(passed=True),
            livenessScore=0.91,
        )

    def match(
        self,
        _image: FaceImage,
        reference_embedding: list[float],
        confidence_threshold: float,
        liveness_threshold: float,
    ) -> MatchResponse:
        assert reference_embedding == [0.1, 0.2]
        assert confidence_threshold == 0.85
        assert liveness_threshold == 0.7
        return MatchResponse(result="MATCH", confidenceScore=0.92, livenessScore=0.9)

    def liveness(self, _image: FaceImage) -> LivenessResponse:
        return LivenessResponse(result="OK", livenessScore=0.9)


def client(processor: object | None = None) -> TestClient:
    settings = Settings(internal_service_token=SecretStr("processing-test-token"))
    return TestClient(create_app(settings, processor=processor))


def test_processing_routes_publish_and_honor_the_frozen_contract() -> None:
    test_client = client(ContractProcessor())

    detect = test_client.post("/v1/face/detect", json=IMAGE, headers=HEADERS)
    quality = test_client.post("/v1/face/quality", json=IMAGE, headers=HEADERS)
    embed = test_client.post("/v1/face/embed", json=IMAGE, headers=HEADERS)
    match = test_client.post(
        "/v1/face/match",
        json={
            **IMAGE,
            "referenceEmbedding": [0.1, 0.2],
            "confidenceThreshold": 0.85,
            "livenessThreshold": 0.7,
        },
        headers=HEADERS,
    )
    liveness = test_client.post("/v1/face/liveness", json=IMAGE, headers=HEADERS)

    assert detect.json() == {
        "result": "OK",
        "detectedFaces": 1,
        "confidenceScore": 0.98,
        "livenessScore": None,
        "failureReason": None,
    }
    assert quality.json()["quality"] == {"passed": True, "reason": None}
    assert embed.json()["embedding"] == [0.1, 0.2]
    assert embed.json()["embeddingModel"] == "face-model-v1"
    assert match.json()["result"] == "MATCH"
    assert match.json()["confidenceScore"] == 0.92
    assert liveness.json()["livenessScore"] == 0.9


def test_unconfigured_model_fails_closed_without_fabricated_result() -> None:
    response = client().post("/v1/face/embed", json=IMAGE, headers=HEADERS)

    assert response.status_code == 503
    assert response.json() == {
        "errorCode": "FACE_MODEL_NOT_READY",
        "message": "Face model backend is not ready.",
        "retryable": True,
        "requestId": "face-contract-test",
    }


def test_invalid_image_or_embedding_is_rejected_at_the_boundary() -> None:
    test_client = client(ContractProcessor())

    invalid_image = test_client.post(
        "/v1/face/detect", json={**IMAGE, "imageBase64": "not-base64"}, headers=HEADERS
    )
    invalid_embedding = test_client.post(
        "/v1/face/match",
        json={
            **IMAGE,
            "referenceEmbedding": [],
            "confidenceThreshold": 0.85,
            "livenessThreshold": 0.7,
        },
        headers=HEADERS,
    )

    assert invalid_image.status_code == 422
    assert invalid_embedding.status_code == 422
    assert invalid_image.json()["errorCode"] == "FACE_INVALID_REQUEST"
