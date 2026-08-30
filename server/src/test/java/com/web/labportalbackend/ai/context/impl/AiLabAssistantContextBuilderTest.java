package com.web.labportalbackend.ai.context.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.context.*;
import com.web.labportalbackend.ai.enums.*;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiLabAssistantContextBuilderTest {
    @Test void currentAcceptedActorIsBoundToLabProjection() {
        LaboratoryRepository labs = mock(LaboratoryRepository.class);
        when(labs.findAiContextLaboratory(7L, 10L, "STUDENT")).thenReturn(Optional.of(new AiLabContext.Laboratory(10L, "Lab", null)));
        AiLabAssistantContextBuilder builder = new AiLabAssistantContextBuilder(labs, mock(TimeSlotRepository.class), mock(BookingRepository.class));
        AiLabContext context = (AiLabContext) builder.build(input());
        assertEquals(10L, context.laboratory().id());
        assertEquals("POLICY_INFORMATION_ONLY", context.policyOrDraftEligibilityLabel());
        verify(labs).findAiContextLaboratory(7L, 10L, "STUDENT");
    }

    @Test void checkinGuidanceUsesOnlyTheFinalResolverDerivedInclusiveEnd() {
        LaboratoryRepository labs = mock(LaboratoryRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        Instant readAt = Instant.parse("2026-08-07T09:00:00Z");
        Instant endInclusive = Instant.parse("2026-08-07T09:30:00Z");
        when(labs.findAiContextLaboratory(7L, 10L, "STUDENT")).thenReturn(Optional.of(new AiLabContext.Laboratory(10L, "Lab", null)));
        when(bookings.findAiContextCheckinBooking(7L, 10L, 30L, readAt, endInclusive, "STUDENT"))
                .thenReturn(Optional.of(new AiLabContext.OwnBooking(30L,
                        com.web.labportalbackend.common.enums.BookingStatus.APPROVED, null)));
        AiLabAssistantContextBuilder builder = new AiLabAssistantContextBuilder(labs, mock(TimeSlotRepository.class), bookings);

        AiLabContext context = (AiLabContext) builder.build(checkinInput(readAt, endInclusive));

        assertEquals(30L, context.booking().id());
        assertEquals(endInclusive, context.checkinPolicySnapshot().endInclusive());
        verify(bookings).findAiContextCheckinBooking(7L, 10L, 30L, readAt, endInclusive, "STUDENT");
    }
    @Test void staleManagedLabFailsClosedBeforeAggregateCounts() {
        LaboratoryRepository labs = mock(LaboratoryRepository.class);
        TimeSlotRepository slots = mock(TimeSlotRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        when(labs.findAiContextLaboratory(7L, 10L, "LAB_MANAGER"))
                .thenReturn(Optional.of(new AiLabContext.Laboratory(10L, "Lab", null)));
        when(labs.existsAiContextManagedLab(7L, 10L, "LAB_MANAGER")).thenReturn(false);
        AiLabAssistantContextBuilder builder = new AiLabAssistantContextBuilder(labs, slots, bookings);

        assertThrows(AiContextReadDeniedException.class, () -> builder.build(managedSummaryInput()));

        verify(labs).existsAiContextManagedLab(7L, 10L, "LAB_MANAGER");
        verify(slots, never()).countAiContextManagedSlots(7L, 10L, "LAB_MANAGER");
        verify(bookings, never()).countAiContextManagedBookings(7L, 10L, "LAB_MANAGER");
    }

    @Test void bookingDraftExposesOnlyTheFixedDraftEligibilityLabel() {
        LaboratoryRepository labs = mock(LaboratoryRepository.class);
        TimeSlotRepository slots = mock(TimeSlotRepository.class);
        when(labs.findAiContextLaboratory(7L, 10L, "STUDENT"))
                .thenReturn(Optional.of(new AiLabContext.Laboratory(10L, "Lab", null)));
        when(slots.findAiContextSlot(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("STUDENT")))
                .thenReturn(Optional.of(new AiLabContext.Slot(20L, Instant.now(), Instant.now(), null)));
        AiLabAssistantContextBuilder builder = new AiLabAssistantContextBuilder(labs, slots, mock(BookingRepository.class));
        AiCapabilityDecision decision = new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.STUDENT, AiAssistantKey.LAB_ASSISTANT,
                AiAssistantDomain.LAB, AiCapability.LAB_BOOKING_DRAFT,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.TIME_SLOT, 20L, 10L,
                        null, null, null, AiResourceScope.SELF),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.DRAFT_ONLY, Set.of(), null);

        AiLabContext context = (AiLabContext) builder.build(new TrustedContextInput(decision, 7L, null, Instant.now()));

        assertEquals("DRAFT_ONLY_NO_BOOKING_WRITE", context.policyOrDraftEligibilityLabel());
        assertEquals(true, context.draftOnly());
    }
    private static TrustedContextInput input() {
        AiCapabilityDecision d = new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.STUDENT, AiAssistantKey.LAB_ASSISTANT,
                AiAssistantDomain.LAB, AiCapability.LAB_POLICY_READ,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L, null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        return new TrustedContextInput(d, 7L, null, Instant.now());
    }

    private static TrustedContextInput checkinInput(Instant readAt, Instant endInclusive) {
        AiCapabilityDecision d = new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.STUDENT, AiAssistantKey.LAB_ASSISTANT,
                AiAssistantDomain.LAB, AiCapability.LAB_CHECKIN_GUIDANCE,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.BOOKING, 30L, 10L,
                        null, null, null, AiResourceScope.SELF),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, AiActionRiskBoundary.READ_ONLY,
                Set.of(), new AiCapabilityDecision.CheckinGuidancePolicySnapshot(endInclusive));
        return new TrustedContextInput(d, 7L, null, readAt);
    }

    private static TrustedContextInput managedSummaryInput() {
        AiCapabilityDecision d = new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.LAB_MANAGER, AiAssistantKey.LAB_ASSISTANT,
                AiAssistantDomain.LAB, AiCapability.LAB_MANAGED_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                        null, null, null, AiResourceScope.MANAGED_LAB),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        return new TrustedContextInput(d, 7L, null, Instant.now());
    }
}
