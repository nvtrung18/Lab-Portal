package com.web.labportalbackend.research.service;

import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Test
    void createProject_savesProjectWhenGroupExistsAndDatesAreValid() {
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        CreateProjectRequest request = createRequest(10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        when(groupRepository.existsById(10L)).thenReturn(true);
        when(groupRepository.getReferenceById(10L)).thenReturn(group);
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> {
            ProjectEntity project = invocation.getArgument(0);
            project.setId(20L);
            return project;
        });

        ProjectResponse response = projectService.createProject(request);

        assertEquals(20L, response.getId());
        assertEquals(10L, response.getGroupId());
        assertEquals("Lab Automation Study", response.getTitle());
        assertEquals(ProjectStatus.PLANNED, response.getStatus());

        ArgumentCaptor<ProjectEntity> captor = ArgumentCaptor.forClass(ProjectEntity.class);
        verify(projectRepository).save(captor.capture());
        assertSame(group, captor.getValue().getGroup());
    }

    @Test
    void createProject_throwsResourceNotFoundWhenGroupDoesNotExist() {
        CreateProjectRequest request = createRequest(99L, LocalDate.of(2026, 1, 1), null);
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> projectService.createProject(request));

        verify(groupRepository, never()).getReferenceById(any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void createProject_throwsIllegalArgumentWhenStartDateAfterEndDate() {
        CreateProjectRequest request = createRequest(10L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1));

        assertThrows(IllegalArgumentException.class, () -> projectService.createProject(request));

        verifyNoInteractions(groupRepository, projectRepository);
    }

    @Test
    void getByGroup_returnsMappedProjects() {
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        ProjectEntity project = project(20L, group, "Project A");

        when(groupRepository.existsById(10L)).thenReturn(true);
        when(projectRepository.findByGroupId(10L)).thenReturn(List.of(project));

        List<ProjectResponse> responses = projectService.getByGroup(10L);

        assertEquals(1, responses.size());
        assertEquals(20L, responses.get(0).getId());
        assertEquals(10L, responses.get(0).getGroupId());
        verify(projectRepository).findByGroupId(10L);
    }

    @Test
    void getDetail_returnsProjectWhenFound() {
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        ProjectEntity project = project(20L, group, "Project Detail");

        when(projectRepository.findById(20L)).thenReturn(Optional.of(project));

        ProjectDetailResponse response = projectService.getDetail(20L);

        assertEquals(20L, response.getId());
        assertEquals(10L, response.getGroupId());
        assertEquals("Project Detail", response.getTitle());
    }

    @Test
    void getDetail_throwsResourceNotFoundWhenMissing() {
        when(projectRepository.findById(20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.getDetail(20L));
    }

    private CreateProjectRequest createRequest(Long groupId, LocalDate startDate, LocalDate endDate) {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setGroupId(groupId);
        request.setTitle("Lab Automation Study");
        request.setDescription("Research automation workflows");
        request.setStatus(ProjectStatus.PLANNED);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    private ProjectEntity project(Long id, GroupEntity group, String title) {
        ProjectEntity project = ProjectEntity.builder()
                .group(group)
                .title(title)
                .description("Description")
                .status(ProjectStatus.ONGOING)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 2, 1))
                .build();
        project.setId(id);
        return project;
    }
}
