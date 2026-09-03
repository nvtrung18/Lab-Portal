package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotBookingCounts;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.impl.TimeSlotServiceImpl;
import com.web.labportalbackend.common.email.EmailService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class TimeSlotServiceImplTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerCompletesRunningSlotAndCheckedInBookings() {
        TimeSlotRepository slotRepository = mock(TimeSlotRepository.class);
        LaboratoryRepository labRepository = mock(LaboratoryRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        NotificationEmitter notificationEmitter = mock(NotificationEmitter.class);
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(slotRepository, labRepository,
                membershipRepository, userRepository, bookingRepository, mock(EmailService.class),
                mock(SystemConfigService.class), auditLogService, notificationEmitter);

        User manager = mock(User.class);
        when(manager.getId()).thenReturn(3L);
        when(manager.hasRole("LAB_MANAGER")).thenReturn(true);
        Laboratory lab = new Laboratory();
        lab.setId(5L);
        lab.setLabName("PTN Vật lý");
        TimeSlot slot = new TimeSlot();
        slot.setId(9L);
        slot.setLab(lab);
        slot.setStatus(TimeSlotStatus.EXPIRED);
        slot.setCapacity(10);
        slot.setStartTime(Instant.now().minusSeconds(300));
        slot.setEndTime(Instant.now().plusSeconds(600));
        Booking booking = new Booking();
        User student = new User();
        student.setId(7L);
        booking.setId(11L);
        booking.setUser(student);
        booking.setStatus(BookingStatus.CHECKED_IN);

        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(labRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));
        when(slotRepository.findActiveById(9L)).thenReturn(Optional.of(slot));
        when(bookingRepository.findBySlotIdAndStatusIn(9L,
                List.of(BookingStatus.CHECKED_IN, BookingStatus.IN_PROGRESS))).thenReturn(List.of(booking));
        when(slotRepository.save(slot)).thenReturn(slot);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a", List.of()));

        service.completeSlot(9L);

        assertEquals(TimeSlotStatus.CLOSED, slot.getStatus());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        verify(bookingRepository).saveAll(List.of(booking));
        verify(auditLogService).log(manager, AuditAction.COMPLETE_LAB_SESSION, AuditModule.BOOKING,
                "TIME_SLOT", 9L, "Manager đã kết thúc ca sử dụng lab.");
        verify(notificationEmitter).emit(7L, NotificationEventType.BOOKING_SESSION_COMPLETED,
                "Ca sử dụng đã kết thúc", "Quản lý đã kết thúc ca sử dụng tại PTN Vật lý.",
                NotificationTargetModule.BOOKING, 11L, null);
    }

    @Test
    void managerListingKeepsRunningSlotAfterCheckinWindowExpires() {
        TimeSlotRepository slotRepository = mock(TimeSlotRepository.class);
        LaboratoryRepository labRepository = mock(LaboratoryRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SystemConfigService systemConfigService = mock(SystemConfigService.class);
        TimeSlotServiceImpl service = new TimeSlotServiceImpl(slotRepository, labRepository,
                membershipRepository, userRepository, bookingRepository, mock(EmailService.class),
                systemConfigService, mock(AuditLogService.class), mock(NotificationEmitter.class));

        User manager = mock(User.class);
        when(manager.getId()).thenReturn(3L);
        when(manager.hasRole("LAB_MANAGER")).thenReturn(true);
        Laboratory lab = new Laboratory();
        lab.setId(5L);
        TimeSlot runningSlot = new TimeSlot();
        runningSlot.setId(9L);
        runningSlot.setLab(lab);
        runningSlot.setStatus(TimeSlotStatus.EXPIRED);
        runningSlot.setCapacity(10);
        runningSlot.setStartTime(Instant.now().minusSeconds(900));
        runningSlot.setEndTime(Instant.now().plusSeconds(900));

        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(labRepository.findById(5L)).thenReturn(Optional.of(lab));
        when(labRepository.findFirstByManagerIdAndDeletedFalse(3L)).thenReturn(Optional.of(lab));
        when(systemConfigService.getConfig()).thenReturn(new SystemConfigResponse(
                null, null, new SystemConfigResponse.BookingConfig(10, 30, true, true), null, null));
        when(slotRepository.findUsableByLabId(eq(5L), any(Instant.class), anyList()))
                .thenReturn(List.of(runningSlot));
        when(bookingRepository.findActiveCountsByTimeSlotIds(List.of(9L)))
                .thenReturn(List.of(new TimeSlotBookingCounts(9L, 4L, 2L, 1L)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a", List.of()));

        var result = service.getSlotsByLab(5L);

        assertEquals(1, result.size());
        assertEquals(TimeSlotStatus.EXPIRED, result.getFirst().getStatus());
        assertEquals(4L, result.getFirst().getApprovedCount());
        assertEquals(2L, result.getFirst().getCheckedInCount());
        assertEquals(1L, result.getFirst().getPendingCount());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TimeSlotStatus>> hiddenStatuses = ArgumentCaptor.forClass(List.class);
        verify(slotRepository).findUsableByLabId(eq(5L), any(Instant.class), hiddenStatuses.capture());
        assertFalse(hiddenStatuses.getValue().contains(TimeSlotStatus.EXPIRED));
        verify(bookingRepository).findActiveCountsByTimeSlotIds(List.of(9L));
        verify(bookingRepository, never()).countActiveByTimeSlotIdAndStatus(anyLong(), any());
        verify(bookingRepository, never()).countActiveByTimeSlotIdAndStatusIn(anyLong(), anyList());
    }
}
