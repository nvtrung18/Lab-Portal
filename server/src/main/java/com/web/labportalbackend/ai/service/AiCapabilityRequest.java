package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;

public record AiCapabilityRequest(
        AiAssistantKey assistantKey,
        Long actorId,
        AiCapability capability,
        ResourceReference resource,
        ResourceReference parentResource,
        AiRequestedAction requestedAction) {

    public record ResourceReference(AiResourceType type, Long id) {
    }
}
