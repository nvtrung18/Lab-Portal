package com.web.labportalbackend.research.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import com.web.labportalbackend.research.enums.GroupStatus;

import java.util.List;

@Getter
@Setter
public class CreateGroupRequest {

    private Long labId;

    private Long topicId;

    private Long projectId;

    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 150, message = "Group name must be between 3 and 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 2000, message = "Objective must not exceed 2000 characters")
    private String objective;

    @Size(max = 2000, message = "Plan must not exceed 2000 characters")
    private String plan;

    private GroupStatus status = GroupStatus.ACTIVE;

    /**
     * Kept for backward compatibility. New requests use the authenticated user as group leader.
     */
    private Long leaderId;

    private Long leaderStudentId;

    private List<Long> memberIds;
}
