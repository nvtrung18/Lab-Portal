package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.mapper.ProjectMapper;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        validateDateRange(request);

        if (!groupRepository.existsById(request.getGroupId())) {
            throw new ResourceNotFoundException("Research group", request.getGroupId());
        }
        GroupEntity group = groupRepository.getReferenceById(request.getGroupId());

        ProjectEntity project = ProjectEntity.builder()
                .group(group)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : ProjectStatus.PLANNED)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        return ProjectMapper.toResponse(projectRepository.save(project));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponse> getByGroup(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Research group", groupId);
        }
        return projectRepository.findByGroupId(groupId)
                .stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse getDetail(Long projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        return ProjectMapper.toDetailResponse(project);
    }

    private void validateDateRange(CreateProjectRequest request) {
        if (request.getEndDate() != null && request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }
    }
}
