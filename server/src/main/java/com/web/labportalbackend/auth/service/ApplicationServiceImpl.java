package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.dto.ApplicationResponseDTO;
import com.web.labportalbackend.common.exception.DuplicateApplicationException;
import com.web.labportalbackend.lab.entity.Application;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of ApplicationService for CV submission operations.
 * Handles business logic for user applications to laboratories.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;

    @Override
    @Transactional
    public ApplicationResponseDTO apply(Long labId, Long userId, String cvUrl) {
        log.info("Processing application: userId={}, labId={}", userId, labId);

        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        // Verify laboratory exists
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));

        // Check for existing application from this user to this lab
        if (applicationRepository.existsByUserIdAndLaboratoryIdAndDeletedFalse(userId, labId)) {
            log.warn("Duplicate application attempt: userId={}, labId={}", userId, labId);
            throw new DuplicateApplicationException(userId, labId);
        }

        // Create new application
        Application application = new Application();
        application.setUser(user);
        application.setLaboratory(laboratory);
        application.setCvUrl(cvUrl);

        // Save application
        Application savedApplication = applicationRepository.save(application);
        log.info("Application created successfully: id={}, userId={}, labId={}", 
                savedApplication.getId(), userId, labId);

        // Convert to DTO and return
        return mapToDTO(savedApplication);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplications(Pageable pageable) {
        log.debug("Fetching applications with pagination: {}", pageable);
        return applicationRepository.findByDeletedFalse(pageable)
                .map(this::mapToDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationResponseDTO getApplicationById(Long applicationId) {
        log.debug("Fetching application by ID: {}", applicationId);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException("Application not found with ID: " + applicationId));

        return mapToDTO(application);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplicationsByUserId(Long userId, Pageable pageable) {
        log.debug("Fetching applications for user: {}", userId);
        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with ID: " + userId);
        }
        java.util.List<ApplicationResponseDTO> allApplications = applicationRepository
                .findByUserIdAndDeletedFalse(userId).stream()
                .map(this::mapToDTO)
                .toList();
        
        int pageSize = pageable.getPageSize();
        int pageNumber = pageable.getPageNumber();
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min((pageNumber + 1) * pageSize, allApplications.size());
        
        java.util.List<ApplicationResponseDTO> pageContent = fromIndex <= allApplications.size() 
                ? allApplications.subList(fromIndex, toIndex)
                : java.util.Collections.emptyList();
        
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, allApplications.size());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplicationsByLabId(Long labId, Pageable pageable) {
        log.debug("Fetching applications for lab: {}", labId);
        // Verify lab exists
        if (!laboratoryRepository.existsById(labId)) {
            throw new EntityNotFoundException("Laboratory not found with ID: " + labId);
        }
        java.util.List<ApplicationResponseDTO> allApplications = applicationRepository
                .findByLaboratoryIdAndDeletedFalse(labId).stream()
                .map(this::mapToDTO)
                .toList();
        
        int pageSize = pageable.getPageSize();
        int pageNumber = pageable.getPageNumber();
        int fromIndex = pageNumber * pageSize;
        int toIndex = Math.min((pageNumber + 1) * pageSize, allApplications.size());
        
        java.util.List<ApplicationResponseDTO> pageContent = fromIndex <= allApplications.size() 
                ? allApplications.subList(fromIndex, toIndex)
                : java.util.Collections.emptyList();
        
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, allApplications.size());
    }

    /**
     * Convert Application entity to ApplicationResponseDTO.
     */
    private ApplicationResponseDTO mapToDTO(Application application) {
        return ApplicationResponseDTO.builder()
                .id(application.getId())
                .userId(application.getUser().getId())
                .labId(application.getLaboratory().getId())
                .labName(application.getLaboratory().getLabName())
                .cvUrl(application.getCvUrl())
                .status(application.getStatus())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
}
