package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
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
import com.web.labportalbackend.research.mapper.ProjectMapper;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
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
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        validateDateRange(request);

        User currentUser = getCurrentUser();
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Research group", request.getGroupId()));
        assertCanCreateInLab(currentUser, group.getLab());

        ProjectEntity project = ProjectEntity.builder()
                .lab(group.getLab())
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

        ProjectEntity saved = projectRepository.save(project);
        auditLogService.log(
                currentUser,
                AuditAction.CREATE_RESEARCH_PROJECT,
                AuditModule.RESEARCH,
                "RESEARCH_PROJECT",
                saved.getId(),
                displayName(currentUser) + " đã tạo đề tài nghiên cứu " + saved.getTitle() + "."
        );
        return ProjectMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponse createResearchProject(CreateResearchProjectRequest request) {
        validateDateRange(request.getStartDate(), request.getExpectedEndDate());

        User currentUser = getCurrentUser();
        Laboratory lab = laboratoryRepository.findById(request.getLabId())
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", request.getLabId()));
        assertCanCreateInLab(currentUser, lab);

        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .code(request.getCode())
                .title(request.getTitle())
                .researchDirection(request.getResearchDirection())
                .description(request.getDescription())
                .objective(request.getObjective())
                .status(request.getStatus() != null ? request.getStatus() : ProjectStatus.DRAFT)
                .startDate(request.getStartDate())
                .endDate(request.getExpectedEndDate())
                .priority(request.getPriority())
                .requiredProducts(request.getRequiredProducts())
                .evaluationCriteria(request.getEvaluationCriteria())
                .manager(currentUser)
                .createdBy(currentUser)
                .build();

        ProjectEntity saved = projectRepository.save(project);
        auditLogService.log(
                currentUser,
                AuditAction.CREATE_RESEARCH_PROJECT,
                AuditModule.RESEARCH,
                "RESEARCH_PROJECT",
                saved.getId(),
                displayName(currentUser) + " đã tạo đề tài nghiên cứu " + saved.getTitle() + "."
        );
        return ProjectMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponse updateResearchProject(Long projectId, CreateResearchProjectRequest request) {
        validateDateRange(request.getStartDate(), request.getExpectedEndDate());

        User currentUser = getCurrentUser();
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        Laboratory lab = resolveProjectLab(project);
        assertCanCreateInLab(currentUser, lab);
        if (!lab.getId().equals(request.getLabId())) {
            throw new AccessDeniedException("Cannot move research projects to another lab");
        }

        project.setCode(request.getCode());
        project.setTitle(request.getTitle());
        project.setResearchDirection(request.getResearchDirection());
        project.setDescription(request.getDescription());
        project.setObjective(request.getObjective());
        project.setStatus(request.getStatus() != null ? request.getStatus() : ProjectStatus.DRAFT);
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getExpectedEndDate());
        project.setPriority(request.getPriority());
        project.setRequiredProducts(request.getRequiredProducts());
        project.setEvaluationCriteria(request.getEvaluationCriteria());

        ProjectEntity saved = projectRepository.save(project);
        auditLogService.log(
                currentUser,
                AuditAction.UPDATE_RESEARCH_PROJECT,
                AuditModule.RESEARCH,
                "RESEARCH_PROJECT",
                saved.getId(),
                displayName(currentUser) + " đã cập nhật đề tài nghiên cứu " + saved.getTitle() + "."
        );
        return ProjectMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getByLab(Long labId) {
        Laboratory lab = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", labId));
        assertCanAccessLab(getCurrentUser(), lab);

        return projectRepository.findByLabIdAndDeletedFalseAndActiveTrue(labId)
                .stream()
                .map(ProjectMapper::toResponse)
                .toList();
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
        assertCanAccessProject(getCurrentUser(), project);
        return ProjectMapper.toDetailResponse(project);
    }

    private void validateDateRange(CreateProjectRequest request) {
        LocalDate endDate = resolveEndDate(request);
        validateDateRange(request.getStartDate(), endDate);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("Expected end date must be after start date");
        }
    }

    private LocalDate resolveEndDate(CreateProjectRequest request) {
        return request.getExpectedEndDate() != null ? request.getExpectedEndDate() : request.getEndDate();
    }

    private Laboratory resolveProjectLab(ProjectEntity project) {
        return project.getLab() != null ? project.getLab() : project.getGroup().getLab();
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

        if (currentUser.hasRole("STUDENT") &&
                membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                        currentUser.getId(),
                        lab.getId()
                )) {
            return;
        }

        throw new AccessDeniedException("Cannot access research projects for this lab");
    }

    private void assertCanAccessProject(User currentUser, ProjectEntity project) {
        Laboratory lab = resolveProjectLab(project);
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertCanAccessLab(currentUser, lab);
            return;
        }

        if (currentUser.hasRole("STUDENT")) {
            if (membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                    currentUser.getId(), lab.getId())) {
                return;
            }

            boolean belongsToProjectGroup = groupMemberRepository
                    .findByUserIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(currentUser.getId())
                    .stream()
                    .map(GroupMemberEntity::getGroup)
                    .anyMatch(group -> (group.getProject() != null && project.getId().equals(group.getProject().getId()))
                            || (project.getGroup() != null && project.getGroup().getId().equals(group.getId())));
            if (belongsToProjectGroup) {
                return;
            }
        }

        throw new AccessDeniedException("Cannot access this research project");
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName();
    }
}
