from __future__ import annotations

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings
from app.models import (
    DetectionResponse,
    EmbeddingResponse,
    ImageRequest,
    LivenessResponse,
    MatchRequest,
    MatchResponse,
    QualityResponse,
)
from app.processor import FaceImage, FaceProcessor, FaceProcessorUnavailable, UnavailableFaceProcessor
from app.security import InternalSecurityMiddleware, error_response


def create_app(settings: Settings | None = None, processor: FaceProcessor | None = None) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    resolved_processor = processor or UnavailableFaceProcessor()
    application = FastAPI(title=resolved_settings.service_name, version="0.1.0")
    application.state.settings = resolved_settings

    @application.get("/health")
    def health() -> dict[str, str]:
        return {"status": "UP", "service": resolved_settings.service_name}

    def face_image(request: ImageRequest) -> FaceImage:
        return FaceImage(request.image_bytes(), request.content_type, request.liveness_required)

    @application.post("/v1/face/detect", response_model=DetectionResponse, response_model_by_alias=True)
    def detect(request: ImageRequest) -> DetectionResponse:
        return resolved_processor.detect(face_image(request))

    @application.post("/v1/face/quality", response_model=QualityResponse, response_model_by_alias=True)
    def quality(request: ImageRequest) -> QualityResponse:
        return resolved_processor.quality(face_image(request))

    @application.post("/v1/face/embed", response_model=EmbeddingResponse, response_model_by_alias=True)
    def embed(request: ImageRequest) -> EmbeddingResponse:
        return resolved_processor.embed(face_image(request))

    @application.post("/v1/face/match", response_model=MatchResponse, response_model_by_alias=True)
    def match(request: MatchRequest) -> MatchResponse:
        return resolved_processor.match(
            face_image(request),
            request.reference_embedding,
            request.confidence_threshold,
            request.liveness_threshold,
        )

    @application.post("/v1/face/liveness", response_model=LivenessResponse, response_model_by_alias=True)
    def liveness(request: ImageRequest) -> LivenessResponse:
        return resolved_processor.liveness(face_image(request))

    async def validation_error_handler(request: Request, _exception: RequestValidationError) -> JSONResponse:
        return error_response(
            request.state.request_id,
            "FACE_INVALID_REQUEST",
            "Request validation failed.",
            status.HTTP_422_UNPROCESSABLE_CONTENT,
        )

    async def unexpected_error_handler(request: Request, _exception: Exception) -> JSONResponse:
        return error_response(
            request.state.request_id,
            "FACE_INTERNAL_ERROR",
            "An unexpected server error occurred.",
            status.HTTP_500_INTERNAL_SERVER_ERROR,
        )

    async def processor_unavailable_handler(request: Request, _exception: FaceProcessorUnavailable) -> JSONResponse:
        response = error_response(
            request.state.request_id,
            "FACE_MODEL_NOT_READY",
            "Face model backend is not ready.",
            status.HTTP_503_SERVICE_UNAVAILABLE,
            retryable=True,
        )
        return response

    application.add_middleware(InternalSecurityMiddleware, settings=resolved_settings)
    application.add_exception_handler(RequestValidationError, validation_error_handler)
    application.add_exception_handler(FaceProcessorUnavailable, processor_unavailable_handler)
    application.add_exception_handler(Exception, unexpected_error_handler)
    return application


app = create_app()
