INSERT INTO face_security_config (
    config_key,
    face_enabled,
    confidence_threshold,
    liveness_threshold,
    liveness_required,
    qr_when_face_disabled,
    qr_when_service_unavailable,
    qr_when_profile_unavailable,
    manual_override_enabled,
    active,
    deleted
)
SELECT
    'default',
    TRUE,
    0.8500,
    0.7000,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    TRUE,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM face_security_config
);
