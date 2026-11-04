package com.web.labportalbackend.ai.controller;

import com.web.labportalbackend.auth.config.SecurityConfig;
import com.web.labportalbackend.auth.security.CustomUserDetailsService;
import com.web.labportalbackend.auth.security.JwtProvider;
import com.web.labportalbackend.auth.security.PortalAccessService;
import com.web.labportalbackend.ai.service.AiAssistantGatewayService;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiAssistantController.class)
@Import(SecurityConfig.class)
class AiPublicPolicySecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CustomUserDetailsService users;
    @MockitoBean JwtProvider jwt;
    @MockitoBean PortalAccessService portal;
    @MockitoBean AiAssistantGatewayService gateway;

    @Test
    void authenticatedStudentWithoutPortalMembershipCanReachTheCapabilityPolicy() throws Exception {
        when(portal.canEnter(any())).thenReturn(false);
        when(gateway.chat(any(), any(), any())).thenReturn(new AiAssistantChatResponse(
                "LAB_ASSISTANT", "Public policy only", 0, 0, List.of()));
        mvc.perform(post("/ai/assistants/LAB_ASSISTANT/chat").with(user("new-student").roles("STUDENT"))
                .contentType("application/json").content(
                        "{\"input\":\"Policy\",\"capability\":\"LAB_POLICY_READ\",\"resourceId\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/ai/chat").with(user("new-student").roles("STUDENT"))
                .contentType("application/json").content("{\"input\":\"Hello\"}"))
                .andExpect(status().isForbidden());
    }
}
