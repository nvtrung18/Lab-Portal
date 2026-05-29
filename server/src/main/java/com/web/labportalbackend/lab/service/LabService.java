package com.web.labportalbackend.lab.service;

import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.dto.response.AssignableLabResponse;
import com.web.labportalbackend.lab.dto.response.LabDashboardStatsResponse;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;
import com.web.labportalbackend.common.enums.LabStatus;
import java.util.List;

/**
 * Service interface for laboratory management operations.
 */
public interface LabService {
    LabResponse createLab(CreateLabRequest request);
    LabResponse assignManager(Long labId, Long managerId);
    LabResponse updateStatus(Long labId, LabStatus status);
    LabResponse getLabById(Long labId);
    List<LabResponse> getAllLabs();
    List<AssignableLabResponse> getAssignableLabs(String keyword, Boolean includeInactive);
    LabResponse assignManagerPatch(Long labId, Long managerUserId);
    LabDashboardStatsResponse getLabDashboardStats(Long labId);
}

