package com.web.labportalbackend.ai.service;

public interface AiCapabilityResolver {

    AiCapabilityDecision resolve(AiCapabilityRequest request);

    AiCapabilityDecision requireAllowed(AiCapabilityRequest request);
}
