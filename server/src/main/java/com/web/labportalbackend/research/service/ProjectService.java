package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;

import java.util.List;

public interface ProjectService {
    ProjectResponse createProject(CreateProjectRequest request);

    List<ProjectResponse> getByGroup(Long groupId);

    ProjectDetailResponse getDetail(Long projectId);
}
