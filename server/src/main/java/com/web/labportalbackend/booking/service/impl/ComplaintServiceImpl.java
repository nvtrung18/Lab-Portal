package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.ComplaintRequest;
import com.web.labportalbackend.booking.dto.request.ReviewComplaintRequest;
import com.web.labportalbackend.booking.dto.response.ComplaintResponse;
import com.web.labportalbackend.booking.entity.ComplaintEntity;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.booking.mapper.ComplaintMapper;
import com.web.labportalbackend.booking.repository.ComplaintRepository;
import com.web.labportalbackend.booking.repository.PenaltyRepository;
import com.web.labportalbackend.booking.service.ComplaintService;
import com.web.labportalbackend.common.enums.ComplaintStatus;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final PenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;

    @Override
    @Transactional
    public ComplaintResponse submitComplaint(ComplaintRequest request) {
        User user = getCurrentUser();
        PenaltyEntity penalty = penaltyRepository.findById(request.getPenaltyId())
                .orElseThrow(() -> new EntityNotFoundException("Penalty not found: " + request.getPenaltyId()));

        if (!penalty.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Students can only complaint their own penalties");
        }

        if (penalty.getStatus() != PenaltyStatus.ACTIVE) {
            throw new IllegalStateException("Only active penalties can be complained");
        }

        if (complaintRepository.existsByPenaltyIdAndStatus(penalty.getId(), ComplaintStatus.PENDING)
                || complaintRepository.existsByPenaltyId(penalty.getId())) {
            throw new IllegalStateException("Complaint already exists for this penalty");
        }

        ComplaintEntity complaint = ComplaintEntity.builder()
                .user(user)
                .penalty(penalty)
                .content(request.getContent())
                .status(ComplaintStatus.PENDING)
                .build();

        return ComplaintMapper.toResponse(complaintRepository.save(complaint));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplaintResponse> getLabComplaints(Long labId) {
        Laboratory lab = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Lab not found: " + labId));
        assertManagerOwnsLab(getCurrentUser(), lab);
        return complaintRepository.findActiveByLabId(labId).stream()
                .map(ComplaintMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ComplaintResponse reviewComplaint(Long complaintId, ReviewComplaintRequest request) {
        ComplaintEntity complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new EntityNotFoundException("Complaint not found: " + complaintId));
        PenaltyEntity penalty = complaint.getPenalty();
        if (penalty == null || penalty.getBooking() == null) {
            throw new IllegalStateException("Complaint is not linked to a penalty");
        }
        assertManagerOwnsLab(getCurrentUser(), penalty.getBooking().getLab());

        if (complaint.getStatus() != ComplaintStatus.PENDING) {
            throw new IllegalStateException("Complaint has already been reviewed");
        }

        String decision = request.getDecision().trim().toUpperCase();
        if ("APPROVE".equals(decision) || "APPROVED".equals(decision)) {
            complaint.setStatus(ComplaintStatus.APPROVED);
            penalty.setStatus(PenaltyStatus.RESOLVED);
        } else if ("REJECT".equals(decision) || "REJECTED".equals(decision)) {
            complaint.setStatus(ComplaintStatus.REJECTED);
        } else {
            throw new IllegalArgumentException("Decision must be APPROVE or REJECT");
        }

        complaint.setResolutionNote(request.getNote());
        complaint.setResolvedAt(Instant.now());
        penaltyRepository.save(penalty);
        return ComplaintMapper.toResponse(complaintRepository.save(complaint));
    }

    private void assertManagerOwnsLab(User currentUser, Laboratory lab) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can review complaints");
        }

        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot review complaints from another lab");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + authentication.getName()));
    }
}
