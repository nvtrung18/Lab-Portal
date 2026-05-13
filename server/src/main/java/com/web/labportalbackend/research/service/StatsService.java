package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.response.ProjectStatsOverviewResponse;

public interface StatsService {
    ProjectStatsOverviewResponse getProjectStats(Long projectId);
}
