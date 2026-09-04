package com.web.labportalbackend;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.booking.outbox.BookingOutboxService;
import com.web.labportalbackend.common.email.BookingEmailData;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.PenaltyRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.CleaningService;
import com.web.labportalbackend.booking.service.PenaltyService;
import com.web.labportalbackend.booking.service.impl.BookingTaskServiceImpl;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import com.web.labportalbackend.common.enums.PenaltyType;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private NotificationEmitter notificationEmitter;

    @Mock
    private BookingOutboxService bookingOutboxService;

    @InjectMocks
    private BookingTaskServiceImpl bookingTaskService;

    @Test
    void processNoShows_marksOverdueBookingNoShowAndQueuesOneUserEmail() {
        when(systemConfigService.getConfig()).thenReturn(systemConfig(15));

        User user = new User();
        user.setId(10L);
        user.setEmail("student@example.com");
        user.setFullName("Student A");
        Laboratory lab = new Laboratory();
        lab.setId(20L);
        lab.setLabName("Lab A");
        TimeSlot slot = new TimeSlot();
        slot.setId(30L);
        slot.setLab(lab);
        slot.setStartTime(Instant.parse("2026-09-04T01:00:00Z"));
        slot.setEndTime(Instant.parse("2026-09-04T03:00:00Z"));

        Booking booking = new Booking();
        booking.setId(99L);
        booking.setUser(user);
        booking.setLab(lab);
        booking.setTimeSlot(slot);
        booking.setStatus(BookingStatus.APPROVED);
        booking.setStartTime(Instant.now().minusSeconds(1800));

        when(bookingRepository.findNoShowCandidates(eq(BookingStatus.APPROVED), any(Instant.class)))
                .thenReturn(List.of(booking));
        when(penaltyRepository.existsByBookingIdAndTypeAndStatus(99L, PenaltyType.NO_SHOW, PenaltyStatus.ACTIVE))
                .thenReturn(false);
        when(penaltyService.getCurrentPenaltyAmount()).thenReturn(BigDecimal.valueOf(50_000));

        int processed = bookingTaskService.processNoShows();

        assertEquals(1, processed);
        assertEquals(BookingStatus.NO_SHOW, booking.getStatus());
        verify(bookingRepository).save(booking);

        ArgumentCaptor<PenaltyEntity> penaltyCaptor = ArgumentCaptor.forClass(PenaltyEntity.class);
        verify(penaltyRepository).save(penaltyCaptor.capture());
        PenaltyEntity penalty = penaltyCaptor.getValue();

        assertSame(user, penalty.getUser());
        assertSame(lab, penalty.getLab());
        assertSame(slot, penalty.getSlot());
        assertSame(booking, penalty.getBooking());
        assertEquals(PenaltyType.NO_SHOW, penalty.getType());
        assertEquals(1, penalty.getPoint());
        assertEquals("Vắng mặt không thông báo", penalty.getReason());
        assertEquals(BigDecimal.valueOf(50_000), penalty.getAmount());
        assertEquals(PenaltyStatus.ACTIVE, penalty.getStatus());
        verify(notificationEmitter).emit(10L, NotificationEventType.BOOKING_NO_SHOW,
                "Vắng mặt không thông báo", "Bạn đã không check-in cho ca sử dụng tại Lab A. "
                + "Hệ thống đã ghi nhận vi phạm vắng mặt không thông báo.",
                NotificationTargetModule.BOOKING, 99L, null);
        verify(bookingOutboxService).enqueueEmail(eq(99L), eq(BookingEmailType.NO_SHOW),
                eq("student@example.com"), argThat((BookingEmailData data) ->
                        "Student A".equals(data.getStudentName())
                                && "Lab A".equals(data.getLabName())
                                && "Vắng mặt không thông báo".equals(data.getNote())));
    }

    @Test
    void expirePastCheckinSlots_marksAvailableAndFullSlotsExpired() {
        when(systemConfigService.getConfig()).thenReturn(systemConfig(15));
        TimeSlot available = new TimeSlot();
        available.setStatus(TimeSlotStatus.AVAILABLE);
        TimeSlot full = new TimeSlot();
        full.setStatus(TimeSlotStatus.FULL);
        when(timeSlotRepository.findCheckinExpiredSlots(
                eq(List.of(TimeSlotStatus.AVAILABLE, TimeSlotStatus.FULL)), any(Instant.class)))
                .thenReturn(List.of(available, full));

        int expired = bookingTaskService.expirePastCheckinSlots();

        assertEquals(2, expired);
        assertEquals(TimeSlotStatus.EXPIRED, available.getStatus());
        assertEquals(TimeSlotStatus.EXPIRED, full.getStatus());
        verify(timeSlotRepository).saveAll(List.of(available, full));
    }

    @Test
    void completeEndedSessions_marksCheckedInAndInProgressBookingsCompleted() {
        Booking checkedIn = new Booking();
        User firstUser = new User();
        firstUser.setId(1L);
        Laboratory lab = new Laboratory();
        lab.setLabName("PTN Hóa học");
        checkedIn.setId(11L);
        checkedIn.setUser(firstUser);
        checkedIn.setLab(lab);
        checkedIn.setStatus(BookingStatus.CHECKED_IN);
        Booking inProgress = new Booking();
        User secondUser = new User();
        secondUser.setId(2L);
        inProgress.setId(12L);
        inProgress.setUser(secondUser);
        inProgress.setLab(lab);
        inProgress.setStatus(BookingStatus.IN_PROGRESS);
        TimeSlot endedSlot = new TimeSlot();
        endedSlot.setStatus(TimeSlotStatus.EXPIRED);
        when(bookingRepository.findEndedSessionCandidates(
                eq(List.of(BookingStatus.CHECKED_IN, BookingStatus.IN_PROGRESS)), any(Instant.class)))
                .thenReturn(List.of(checkedIn, inProgress));
        when(timeSlotRepository.findEndedSessionSlots(
                eq(List.of(TimeSlotStatus.AVAILABLE, TimeSlotStatus.FULL, TimeSlotStatus.EXPIRED)), any(Instant.class)))
                .thenReturn(List.of(endedSlot));

        int completed = bookingTaskService.completeEndedSessions();

        assertEquals(2, completed);
        assertEquals(BookingStatus.COMPLETED, checkedIn.getStatus());
        assertEquals(BookingStatus.COMPLETED, inProgress.getStatus());
        assertEquals(TimeSlotStatus.CLOSED, endedSlot.getStatus());
        verify(bookingRepository).saveAll(List.of(checkedIn, inProgress));
        verify(timeSlotRepository).saveAll(List.of(endedSlot));
        verify(notificationEmitter).emit(1L, NotificationEventType.BOOKING_SESSION_COMPLETED,
                "Ca sử dụng đã kết thúc", "Ca sử dụng tại PTN Hóa học đã tự động kết thúc theo lịch.",
                NotificationTargetModule.BOOKING, 11L, null);
        verify(notificationEmitter).emit(2L, NotificationEventType.BOOKING_SESSION_COMPLETED,
                "Ca sử dụng đã kết thúc", "Ca sử dụng tại PTN Hóa học đã tự động kết thúc theo lịch.",
                NotificationTargetModule.BOOKING, 12L, null);
    }

    private SystemConfigResponse systemConfig(int checkinWindowMinutes) {
        return new SystemConfigResponse(null, null,
                new SystemConfigResponse.BookingConfig(checkinWindowMinutes, 0, true, true),
                null, null);
    }
}
