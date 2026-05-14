package com.web.labportalbackend.research.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class GroupResponse {
    private Long id;
    private Long labId;
    private String name;
    private Long leaderId;
    private Instant createdAt;
    private Instant updatedAt;
    private List<GroupMemberResponse> members;
}
