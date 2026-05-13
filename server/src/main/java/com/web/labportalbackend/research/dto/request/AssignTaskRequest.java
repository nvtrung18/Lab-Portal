package com.web.labportalbackend.research.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignTaskRequest {

    @NotNull(message = "Assignee ID is required")
    private Long assigneeId;
}
