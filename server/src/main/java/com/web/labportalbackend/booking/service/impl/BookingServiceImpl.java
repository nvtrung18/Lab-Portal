package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.booking.dto.request.ReviewBookingRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.BookingService;
import com.web.labportalbackend.common.email.BookingEmailData;
import com.web.labportalbackend.common.email.EmailService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.common.exception.DuplicateBookingException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public BookingResponse bookCurrentUser(CreateBookingRequest request) {
        return book(getCurrentUser().getId(), request);
    }

    @Override
    @Transactional
    public BookingResponse book(Long userId, CreateBookingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));
        TimeSlot slot = timeSlotRepository.findActiveById(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found with ID: " + request.getSlotId()));

        assertStudentCanBook(user, slot);

        if (bookingRepository.existsActiveBookingByUserAndSlot(userId, request.getSlotId())) {
            throw new DuplicateBookingException(userId, request.getSlotId());
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setLab(slot.getLab());
        booking.setTimeSlot(slot);
        booking.setStartTime(slot.getStartTime());
        booking.setEndTime(slot.getEndTime());
        booking.setStatus(BookingStatus.PENDING_APPROVAL);
        booking.setPurpose(request.getPurpose());
        booking.setParticipantsCount(request.getParticipantsCount() != null ? request.getParticipantsCount() : 1);

        Booking saved = bookingRepository.save(booking);
        safeSendEmail(() -> emailService.sendBookingCreatedEmail(
                saved.getUser().getEmail(),
                buildEmailData(saved, "Chờ phê duyệt", "Vui lòng chờ quản lý PTN phê duyệt.")
        ));
        return BookingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(BookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings() {
        return bookingRepository.findActiveByUserId(getCurrentUser().getId()).stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        return bookingRepository.findByUserId(userId).stream().map(BookingMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsBySlot(Long slotId) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        assertManagerOwnsLab(getCurrentUser(), slot.getLab());
        return bookingRepository.findBySlotId(slotId).stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse cancelCurrentUserBooking(Long bookingId) {
        User user = getCurrentUser();
        cancelBooking(bookingId, user.getId());
        return BookingMapper.toResponse(bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId)));
    }

    @Override
    @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You can only cancel your own bookings");
        }
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL && booking.getStatus() != BookingStatus.APPROVED) {
            throw new IllegalStateException("Booking cannot be cancelled in current status");
        }
        if (booking.getStatus() == BookingStatus.APPROVED && !booking.getStartTime().isAfter(Instant.now())) {
            throw new IllegalStateException("Approved booking cannot be cancelled after start time");
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_STUDENT);
        Booking saved = bookingRepository.save(booking);
        safeSendEmail(() -> emailService.sendBookingCancelledByStudentEmail(
                saved.getUser().getEmail(),
                buildEmailData(saved, "Sinh viên đã hủy", null)
        ));
        refreshSlotFullStatus(booking.getTimeSlot());
    }

    @Override
    @Transactional
    public BookingResponse reviewBooking(Long bookingId, ReviewBookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        assertManagerOwnsLab(getCurrentUser(), booking.getLab());

        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only pending bookings can be reviewed");
        }

        String decision = request.getDecision().trim().toUpperCase();
        if ("APPROVE".equals(decision)) {
            long approvedCount = bookingRepository.countActiveByTimeSlotIdAndStatusIn(
                    booking.getTimeSlot().getId(),
                    List.of(BookingStatus.APPROVED, BookingStatus.CHECKED_IN)
            );
            if (approvedCount >= booking.getTimeSlot().getCapacity()) {
                throw new IllegalStateException("Khung giờ đã đủ số lượng.");
            }
            booking.setStatus(BookingStatus.APPROVED);
        } else if ("REJECT".equals(decision)) {
            booking.setStatus(BookingStatus.REJECTED);
        } else {
            throw new IllegalArgumentException("Decision must be APPROVE or REJECT");
        }

        Booking saved = bookingRepository.save(booking);
        refreshSlotFullStatus(saved.getTimeSlot());
        if (saved.getStatus() == BookingStatus.APPROVED) {
            safeSendEmail(() -> emailService.sendBookingApprovedEmail(
                    saved.getUser().getEmail(),
                    buildEmailData(saved, "Đã phê duyệt", "Khi đến giờ sử dụng, sinh viên vui lòng mở hệ thống để tạo mã QR check-in và đưa cho quản lý PTN quét xác nhận.")
            ));
        } else if (saved.getStatus() == BookingStatus.REJECTED) {
            safeSendEmail(() -> emailService.sendBookingRejectedEmail(
                    saved.getUser().getEmail(),
                    buildEmailData(saved, "Không được phê duyệt", request.getNote())
            ));
        }
        return BookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @Deprecated(since = "1.0", forRemoval = true)
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        booking.setStatus(BookingStatus.CANCELLED_BY_STUDENT);
        bookingRepository.save(booking);
    }

    private void assertStudentCanBook(User user, TimeSlot slot) {
        if (!user.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students can register for time slots");
        }
        if (!membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(
                user.getId(),
                slot.getLab().getId()
        )) {
            throw new AccessDeniedException("Student is not an active member of this lab");
        }
        if (slot.getStatus() != TimeSlotStatus.AVAILABLE) {
            throw new IllegalStateException("Time slot is not available for registration");
        }
        if (!slot.getStartTime().isAfter(Instant.now())) {
            throw new IllegalStateException("Time slot has already started or expired");
        }
    }

    private void refreshSlotFullStatus(TimeSlot slot) {
        if (slot == null || slot.getStatus() == TimeSlotStatus.CANCELLED) {
            return;
        }
        long approvedCount = bookingRepository.countActiveByTimeSlotIdAndStatusIn(
                slot.getId(),
                List.of(BookingStatus.APPROVED, BookingStatus.CHECKED_IN)
        );
        if (approvedCount >= slot.getCapacity()) {
            slot.setStatus(TimeSlotStatus.FULL);
        } else if (slot.getStatus() == TimeSlotStatus.FULL) {
            slot.setStatus(TimeSlotStatus.AVAILABLE);
        }
        timeSlotRepository.save(slot);
    }

    private BookingEmailData buildEmailData(Booking booking, String status, String note) {
        return BookingEmailData.builder()
                .studentName(booking.getUser().getFullName())
                .labName(booking.getLab().getLabName())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(status)
                .note(note)
                .build();
    }

    private void safeSendEmail(Runnable emailAction) {
        try {
            emailAction.run();
        } catch (RuntimeException ex) {
            log.warn("Could not send booking notification email: {}", ex.getMessage());
        }
    }

    private void assertManagerOwnsLab(User currentUser, Laboratory lab) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can manage bookings");
        }
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (!managedLab.getId().equals(lab.getId())) {
            throw new AccessDeniedException("Cannot manage bookings from another lab");
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
}
