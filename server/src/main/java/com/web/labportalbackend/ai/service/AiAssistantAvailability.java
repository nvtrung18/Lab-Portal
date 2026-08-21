package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;

/**
 * Server-derived admission result. Actor identity and role never bind from the public request.
 */
public record AiAssistantAvailability(
        AiAssistantProfile profile,
        Long actorId,
        AiAssistantSystemRole selectedSystemRole) {

    public AiAssistantAvailability {
        if (profile == null || actorId == null || actorId <= 0 || selectedSystemRole == null
                || !profile.allowedSystemRoles().contains(selectedSystemRole)) {
            throw new IllegalArgumentException("assistant availability result is invalid");
        }
    }
}
