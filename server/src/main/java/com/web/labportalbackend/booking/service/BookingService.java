package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.booking.dto.request.ReviewBookingRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * Service interface for booking operations.
 */
public interface BookingService {
    BookingResponse book(Long userId, CreateBookingRequest request);
    BookingResponse bookCurrentUser(CreateBookingRequest request);
    Page<BookingResponse> getAllBookings(Pageable pageable);
    List<BookingResponse> getMyBookings();
    List<BookingResponse> getBookingsByUser(Long userId);
    List<BookingResponse> getBookingsBySlot(Long slotId);
    void cancelBooking(Long bookingId, Long userId);
    BookingResponse cancelCurrentUserBooking(Long bookingId);
    BookingResponse reviewBooking(Long bookingId, ReviewBookingRequest request);
    @Deprecated(since = "1.0", forRemoval = true) void cancelBooking(Long bookingId);
}
