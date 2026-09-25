package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.common.enums.CleaningStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.entity.Membership;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CleaningRepositoryTest {

    @Autowired
    private CleaningRepository cleaningRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void assignedStaffHistoryContainsOnlyCurrentNonCancelledAssignmentsForTheLab() {
        Laboratory lab = saveLab("Cleaning History Lab");
        Laboratory anotherLab = saveLab("Other Cleaning Lab");
        TimeSlot slot = saveSlot(lab);
        TimeSlot otherSlot = saveSlot(anotherLab);
        User assigned = saveUser("cleaning-assigned");
        User done = saveUser("cleaning-done");
        User completed = saveUser("cleaning-completed");
        User cancelled = saveUser("cleaning-cancelled");
        User pending = saveUser("cleaning-pending");
        User inactive = saveUser("cleaning-inactive");
        User deleted = saveUser("cleaning-deleted");
        User otherLabStaff = saveUser("cleaning-other-lab");

        saveCleaning(slot, assigned, CleaningStatus.ASSIGNED, true, false);
        saveCleaning(slot, done, CleaningStatus.DONE, true, false);
        saveCleaning(slot, completed, CleaningStatus.COMPLETED, true, false);
        saveCleaning(slot, cancelled, CleaningStatus.CANCELLED, true, false);
        saveCleaning(slot, pending, CleaningStatus.PENDING, true, false);
        saveCleaning(slot, inactive, CleaningStatus.ASSIGNED, false, false);
        saveCleaning(slot, deleted, CleaningStatus.DONE, true, true);
        saveCleaning(otherSlot, otherLabStaff, CleaningStatus.ASSIGNED, true, false);

        List<Long> staffIds = cleaningRepository.findAssignedStaffIdsByLabIdAndStatuses(
                lab.getId(),
                List.of(CleaningStatus.ASSIGNED, CleaningStatus.DONE, CleaningStatus.COMPLETED)
        );

        assertEquals(3, staffIds.size());
        assertTrue(staffIds.containsAll(List.of(assigned.getId(), done.getId(), completed.getId())));
    }

    @Test
    void activeMembershipsAreOrderedByJoinDateThenMembershipId() {
        Laboratory lab = saveLab("Cleaning Rotation Lab");
        Membership later = saveMembership(lab, "rotation-later", "2026-02-01T00:00:00Z", true, false);
        Membership earlier = saveMembership(lab, "rotation-earlier", "2026-01-01T00:00:00Z", true, false);
        Membership sameDateHigherId = saveMembership(lab, "rotation-tie-later", "2026-01-01T00:00:00Z", true, false);
        saveMembership(lab, "rotation-inactive", "2025-01-01T00:00:00Z", false, false);
        saveMembership(lab, "rotation-deleted", "2025-01-01T00:00:00Z", true, true);

        List<Membership> memberships = membershipRepository
                .findByLaboratoryIdAndActiveTrueAndDeletedFalseOrderByCreatedAtAscIdAsc(lab.getId());

        assertEquals(List.of(earlier.getId(), sameDateHigherId.getId(), later.getId()),
                memberships.stream().map(Membership::getId).toList());
    }

    @Test
    void labCanBeLoadedAsTheCleaningRotationWriteLock() {
        Laboratory lab = saveLab("Cleaning Rotation Lock Lab");

        Laboratory lockedLab = laboratoryRepository.findByIdForCleaningRotation(lab.getId()).orElseThrow();

        assertEquals(lab.getId(), lockedLab.getId());
    }

    @Test
    void completedMembersAreReadOnlyFromTheRequestedRotationRound() {
        Laboratory lab = saveLab("Cleaning Completed Round Lab");
        Laboratory anotherLab = saveLab("Other Completed Round Lab");
        User done = saveUser("rotation-done");
        User completed = saveUser("rotation-completed");
        User assigned = saveUser("rotation-assigned");
        User cancelled = saveUser("rotation-cancelled");
        User previousRound = saveUser("rotation-previous-round");

        saveCleaning(saveSlot(lab), done, CleaningStatus.DONE, true, false, 3);
        saveCleaning(saveSlot(lab), completed, CleaningStatus.COMPLETED, true, false, 3);
        saveCleaning(saveSlot(lab), assigned, CleaningStatus.ASSIGNED, true, false, 3);
        saveCleaning(saveSlot(lab), cancelled, CleaningStatus.CANCELLED, true, false, 3);
        saveCleaning(saveSlot(lab), previousRound, CleaningStatus.DONE, true, false, 2);
        saveCleaning(saveSlot(anotherLab), saveUser("rotation-other-lab"), CleaningStatus.DONE,
                true, false, 3);

        List<Long> completedStaffIds = cleaningRepository
                .findCompletedStaffIdsByLabIdAndRotationRoundAndStatuses(
                        lab.getId(), 3, List.of(CleaningStatus.DONE, CleaningStatus.COMPLETED));

        assertEquals(2, completedStaffIds.size());
        assertTrue(completedStaffIds.containsAll(List.of(done.getId(), completed.getId())));
    }

    @Test
    void schedulerQueryReturnsEndedSlotsWithoutAnyCleaningRecord() {
        Laboratory lab = saveLab("Cleaning New Slot Lab");
        TimeSlot noTask = saveSlot(lab);
        TimeSlot withTask = saveSlot(lab);
        TimeSlot cancelled = saveSlot(lab);
        cancelled.setStatus(TimeSlotStatus.CANCELLED);
        timeSlotRepository.saveAndFlush(cancelled);
        saveCleaning(withTask, null, CleaningStatus.PENDING, true, false, null);

        List<TimeSlot> slots = timeSlotRepository.findEndedSlotsWithoutCleaning(
                Instant.now(), List.of(TimeSlotStatus.CANCELLED, TimeSlotStatus.INACTIVE,
                        TimeSlotStatus.ARCHIVED));

        assertEquals(List.of(noTask.getId()), slots.stream().map(TimeSlot::getId).toList());
    }

    @Test
    void schedulerRetryQueryReturnsOnlyPendingUnassignedTasksForVisibleEndedSlots() {
        Laboratory lab = saveLab("Cleaning Retry Query Lab");
        TimeSlot pendingSlot = saveSlot(lab);
        TimeSlot assignedSlot = saveSlot(lab);
        TimeSlot cancelledSlot = saveSlot(lab);
        cancelledSlot.setStatus(TimeSlotStatus.CANCELLED);
        timeSlotRepository.saveAndFlush(cancelledSlot);
        saveCleaning(pendingSlot, null, CleaningStatus.PENDING, true, false, null);
        saveCleaning(assignedSlot, saveUser("retry-query-assigned"), CleaningStatus.ASSIGNED,
                true, false, null);
        saveCleaning(cancelledSlot, null, CleaningStatus.PENDING, true, false, null);

        List<Long> slotIds = cleaningRepository.findPendingUnassignedEndedSlotIds(
                Instant.now(), List.of(TimeSlotStatus.CANCELLED, TimeSlotStatus.INACTIVE,
                        TimeSlotStatus.ARCHIVED), CleaningStatus.PENDING);

        assertEquals(List.of(pendingSlot.getId()), slotIds);
    }

    private Laboratory saveLab(String name) {
        Laboratory lab = new Laboratory();
        lab.setLabName(name);
        lab.setLocation("Building A");
        lab.setCapacity(20);
        lab.setStatus(LabStatus.AVAILABLE);
        return laboratoryRepository.saveAndFlush(lab);
    }

    private TimeSlot saveSlot(Laboratory lab) {
        return timeSlotRepository.saveAndFlush(TimeSlot.builder()
                .lab(lab)
                .startTime(Instant.parse("2026-09-24T01:00:00Z"))
                .endTime(Instant.parse("2026-09-24T02:00:00Z"))
                .capacity(10)
                .status(TimeSlotStatus.AVAILABLE)
                .build());
    }

    private User saveUser(String username) {
        User user = new User();
        user.setEmail(username + "@example.test");
        user.setUsername(username);
        user.setPassword("password");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private Membership saveMembership(
            Laboratory lab,
            String username,
            String joinedAt,
            boolean active,
            boolean deleted
    ) {
        Membership membership = new Membership();
        membership.setLaboratory(lab);
        membership.setUser(saveUser(username));
        membership.setCreatedAt(Instant.parse(joinedAt));
        membership.setActive(active);
        membership.setDeleted(deleted);
        Membership saved = membershipRepository.saveAndFlush(membership);
        jdbcTemplate.update("UPDATE memberships SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse(joinedAt)), saved.getId());
        return saved;
    }

    private void saveCleaning(
            TimeSlot slot,
            User staff,
            CleaningStatus status,
            boolean active,
            boolean deleted
    ) {
        saveCleaning(slot, staff, status, active, deleted, null);
    }

    private CleaningEntity saveCleaning(
            TimeSlot slot,
            User staff,
            CleaningStatus status,
            boolean active,
            boolean deleted,
            Integer rotationRound
    ) {
        CleaningEntity cleaning = CleaningEntity.builder()
                .slot(slot)
                .staff(staff)
                .status(status)
                .rotationRound(rotationRound)
                .build();
        cleaning.setActive(active);
        cleaning.setDeleted(deleted);
        return cleaningRepository.saveAndFlush(cleaning);
    }
}
