package com.web.labportalbackend.ai.service;

/**
 * Trusted internal anchor for research context. The authenticated server identity,
 * not this request, determines the user and role scope.
 */
public record AiResearchContextRequest(Long projectId, Long groupId) {

    public AiResearchContextRequest {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("projectId must be positive");
        }
        if (groupId != null && groupId <= 0) {
            throw new IllegalArgumentException("groupId must be positive when provided");
        }
    }
}
