package com.web.labportalbackend.research.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProjectTaskBoardResponse {
    private Long projectId;
    private List<TaskBoardColumnResponse> columns;
}
