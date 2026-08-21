package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.ai.client.AiChatResponse;
import com.web.labportalbackend.ai.client.AiGatewayClient;
import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.dto.request.AiAssistantChatRequest;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class AiAssistantGatewayServiceImplTest {

    private final AiAssistantRegistry assistantRegistry = mock(AiAssistantRegistry.class);
    private final AiGatewayClient gatewayClient = mock(AiGatewayClient.class);
    private final AiAssistantGatewayServiceImpl service = new AiAssistantGatewayServiceImpl(
            assistantRegistry, gatewayClient, new ObjectMapper());

    @ParameterizedTest
    @EnumSource(AiAssistantKey.class)
    void canonicalAssistantIsResolvedWithoutTreatingTheKeyAsAuthorization(AiAssistantKey assistantKey) {
        AiAssistantProfile profile = mock(AiAssistantProfile.class);
        when(profile.key()).thenReturn(assistantKey);
        when(assistantRegistry.getProfile(assistantKey)).thenReturn(profile);
        when(gatewayClient.chat(any())).thenReturn(new AiChatResponse(
                assistantKey.name(), "Safe answer", 12, 7, Map.of()));

        AiAssistantChatRequest publicRequest = new AiAssistantChatRequest();
        publicRequest.setInput("Summarize what I may access.");

        AiAssistantChatResponse response = service.chat(assistantKey, publicRequest, " request-123 ");

        ArgumentCaptor<AiGatewayRequest> gatewayRequest = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gatewayClient).chat(gatewayRequest.capture());
        verify(assistantRegistry).getProfile(assistantKey);
        verify(assistantRegistry, never()).getAvailableProfile(any(), any(AiAssistantSystemRole.class));

        assertEquals(assistantKey.name(), gatewayRequest.getValue().payload().path("assistantKey").asText());
        assertEquals("Summarize what I may access.", gatewayRequest.getValue().payload().path("input").asText());
        assertEquals(2, gatewayRequest.getValue().payload().size());
        assertFalse(gatewayRequest.getValue().payload().has("authorizedContext"));
        assertFalse(gatewayRequest.getValue().payload().has("Authorization"));
        assertFalse(gatewayRequest.getValue().payload().has("userJwt"));
        assertFalse(gatewayRequest.getValue().payload().has("jwt"));
        assertFalse(gatewayRequest.getValue().payload().has("accessToken"));
        assertFalse(gatewayRequest.getValue().payload().has("refreshToken"));
        assertEquals("request-123", gatewayRequest.getValue().requestId());

        assertEquals(assistantKey.name(), response.assistantKey());
        assertEquals("Safe answer", response.answer());
        assertEquals(12, response.promptTokens());
        assertEquals(7, response.completionTokens());
    }
}
