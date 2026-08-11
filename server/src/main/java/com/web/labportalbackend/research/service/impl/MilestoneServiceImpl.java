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
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.mapper.MilestoneMapper;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
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

    private static final String APPROVED_REPORT_REQUIRED =
            "Cần có báo cáo được duyệt trước khi hoàn thành nhiệm vụ/mốc nghiên cứu.";

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TaskRepository taskRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MilestoneResponse createMilestone(CreateMilestoneRequest request) {
        if (request.getProjectId() == null && request.getGroupId() != null) {
            GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(request.getGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Research group", request.getGroupId()));
            ProjectEntity groupProject = resolveProjectForGroup(group);
            if (groupProject != null) {
                request.setProjectId(groupProject.getId());
            }
        }
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("Project ID is required");
        }

        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));
        GroupEntity group = resolveGroupForMilestone(request.getGroupId(), project);
        User currentUser = getCurrentUser();
        assertManagerOwnsProject(currentUser, project);
        assertProjectAllowsMilestones(project);
        assertSupportedStatus(request.getStatus());
        validateProgress(request.getStatus(), request.getProgressPercent());
        if (request.getStatus() == MilestoneStatus.COMPLETED) {
            throw new IllegalArgumentException(APPROVED_REPORT_REQUIRED);
        }
        if (request.getTitle().trim().length() < 3) {
            throw new IllegalArgumentException("Milestone title must contain at least 3 non-whitespace characters");
        }

        User assignedStudent = resolveAssignedStudent(request.getAssignedToStudentId(), project);
        if (assignedStudent != null && group != null) {
            boolean activeInGroup = groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(
                    group.getId(),
                    assignedStudent.getId()
            );
            if (!activeInGroup) {
                throw new AccessDeniedException("Assignee must be an active student member of this research group");
            }
        }
        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(project)
                .group(group)
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
        User currentUser = getCurrentUser();
        GroupRole groupRole = assertCanViewMilestones(currentUser, project);

        return milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(projectId)
                .stream()
                .filter(milestone -> groupRole != GroupRole.MEMBER || isMemberMilestone(milestone, currentUser.getId()))
                .map(MilestoneMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneResponse> getByGroup(Long groupId) {
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        ProjectEntity project = resolveProjectForGroup(group);
        if (project == null) {
            throw new AccessDeniedException("Research group is not assigned to a project");
        }
        User currentUser = getCurrentUser();
        GroupRole groupRole;
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsProject(currentUser, project);
            groupRole = null;
        } else if (currentUser.hasRole("STUDENT")) {
            groupRole = groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Cannot access milestones for this group"));
        } else {
            throw new AccessDeniedException("Cannot access milestones");
        }

        return milestoneRepository.findByGroupIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(groupId)
                .stream()
                .filter(milestone -> groupRole != GroupRole.MEMBER || isMemberMilestone(milestone, currentUser.getId()))
                .map(MilestoneMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneResponse> getMyMilestonesInGroup(Long groupId) {
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        ProjectEntity project = resolveProjectForGroup(group);
        if (project == null) {
            throw new AccessDeniedException("Research group is not assigned to a project");
        }
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT") || !isMemberOfGroup(currentUser.getId(), groupId)) {
            throw new AccessDeniedException("Cannot access milestones for this group");
        }

        return milestoneRepository.findByGroupIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(groupId)
                .stream()
                .filter(milestone -> isMemberMilestone(milestone, currentUser.getId()))
                .map(milestone -> {
                    int myTaskCount = taskRepository.countByMilestoneIdAndAssigneeId(milestone.getId(), currentUser.getId());
                    int myCompletedTaskCount = taskRepository.countDoneByMilestoneIdAndAssigneeId(milestone.getId(), currentUser.getId());
                    return MilestoneMapper.toResponse(milestone, myTaskCount, myCompletedTaskCount);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MilestoneResponse getDetail(Long milestoneId) {
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        User currentUser = getCurrentUser();
        GroupRole groupRole = assertCanViewMilestones(currentUser, milestone.getProject());
        if (groupRole == GroupRole.MEMBER && !isMemberMilestone(milestone, currentUser.getId())) {
            throw new AccessDeniedException("Members may view only milestones assigned to them or containing their tasks");
        }
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
        if (request.getStatus() == MilestoneStatus.COMPLETED) {
            assertCanCompleteMilestone(milestoneId);
        }
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

    private GroupEntity resolveGroupForMilestone(Long groupId, ProjectEntity project) {
        if (groupId == null) {
            return project.getGroup();
        }

        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        ProjectEntity groupProject = resolveProjectForGroup(group);
        if (groupProject == null || !project.getId().equals(groupProject.getId())) {
            throw new IllegalArgumentException("Research group does not belong to selected project");
        }
        return group;
    }

    private ProjectEntity resolveProjectForGroup(GroupEntity group) {
        if (group.getProject() != null) {
            return group.getProject();
        }
        return projectRepository.findByGroupIdAndDeletedFalseAndActiveTrue(group.getId())
                .stream()
                .findFirst()
                .orElse(null);
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

    private void assertCanCompleteMilestone(Long milestoneId) {
        if (!reportRepository.existsApprovedByMilestoneId(milestoneId)
                || reportRepository.existsLatestUnapprovedByMilestoneId(milestoneId)
                || taskRepository.existsIncompleteTaskByMilestoneId(milestoneId)
                || taskRepository.existsTaskWithoutApprovedReportByMilestoneId(milestoneId)) {
            throw new IllegalArgumentException(APPROVED_REPORT_REQUIRED);
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

    private GroupRole assertCanViewMilestones(User currentUser, ProjectEntity project) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsProject(currentUser, project);
            return null;
        }

        if (currentUser.hasRole("STUDENT")) {
            return groupMemberRepository.findActiveRoleByProjectIdAndUserId(project.getId(), currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Cannot access milestones for this project"));
        }

        throw new AccessDeniedException("Cannot access milestones for this project");
    }

    private boolean isMemberMilestone(MilestoneEntity milestone, Long userId) {
        return (milestone.getAssignedToStudent() != null && userId.equals(milestone.getAssignedToStudent().getId()))
                || taskRepository.existsByMilestoneIdAndAssigneeIdAndDeletedFalseAndActiveTrue(milestone.getId(), userId);
    }

    private boolean isMemberOfGroup(Long userId, Long groupId) {
        return groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(groupId, userId);
    }

    @SuppressWarnings("unused")
    private boolean isLeaderOfGroup(Long userId, Long groupId) {
        return groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, userId)
                .filter(role -> role == GroupRole.LEADER)
                .isPresent();
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
