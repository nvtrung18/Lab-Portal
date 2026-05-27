package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.GroupStatus;
import com.web.labportalbackend.research.enums.GroupRole;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class GroupResponse {
    private Long id;
    private Long labId;
    private Long topicId;
    private Long projectId;
    private String topicName;
    private String projectTitle;
    private String projectCode;
    private String leaderName;
    private String managerName;
    private String name;
    private String description;
    private String objective;
    private String plan;
    private GroupStatus status;
    private Long leaderId;
    private GroupRole myRole;
    private String createdByName;
    private Integer memberCount;
    private Long projectCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<GroupMemberResponse> members;
}
