package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.request.CreateResearchProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;

import java.util.List;

public interface ProjectService {
    ProjectResponse createProject(CreateProjectRequest request);

    ProjectResponse createResearchProject(CreateResearchProjectRequest request);

    List<ProjectResponse> getByLab(Long labId);

    List<ProjectResponse> getByGroup(Long groupId);

    ProjectDetailResponse getDetail(Long projectId);
}
