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
import com.web.labportalbackend.research.dto.request.CreateResearchTaskRequest;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class OfficialTaskCreationTransactionIntegrationTest {

    @Autowired TaskService taskService;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockitoSpyBean TaskRepository taskRepository;
    @MockitoSpyBean AuditLogService auditLogService;

    @AfterEach
    void cleanSecurityAndSpies() {
        SecurityContextHolder.clearContext();
        reset(taskRepository, auditLogService);
    }

    @Test
    void taskAndAuditCommitTogetherOnSuccess() {
        ProjectEntity project = fixture("success");
        long tasksBefore = taskRepository.count();
        long auditsBefore = auditLogRepository.count();

        taskService.createResearchTask(request(project.getId(), "Committed task"));

        assertEquals(tasksBefore + 1, taskRepository.count());
        assertEquals(auditsBefore + 1, auditLogRepository.count());
        assertEquals(AuditAction.CREATE_RESEARCH_TASK,
                auditLogRepository.findAll().stream()
                        .filter(log -> "RESEARCH_TASK".equals(log.getTargetType()))
                        .filter(log -> "Created official research task: Committed task".equals(log.getDescription()))
                        .findFirst().orElseThrow().getAction());
    }

    @Test
    void auditRuntimeFailureRollsBackTaskAndAudit() {
        ProjectEntity project = fixture("audit-failure");
        long tasksBefore = taskRepository.count();
        long auditsBefore = auditLogRepository.count();
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditLogService).log(any(), any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class,
                () -> taskService.createResearchTask(request(project.getId(), "Rolled back task")));

        assertEquals(tasksBefore, taskRepository.count());
        assertEquals(auditsBefore, auditLogRepository.count());
    }

    @Test
    void taskSaveFailureDoesNotCommitAudit() {
        ProjectEntity project = fixture("save-failure");
        long tasksBefore = taskRepository.count();
        long auditsBefore = auditLogRepository.count();
        doThrow(new IllegalStateException("task save failed")).when(taskRepository).save(any());

        assertThrows(RuntimeException.class,
                () -> taskService.createResearchTask(request(project.getId(), "Unsaved task")));

        assertEquals(tasksBefore, taskRepository.count());
        assertEquals(auditsBefore, auditLogRepository.count());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    private ProjectEntity fixture(String suffix) {
        Role managerRole = roleRepository.findByName("LAB_MANAGER")
                .orElseGet(() -> roleRepository.save(new Role("LAB_MANAGER", "Lab manager")));
        User manager = new User();
        manager.setUsername("manager-" + suffix);
        manager.setEmail("manager-" + suffix + "@example.test");
        manager.setPassword("password");
        manager.addRole(managerRole);
        manager = userRepository.save(manager);

        Laboratory lab = new Laboratory();
        lab.setLabName("Lab " + suffix);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(manager);
        lab = laboratoryRepository.save(lab);

        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project " + suffix).build();
        project = projectRepository.save(project);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager.getUsername(), null, List.of()));
        return project;
    }

    private CreateResearchTaskRequest request(Long projectId, String title) {
        CreateResearchTaskRequest request = new CreateResearchTaskRequest();
        request.setProjectId(projectId);
        request.setTitle(title);
        return request;
    }
}
