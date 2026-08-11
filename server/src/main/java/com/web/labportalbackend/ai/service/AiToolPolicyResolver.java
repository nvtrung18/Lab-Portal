package com.web.labportalbackend.ai.service;

public interface AiToolPolicyResolver {
    AiAuthorizedToolPolicy resolve(AiCapabilityDecision decision, AiToolRequest request);

    AiAuthorizedToolPolicy resolve(AiCapabilityDecision decision);
}
