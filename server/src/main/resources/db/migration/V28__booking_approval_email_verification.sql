-- Day 26+ booking approval flow and email verification support

CREATE TABLE IF NOT EXISTS verification_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    type VARCHAR(40) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_verification_email_type (email, type),
    INDEX idx_verification_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE bookings MODIFY status VARCHAR(30) NOT NULL;
ALTER TABLE users MODIFY status VARCHAR(30) NOT NULL;
