package com.web.labportalbackend.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.ai.client.AiChatResponse;
import com.web.labportalbackend.ai.client.AiGatewayClient;
import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.dto.request.AiAssistantChatRequest;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.service.AiAssistantGatewayService;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import org.springframework.stereotype.Service;

@Service
public class AiAssistantGatewayServiceImpl implements AiAssistantGatewayService {

    private final AiAssistantRegistry assistantRegistry;
    private final AiGatewayClient gatewayClient;
    private final ObjectMapper objectMapper;

    public AiAssistantGatewayServiceImpl(AiAssistantRegistry assistantRegistry,
                                         AiGatewayClient gatewayClient,
                                         ObjectMapper objectMapper) {
        this.assistantRegistry = assistantRegistry;
        this.gatewayClient = gatewayClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAssistantChatResponse chat(AiAssistantKey assistantKey,
                                        AiAssistantChatRequest request,
                                        String requestId) {
        AiAssistantProfile profile = assistantRegistry.getProfile(assistantKey);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("assistantKey", profile.key().name());
        payload.put("input", request.getInput());

        AiChatResponse response = gatewayClient.chat(new AiGatewayRequest(payload, requestId));
        return new AiAssistantChatResponse(profile.key().name(), response.answer(),
                response.promptTokens(), response.completionTokens());
    }
}
