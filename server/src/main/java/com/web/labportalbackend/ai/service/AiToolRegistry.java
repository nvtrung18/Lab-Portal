package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiToolId;

public interface AiToolRegistry {
    AiToolDefinition get(String externalToolId);

    AiToolDefinition get(AiToolId toolId);

    AiToolDefinition get(AiCapability capability);
}
