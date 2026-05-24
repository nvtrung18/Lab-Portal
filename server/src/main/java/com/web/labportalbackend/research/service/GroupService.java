package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.AddMemberRequest;
import com.web.labportalbackend.research.dto.request.CreateGroupRequest;
import com.web.labportalbackend.research.dto.response.GroupMemberResponse;
import com.web.labportalbackend.research.dto.response.GroupResponse;

import java.util.List;

public interface GroupService {
    GroupResponse createGroup(CreateGroupRequest request);

    GroupMemberResponse addMember(Long groupId, AddMemberRequest request);

    List<GroupResponse> getByLab(Long labId);

    List<GroupResponse> getByTopic(Long topicId);
}
