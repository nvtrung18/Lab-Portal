package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.ai.client.AiChatResponse;
import com.web.labportalbackend.ai.client.AiGatewayClient;
import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.dto.request.AiAssistantChatRequest;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityFailure;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityService;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AiAssistantGatewayServiceImplTest {

    private final AiAssistantAvailabilityService availabilityService = mock(AiAssistantAvailabilityService.class);
    private final AiGatewayClient gatewayClient = mock(AiGatewayClient.class);
    private final AiAssistantGatewayServiceImpl service = new AiAssistantGatewayServiceImpl(
            availabilityService, gatewayClient, new ObjectMapper());

    @ParameterizedTest
    @EnumSource(AiAssistantKey.class)
    void canonicalAssistantIsResolvedWithoutTreatingTheKeyAsAuthorization(AiAssistantKey assistantKey) {
        AiAssistantProfile profile = mock(AiAssistantProfile.class);
        when(profile.key()).thenReturn(assistantKey);
        when(availabilityService.requireAvailable(assistantKey)).thenReturn(profile);
        when(gatewayClient.chat(any())).thenReturn(new AiChatResponse(
                assistantKey.name(), "Safe answer", 12, 7, Map.of()));

        AiAssistantChatRequest publicRequest = new AiAssistantChatRequest();
        publicRequest.setInput("Summarize what I may access.");

        AiAssistantChatResponse response = service.chat(assistantKey, publicRequest, " request-123 ");

        ArgumentCaptor<AiGatewayRequest> gatewayRequest = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gatewayClient).chat(gatewayRequest.capture());
        InOrder invocationOrder = inOrder(availabilityService, gatewayClient);
        invocationOrder.verify(availabilityService).requireAvailable(assistantKey);
        invocationOrder.verify(gatewayClient).chat(any());

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

    static Stream<Arguments> availabilityDenials() {
        return Stream.of(
                Arguments.of(AiAssistantAvailabilityFailure.ASSISTANT_UNAVAILABLE),
                Arguments.of(AiAssistantAvailabilityFailure.ROLE_NOT_ALLOWED),
                Arguments.of(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE),
                Arguments.of(AiAssistantAvailabilityFailure.QUOTA_EXCEEDED));
    }

    @ParameterizedTest
    @MethodSource("availabilityDenials")
    void deniedAvailabilityNeverInvokesDownstreamClient(AiAssistantAvailabilityFailure failure) {
        when(availabilityService.requireAvailable(AiAssistantKey.RESEARCH_ASSISTANT))
                .thenThrow(new AiAssistantAvailabilityException(failure));
        AiAssistantChatRequest request = new AiAssistantChatRequest();
        request.setInput("Do not send this request downstream.");

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.chat(AiAssistantKey.RESEARCH_ASSISTANT, request, "request-denied"));

        assertEquals(failure, exception.failure());
        verifyNoInteractions(gatewayClient);
    }
}
