package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.dto.response.EligibleCleanerResponse;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.CleaningMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.CleaningService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.CleaningStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CleaningServiceImpl implements CleaningService {

    private static final List<BookingStatus> ELIGIBLE_BOOKING_STATUSES =
            List.of(BookingStatus.APPROVED, BookingStatus.CHECKED_IN);
    private static final List<TimeSlotStatus> HIDDEN_SLOT_STATUSES =
            List.of(TimeSlotStatus.CANCELLED, TimeSlotStatus.INACTIVE, TimeSlotStatus.ARCHIVED);

    private final CleaningRepository cleaningRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public CleaningResponse createCleaningTask(Long slotId) {
        return toResponse(createCleaningTaskEntity(slotId));
    }

    @Transactional
    public CleaningEntity createCleaningTaskEntity(Long slotId) {
        return cleaningRepository.findFirstBySlotId(slotId).orElseGet(() -> {
            TimeSlot slot = timeSlotRepository.findById(slotId)
                    .orElseThrow(() -> new EntityNotFoundException("Time slot not found: " + slotId));
            CleaningEntity cleaning = CleaningEntity.builder()
                    .slot(slot)
                    .status(CleaningStatus.PENDING)
                    .build();
            return cleaningRepository.save(cleaning);
        });
    }

    @Override
    @Transactional
    public CleaningResponse assignStaff(Long cleaningId, Long staffId) {
        CleaningEntity cleaning = cleaningRepository.findById(cleaningId)
                .orElseThrow(() -> new EntityNotFoundException("Cleaning task not found: " + cleaningId));
        assertManagerOwnsLab(getCurrentUser(), cleaning.getSlot().getLab());
        assignToStudent(cleaning, staffId);
        return toResponse(cleaningRepository.save(cleaning));
    }

    @Override
    @Transactional
    public List<CleaningResponse> assignCleaningTasks(Long slotId, List<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            throw new IllegalArgumentException("At least one assignee is required");
        }

        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found: " + slotId));
        assertManagerOwnsLab(getCurrentUser(), slot.getLab());

        return assigneeIds.stream()
                .distinct()
                .map(assigneeId -> assignCleaningForSlot(slot, assigneeId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EligibleCleanerResponse> getEligibleCleaners(Long slotId) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found: " + slotId));
        assertManagerOwnsLab(getCurrentUser(), slot.getLab());

        return bookingRepository.findBySlotIdAndStatusIn(slotId, ELIGIBLE_BOOKING_STATUSES)
                .stream()
                .map(booking -> EligibleCleanerResponse.builder()
                        .userId(booking.getUser().getId())
                        .fullName(booking.getUser().getFullName())
                        .email(booking.getUser().getEmail())
                        .bookingId(booking.getId())
                        .bookingStatus(booking.getStatus())
                        .checkedIn(booking.getStatus() == BookingStatus.CHECKED_IN)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleaningResponse> getLabCleaningTasks(Long labId) {
        Laboratory lab = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Lab not found: " + labId));
        assertManagerOwnsLab(getCurrentUser(), lab);

        List<TimeSlot> slots = timeSlotRepository.findUsableByLabId(labId, Instant.now(), HIDDEN_SLOT_STATUSES);
        if (slots.isEmpty()) {
            return List.of();
        }

        List<Long> slotIds = slots.stream().map(TimeSlot::getId).toList();
        Map<Long, List<CleaningEntity>> cleaningsBySlotId = cleaningRepository
                .findActiveBySlotIdIn(slotIds)
                .stream()
                .collect(Collectors.groupingBy(cleaning -> cleaning.getSlot().getId()));

        List<CleaningResponse> responses = new ArrayList<>();
        for (TimeSlot slot : slots) {
            List<CleaningEntity> cleanings = cleaningsBySlotId.getOrDefault(slot.getId(), List.of());
            if (cleanings.isEmpty()) {
                responses.add(toPendingResponse(slot));
            } else {
                cleanings.stream()
                        .map(this::toResponse)
                        .forEach(responses::add);
            }
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleaningResponse> getMyCleaningTasks() {
        User currentUser = getCurrentUser();
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students can view their cleaning tasks");
        }
        return cleaningRepository.findActiveByStaffId(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CleaningResponse confirmCompleted(Long cleaningId) {
        return completeCleaningTask(cleaningId);
    }

    @Override
    @Transactional
    public CleaningResponse completeCleaningTask(Long cleaningId) {
        CleaningEntity cleaning = cleaningRepository.findById(cleaningId)
                .orElseThrow(() -> new EntityNotFoundException("Cleaning task not found: " + cleaningId));
        User currentUser = getCurrentUser();

        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students can complete cleaning tasks");
        }
        if (cleaning.getStaff() == null || !cleaning.getStaff().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Students can only complete their own cleaning tasks");
        }
        if (cleaning.getStatus() == CleaningStatus.PENDING) {
            throw new IllegalStateException("Cleaning task must be assigned before completion");
        }
        if (cleaning.getStatus() == CleaningStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled cleaning task cannot be completed");
        }
        if (cleaning.getStatus() == CleaningStatus.DONE || cleaning.getStatus() == CleaningStatus.COMPLETED) {
            throw new IllegalStateException("Cleaning task has already been completed");
        }

        cleaning.setStatus(CleaningStatus.DONE);
        cleaning.setCompletedAt(Instant.now());
        return toResponse(cleaningRepository.save(cleaning));
    }

    @Override
    @Transactional
    public CleaningResponse cancelCleaningTask(Long cleaningId) {
        CleaningEntity cleaning = cleaningRepository.findById(cleaningId)
                .orElseThrow(() -> new EntityNotFoundException("Cleaning task not found: " + cleaningId));
        assertManagerOwnsLab(getCurrentUser(), cleaning.getSlot().getLab());

        if (cleaning.getStatus() == CleaningStatus.DONE || cleaning.getStatus() == CleaningStatus.COMPLETED) {
            throw new IllegalStateException("Completed cleaning task cannot be cancelled");
        }

        cleaning.setStatus(CleaningStatus.CANCELLED);
        return toResponse(cleaningRepository.save(cleaning));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleaningResponse> getPendingCleanings() {
        return getMyCleaningTasks();
    }

    private CleaningEntity assignCleaningForSlot(TimeSlot slot, Long assigneeId) {
        if (cleaningRepository.existsActiveNonCancelledBySlotIdAndStaffId(slot.getId(), assigneeId)) {
            throw new IllegalStateException("Student is already assigned to this cleaning task");
        }

        CleaningEntity cleaning = cleaningRepository.findFirstBySlotIdAndStaffIsNullAndStatus(
                        slot.getId(),
                        CleaningStatus.PENDING
                )
                .orElseGet(() -> CleaningEntity.builder()
                        .slot(slot)
                        .status(CleaningStatus.PENDING)
                        .build());
        assignToStudent(cleaning, assigneeId);
        return cleaningRepository.save(cleaning);
    }

    private void assignToStudent(CleaningEntity cleaning, Long staffId) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found: " + staffId));

        if (!bookingRepository.existsBySlotIdAndUserIdAndStatusIn(
                cleaning.getSlot().getId(),
                staffId,
                ELIGIBLE_BOOKING_STATUSES
        )) {
            throw new AccessDeniedException("Student is not eligible for this cleaning task");
        }
        if (cleaning.getStatus() == CleaningStatus.DONE || cleaning.getStatus() == CleaningStatus.COMPLETED) {
            throw new IllegalStateException("Completed cleaning task cannot be reassigned");
        }
        if (cleaning.getStatus() == CleaningStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled cleaning task cannot be reassigned");
        }

        cleaning.setStaff(staff);
        cleaning.setStatus(CleaningStatus.ASSIGNED);
        cleaning.setStartedAt(Instant.now());
    }

    private CleaningResponse toResponse(CleaningEntity cleaning) {
        long participantCount = bookingRepository.countActiveByTimeSlotIdAndStatusIn(
                cleaning.getSlot().getId(),
                ELIGIBLE_BOOKING_STATUSES
        );
        return CleaningMapper.toResponse(cleaning, participantCount);
    }

    private CleaningResponse toPendingResponse(TimeSlot slot) {
        long participantCount = bookingRepository.countActiveByTimeSlotIdAndStatusIn(
                slot.getId(),
                ELIGIBLE_BOOKING_STATUSES
        );
        return CleaningResponse.builder()
                .id(null)
                .slotId(slot.getId())
                .labId(slot.getLab().getId())
                .labName(slot.getLab().getLabName())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .slotStatus(slot.getStatus())
                .participantCount(participantCount)
                .status(CleaningStatus.PENDING)
                .build();
    }

    private void assertManagerOwnsLab(User currentUser, Laboratory lab) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can manage cleaning tasks");
        }

        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot manage cleaning tasks from another lab");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
    }
}
