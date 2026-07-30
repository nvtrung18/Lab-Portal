package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskAuditAction;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.repository.TaskActivityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class TaskMetadataPatchTransactionIntegrationTest {

    @Autowired TaskService taskService;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockitoSpyBean TaskRepository taskRepository;
    @MockitoSpyBean TaskActivityRepository taskActivityRepository;
    @MockitoSpyBean AuditLogService auditLogService;

    @AfterEach
    void cleanSecurityAndSpies() {
        SecurityContextHolder.clearContext();
        reset(taskRepository, taskActivityRepository, auditLogService);
    }

    @Test
    void taskMetadataAndAuditCommitTogether() {
        TaskEntity task = fixture("patch-success");
        long auditsBefore = auditLogRepository.count();
        long activitiesBefore = taskActivityRepository.count();
        PatchResearchTaskRequest request = request("Committed title");

        taskService.patchResearchTask(task.getId(), request);

        TaskEntity committed = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals("Committed title", committed.getTitle());
        assertEquals(TaskPriority.HIGH, committed.getPriority());
        assertEquals(auditsBefore + 1, auditLogRepository.count());
        assertEquals(activitiesBefore + 1, taskActivityRepository.count());
        var activity = taskActivityRepository.findAll().stream()
                .filter(row -> task.getId().equals(row.getTaskId())).reduce((first, second) -> second).orElseThrow();
        assertEquals(userRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow().getId(), activity.getUserId());
        assertEquals(TaskAuditAction.TASK_METADATA_UPDATED, activity.getAction());
        assertEquals(snapshot(task, "Original title", TaskPriority.MEDIUM), activity.getOldValue());
        assertEquals(snapshot(task, "Committed title", TaskPriority.HIGH), activity.getNewValue());
        var audit = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.UPDATE_RESEARCH_TASK)
                .filter(log -> task.getId().equals(log.getTargetId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(audit.getMetadataJson());
    }

    @Test
    void auditRuntimeFailureRollsBackAllMetadata() {
        TaskEntity task = fixture("patch-audit-failure");
        long auditsBefore = auditLogRepository.count();
        long activitiesBefore = taskActivityRepository.count();
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditLogService).log(any(), any(), any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class,
                () -> taskService.patchResearchTask(task.getId(),
                        request("Rolled back title")));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals("Original title", reloaded.getTitle());
        assertEquals(TaskPriority.MEDIUM, reloaded.getPriority());
        assertEquals(TaskStatus.BACKLOG, reloaded.getStatus());
        assertEquals(auditsBefore, auditLogRepository.count());
        assertEquals(activitiesBefore, taskActivityRepository.count());
    }

    @Test
    void taskSaveFailureDoesNotCommitAudit() {
        TaskEntity task = fixture("patch-save-failure");
        long auditsBefore = auditLogRepository.count();
        long activitiesBefore = taskActivityRepository.count();
        doThrow(new IllegalStateException("task save failed")).when(taskRepository).save(any(TaskEntity.class));

        assertThrows(RuntimeException.class,
                () -> taskService.patchResearchTask(task.getId(), request("Unsaved title")));

        assertEquals("Original title", taskRepository.findById(task.getId()).orElseThrow().getTitle());
        assertEquals(TaskPriority.MEDIUM, taskRepository.findById(task.getId()).orElseThrow().getPriority());
        assertEquals(auditsBefore, auditLogRepository.count());
        assertEquals(activitiesBefore, taskActivityRepository.count());
        verify(taskActivityRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void activitySaveFailureRollsBackTaskActivityAndAudit() {
        TaskEntity task = fixture("patch-activity-failure");
        long auditsBefore = auditLogRepository.count();
        long activitiesBefore = taskActivityRepository.count();
        doThrow(new IllegalStateException("activity save failed"))
                .when(taskActivityRepository).save(any());

        assertThrows(RuntimeException.class,
                () -> taskService.patchResearchTask(task.getId(), request("Rolled back activity")));

        assertEquals("Original title", taskRepository.findById(task.getId()).orElseThrow().getTitle());
        assertEquals(auditsBefore, auditLogRepository.count());
        assertEquals(activitiesBefore, taskActivityRepository.count());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void pureNoOpKeepsPersistedTimestampAndSkipsSaveAndAudit() {
        TaskEntity task = fixture("patch-no-op");
        var updatedAtBefore = taskRepository.findById(task.getId()).orElseThrow().getUpdatedAt();
        long auditsBefore = auditLogRepository.count();
        long activitiesBefore = taskActivityRepository.count();
        clearInvocations(taskRepository, auditLogService, taskActivityRepository);
        PatchResearchTaskRequest noOp = new PatchResearchTaskRequest();
        noOp.setTitle("  Original title  ");
        noOp.setPriority(TaskPriority.MEDIUM);

        taskService.patchResearchTask(task.getId(), noOp);

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals("Original title", reloaded.getTitle());
        assertEquals(updatedAtBefore, reloaded.getUpdatedAt());
        assertEquals(auditsBefore, auditLogRepository.count());
        assertEquals(activitiesBefore, taskActivityRepository.count());
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(taskActivityRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void managerWithTwoLabsIsAuthorizedAgainstTheTaskTargetLab() {
        TaskEntity task = fixture("patch-multi-lab");
        String managerUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User manager = userRepository.findByUsername(managerUsername).orElseThrow();
        Laboratory secondLab = new Laboratory();
        secondLab.setLabName("Other managed lab");
        secondLab.setLocation("Room 2");
        secondLab.setCapacity(12);
        secondLab.setManager(manager);
        laboratoryRepository.save(secondLab);
        clearInvocations(taskRepository, auditLogService);

        taskService.patchResearchTask(task.getId(), request("Updated in target lab"));

        assertEquals("Updated in target lab", taskRepository.findById(task.getId()).orElseThrow().getTitle());
        verify(taskRepository).save(any(TaskEntity.class));
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    private TaskEntity fixture(String suffix) {
        Role managerRole = roleRepository.findByName("LAB_MANAGER")
                .orElseGet(() -> roleRepository.save(new Role("LAB_MANAGER", "Lab manager")));
        User manager = new User();
        manager.setUsername("patch-manager-" + suffix);
        manager.setEmail("patch-manager-" + suffix + "@example.test");
        manager.setPassword("password");
        manager.addRole(managerRole);
        manager = userRepository.save(manager);

        Laboratory lab = new Laboratory();
        lab.setLabName("Patch Lab " + suffix);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(manager);
        lab = laboratoryRepository.save(lab);

        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Patch Project " + suffix).build();
        project = projectRepository.save(project);
        TaskEntity task = TaskEntity.builder()
                .projectId(project.getId())
                .title("Original title")
                .status(TaskStatus.BACKLOG)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .progressPercent(0)
                .build();
        task = taskRepository.save(task);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager.getUsername(), null, List.of()));
        return task;
    }

    private PatchResearchTaskRequest request(String title) {
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setTitle(title);
        request.setPriority(TaskPriority.HIGH);
        return request;
    }

    private String snapshot(TaskEntity task, String title, TaskPriority priority) {
        return "{\"schemaVersion\":1,\"projectId\":" + task.getProjectId()
                + ",\"title\":\"" + title + "\",\"status\":\"BACKLOG\",\"priority\":\""
                + priority + "\",\"type\":\"TASK\",\"progressPercent\":0}";
    }
}
