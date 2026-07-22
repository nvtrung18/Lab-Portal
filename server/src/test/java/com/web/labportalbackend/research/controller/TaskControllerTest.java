package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.auth.security.JwtAuthenticationFilter;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.response.ProjectTaskBoardResponse;
import com.web.labportalbackend.research.dto.response.TaskBacklogPageResponse;
import com.web.labportalbackend.research.dto.response.TaskBoardColumnResponse;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.service.TaskBoardReadService;
import com.web.labportalbackend.research.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TaskController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@Import(TaskControllerTest.MethodSecurityTestConfig.class)
class TaskControllerTest {

    private static final Long PROJECT_ID = 123L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private TaskBoardReadService taskBoardReadService;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Test
    void getProjectBoardReturnsExactWrapperShapeAndDefaultBindings() throws Exception {
        ProjectTaskBoardResponse response = board(TaskStatus.TODO);
        when(taskBoardReadService.read(PROJECT_ID, null, null, null, null, null, false, false))
                .thenReturn(response);

        mockMvc.perform(boardRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(6)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Project task board retrieved successfully"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.columns[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$.data.columns[0].status").value("TODO"))
                .andExpect(jsonPath("$.data.columns[0].tasks").isArray())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());

        verify(taskBoardReadService).read(PROJECT_ID, null, null, null, null, null, false, false);
    }

    @Test
    void allQueryParametersAreConvertedAndForwardedUnchanged() throws Exception {
        when(taskBoardReadService.read(PROJECT_ID, 10L, 20L, TaskStatus.IN_REVIEW,
                TaskPriority.HIGH, TaskType.REVIEW, true, true)).thenReturn(board(TaskStatus.IN_REVIEW));

        mockMvc.perform(boardRequest(PROJECT_ID)
                        .param("groupId", "10")
                        .param("assigneeId", "20")
                        .param("status", "IN_REVIEW")
                        .param("priority", "HIGH")
                        .param("type", "REVIEW")
                        .param("includeBacklog", "true")
                        .param("includeCancelled", "true")
                        .with(manager()))
                .andExpect(status().isOk());

        verify(taskBoardReadService).read(PROJECT_ID, 10L, 20L, TaskStatus.IN_REVIEW,
                TaskPriority.HIGH, TaskType.REVIEW, true, true);
    }

    @ParameterizedTest
    @MethodSource("explicitStatusForwardingCases")
    void explicitStatusAndIncludeFlagsAreForwardedUnchanged(TaskStatus taskStatus,
                                                            boolean includeBacklog,
                                                            boolean includeCancelled) throws Exception {
        when(taskBoardReadService.read(PROJECT_ID, null, null, taskStatus, null, null,
                includeBacklog, includeCancelled)).thenReturn(board(taskStatus));

        mockMvc.perform(boardRequest(PROJECT_ID)
                        .param("status", taskStatus.name())
                        .param("includeBacklog", Boolean.toString(includeBacklog))
                        .param("includeCancelled", Boolean.toString(includeCancelled))
                        .with(manager()))
                .andExpect(status().isOk());

        verify(taskBoardReadService).read(PROJECT_ID, null, null, taskStatus, null, null,
                includeBacklog, includeCancelled);
    }

    @Test
    void bothIncludeFlagsAreForwardedWhenStatusIsAbsent() throws Exception {
        when(taskBoardReadService.read(PROJECT_ID, null, null, null, null, null, true, true))
                .thenReturn(board(TaskStatus.BACKLOG));

        mockMvc.perform(boardRequest(PROJECT_ID)
                        .param("includeBacklog", "true")
                        .param("includeCancelled", "true")
                        .with(manager()))
                .andExpect(status().isOk());

        verify(taskBoardReadService).read(PROJECT_ID, null, null, null, null, null, true, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_A_STATUS", "DOING", "WAITING_REVIEW", "OVERDUE"})
    void invalidAndLegacyStatusesReturnBadRequestWithoutCallingService(String statusValue) throws Exception {
        mockMvc.perform(boardRequest(PROJECT_ID).param("status", statusValue).with(manager()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void invalidPriorityReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(boardRequest(PROJECT_ID).param("priority", "CRITICAL").with(manager()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void invalidTypeReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(boardRequest(PROJECT_ID).param("type", "FEATURE").with(manager()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskBoardReadService);
    }

    @ParameterizedTest
    @MethodSource("malformedIdRequests")
    void malformedIdsReturnBadRequestWithoutCallingService(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(manager()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void labManagerCanReachProjectBoard() throws Exception {
        stubDefaultBoard();

        mockMvc.perform(boardRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isOk());
    }

    @Test
    void studentSystemRoleAllowsLeaderMembershipActorsToReachProjectBoard() throws Exception {
        stubDefaultBoard();

        mockMvc.perform(boardRequest(PROJECT_ID).with(user("leader").roles("STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestUsesExistingUnauthorizedSecurityResult() throws Exception {
        mockMvc.perform(boardRequest(PROJECT_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void unsupportedSystemRoleIsForbidden() throws Exception {
        mockMvc.perform(boardRequest(PROJECT_ID).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void wrongProjectOwnershipIsForbidden() throws Exception {
        when(taskBoardReadService.read(PROJECT_ID, null, null, null, null, null, false, false))
                .thenThrow(new AccessDeniedException("Cannot access tasks for this project"));

        mockMvc.perform(boardRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(taskBoardReadService).read(PROJECT_ID, null, null, null, null, null, false, false);
    }

    @Test
    void missingProjectIsNotFound() throws Exception {
        when(taskBoardReadService.read(PROJECT_ID, null, null, null, null, null, false, false))
                .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

        mockMvc.perform(boardRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        verify(taskBoardReadService).read(PROJECT_ID, null, null, null, null, null, false, false);
    }

    @Test
    void missingOrInactiveFilterResourceIsNotFound() throws Exception {
        when(taskBoardReadService.read(PROJECT_ID, 999L, null, null, null, null, false, false))
                .thenThrow(new ResourceNotFoundException("Research group", 999L));

        mockMvc.perform(boardRequest(PROJECT_ID).param("groupId", "999").with(manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        verify(taskBoardReadService).read(PROJECT_ID, 999L, null, null, null, null, false, false);
    }

    @Test
    void existingOutOfScopeFilterResourceIsForbidden() throws Exception {
        when(taskBoardReadService.read(PROJECT_ID, null, 999L, null, null, null, false, false))
                .thenThrow(new AccessDeniedException("Assignee is outside the requested project or task scope"));

        mockMvc.perform(boardRequest(PROJECT_ID).param("assigneeId", "999").with(manager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(taskBoardReadService).read(PROJECT_ID, null, 999L, null, null, null, false, false);
    }

    @Test
    void representativeExistingTaskRouteRemainsAvailableWithoutBoardCollision() throws Exception {
        when(taskService.getByMilestone(77L)).thenReturn(List.of());

        mockMvc.perform(apiGet("/milestones/{id}/tasks", 77L).with(manager()))
                .andExpect(status().isOk());

        verify(taskService).getByMilestone(77L);
        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void createResearchTaskReturnsCreatedWrapperAndDelegatesCanonicalRoute() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id(20L).projectId(PROJECT_ID).title("Official task")
                .status(TaskStatus.BACKLOG).priority(TaskPriority.MEDIUM).type(TaskType.TASK)
                .progressPercent(0).build();
        when(taskService.createResearchTask(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(apiPost("/research/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":123,\"title\":\"Official task\"}")
                        .with(manager()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(6)))
                .andExpect(jsonPath("$.data.id").value(20L))
                .andExpect(jsonPath("$.data.projectId").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.status").value("BACKLOG"));

        verify(taskService).createResearchTask(org.mockito.ArgumentMatchers.argThat(request ->
                PROJECT_ID.equals(request.getProjectId()) && "Official task".equals(request.getTitle())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "LEADER", "MEMBER", "ADMIN"})
    void createResearchTaskRejectsNonManagerRoles(String role) throws Exception {
        mockMvc.perform(apiPost("/research/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":123,\"title\":\"Official task\"}")
                        .with(user("actor").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskService);
    }

    @Test
    void createResearchTaskRequiresAuthenticationAndValidBody() throws Exception {
        mockMvc.perform(apiPost("/research/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":123,\"title\":\"Official task\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(apiPost("/research/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}")
                        .with(manager()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createResearchTaskRejectsInvalidEnumsAndMapsServiceErrors() throws Exception {
        mockMvc.perform(apiPost("/research/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":123,\"title\":\"Task\",\"priority\":\"CRITICAL\"}")
                        .with(manager()))
                .andExpect(status().isBadRequest());

        when(taskService.createResearchTask(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Group mismatch"));
        mockMvc.perform(apiPost("/research/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":123,\"title\":\"Task\"}")
                        .with(manager()))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.reset(taskService);
        doThrow(new ResourceNotFoundException("Project", PROJECT_ID))
                .when(taskService).createResearchTask(org.mockito.ArgumentMatchers.any());
        mockMvc.perform(apiPost("/research/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":123,\"title\":\"Task\"}")
                        .with(manager()))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyCreateRouteStillDelegatesToLegacyMethod() throws Exception {
        when(taskService.createTask(org.mockito.ArgumentMatchers.any())).thenReturn(
                TaskResponse.builder().id(30L).milestoneId(77L).title("Legacy").status(TaskStatus.TODO).build());

        mockMvc.perform(apiPost("/milestones/77/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Legacy\",\"assignedToStudentId\":7}")
                        .with(manager()))
                .andExpect(status().isCreated());

        verify(taskService).createTask(org.mockito.ArgumentMatchers.argThat(request ->
                Long.valueOf(77L).equals(request.getMilestoneId())));
    }

    @Test
    void patchResearchTaskReturnsOkWrapperAndDelegatesPresenceAwareRequest() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id(20L).projectId(PROJECT_ID).title("Updated task")
                .status(TaskStatus.BACKLOG).priority(TaskPriority.HIGH).type(TaskType.TASK)
                .progressPercent(0).build();
        when(taskService.patchResearchTask(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated task\",\"priority\":\"HIGH\"}")
                        .with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(6)))
                .andExpect(jsonPath("$.data.id").value(20L))
                .andExpect(jsonPath("$.data.title").value("Updated task"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"));

        verify(taskService).patchResearchTask(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.argThat(request -> request.isTitlePresent()
                        && request.isPriorityPresent()
                        && !request.isDescriptionPresent()));
    }

    @Test
    void patchResearchTaskAllowsStudentRoleThroughCoarseControllerBoundary() throws Exception {
        when(taskService.patchResearchTask(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any())).thenReturn(TaskResponse.builder().id(20L).title("Task").build());

        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":null}")
                        .with(user("leader").roles("STUDENT")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MEMBER", "LEADER"})
    void patchResearchTaskRejectsUnsupportedSystemRoles(String role) throws Exception {
        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Task\"}")
                        .with(user("actor").roles(role)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskService);
    }

    @Test
    void patchResearchTaskRequiresAuthentication() throws Exception {
        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Task\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taskService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\":\"DONE\"}",
            "{\"projectId\":123}",
            "{\"titel\":\"Typo\"}",
            "{\"title\":\"Valid\",\"status\":\"DONE\"}",
            "{}"
    })
    void patchResearchTaskMapsUnknownAndEmptyRequestErrorsToBadRequest(String body) throws Exception {
        when(taskService.patchResearchTask(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest request = invocation.getArgument(1);
            if (!request.getUnknownFields().isEmpty()) {
                throw new IllegalArgumentException("Unknown task patch fields: " + request.getUnknownFields());
            }
            if (!request.hasAnyRecognizedField()) {
                throw new IllegalArgumentException("Task patch request must contain at least one recognized field");
            }
            return TaskResponse.builder().id(20L).build();
        });

        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"priority\":\"CRITICAL\"}",
            "{\"type\":\"UNKNOWN\"}",
            "{\"dueDate\":\"not-a-date\"}"
    })
    void patchResearchTaskRejectsInvalidEnumAndDateJson(String body) throws Exception {
        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(manager()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void patchResearchTaskAppliesBeanSizeValidation() throws Exception {
        String body = "{\"title\":\"" + "t".repeat(201) + "\",\"description\":\""
                + "d".repeat(4001) + "\"}";

        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(manager()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void patchResearchTaskMapsServiceValidationPermissionAndMissingTaskErrors() throws Exception {
        when(taskService.patchResearchTask(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Title cannot be null"))
                .thenThrow(new AccessDeniedException("Cannot update task metadata"))
                .thenThrow(new ResourceNotFoundException("Task", 20L));

        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":null}").with(manager()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Task\"}").with(manager()))
                .andExpect(status().isForbidden());
        mockMvc.perform(apiPatch("/research/tasks/{taskId}", 20L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Task\"}").with(manager()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProjectBacklogReturnsExactWrapperShapeAndDefaultBindings() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 0, 20)).thenReturn(backlog(0, 20));

        mockMvc.perform(backlogRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(6)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("Project backlog retrieved successfully"))
                .andExpect(jsonPath("$.data", aMapWithSize(5)))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));

        verify(taskBoardReadService).readBacklog(PROJECT_ID, 0, 20);
    }

    @Test
    void explicitBacklogPaginationIsForwardedUnchanged() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 1, 50)).thenReturn(backlog(1, 50));

        mockMvc.perform(backlogRequest(PROJECT_ID).param("page", "1").param("size", "50").with(manager()))
                .andExpect(status().isOk());

        verify(taskBoardReadService).readBacklog(PROJECT_ID, 1, 50);
    }

    @Test
    void maximumBacklogPageSizeIsAccepted() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 0, 100)).thenReturn(backlog(0, 100));

        mockMvc.perform(backlogRequest(PROJECT_ID).param("size", "100").with(manager()))
                .andExpect(status().isOk());

        verify(taskBoardReadService).readBacklog(PROJECT_ID, 0, 100);
    }

    @ParameterizedTest
    @MethodSource("malformedBacklogRequests")
    void malformedBacklogValuesReturnBadRequestWithoutCallingService(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(manager()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskBoardReadService);
    }

    @ParameterizedTest
    @MethodSource("invalidBacklogPagination")
    void invalidBacklogPaginationUsesExistingBadRequestMapping(int page, int size) throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, page, size))
                .thenThrow(new IllegalArgumentException("Invalid pagination"));

        mockMvc.perform(backlogRequest(PROJECT_ID)
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size))
                        .with(manager()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(taskBoardReadService).readBacklog(PROJECT_ID, page, size);
    }

    @Test
    void labManagerCanReachProjectBacklog() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 0, 20)).thenReturn(backlog(0, 20));

        mockMvc.perform(backlogRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isOk());
    }

    @Test
    void studentSystemRoleAllowsLeaderAndMemberActorsToReachProjectBacklog() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 0, 20)).thenReturn(backlog(0, 20));

        mockMvc.perform(backlogRequest(PROJECT_ID).with(user("leader").roles("STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedBacklogRequestUsesExistingUnauthorizedResult() throws Exception {
        mockMvc.perform(backlogRequest(PROJECT_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void unsupportedSystemRoleCannotReachProjectBacklog() throws Exception {
        mockMvc.perform(backlogRequest(PROJECT_ID).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(taskBoardReadService);
    }

    @Test
    void backlogWrongScopeIsForbidden() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 0, 20))
                .thenThrow(new AccessDeniedException("Cannot access tasks for this project"));

        mockMvc.perform(backlogRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void backlogMissingProjectIsNotFound() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 0, 20))
                .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

        mockMvc.perform(backlogRequest(PROJECT_ID).with(manager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void unrelatedSortParameterCannotChangeBacklogDelegation() throws Exception {
        when(taskBoardReadService.readBacklog(PROJECT_ID, 1, 50)).thenReturn(backlog(1, 50));

        mockMvc.perform(backlogRequest(PROJECT_ID)
                        .param("page", "1")
                        .param("size", "50")
                        .param("sort", "priority,desc")
                        .with(manager()))
                .andExpect(status().isOk());

        verify(taskBoardReadService).readBacklog(PROJECT_ID, 1, 50);
    }

    private void stubDefaultBoard() {
        when(taskBoardReadService.read(PROJECT_ID, null, null, null, null, null, false, false))
                .thenReturn(board(TaskStatus.TODO));
    }

    private ProjectTaskBoardResponse board(TaskStatus status) {
        return ProjectTaskBoardResponse.builder()
                .projectId(PROJECT_ID)
                .columns(List.of(TaskBoardColumnResponse.builder().status(status).tasks(List.of()).build()))
                .build();
    }

    private TaskBacklogPageResponse backlog(int page, int size) {
        return new TaskBacklogPageResponse(List.of(), page, size, 0, 0);
    }

    private MockHttpServletRequestBuilder boardRequest(Object projectId) {
        return apiGet("/research/projects/{projectId}/board", projectId);
    }

    private MockHttpServletRequestBuilder backlogRequest(Object projectId) {
        return apiGet("/research/projects/{projectId}/backlog", projectId);
    }

    private MockHttpServletRequestBuilder apiGet(String path, Object... uriVariables) {
        return get("/api" + path, uriVariables).contextPath("/api");
    }

    private MockHttpServletRequestBuilder apiPost(String path, Object... uriVariables) {
        return post("/api" + path, uriVariables).contextPath("/api").with(csrf());
    }

    private MockHttpServletRequestBuilder apiPatch(String path, Object... uriVariables) {
        return patch("/api" + path, uriVariables).contextPath("/api").with(csrf());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor manager() {
        return user("manager").roles("LAB_MANAGER");
    }

    private static Stream<Arguments> explicitStatusForwardingCases() {
        return Stream.of(
                Arguments.of(TaskStatus.TODO, true, false),
                Arguments.of(TaskStatus.BACKLOG, false, false),
                Arguments.of(TaskStatus.CANCELLED, false, false)
        );
    }

    private static Stream<Arguments> malformedIdRequests() {
        return Stream.of(
                Arguments.of(get("/api/research/projects/not-a-number/board").contextPath("/api")),
                Arguments.of(get("/api/research/projects/123/board").contextPath("/api").param("groupId", "bad")),
                Arguments.of(get("/api/research/projects/123/board").contextPath("/api").param("assigneeId", "bad"))
        );
    }

    private static Stream<Arguments> malformedBacklogRequests() {
        return Stream.of(
                Arguments.of(get("/api/research/projects/not-a-number/backlog").contextPath("/api")),
                Arguments.of(get("/api/research/projects/123/backlog").contextPath("/api").param("page", "abc")),
                Arguments.of(get("/api/research/projects/123/backlog").contextPath("/api").param("size", "abc"))
        );
    }

    private static Stream<Arguments> invalidBacklogPagination() {
        return Stream.of(
                Arguments.of(-1, 20),
                Arguments.of(0, 0),
                Arguments.of(0, -1),
                Arguments.of(0, 101)
        );
    }
}
