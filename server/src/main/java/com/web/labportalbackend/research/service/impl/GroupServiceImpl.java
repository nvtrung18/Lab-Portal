package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.DuplicateMemberException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.research.dto.request.AddMemberRequest;
import com.web.labportalbackend.research.dto.request.CreateGroupRequest;
import com.web.labportalbackend.research.dto.response.GroupMemberResponse;
import com.web.labportalbackend.research.dto.response.GroupResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ResearchTopicEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.GroupStatus;
import com.web.labportalbackend.research.mapper.GroupMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ResearchTopicRepository;
import com.web.labportalbackend.research.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final ResearchTopicRepository topicRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        User currentUser = getCurrentUser();
        if (request.getProjectId() != null) {
            return createProjectGroup(request, currentUser);
        }

        ResearchTopicEntity topic = topicRepository.findByIdAndDeletedFalseAndActiveTrue(request.getTopicId())
                .orElseThrow(() -> new ResourceNotFoundException("Research topic", request.getTopicId()));
        Laboratory lab = topic.getLab();
        if (!lab.getId().equals(request.getLabId())) {
            throw new IllegalArgumentException("Research topic does not belong to selected lab");
        }

        assertCanCreateInLab(currentUser, lab);
        assertLabCanHostResearch(lab);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .topic(topic)
                .name(request.getName())
                .description(request.getDescription())
                .objective(request.getObjective())
                .plan(request.getPlan())
                .status(request.getStatus() != null ? request.getStatus() : GroupStatus.ACTIVE)
                .leader(currentUser)
                .build();
        GroupEntity savedGroup = groupRepository.save(group);

        GroupMemberEntity leaderMember = GroupMemberEntity.builder()
                .group(savedGroup)
                .user(currentUser)
                .role(GroupRole.LEADER)
                .joinedAt(Instant.now())
                .build();
        GroupMemberEntity savedLeaderMember = groupMemberRepository.save(leaderMember);
        savedGroup.getMembers().add(savedLeaderMember);

        return GroupMapper.toResponse(savedGroup);
    }

    private GroupResponse createProjectGroup(CreateGroupRequest request, User currentUser) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));
        Laboratory lab = project.getLab();

        assertCanCreateInLab(currentUser, lab);
        assertLabCanHostResearch(lab);
        validateProjectGroupMembers(request, lab.getId());

        User leader = userRepository.findById(request.getLeaderStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getLeaderStudentId()));

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .topic(project.getTopic())
                .project(project)
                .name(request.getName())
                .description(request.getDescription())
                .objective(request.getObjective())
                .plan(request.getPlan())
                .status(request.getStatus() != null ? request.getStatus() : GroupStatus.ACTIVE)
                .leader(leader)
                .build();
        GroupEntity savedGroup = groupRepository.save(group);

        for (Long memberId : new LinkedHashSet<>(request.getMemberIds())) {
            User member = userRepository.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", memberId));
            GroupMemberEntity groupMember = GroupMemberEntity.builder()
                    .group(savedGroup)
                    .user(member)
                    .role(memberId.equals(request.getLeaderStudentId()) ? GroupRole.LEADER : GroupRole.MEMBER)
                    .joinedAt(Instant.now())
                    .build();
            GroupMemberEntity savedMember = groupMemberRepository.save(groupMember);
            savedGroup.getMembers().add(savedMember);
        }

        return GroupMapper.toResponse(savedGroup);
    }

    @Override
    @Transactional
    public GroupMemberResponse addMember(Long groupId, AddMemberRequest request) {
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getUserId())) {
            throw new DuplicateMemberException(groupId, request.getUserId());
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));

        GroupMemberEntity member = GroupMemberEntity.builder()
                .group(group)
                .user(user)
                .role(GroupRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        return GroupMapper.toMemberResponse(groupMemberRepository.save(member));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getByLab(Long labId) {
        Laboratory lab = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", labId));
        assertCanAccessLab(getCurrentUser(), lab);

        return groupRepository.findByLabIdAndDeletedFalseAndActiveTrue(labId)
                .stream()
                .map(group -> GroupMapper.toResponse(
                        group,
                        projectRepository.countByGroupIdAndDeletedFalseAndActiveTrue(group.getId())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getByTopic(Long topicId) {
        ResearchTopicEntity topic = topicRepository.findByIdAndDeletedFalseAndActiveTrue(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Research topic", topicId));
        assertCanAccessLab(getCurrentUser(), topic.getLab());

        return groupRepository.findByTopicIdAndDeletedFalseAndActiveTrue(topicId)
                .stream()
                .map(group -> GroupMapper.toResponse(
                        group,
                        projectRepository.countByGroupIdAndDeletedFalseAndActiveTrue(group.getId())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getByProject(Long projectId) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        assertCanAccessLab(getCurrentUser(), project.getLab());

        return groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(projectId)
                .stream()
                .map(group -> GroupMapper.toResponse(
                        group,
                        projectRepository.countByGroupIdAndDeletedFalseAndActiveTrue(group.getId())
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups() {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students can view their research groups");
        }

        return groupMemberRepository
                .findByUserIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(currentUser.getId())
                .stream()
                .map(GroupMemberEntity::getGroup)
                .map(group -> GroupMapper.toResponse(
                        group,
                        projectRepository.countByGroupIdAndDeletedFalseAndActiveTrue(group.getId())
                ))
                .toList();
    }

    private void validateProjectGroupMembers(CreateGroupRequest request, Long labId) {
        if (request.getLeaderStudentId() == null) {
            throw new IllegalArgumentException("Leader student ID is required");
        }
        if (request.getMemberIds() == null || request.getMemberIds().isEmpty()) {
            throw new IllegalArgumentException("At least one group member is required");
        }

        Set<Long> memberIds = new LinkedHashSet<>(request.getMemberIds());
        if (!memberIds.contains(request.getLeaderStudentId())) {
            throw new IllegalArgumentException("Leader student must be included in member IDs");
        }

        for (Long memberId : memberIds) {
            User member = userRepository.findById(memberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", memberId));
            if (!member.hasRole("STUDENT")) {
                throw new AccessDeniedException("Only students can be added to research groups");
            }
            if (!membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(memberId, labId)) {
                throw new AccessDeniedException("Student is not an active member of this lab");
            }
        }
    }

    private void assertCanCreateInLab(User currentUser, Laboratory lab) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can create research groups");
        }
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot create research groups for another lab");
        }
    }

    private void assertCanAccessLab(User currentUser, Laboratory lab) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
            if (!managedLab.getId().equals(lab.getId())) {
                throw new AccessDeniedException("Cannot access research groups from another lab");
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

        throw new AccessDeniedException("Cannot access research groups for this lab");
    }

    private void assertLabCanHostResearch(Laboratory lab) {
        if (lab.getStatus() == LabStatus.INACTIVE || lab.getStatus() == LabStatus.CLOSED) {
            throw new IllegalStateException("Cannot create research group in an inactive lab");
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
