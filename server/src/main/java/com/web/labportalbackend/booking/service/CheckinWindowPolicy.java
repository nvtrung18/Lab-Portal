package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.booking.entity.Booking;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CheckinWindowPolicy {

    public static final Duration EARLY_CHECKIN_WINDOW = Duration.ofMinutes(5);

    private final SystemConfigService systemConfigService;

    public void validate(Booking booking, Instant checkedAt) {
        if (checkedAt.isBefore(opensAt(booking))) {
            throw new IllegalStateException("Chưa đến giờ check-in. Có thể check-in trước giờ bắt đầu 5 phút.");
        }
        if (checkedAt.isAfter(closesAt(booking))) {
            throw new IllegalStateException("Đã quá thời gian check-in.");
        }
    }

    public Instant opensAt(Booking booking) {
        return booking.getStartTime().minus(EARLY_CHECKIN_WINDOW);
    }

    public Instant closesAt(Booking booking) {
        return booking.getStartTime().plus(lateCheckinWindow());
    }

    public CandidateWindow candidateWindow(Instant now) {
        return new CandidateWindow(now.minus(lateCheckinWindow()), now.plus(EARLY_CHECKIN_WINDOW));
    }

    private Duration lateCheckinWindow() {
        SystemConfigResponse config = systemConfigService.getConfig();
        if (config == null || config.booking() == null || config.booking().checkinWindowMinutes() <= 0) {
            throw new IllegalStateException("Check-in window is not configured");
        }
        return Duration.ofMinutes(config.booking().checkinWindowMinutes());
    }

    public record CandidateWindow(Instant earliestStart, Instant latestStart) {
    }
}
