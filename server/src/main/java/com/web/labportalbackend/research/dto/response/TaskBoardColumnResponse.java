package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TaskBoardColumnResponse {
    private TaskStatus status;
    private List<TaskResponse> tasks;
}
