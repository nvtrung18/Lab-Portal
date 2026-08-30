package com.web.labportalbackend.ai.rag.service;

import com.web.labportalbackend.ai.rag.dto.request.AiRagDocumentIngestRequest;
import com.web.labportalbackend.ai.rag.dto.response.AiRagDocumentResponse;

public interface AiRagIngestionService {

    AiRagDocumentResponse ingest(AiRagDocumentIngestRequest request);
}
