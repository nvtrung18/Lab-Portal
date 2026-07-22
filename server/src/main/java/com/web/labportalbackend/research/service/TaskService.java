package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.AssignTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateResearchTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.request.UpdateTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;

import java.util.List;

public interface TaskService {
    TaskResponse createTask(CreateTaskRequest request);

    TaskResponse createResearchTask(CreateResearchTaskRequest request);

    TaskResponse assign(Long taskId, AssignTaskRequest request);

    List<TaskResponse> getByMilestone(Long milestoneId);

    List<TaskResponse> getByGroup(Long groupId);

    List<TaskResponse> getMyTasksInGroup(Long groupId);

    TaskResponse updateStatus(Long taskId, UpdateTaskStatusRequest request);
}
