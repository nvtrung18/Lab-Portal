package com.web.labportalbackend.ai.security.impl;

import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.NOT_LAB_MEMBER;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.NOT_MANAGED_LAB;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.NOT_OWNER;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_UNAVAILABLE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ROLE_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiLabCapabilityPermissionAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-06T15:00:00Z");

    @Mock LaboratoryRepository laboratoryRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock TimeSlotRepository timeSlotRepository;
    @Mock BookingRepository bookingRepository;
    @Mock SystemConfigService systemConfigService;

    private AiLabCapabilityPermissionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AiLabCapabilityPermissionAdapter(laboratoryRepository, membershipRepository,
                timeSlotRepository, bookingRepository, systemConfigService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void policyReadRequiresOnlyAnActiveNonDeletedLab() {
        Laboratory lab = lab(10L, LabStatus.MAINTENANCE);
        when(laboratoryRepository.findById(10L)).thenReturn(Optional.of(lab));

        assertTrue(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, 10L, AiRequestedAction.READ)).allowed());

        lab.setDeleted(true);
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, 10L,
                AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
    }

    @Test
    void slotReadRequiresCurrentMembershipOrExactManagedLabWithoutAdminBypass() {
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        TimeSlot slot = slot(20L, lab, NOW.plusSeconds(3600));
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(slot));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 10L))
                .thenReturn(true, false);
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 8L))
                .thenReturn(true);
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 7L))
                .thenReturn(true);

        assertTrue(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L, AiRequestedAction.READ)).allowed());
        assertTrue(adapter.evaluate(user(8L, "LAB_MANAGER"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L, AiRequestedAction.READ)).allowed());
        assertTrue(adapter.evaluate(user(7L, "STUDENT", "LAB_MANAGER"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L, AiRequestedAction.READ)).allowed());
        assertDenied(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.READ)), ROLE_NOT_ALLOWED);
    }

    @Test
    void ownBookingReadDeniesAnotherStudentAndAllowsHistoricalOwner() {
        Laboratory lab = lab(10L, LabStatus.INACTIVE);
        Booking booking = booking(30L, user(7L, "STUDENT"), lab, null, BookingStatus.COMPLETED, NOW);
        booking.setActive(false);
        when(bookingRepository.findById(30L)).thenReturn(Optional.of(booking));

        assertTrue(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_OWN_BOOKING_READ, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)).allowed());
        assertDenied(adapter.evaluate(user(8L, "STUDENT"), request(
                AiCapability.LAB_OWN_BOOKING_READ, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), NOT_OWNER);
    }

    @Test
    void managedSummaryRequiresExactActiveManagedLab() {
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        when(laboratoryRepository.findById(10L)).thenReturn(Optional.of(lab));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 8L))
                .thenReturn(true);

        assertTrue(adapter.evaluate(user(8L, "LAB_MANAGER"), request(
                AiCapability.LAB_MANAGED_SUMMARY, AiResourceType.LABORATORY, 10L,
                AiRequestedAction.READ)).allowed());
        assertDenied(adapter.evaluate(user(9L, "LAB_MANAGER"), request(
                AiCapability.LAB_MANAGED_SUMMARY, AiResourceType.LABORATORY, 10L,
                AiRequestedAction.READ)), NOT_MANAGED_LAB);
    }

    @Test
    void bookingDraftAllowsOnlyWhenEveryCurrentEligibilityPredicatePasses() {
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        TimeSlot slot = slot(20L, lab, NOW.plusSeconds(3600));
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(slot));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 10L))
                .thenReturn(true);
        when(systemConfigService.getConfig()).thenReturn(config(true, 15));

        assertTrue(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)).allowed());

        when(bookingRepository.existsActiveBookingByUserAndSlot(7L, 20L)).thenReturn(true);
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
    }

    @Test
    void bookingDraftDeniesNonStudentRevocationUnavailableOrNonFutureSlot() {
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        TimeSlot slot = slot(20L, lab, NOW);
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(slot));

        assertDenied(adapter.evaluate(user(8L, "LAB_MANAGER"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), ROLE_NOT_ALLOWED);
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), NOT_LAB_MEMBER);

        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 10L))
                .thenReturn(true);
        when(systemConfigService.getConfig()).thenReturn(config(true, 15));
        slot.setStatus(TimeSlotStatus.FULL);
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
    }

    @Test
    void bookingDraftMatchesConfiguredInactiveLabRule() {
        Laboratory lab = lab(10L, LabStatus.MAINTENANCE);
        TimeSlot slot = slot(20L, lab, NOW.plusSeconds(3600));
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(slot));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 10L))
                .thenReturn(true);
        when(systemConfigService.getConfig()).thenReturn(config(false, 15), config(true, 15));

        assertTrue(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)).allowed());
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
    }

    @Test
    void checkinGuidanceEnforcesOwnerApprovedCoherentNonCancelledHalfOpenWindow() {
        User owner = user(7L, "STUDENT");
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        TimeSlot slot = slot(20L, lab, NOW.minusSeconds(60));
        Booking booking = booking(30L, owner, lab, slot, BookingStatus.APPROVED, NOW.minusSeconds(60));
        when(bookingRepository.findById(30L)).thenReturn(Optional.of(booking));
        when(systemConfigService.getConfig()).thenReturn(config(true, 15));

        assertTrue(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)).allowed());

        assertDenied(adapter.evaluate(user(8L, "STUDENT"), request(
                AiCapability.LAB_CHECKIN_GUIDANCE, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), NOT_OWNER);
        booking.setStatus(BookingStatus.CHECKED_IN);
        assertDenied(adapter.evaluate(owner, request(
                AiCapability.LAB_CHECKIN_GUIDANCE, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);
        booking.setStatus(BookingStatus.APPROVED);
        slot.setStatus(TimeSlotStatus.CANCELLED);
        assertDenied(adapter.evaluate(owner, request(
                AiCapability.LAB_CHECKIN_GUIDANCE, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);
    }

    @Test
    void checkinGuidanceDeniesBeforeStartAndAtConfiguredEndBoundary() {
        User owner = user(7L, "STUDENT");
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        TimeSlot slot = slot(20L, lab, NOW.plusSeconds(1));
        Booking booking = booking(30L, owner, lab, slot, BookingStatus.APPROVED, NOW.plusSeconds(1));
        when(bookingRepository.findById(30L)).thenReturn(Optional.of(booking));

        assertDenied(adapter.evaluate(owner, request(
                AiCapability.LAB_CHECKIN_GUIDANCE, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);

        booking.setStartTime(NOW.minusSeconds(900));
        slot.setStartTime(booking.getStartTime());
        when(systemConfigService.getConfig()).thenReturn(config(true, 15));
        assertDenied(adapter.evaluate(owner, request(
                AiCapability.LAB_CHECKIN_GUIDANCE, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);
    }

    @Test
    void missingInactiveDeletedAndRepositoryFailedLabAreCoarsenedWithoutDownstreamCalls() {
        Laboratory inactive = lab(11L, LabStatus.AVAILABLE);
        inactive.setActive(false);
        Laboratory deleted = lab(12L, LabStatus.AVAILABLE);
        deleted.setDeleted(true);
        when(laboratoryRepository.findById(10L)).thenReturn(Optional.empty());
        when(laboratoryRepository.findById(11L)).thenReturn(Optional.of(inactive));
        when(laboratoryRepository.findById(12L)).thenReturn(Optional.of(deleted));
        when(laboratoryRepository.findById(13L)).thenThrow(new IllegalStateException("private database detail"));

        for (long id = 10L; id <= 13L; id++) {
            assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                    AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, id,
                    AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        }
        verifyNoInteractions(membershipRepository, timeSlotRepository, bookingRepository, systemConfigService);
    }

    @Test
    void slotDenialsAreRedactedAndStopAtTheFirstDeterminableBoundary() {
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(membershipRepository, laboratoryRepository, bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        TimeSlot inactive = slot(20L, lab(10L, LabStatus.AVAILABLE), NOW.plusSeconds(60));
        inactive.setActive(false);
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(inactive));
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(membershipRepository, laboratoryRepository, bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        TimeSlot active = slot(20L, lab(10L, LabStatus.AVAILABLE), NOW.plusSeconds(60));
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(active));
        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.READ)), NOT_LAB_MEMBER);
        verify(laboratoryRepository, never())
                .existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 7L);
        verifyNoInteractions(bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        assertDenied(adapter.evaluate(user(8L, "LAB_MANAGER"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.READ)), NOT_MANAGED_LAB);
        verifyNoInteractions(membershipRepository, bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        assertDenied(adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.LAB_SLOT_READ, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.READ)), ROLE_NOT_ALLOWED);
        verifyNoInteractions(membershipRepository, laboratoryRepository, bookingRepository, systemConfigService);
    }

    @Test
    void membershipRevocationIsReadFreshAndDeniesBeforeConfigurationOrBookingLookup() {
        TimeSlot slot = slot(20L, lab(10L, LabStatus.AVAILABLE), NOW.plusSeconds(3600));
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(slot));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 10L))
                .thenReturn(true, false);
        when(systemConfigService.getConfig()).thenReturn(config(true, 15));

        assertTrue(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)).allowed());
        clearInvocations(timeSlotRepository, membershipRepository, bookingRepository,
                laboratoryRepository, systemConfigService);

        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), NOT_LAB_MEMBER);
        verifyNoInteractions(systemConfigService, bookingRepository, laboratoryRepository);
    }

    @Test
    void bookingDraftNegativeMatrixStopsBeforeEveryInappropriateDownstreamLookup() {
        User student = user(7L, "STUDENT");
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        TimeSlot slot = slot(20L, lab, NOW.plusSeconds(3600));

        assertDenied(adapter.evaluate(user(8L, "LAB_MANAGER"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), ROLE_NOT_ALLOWED);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        assertDenied(adapter.evaluate(student, request(AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 20L, AiRequestedAction.DRAFT)), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(membershipRepository, laboratoryRepository, bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(slot));
        assertDenied(adapter.evaluate(student, request(AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 20L, AiRequestedAction.DRAFT)), NOT_LAB_MEMBER);
        verifyNoInteractions(laboratoryRepository, bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 10L))
                .thenReturn(true);
        slot.setStatus(TimeSlotStatus.FULL);
        assertDenied(adapter.evaluate(student, request(AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 20L, AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(laboratoryRepository, bookingRepository, systemConfigService);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        when(systemConfigService.getConfig()).thenReturn(null);
        assertDenied(adapter.evaluate(student, request(AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 20L, AiRequestedAction.DRAFT)), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(laboratoryRepository, bookingRepository);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        lab.setStatus(LabStatus.MAINTENANCE);
        when(systemConfigService.getConfig()).thenReturn(config(true, 15));
        assertDenied(adapter.evaluate(student, request(AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 20L, AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(laboratoryRepository, bookingRepository);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        lab.setStatus(LabStatus.AVAILABLE);
        slot.setStartTime(NOW);
        assertDenied(adapter.evaluate(student, request(AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 20L, AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(laboratoryRepository, bookingRepository);

        clearInvocations(timeSlotRepository, membershipRepository, laboratoryRepository,
                bookingRepository, systemConfigService);
        slot.setStartTime(NOW.plusSeconds(3600));
        when(bookingRepository.existsActiveBookingByUserAndSlot(7L, 20L)).thenReturn(true);
        assertDenied(adapter.evaluate(student, request(AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 20L, AiRequestedAction.DRAFT)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(laboratoryRepository);
    }

    @Test
    void checkinGuidanceNegativeMatrixStopsBeforeConfigurationWhenAlreadyDenied() {
        User owner = user(7L, "STUDENT");
        Laboratory lab = lab(10L, LabStatus.AVAILABLE);
        TimeSlot slot = slot(20L, lab, NOW.minusSeconds(60));
        Booking booking = booking(30L, owner, lab, slot, BookingStatus.APPROVED, NOW.minusSeconds(60));

        assertDenied(adapter.evaluate(user(8L, "LAB_MANAGER"), request(
                AiCapability.LAB_CHECKIN_GUIDANCE, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), ROLE_NOT_ALLOWED);
        verifyNoInteractions(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        assertDenied(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository, systemConfigService);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        when(bookingRepository.findById(30L)).thenReturn(Optional.of(booking));
        assertDenied(adapter.evaluate(user(8L, "STUDENT"), request(
                AiCapability.LAB_CHECKIN_GUIDANCE, AiResourceType.BOOKING, 30L,
                AiRequestedAction.READ)), NOT_OWNER);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository, systemConfigService);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        booking.setTimeSlot(null);
        assertDenied(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository, systemConfigService);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        booking.setTimeSlot(slot);
        booking.setStatus(BookingStatus.COMPLETED);
        assertDenied(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository, systemConfigService);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        booking.setStatus(BookingStatus.APPROVED);
        slot.setStatus(TimeSlotStatus.CANCELLED);
        assertDenied(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository, systemConfigService);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        booking.setStartTime(NOW.plusSeconds(1));
        assertDenied(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository, systemConfigService);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        booking.setStartTime(NOW.minusSeconds(60));
        when(systemConfigService.getConfig()).thenReturn(null);
        assertDenied(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository);

        clearInvocations(bookingRepository, timeSlotRepository, membershipRepository,
                laboratoryRepository, systemConfigService);
        when(systemConfigService.getConfig()).thenReturn(config(true, 1));
        assertDenied(adapter.evaluate(owner, request(AiCapability.LAB_CHECKIN_GUIDANCE,
                AiResourceType.BOOKING, 30L, AiRequestedAction.READ)), RESOURCE_OUT_OF_SCOPE);
        verifyNoInteractions(timeSlotRepository, membershipRepository, laboratoryRepository);
    }

    @Test
    void configurationFailureIsCoarsenedAndNeverReachesDuplicateBookingLookup() {
        TimeSlot slot = slot(20L, lab(10L, LabStatus.AVAILABLE), NOW.plusSeconds(3600));
        when(timeSlotRepository.findActiveById(20L)).thenReturn(Optional.of(slot));
        when(membershipRepository.existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(7L, 10L))
                .thenReturn(true);
        when(systemConfigService.getConfig()).thenThrow(new IllegalStateException("secret config detail"));

        assertDenied(adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT)), RESOURCE_UNAVAILABLE);
        verify(bookingRepository, never()).existsActiveBookingByUserAndSlot(7L, 20L);
        verifyNoInteractions(laboratoryRepository);
    }

    @Test
    void repositoryOrConfigurationFailureFailsClosed() {
        when(timeSlotRepository.findActiveById(20L)).thenThrow(new IllegalStateException("database detail"));

        AiCapabilityPermissionAdapter.Evaluation result = adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.LAB_BOOKING_DRAFT, AiResourceType.TIME_SLOT, 20L,
                AiRequestedAction.DRAFT));

        assertDenied(result, RESOURCE_UNAVAILABLE);
    }

    private static AiCapabilityRequest request(AiCapability capability, AiResourceType type, Long id,
                                               AiRequestedAction action) {
        return new AiCapabilityRequest(AiAssistantKey.LAB_ASSISTANT, 7L, capability,
                new AiCapabilityRequest.ResourceReference(type, id), null, action);
    }

    private static User user(Long id, String... roles) {
        User user = new User();
        user.setId(id);
        user.setActive(true);
        user.setDeleted(false);
        for (String role : roles) {
            user.addRole(new Role(role, role));
        }
        return user;
    }

    private static Laboratory lab(Long id, LabStatus status) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        lab.setActive(true);
        lab.setDeleted(false);
        lab.setStatus(status);
        return lab;
    }

    private static TimeSlot slot(Long id, Laboratory lab, Instant start) {
        TimeSlot slot = TimeSlot.builder().lab(lab).startTime(start).endTime(start.plusSeconds(3600))
                .capacity(5).status(TimeSlotStatus.AVAILABLE).build();
        slot.setId(id);
        slot.setActive(true);
        slot.setDeleted(false);
        return slot;
    }

    private static Booking booking(Long id, User owner, Laboratory lab, TimeSlot slot,
                                   BookingStatus status, Instant start) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setUser(owner);
        booking.setLab(lab);
        booking.setTimeSlot(slot);
        booking.setStartTime(start);
        booking.setEndTime(start.plusSeconds(3600));
        booking.setStatus(status);
        booking.setActive(true);
        booking.setDeleted(false);
        return booking;
    }

    private static SystemConfigResponse config(boolean disableInactiveLab, int checkinMinutes) {
        return new SystemConfigResponse(null,
                new SystemConfigResponse.LabConfig(false, false, false, disableInactiveLab),
                new SystemConfigResponse.BookingConfig(checkinMinutes, 0, false, false),
                null, null);
    }

    private static void assertDenied(AiCapabilityPermissionAdapter.Evaluation evaluation,
                                     com.web.labportalbackend.ai.enums.AiCapabilityDenialReason reason) {
        assertFalse(evaluation.allowed());
        assertEquals(reason, evaluation.denialReason());
        assertNull(evaluation.resolvedResource());
        assertTrue(evaluation.evidence().isEmpty());
    }
}
