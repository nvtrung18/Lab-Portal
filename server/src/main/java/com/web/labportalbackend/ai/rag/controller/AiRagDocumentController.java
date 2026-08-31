package com.web.labportalbackend.ai.rag.controller;

import com.web.labportalbackend.ai.rag.dto.request.AiRagDocumentIngestRequest;
import com.web.labportalbackend.ai.rag.dto.response.AiRagDocumentResponse;
import com.web.labportalbackend.ai.rag.service.AiRagIngestionService;
import com.web.labportalbackend.common.dto.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/rag/documents")
@RequiredArgsConstructor
public class AiRagDocumentController {

    private final AiRagIngestionService ingestionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')")
    public ResponseEntity<Response<AiRagDocumentResponse>> ingest(
            @Valid @RequestBody AiRagDocumentIngestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("RAG document ingested successfully", ingestionService.ingest(request)));
    }

    @PutMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')")
    public ResponseEntity<Response<AiRagDocumentResponse>> reindex(
            @PathVariable Long documentId,
            @Valid @RequestBody AiRagDocumentIngestRequest request) {
        return ResponseEntity.ok(Response.ok("RAG document reindexed successfully",
                ingestionService.reindex(documentId, request)));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')")
    public ResponseEntity<Response<Void>> revoke(@PathVariable Long documentId) {
        ingestionService.revoke(documentId);
        return ResponseEntity.ok(Response.ok("RAG document revoked successfully"));
    }
}
