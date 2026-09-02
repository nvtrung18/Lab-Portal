package com.web.labportalbackend.booking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.booking.entity.Booking;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckinWindowPolicyTest {

    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final Booking booking = mock(Booking.class);
    private final CheckinWindowPolicy policy = new CheckinWindowPolicy(systemConfigService);
    private final Instant start = Instant.parse("2026-09-01T08:00:00Z");

    @BeforeEach
    void setUp() {
        when(booking.getStartTime()).thenReturn(start);
        when(systemConfigService.getConfig()).thenReturn(new SystemConfigResponse(null, null,
                new SystemConfigResponse.BookingConfig(10, 0, false, false), null, null));
    }

    @Test
    void allowsCheckinFromFiveMinutesBeforeUntilTenMinutesAfterStart() {
        assertDoesNotThrow(() -> policy.validate(booking, start.minusSeconds(5 * 60)));
        assertDoesNotThrow(() -> policy.validate(booking, start.plusSeconds(10 * 60)));
    }

    @Test
    void rejectsCheckinOutsideConfiguredBounds() {
        assertThrows(IllegalStateException.class,
                () -> policy.validate(booking, start.minusSeconds(5 * 60 + 1)));
        assertThrows(IllegalStateException.class,
                () -> policy.validate(booking, start.plusSeconds(10 * 60 + 1)));
    }

    @Test
    void candidateWindowIncludesEarlyAndLateBookings() {
        CheckinWindowPolicy.CandidateWindow window = policy.candidateWindow(start);

        assertEquals(start.minusSeconds(10 * 60), window.earliestStart());
        assertEquals(start.plusSeconds(5 * 60), window.latestStart());
    }
}
