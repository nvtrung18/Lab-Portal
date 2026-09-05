package com.web.labportalbackend.ai.service;

public record AiResearchToolCandidateResource(Long id, String label, Long projectId) {
    public AiResearchToolCandidateResource {
        if (id == null || id <= 0 || label == null || label.isBlank()
                || (projectId != null && projectId <= 0)) {
            throw new IllegalArgumentException("Research candidate resource is invalid");
        }
    }
}
