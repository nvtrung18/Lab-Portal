from __future__ import annotations

from fastapi.testclient import TestClient
from pathlib import Path

from app.config import Settings
from app.main import app


HEADERS = {
    "X-Internal-Service-Token": "test-only-face-service-token",
    "X-Request-Id": "face-health-test",
}


def test_service_starts_without_business_database_configuration(monkeypatch) -> None:
    for name in ("DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "DATABASE_URL"):
        monkeypatch.delenv(name, raising=False)

    settings = Settings.from_env()

    assert settings.service_name == "face-service"
    assert not any("database" in name.lower() or name.lower().startswith("db_") for name in type(settings).model_fields)
    requirements = (Path(__file__).parents[1] / "requirements.txt").read_text(encoding="utf-8").lower()
    assert "mysql" not in requirements
    assert "sqlalchemy" not in requirements


def test_health_requires_internal_token_and_returns_liveness() -> None:
    client = TestClient(app)

    denied = client.get("/health")
    health = client.get("/health", headers=HEADERS)

    assert denied.status_code == 401
    assert denied.json()["errorCode"] == "FACE_INTERNAL_AUTH_FAILED"
    assert health.status_code == 200
    assert health.json() == {"status": "UP", "service": "face-service"}
    assert health.headers["X-Request-Id"] == "face-health-test"


def test_forwarded_user_authorization_is_rejected() -> None:
    response = TestClient(app).get("/health", headers={**HEADERS, "Authorization": "Bearer user-token"})

    assert response.status_code == 401
    assert response.json()["errorCode"] == "FACE_INTERNAL_AUTH_FAILED"
