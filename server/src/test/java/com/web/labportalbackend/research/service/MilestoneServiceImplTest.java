package com.web.labportalbackend.research.service;

import com.web.labportalbackend.common.exception.InvalidDateRangeException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateMilestoneRequest;
import com.web.labportalbackend.research.dto.response.MilestoneResponse;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.MilestoneServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceImplTest {

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private MilestoneServiceImpl milestoneService;

    @Test
    void createMilestone_savesMilestoneWhenProjectExistsAndDatesAreValid() {
        ProjectEntity project = project(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        CreateMilestoneRequest request = request(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(milestoneRepository.save(any(MilestoneEntity.class))).thenAnswer(invocation -> {
            MilestoneEntity milestone = invocation.getArgument(0);
            milestone.setId(20L);
            return milestone;
        });

        MilestoneResponse response = milestoneService.createMilestone(request);

        assertEquals(20L, response.getId());
        assertEquals(10L, response.getProjectId());
        assertEquals("Literature Review", response.getName());
        assertEquals(MilestoneStatus.PLANNED, response.getStatus());

        ArgumentCaptor<MilestoneEntity> captor = ArgumentCaptor.forClass(MilestoneEntity.class);
        verify(milestoneRepository).save(captor.capture());
        assertSame(project, captor.getValue().getProject());
    }

    @Test
    void createMilestone_throwsResourceNotFoundWhenProjectMissing() {
        CreateMilestoneRequest request = request(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        when(projectRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> milestoneService.createMilestone(request));

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void createMilestone_throwsInvalidDateRangeWhenStartAfterEnd() {
        ProjectEntity project = project(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        CreateMilestoneRequest request = request(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(InvalidDateRangeException.class, () -> milestoneService.createMilestone(request));

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void createMilestone_throwsInvalidDateRangeWhenOutsideProjectRange() {
        ProjectEntity project = project(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));
        CreateMilestoneRequest request = request(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 1));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        assertThrows(InvalidDateRangeException.class, () -> milestoneService.createMilestone(request));

        verify(milestoneRepository, never()).save(any());
    }

    private ProjectEntity project(LocalDate startDate, LocalDate endDate) {
        ProjectEntity project = new ProjectEntity();
        project.setId(10L);
        project.setStartDate(startDate);
        project.setEndDate(endDate);
        return project;
    }

    private CreateMilestoneRequest request(LocalDate startDate, LocalDate endDate) {
        CreateMilestoneRequest request = new CreateMilestoneRequest();
        request.setProjectId(10L);
        request.setName("Literature Review");
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setStatus(MilestoneStatus.PLANNED);
        return request;
    }
}
