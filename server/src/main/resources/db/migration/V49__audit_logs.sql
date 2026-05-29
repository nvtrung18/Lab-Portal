CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NULL,
    actor_name VARCHAR(150) NULL,
    actor_role VARCHAR(100) NULL,
    action VARCHAR(100) NOT NULL,
    module VARCHAR(100) NOT NULL,
    target_type VARCHAR(100) NULL,
    target_id BIGINT NULL,
    description TEXT NOT NULL,
    metadata_json JSON NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_module ON audit_logs (module);
CREATE INDEX idx_audit_logs_target ON audit_logs (target_type, target_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
