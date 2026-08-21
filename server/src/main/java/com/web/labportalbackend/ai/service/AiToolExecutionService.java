package com.web.labportalbackend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;

/** Internal Spring authority boundary for untrusted model-produced tool requests. */
public interface AiToolExecutionService {
    AiToolExecutionResult execute(JsonNode untrustedRequest, String requestId);
}
