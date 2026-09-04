package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.CreatePenaltyRequest;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.booking.outbox.BookingOutboxService;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.PenaltyRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.impl.PenaltyServiceImpl;
import com.web.labportalbackend.common.enums.PenaltyType;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PenaltyServiceImplTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerCreatedPenaltyNotifiesAndQueuesEmailForStudent() {
        PenaltyRepository penaltyRepository = mock(PenaltyRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TimeSlotRepository timeSlotRepository = mock(TimeSlotRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        NotificationEmitter notificationEmitter = mock(NotificationEmitter.class);
        BookingOutboxService bookingOutboxService = mock(BookingOutboxService.class);
        PenaltyServiceImpl service = new PenaltyServiceImpl(penaltyRepository, userRepository, timeSlotRepository,
                bookingRepository, laboratoryRepository, membershipRepository, notificationEmitter, bookingOutboxService);
        ReflectionTestUtils.setField(service, "defaultAmount", BigDecimal.valueOf(50_000));

        User manager = mock(User.class);
        User student = new User();
        student.setId(2L);
        student.setEmail("student@example.com");
        student.setFullName("Student A");
        Laboratory lab = new Laboratory();
        lab.setId(3L);
        lab.setLabName("Lab A");
        TimeSlot slot = new TimeSlot();
        slot.setId(4L);
        slot.setLab(lab);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        slot.setStartTime(Instant.now().plusSeconds(60));
        slot.setEndTime(Instant.now().plusSeconds(3600));
        CreatePenaltyRequest request = new CreatePenaltyRequest();
        request.setUserId(2L);
        request.setSlotId(4L);
        request.setType(PenaltyType.RULE_VIOLATION);
        request.setReason("Không tuân thủ quy định an toàn");

        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(manager.getId()).thenReturn(1L);
        when(manager.hasRole("LAB_MANAGER")).thenReturn(true);
        when(timeSlotRepository.findActiveById(4L)).thenReturn(Optional.of(slot));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(1L)).thenReturn(Optional.of(lab));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(2L, 3L)).thenReturn(true);
        when(penaltyRepository.save(any(PenaltyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", "n/a", List.of()));

        service.createPenalty(request);

        verify(notificationEmitter).emit(2L, NotificationEventType.PENALTY_CREATED,
                "Đã ghi nhận vi phạm", "Quản lý PTN đã ghi nhận vi phạm cho ca sử dụng tại Lab A.",
                NotificationTargetModule.BOOKING, 4L, null);
        verify(bookingOutboxService).enqueueEmail(eq(4L), eq(BookingEmailType.PENALTY_CREATED),
                eq("student@example.com"), any());
    }
}
