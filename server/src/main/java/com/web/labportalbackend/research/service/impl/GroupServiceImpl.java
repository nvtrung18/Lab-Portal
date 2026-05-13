package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.DuplicateMemberException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.AddMemberRequest;
import com.web.labportalbackend.research.dto.request.CreateGroupRequest;
import com.web.labportalbackend.research.dto.response.GroupMemberResponse;
import com.web.labportalbackend.research.dto.response.GroupResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.mapper.GroupMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        Laboratory lab = laboratoryRepository.findById(request.getLabId())
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", request.getLabId()));
        User leader = userRepository.findById(request.getLeaderId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getLeaderId()));

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .name(request.getName())
                .leader(leader)
                .build();
        GroupEntity savedGroup = groupRepository.save(group);

        GroupMemberEntity leaderMember = GroupMemberEntity.builder()
                .group(savedGroup)
                .user(leader)
                .role(GroupRole.LEADER)
                .joinedAt(Instant.now())
                .build();
        GroupMemberEntity savedLeaderMember = groupMemberRepository.save(leaderMember);
        savedGroup.getMembers().add(savedLeaderMember);

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
        if (!laboratoryRepository.existsById(labId)) {
            throw new ResourceNotFoundException("Laboratory", labId);
        }
        return groupRepository.findByLabId(labId)
                .stream()
                .map(GroupMapper::toResponse)
                .toList();
    }
}
