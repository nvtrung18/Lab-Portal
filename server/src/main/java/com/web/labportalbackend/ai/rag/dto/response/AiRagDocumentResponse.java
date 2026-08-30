package com.web.labportalbackend.ai.rag.dto.response;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.rag.enums.AiRagVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

public record AiRagDocumentResponse(
        @Schema(description = "Persisted RAG document identifier") Long documentId,
        @Schema(description = "Isolated domain knowledge namespace") String namespace,
        @Schema(description = "Assistant domain that owns the document") AiAssistantDomain domain,
        @Schema(description = "Stable source-resource identifier") String resourceId,
        @Schema(description = "Persisted source document version") int version,
        @Schema(description = "Source classification used by citations") String sourceType,
        @Schema(description = "Spring-enforced document visibility") AiRagVisibility visibility,
        @Schema(description = "Number of persisted document chunks") int chunkCount) {
}
