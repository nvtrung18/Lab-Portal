package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.research.dto.request.CreateMilestoneRequest;
import com.web.labportalbackend.research.dto.request.UpdateMilestoneRequest;
import com.web.labportalbackend.research.dto.response.MilestoneResponse;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.MilestoneServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
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

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MilestoneServiceImpl milestoneService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createMilestone_savesMilestoneForManagerOfProjectLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        ProjectEntity project = project(10L, lab, ProjectStatus.ONGOING);
        CreateMilestoneRequest request = request(10L);

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(project));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(milestoneRepository.save(any(MilestoneEntity.class))).thenAnswer(invocation -> {
            MilestoneEntity milestone = invocation.getArgument(0);
            milestone.setId(20L);
            return milestone;
        });

        MilestoneResponse response = milestoneService.createMilestone(request);

        assertEquals(20L, response.getId());
        assertEquals(10L, response.getProjectId());
        assertEquals("Khảo sát mô hình nhận diện khuôn mặt", response.getTitle());
        assertEquals(MilestoneStatus.NOT_STARTED, response.getStatus());
        assertEquals(0, response.getProgressPercent());

        ArgumentCaptor<MilestoneEntity> captor = ArgumentCaptor.forClass(MilestoneEntity.class);
        verify(milestoneRepository).save(captor.capture());
        assertSame(manager, captor.getValue().getCreatedBy());
        assertEquals(LocalDate.of(2026, 6, 10), captor.getValue().getDeadline());
    }

    @Test
    void createMilestone_savesDocumentFieldsWhenProvided() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        ProjectEntity project = project(10L, lab, ProjectStatus.ONGOING);
        User student = student(12L);
        CreateMilestoneRequest request = request(10L);
        request.setAssignedToStudentId(12L);
        request.setEvidenceUrl("https://github.com/uet/survey");
        request.setManagerComment("Ưu tiên so sánh mô hình theo độ chính xác.");

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(project));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(userRepository.findById(12L)).thenReturn(Optional.of(student));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(12L, 1L))
                .thenReturn(true);
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, 12L)).thenReturn(false);
        when(milestoneRepository.save(any(MilestoneEntity.class))).thenAnswer(invocation -> {
            MilestoneEntity milestone = invocation.getArgument(0);
            milestone.setId(20L);
            return milestone;
        });

        MilestoneResponse response = milestoneService.createMilestone(request);

        assertEquals(12L, response.getAssignedToStudentId());
        assertEquals("https://github.com/uet/survey", response.getEvidenceUrl());
        assertEquals("Ưu tiên so sánh mô hình theo độ chính xác.", response.getManagerComment());
    }

    @Test
    void createMilestone_deniesStudent() {
        authenticate("student", "STUDENT");
        Laboratory lab = lab(1L);
        ProjectEntity project = project(10L, lab, ProjectStatus.ONGOING);
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(project));

        assertThrows(AccessDeniedException.class, () -> milestoneService.createMilestone(request(10L)));
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void createMilestone_deniesManagerForAnotherLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory projectLab = lab(1L);
        Laboratory managedLab = lab(2L);
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(project(10L, projectLab, ProjectStatus.ONGOING)));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(managedLab));

        assertThrows(AccessDeniedException.class, () -> milestoneService.createMilestone(request(10L)));
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void createMilestone_rejectsCompletedProject() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(project(10L, lab, ProjectStatus.COMPLETED)));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        assertThrows(IllegalStateException.class, () -> milestoneService.createMilestone(request(10L)));
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void createMilestone_rejectsTitleWithOnlyWhitespace() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        CreateMilestoneRequest request = request(10L);
        request.setTitle("   ");
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(project(10L, lab, ProjectStatus.ONGOING)));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        assertThrows(IllegalArgumentException.class, () -> milestoneService.createMilestone(request));
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void getByProject_returnsMilestonesForActiveStudentGroupMember() {
        User student = authenticate("student", "STUDENT");
        Laboratory lab = lab(1L);
        ProjectEntity project = project(10L, lab, ProjectStatus.ONGOING);
        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(project)
                .title("Mốc 1")
                .deadline(LocalDate.of(2026, 6, 10))
                .status(MilestoneStatus.IN_PROGRESS)
                .progressPercent(35)
                .build();
        milestone.setId(20L);

        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, student.getId())).thenReturn(true);
        when(milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(10L))
                .thenReturn(List.of(milestone));

        List<MilestoneResponse> result = milestoneService.getByProject(10L);

        assertEquals(1, result.size());
        assertEquals("Mốc 1", result.get(0).getTitle());
        assertEquals(35, result.get(0).getProgressPercent());
    }

    @Test
    void getByProject_deniesStudentOutsideProjectGroup() {
        User student = authenticate("student", "STUDENT");
        Laboratory lab = lab(1L);
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(project(10L, lab, ProjectStatus.ONGOING)));
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, student.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> milestoneService.getByProject(10L));
        verify(milestoneRepository, never())
                .findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(any());
    }

    @Test
    void getByProject_throwsWhenProjectMissing() {
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> milestoneService.getByProject(10L));
    }

    @Test
    void getDetail_returnsMilestoneForManagerOfProjectLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(20L, project(10L, lab, ProjectStatus.ONGOING));

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        MilestoneResponse result = milestoneService.getDetail(20L);

        assertEquals(20L, result.getId());
        assertEquals(10L, result.getProjectId());
        assertEquals("Face Recognition", result.getProjectTitle());
    }

    @Test
    void getDetail_returnsMilestoneForActiveStudentGroupMember() {
        User student = authenticate("student", "STUDENT");
        MilestoneEntity milestone = milestone(20L, project(10L, lab(1L), ProjectStatus.ONGOING));

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, student.getId())).thenReturn(true);

        MilestoneResponse result = milestoneService.getDetail(20L);

        assertEquals(20L, result.getId());
        assertEquals(MilestoneStatus.IN_PROGRESS, result.getStatus());
    }

    @Test
    void getDetail_deniesStudentOutsideProjectGroup() {
        User student = authenticate("student", "STUDENT");
        MilestoneEntity milestone = milestone(20L, project(10L, lab(1L), ProjectStatus.ONGOING));

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, student.getId())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> milestoneService.getDetail(20L));
    }

    @Test
    void updateMilestone_updatesDocumentFieldsForManagerOfProjectLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        ProjectEntity project = project(10L, lab, ProjectStatus.ONGOING);
        MilestoneEntity milestone = milestone(20L, project);
        User student = student(12L);
        UpdateMilestoneRequest request = updateRequest(12L);

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(userRepository.findById(12L)).thenReturn(Optional.of(student));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(12L, 1L))
                .thenReturn(true);
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, 12L)).thenReturn(false);
        when(milestoneRepository.save(milestone)).thenReturn(milestone);

        MilestoneResponse result = milestoneService.updateMilestone(20L, request);

        assertEquals("Prototype integration", result.getTitle());
        assertEquals(12L, result.getAssignedToStudentId());
        assertEquals("Student Twelve", result.getAssignedToStudentName());
        assertEquals("https://github.com/uet/prototype", result.getEvidenceUrl());
        assertEquals("Bổ sung kết quả thử nghiệm.", result.getManagerComment());
        assertEquals(MilestoneStatus.IN_PROGRESS, result.getStatus());
        assertEquals(40, result.getProgressPercent());
        verify(milestoneRepository).save(milestone);
    }

    @Test
    void updateMilestone_deniesManagerForAnotherLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        MilestoneEntity milestone = milestone(20L, project(10L, lab(1L), ProjectStatus.ONGOING));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab(2L)));

        assertThrows(AccessDeniedException.class, () -> milestoneService.updateMilestone(20L, updateRequest(null)));
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void updateMilestone_deniesAssigneeOutsideLabAndProjectGroup() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(20L, project(10L, lab, ProjectStatus.ONGOING));
        User student = student(12L);

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(userRepository.findById(12L)).thenReturn(Optional.of(student));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(12L, 1L))
                .thenReturn(false);
        when(groupMemberRepository.existsActiveMemberByProjectIdAndUserId(10L, 12L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> milestoneService.updateMilestone(20L, updateRequest(12L)));
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void updateMilestone_deniesStudent() {
        authenticate("student", "STUDENT");
        MilestoneEntity milestone = milestone(20L, project(10L, lab(1L), ProjectStatus.ONGOING));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));

        assertThrows(AccessDeniedException.class, () -> milestoneService.updateMilestone(20L, updateRequest(null)));
        verify(milestoneRepository, never()).save(any());
    }

    @Test
    void updateMilestone_requiresFullProgressWhenCompleted() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(20L, project(10L, lab, ProjectStatus.ONGOING));
        UpdateMilestoneRequest request = updateRequest(null);
        request.setStatus(MilestoneStatus.COMPLETED);

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        assertThrows(IllegalArgumentException.class, () -> milestoneService.updateMilestone(20L, request));
        verify(milestoneRepository, never()).save(any());
    }

    private User authenticate(String username, String roleName) {
        User user = new User();
        user.setId(roleName.equals("LAB_MANAGER") ? 2L : 3L);
        user.setUsername(username);
        user.setEmail(username + "@uet.edu.vn");
        user.addRole(new Role(roleName, roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of())
        );
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        return lab;
    }

    private ProjectEntity project(Long id, Laboratory lab, ProjectStatus status) {
        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Face Recognition")
                .status(status)
                .build();
        project.setId(id);
        return project;
    }

    private MilestoneEntity milestone(Long id, ProjectEntity project) {
        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(project)
                .title("Milestone detail")
                .status(MilestoneStatus.IN_PROGRESS)
                .progressPercent(40)
                .build();
        milestone.setId(id);
        return milestone;
    }

    private User student(Long id) {
        User student = new User();
        student.setId(id);
        student.setFullName("Student Twelve");
        student.setEmail("student12@uet.edu.vn");
        student.addRole(new Role("STUDENT", "STUDENT"));
        return student;
    }

    private UpdateMilestoneRequest updateRequest(Long assignedStudentId) {
        UpdateMilestoneRequest request = new UpdateMilestoneRequest();
        request.setTitle("Prototype integration");
        request.setDescription("Hoàn thiện bản tích hợp thử nghiệm.");
        request.setAssignedToStudentId(assignedStudentId);
        request.setDeadline(LocalDate.of(2026, 7, 10));
        request.setStatus(MilestoneStatus.IN_PROGRESS);
        request.setProgressPercent(40);
        request.setEvidenceUrl("https://github.com/uet/prototype");
        request.setManagerComment("Bổ sung kết quả thử nghiệm.");
        return request;
    }

    private CreateMilestoneRequest request(Long projectId) {
        CreateMilestoneRequest request = new CreateMilestoneRequest();
        request.setProjectId(projectId);
        request.setTitle("Khảo sát mô hình nhận diện khuôn mặt");
        request.setDescription("Tìm hiểu FaceNet, ArcFace và các mô hình liên quan.");
        request.setDeadline(LocalDate.of(2026, 6, 10));
        request.setStatus(MilestoneStatus.NOT_STARTED);
        request.setProgressPercent(0);
        return request;
    }
}
