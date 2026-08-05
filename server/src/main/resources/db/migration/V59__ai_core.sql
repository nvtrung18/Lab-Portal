CREATE TABLE ai_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    assistant_key VARCHAR(50) NOT NULL,
    module_context VARCHAR(100) NOT NULL,
    lab_id BIGINT NULL,
    project_id BIGINT NULL,
    group_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ai_conversation_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_conversation_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    CONSTRAINT fk_ai_conversation_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_ai_conversation_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    CONSTRAINT chk_ai_conversation_assistant_key CHECK (assistant_key IN ('LAB_ASSISTANT', 'RESEARCH_ASSISTANT')),

    INDEX idx_ai_conversation_user_assistant_module_updated (user_id, assistant_key, module_context, updated_at, id),
    INDEX idx_ai_conversation_lab_updated (lab_id, updated_at, id),
    INDEX idx_ai_conversation_project_updated (project_id, updated_at, id),
    INDEX idx_ai_conversation_group_updated (group_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_assistant_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assistant_key VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    system_prompt_key VARCHAR(100) NOT NULL,
    max_requests_per_day INT NOT NULL,
    max_context_tokens INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_ai_assistant_config_assistant_key UNIQUE (assistant_key),
    CONSTRAINT chk_ai_assistant_config_assistant_key CHECK (assistant_key IN ('LAB_ASSISTANT', 'RESEARCH_ASSISTANT')),
    CONSTRAINT chk_ai_assistant_config_requests CHECK (max_requests_per_day > 0),
    CONSTRAINT chk_ai_assistant_config_context CHECK (max_context_tokens > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id),
    CONSTRAINT chk_ai_message_role CHECK (`role` IN ('USER', 'ASSISTANT', 'SYSTEM')),

    INDEX idx_ai_message_conversation_created (conversation_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_action_suggestion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requested_by BIGINT NOT NULL,
    assistant_key VARCHAR(50) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    target_module VARCHAR(100) NOT NULL,
    target_id BIGINT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    executed_by BIGINT NULL,
    rejected_reason TEXT NULL,
    executed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ai_action_suggestion_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT fk_ai_action_suggestion_executed_by FOREIGN KEY (executed_by) REFERENCES users (id),
    CONSTRAINT chk_ai_action_suggestion_assistant_key CHECK (assistant_key IN ('LAB_ASSISTANT', 'RESEARCH_ASSISTANT')),
    CONSTRAINT chk_ai_action_suggestion_status CHECK (status IN ('PENDING', 'EDITED', 'REJECTED', 'EXECUTED')),

    INDEX idx_ai_action_suggestion_requested_status_created (requested_by, status, created_at, id),
    INDEX idx_ai_action_suggestion_assistant_status_created (assistant_key, status, created_at, id),
    INDEX idx_ai_action_suggestion_target (target_module, target_id),
    INDEX idx_ai_action_suggestion_executed_created (executed_by, executed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_usage_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    assistant_key VARCHAR(50) NOT NULL,
    `role` VARCHAR(50) NOT NULL,
    `module` VARCHAR(100) NOT NULL,
    lab_id BIGINT NULL,
    project_id BIGINT NULL,
    group_id BIGINT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ai_usage_log_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_usage_log_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    CONSTRAINT fk_ai_usage_log_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_ai_usage_log_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    CONSTRAINT chk_ai_usage_log_assistant_key CHECK (assistant_key IN ('LAB_ASSISTANT', 'RESEARCH_ASSISTANT')),
    CONSTRAINT chk_ai_usage_log_prompt_tokens CHECK (prompt_tokens >= 0),
    CONSTRAINT chk_ai_usage_log_completion_tokens CHECK (completion_tokens >= 0),

    INDEX idx_ai_usage_log_user_created (user_id, created_at, id),
    INDEX idx_ai_usage_log_assistant_role_module_created (assistant_key, `role`, `module`, created_at, id),
    INDEX idx_ai_usage_log_lab_created (lab_id, created_at, id),
    INDEX idx_ai_usage_log_project_created (project_id, created_at, id),
    INDEX idx_ai_usage_log_group_created (group_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_quota_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assistant_key VARCHAR(50) NOT NULL,
    `role` VARCHAR(50) NOT NULL,
    `module` VARCHAR(100) NOT NULL,
    max_requests_per_day INT NOT NULL,
    max_context_tokens INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_ai_quota_config_assistant_role_module UNIQUE (assistant_key, `role`, `module`),
    CONSTRAINT chk_ai_quota_config_assistant_key CHECK (assistant_key IN ('LAB_ASSISTANT', 'RESEARCH_ASSISTANT')),
    CONSTRAINT chk_ai_quota_config_requests CHECK (max_requests_per_day > 0),
    CONSTRAINT chk_ai_quota_config_context CHECK (max_context_tokens > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE research_task_proposal
    ADD CONSTRAINT fk_task_proposal_ai_action_suggestion
        FOREIGN KEY (ai_action_suggestion_id) REFERENCES ai_action_suggestion (id);
