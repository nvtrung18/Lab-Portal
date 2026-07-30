package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.service.TaskProposalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TaskProposalController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(TaskProposalControllerTest.MethodSecurityTestConfig.class)
class TaskProposalControllerTest {

    private static final String VALID_BODY = """
            {"projectId":20,"groupId":30,"title":"Proposal title"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskProposalService taskProposalService;

    @Test
    void studentSubmissionReturnsExactCreatedWrapperAndDataShape() throws Exception {
        when(taskProposalService.submit(any())).thenReturn(response());

        mockMvc.perform(request(VALID_BODY).with(user("student").roles("STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(6)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Task proposal submitted successfully"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data", aMapWithSize(16)))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.proposedById").value(7))
                .andExpect(jsonPath("$.data.projectId").value(20))
                .andExpect(jsonPath("$.data.groupId").value(30))
                .andExpect(jsonPath("$.data.milestoneId").value(nullValue()))
                .andExpect(jsonPath("$.data.parentTaskId").value(nullValue()))
                .andExpect(jsonPath("$.data.title").value("Proposal title"))
                .andExpect(jsonPath("$.data.description").value(nullValue()))
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.data.type").value("TASK"))
                .andExpect(jsonPath("$.data.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.data.assistedByAi").value(false))
                .andExpect(jsonPath("$.data.aiActionSuggestionId").value(nullValue()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-07-30T08:00:00Z"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-07-30T08:00:00Z"));

        verify(taskProposalService).submit(org.mockito.ArgumentMatchers.argThat(request ->
                request.getProjectId().equals(20L)
                        && request.getGroupId().equals(30L)
                        && request.getTitle().equals("Proposal title")));
    }

    @Test
    void unauthenticatedSubmissionIsUnauthorized() throws Exception {
        mockMvc.perform(request(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taskProposalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LAB_MANAGER", "ADMIN", "RESEARCHER", "LEADER",
            "LAB_LEADER", "UNRELATED"
    })
    void unsupportedSystemRolesAreForbidden(String role) throws Exception {
        mockMvc.perform(request(VALID_BODY).with(user("unsupported").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskProposalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN", "LAB_MANAGER", "RESEARCHER",
            "LEADER", "LAB_LEADER", "UNRELATED"
    })
    void studentCombinedWithAnyOtherSystemRoleIsForbidden(String otherRole) throws Exception {
        mockMvc.perform(request(VALID_BODY)
                        .with(user("mixed-role").roles("STUDENT", otherRole)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskProposalService);
    }

    @Test
    void nonRoleAuthorityDoesNotDisqualifyStudent() throws Exception {
        when(taskProposalService.submit(any())).thenReturn(response());

        mockMvc.perform(request(VALID_BODY)
                        .with(user("student").authorities(
                                new SimpleGrantedAuthority("ROLE_STUDENT"),
                                new SimpleGrantedAuthority("SCOPE_profile")
                        )))
                .andExpect(status().isCreated());

        verify(taskProposalService).submit(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"projectId\":20,\"groupId\":30}",
            "{\"groupId\":30,\"title\":\"Title\"}",
            "{\"projectId\":20,\"title\":\"Title\"}",
            "{\"projectId\":20,\"groupId\":30,\"title\":\"   \"}",
            "{\"projectId\":20,\"groupId\":30,\"title\":\"Title\",\"priority\":\"CRITICAL\"}",
            "{\"projectId\":20,\"groupId\":30,\"title\":\"Title\",\"type\":\"UNKNOWN\"}",
            "{\"projectId\":20,\"groupId\":30,\"title\":\"Title\",\"dueDate\":\"bad\"}",
            "",
            "null",
            "{"
    })
    void malformedOrInvalidBodiesAreBadRequestWithoutServiceInvocation(String body) throws Exception {
        mockMvc.perform(request(body).with(user("student").roles("STUDENT")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskProposalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "proposedBy", "proposedById", "proposerId", "status",
            "reviewedById", "reason", "reviewedAt", "payloadJson",
            "assistedByAi", "aiActionSuggestionId", "assigneeId",
            "ProjectId", "unknown"
    })
    void unknownSpoofedAndCaseVariantFieldsAreBadRequest(String field) throws Exception {
        String body = "{\"projectId\":20,\"groupId\":30,\"title\":\"Title\",\""
                + field + "\":1}";

        mockMvc.perform(request(body).with(user("student").roles("STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(taskProposalService);
    }

    @Test
    void appliesTitleAndDescriptionLengthLimits() throws Exception {
        String body = "{\"projectId\":20,\"groupId\":30,\"title\":\""
                + "t".repeat(201) + "\",\"description\":\"" + "d".repeat(4001) + "\"}";

        mockMvc.perform(request(body).with(user("student").roles("STUDENT")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskProposalService);
    }

    @Test
    void preservesExistingServiceErrorContract() throws Exception {
        when(taskProposalService.submit(any()))
                .thenThrow(new IllegalArgumentException("Invalid scope"))
                .thenThrow(new AccessDeniedException("Membership required"))
                .thenThrow(new ResourceNotFoundException("Project", 20L))
                .thenThrow(new RuntimeException("unexpected"));

        for (int expected : new int[]{400, 403, 404, 500}) {
            mockMvc.perform(request(VALID_BODY).with(user("student").roles("STUDENT")))
                    .andExpect(status().is(expected))
                    .andExpect(jsonPath("$.code").value(expected));
        }
    }

    @Test
    void allCurrentEnumsAndIsoDateBindAndDelegate() throws Exception {
        when(taskProposalService.submit(any())).thenReturn(response());
        String body = """
                {"projectId":20,"groupId":30,"title":"Title",
                 "priority":"URGENT","type":"REVIEW","dueDate":"2026-08-31"}
                """;

        mockMvc.perform(request(body).with(user("student").roles("STUDENT")))
                .andExpect(status().isCreated());

        verify(taskProposalService).submit(org.mockito.ArgumentMatchers.argThat(request ->
                request.getPriority() == TaskPriority.URGENT
                        && request.getType() == TaskType.REVIEW
                        && request.getDueDate().toString().equals("2026-08-31")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post("/api/research/task-proposals")
                .contextPath("/api")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private TaskProposalResponse response() {
        return TaskProposalResponse.builder()
                .id(100L)
                .proposedById(7L)
                .projectId(20L)
                .groupId(30L)
                .title("Proposal title")
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .assistedByAi(false)
                .status(TaskProposalStatus.PENDING)
                .createdAt(Instant.parse("2026-07-30T08:00:00Z"))
                .updatedAt(Instant.parse("2026-07-30T08:00:00Z"))
                .build();
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }
}
