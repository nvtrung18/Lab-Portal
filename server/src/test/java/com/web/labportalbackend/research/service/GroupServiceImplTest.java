package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.DuplicateMemberException;
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
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ResearchTopicRepository;
import com.web.labportalbackend.research.service.impl.GroupServiceImpl;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ResearchTopicRepository topicRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupServiceImpl groupService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createGroup_savesGroupAndAddsLeaderAsMember() {
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        ResearchTopicEntity topic = new ResearchTopicEntity();
        topic.setId(5L);
        topic.setLab(lab);
        User leader = new User();
        leader.setId(2L);
        leader.setUsername("manager");
        leader.addRole(new Role("LAB_MANAGER", "Lab manager"));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setLabId(1L);
        request.setTopicId(5L);
        request.setName("AI Research Group");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "password", List.of())
        );
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(leader));
        when(topicRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(topic));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(2L)).thenReturn(Optional.of(lab));
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> {
            GroupEntity group = invocation.getArgument(0);
            group.setId(10L);
            return group;
        });
        when(groupMemberRepository.save(any(GroupMemberEntity.class))).thenAnswer(invocation -> {
            GroupMemberEntity member = invocation.getArgument(0);
            member.setId(20L);
            return member;
        });

        GroupResponse response = groupService.createGroup(request);

        assertEquals(10L, response.getId());
        assertEquals(1, response.getMembers().size());
        assertEquals(GroupRole.LEADER, response.getMembers().get(0).getRole());

        verify(groupRepository).save(any(GroupEntity.class));
        ArgumentCaptor<GroupMemberEntity> memberCaptor = ArgumentCaptor.forClass(GroupMemberEntity.class);
        verify(groupMemberRepository).save(memberCaptor.capture());

        GroupMemberEntity savedMember = memberCaptor.getValue();
        assertEquals(GroupRole.LEADER, savedMember.getRole());
        assertSame(leader, savedMember.getUser());
        assertEquals(10L, savedMember.getGroup().getId());
    }

    @Test
    void addMember_savesMemberWithMemberRole() {
        User manager = authenticateManager();
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        group.setLab(lab);
        User user = new User();
        user.setId(3L);
        user.addRole(new Role("STUDENT", "Student"));

        AddMemberRequest request = new AddMemberRequest();
        request.setUserId(3L);

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(group));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 3L)).thenReturn(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(3L, 1L)).thenReturn(true);
        when(groupMemberRepository.save(any(GroupMemberEntity.class))).thenAnswer(invocation -> {
            GroupMemberEntity member = invocation.getArgument(0);
            member.setId(30L);
            return member;
        });

        GroupMemberResponse response = groupService.addMember(10L, request);

        assertEquals(30L, response.getId());
        assertEquals(10L, response.getGroupId());
        assertEquals(3L, response.getUserId());
        assertEquals(GroupRole.MEMBER, response.getRole());
        verify(groupMemberRepository).save(any(GroupMemberEntity.class));
    }

    @Test
    void addMember_throwsDuplicateMemberExceptionWhenUserAlreadyInGroup() {
        User manager = authenticateManager();
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        group.setLab(lab);

        AddMemberRequest request = new AddMemberRequest();
        request.setUserId(3L);

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(group));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 3L)).thenReturn(true);

        assertThrows(DuplicateMemberException.class, () -> groupService.addMember(10L, request));

        verify(userRepository, never()).findById(any());
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void getMyGroupsByLab_queriesOnlySelectedLabForActiveStudentMember() {
        User student = authenticateStudent();
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(student.getId(), 1L))
                .thenReturn(true);
        when(groupMemberRepository
                .findByUserIdAndGroupLabIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(
                        student.getId(),
                        1L
                ))
                .thenReturn(List.of());

        List<GroupResponse> response = groupService.getMyGroupsByLab(1L);

        assertTrue(response.isEmpty());
        verify(groupMemberRepository)
                .findByUserIdAndGroupLabIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(
                        student.getId(),
                        1L
                );
    }

    @Test
    void getMyGroupsByLab_deniesStudentWithoutActiveLabMembership() {
        User student = authenticateStudent();
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(student.getId(), 2L))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> groupService.getMyGroupsByLab(2L));
        verify(groupMemberRepository, never())
                .findByUserIdAndGroupLabIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(
                        any(),
                        any()
                );
    }

    @Test
    void getDetail_returnsProjectGroupForManagerOfSameLab() {
        User manager = authenticateManager();
        Laboratory lab = laboratory(1L);
        GroupEntity group = projectGroup(10L, lab);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(group));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        GroupResponse response = groupService.getDetail(10L);

        assertEquals(10L, response.getId());
        assertEquals(group.getProject().getId(), response.getProjectId());
    }

    @Test
    void getDetail_deniesManagerForGroupInAnotherLab() {
        User manager = authenticateManager();
        Laboratory groupLab = laboratory(1L);
        Laboratory managedLab = laboratory(2L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(projectGroup(10L, groupLab)));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(managedLab));

        assertThrows(AccessDeniedException.class, () -> groupService.getDetail(10L));
    }

    @Test
    void getDetail_returnsGroupForActiveStudentMember() {
        User student = authenticateStudent();
        GroupEntity group = projectGroup(10L, laboratory(1L));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(10L, student.getId()))
                .thenReturn(true);

        GroupResponse response = groupService.getDetail(10L);

        assertEquals(10L, response.getId());
    }

    @Test
    void getDetail_deniesStudentOutsideGroup() {
        User student = authenticateStudent();
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(projectGroup(10L, laboratory(1L))));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(10L, student.getId()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> groupService.getDetail(10L));
    }

    @Test
    void updateResearchGroup_updatesMembersAndLeaderForManagerOfSameLab() {
        User manager = authenticateManager();
        Laboratory lab = laboratory(1L);
        GroupEntity group = projectGroup(10L, lab);
        User formerLeader = student(4L, "old@uet.edu.vn");
        User newLeader = student(5L, "new@uet.edu.vn");
        GroupMemberEntity removedMember = member(group, formerLeader, GroupRole.LEADER);
        GroupMemberEntity promotedMember = member(group, newLeader, GroupRole.MEMBER);
        group.getMembers().addAll(List.of(removedMember, promotedMember));
        group.setLeader(formerLeader);

        CreateGroupRequest request = updateRequest(newLeader.getId(), List.of(newLeader.getId()));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(group));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(userRepository.findById(newLeader.getId())).thenReturn(Optional.of(newLeader));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(newLeader.getId(), lab.getId()))
                .thenReturn(true);
        when(groupRepository.save(group)).thenReturn(group);

        GroupResponse response = groupService.updateResearchGroup(10L, request);

        assertEquals("Nhóm cập nhật", response.getName());
        assertSame(newLeader, group.getLeader());
        assertFalse(removedMember.getActive());
        assertEquals(GroupRole.LEADER, promotedMember.getRole());
        assertEquals(1, response.getMembers().size());
        verify(groupMemberRepository).saveAll(group.getMembers());
    }

    @Test
    void updateResearchGroup_deniesManagerForGroupInAnotherLab() {
        User manager = authenticateManager();
        Laboratory groupLab = laboratory(1L);
        Laboratory managedLab = laboratory(2L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(projectGroup(10L, groupLab)));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(managedLab));

        assertThrows(AccessDeniedException.class,
                () -> groupService.updateResearchGroup(10L, updateRequest(5L, List.of(5L))));
        verify(groupRepository, never()).save(any());
    }

    @Test
    void updateResearchGroup_deniesStudentWithoutActiveMembershipInLab() {
        User manager = authenticateManager();
        Laboratory lab = laboratory(1L);
        User selectedStudent = student(5L, "selected@uet.edu.vn");
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(projectGroup(10L, lab)));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(userRepository.findById(selectedStudent.getId())).thenReturn(Optional.of(selectedStudent));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(selectedStudent.getId(), lab.getId()))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> groupService.updateResearchGroup(10L, updateRequest(selectedStudent.getId(), List.of(selectedStudent.getId()))));
        verify(groupRepository, never()).save(any());
    }

    private User authenticateManager() {
        User manager = new User();
        manager.setId(2L);
        manager.setUsername("manager");
        manager.addRole(new Role("LAB_MANAGER", "Lab manager"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "password", List.of())
        );
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        return manager;
    }

    private User authenticateStudent() {
        User student = new User();
        student.setId(3L);
        student.setUsername("student");
        student.addRole(new Role("STUDENT", "Student"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", "password", List.of())
        );
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        return student;
    }

    private Laboratory laboratory(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        return lab;
    }

    private GroupEntity projectGroup(Long id, Laboratory lab) {
        User leader = new User();
        leader.setId(4L);
        leader.setEmail("leader@uet.edu.vn");
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Đề tài AI").build();
        project.setId(20L);
        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .project(project)
                .leader(leader)
                .name("Nhóm AI")
                .build();
        group.setId(id);
        return group;
    }

    private User student(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.addRole(new Role("STUDENT", "Student"));
        return user;
    }

    private GroupMemberEntity member(GroupEntity group, User user, GroupRole role) {
        GroupMemberEntity member = GroupMemberEntity.builder().group(group).user(user).role(role).build();
        member.setActive(true);
        member.setDeleted(false);
        return member;
    }

    private CreateGroupRequest updateRequest(Long leaderId, List<Long> memberIds) {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Nhóm cập nhật");
        request.setObjective("Mục tiêu mới");
        request.setPlan("Kế hoạch mới");
        request.setLeaderStudentId(leaderId);
        request.setMemberIds(memberIds);
        request.setStatus(GroupStatus.ACTIVE);
        return request;
    }
}
