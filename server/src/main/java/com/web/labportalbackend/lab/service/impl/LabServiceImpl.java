package com.web.labportalbackend.lab.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.mapper.LabMapper;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.service.LabService;
import com.web.labportalbackend.common.enums.LabStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabServiceImpl implements LabService {

    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public LabResponse createLab(CreateLabRequest request) {
        if (laboratoryRepository.existsByLabName(request.getLabName())) {
            throw new IllegalArgumentException("Laboratory name is already in use: " + request.getLabName());
        }
        Laboratory laboratory = LabMapper.toEntity(request);
        laboratory.setStatus(LabStatus.AVAILABLE);
        Laboratory savedLab = laboratoryRepository.save(laboratory);
        log.info("Laboratory created: {} (ID: {})", savedLab.getLabName(), savedLab.getId());
        return LabMapper.toResponse(savedLab);
    }

    @Override
    @Transactional
    public LabResponse assignManager(Long labId, Long managerId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + managerId));
        if (!manager.hasRole("LAB_MANAGER")) {
            throw new IllegalArgumentException("User must have LAB_MANAGER role to be assigned as lab manager");
        }
        laboratoryRepository.findFirstByManagerIdAndDeletedFalse(managerId)
                .filter(existingLab -> !existingLab.getId().equals(labId))
                .ifPresent(existingLab -> {
                    throw new IllegalArgumentException("Lab manager is already assigned to another lab: " + existingLab.getLabName());
                });
        if (laboratory.getManager() != null && !laboratory.getManager().getId().equals(managerId)) {
            laboratory.setManager(null);
        }
        laboratory.setManager(manager);
        Laboratory updatedLab = laboratoryRepository.save(laboratory);
        log.info("Manager assigned to laboratory: {} -> User: {} (ID: {})",
                updatedLab.getLabName(), manager.getUsername(), manager.getId());
        return LabMapper.toResponse(updatedLab);
    }

    @Override
    @Transactional
    public LabResponse updateStatus(Long labId, LabStatus status) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        laboratory.setStatus(status);
        Laboratory updatedLab = laboratoryRepository.save(laboratory);
        log.info("Laboratory status updated: {} -> {}", updatedLab.getLabName(), status);
        return LabMapper.toResponse(updatedLab);
    }

    @Override
    @Transactional(readOnly = true)
    public LabResponse getLabById(Long labId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        return LabMapper.toResponse(laboratory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabResponse> getAllLabs() {
        return laboratoryRepository.findAll().stream()
                .filter(lab -> Boolean.TRUE.equals(lab.getActive()) && Boolean.FALSE.equals(lab.getDeleted()))
                .map(LabMapper::toResponse)
                .toList();
    }
}
