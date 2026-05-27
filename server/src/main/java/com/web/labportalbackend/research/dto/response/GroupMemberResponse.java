package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.GroupRole;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class GroupMemberResponse {
    private Long id;
    private Long groupId;
    private Long userId;
    private String fullName;
    private String email;
    private GroupRole role;
    private Instant joinedAt;
}
