package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.booking.service.BookingService;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.SlotFullException;
import com.web.labportalbackend.common.exception.DuplicateBookingException;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.PessimisticLockingFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final BookingCoreService bookingCoreService;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 150;

    @Override
    public BookingResponse book(Long userId, CreateBookingRequest request) {
        log.debug("Starting booking process for user {} on slot {}", userId, request.getSlotId());
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));
        TimeSlot timeSlot = timeSlotRepository.findById(request.getSlotId()).orElseThrow(() -> new EntityNotFoundException("Time slot not found with ID: " + request.getSlotId()));
        if (bookingRepository.existsActiveBookingByUserAndSlot(userId, request.getSlotId())) {
            throw new DuplicateBookingException(userId, request.getSlotId());
        }
        BookingResponse response = attemptConfirmedBookingWithRetry(userId, request.getSlotId(), user);
        if (response != null) return response;
        return createWaitlistedBooking(user, timeSlot);
    }

    private BookingResponse attemptConfirmedBookingWithRetry(Long userId, Long slotId, User user) {
        int attemptCount = 0;
        while (attemptCount < MAX_RETRIES) {
            attemptCount++;
            try {
                return bookingCoreService.lockSlotAndBook(userId, slotId, user);
            } catch (SlotFullException e) {
                return null;
            } catch (PessimisticLockingFailureException e) {
                if (attemptCount < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS + (long)(Math.random() * 100)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
                }
            } catch (JpaSystemException e) {
                if (e.getCause() instanceof PessimisticLockingFailureException && attemptCount < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
                } else throw e;
            }
        }
        return null;
    }

    @Transactional
    private BookingResponse createWaitlistedBooking(User user, TimeSlot timeSlot) {
        Booking booking = new Booking();
        booking.setUser(user); booking.setLab(timeSlot.getLab()); booking.setTimeSlot(timeSlot);
        booking.setStartTime(timeSlot.getStartTime()); booking.setEndTime(timeSlot.getEndTime());
        booking.setStatus(BookingStatus.WAITLISTED); booking.setParticipantsCount(1);
        return BookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Override @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(BookingMapper::toResponse);
    }

    @Override @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByUser(Long userId) {
        if (!userRepository.existsById(userId)) throw new EntityNotFoundException("User not found: " + userId);
        return bookingRepository.findByUserId(userId).stream().map(BookingMapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsBySlot(Long slotId) {
        if (!timeSlotRepository.existsById(slotId)) throw new EntityNotFoundException("Slot not found: " + slotId);
        return bookingRepository.findBySlotId(slotId).stream().map(BookingMapper::toResponse).toList();
    }

    @Override @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getId().equals(userId)) throw new AccessDeniedException("You can only cancel your own bookings");
        if (booking.getStatus() == BookingStatus.CANCELLED) throw new IllegalStateException("Booking already cancelled");
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    @Override @Transactional @Deprecated(since = "1.0", forRemoval = true)
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }
}
