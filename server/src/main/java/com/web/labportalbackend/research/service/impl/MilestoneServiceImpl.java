package com.web.labportalbackend.research.service.impl;

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
import com.web.labportalbackend.research.mapper.MilestoneMapper;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.MilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MilestoneResponse createMilestone(CreateMilestoneRequest request) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));
        User currentUser = getCurrentUser();
        assertManagerOwnsProject(currentUser, project);
        assertProjectAllowsMilestones(project);
        assertSupportedStatus(request.getStatus());
        validateProgress(request.getStatus(), request.getProgressPercent());
        if (request.getTitle().trim().length() < 3) {
            throw new IllegalArgumentException("Milestone title must contain at least 3 non-whitespace characters");
        }

        User assignedStudent = resolveAssignedStudent(request.getAssignedToStudentId(), project);
        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(project)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .assignedToStudent(assignedStudent)
                .deadline(request.getDeadline())
                .status(request.getStatus() != null ? request.getStatus() : MilestoneStatus.NOT_STARTED)
                .progressPercent(request.getProgressPercent() != null ? request.getProgressPercent() : 0)
                .evidenceUrl(request.getEvidenceUrl())
                .managerComment(request.getManagerComment())
                .createdBy(currentUser)
                .build();

        return MilestoneMapper.toResponse(milestoneRepository.save(milestone));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneResponse> getByProject(Long projectId) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        assertCanViewMilestones(getCurrentUser(), project);

        return milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(projectId)
                .stream()
                .map(MilestoneMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneResponse getDetail(Long milestoneId) {
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        assertCanViewMilestones(getCurrentUser(), milestone.getProject());
        return MilestoneMapper.toResponse(milestone);
    }

    @Override
    @Transactional
    public MilestoneResponse updateMilestone(Long milestoneId, UpdateMilestoneRequest request) {
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        User currentUser = getCurrentUser();
        ProjectEntity project = milestone.getProject();
        assertManagerOwnsProject(currentUser, project);
        assertSupportedStatus(request.getStatus());
        validateProgress(request.getStatus(), request.getProgressPercent());
        if (request.getTitle().trim().length() < 3) {
            throw new IllegalArgumentException("Milestone title must contain at least 3 non-whitespace characters");
        }

        User assignedStudent = resolveAssignedStudent(request.getAssignedToStudentId(), project);
        milestone.setTitle(request.getTitle().trim());
        milestone.setDescription(request.getDescription());
        milestone.setAssignedToStudent(assignedStudent);
        milestone.setDeadline(request.getDeadline());
        milestone.setStatus(request.getStatus() != null ? request.getStatus() : MilestoneStatus.NOT_STARTED);
        milestone.setProgressPercent(request.getProgressPercent() != null ? request.getProgressPercent() : 0);
        milestone.setEvidenceUrl(request.getEvidenceUrl());
        milestone.setManagerComment(request.getManagerComment());

        return MilestoneMapper.toResponse(milestoneRepository.save(milestone));
    }

    private void assertProjectAllowsMilestones(ProjectEntity project) {
        if (project.getStatus() == ProjectStatus.CANCELLED || project.getStatus() == ProjectStatus.COMPLETED) {
            throw new IllegalStateException("Cannot create milestones for a completed or cancelled project");
        }
    }

    private void assertSupportedStatus(MilestoneStatus status) {
        if (status == MilestoneStatus.PLANNED || status == MilestoneStatus.DELAYED) {
            throw new IllegalArgumentException("Unsupported milestone status");
        }
    }

    private void validateProgress(MilestoneStatus status, Integer progressPercent) {
        if (status == MilestoneStatus.COMPLETED && !Integer.valueOf(100).equals(progressPercent)) {
            throw new IllegalArgumentException("Completed milestones must have 100 percent progress");
        }
    }

    private User resolveAssignedStudent(Long assignedToStudentId, ProjectEntity project) {
        if (assignedToStudentId == null) {
            return null;
        }

        User student = userRepository.findById(assignedToStudentId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assignedToStudentId));
        boolean activeInLab = membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                assignedToStudentId,
                project.getLab().getId()
        );
        boolean activeInProjectGroup = groupMemberRepository.existsActiveMemberByProjectIdAndUserId(
                project.getId(),
                assignedToStudentId
        );
        if (!student.hasRole("STUDENT") || (!activeInLab && !activeInProjectGroup)) {
            throw new AccessDeniedException("Assignee must be an active student member of this project or lab");
        }
        return student;
    }

    private void assertCanViewMilestones(User currentUser, ProjectEntity project) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsProject(currentUser, project);
            return;
        }

        if (currentUser.hasRole("STUDENT") &&
                groupMemberRepository.existsActiveMemberByProjectIdAndUserId(project.getId(), currentUser.getId())) {
            return;
        }

        throw new AccessDeniedException("Cannot access milestones for this project");
    }

    private void assertManagerOwnsProject(User currentUser, ProjectEntity project) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can manage milestones");
        }

        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (project.getLab() == null || !managedLab.getId().equals(project.getLab().getId())) {
            throw new AccessDeniedException("Cannot manage milestones for another lab");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }
}
