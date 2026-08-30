CREATE TABLE ai_rag_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    namespace VARCHAR(64) NOT NULL,
    domain VARCHAR(20) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    document_version INT NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    owner_id BIGINT NOT NULL,
    lab_id BIGINT NULL,
    project_id BIGINT NULL,
    group_id BIGINT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ai_rag_document_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_ai_rag_document_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    CONSTRAINT fk_ai_rag_document_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_ai_rag_document_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    CONSTRAINT uk_ai_rag_document_resource_version UNIQUE (namespace, resource_id, document_version),
    CONSTRAINT chk_ai_rag_document_domain CHECK (domain IN ('ADMIN', 'LAB', 'RESEARCH')),
    CONSTRAINT chk_ai_rag_document_namespace CHECK (
        namespace IN ('admin-knowledge', 'lab-knowledge', 'research-knowledge')
    ),
    CONSTRAINT chk_ai_rag_document_visibility CHECK (
        visibility IN ('OWNER', 'LAB_MEMBERS', 'PROJECT_MEMBERS', 'GROUP_MEMBERS', 'ADMIN_ONLY')
    ),
    CONSTRAINT chk_ai_rag_document_version CHECK (document_version > 0),

    INDEX idx_ai_rag_document_namespace_active (namespace, active, deleted),
    INDEX idx_ai_rag_document_scope (namespace, lab_id, project_id, group_id, active, deleted),
    INDEX idx_ai_rag_document_owner (owner_id, active, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_rag_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    namespace VARCHAR(64) NOT NULL,
    domain VARCHAR(20) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    document_version INT NOT NULL,
    chunk_index INT NOT NULL,
    page_number INT NULL,
    source_type VARCHAR(64) NOT NULL,
    visibility VARCHAR(32) NOT NULL,
    owner_id BIGINT NOT NULL,
    lab_id BIGINT NULL,
    project_id BIGINT NULL,
    group_id BIGINT NULL,
    content_hash CHAR(64) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ai_rag_chunk_document FOREIGN KEY (document_id) REFERENCES ai_rag_document (id),
    CONSTRAINT fk_ai_rag_chunk_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_ai_rag_chunk_lab FOREIGN KEY (lab_id) REFERENCES laboratories (id),
    CONSTRAINT fk_ai_rag_chunk_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_ai_rag_chunk_group FOREIGN KEY (group_id) REFERENCES research_groups (id),
    CONSTRAINT uk_ai_rag_chunk_position UNIQUE (document_id, chunk_index),
    CONSTRAINT chk_ai_rag_chunk_version CHECK (document_version > 0),
    CONSTRAINT chk_ai_rag_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT chk_ai_rag_chunk_page CHECK (page_number IS NULL OR page_number > 0),

    INDEX idx_ai_rag_chunk_namespace_active (namespace, active, deleted, document_id, chunk_index),
    INDEX idx_ai_rag_chunk_scope (namespace, lab_id, project_id, group_id, active, deleted),
    INDEX idx_ai_rag_chunk_owner (owner_id, active, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
