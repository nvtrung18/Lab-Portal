package com.web.labportalbackend;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.booking.outbox.BookingOutboxService;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.impl.CleaningServiceImpl;
import com.web.labportalbackend.common.enums.CleaningStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.entity.Membership;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleaningAutoAssignmentTest {

    private static final Long SLOT_ID = 70L;
    private static final Long LAB_ID = 20L;
    private static final List<CleaningStatus> COMPLETED_STATUSES =
            List.of(CleaningStatus.DONE, CleaningStatus.COMPLETED);

    @Test
    void randomlyAssignsOnlyStudentsWhoHaveNotCompletedOrAlreadyHaveAnOpenTaskThisRound() {
        Fixture fixture = fixture(List.of(
                membership(1L, 101L, true),
                membership(2L, 102L, true),
                membership(3L, 103L, true)
        ), List.of(101L), List.of(102L), 1);

        CleaningResponse response = fixture.service().createCleaningTask(SLOT_ID);

        assertEquals(CleaningStatus.ASSIGNED, response.getStatus());
        assertEquals(103L, response.getStaffId());
        assertEquals(1, fixture.cleaningRepository().findFirstBySlotId(SLOT_ID)
                .orElseThrow().getRotationRound());
        CleaningResponse retried = fixture.service().createCleaningTask(SLOT_ID);
        assertEquals(response.getId(), retried.getId());
        assertEquals(response.getStaffId(), retried.getStaffId());
        verify(fixture.notificationEmitter()).emit(103L,
                NotificationEventType.CLEANING_TASK_ASSIGNED,
                "Đã phân công vệ sinh PTN",
                "Bạn được phân công vệ sinh sau ca lab vừa kết thúc.",
                NotificationTargetModule.CLEANING, 700L, null);
        verify(fixture.bookingOutboxService()).enqueueEmail(eq(700L),
                eq(BookingEmailType.CLEANING_ASSIGNED), eq("student-103@example.test"), any());
    }

    @Test
    void keepsTaskPendingWhenEveryNotYetCompletedStudentHasAnOpenCleaningTask() {
        Fixture fixture = fixture(List.of(
                membership(1L, 101L, true),
                membership(2L, 102L, true)
        ), List.of(101L), List.of(102L), 1);

        CleaningResponse response = fixture.service().createCleaningTask(SLOT_ID);

        assertEquals(CleaningStatus.PENDING, response.getStatus());
        assertNull(response.getStaffId());
        assertEquals(1, fixture.lab().getCleaningRotationRound());
        verify(fixture.notificationEmitter(), never()).emit(any(), any(), any(), any(), any(), any(), any());
        verify(fixture.bookingOutboxService(), never()).enqueueEmail(any(), any(), any(), any());
    }

    @Test
    void startsANewRoundOnlyAfterEveryActiveMemberCompletedAndThenSelectsFromTheFullRoster() {
        Fixture fixture = fixture(List.of(
                membership(1L, 101L, true),
                membership(2L, 102L, true)
        ), List.of(101L, 102L), List.of(), 4);

        CleaningResponse response = fixture.service().createCleaningTask(SLOT_ID);

        assertEquals(CleaningStatus.ASSIGNED, response.getStatus());
        assertTrue(List.of(101L, 102L).contains(response.getStaffId()));
        assertEquals(5, fixture.lab().getCleaningRotationRound());
        assertEquals(5, fixture.cleaningRepository().findFirstBySlotId(SLOT_ID)
                .orElseThrow().getRotationRound());
    }

    @Test
    void leavesTaskPendingWhenThereAreNoActiveStudentMembers() {
        Fixture fixture = fixture(List.of(), List.of(), List.of(), 1);

        CleaningResponse response = fixture.service().createCleaningTask(SLOT_ID);

        assertEquals(CleaningStatus.PENDING, response.getStatus());
        assertNull(response.getStaffId());
    }

    private Fixture fixture(
            List<Membership> memberships,
            List<Long> completedStaffIds,
            List<Long> assignedStaffIds,
            int currentRound
    ) {
        CleaningRepository cleaningRepository = mock(CleaningRepository.class);
        TimeSlotRepository timeSlotRepository = mock(TimeSlotRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        MembershipRepository membershipRepository = mock(MembershipRepository.class);
        NotificationEmitter notificationEmitter = mock(NotificationEmitter.class);
        BookingOutboxService bookingOutboxService = mock(BookingOutboxService.class);
        AtomicReference<CleaningEntity> persistedCleaning = new AtomicReference<>();

        Laboratory lab = new Laboratory();
        lab.setId(LAB_ID);
        lab.setLabName("Lab A");
        lab.setCleaningRotationRound(currentRound);
        TimeSlot slot = new TimeSlot();
        slot.setId(SLOT_ID);
        slot.setLab(lab);
        slot.setStartTime(Instant.parse("2026-09-24T08:00:00Z"));
        slot.setEndTime(Instant.parse("2026-09-24T09:00:00Z"));

        when(timeSlotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));
        when(laboratoryRepository.findByIdForCleaningRotation(LAB_ID)).thenReturn(Optional.of(lab));
        when(cleaningRepository.findFirstBySlotId(SLOT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(persistedCleaning.get()));
        when(membershipRepository.findByLaboratoryIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAscIdAsc(LAB_ID))
                .thenReturn(memberships);
        when(cleaningRepository.findCompletedStaffIdsByLabIdAndRotationRoundAndStatuses(
                LAB_ID, currentRound, COMPLETED_STATUSES)).thenReturn(completedStaffIds);
        when(cleaningRepository.findAssignedStaffIdsByLabIdAndStatuses(
                LAB_ID, List.of(CleaningStatus.ASSIGNED))).thenReturn(assignedStaffIds);
        when(cleaningRepository.save(any(CleaningEntity.class))).thenAnswer(invocation -> {
            CleaningEntity entity = invocation.getArgument(0);
            entity.setId(700L);
            persistedCleaning.set(entity);
            return entity;
        });
        when(bookingRepository.countActiveByTimeSlotIdAndStatusIn(any(), any())).thenReturn(0L);

        return new Fixture(new CleaningServiceImpl(
                cleaningRepository,
                timeSlotRepository,
                userRepository,
                laboratoryRepository,
                bookingRepository,
                membershipRepository,
                notificationEmitter,
                bookingOutboxService
        ), cleaningRepository, notificationEmitter, bookingOutboxService, lab);
    }

    private Membership membership(Long membershipId, Long userId, boolean active) {
        Membership membership = new Membership();
        membership.setId(membershipId);
        membership.setCreatedAt(Instant.parse("2026-01-0" + membershipId + "T00:00:00Z"));
        membership.setActive(active);
        membership.setDeleted(false);

        User user = new User();
        user.setId(userId);
        user.setEmail("student-" + userId + "@example.test");
        Role role = new Role();
        role.setName("STUDENT");
        user.addRole(role);
        membership.setUser(user);
        return membership;
    }

    private record Fixture(
            CleaningServiceImpl service,
            CleaningRepository cleaningRepository,
            NotificationEmitter notificationEmitter,
            BookingOutboxService bookingOutboxService,
            Laboratory lab
    ) {
    }
}
