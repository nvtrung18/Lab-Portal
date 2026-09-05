package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;

public record AiCurrentActor(Long id, AiAssistantSystemRole role) {
    public AiCurrentActor {
        if (id == null || id <= 0 || role == null) {
            throw new IllegalArgumentException("Current AI actor is invalid");
        }
    }
}
