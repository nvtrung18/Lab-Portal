package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.dto.CreateLabRequest;
import com.web.labportalbackend.common.dto.LabDTO;
import com.web.labportalbackend.common.dto.LabMapper;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for laboratory management operations.
 * Handles creation, assignment of managers, and other lab-related business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabService {

    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;

    /**
     * Create a new laboratory.
     *
     * @param request Laboratory creation request
     * @return Created laboratory DTO
     * @throws IllegalArgumentException if lab_name already exists
     */
    @Transactional
    public LabDTO createLab(CreateLabRequest request) {
        // Validate uniqueness of lab name
        if (laboratoryRepository.existsByLabName(request.getLabName())) {
            throw new IllegalArgumentException("Laboratory name is already in use: " + request.getLabName());
        }

        // Convert request to entity
        Laboratory laboratory = LabMapper.toEntity(request);
        laboratory.setStatus(LabStatus.AVAILABLE);

        // Save laboratory
        Laboratory savedLab = laboratoryRepository.save(laboratory);
        log.info("Laboratory created: {} (ID: {})", savedLab.getLabName(), savedLab.getId());

        return LabMapper.toDTO(savedLab);
    }

    /**
     * Assign a manager to a laboratory.
     * Only users with ADMIN role can be assigned as managers.
     *
     * @param labId     Laboratory ID
     * @param managerId User ID of the manager to assign
     * @return Updated laboratory DTO
     * @throws EntityNotFoundException if lab or user not found
     * @throws IllegalArgumentException if user does not have ADMIN role
     */
    @Transactional
    public LabDTO assignManager(Long labId, Long managerId) {
        // Find laboratory
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));

        // Find user
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + managerId));

        // Verify user has ADMIN role
        if (!manager.hasRole("ADMIN")) {
            throw new IllegalArgumentException("User must have ADMIN role to be assigned as lab manager");
        }

        // Assign manager
        laboratory.setManager(manager);
        Laboratory updatedLab = laboratoryRepository.save(laboratory);
        log.info("Manager assigned to laboratory: {} -> User: {} (ID: {})", 
                 updatedLab.getLabName(), manager.getUsername(), manager.getId());

        return LabMapper.toDTO(updatedLab);
    }

    /**
     * Retrieve a laboratory by ID.
     *
     * @param labId Laboratory ID
     * @return Laboratory DTO
     * @throws EntityNotFoundException if lab not found
     */
    public LabDTO getLabById(Long labId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        return LabMapper.toDTO(laboratory);
    }
}
