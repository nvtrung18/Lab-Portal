package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiToolArgument;
import com.web.labportalbackend.ai.enums.AiToolId;
import java.util.Set;

/** Internal metadata request only; it contains no executable target or model-supplied payload. */
public record AiToolRequest(AiToolId toolId, String schemaVersion, Set<AiToolArgument> argumentNames) {
    public AiToolRequest {
        argumentNames = argumentNames == null ? Set.of() : Set.copyOf(argumentNames);
    }
}
