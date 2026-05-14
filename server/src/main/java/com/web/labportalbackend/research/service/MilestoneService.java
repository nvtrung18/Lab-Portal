package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.CreateMilestoneRequest;
import com.web.labportalbackend.research.dto.response.MilestoneResponse;

import java.util.List;

public interface MilestoneService {
    MilestoneResponse createMilestone(CreateMilestoneRequest request);

    List<MilestoneResponse> getByProject(Long projectId);
}
