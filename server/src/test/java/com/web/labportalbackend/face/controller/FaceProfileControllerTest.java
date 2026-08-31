package com.web.labportalbackend.face.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import com.web.labportalbackend.face.dto.response.FaceConsentResponse;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.service.FaceProfileService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = FaceProfileController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(FaceProfileControllerTest.MethodSecurityTestConfig.class)
class FaceProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean FaceProfileService faceProfileService;

    @Test
    void authenticatedOwnerCanChangeOwnConsent() throws Exception {
        when(faceProfileService.changeConsent(eq(null), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FaceConsentResponse(7L, FaceConsentStatus.GRANTED,
                        Instant.parse("2026-08-31T00:00:00Z")));

        mockMvc.perform(post("/api/face/consent").contextPath("/api")
                        .with(csrf()).with(user("student").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"GRANTED\",\"reason\":\"opt in\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.status").value("GRANTED"));

        verify(faceProfileService).changeConsent(eq(null),
                argThat(request -> request.status() == FaceConsentStatus.GRANTED));
    }

    @Test
    void userScopedAdminRouteRejectsNonAdmin() throws Exception {
        mockMvc.perform(post("/api/face/users/9/consent").contextPath("/api")
                        .with(csrf()).with(user("student").roles("STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WITHDRAWN\",\"reason\":\"requested\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(faceProfileService);
    }

    @Test
    void userScopedAdminRouteAllowsAdmin() throws Exception {
        when(faceProfileService.changeConsent(eq(9L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new FaceConsentResponse(9L, FaceConsentStatus.WITHDRAWN,
                        Instant.parse("2026-08-31T00:00:00Z")));

        mockMvc.perform(post("/api/face/users/9/consent").contextPath("/api")
                        .with(csrf()).with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WITHDRAWN\",\"reason\":\"requested\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(9));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
