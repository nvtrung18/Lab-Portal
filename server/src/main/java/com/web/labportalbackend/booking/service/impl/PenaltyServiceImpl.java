package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.CreatePenaltyRequest;
import com.web.labportalbackend.booking.dto.request.PenaltyConfigRequest;
import com.web.labportalbackend.booking.dto.response.PenaltyConfigResponse;
import com.web.labportalbackend.booking.dto.response.PenaltyResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.booking.mapper.PenaltyMapper;
import com.web.labportalbackend.booking.outbox.BookingOutboxService;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.PenaltyRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.PenaltyService;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.common.email.BookingEmailData;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class PenaltyServiceImpl implements PenaltyService {

    private static final List<TimeSlotStatus> HIDDEN_SLOT_STATUSES =
            List.of(TimeSlotStatus.CANCELLED, TimeSlotStatus.INACTIVE, TimeSlotStatus.ARCHIVED);

    private final PenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final MembershipRepository membershipRepository;
    private final NotificationEmitter notificationEmitter;
    private final BookingOutboxService bookingOutboxService;
    private final AtomicReference<BigDecimal> configuredAmount = new AtomicReference<>();

    @Value("${booking.penalty.default-amount:0}")
    private BigDecimal defaultAmount;

    @Override
    public PenaltyConfigResponse updateConfig(PenaltyConfigRequest request) {
        configuredAmount.set(request.getAmount());
        return PenaltyConfigResponse.builder()
                .amount(request.getAmount())
                .build();
    }

    @Override
    public BigDecimal getCurrentPenaltyAmount() {
        BigDecimal amount = configuredAmount.get();
        return amount != null ? amount : defaultAmount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PenaltyResponse> getUserPenalties(Long userId) {
        var currentUser = getCurrentUser();
        if (currentUser.hasRole("STUDENT") && !currentUser.getId().equals(userId)) {
            throw new AccessDeniedException("Students can only view their own penalties");
        }

        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        return penaltyRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PenaltyMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PenaltyResponse> getMyPenalties() {
        return getUserPenalties(getCurrentUser().getId());
    }

    @Override
    @Transactional
    public PenaltyResponse createPenalty(CreatePenaltyRequest request) {
        User manager = getCurrentUser();
        TimeSlot slot = timeSlotRepository.findActiveById(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found: " + request.getSlotId()));
        Laboratory managedLab = assertManagerOwnsLab(manager, slot.getLab());
        if (slot.getEndTime().isBefore(java.time.Instant.now()) || HIDDEN_SLOT_STATUSES.contains(slot.getStatus())) {
            throw new IllegalStateException("Không thể ghi nhận vi phạm cho ca sử dụng đã kết thúc hoặc không còn hiệu lực.");
        }
        User student = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found: " + request.getUserId()));

        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findById(request.getBookingId())
                    .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + request.getBookingId()));
            if (booking.getTimeSlot() == null || !booking.getTimeSlot().getId().equals(slot.getId())) {
                throw new AccessDeniedException("Booking does not belong to this time slot");
            }
            if (!booking.getUser().getId().equals(student.getId())) {
                throw new AccessDeniedException("Booking does not belong to this student");
            }
            if (!booking.getLab().getId().equals(managedLab.getId())) {
                throw new AccessDeniedException("Cannot create penalties for another lab");
            }
            if (penaltyRepository.existsByBookingIdAndTypeAndStatus(
                    booking.getId(),
                    request.getType(),
                    PenaltyStatus.ACTIVE
            )) {
                throw new IllegalStateException("Vi phạm này đã được ghi nhận cho sinh viên trong ca sử dụng.");
            }
        } else if (!membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                student.getId(),
                managedLab.getId()
        )) {
            throw new AccessDeniedException("Student is not an active member of this lab");
        }

        PenaltyEntity penalty = PenaltyEntity.builder()
                .user(student)
                .lab(managedLab)
                .slot(slot)
                .booking(booking)
                .createdBy(manager)
                .type(request.getType())
                .point(request.getPoint() != null ? request.getPoint() : 0)
                .reason(request.getReason().trim())
                .amount(getCurrentPenaltyAmount())
                .status(PenaltyStatus.ACTIVE)
                .build();

        PenaltyEntity saved = penaltyRepository.save(penalty);
        String message = "Quản lý PTN đã ghi nhận vi phạm cho ca sử dụng tại " + managedLab.getLabName() + ".";
        notificationEmitter.emit(student.getId(), NotificationEventType.PENALTY_CREATED,
                "Đã ghi nhận vi phạm", message, NotificationTargetModule.BOOKING,
                booking != null ? booking.getId() : slot.getId(), null);
        bookingOutboxService.enqueueEmail(booking != null ? booking.getId() : slot.getId(),
                BookingEmailType.PENALTY_CREATED, student.getEmail(), BookingEmailData.builder()
                        .studentName(student.getFullName())
                        .labName(managedLab.getLabName())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .status(saved.getStatus().name())
                        .note(saved.getReason())
                        .build());
        return PenaltyMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PenaltyResponse> getSlotPenalties(Long slotId) {
        User manager = getCurrentUser();
        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found: " + slotId));
        assertManagerOwnsLab(manager, slot.getLab());

        return penaltyRepository.findActiveBySlotId(slotId)
                .stream()
                .map(PenaltyMapper::toResponse)
                .toList();
    }

    private Laboratory assertManagerOwnsLab(User currentUser, Laboratory lab) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can create penalties");
        }
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot create penalties for another lab");
        }
        return managedLab;
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
