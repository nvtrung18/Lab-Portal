from app.liveness_challenge import create_challenge, create_observation_session, verify_challenge
from app.config import Settings
from app.opencv_processor import OpenCvFaceProcessor
from app.processor import FaceImage
import numpy as np
from unittest.mock import patch


def test_challenge_is_signed_and_verifiable() -> None:
    challenge = create_challenge("test-secret")

    payload = verify_challenge(challenge.token, "test-secret")

    assert payload is not None
    assert payload["action"] == challenge.action
    assert payload["expiresAt"] == challenge.expires_at


def test_challenge_rejects_tampering_or_wrong_secret() -> None:
    challenge = create_challenge("test-secret")

    assert verify_challenge(challenge.token + "x", "test-secret") is None
    assert verify_challenge(challenge.token, "wrong-secret") is None


def test_passive_observation_session_is_signed_and_distinct_from_turn_challenge() -> None:
    session = create_observation_session("test-secret")

    payload = verify_challenge(session.token, "test-secret")

    assert session.action == "OBSERVE"
    assert payload is not None
    assert payload["action"] == "OBSERVE"
    assert payload["issuedAt"] <= payload["expiresAt"]


def test_passive_observation_accepts_stable_frontal_live_camera_sequence() -> None:
    session = create_observation_session("test-secret")
    processor = object.__new__(OpenCvFaceProcessor)
    processor._settings = Settings(
        internal_service_token="test-secret",
        challenge_secret="test-secret",
    )
    face = np.ones((1, 15), dtype=np.float32)
    face[0][14] = 0.99
    frames = [np.full((100, 100, 3), 80 + index * 2, dtype=np.uint8) for index in range(5)]
    processor._decode_and_detect = lambda _image: (frames.pop(0), face)
    processor._quality = lambda _frame, _face: (True, None)
    yaws = [0.0, 0.01, -0.01, 0.015, -0.005]
    processor._yaw_offset = lambda _face: yaws.pop(0)

    class Recognizer:
        def alignCrop(self, frame, _face):
            return frame

    processor._recognizer = Recognizer()
    image = FaceImage(b"1", "image/jpeg", True, session.token, (b"2", b"3", b"4", b"5"))

    with patch("app.opencv_processor.time.time", return_value=session.expires_at - 177):
        passed, reason = processor._passive_liveness_result(image)

    assert passed is True
    assert reason is None


def test_passive_observation_rejects_identical_frames() -> None:
    session = create_observation_session("test-secret")
    processor = object.__new__(OpenCvFaceProcessor)
    processor._settings = Settings(
        internal_service_token="test-secret",
        challenge_secret="test-secret",
    )
    face = np.ones((1, 15), dtype=np.float32)
    face[0][14] = 0.99
    frame = np.full((100, 100, 3), 80, dtype=np.uint8)
    processor._decode_and_detect = lambda _image: (frame.copy(), face)
    processor._quality = lambda _frame, _face: (True, None)
    processor._yaw_offset = lambda _face: 0.0

    class Recognizer:
        def alignCrop(self, current, _face):
            return current

    processor._recognizer = Recognizer()
    image = FaceImage(b"1", "image/jpeg", True, session.token, (b"2", b"3", b"4", b"5"))

    with patch("app.opencv_processor.time.time", return_value=session.expires_at - 177):
        passed, reason = processor._passive_liveness_result(image)

    assert passed is False
    assert reason == "OBSERVATION_NOT_LIVE"


def test_active_liveness_requires_front_frame_and_requested_turn() -> None:
    challenge = create_challenge("test-secret")
    processor = object.__new__(OpenCvFaceProcessor)
    processor._settings = Settings(
        internal_service_token="test-secret",
        challenge_secret="test-secret",
    )
    processor._decode_and_detect = lambda _image: (np.zeros((10, 10, 3), dtype=np.uint8), np.ones((1, 15)))
    yaws = [0.0, -0.08, -0.22, -0.36] if challenge.action == "TURN_LEFT" else [0.0, 0.08, 0.22, 0.36]
    processor._yaw_offset = lambda _face: yaws.pop(0)

    image = FaceImage(b"initial", "image/jpeg", True, challenge.token, (b"2", b"3", b"4"))

    assert processor._active_liveness_passed(image) is True


def test_active_liveness_uses_peak_turn_instead_of_only_last_frame() -> None:
    challenge = create_challenge("test-secret")
    processor = object.__new__(OpenCvFaceProcessor)
    processor._settings = Settings(
        internal_service_token="test-secret",
        challenge_secret="test-secret",
    )
    processor._decode_and_detect = lambda _image: (np.zeros((10, 10, 3), dtype=np.uint8), np.ones((1, 15)))
    yaws = [0.02, -0.04, -0.12, -0.03] if challenge.action == "TURN_LEFT" else [0.02, 0.07, 0.14, 0.04]
    processor._yaw_offset = lambda _face: yaws.pop(0)

    image = FaceImage(b"initial", "image/jpeg", True, challenge.token, (b"2", b"3", b"4"))

    assert processor._active_liveness_passed(image) is True


def test_active_liveness_rejects_small_head_jitter() -> None:
    challenge = create_challenge("test-secret")
    processor = object.__new__(OpenCvFaceProcessor)
    processor._settings = Settings(
        internal_service_token="test-secret",
        challenge_secret="test-secret",
    )
    processor._decode_and_detect = lambda _image: (np.zeros((10, 10, 3), dtype=np.uint8), np.ones((1, 15)))
    yaws = [0.01, 0.03, -0.02, 0.04]
    processor._yaw_offset = lambda _face: yaws.pop(0)

    image = FaceImage(b"initial", "image/jpeg", True, challenge.token, (b"2", b"3", b"4"))

    passed, reason = processor._active_liveness_result(image)

    assert passed is False
    assert reason == "CHALLENGE_TURN_NOT_DETECTED"


def test_match_accepts_signed_passive_observation_when_model_is_miscalibrated() -> None:
    class Recognizer:
        def alignCrop(self, _frame, _face):
            return np.zeros((8, 8, 3), dtype=np.uint8)

        def feature(self, _aligned):
            return np.asarray([[0.1, 0.2]], dtype=np.float32)

        def match(self, _reference, _candidate, _metric):
            return 0.93

    processor = object.__new__(OpenCvFaceProcessor)
    face = np.ones((1, 15), dtype=np.float32)
    face[0][14] = 0.99
    processor._decode_and_detect = lambda _image: (np.zeros((100, 100, 3), dtype=np.uint8), face)
    processor._quality = lambda _frame, _face: (True, None)
    processor._liveness_score = lambda _frame, _face: 0.0003
    processor._passive_liveness_result = lambda _image: (True, None)
    processor._recognizer = Recognizer()

    result = processor.match(
        FaceImage(b"initial", "image/jpeg", True, "signed", (b"2", b"3", b"4")),
        [[0.1, 0.2]],
        0.85,
        0.7,
    )

    assert result.result == "MATCH"
    assert result.passive_liveness_passed is True
