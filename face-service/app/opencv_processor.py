from __future__ import annotations

import math
import logging
import time
from pathlib import Path

import cv2
import numpy as np

from app.config import Settings
from app.liveness_challenge import OBSERVATION_ACTION, verify_challenge
from app.models import (
    DetectionResponse,
    EmbeddingResponse,
    GuidanceResponse,
    LivenessResponse,
    MatchResponse,
    QualityResponse,
    QualityResult,
)
from app.processor import FaceImage, FaceProcessorUnavailable


logger = logging.getLogger("face-service")
logger.setLevel(logging.INFO)


class OpenCvFaceProcessor:
    """CPU face pipeline: YuNet detection, SFace embeddings and MiniFASNetV2 anti-spoofing."""

    MODEL_NAME = "opencv-sface-2021dec"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        paths = (
            settings.detector_model_path,
            settings.recognizer_model_path,
            settings.liveness_model_path,
        )
        if any(not Path(path).is_file() for path in paths):
            raise FaceProcessorUnavailable("One or more approved face model files are missing")
        self._detector = cv2.FaceDetectorYN.create(
            settings.detector_model_path, "", (320, 320), 0.85, 0.3, 5000
        )
        self._recognizer = cv2.FaceRecognizerSF.create(settings.recognizer_model_path, "")
        self._liveness = cv2.dnn.readNetFromONNX(settings.liveness_model_path)
        self._liveness_threshold = settings.liveness_threshold

    def detect(self, image: FaceImage) -> DetectionResponse:
        frame, faces = self._decode_and_detect(image)
        del frame
        count = len(faces)
        if count == 0:
            return DetectionResponse(result="NO_FACE", detectedFaces=0, failureReason="NO_FACE")
        if count != 1:
            return DetectionResponse(result="MULTIPLE_FACES", detectedFaces=count,
                                     failureReason="MULTIPLE_FACES")
        return DetectionResponse(result="OK", detectedFaces=1,
                                 confidenceScore=float(faces[0][14]))

    def quality(self, image: FaceImage) -> QualityResponse:
        frame, faces = self._decode_and_detect(image)
        failure = self._face_count_failure(len(faces))
        if failure:
            return QualityResponse(result=failure, quality=QualityResult(passed=False, reason=failure),
                                   failureReason=failure)
        passed, reason = self._quality(frame, faces[0])
        return QualityResponse(
            result="OK" if passed else "LOW_QUALITY",
            quality=QualityResult(passed=passed, reason=reason),
            confidenceScore=float(faces[0][14]),
            failureReason=None if passed else "LOW_QUALITY",
        )

    def embed(self, image: FaceImage) -> EmbeddingResponse:
        frame, faces = self._decode_and_detect(image)
        failure = self._face_count_failure(len(faces))
        if failure:
            return EmbeddingResponse(result=failure, quality=QualityResult(passed=False, reason=failure),
                                     failureReason=failure)
        face = faces[0]
        passed, reason = self._quality(frame, face)
        if not passed:
            return EmbeddingResponse(result="LOW_QUALITY", quality=QualityResult(passed=False, reason=reason),
                                     confidenceScore=float(face[14]), failureReason="LOW_QUALITY")
        liveness = self._liveness_score(frame, face)
        active_liveness_passed, active_liveness_failure = self._active_liveness_result(image)
        if image.liveness_required and liveness < self._liveness_threshold and not active_liveness_passed:
            return EmbeddingResponse(result="SPOOF_DETECTED", quality=QualityResult(passed=True),
                                     confidenceScore=float(face[14]), livenessScore=liveness,
                                     failureReason=active_liveness_failure, activeLivenessPassed=False)
        aligned = self._recognizer.alignCrop(frame, face)
        feature = self._recognizer.feature(aligned).flatten().astype(float).tolist()
        if not feature or any(not math.isfinite(value) for value in feature):
            raise FaceProcessorUnavailable("SFace returned an invalid embedding")
        return EmbeddingResponse(result="OK", embedding=feature, embeddingModel=self.MODEL_NAME,
                                 quality=QualityResult(passed=True), confidenceScore=float(face[14]),
                                 livenessScore=liveness, activeLivenessPassed=active_liveness_passed)

    def match(self, image: FaceImage, reference_embeddings: list[list[float]], confidence_threshold: float,
              liveness_threshold: float) -> MatchResponse:
        frame, faces = self._decode_and_detect(image)
        failure = self._face_count_failure(len(faces))
        if failure:
            return MatchResponse(result=failure, failureReason=failure)
        face = faces[0]
        passed, _ = self._quality(frame, face)
        if not passed:
            return MatchResponse(result="LOW_QUALITY", confidenceScore=float(face[14]),
                                 failureReason="LOW_QUALITY")
        liveness = self._liveness_score(frame, face)
        passive_liveness_passed, passive_liveness_failure = self._passive_liveness_result(image)
        if image.liveness_required and not passive_liveness_passed:
            return MatchResponse(result="SPOOF_DETECTED", livenessScore=liveness,
                                 activeLivenessPassed=False,
                                 passiveLivenessPassed=False,
                                 failureReason=passive_liveness_failure)
        references = [np.asarray(embedding, dtype=np.float32).reshape(1, -1)
                      for embedding in reference_embeddings]
        aligned = self._recognizer.alignCrop(frame, face)
        candidates = [self._recognizer.feature(aligned).astype(np.float32)]
        if passive_liveness_passed:
            for content in image.challenge_frames:
                observed_frame, observed_faces = self._decode_and_detect(
                    FaceImage(content, image.content_type, False)
                )
                if len(observed_faces) != 1:
                    return MatchResponse(result="OBSERVATION_FACE_INVALID", livenessScore=liveness,
                                         passiveLivenessPassed=False,
                                         failureReason="OBSERVATION_FACE_INVALID")
                observed_aligned = self._recognizer.alignCrop(observed_frame, observed_faces[0])
                candidates.append(self._recognizer.feature(observed_aligned).astype(np.float32))
        if not references or any(
            candidate.size != reference.size for candidate in candidates for reference in references
        ):
            return MatchResponse(result="SERVICE_ERROR", livenessScore=liveness,
                                 passiveLivenessPassed=passive_liveness_passed,
                                 failureReason="SERVICE_ERROR")
        similarities = [max(float(self._recognizer.match(
            reference, candidate, cv2.FaceRecognizerSF_FR_COSINE
        )) for reference in references) for candidate in candidates]
        similarity = float(np.median(similarities))
        similarity = max(0.0, min(1.0, similarity))
        matched = similarity >= confidence_threshold
        return MatchResponse(result="MATCH" if matched else "NO_MATCH", confidenceScore=similarity,
                             livenessScore=liveness, passiveLivenessPassed=passive_liveness_passed,
                             failureReason=None if matched else "NO_MATCH")

    def _passive_liveness_result(self, image: FaceImage) -> tuple[bool, str | None]:
        if not image.challenge_token or len(image.challenge_frames) < 4:
            return False, "OBSERVATION_TOO_SHORT"
        settings = getattr(self, "_settings", None)
        if settings is None:
            return False, "CHALLENGE_INVALID"
        secret = settings.challenge_secret or settings.internal_service_token
        payload = verify_challenge(image.challenge_token, secret.get_secret_value())
        if payload is None or payload.get("action") != OBSERVATION_ACTION:
            return False, "CHALLENGE_INVALID"
        issued_at = int(payload.get("issuedAt", 0))
        if issued_at <= 0 or int(time.time()) - issued_at < 2:
            return False, "OBSERVATION_TOO_SHORT"

        crops: list[np.ndarray] = []
        yaws: list[float] = []
        for content in (image.content, *image.challenge_frames):
            frame, faces = self._decode_and_detect(FaceImage(content, image.content_type, False))
            if len(faces) != 1:
                return False, "OBSERVATION_FACE_INVALID"
            face = faces[0]
            passed, _ = self._quality(frame, face)
            yaw = self._yaw_offset(face)
            if not passed or yaw is None:
                return False, "OBSERVATION_FACE_INVALID"
            if abs(yaw) > 0.24:
                return False, "OBSERVATION_NOT_FRONTAL"
            aligned = self._recognizer.alignCrop(frame, face)
            crops.append(cv2.resize(cv2.cvtColor(aligned, cv2.COLOR_BGR2GRAY), (96, 96)))
            yaws.append(yaw)

        differences = [
            float(np.mean(cv2.absdiff(previous, current)))
            for previous, current in zip(crops, crops[1:])
        ]
        # A live camera sequence must contain sensor/expression variation while the face remains frontal.
        # This is a bounded RGB heuristic, not a replacement for depth/IR anti-spoofing hardware.
        if max(differences, default=0.0) < 0.65 or max(yaws) - min(yaws) > 0.20:
            logger.warning(
                "face_passive_liveness result=failed differences=%s yaws=%s",
                ",".join(f"{value:.3f}" for value in differences),
                ",".join(f"{value:.3f}" for value in yaws),
            )
            return False, "OBSERVATION_NOT_LIVE"
        logger.info(
            "face_passive_liveness result=passed maxDifference=%.3f yawRange=%.3f",
            max(differences), max(yaws) - min(yaws),
        )
        return True, None

    def liveness(self, image: FaceImage) -> LivenessResponse:
        frame, faces = self._decode_and_detect(image)
        failure = self._face_count_failure(len(faces))
        if failure:
            return LivenessResponse(result=failure, failureReason=failure)
        score = self._liveness_score(frame, faces[0])
        passed = score >= self._liveness_threshold
        return LivenessResponse(result="OK" if passed else "SPOOF_DETECTED", livenessScore=score,
                                failureReason=None if passed else "SPOOF_DETECTED")

    def _decode_and_detect(self, image: FaceImage) -> tuple[np.ndarray, np.ndarray]:
        frame = cv2.imdecode(np.frombuffer(image.content, dtype=np.uint8), cv2.IMREAD_COLOR)
        if frame is None or frame.size == 0:
            raise ValueError("Image cannot be decoded")
        height, width = frame.shape[:2]
        self._detector.setInputSize((width, height))
        _, detected = self._detector.detect(frame)
        faces = detected if detected is not None else np.empty((0, 15), dtype=np.float32)
        return frame, faces

    def _face_count_failure(self, count: int) -> str | None:
        if count == 0:
            return "NO_FACE"
        if count != 1:
            return "MULTIPLE_FACES"
        return None

    def _quality(self, frame: np.ndarray, face: np.ndarray) -> tuple[bool, str | None]:
        lighting_good, sharpness_good, reason = self._quality_checks(frame, face)
        return lighting_good and sharpness_good, reason

    def _quality_checks(self, frame: np.ndarray, face: np.ndarray) -> tuple[bool, bool, str | None]:
        x, y, width, height = [int(value) for value in face[:4]]
        x, y = max(0, x), max(0, y)
        crop = frame[y:min(frame.shape[0], y + height), x:min(frame.shape[1], x + width)]
        if crop.size == 0 or min(width, height) < 96:
            return False, False, "FACE_TOO_SMALL"
        gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        brightness = float(gray.mean())
        if brightness < 45:
            return False, True, "TOO_DARK"
        if brightness > 220:
            return False, True, "TOO_BRIGHT"
        if float(cv2.Laplacian(gray, cv2.CV_64F).var()) < 45:
            return True, False, "BLURRY"
        return True, True, None

    def _landmark_checks(self, face: np.ndarray) -> tuple[bool, bool]:
        right_eye = np.asarray(face[4:6], dtype=np.float32)
        left_eye = np.asarray(face[6:8], dtype=np.float32)
        nose = np.asarray(face[8:10], dtype=np.float32)
        right_mouth = np.asarray(face[10:12], dtype=np.float32)
        left_mouth = np.asarray(face[12:14], dtype=np.float32)
        eye_distance = float(np.linalg.norm(left_eye - right_eye))
        mouth_distance = float(np.linalg.norm(left_mouth - right_mouth))
        landmarks_visible = eye_distance >= 12 and mouth_distance >= 8 and float(face[14]) >= 0.88
        if not landmarks_visible:
            return False, False
        eye_midpoint = (left_eye + right_eye) / 2
        mouth_midpoint = (left_mouth + right_mouth) / 2
        facing_forward = (
            abs(float(nose[0] - eye_midpoint[0])) <= eye_distance * 0.22
            and abs(float(mouth_midpoint[0] - eye_midpoint[0])) <= eye_distance * 0.28
            and abs(float(left_eye[1] - right_eye[1])) <= eye_distance * 0.18
            and eye_midpoint[1] < nose[1] < mouth_midpoint[1]
        )
        return facing_forward, True

    def _liveness_score(self, frame: np.ndarray, face: np.ndarray) -> float:
        x, y, width, height = [float(value) for value in face[:4]]
        scale = 2.7
        crop_width, crop_height = width * scale, height * scale
        center_x, center_y = x + width / 2, y + height / 2
        left = max(0, int(center_x - crop_width / 2))
        top = max(0, int(center_y - crop_height / 2))
        right = min(frame.shape[1], int(center_x + crop_width / 2))
        bottom = min(frame.shape[0], int(center_y + crop_height / 2))
        crop = frame[top:bottom, left:right]
        if crop.size == 0:
            return 0.0
        tensor = cv2.resize(crop, (80, 80)).astype(np.float32) / 255.0
        tensor = np.transpose(tensor, (2, 0, 1))[None, ...]
        self._liveness.setInput(tensor)
        logits = np.asarray(self._liveness.forward()).reshape(-1)
        probabilities = np.exp(logits - np.max(logits))
        probabilities /= probabilities.sum()
        logger.warning(
            "face_liveness_scores live=%.6f print_attack=%.6f replay_attack=%.6f",
            float(probabilities[0]), float(probabilities[1]), float(probabilities[2]),
        )
        # MiniFASNetV2 exports [live, print-attack, replay-attack].
        # The liveness score is therefore the probability of class 0.
        return float(max(0.0, min(1.0, probabilities[0])))

    def _active_liveness_passed(self, image: FaceImage) -> bool:
        passed, _ = self._active_liveness_result(image)
        return passed

    def _active_liveness_result(self, image: FaceImage) -> tuple[bool, str | None]:
        if not image.challenge_token or len(image.challenge_frames) < 3:
            logger.warning(
                "face_active_liveness result=failed reason=CHALLENGE_MISSING tokenPresent=%s frameCount=%d",
                bool(image.challenge_token), len(image.challenge_frames),
            )
            return False, "CHALLENGE_MISSING"
        settings = getattr(self, "_settings", None)
        if settings is None:
            logger.warning("face_active_liveness result=failed reason=CHALLENGE_INVALID settingsMissing=true")
            return False, "CHALLENGE_INVALID"
        secret = settings.challenge_secret or settings.internal_service_token
        payload = verify_challenge(image.challenge_token, secret.get_secret_value())
        if payload is None:
            logger.warning("face_active_liveness result=failed reason=CHALLENGE_INVALID frameCount=%d",
                           len(image.challenge_frames))
            return False, "CHALLENGE_INVALID"
        observations: list[float] = []
        for index, content in enumerate((image.content, *image.challenge_frames)):
            frame, faces = self._decode_and_detect(FaceImage(content, image.content_type, False))
            if len(faces) != 1:
                logger.warning(
                    "face_active_liveness result=failed reason=CHALLENGE_FACE_INVALID frameIndex=%d faceCount=%d",
                    index, len(faces),
                )
                return False, "CHALLENGE_FACE_INVALID"
            yaw = self._yaw_offset(faces[0])
            if yaw is None:
                logger.warning(
                    "face_active_liveness result=failed reason=CHALLENGE_FACE_INVALID frameIndex=%d landmarksInvalid=true",
                    index,
                )
                return False, "CHALLENGE_FACE_INVALID"
            observations.append(yaw)
        initial = observations[0]
        action = payload.get("action")
        formatted_observations = ",".join(f"{value:.3f}" for value in observations)
        if abs(initial) > 0.24:
            logger.warning(
                "face_active_liveness result=failed reason=CHALLENGE_START_NOT_FRONTAL action=%s yaws=%s",
                action, formatted_observations,
            )
            return False, "CHALLENGE_START_NOT_FRONTAL"
        if action == "TURN_LEFT":
            peak = min(observations[1:])
            directional_movement = initial - peak
        elif action == "TURN_RIGHT":
            peak = max(observations[1:])
            directional_movement = peak - initial
        else:
            logger.warning("face_active_liveness result=failed reason=CHALLENGE_INVALID action=%s", action)
            return False, "CHALLENGE_INVALID"
        if directional_movement < 0.10:
            logger.warning(
                "face_active_liveness result=failed reason=CHALLENGE_TURN_NOT_DETECTED action=%s movement=%.3f yaws=%s",
                action, directional_movement, formatted_observations,
            )
            return False, "CHALLENGE_TURN_NOT_DETECTED"
        logger.warning(
            "face_active_liveness result=passed action=%s movement=%.3f yaws=%s",
            action, directional_movement, formatted_observations,
        )
        return True, None

    def _yaw_offset(self, face: np.ndarray) -> float | None:
        right_eye = np.asarray(face[4:6], dtype=np.float32)
        left_eye = np.asarray(face[6:8], dtype=np.float32)
        nose = np.asarray(face[8:10], dtype=np.float32)
        eye_distance = float(np.linalg.norm(left_eye - right_eye))
        if eye_distance < 12 or float(face[14]) < 0.88:
            return None
        return float((nose[0] - ((left_eye[0] + right_eye[0]) / 2)) / eye_distance)
    def guidance(self, image: FaceImage) -> GuidanceResponse:
        frame, faces = self._decode_and_detect(image)
        count = len(faces)
        if count != 1:
            reason = "NO_FACE" if count == 0 else "MULTIPLE_FACES"
            return GuidanceResponse(
                detectedFaces=count,
                singleFace=False,
                faceInGuide=False,
                facingForward=False,
                landmarksVisible=False,
                lightingGood=False,
                sharpnessGood=False,
                failureReason=reason,
            )

        face = faces[0]
        frame_height, frame_width = frame.shape[:2]
        x, y, width, height = [float(value) for value in face[:4]]
        center_x = max(0.0, min(1.0, (x + width / 2) / frame_width))
        center_y = max(0.0, min(1.0, (y + height / 2) / frame_height))
        width_ratio = max(0.0, min(1.0, width / frame_width))
        height_ratio = max(0.0, min(1.0, height / frame_height))
        face_in_guide = (
            0.32 <= center_x <= 0.68
            and 0.30 <= center_y <= 0.70
            and 0.22 <= width_ratio <= 0.58
            and 0.35 <= height_ratio <= 0.85
        )
        facing_forward, landmarks_visible = self._landmark_checks(face)
        lighting_good, sharpness_good, quality_reason = self._quality_checks(frame, face)
        failure_reason = next((reason for passed, reason in (
            (face_in_guide, "FACE_OUTSIDE_GUIDE"),
            (facing_forward, "NOT_FACING_FORWARD"),
            (landmarks_visible, "LANDMARKS_NOT_VISIBLE"),
            (lighting_good, quality_reason or "LIGHTING_INVALID"),
            (sharpness_good, quality_reason or "BLURRY"),
        ) if not passed), None)
        return GuidanceResponse(
            detectedFaces=1,
            singleFace=True,
            faceInGuide=face_in_guide,
            facingForward=facing_forward,
            landmarksVisible=landmarks_visible,
            lightingGood=lighting_good,
            sharpnessGood=sharpness_good,
            centerX=center_x,
            centerY=center_y,
            faceWidthRatio=width_ratio,
            faceHeightRatio=height_ratio,
            failureReason=failure_reason,
        )
