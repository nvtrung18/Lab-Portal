package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TaskResponse {
    private Long id;
    private Long milestoneId;
    private Long assigneeId;
    private String title;
    private TaskStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
