package com.web.labportalbackend.lab.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.dto.response.ApplicationResponseDTO;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import com.web.labportalbackend.common.exception.ApplicationAlreadyReviewedException;
import com.web.labportalbackend.common.exception.DuplicateApplicationException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Application;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.entity.Membership;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.lab.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;

    @Override @Transactional
    public ApplicationResponseDTO apply(Long labId, Long userId, String cvUrl) {
        log.info("Processing application: userId={}, labId={}", userId, labId);
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Laboratory lab = laboratoryRepository.findById(labId).orElseThrow(() -> new ResourceNotFoundException("Laboratory", labId));
        if (applicationRepository.existsByUserIdAndLaboratoryIdAndDeletedFalse(userId, labId)) {
            throw new DuplicateApplicationException(userId, labId);
        }
        Application app = new Application(); app.setUser(user); app.setLaboratory(lab); app.setCvUrl(cvUrl);
        Application saved = applicationRepository.save(app);
        log.info("Application created: id={}", saved.getId());
        return mapToDTO(saved);
    }

    @Override @Transactional
    public ApplicationResponseDTO review(Long applicationId, ApplicationStatus newStatus) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId));
        if (app.getStatus() != ApplicationStatus.PENDING) throw new ApplicationAlreadyReviewedException(applicationId);
        app.setStatus(newStatus);
        Application updated = applicationRepository.save(app);
        if (newStatus == ApplicationStatus.APPROVED &&
                !membershipRepository.existsByUserIdAndLaboratoryIdAndDeletedFalse(app.getUser().getId(), app.getLaboratory().getId())) {
            Membership m = new Membership(); m.setUser(app.getUser()); m.setLaboratory(app.getLaboratory()); m.setRole("MEMBER");
            membershipRepository.save(m);
            log.info("Membership created: userId={}, labId={}", app.getUser().getId(), app.getLaboratory().getId());
        }
        return mapToDTO(updated);
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplications(Pageable pageable) {
        return applicationRepository.findByDeletedFalse(pageable).map(this::mapToDTO);
    }

    @Override @Transactional(readOnly = true)
    public ApplicationResponseDTO getApplicationById(Long applicationId) {
        return mapToDTO(applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application", applicationId)));
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplicationsByUserId(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) throw new ResourceNotFoundException("User", userId);
        return toPage(applicationRepository.findByUserIdAndDeletedFalse(userId).stream().map(this::mapToDTO).toList(), pageable);
    }

    @Override @Transactional(readOnly = true)
    public Page<ApplicationResponseDTO> getApplicationsByLabId(Long labId, Pageable pageable) {
        if (!laboratoryRepository.existsById(labId)) throw new ResourceNotFoundException("Laboratory", labId);
        return toPage(applicationRepository.findByLaboratoryIdAndDeletedFalse(labId).stream().map(this::mapToDTO).toList(), pageable);
    }

    private ApplicationResponseDTO mapToDTO(Application app) {
        return ApplicationResponseDTO.builder()
                .id(app.getId()).userId(app.getUser().getId()).labId(app.getLaboratory().getId())
                .labName(app.getLaboratory().getLabName()).cvUrl(app.getCvUrl()).status(app.getStatus())
                .createdAt(app.getCreatedAt()).updatedAt(app.getUpdatedAt()).build();
    }

    private <T> Page<T> toPage(List<T> list, Pageable pageable) {
        int from = (int) pageable.getOffset();
        int to = Math.min(from + pageable.getPageSize(), list.size());
        return new PageImpl<>(from <= list.size() ? list.subList(from, to) : Collections.emptyList(), pageable, list.size());
    }
}
