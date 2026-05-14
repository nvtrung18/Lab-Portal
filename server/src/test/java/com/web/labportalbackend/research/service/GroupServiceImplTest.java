package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.DuplicateMemberException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.AddMemberRequest;
import com.web.labportalbackend.research.dto.request.CreateGroupRequest;
import com.web.labportalbackend.research.dto.response.GroupMemberResponse;
import com.web.labportalbackend.research.dto.response.GroupResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.service.impl.GroupServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private UserRepository userRepository;

    @InjectMocks
    private GroupServiceImpl groupService;

    @Test
    void createGroup_savesGroupAndAddsLeaderAsMember() {
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        User leader = new User();
        leader.setId(2L);

        CreateGroupRequest request = new CreateGroupRequest();
        request.setLabId(1L);
        request.setName("AI Research Group");
        request.setLeaderId(2L);

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(lab));
        when(userRepository.findById(2L)).thenReturn(Optional.of(leader));
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
        GroupEntity group = new GroupEntity();
        group.setId(10L);
        User user = new User();
        user.setId(3L);

        AddMemberRequest request = new AddMemberRequest();
        request.setUserId(3L);

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 3L)).thenReturn(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
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
        GroupEntity group = new GroupEntity();
        group.setId(10L);

        AddMemberRequest request = new AddMemberRequest();
        request.setUserId(3L);

        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 3L)).thenReturn(true);

        assertThrows(DuplicateMemberException.class, () -> groupService.addMember(10L, request));

        verify(userRepository, never()).findById(any());
        verify(groupMemberRepository, never()).save(any());
    }
}
