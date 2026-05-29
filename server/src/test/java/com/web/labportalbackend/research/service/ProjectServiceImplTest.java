package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.request.CreateResearchProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.ProjectServiceImpl;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProject_savesProjectWhenGroupExistsAndDatesAreValid() {
        User manager = authenticateManager();
        Laboratory lab = lab(1L);
        GroupEntity group = group(10L, lab);
        CreateProjectRequest request = createRequest(10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1));

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(group));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
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
        assertSame(manager, captor.getValue().getCreatedBy());
        assertSame(manager, captor.getValue().getManager());
    }

    @Test
    void createProject_throwsResourceNotFoundWhenGroupDoesNotExist() {
        authenticateStudent();
        CreateProjectRequest request = createRequest(99L, LocalDate.of(2026, 1, 1), null);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.createProject(request));

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
        User student = authenticateStudent();
        Laboratory lab = lab(1L);
        GroupEntity group = group(10L, lab);
        ProjectEntity project = project(20L, group, "Project A");

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(group));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(student.getId(), lab.getId()))
                .thenReturn(true);
        when(projectRepository.findByGroupIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(List.of(project));

        List<ProjectResponse> responses = projectService.getByGroup(10L);

        assertEquals(1, responses.size());
        assertEquals(20L, responses.get(0).getId());
        assertEquals(10L, responses.get(0).getGroupId());
        verify(projectRepository).findByGroupIdAndDeletedFalseAndActiveTrue(10L);
    }

    @Test
    void getByLab_returnsProjectsForStudentWithActiveMembership() {
        User student = authenticateStudent();
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Lab Project")
                .status(ProjectStatus.ONGOING)
                .build();
        project.setId(20L);

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(lab));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(student.getId(), lab.getId()))
                .thenReturn(true);
        when(projectRepository.findByLabIdAndDeletedFalseAndActiveTrue(1L)).thenReturn(List.of(project));

        List<ProjectResponse> responses = projectService.getByLab(1L);

        assertEquals(1, responses.size());
        assertEquals(20L, responses.get(0).getId());
    }

    @Test
    void updateResearchProject_updatesProjectForManagerOfSameLab() {
        User manager = authenticateManager();
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Original title")
                .status(ProjectStatus.DRAFT)
                .build();
        project.setId(20L);
        CreateResearchProjectRequest request = researchRequest(1L);
        request.setTitle("Updated research project");

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(projectRepository.save(project)).thenReturn(project);

        ProjectResponse response = projectService.updateResearchProject(20L, request);

        assertEquals("Updated research project", response.getTitle());
        assertEquals(LocalDate.of(2026, 3, 15), project.getEndDate());
        verify(projectRepository).save(project);
    }

    @Test
    void updateResearchProject_deniesManagerOfAnotherLab() {
        User manager = authenticateManager();
        Laboratory projectLab = lab(1L);
        Laboratory managedLab = lab(2L);
        ProjectEntity project = ProjectEntity.builder()
                .lab(projectLab)
                .title("Protected project")
                .status(ProjectStatus.DRAFT)
                .build();
        project.setId(20L);

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(managedLab));

        assertThrows(AccessDeniedException.class, () -> projectService.updateResearchProject(20L, researchRequest(1L)));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void updateResearchProject_deniesChangingProjectLabThroughPayload() {
        User manager = authenticateManager();
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Protected project")
                .status(ProjectStatus.DRAFT)
                .build();
        project.setId(20L);

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        assertThrows(AccessDeniedException.class, () -> projectService.updateResearchProject(20L, researchRequest(2L)));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void getByLab_deniesStudentWithoutActiveMembership() {
        User student = authenticateStudent();
        Laboratory lab = lab(1L);

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(lab));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(student.getId(), lab.getId()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> projectService.getByLab(1L));
        verify(projectRepository, never()).findByLabIdAndDeletedFalseAndActiveTrue(any());
    }

    @Test
    void getDetail_returnsProjectWhenFound() {
        User student = authenticateStudent();
        Laboratory lab = lab(1L);
        GroupEntity group = group(10L, lab);
        ProjectEntity project = project(20L, group, "Project Detail");

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(student.getId(), lab.getId()))
                .thenReturn(true);

        ProjectDetailResponse response = projectService.getDetail(20L);

        assertEquals(20L, response.getId());
        assertEquals(10L, response.getGroupId());
        assertEquals("Project Detail", response.getTitle());
    }

    @Test
    void getDetail_returnsProjectForActiveResearchGroupMemberWithoutLabMembership() {
        User student = authenticateStudent();
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Group Project")
                .status(ProjectStatus.ONGOING)
                .build();
        project.setId(20L);
        GroupEntity group = group(10L, lab);
        group.setProject(project);
        GroupMemberEntity member = GroupMemberEntity.builder().group(group).user(student).build();

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(student.getId(), lab.getId()))
                .thenReturn(false);
        when(groupMemberRepository.findByUserIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(student.getId()))
                .thenReturn(List.of(member));

        ProjectDetailResponse response = projectService.getDetail(20L);

        assertEquals(20L, response.getId());
        assertEquals("Group Project", response.getTitle());
    }

    @Test
    void getDetail_throwsResourceNotFoundWhenMissing() {
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.getDetail(20L));
    }

    private User authenticateStudent() {
        User user = new User();
        user.setId(2L);
        user.setUsername("student01");
        user.setEmail("student01@uet.edu.vn");
        user.addRole(new Role("STUDENT", "Student"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of())
        );
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        return user;
    }

    private User authenticateManager() {
        User user = new User();
        user.setId(3L);
        user.setUsername("manager01");
        user.setEmail("manager01@uet.edu.vn");
        user.addRole(new Role("LAB_MANAGER", "Lab manager"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of())
        );
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        return user;
    }

    private Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        lab.setLabName("AI Research Lab");
        return lab;
    }

    private GroupEntity group(Long id, Laboratory lab) {
        GroupEntity group = new GroupEntity();
        group.setId(id);
        group.setLab(lab);
        return group;
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

    private CreateResearchProjectRequest researchRequest(Long labId) {
        CreateResearchProjectRequest request = new CreateResearchProjectRequest();
        request.setLabId(labId);
        request.setTitle("Updated title");
        request.setDescription("Updated description");
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setExpectedEndDate(LocalDate.of(2026, 3, 15));
        request.setStatus(ProjectStatus.ONGOING);
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
