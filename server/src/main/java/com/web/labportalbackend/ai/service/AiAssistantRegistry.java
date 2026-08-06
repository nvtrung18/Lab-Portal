package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;

public interface AiAssistantRegistry {

    AiAssistantProfile getProfile(AiAssistantKey assistantKey);

    AiAssistantProfile getAvailableProfile(AiAssistantKey assistantKey, AiAssistantSystemRole systemRole);
}
