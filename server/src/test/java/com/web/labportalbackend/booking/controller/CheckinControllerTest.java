package com.web.labportalbackend.booking.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import com.web.labportalbackend.booking.service.CheckinService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = CheckinController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(CheckinControllerTest.MethodSecurityTestConfig.class)
class CheckinControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CheckinService checkinService;

    @Test
    void legacyGpsOnlySelfCheckinEndpointIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/checkin/self").contextPath("/api")
                        .with(csrf()).with(user("student").roles("STUDENT"))
                        .contentType("application/json")
                        .content("{\"bookingId\":11,\"latitude\":10.7627,\"longitude\":106.660172,\"accuracyMeters\":25}"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
