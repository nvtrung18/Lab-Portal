package com.web.labportalbackend.research.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import com.web.labportalbackend.research.enums.GroupStatus;

@Getter
@Setter
public class CreateGroupRequest {

    @NotNull(message = "Lab ID is required")
    private Long labId;

    @NotNull(message = "Topic ID is required")
    private Long topicId;

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
}
