package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.SlotFullException;
import com.web.labportalbackend.auth.entity.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCoreService {
    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Transactional(timeout = 5)
    public BookingResponse lockSlotAndBook(Long userId, Long slotId, User user) {
        TimeSlot timeSlot = timeSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found with ID: " + slotId));
        long confirmedCount = bookingRepository.countByTimeSlotIdAndStatus(slotId, BookingStatus.CONFIRMED);
        if (confirmedCount >= timeSlot.getCapacity()) {
            throw new SlotFullException(slotId, timeSlot.getCapacity(), (int) confirmedCount);
        }
        Booking booking = new Booking();
        booking.setUser(user); booking.setLab(timeSlot.getLab()); booking.setTimeSlot(timeSlot);
        booking.setStartTime(timeSlot.getStartTime()); booking.setEndTime(timeSlot.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED); booking.setParticipantsCount(1);
        return BookingMapper.toResponse(bookingRepository.save(booking));
    }
}
