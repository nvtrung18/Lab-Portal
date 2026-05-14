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
        if (!manager.hasRole("ADMIN")) {
            throw new IllegalArgumentException("User must have ADMIN role to be assigned as lab manager");
        }
        laboratory.setManager(manager);
        Laboratory updatedLab = laboratoryRepository.save(laboratory);
        log.info("Manager assigned to laboratory: {} -> User: {} (ID: {})",
                updatedLab.getLabName(), manager.getUsername(), manager.getId());
        return LabMapper.toResponse(updatedLab);
    }

    @Override
    @Transactional(readOnly = true)
    public LabResponse getLabById(Long labId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        return LabMapper.toResponse(laboratory);
    }
}
