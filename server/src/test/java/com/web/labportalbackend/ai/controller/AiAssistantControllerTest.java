package com.web.labportalbackend.ai.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.web.labportalbackend.ai.client.AiGatewayException;
import com.web.labportalbackend.ai.client.AiGatewayFailure;
import com.web.labportalbackend.ai.client.AiGatewayFailureCategory;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityFailure;
import com.web.labportalbackend.ai.service.AiAssistantGatewayService;
import com.web.labportalbackend.ai.service.AiCapabilityDeniedException;
import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AiAssistantController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(AiAssistantControllerTest.MethodSecurityTestConfig.class)
class AiAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiAssistantGatewayService gatewayService;

    @ParameterizedTest
    @EnumSource(AiAssistantKey.class)
    void authenticatedCanonicalKeyReachesGatewayWithoutForwardingAuthorization(AiAssistantKey assistantKey)
            throws Exception {
        when(gatewayService.chat(eq(assistantKey), org.mockito.ArgumentMatchers.any(), eq("request-123")))
                .thenReturn(new AiAssistantChatResponse(assistantKey.name(), "Safe answer", 12, 7));

        mockMvc.perform(post("/api/ai/assistants/{assistantKey}/chat", assistantKey.name())
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-jwt-must-stay-in-spring")
                        .header("X-Request-Id", "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":"Summarize allowed information.",
                                 "capability":"LAB_POLICY_READ","resourceId":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.assistantKey").value(assistantKey.name()))
                .andExpect(jsonPath("$.data.answer").value("Safe answer"))
                .andExpect(jsonPath("$.data.metadata").doesNotExist())
                .andExpect(content().string(not(containsString("user-jwt-must-stay-in-spring"))));

        verify(gatewayService).chat(eq(assistantKey),
                argThat(request -> "Summarize allowed information.".equals(request.getInput())
                        && request.getCapability() == AiCapability.LAB_POLICY_READ
                        && Long.valueOf(10L).equals(request.getResourceId())),
                eq("request-123"));
    }

    @Test
    void assistantKeyWithoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(post("/api/ai/assistants/LAB_ASSISTANT/chat")
                        .contextPath("/api")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gatewayService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN_ASSISTANT", "research_assistant", "LAB", ""})
    void unknownOrNonCanonicalKeyFailsClosed(String assistantKey) throws Exception {
        mockMvc.perform(post("/api/ai/assistants/{assistantKey}/chat", assistantKey)
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gatewayService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "null", "{", "{}", "{\"input\":\"   \"}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\"}",
            "{\"input\":\"Hello\",\"capability\":\"ADMIN_SYSTEM_SUMMARY\",\"resourceId\":10}",
            "{\"input\":\"Hello\",\"capability\":\"RESEARCH_TASK_PROPOSAL_DRAFT\",\"resourceId\":30}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"parentResourceId\":20}",
            "{\"input\":\"Hello\",\"capability\":\"UNKNOWN\",\"resourceId\":10}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"resourceType\":\"LABORATORY\"}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"authorizedContext\":{}}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"allowedTools\":[]}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"allowedToolSchemas\":[]}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"authorizedResources\":[]}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"resourceReferences\":[]}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"effectiveCapabilities\":[]}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"role\":\"ADMIN\"}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"userJwt\":\"secret\"}",
            "{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10,\"accessToken\":\"secret\"}"
    })
    void malformedOrAuthorityInjectingBodyFailsBeforeGateway(String body) throws Exception {
        mockMvc.perform(post("/api/ai/assistants/LAB_ASSISTANT/chat")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(gatewayService);
    }

    @Test
    void downstreamNotReadyFailureMapsToGenericServiceUnavailable() throws Exception {
        AiGatewayException exception = Mockito.mock(AiGatewayException.class);
        when(exception.failure()).thenReturn(new AiGatewayFailure(
                AiGatewayFailureCategory.REMOTE, 503, "AI_MODEL_NOT_READY", "request-123"));
        when(gatewayService.chat(eq(AiAssistantKey.RESEARCH_ASSISTANT),
                org.mockito.ArgumentMatchers.any(), eq("request-123"))).thenThrow(exception);

        mockMvc.perform(post("/api/ai/assistants/RESEARCH_ASSISTANT/chat")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .header("X-Request-Id", "request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello\",\"capability\":\"RESEARCH_GROUP_SUMMARY\",\"resourceId\":30}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("AI assistant is temporarily unavailable"))
                .andExpect(content().string(not(containsString("AI_MODEL_NOT_READY"))))
                .andExpect(content().string(not(containsString("X-Internal-Service-Token"))))
                .andExpect(content().string(not(containsString("ai-service"))));
    }

    @Test
    void quotaDenialMapsToTooManyRequestsWithoutBecomingGatewayFailure() throws Exception {
        when(gatewayService.chat(eq(AiAssistantKey.RESEARCH_ASSISTANT),
                org.mockito.ArgumentMatchers.any(), eq("request-quota")))
                .thenThrow(new AiAssistantAvailabilityException(AiAssistantAvailabilityFailure.QUOTA_EXCEEDED));

        mockMvc.perform(post("/api/ai/assistants/RESEARCH_ASSISTANT/chat")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .header("X-Request-Id", "request-quota")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello\",\"capability\":\"RESEARCH_GROUP_SUMMARY\",\"resourceId\":30}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("AI assistant quota exceeded"))
                .andExpect(content().string(not(containsString("QUOTA_EXCEEDED"))))
                .andExpect(content().string(not(containsString("temporarily unavailable"))));
    }

    @Test
    void unavailableAssistantMapsToGenericForbiddenWithoutConfigDisclosure() throws Exception {
        when(gatewayService.chat(eq(AiAssistantKey.LAB_ASSISTANT),
                org.mockito.ArgumentMatchers.any(), eq("request-disabled")))
                .thenThrow(new AiAssistantAvailabilityException(
                        AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE));

        mockMvc.perform(post("/api/ai/assistants/LAB_ASSISTANT/chat")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .header("X-Request-Id", "request-disabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("AI assistant is not available for this request"))
                .andExpect(content().string(not(containsString("CONFIGURATION_UNAVAILABLE"))))
                .andExpect(content().string(not(containsString("database"))));
    }

    @Test
    void capabilityDenialMapsToGenericForbiddenWithoutAuthorizationDetails() throws Exception {
        when(gatewayService.chat(eq(AiAssistantKey.RESEARCH_ASSISTANT),
                org.mockito.ArgumentMatchers.any(), eq("request-capability")))
                .thenThrow(Mockito.mock(AiCapabilityDeniedException.class));

        mockMvc.perform(post("/api/ai/assistants/RESEARCH_ASSISTANT/chat")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .header("X-Request-Id", "request-capability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello\",\"capability\":\"RESEARCH_GROUP_SUMMARY\",\"resourceId\":30}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("AI assistant is not available for this request"))
                .andExpect(content().string(not(containsString("NOT_GROUP_MEMBER"))));
    }

    @Test
    void contextDenialMapsToGenericForbiddenWithoutResourceDetails() throws Exception {
        when(gatewayService.chat(eq(AiAssistantKey.LAB_ASSISTANT),
                org.mockito.ArgumentMatchers.any(), eq("request-context")))
                .thenThrow(new AiContextReadDeniedException());

        mockMvc.perform(post("/api/ai/assistants/LAB_ASSISTANT/chat")
                        .contextPath("/api")
                        .with(csrf())
                        .with(user("student").roles("STUDENT"))
                        .header("X-Request-Id", "request-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("AI assistant is not available for this request"))
                .andExpect(content().string(not(containsString("context is unavailable"))));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
