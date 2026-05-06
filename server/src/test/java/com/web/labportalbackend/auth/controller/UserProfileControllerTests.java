package com.web.labportalbackend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.dto.AuthResponse;
import com.web.labportalbackend.auth.dto.LoginRequest;
import com.web.labportalbackend.auth.dto.UpdateProfileRequest;
import com.web.labportalbackend.auth.dto.UserProfileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for User Profile endpoints (Day 4).
 * Tests include:
 * - Bearer token authentication for GET /api/users/me
 * - Bearer token authentication for PUT /api/users/me
 * - Profile retrieval from SecurityContext
 * - Profile update with validation
 * - Complete auth flow (login → get profile → update profile)
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("User Profile Controller Tests - Day 4")
class UserProfileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String validAccessToken;
    private String validUsername;

    @BeforeEach
    void setup() throws Exception {
        // Day 1-3: Login to get valid access token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("admin");
        loginRequest.setPassword("admin123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        // Extract token from response using JsonPath  
        String responseBody = loginResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        com.fasterxml.jackson.databind.JsonNode dataNode = rootNode.get("data");
        
        validAccessToken = dataNode.get("accessToken").asText();
        validUsername = dataNode.get("username").asText();
    }

    @Test
    @DisplayName("GET /api/users/me should return 403 without Bearer token")
    void testGetProfileWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/me should return 403 with invalid Bearer token")
    void testGetProfileWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer invalid.token.here")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users/me should return current user profile with valid Bearer token")
    void testGetProfileWithValidToken() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", equalTo(validUsername)))
                .andExpect(jsonPath("$.data.email", notNullValue()))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.roles", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("GET /api/users/me should return profile extracted from SecurityContext")
    void testGetProfileUsesSecurityContext() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"username\""));
        assertFalse(responseBody.contains("\"password\"")); // No password in response
    }

    @Test
    @DisplayName("PUT /api/users/me should return 403 without Bearer token")
    void testUpdateProfileWithoutToken() throws Exception {
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .phone("0912345678")
                .build();

        mockMvc.perform(put("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/users/me should return 400 with invalid fullName")
    void testUpdateProfileWithInvalidFullName() throws Exception {
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .fullName("") // Empty fullName
                .phone("0912345678")
                .build();

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/users/me should return 400 with invalid phone format")
    void testUpdateProfileWithInvalidPhone() throws Exception {
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .fullName("Valid Name")
                .phone("abc") // Invalid phone
                .build();

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/users/me should update profile with valid Bearer token")
    void testUpdateProfileWithValidToken() throws Exception {
        String newFullName = "Updated Admin Name";
        String newPhone = "0987654321";

        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .fullName(newFullName)
                .phone(newPhone)
                .build();

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.fullName", equalTo(newFullName)))
                .andExpect(jsonPath("$.data.phone", equalTo(newPhone)))
                .andExpect(jsonPath("$.data.username", equalTo(validUsername)))
                .andExpect(jsonPath("$.data.email", notNullValue()));
    }

    @Test
    @DisplayName("PUT /api/users/me should not allow updating email")
    void testUpdateProfileDoesNotUpdateEmail() throws Exception {
        // Get original email first
        MvcResult getResult = mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String getResponse = getResult.getResponse().getContentAsString();
        String originalEmail = objectMapper.readTree(getResponse)
                .get("data").get("email").asText();

        // Try to update profile (email should not change)
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .fullName("New Name")
                .phone("0912345678")
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String updateResponse = updateResult.getResponse().getContentAsString();
        String emailAfterUpdate = objectMapper.readTree(updateResponse)
                .get("data").get("email").asText();

        assertEquals(originalEmail, emailAfterUpdate, "Email should not change after profile update");
    }

    @Test
    @DisplayName("Complete auth flow: login → get profile → update profile")
    void testCompleteAuthFlow() throws Exception {
        // Step 1: Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("admin");
        loginRequest.setPassword("admin123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode dataNode = objectMapper.readTree(loginResponse)
                .get("data");
        
        String accessToken = dataNode.get("accessToken").asText();
        assertNotNull(accessToken);

        // Step 2: Get current user profile
        MvcResult getProfileResult = mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", notNullValue()))
                .andReturn();

        String getProfileResponse = getProfileResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode profileNode = objectMapper.readTree(getProfileResponse)
                .get("data");
        
        Long userId = profileNode.get("id").asLong();
        String username = profileNode.get("username").asText();
        
        assertNotNull(userId);
        assertNotNull(username);

        // Step 3: Update profile
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .fullName("Flow Test User")
                .phone("0912345678")
                .build();

        MvcResult updateResult = mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.fullName", equalTo("Flow Test User")))
                .andExpect(jsonPath("$.data.phone", equalTo("0912345678")))
                .andReturn();

        // Step 4: Verify profile was updated by getting it again
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName", equalTo("Flow Test User")))
                .andExpect(jsonPath("$.data.phone", equalTo("0912345678")));
    }

    @Test
    @DisplayName("Verify user profile DTO does not expose password")
    void testProfileDTODoesNotExposeSensitiveData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("password"), "Response should not contain password field");
    }

    @Test
    @DisplayName("PUT /api/users/me should persist changes in database")
    void testProfileUpdatePersistence() throws Exception {
        String newFullName = "Persistence Test";
        String newPhone = "+84912345678";

        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .fullName(newFullName)
                .phone(newPhone)
                .build();

        // Update profile
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // Login again and verify changes are persisted
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail(validUsername);
        loginRequest.setPassword("admin123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        String newAccessToken = objectMapper.readTree(loginResponse)
                .get("data").get("accessToken").asText();

        // Get profile with new token
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + newAccessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName", equalTo(newFullName)))
                .andExpect(jsonPath("$.data.phone", equalTo(newPhone)));
    }
}

