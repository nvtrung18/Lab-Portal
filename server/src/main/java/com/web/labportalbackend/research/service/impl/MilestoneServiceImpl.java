package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.InvalidDateRangeException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateMilestoneRequest;
import com.web.labportalbackend.research.dto.response.MilestoneResponse;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.mapper.MilestoneMapper;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.MilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;

    @Override
    @Transactional
    public MilestoneResponse createMilestone(CreateMilestoneRequest request) {
        ProjectEntity project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));
        validateMilestoneDates(request, project);

        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(project)
                .name(request.getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(request.getStatus() != null ? request.getStatus() : MilestoneStatus.PLANNED)
                .build();

        return MilestoneMapper.toResponse(milestoneRepository.save(milestone));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MilestoneResponse> getByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return milestoneRepository.findByProjectIdOrderByStartDateAsc(projectId)
                .stream()
                .map(MilestoneMapper::toResponse)
                .toList();
    }

    private void validateMilestoneDates(CreateMilestoneRequest request, ProjectEntity project) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new InvalidDateRangeException("Milestone start date must be before or equal to end date");
        }
        if (request.getStartDate().isBefore(project.getStartDate())) {
            throw new InvalidDateRangeException("Milestone start date must be within project date range");
        }
        if (project.getEndDate() != null && request.getEndDate().isAfter(project.getEndDate())) {
            throw new InvalidDateRangeException("Milestone end date must be within project date range");
        }
    }
}
