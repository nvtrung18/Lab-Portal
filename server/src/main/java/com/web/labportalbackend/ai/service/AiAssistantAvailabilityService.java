package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantKey;

/**
 * Spring-owned admission gate for an authenticated assistant request.
 */
public interface AiAssistantAvailabilityService {

    AiAssistantProfile requireAvailable(AiAssistantKey assistantKey);

    AiAssistantAvailability requireAvailableForActor(AiAssistantKey assistantKey);
}
