package com.web.labportalbackend.ai.rag.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiRagCitationResponse(
        @Schema(description = "Stable identifier of the authorized source document") Long documentId,
        @Schema(description = "Domain resource identifier attached to the source document") String resourceId,
        @Schema(description = "Version of the source document used for this answer") int version,
        @Schema(description = "Zero-based chunk position within the source document") int chunkIndex,
        @Schema(description = "One-based source page number when the source provides page boundaries") Integer pageNumber,
        @Schema(description = "Source classification recorded during document ingestion") String sourceType) {
}
