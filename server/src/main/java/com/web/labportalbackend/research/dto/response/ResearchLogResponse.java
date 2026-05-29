package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.ResearchLogType;
import com.web.labportalbackend.research.enums.ResearchLogVisibility;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class ResearchLogResponse {
    private Long id;
    private Long projectId;
    private Long groupId;
    private String groupName;
    private Long milestoneId;
    private String milestoneTitle;
    private Long taskId;
    private String taskTitle;
    private Long authorId;
    private String authorName;
    private String authorRole;
    private GroupRole groupRole;
    private ResearchLogType logType;
    private LocalDate workDate;
    private Integer durationMinutes;
    private String content;
    private String result;
    private String problem;
    private String nextPlan;
    private String evidenceLink;
    private ResearchLogVisibility visibility;
    private Instant createdAt;
    private Instant updatedAt;
}
