package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.labportalbackend.research.dto.ResearchAttendanceDTO;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProjectStatsOverviewResponse {

    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("total_tasks")
    private long totalTasks;

    @JsonProperty("done_tasks")
    private long doneTasks;

    @JsonProperty("completion_rate")
    private BigDecimal completionRate;

    @JsonProperty("report_count")
    private long reportCount;

    private ResearchAttendanceDTO attendance;
}
