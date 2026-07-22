package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.CreateResearchLogRequest;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ResearchLogEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.ResearchLogVisibility;
import com.web.labportalbackend.research.repository.*;
import com.web.labportalbackend.research.service.impl.ResearchLogServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResearchLogNullMilestoneCompatibilityTest {

    @Mock ResearchLogRepository researchLogRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock TaskRepository taskRepository;
    @Mock LaboratoryRepository laboratoryRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ResearchLogServiceImpl service;

    private CreateResearchLogRequest request;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        User manager = new User();
        manager.setId(2L);
        manager.setUsername("manager");
        manager.addRole(new Role("LAB_MANAGER", "manager"));
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(50L);
        TaskEntity task = TaskEntity.builder().projectId(50L).milestoneId(null).title("Backlog").build();
        task.setId(20L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null, List.of()));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(2L)).thenReturn(Optional.of(lab));
        when(taskRepository.findById(20L)).thenReturn(Optional.of(task));
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        lenient().when(researchLogRepository.save(any(ResearchLogEntity.class))).thenAnswer(invocation -> {
            ResearchLogEntity log = invocation.getArgument(0);
            log.setId(30L);
            return log;
        });

        request = new CreateResearchLogRequest();
        request.setProjectId(50L);
        request.setTaskId(20L);
        request.setWorkDate(LocalDate.of(2026, 7, 22));
        request.setContent("Worked on backlog");
        request.setVisibility(ResearchLogVisibility.PROJECT);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void taskAndRequestWithoutMilestoneRemainSupported() {
        assertEquals(20L, service.createManualLog(request).getTaskId());
        verify(milestoneRepository, never()).findByIdAndDeletedFalseAndActiveTrue(any());
    }

    @Test
    void nullMilestoneTaskCannotBeComparedToRequestedMilestone() {
        request.setMilestoneId(10L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createManualLog(request));

        assertEquals("Task without a milestone cannot be linked to a milestone log", error.getMessage());
        verify(researchLogRepository, never()).save(any());
    }
}
