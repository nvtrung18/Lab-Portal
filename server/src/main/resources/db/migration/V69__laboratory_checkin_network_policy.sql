ALTER TABLE laboratories
    ADD COLUMN checkin_latitude DECIMAL(10, 7) NULL,
    ADD COLUMN checkin_longitude DECIMAL(10, 7) NULL,
    ADD COLUMN checkin_radius_meters INT NULL,
    ADD COLUMN checkin_max_accuracy_meters INT NULL,
    ADD COLUMN checkin_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE laboratory_allowed_networks (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    laboratory_id   BIGINT       NOT NULL,
    network_value   VARCHAR(100) NOT NULL,
    registered_by   BIGINT       NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_lab_allowed_network_lab FOREIGN KEY (laboratory_id) REFERENCES laboratories (id),
    CONSTRAINT fk_lab_allowed_network_user FOREIGN KEY (registered_by) REFERENCES users (id),
    CONSTRAINT uk_lab_allowed_network_value UNIQUE (laboratory_id, network_value, deleted),
    INDEX idx_lab_allowed_network_lab_active (laboratory_id, active, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
