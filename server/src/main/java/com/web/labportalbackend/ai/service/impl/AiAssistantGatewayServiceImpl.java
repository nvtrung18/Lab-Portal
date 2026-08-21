package com.web.labportalbackend.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.ai.client.AiChatResponse;
import com.web.labportalbackend.ai.client.AiGatewayClient;
import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiContextBuildRequest;
import com.web.labportalbackend.ai.context.AiContextFacade;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.context.AiPythonAuthorizedContext;
import com.web.labportalbackend.ai.dto.request.AiAssistantChatRequest;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.service.AiAssistantAvailability;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityService;
import com.web.labportalbackend.ai.service.AiAssistantGatewayService;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantGatewayServiceImpl implements AiAssistantGatewayService {

    private final AiAssistantAvailabilityService availabilityService;
    private final AiContextFacade contextFacade;
    private final AiGatewayClient gatewayClient;
    private final ObjectMapper objectMapper;

    public AiAssistantGatewayServiceImpl(AiAssistantAvailabilityService availabilityService,
                                         AiContextFacade contextFacade,
                                         AiGatewayClient gatewayClient,
                                         ObjectMapper objectMapper) {
        this.availabilityService = availabilityService;
        this.contextFacade = contextFacade;
        this.gatewayClient = gatewayClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAssistantChatResponse chat(AiAssistantKey assistantKey,
                                        AiAssistantChatRequest request,
                                        String requestId) {
        String normalizedRequestId = AiGatewayRequest.normalizeRequestId(requestId);
        if (request == null || request.getCapability() == null || !request.hasValidResourceSelection()) {
            throw new IllegalArgumentException("AI capability selection is invalid");
        }
        AiAssistantAvailability availability = availabilityService.requireAvailableForActor(assistantKey);
        AiAssistantProfile profile = availability.profile();
        AiCapabilityRequest capabilityRequest = new AiCapabilityRequest(
                profile.key(), availability.actorId(), request.getCapability(),
                new AiCapabilityRequest.ResourceReference(
                        request.getCapability().resourceType(), request.getResourceId()),
                request.getCapability().parentResourceType() == null ? null
                        : new AiCapabilityRequest.ResourceReference(
                                request.getCapability().parentResourceType(), request.getParentResourceId()),
                request.getCapability().action());
        AiAuthorizedContext authorized = contextFacade.build(
                new AiContextBuildRequest(capabilityRequest, normalizedRequestId));
        validateAuthority(profile, capabilityRequest, authorized);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("assistantKey", profile.key().name());
        payload.put("input", request.getInput());
        payload.set("authorizedContext", objectMapper.valueToTree(AiPythonAuthorizedContext.from(authorized)));

        AiChatResponse response = gatewayClient.chat(new AiGatewayRequest(payload, normalizedRequestId));
        return new AiAssistantChatResponse(profile.key().name(), response.answer(),
                response.promptTokens(), response.completionTokens());
    }

    private static void validateAuthority(AiAssistantProfile profile,
                                          AiCapabilityRequest request,
                                          AiAuthorizedContext authorized) {
        if (profile == null || authorized == null || authorized.assistantKey() != profile.key()
                || authorized.domain() != profile.domain() || authorized.capability() != request.capability()
                || authorized.resource() == null
                || authorized.resource().type() != request.resource().type()
                || !Objects.equals(authorized.resource().id(), request.resource().id())) {
            throw new AiContextReadDeniedException();
        }
        if (request.parentResource() != null
                && (request.parentResource().type() != com.web.labportalbackend.ai.enums.AiResourceType.PROJECT
                || !Objects.equals(authorized.resource().projectId(), request.parentResource().id()))) {
            throw new AiContextReadDeniedException();
        }
    }
}
