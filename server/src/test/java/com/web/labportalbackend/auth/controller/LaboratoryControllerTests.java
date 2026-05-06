package com.web.labportalbackend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.dto.LoginRequest;
import com.web.labportalbackend.common.dto.CreateLabRequest;
import com.web.labportalbackend.common.dto.LabDTO;
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
 * Integration tests for Laboratory Controller endpoints (Day 5).
 * Tests include:
 * - POST /api/labs - Create laboratory
 * - PUT /api/labs/{id}/manager - Assign manager
 * - Authentication and authorization
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Laboratory Controller Tests - Day 5")
class LaboratoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    private String validAccessToken;
    private String adminUsername;

    @BeforeEach
    void setup() throws Exception {
        // Login as admin to get valid token
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("admin");
        loginRequest.setPassword("admin123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        com.fasterxml.jackson.databind.JsonNode dataNode = rootNode.get("data");
        
        validAccessToken = dataNode.get("accessToken").asText();
        adminUsername = dataNode.get("username").asText();
    }

    @Test
    @DisplayName("GET /api/labs/health should return 200 without token")
    void testLabsHealthCheck() throws Exception {
        mockMvc.perform(get("/api/labs/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lab service is healthy"));
    }

    @Test
    @DisplayName("POST /api/labs should require Bearer token")
    void testCreateLabWithoutToken() throws Exception {
        CreateLabRequest request = new CreateLabRequest();
        request.setLabName("Test Lab");
        request.setDescription("Description");
        request.setLocation("Location");
        request.setCapacity(20);

        mockMvc.perform(post("/api/labs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/labs should require ADMIN role")
    void testCreateLabWithoutAdminRole() throws Exception {
        // Register and login as student
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        createRegisterRequest("student_test@test.com", "student_test", 
                                            "password123", "Student Test", "+84901234567"))))
                .andExpect(status().isOk());

        LoginRequest studentLogin = new LoginRequest();
        studentLogin.setUsernameOrEmail("student_test");
        studentLogin.setPassword("password123");

        MvcResult studentLoginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(studentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        String studentResponse = studentLoginResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode studentNode = objectMapper.readTree(studentResponse);
        String studentToken = studentNode.get("data").get("accessToken").asText();

        CreateLabRequest request = new CreateLabRequest();
        request.setLabName("Student Lab Test");
        request.setDescription("Description");
        request.setLocation("Location");
        request.setCapacity(20);

        mockMvc.perform(post("/api/labs")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/labs should create lab successfully with ADMIN token")
    void testCreateLabSuccess() throws Exception {
        CreateLabRequest request = new CreateLabRequest();
        request.setLabName("Chemistry Lab Controller Test");
        request.setDescription("Advanced chemistry experiments");
        request.setLocation("Building B, Floor 2");
        request.setCapacity(25);
        request.setDepartment("Chemistry");

        MvcResult result = mockMvc.perform(post("/api/labs")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Laboratory created successfully"))
                .andExpect(jsonPath("$.data.labName").value("Chemistry Lab Controller Test"))
                .andExpect(jsonPath("$.data.description").value("Advanced chemistry experiments"))
                .andExpect(jsonPath("$.data.location").value("Building B, Floor 2"))
                .andExpect(jsonPath("$.data.capacity").value(25))
                .andExpect(jsonPath("$.data.department").value("Chemistry"))
                .andExpect(jsonPath("$.data.manager").isEmptyOrNullString())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        Long labId = rootNode.get("data").get("id").asLong();

        assertNotNull(labId);
        assertTrue(laboratoryRepository.existsById(labId));
    }

    @Test
    @DisplayName("POST /api/labs should reject invalid request")
    void testCreateLabValidationFailed() throws Exception {
        CreateLabRequest request = new CreateLabRequest();
        // Missing required fields
        request.setLabName(""); // blank
        request.setCapacity(-1); // invalid

        mockMvc.perform(post("/api/labs")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/labs/{id} should retrieve lab by ID")
    void testGetLabById() throws Exception {
        // First create a lab
        CreateLabRequest createRequest = new CreateLabRequest();
        createRequest.setLabName("Get Lab By ID Test");
        createRequest.setDescription("Test lab");
        createRequest.setLocation("Test location");
        createRequest.setCapacity(20);
        createRequest.setDepartment("Test");

        MvcResult createResult = mockMvc.perform(post("/api/labs")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        Long labId = rootNode.get("data").get("id").asLong();

        // Now retrieve it
        mockMvc.perform(get("/api/labs/" + labId)
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(labId))
                .andExpect(jsonPath("$.data.labName").value("Get Lab By ID Test"));
    }

    @Test
    @DisplayName("PUT /api/labs/{id}/manager should assign manager successfully")
    void testAssignManagerSuccess() throws Exception {
        // Create lab
        CreateLabRequest createRequest = new CreateLabRequest();
        createRequest.setLabName("Assign Manager Test Lab");
        createRequest.setDescription("Test");
        createRequest.setLocation("Test location");
        createRequest.setCapacity(20);

        MvcResult createResult = mockMvc.perform(post("/api/labs")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        Long labId = rootNode.get("data").get("id").asLong();

        // Get admin user ID from login
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
        Long adminId = adminNode.get("data").get("userId").asLong();

        // Assign manager
        mockMvc.perform(put("/api/labs/" + labId + "/manager")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .param("managerId", adminId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Manager assigned successfully"))
                .andExpect(jsonPath("$.data.manager").isNotEmpty())
                .andExpect(jsonPath("$.data.manager.id").value(adminId));
    }

    @Test
    @DisplayName("PUT /api/labs/{id}/manager should require ADMIN role")
    void testAssignManagerWithoutAdminRole() throws Exception {
        // Create lab as admin
        CreateLabRequest createRequest = new CreateLabRequest();
        createRequest.setLabName("Admin-Only Manager Lab");
        createRequest.setDescription("Test");
        createRequest.setLocation("Test location");
        createRequest.setCapacity(20);

        MvcResult createResult = mockMvc.perform(post("/api/labs")
                .header("Authorization", "Bearer " + validAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(responseBody);
        Long labId = rootNode.get("data").get("id").asLong();

        // Register and login as student
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        createRegisterRequest("student2@test.com", "student2", 
                                            "password123", "Student 2", "+84901234567"))))
                .andExpect(status().isOk());

        LoginRequest studentLogin = new LoginRequest();
        studentLogin.setUsernameOrEmail("student2");
        studentLogin.setPassword("password123");

        MvcResult studentLoginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(studentLogin)))
                .andExpect(status().isOk())
                .andReturn();

        String studentResponse = studentLoginResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode studentNode = objectMapper.readTree(studentResponse);
        String studentToken = studentNode.get("data").get("accessToken").asText();

        // Try to assign manager as student
        mockMvc.perform(put("/api/labs/" + labId + "/manager")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .param("managerId", "1"))
                .andExpect(status().isForbidden());
    }

    // Helper method to create RegisterRequest
    private Object createRegisterRequest(String email, String username, String password, 
                                         String fullName, String phone) {
        com.web.labportalbackend.auth.dto.RegisterRequest request = new com.web.labportalbackend.auth.dto.RegisterRequest();
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword(password);
        request.setFullName(fullName);
        request.setPhone(phone);
        return request;
    }
}
