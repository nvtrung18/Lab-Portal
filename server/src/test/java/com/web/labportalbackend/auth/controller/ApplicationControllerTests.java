package com.web.labportalbackend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.dto.LoginRequest;
import com.web.labportalbackend.auth.dto.RegisterRequest;
import com.web.labportalbackend.common.dto.ApplyRequestDTO;
import com.web.labportalbackend.common.dto.CreateLabRequest;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
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
 * Integration tests for Application Controller endpoints (Day 6).
 * Tests cover application submission, duplicate prevention, and retrieval scenarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Application Controller Tests - Day 6")
class ApplicationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private String adminToken;
    private String userToken;
    private Long testLabId;
    private Long testUserId;

    @BeforeEach
    void setup() throws Exception {
        // Clear existing data
        applicationRepository.deleteAll();
        laboratoryRepository.deleteAll();

        // Login as admin
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setUsernameOrEmail("admin");
        adminLogin.setPassword("admin123");

        MvcResult adminLoginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();

        String adminResponse = adminLoginResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode adminNode = objectMapper.readTree(adminResponse);
        adminToken = adminNode.get("data").get("accessToken").asText();

        // Register and login as test user
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail("apptest@test.com");
        registerRequest.setUsername("apptestuser");
        registerRequest.setPassword("password123");
        registerRequest.setFullName("App Test User");
        registerRequest.setPhone("+84901234567");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        LoginRequest userLogin = new LoginRequest();
        userLogin.setUsernameOrEmail("apptestuser");
        userLogin.setPassword("password123");

        MvcResult userLoginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userLogin)))
                .andExpect(status().isOk())
                .andReturn();

        String userResponse = userLoginResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode userNode = objectMapper.readTree(userResponse);
        userToken = userNode.get("data").get("accessToken").asText();
        testUserId = userNode.get("data").get("userId").asLong();

        // Create a test laboratory
        CreateLabRequest labRequest = new CreateLabRequest();
        labRequest.setLabName("Application Test Lab");
        labRequest.setDescription("Lab for application testing");
        labRequest.setLocation("Building C");
        labRequest.setCapacity(25);
        labRequest.setDepartment("Testing");

        MvcResult labResult = mockMvc.perform(post("/api/labs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(labRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String labResponse = labResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode labNode = objectMapper.readTree(labResponse);
        testLabId = labNode.get("data").get("id").asLong();
    }

    @Test
    @DisplayName("POST /api/applications/labs/{id}/apply - Should successfully apply to lab")
    void testApplySuccess() throws Exception {
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Application submitted successfully"))
                .andExpect(jsonPath("$.data.userId").value(testUserId.intValue()))
                .andExpect(jsonPath("$.data.labId").value(testLabId.intValue()))
                .andExpect(jsonPath("$.data.labName").value("Application Test Lab"))
                .andExpect(jsonPath("$.data.cvUrl").value("https://storage.example.com/cv/user_cv.pdf"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/applications/labs/{id}/apply - Should reject without token")
    void testApplyWithoutToken() throws Exception {
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/applications/labs/{id}/apply - Should reject invalid CV URL")
    void testApplyInvalidCvUrl() throws Exception {
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl(""); // Empty CV URL

        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/applications/labs/{id}/apply - Should reject duplicate application")
    void testApplyDuplicate() throws Exception {
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        // First application should succeed
        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second application from same user to same lab should fail
        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/applications/labs/{id}/apply - Should reject non-existent lab")
    void testApplyNonExistentLab() throws Exception {
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        mockMvc.perform(post("/api/applications/labs/999/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/applications - Should retrieve all applications")
    void testGetApplications() throws Exception {
        // Create an application first
        ApplyRequestDTO applyRequest = new ApplyRequestDTO();
        applyRequest.setUserId(testUserId);
        applyRequest.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(applyRequest)))
                .andExpect(status().isCreated());

        // Retrieve all applications
        mockMvc.perform(get("/api/applications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.content[0].labName").value("Application Test Lab"));
    }

    @Test
    @DisplayName("GET /api/applications - Should require authentication")
    void testGetApplicationsRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/applications")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/applications/{applicationId} - Should retrieve application by ID")
    void testGetApplicationById() throws Exception {
        // Create an application first
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        MvcResult result = mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        Long applicationId = rootNode.get("data").get("id").asLong();

        // Retrieve the application by ID
        mockMvc.perform(get("/api/applications/" + applicationId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(applicationId))
                .andExpect(jsonPath("$.data.labName").value("Application Test Lab"));
    }

    @Test
    @DisplayName("GET /api/applications/{applicationId} - Should return 404 for non-existent application")
    void testGetApplicationByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/applications/999")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/applications/users/{userId} - Should retrieve applications by user")
    void testGetApplicationsByUserId() throws Exception {
        // Create an application
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Retrieve applications by user
        mockMvc.perform(get("/api/applications/users/" + testUserId)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/applications/labs/{labId} - Should retrieve applications by lab")
    void testGetApplicationsByLabId() throws Exception {
        // Create an application
        ApplyRequestDTO request = new ApplyRequestDTO();
        request.setUserId(testUserId);
        request.setCvUrl("https://storage.example.com/cv/user_cv.pdf");

        mockMvc.perform(post("/api/applications/labs/" + testLabId + "/apply")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Retrieve applications by lab
        mockMvc.perform(get("/api/applications/labs/" + testLabId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }
}
