package com.web.labportalbackend;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.PenaltyRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.CleaningService;
import com.web.labportalbackend.booking.service.PenaltyService;
import com.web.labportalbackend.booking.service.impl.BookingTaskServiceImpl;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingTaskServiceNoShowTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PenaltyRepository penaltyRepository;

    @Mock
    private PenaltyService penaltyService;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private CleaningRepository cleaningRepository;

    @Mock
    private CleaningService cleaningService;

    @InjectMocks
    private BookingTaskServiceImpl bookingTaskService;

    @Test
    void processNoShows_marksOverdueBookingNoShowAndCreatesPenalty() {
        ReflectionTestUtils.setField(bookingTaskService, "noShowGraceMinutes", 15L);

        User user = new User();
        user.setId(10L);

        Booking booking = new Booking();
        booking.setId(99L);
        booking.setUser(user);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setEndTime(Instant.now().minusSeconds(1800));

        when(bookingRepository.findNoShowCandidates(eq(BookingStatus.CONFIRMED), any(Instant.class)))
                .thenReturn(List.of(booking));
        when(penaltyRepository.existsByBookingId(99L)).thenReturn(false);
        when(penaltyService.getCurrentPenaltyAmount()).thenReturn(BigDecimal.valueOf(50_000));

        int processed = bookingTaskService.processNoShows();

        assertEquals(1, processed);
        assertEquals(BookingStatus.NO_SHOW, booking.getStatus());
        verify(bookingRepository).save(booking);

        ArgumentCaptor<PenaltyEntity> penaltyCaptor = ArgumentCaptor.forClass(PenaltyEntity.class);
        verify(penaltyRepository).save(penaltyCaptor.capture());
        PenaltyEntity penalty = penaltyCaptor.getValue();

        assertSame(user, penalty.getUser());
        assertSame(booking, penalty.getBooking());
        assertEquals("Vắng mặt không thông báo", penalty.getReason());
        assertEquals(BigDecimal.valueOf(50_000), penalty.getAmount());
        assertEquals(PenaltyStatus.ACTIVE, penalty.getStatus());
    }
}
