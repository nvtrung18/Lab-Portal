package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.MilestoneStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class MilestoneResponse {
    private Long id;
    private Long projectId;
    private String projectTitle;
    private Long groupId;
    private String groupName;
    private String title;
    private String description;
    private Long assignedToStudentId;
    private String assignedToStudentName;
    private LocalDate deadline;
    private MilestoneStatus status;
    private Integer progressPercent;
    private String evidenceUrl;
    private String managerComment;
    private Integer myTaskCount;
    private Integer myCompletedTaskCount;
    private Long createdById;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;
}
