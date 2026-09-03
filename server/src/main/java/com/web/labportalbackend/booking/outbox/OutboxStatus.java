package com.web.labportalbackend.booking.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED
}
