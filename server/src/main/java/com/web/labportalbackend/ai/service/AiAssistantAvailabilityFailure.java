package com.web.labportalbackend.ai.service;

public enum AiAssistantAvailabilityFailure {
    UNAUTHENTICATED,
    ACTOR_UNAVAILABLE,
    ASSISTANT_UNAVAILABLE,
    ROLE_NOT_ALLOWED,
    CONFIGURATION_UNAVAILABLE,
    QUOTA_EXCEEDED
}
