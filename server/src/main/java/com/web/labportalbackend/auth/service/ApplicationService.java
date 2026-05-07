package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.common.dto.ApplicationResponseDTO;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for application (CV submission) operations.
 */
public interface ApplicationService {

    /**
     * Submit a new CV application to a laboratory.
     * Prevents duplicate applications from the same user to the same lab.
     *
     * @param labId the ID of the target laboratory
     * @param userId the ID of the applicant user
     * @param cvUrl the URL/path to the CV file
     * @return the created application DTO
     * @throws com.web.labportalbackend.common.exception.DuplicateApplicationException if user already applied to this lab
     * @throws com.web.labportalbackend.common.exception.ResourceNotFoundException if lab or user not found
     */
    ApplicationResponseDTO apply(Long labId, Long userId, String cvUrl);

    /**
     * Review an application (approve or reject).
     * If approved, creates a membership record for the user in the laboratory.
     * Uses @Transactional to ensure atomicity of status update and membership creation.
     *
     * @param applicationId the ID of the application to review
     * @param newStatus the new status (APPROVED or REJECTED)
     * @return the updated application DTO
     * @throws com.web.labportalbackend.common.exception.ResourceNotFoundException if application not found
     * @throws com.web.labportalbackend.common.exception.ApplicationAlreadyReviewedException if already reviewed
     */
    ApplicationResponseDTO review(Long applicationId, ApplicationStatus newStatus);

    /**
     * Retrieve a paginated list of all applications.
     *
     * @param pageable pagination parameters
     * @return page of applications
     */
    Page<ApplicationResponseDTO> getApplications(Pageable pageable);

    /**
     * Retrieve an application by its ID.
     *
     * @param applicationId the ID of the application
     * @return the application DTO
     * @throws com.web.labportalbackend.common.exception.ResourceNotFoundException if not found
     */
    ApplicationResponseDTO getApplicationById(Long applicationId);

    /**
     * Retrieve all applications for a specific user.
     *
     * @param userId the ID of the user
     * @param pageable pagination parameters
     * @return page of applications
     */
    Page<ApplicationResponseDTO> getApplicationsByUserId(Long userId, Pageable pageable);

    /**
     * Retrieve all applications for a specific laboratory.
     *
     * @param labId the ID of the laboratory
     * @param pageable pagination parameters
     * @return page of applications
     */
    Page<ApplicationResponseDTO> getApplicationsByLabId(Long labId, Pageable pageable);
}
