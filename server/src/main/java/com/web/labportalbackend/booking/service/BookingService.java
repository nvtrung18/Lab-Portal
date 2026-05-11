package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * Service interface for booking operations.
 */
public interface BookingService {
    BookingResponse book(Long userId, CreateBookingRequest request);
    Page<BookingResponse> getAllBookings(Pageable pageable);
    List<BookingResponse> getBookingsByUser(Long userId);
    List<BookingResponse> getBookingsBySlot(Long slotId);
    void cancelBooking(Long bookingId, Long userId);
    @Deprecated(since = "1.0", forRemoval = true) void cancelBooking(Long bookingId);
}
