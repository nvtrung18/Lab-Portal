ALTER TABLE ai_conversation
    DROP CHECK chk_ai_conversation_assistant_key,
    ADD CONSTRAINT chk_ai_conversation_assistant_key
        CHECK (assistant_key IN ('ADMIN_ASSISTANT', 'LAB_ASSISTANT', 'RESEARCH_ASSISTANT'));

ALTER TABLE ai_assistant_config
    DROP CHECK chk_ai_assistant_config_assistant_key,
    ADD CONSTRAINT chk_ai_assistant_config_assistant_key
        CHECK (assistant_key IN ('ADMIN_ASSISTANT', 'LAB_ASSISTANT', 'RESEARCH_ASSISTANT'));

ALTER TABLE ai_action_suggestion
    DROP CHECK chk_ai_action_suggestion_assistant_key,
    ADD COLUMN model_version VARCHAR(100) NULL,
    ADD COLUMN adapter_version VARCHAR(255) NULL,
    ADD COLUMN prompt_version VARCHAR(100) NULL,
    ADD COLUMN resource_type VARCHAR(50) NULL,
    ADD COLUMN resource_id BIGINT NULL,
    ADD COLUMN action_risk_level VARCHAR(30) NULL,
    ADD COLUMN confirmation_status VARCHAR(30) NULL,
    ADD COLUMN execution_status VARCHAR(30) NULL,
    ADD CONSTRAINT chk_ai_action_suggestion_assistant_key
        CHECK (assistant_key IN ('ADMIN_ASSISTANT', 'LAB_ASSISTANT', 'RESEARCH_ASSISTANT')),
    ADD CONSTRAINT chk_ai_action_suggestion_resource_type
        CHECK (resource_type IN ('SYSTEM', 'AUDIT_LOG', 'USER_ACCOUNT', 'SYSTEM_CONFIG', 'LABORATORY', 'TIME_SLOT',
            'BOOKING', 'PROJECT', 'GROUP', 'TASK', 'REPORT')),
    ADD CONSTRAINT chk_ai_action_suggestion_risk_level
        CHECK (action_risk_level IN ('READ_ONLY', 'DRAFT_ONLY', 'CONFIRM_REQUIRED', 'APPROVAL_REQUIRED', 'PROHIBITED')),
    ADD CONSTRAINT chk_ai_action_suggestion_confirmation_status
        CHECK (confirmation_status IN ('NOT_REQUIRED', 'PENDING', 'CONFIRMED', 'REJECTED')),
    ADD CONSTRAINT chk_ai_action_suggestion_execution_status
        CHECK (execution_status IN ('NOT_REQUESTED', 'PENDING', 'EXECUTED', 'FAILED'));

ALTER TABLE ai_usage_log
    DROP CHECK chk_ai_usage_log_assistant_key,
    ADD CONSTRAINT chk_ai_usage_log_assistant_key
        CHECK (assistant_key IN ('ADMIN_ASSISTANT', 'LAB_ASSISTANT', 'RESEARCH_ASSISTANT'));

ALTER TABLE ai_quota_config
    DROP CHECK chk_ai_quota_config_assistant_key,
    ADD CONSTRAINT chk_ai_quota_config_assistant_key
        CHECK (assistant_key IN ('ADMIN_ASSISTANT', 'LAB_ASSISTANT', 'RESEARCH_ASSISTANT'));
