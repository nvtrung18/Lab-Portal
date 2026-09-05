package com.web.labportalbackend.ai.context;

import java.time.Instant;

public record AiLabAvailableSlotsContext(
        AiLabContext.Laboratory laboratory,
        AiBoundedList<AiLabContext.Slot> availableSlots,
        Instant evaluatedAt) implements AiDomainContext {

    public AiLabAvailableSlotsContext {
        if (laboratory == null || availableSlots == null || evaluatedAt == null) {
            throw new IllegalArgumentException("Available Lab slot context is incomplete");
        }
    }
}
