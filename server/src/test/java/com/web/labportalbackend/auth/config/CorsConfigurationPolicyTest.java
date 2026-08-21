package com.web.labportalbackend.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:5173, http://localhost:5174, http://localhost:5173, https://frontend.example.invalid"
})
@AutoConfigureMockMvc
class CorsConfigurationPolicyTest {

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exactOriginsAreTrimmedDeduplicatedAndCredentialsStayDisabled() {
        CorsConfiguration configuration = configuration();

        assertThat(configuration.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "http://localhost:5174", "https://frontend.example.invalid");
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Accept", "X-Request-Id");
    }

    @Test
    void untrustedOriginsAreNotAllowedAndPreflightMethodsAreExplicit() {
        CorsConfiguration configuration = configuration();

        assertThat(configuration.checkOrigin("https://untrusted.example.invalid")).isNull();
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
    }

    @Test
    void authorizationPreflightAllowsTrustedOriginAndRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://frontend.example.invalid")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type,X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.invalid"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        "Authorization, Content-Type, X-Request-Id"));

        mockMvc.perform(options("/auth/login")
                        .header("Origin", "https://untrusted.example.invalid")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedConfiguredOriginsAreRejected() {
        for (String malformed : new String[]{
                "null",
                "ftp://frontend.example.invalid",
                "https://user@frontend.example.invalid",
                "https://frontend.example.invalid/path",
                "https://frontend.example.invalid/"
        }) {
            SecurityConfig config = new SecurityConfig(null, null);
            ReflectionTestUtils.setField(config, "configuredAllowedOrigins", malformed);
            assertThatThrownBy(config::corsConfigurationSource)
                    .as("configured origin %s", malformed)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private CorsConfiguration configuration() {
        CorsConfiguration configuration = corsConfigurationSource.getCorsConfiguration(
                new MockHttpServletRequest("OPTIONS", "/api/auth/login")
        );
        assertThat(configuration).isNotNull();
        return configuration;
    }
}
