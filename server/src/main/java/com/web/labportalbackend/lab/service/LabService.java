package com.web.labportalbackend.lab.service;

import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;

/**
 * Service interface for laboratory management operations.
 */
public interface LabService {
    LabResponse createLab(CreateLabRequest request);
    LabResponse assignManager(Long labId, Long managerId);
    LabResponse getLabById(Long labId);
}
