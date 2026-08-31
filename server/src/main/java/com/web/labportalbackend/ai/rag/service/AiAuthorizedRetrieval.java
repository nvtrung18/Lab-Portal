package com.web.labportalbackend.ai.rag.service;

import java.util.List;

public record AiAuthorizedRetrieval(String namespace, List<Chunk> chunks) {

    public AiAuthorizedRetrieval {
        if (namespace == null || namespace.isBlank() || chunks == null || chunks.size() > 5) {
            throw new IllegalArgumentException("Authorized RAG retrieval is invalid");
        }
        chunks = List.copyOf(chunks);
    }

    public static AiAuthorizedRetrieval empty(String namespace) {
        return new AiAuthorizedRetrieval(namespace, List.of());
    }

    public record Chunk(
            Long documentId,
            String resourceId,
            int version,
            int chunkIndex,
            Integer pageNumber,
            String sourceType,
            String content,
            boolean trusted) {

        public Chunk {
            if (documentId == null || documentId <= 0 || resourceId == null || resourceId.isBlank()
                    || version <= 0 || chunkIndex < 0 || (pageNumber != null && pageNumber <= 0)
                    || sourceType == null || sourceType.isBlank() || content == null || content.isBlank()
                    || trusted) {
                throw new IllegalArgumentException("Authorized RAG chunk is invalid");
            }
        }
    }
}
