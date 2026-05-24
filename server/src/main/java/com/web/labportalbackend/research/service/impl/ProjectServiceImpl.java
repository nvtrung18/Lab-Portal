package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.mapper.ProjectMapper;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        validateDateRange(request);

        User currentUser = getCurrentUser();
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Research group", request.getGroupId()));
        assertCanCreateInLab(currentUser, group.getLab());

        ProjectEntity project = ProjectEntity.builder()
                .group(group)
                .topic(group.getTopic())
                .code(request.getCode())
                .title(request.getTitle())
                .description(request.getDescription())
                .objective(request.getObjective())
                .status(request.getStatus() != null ? request.getStatus() : ProjectStatus.DRAFT)
                .startDate(request.getStartDate())
                .endDate(resolveEndDate(request))
                .priority(request.getPriority())
                .requiredProducts(request.getRequiredProducts())
                .evaluationCriteria(request.getEvaluationCriteria())
                .manager(currentUser)
                .createdBy(currentUser)
                .build();

        return ProjectMapper.toResponse(projectRepository.save(project));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getByGroup(Long groupId) {
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        assertCanAccessLab(getCurrentUser(), group.getLab());

        return projectRepository.findByGroupIdAndDeletedFalseAndActiveTrue(groupId)
                .stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse getDetail(Long projectId) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        assertCanAccessLab(getCurrentUser(), project.getGroup().getLab());
        return ProjectMapper.toDetailResponse(project);
    }

    private void validateDateRange(CreateProjectRequest request) {
        LocalDate endDate = resolveEndDate(request);
        if (request.getStartDate() != null && endDate != null && !endDate.isAfter(request.getStartDate())) {
            throw new IllegalArgumentException("Expected end date must be after start date");
        }
    }

    private LocalDate resolveEndDate(CreateProjectRequest request) {
        return request.getExpectedEndDate() != null ? request.getExpectedEndDate() : request.getEndDate();
    }

    private void assertCanCreateInLab(User currentUser, Laboratory lab) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can create research projects");
        }
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot create research projects for another lab");
        }
    }

    private void assertCanAccessLab(User currentUser, Laboratory lab) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
            if (!managedLab.getId().equals(lab.getId())) {
                throw new AccessDeniedException("Cannot access research projects from another lab");
            }
            return;
        }

        // TODO: Khi hoàn thiện group membership, STUDENT nên được kiểm tra là member/leader của group.
        // Hiện tại phạm vi ngày 31 cho phép theo membership ACTIVE của PTN chứa group.
        if (currentUser.hasRole("STUDENT") &&
                membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                        currentUser.getId(),
                        lab.getId()
                )) {
            return;
        }

        throw new AccessDeniedException("Cannot access research projects for this lab");
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
