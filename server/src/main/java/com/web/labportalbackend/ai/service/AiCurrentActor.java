package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import java.util.Set;

public record AiCurrentActor(Long id, AiAssistantSystemRole role, Set<AiAssistantSystemRole> systemRoles) {
    public AiCurrentActor(Long id, AiAssistantSystemRole role) {
        this(id, role, role == null ? Set.of() : Set.of(role));
    }

    public AiCurrentActor {
        if (id == null || id <= 0 || role == null || systemRoles == null || !systemRoles.contains(role)) {
            throw new IllegalArgumentException("Current AI actor is invalid");
        }
        systemRoles = Set.copyOf(systemRoles);
    }
}
