package com.web.labportalbackend.common.exception;

/** A rejected slot interval detected before any slot, audit or notification is written. */
public class TimeSlotUnavailableException extends IllegalStateException {
    public TimeSlotUnavailableException(String message) {
        super(message);
    }
}
