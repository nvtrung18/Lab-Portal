package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.context.AiDomainContext;

/** Bounded internal result; the closed domain-context hierarchy contains no persistence entities. */
public record AiToolExecutionResult(String requestId, String toolId, AiDomainContext data) {

    public AiToolExecutionResult {
        if (toolId == null || toolId.isBlank() || data == null) {
            throw new IllegalArgumentException("tool execution result is incomplete");
        }
    }
}
