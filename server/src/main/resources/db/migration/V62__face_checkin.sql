CREATE TABLE face_consent_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    changed_by BIGINT NOT NULL,
    consent_status VARCHAR(30) NOT NULL,
    reason TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_face_consent_log_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_face_consent_log_changed_by FOREIGN KEY (changed_by) REFERENCES users (id),
    CONSTRAINT chk_face_consent_log_status CHECK (
        consent_status IN ('GRANTED', 'WITHDRAWN', 'DELETE_REQUESTED', 'DELETED')
    ),

    INDEX idx_face_consent_log_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE face_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    encrypted_embedding TEXT NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    profile_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_face_profile_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_face_profile_user UNIQUE (user_id),
    CONSTRAINT chk_face_profile_status CHECK (profile_status IN ('ACTIVE', 'DISABLED', 'DELETED')),

    INDEX idx_face_profile_user_status (user_id, profile_status, active, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE face_security_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(50) NOT NULL,
    face_enabled BOOLEAN NOT NULL,
    confidence_threshold DECIMAL(5,4) NOT NULL,
    liveness_threshold DECIMAL(5,4) NOT NULL,
    liveness_required BOOLEAN NOT NULL,
    qr_when_face_disabled BOOLEAN NOT NULL,
    qr_when_service_unavailable BOOLEAN NOT NULL,
    qr_when_profile_unavailable BOOLEAN NOT NULL,
    manual_override_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_face_security_config_key UNIQUE (config_key),
    CONSTRAINT chk_face_security_config_confidence CHECK (
        confidence_threshold >= 0 AND confidence_threshold <= 1
    ),
    CONSTRAINT chk_face_security_config_liveness CHECK (
        liveness_threshold >= 0 AND liveness_threshold <= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE face_checkin_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    lab_id BIGINT NOT NULL,
    checked_in_by BIGINT NOT NULL,
    checkin_method VARCHAR(30) NOT NULL,
    result VARCHAR(20) NOT NULL,
    confidence_score DECIMAL(5,4) NULL,
    liveness_score DECIMAL(5,4) NULL,
    failure_reason VARCHAR(50) NULL,
    fallback_reason TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_face_checkin_log_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_face_checkin_log_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_face_checkin_log_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    CONSTRAINT fk_face_checkin_log_checked_in_by FOREIGN KEY (checked_in_by) REFERENCES users (id),
    CONSTRAINT chk_face_checkin_log_method CHECK (
        checkin_method IN ('FACE', 'QR_FALLBACK', 'MANUAL')
    ),
    CONSTRAINT chk_face_checkin_log_result CHECK (result IN ('SUCCESS', 'FAILED', 'DENIED')),
    CONSTRAINT chk_face_checkin_log_confidence CHECK (
        confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1)
    ),
    CONSTRAINT chk_face_checkin_log_liveness CHECK (
        liveness_score IS NULL OR (liveness_score >= 0 AND liveness_score <= 1)
    ),
    CONSTRAINT chk_face_checkin_log_fallback_reason CHECK (
        checkin_method = 'FACE' OR (fallback_reason IS NOT NULL AND CHAR_LENGTH(TRIM(fallback_reason)) > 0)
    ),

    INDEX idx_face_checkin_log_booking_created (booking_id, created_at, id),
    INDEX idx_face_checkin_log_user_created (user_id, created_at, id),
    INDEX idx_face_checkin_log_lab_created (lab_id, created_at, id),
    INDEX idx_face_checkin_log_method_result_created (checkin_method, result, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
