package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateMilestoneRequest;
import com.web.labportalbackend.research.dto.request.UpdateMilestoneRequest;
import com.web.labportalbackend.research.dto.response.MilestoneResponse;

import java.util.List;

public interface MilestoneService {
    MilestoneResponse createMilestone(CreateMilestoneRequest request);

    List<MilestoneResponse> getByProject(Long projectId);

    List<MilestoneResponse> getByGroup(Long groupId);

    List<MilestoneResponse> getMyMilestonesInGroup(Long groupId);

    MilestoneResponse getDetail(Long milestoneId);

    MilestoneResponse updateMilestone(Long milestoneId, UpdateMilestoneRequest request);
}
