package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import java.time.Instant;

public record AiLabContext(
        Laboratory laboratory,
        Slot slot,
        OwnBooking booking,
        ManagedSummary managedSummary,
        CheckinPolicySnapshot checkinPolicySnapshot,
        boolean draftOnly,
        String policyOrDraftEligibilityLabel) implements AiDomainContext {

    public record Laboratory(Long id, String name, LabStatus status) {
    }

    public record Slot(Long id, Instant startTime, Instant endTime, TimeSlotStatus status) {
    }

    public record OwnBooking(Long id, BookingStatus status, Slot slot) {
    }

    /** Aggregate-only managed-lab view; no booking or membership rows are represented. */
    public record ManagedSummary(long activeSlotCount, long activeBookingCount) {
    }

    public record CheckinPolicySnapshot(Instant endInclusive) {
    }
}
