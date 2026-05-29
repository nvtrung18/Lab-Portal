package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.booking.dto.request.CancelTimeSlotRequest;
import com.web.labportalbackend.booking.dto.request.CreateTimeSlotRequest;
import com.web.labportalbackend.booking.dto.response.TimeSlotResponse;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.TimeSlotMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.TimeSlotService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.common.email.EmailService;
import com.web.labportalbackend.common.email.SlotCancelledEmailData;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {

    private static final List<TimeSlotStatus> HIDDEN_SLOT_STATUSES =
            List.of(TimeSlotStatus.CANCELLED, TimeSlotStatus.INACTIVE, TimeSlotStatus.ARCHIVED);

    private final TimeSlotRepository timeSlotRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final EmailService emailService;
    private final SystemConfigService systemConfigService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public TimeSlotResponse createSlot(CreateTimeSlotRequest request) {
        User currentUser = getCurrentUser();
        Laboratory lab = laboratoryRepository.findById(request.getLabId())
                .orElseThrow(() -> new EntityNotFoundException("Lab not found: " + request.getLabId()));

        assertCanCreateSlot(currentUser, lab);
        validateCreateRequest(request, lab);

        TimeSlotStatus status = request.getStatus() != null
                ? request.getStatus()
                : TimeSlotStatus.AVAILABLE;

        TimeSlot slot = TimeSlot.builder()
                .lab(lab)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .capacity(request.getCapacity())
                .status(status)
                .build();

        TimeSlot saved = timeSlotRepository.save(slot);
        auditLogService.log(
                currentUser,
                AuditAction.CREATE_SLOT,
                AuditModule.BOOKING,
                "TIME_SLOT",
                saved.getId(),
                "Manager đã tạo ca sử dụng cho " + lab.getLabName() + "."
        );
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getSlotsByLab(Long labId) {
        Laboratory lab = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Lab not found: " + labId));

        assertCanViewSlots(getCurrentUser(), lab);

        SystemConfigResponse.BookingConfig bookingConfig = systemConfig().booking();
        Instant cutoff = bookingConfig.hidePastSlots() ? Instant.now() : Instant.EPOCH;
        List<TimeSlotStatus> hiddenStatuses = bookingConfig.hideCancelledSlots()
                ? HIDDEN_SLOT_STATUSES
                : List.of(TimeSlotStatus.INACTIVE, TimeSlotStatus.ARCHIVED);
        return timeSlotRepository.findUsableByLabId(labId, cutoff, hiddenStatuses).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TimeSlotResponse getSlotById(Long slotId) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        assertCanViewSlots(getCurrentUser(), slot.getLab());
        return toResponse(slot);
    }

    @Override
    @Transactional
    public TimeSlotResponse updateSlotStatus(Long slotId, String status) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        assertCanManageSlot(getCurrentUser(), slot.getLab());
        slot.setStatus(TimeSlotStatus.valueOf(status.toUpperCase()));
        return toResponse(timeSlotRepository.save(slot));
    }

    @Override
    @Transactional
    public TimeSlotResponse cancelSlot(Long slotId, CancelTimeSlotRequest request) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        User currentUser = getCurrentUser();
        assertCanManageSlot(currentUser, slot.getLab());

        if (!slot.getStartTime().isAfter(java.time.Instant.now())) {
            throw new IllegalStateException("Cannot cancel an expired time slot");
        }

        slot.setStatus(TimeSlotStatus.CANCELLED);
        List<com.web.labportalbackend.booking.entity.Booking> affectedBookings =
                bookingRepository.findBySlotIdAndStatusIn(
                        slotId,
                        List.of(BookingStatus.PENDING_APPROVAL, BookingStatus.APPROVED)
                );
        affectedBookings.forEach(booking -> booking.setStatus(BookingStatus.CANCELLED_BY_MANAGER));
        bookingRepository.saveAll(affectedBookings);
        TimeSlot savedSlot = timeSlotRepository.save(slot);

        if (Boolean.TRUE.equals(request.getNotifyByEmail())) {
            notifySlotCancelled(currentUser, savedSlot, affectedBookings, request.getReason());
        }

        auditLogService.log(
                currentUser,
                AuditAction.CANCEL_SLOT,
                AuditModule.BOOKING,
                "TIME_SLOT",
                savedSlot.getId(),
                "Manager đã hủy ca sử dụng của " + savedSlot.getLab().getLabName() + "."
        );

        return toResponse(savedSlot);
    }

    @Override
    @Transactional
    public void deleteSlot(Long slotId) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        assertCanManageSlot(getCurrentUser(), slot.getLab());
        slot.setDeleted(true);
        timeSlotRepository.save(slot);
    }

    private void validateCreateRequest(CreateTimeSlotRequest request, Laboratory lab) {
        if (lab.getStatus() != LabStatus.AVAILABLE) {
            throw new IllegalStateException("Lab is not available for time slot creation");
        }

        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }

        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }

        boolean hasOverlap = !timeSlotRepository
                .findOverlappingSlots(lab.getId(), request.getStartTime(), request.getEndTime())
                .isEmpty();
        if (hasOverlap) {
            throw new IllegalStateException("Time slot overlaps with an existing slot in this lab");
        }
    }

    private void assertCanViewSlots(User currentUser, Laboratory lab) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsLab(currentUser, lab);
            return;
        }

        if (currentUser.hasRole("STUDENT") &&
                membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                        currentUser.getId(),
                        lab.getId()
                )) {
            return;
        }

        throw new AccessDeniedException("Cannot view time slots for this lab");
    }

    private void assertCanCreateSlot(User currentUser, Laboratory lab) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsLab(currentUser, lab);
            return;
        }

        throw new AccessDeniedException("Only lab managers can create time slots");
    }

    private void assertCanManageSlot(User currentUser, Laboratory lab) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerOwnsLab(currentUser, lab);
            return;
        }

        throw new AccessDeniedException("Only lab managers can manage time slots");
    }

    private void assertManagerOwnsLab(User currentUser, Laboratory lab) {
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));

        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot manage time slots from another lab");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
    }

    private TimeSlotResponse toResponse(TimeSlot slot) {
        long approvedCount = bookingRepository.countActiveByTimeSlotIdAndStatusIn(
                slot.getId(),
                List.of(BookingStatus.APPROVED, BookingStatus.CHECKED_IN)
        );
        long checkedInCount = bookingRepository.countActiveByTimeSlotIdAndStatus(
                slot.getId(),
                BookingStatus.CHECKED_IN
        );
        long pendingCount = bookingRepository.countActiveByTimeSlotIdAndStatus(
                slot.getId(),
                BookingStatus.PENDING_APPROVAL
        );
        return TimeSlotMapper.toResponse(slot, approvedCount, checkedInCount, pendingCount);
    }

    private SystemConfigResponse systemConfig() {
        return systemConfigService.getConfig();
    }

    private void notifySlotCancelled(
            User manager,
            TimeSlot slot,
            List<com.web.labportalbackend.booking.entity.Booking> bookings,
            String reason
    ) {
        SlotCancelledEmailData emailData = SlotCancelledEmailData.builder()
                .labName(slot.getLab().getLabName())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .reason(reason)
                .managerName(manager.getFullName() != null ? manager.getFullName() : manager.getEmail())
                .build();
        bookings.forEach(booking -> {
            try {
                emailService.sendSlotCancelledEmail(booking.getUser().getEmail(), emailData);
            } catch (RuntimeException ex) {
                log.warn("Could not send slot cancellation email for booking {}: {}", booking.getId(), ex.getMessage());
            }
        });
    }
}
