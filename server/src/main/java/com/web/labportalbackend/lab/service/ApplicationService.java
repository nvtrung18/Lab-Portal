package com.web.labportalbackend.lab.service;

import com.web.labportalbackend.lab.dto.response.ApplicationResponseDTO;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for application (CV submission) operations.
 */
public interface ApplicationService {
    ApplicationResponseDTO apply(Long labId, Long userId, String cvUrl, MultipartFile cvFile);
    ApplicationResponseDTO review(Long applicationId, ApplicationStatus newStatus);
    Page<ApplicationResponseDTO> getApplications(Pageable pageable);
    ApplicationResponseDTO getApplicationById(Long applicationId);
    Page<ApplicationResponseDTO> getApplicationsByUserId(Long userId, Pageable pageable);
    Page<ApplicationResponseDTO> getApplicationsByLabId(Long labId, Pageable pageable);
}
