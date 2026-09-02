ALTER TABLE ai_rag_document
    MODIFY content_hash VARCHAR(64) NOT NULL;

ALTER TABLE ai_rag_chunk
    MODIFY content_hash VARCHAR(64) NOT NULL;
