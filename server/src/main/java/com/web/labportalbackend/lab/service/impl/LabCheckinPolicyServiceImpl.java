package com.web.labportalbackend.lab.service.impl;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.network.IpNetworkMatcher;
import com.web.labportalbackend.lab.dto.request.AddLabAllowedNetworkRequest;
import com.web.labportalbackend.lab.dto.request.LabCheckinPolicyRequest;
import com.web.labportalbackend.lab.dto.response.LabAllowedNetworkResponse;
import com.web.labportalbackend.lab.dto.response.LabCheckinPolicyResponse;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.entity.LaboratoryAllowedNetwork;
import com.web.labportalbackend.lab.repository.LaboratoryAllowedNetworkRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.service.LabCheckinPolicyService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LabCheckinPolicyServiceImpl implements LabCheckinPolicyService {

    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryAllowedNetworkRepository networkRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public LabCheckinPolicyResponse getPolicy(Long labId) {
        Laboratory laboratory = authorizedLaboratory(labId, currentUser());
        return toPolicyResponse(laboratory);
    }

    @Override
    @Transactional
    public LabCheckinPolicyResponse updatePolicy(Long labId, LabCheckinPolicyRequest request) {
        User actor = currentUser();
        Laboratory laboratory = authorizedAdminLaboratory(labId, actor);
        laboratory.setCheckinLatitude(request.latitude());
        laboratory.setCheckinLongitude(request.longitude());
        laboratory.setCheckinRadiusMeters(request.radiusMeters());
        laboratory.setCheckinMaxAccuracyMeters(request.maxAccuracyMeters());
        laboratory.setCheckinEnabled(request.enabled());
        Laboratory saved = laboratoryRepository.save(laboratory);
        auditLogService.log(actor, AuditAction.UPDATE_LAB_CHECKIN_POLICY, AuditModule.LAB,
                "LAB", saved.getId(), "Laboratory self check-in policy updated");
        return toPolicyResponse(saved);
    }

    @Override
    @Transactional
    public LabAllowedNetworkResponse addNetwork(Long labId, AddLabAllowedNetworkRequest request) {
        return addNetwork(labId, request.network());
    }

    @Override
    @Transactional
    public LabAllowedNetworkResponse registerCurrentNetwork(Long labId, String currentIp) {
        return addNetwork(labId, currentIp);
    }

    private LabAllowedNetworkResponse addNetwork(Long labId, String rawNetwork) {
        User actor = currentUser();
        Laboratory laboratory = authorizedAdminLaboratory(labId, actor);
        String normalizedNetwork = IpNetworkMatcher.normalize(rawNetwork);
        LaboratoryAllowedNetwork existing = networkRepository
                .findFirstByLaboratoryIdAndNetworkValueOrderByIdDesc(laboratory.getId(), normalizedNetwork)
                .orElse(null);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getActive()) || Boolean.TRUE.equals(existing.getDeleted())) {
                existing.setActive(true);
                existing.setDeleted(false);
                existing = networkRepository.save(existing);
                auditLogService.log(actor, AuditAction.UPDATE_LAB_CHECKIN_POLICY, AuditModule.LAB,
                        "LAB", laboratory.getId(), "Laboratory self check-in network reactivated");
            }
            return toNetworkResponse(existing);
        }

        LaboratoryAllowedNetwork network = LaboratoryAllowedNetwork.builder()
                .laboratory(laboratory)
                .networkValue(normalizedNetwork)
                .registeredBy(actor)
                .build();
        LaboratoryAllowedNetwork saved = networkRepository.save(network);
        auditLogService.log(actor, AuditAction.UPDATE_LAB_CHECKIN_POLICY, AuditModule.LAB,
                "LAB", laboratory.getId(), "Laboratory self check-in network added");
        return toNetworkResponse(saved);
    }

    @Override
    @Transactional
    public void disableNetwork(Long labId, Long networkId) {
        User actor = currentUser();
        Laboratory laboratory = authorizedAdminLaboratory(labId, actor);
        LaboratoryAllowedNetwork network = networkRepository
                .findByIdAndLaboratoryIdAndDeletedFalse(networkId, laboratory.getId())
                .orElseThrow(() -> new EntityNotFoundException("Laboratory network entry not found"));
        network.disable();
        networkRepository.save(network);
        auditLogService.log(actor, AuditAction.UPDATE_LAB_CHECKIN_POLICY, AuditModule.LAB,
                "LAB", laboratory.getId(), "Laboratory self check-in network disabled");
    }

    private Laboratory authorizedLaboratory(Long labId, User actor) {
        if (actor.hasRole("ADMIN")) {
            return laboratoryRepository.findById(labId)
                    .filter(lab -> Boolean.TRUE.equals(lab.getActive()) && !Boolean.TRUE.equals(lab.getDeleted()))
                    .orElseThrow(() -> new EntityNotFoundException("Laboratory not found"));
        }
        if (!actor.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only laboratory managers can manage check-in policy");
        }
        return laboratoryRepository.findManagedByIdForStatusAuthorization(labId, actor.getId())
                .orElseThrow(() -> new AccessDeniedException("Cannot manage another laboratory's check-in policy"));
    }

    private Laboratory authorizedAdminLaboratory(Long labId, User actor) {
        if (!actor.hasRole("ADMIN")) {
            throw new AccessDeniedException("Only administrators can manage laboratory check-in policy");
        }
        return laboratoryRepository.findById(labId)
                .filter(lab -> Boolean.TRUE.equals(lab.getActive()) && !Boolean.TRUE.equals(lab.getDeleted()))
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found"));
    }

    private LabCheckinPolicyResponse toPolicyResponse(Laboratory laboratory) {
        List<LabAllowedNetworkResponse> networks = networkRepository
                .findByLaboratoryIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAscIdAsc(laboratory.getId())
                .stream()
                .map(this::toNetworkResponse)
                .toList();
        return new LabCheckinPolicyResponse(
                laboratory.getId(), laboratory.getCheckinLatitude(), laboratory.getCheckinLongitude(),
                laboratory.getCheckinRadiusMeters(), laboratory.getCheckinMaxAccuracyMeters(),
                Boolean.TRUE.equals(laboratory.getCheckinEnabled()), networks);
    }

    private LabAllowedNetworkResponse toNetworkResponse(LaboratoryAllowedNetwork network) {
        return new LabAllowedNetworkResponse(network.getId(), network.getNetworkValue(),
                Boolean.TRUE.equals(network.getActive()) && !Boolean.TRUE.equals(network.getDeleted()),
                network.getCreatedAt(), network.getUpdatedAt());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
    }
}
