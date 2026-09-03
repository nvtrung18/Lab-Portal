package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.ReviewBookingRequest;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.booking.outbox.BookingOutboxService;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.impl.BookingServiceImpl;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceImplTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerApprovalNotifiesStudentForRealtimeRefresh() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        TimeSlotRepository timeSlotRepository = mock(TimeSlotRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
        NotificationEmitter notificationEmitter = mock(NotificationEmitter.class);
        BookingOutboxService outboxService = mock(BookingOutboxService.class);
        BookingServiceImpl service = new BookingServiceImpl(
                bookingRepository,
                timeSlotRepository,
                userRepository,
                mock(MembershipRepository.class),
                laboratoryRepository,
                mock(SystemConfigService.class),
                notificationEmitter,
                outboxService
        );

        User manager = mock(User.class);
        when(manager.getId()).thenReturn(3L);
        when(manager.hasRole("LAB_MANAGER")).thenReturn(true);
        User student = new User();
        student.setId(7L);
        student.setFullName("Nguyễn An");
        student.setEmail("student@example.com");
        Laboratory lab = new Laboratory();
        lab.setId(5L);
        lab.setLabName("PTN Vật lý");
        TimeSlot slot = new TimeSlot();
        slot.setId(9L);
        slot.setLab(lab);
        slot.setCapacity(10);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        slot.setStartTime(Instant.now().plusSeconds(3600));
        slot.setEndTime(Instant.now().plusSeconds(7200));
        Booking booking = new Booking();
        booking.setId(11L);
        booking.setUser(student);
        booking.setLab(lab);
        booking.setTimeSlot(slot);
        booking.setStartTime(slot.getStartTime());
        booking.setEndTime(slot.getEndTime());
        booking.setParticipantsCount(1);
        booking.setStatus(BookingStatus.PENDING_APPROVAL);

        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));
        when(bookingRepository.findById(11L)).thenReturn(Optional.of(booking));
        when(bookingRepository.countActiveByTimeSlotIdAndStatusIn(
                9L, List.of(BookingStatus.APPROVED, BookingStatus.CHECKED_IN))).thenReturn(0L);
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(timeSlotRepository.save(slot)).thenReturn(slot);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a", List.of()));

        ReviewBookingRequest request = new ReviewBookingRequest();
        request.setDecision("APPROVE");
        service.reviewBooking(11L, request);

        assertEquals(BookingStatus.APPROVED, booking.getStatus());
        verify(notificationEmitter).emit(
                7L,
                NotificationEventType.BOOKING_APPROVED,
                "Đăng ký ca sử dụng đã được phê duyệt",
                "Bạn đã được phê duyệt sử dụng PTN Vật lý.",
                NotificationTargetModule.BOOKING,
                11L,
                null
        );
        verify(outboxService).enqueueEmail(
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq(BookingEmailType.APPROVED),
                org.mockito.ArgumentMatchers.eq("student@example.com"),
                org.mockito.ArgumentMatchers.any());
    }
}
