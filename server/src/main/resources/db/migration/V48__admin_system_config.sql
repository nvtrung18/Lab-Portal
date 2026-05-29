CREATE TABLE system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    config_value_json JSON NOT NULL,
    updated_by BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_system_configs_key UNIQUE (config_key),
    CONSTRAINT fk_system_configs_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE TABLE system_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    module VARCHAR(100) NOT NULL,
    description TEXT NULL,
    actor_id BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_system_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_system_configs_key_deleted ON system_configs (config_key, deleted);
CREATE INDEX idx_system_audit_logs_module_action ON system_audit_logs (module, action);
CREATE INDEX idx_system_audit_logs_actor ON system_audit_logs (actor_id);
