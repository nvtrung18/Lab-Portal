package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.CreateResearchLogRequest;
import com.web.labportalbackend.research.dto.response.ResearchLogResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ResearchLogEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.ResearchLogType;
import com.web.labportalbackend.research.enums.ResearchLogVisibility;
import com.web.labportalbackend.research.mapper.ResearchLogMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ResearchLogRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.ResearchLogService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResearchLogServiceImpl implements ResearchLogService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ResearchLogRepository researchLogRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ResearchLogResponse> getProjectLogs(
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long taskId,
            Long authorId,
            ResearchLogType logType,
            Integer page,
            Integer size
    ) {
        ProjectEntity project = findProject(projectId);
        User currentUser = getCurrentUser();
        AccessScope accessScope = resolveAccessScope(currentUser, project);
        assertRequestedGroupAllowed(groupId, accessScope);

        PageRequest pageRequest = PageRequest.of(
                page == null || page < 0 ? DEFAULT_PAGE : page,
                Math.min(size == null || size <= 0 ? DEFAULT_SIZE : size, MAX_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<ResearchLogEntity> spec = buildSpecification(
                projectId,
                groupId,
                milestoneId,
                taskId,
                authorId,
                logType,
                currentUser,
                accessScope
        );
        return researchLogRepository.findAll(spec, pageRequest)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResearchLogResponse> getGroupLogs(
            Long groupId,
            Long milestoneId,
            Long taskId,
            Long authorId,
            ResearchLogType logType,
            Integer page,
            Integer size
    ) {
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        ProjectEntity project = resolveProjectForGroup(group);
        if (project == null) {
            throw new AccessDeniedException("Research group is not assigned to a project");
        }
        User currentUser = getCurrentUser();
        AccessScope accessScope = resolveAccessScope(currentUser, project);
        assertRequestedGroupAllowed(groupId, accessScope);

        PageRequest pageRequest = PageRequest.of(
                page == null || page < 0 ? DEFAULT_PAGE : page,
                Math.min(size == null || size <= 0 ? DEFAULT_SIZE : size, MAX_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<ResearchLogEntity> spec = buildSpecification(
                project.getId(),
                groupId,
                milestoneId,
                taskId,
                authorId,
                logType,
                currentUser,
                accessScope
        );
        return researchLogRepository.findAll(spec, pageRequest)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ResearchLogResponse createManualLog(CreateResearchLogRequest request) {
        ProjectEntity project = findProject(request.getProjectId());
        User currentUser = getCurrentUser();
        AccessScope accessScope = resolveAccessScope(currentUser, project);

        Long groupId = resolveGroupIdForCreate(request, project, accessScope);
        MilestoneEntity milestone = validateMilestone(request.getMilestoneId(), project.getId());
        TaskEntity task = validateTask(request.getTaskId(), project.getId(), milestone == null ? null : milestone.getId());
        validateGroup(groupId, project.getId());
        assertCanCreateLog(currentUser, accessScope, groupId, milestone, task);
        ResearchLogVisibility visibility = resolveAllowedVisibility(request.getVisibility(), accessScope, groupId);

        ResearchLogEntity log = ResearchLogEntity.builder()
                .projectId(project.getId())
                .groupId(groupId)
                .milestoneId(milestone == null ? null : milestone.getId())
                .taskId(task == null ? null : task.getId())
                .authorId(currentUser.getId())
                .authorName(currentUser.getFullName())
                .logType(ResearchLogType.MANUAL)
                .workDate(request.getWorkDate())
                .durationMinutes(request.getDurationMinutes() == null ? 0 : request.getDurationMinutes())
                .content(request.getContent().trim())
                .result(trimToNull(request.getResult()))
                .problem(trimToNull(request.getProblem()))
                .nextPlan(trimToNull(request.getNextPlan()))
                .evidenceLink(normalizeEvidenceLink(request.getEvidenceLink()))
                .visibility(visibility)
                .build();

        return toResponse(researchLogRepository.save(log));
    }

    @Override
    @Transactional
    public void createSystemLog(
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long taskId,
            Long authorId,
            String content,
            String result,
            ResearchLogVisibility visibility
    ) {
        if (projectId == null || authorId == null || !StringUtils.hasText(content)) {
            return;
        }
        try {
            User author = userRepository.findById(authorId).orElse(null);
            ResearchLogEntity log = ResearchLogEntity.builder()
                    .projectId(projectId)
                    .groupId(groupId)
                    .milestoneId(milestoneId)
                    .taskId(taskId)
                    .authorId(authorId)
                    .authorName(author == null ? null : author.getFullName())
                    .logType(ResearchLogType.SYSTEM)
                    .workDate(LocalDate.now())
                    .durationMinutes(0)
                    .content(content.trim())
                    .result(trimToNull(result))
                    .visibility(visibility == null ? ResearchLogVisibility.GROUP : visibility)
                    .build();
            researchLogRepository.save(log);
        } catch (RuntimeException ex) {
            log.warn("Cannot create research system log for project {}", projectId, ex);
        }
    }

    private Specification<ResearchLogEntity> buildSpecification(
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long taskId,
            Long authorId,
            ResearchLogType logType,
            User currentUser,
            AccessScope accessScope
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));
            predicates.add(cb.isFalse(root.get("deleted")));
            predicates.add(cb.isTrue(root.get("active")));
            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
            }
            if (milestoneId != null) {
                predicates.add(cb.equal(root.get("milestoneId"), milestoneId));
            }
            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            if (authorId != null) {
                predicates.add(cb.equal(root.get("authorId"), authorId));
            }
            if (logType != null) {
                predicates.add(cb.equal(root.get("logType"), logType));
            }
            if (!accessScope.manager()) {
                List<Predicate> visibilityPredicates = new ArrayList<>();
                visibilityPredicates.add(cb.equal(root.get("authorId"), currentUser.getId()));
                if (!accessScope.leaderGroupIds().isEmpty()) {
                    visibilityPredicates.add(cb.and(
                            root.get("groupId").in(accessScope.leaderGroupIds()),
                            root.get("visibility").in(ResearchLogVisibility.GROUP, ResearchLogVisibility.PROJECT)
                    ));
                }
                if (!accessScope.memberGroupIds().isEmpty()) {
                    visibilityPredicates.add(cb.and(
                            root.get("groupId").in(accessScope.memberGroupIds()),
                            cb.equal(root.get("visibility"), ResearchLogVisibility.GROUP)
                    ));
                }
                predicates.add(cb.or(visibilityPredicates.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ResearchLogResponse toResponse(ResearchLogEntity log) {
        GroupEntity group = log.getGroupId() == null
                ? null
                : groupRepository.findByIdAndDeletedFalseAndActiveTrue(log.getGroupId()).orElse(null);
        MilestoneEntity milestone = log.getMilestoneId() == null
                ? null
                : milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(log.getMilestoneId()).orElse(null);
        TaskEntity task = log.getTaskId() == null
                ? null
                : taskRepository.findById(log.getTaskId()).orElse(null);
        User author = userRepository.findById(log.getAuthorId()).orElse(null);
        GroupRole groupRole = log.getGroupId() == null
                ? groupMemberRepository.findActiveRoleByProjectIdAndUserId(log.getProjectId(), log.getAuthorId()).orElse(null)
                : groupMemberRepository.findActiveRoleByGroupIdAndUserId(log.getGroupId(), log.getAuthorId()).orElse(null);
        return ResearchLogMapper.toResponse(
                log,
                group,
                milestone,
                task,
                author == null ? null : resolvePrimaryRole(author),
                groupRole
        );
    }

    private AccessScope resolveAccessScope(User currentUser, ProjectEntity project) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerCanAccessProject(currentUser, project);
            return new AccessScope(true, List.of(), List.of());
        }
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Cannot access research logs");
        }
        List<Long> groupIds = groupMemberRepository.findActiveGroupIdsByProjectIdAndUserId(project.getId(), currentUser.getId());
        if (groupIds.isEmpty()) {
            throw new AccessDeniedException("Cannot access logs for this project");
        }
        List<Long> leaderGroupIds = new ArrayList<>();
        List<Long> memberGroupIds = new ArrayList<>();
        for (Long groupId : groupIds) {
            GroupRole role = groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, currentUser.getId())
                    .orElse(GroupRole.MEMBER);
            if (role == GroupRole.LEADER) {
                leaderGroupIds.add(groupId);
            } else {
                memberGroupIds.add(groupId);
            }
        }
        return new AccessScope(false, leaderGroupIds, memberGroupIds);
    }

    private void assertCanCreateLog(
            User currentUser,
            AccessScope accessScope,
            Long groupId,
            MilestoneEntity milestone,
            TaskEntity task
    ) {
        if (accessScope.manager()) {
            return;
        }
        if (groupId == null || !accessScope.allGroupIds().contains(groupId)) {
            throw new AccessDeniedException("Cannot create logs for another group");
        }
        if (accessScope.leaderGroupIds().contains(groupId)) {
            return;
        }
        if (task != null && currentUser.getId().equals(task.getAssigneeId())) {
            return;
        }
        if (milestone != null
                && milestone.getAssignedToStudent() != null
                && currentUser.getId().equals(milestone.getAssignedToStudent().getId())) {
            return;
        }
        throw new AccessDeniedException("Members may create logs only for their assigned task or milestone");
    }

    private ResearchLogVisibility resolveAllowedVisibility(
            ResearchLogVisibility requestedVisibility,
            AccessScope accessScope,
            Long groupId
    ) {
        ResearchLogVisibility visibility = requestedVisibility == null
                ? ResearchLogVisibility.GROUP
                : requestedVisibility;
        if (accessScope.manager()) {
            if (visibility != ResearchLogVisibility.PROJECT) {
                throw new AccessDeniedException("Managers may create project-visible research logs only");
            }
            return visibility;
        }
        if (groupId != null && accessScope.leaderGroupIds().contains(groupId)) {
            if (visibility != ResearchLogVisibility.GROUP) {
                throw new AccessDeniedException("Leaders may create group-visible research logs only");
            }
            return visibility;
        }
        if (visibility == ResearchLogVisibility.PROJECT) {
            throw new AccessDeniedException("Members may create private or group-visible research logs only");
        }
        return visibility;
    }

    private Long resolveGroupIdForCreate(CreateResearchLogRequest request, ProjectEntity project, AccessScope accessScope) {
        if (request.getGroupId() != null) {
            assertRequestedGroupAllowed(request.getGroupId(), accessScope);
            return request.getGroupId();
        }
        if (!accessScope.manager() && accessScope.allGroupIds().size() == 1) {
            return accessScope.allGroupIds().get(0);
        }
        if (project.getGroup() != null) {
            return project.getGroup().getId();
        }
        return null;
    }

    private void assertRequestedGroupAllowed(Long groupId, AccessScope accessScope) {
        if (groupId == null || accessScope.manager()) {
            return;
        }
        if (!accessScope.allGroupIds().contains(groupId)) {
            throw new AccessDeniedException("Cannot access logs for another group");
        }
    }

    private void validateGroup(Long groupId, Long projectId) {
        if (groupId == null) {
            return;
        }
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        if (group.getProject() == null || !projectId.equals(group.getProject().getId())) {
            throw new IllegalArgumentException("Research group does not belong to selected project");
        }
    }

    private MilestoneEntity validateMilestone(Long milestoneId, Long projectId) {
        if (milestoneId == null) {
            return null;
        }
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        if (!projectId.equals(milestone.getProject().getId())) {
            throw new IllegalArgumentException("Milestone does not belong to selected project");
        }
        return milestone;
    }

    private TaskEntity validateTask(Long taskId, Long projectId, Long requestMilestoneId) {
        if (taskId == null) {
            return null;
        }
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        MilestoneEntity taskMilestone = validateMilestone(task.getMilestoneId(), projectId);
        if (requestMilestoneId != null && !requestMilestoneId.equals(taskMilestone.getId())) {
            throw new IllegalArgumentException("Task does not belong to selected milestone");
        }
        return task;
    }

    private ProjectEntity findProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
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

    private void assertManagerCanAccessProject(User currentUser, ProjectEntity project) {
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (project.getLab() == null || !managedLab.getId().equals(project.getLab().getId())) {
            throw new AccessDeniedException("Cannot access logs from another lab");
        }
    }

    private String normalizeEvidenceLink(String evidenceLink) {
        if (!StringUtils.hasText(evidenceLink)) {
            return null;
        }
        String value = evidenceLink.trim();
        try {
            URI uri = URI.create(value);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Evidence link is invalid");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolvePrimaryRole(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }

    private record AccessScope(boolean manager, List<Long> leaderGroupIds, List<Long> memberGroupIds) {
        private List<Long> allGroupIds() {
            List<Long> groupIds = new ArrayList<>(leaderGroupIds);
            groupIds.addAll(memberGroupIds);
            return groupIds;
        }
    }
}
