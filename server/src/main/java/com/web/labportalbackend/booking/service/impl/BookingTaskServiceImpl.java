package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.PenaltyRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.BookingTaskService;
import com.web.labportalbackend.booking.service.CleaningService;
import com.web.labportalbackend.booking.service.PenaltyService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import com.web.labportalbackend.common.enums.PenaltyType;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingTaskServiceImpl implements BookingTaskService {

    private static final String NO_SHOW_REASON = "Vắng mặt không thông báo";

    private final BookingRepository bookingRepository;
    private final PenaltyRepository penaltyRepository;
    private final PenaltyService penaltyService;
    private final TimeSlotRepository timeSlotRepository;
    private final CleaningRepository cleaningRepository;
    private final CleaningService cleaningService;
    private final SystemConfigService systemConfigService;
    private final NotificationEmitter notificationEmitter;

    @Override
    @Scheduled(cron = "${booking.task.cron:0 * * * * *}")
    @Transactional
    public int processNoShows() {
        Instant cutoff = checkinCutoff();
        List<Booking> candidates = bookingRepository.findNoShowCandidates(BookingStatus.APPROVED, cutoff);

        for (Booking booking : candidates) {
            booking.setStatus(BookingStatus.NO_SHOW);
            bookingRepository.save(booking);

            if (!penaltyRepository.existsByBookingIdAndTypeAndStatus(
                    booking.getId(),
                    PenaltyType.NO_SHOW,
                    PenaltyStatus.ACTIVE
            )) {
                PenaltyEntity penalty = PenaltyEntity.builder()
                        .user(booking.getUser())
                        .lab(booking.getLab())
                        .slot(booking.getTimeSlot())
                        .booking(booking)
                        .type(PenaltyType.NO_SHOW)
                        .point(1)
                        .reason(NO_SHOW_REASON)
                        .amount(penaltyService.getCurrentPenaltyAmount())
                        .status(PenaltyStatus.ACTIVE)
                        .build();
                penaltyRepository.save(penalty);
            }
        }

        if (!candidates.isEmpty()) {
            log.info("Processed {} no-show booking(s)", candidates.size());
        }
        return candidates.size();
    }

    @Override
    @Scheduled(cron = "${booking.task.cron:0 * * * * *}")
    @Transactional
    public int expirePastCheckinSlots() {
        List<TimeSlot> expiredSlots = timeSlotRepository.findCheckinExpiredSlots(
                List.of(TimeSlotStatus.AVAILABLE, TimeSlotStatus.FULL),
                checkinCutoff());
        expiredSlots.forEach(slot -> slot.setStatus(TimeSlotStatus.EXPIRED));
        if (!expiredSlots.isEmpty()) {
            timeSlotRepository.saveAll(expiredSlots);
            log.info("Expired {} time slot(s) after the check-in window", expiredSlots.size());
        }
        return expiredSlots.size();
    }

    @Override
    @Scheduled(cron = "${booking.task.cron:0 * * * * *}")
    public int createCleaningTasksForEndedSlots() {
        Instant cutoff = Instant.now();
        List<TimeSlot> endedSlots = timeSlotRepository.findEndedSlots(cutoff);
        int created = 0;

        for (TimeSlot slot : endedSlots) {
            if (!cleaningRepository.existsBySlotId(slot.getId())) {
                cleaningService.createCleaningTask(slot.getId());
                created++;
            }
        }

        if (created > 0) {
            log.info("Created {} cleaning task(s) for ended slots", created);
        }
        return created;
    }

    @Override
    @Scheduled(cron = "${booking.task.cron:0 * * * * *}")
    @Transactional
    public int completeEndedSessions() {
        Instant now = Instant.now();
        List<Booking> endedSessions = bookingRepository.findEndedSessionCandidates(
                List.of(BookingStatus.CHECKED_IN, BookingStatus.IN_PROGRESS), now);
        endedSessions.forEach(booking -> booking.setStatus(BookingStatus.COMPLETED));
        if (!endedSessions.isEmpty()) {
            bookingRepository.saveAll(endedSessions);
            endedSessions.forEach(booking -> notificationEmitter.emit(
                    booking.getUser().getId(),
                    NotificationEventType.BOOKING_SESSION_COMPLETED,
                    "Ca sử dụng đã kết thúc",
                    "Ca sử dụng tại " + booking.getLab().getLabName() + " đã tự động kết thúc theo lịch.",
                    NotificationTargetModule.BOOKING,
                    booking.getId(),
                    null
            ));
            log.info("Automatically completed {} ended lab session(s)", endedSessions.size());
        }
        List<TimeSlot> endedSlots = timeSlotRepository.findEndedSessionSlots(
                List.of(TimeSlotStatus.AVAILABLE, TimeSlotStatus.FULL, TimeSlotStatus.EXPIRED), now);
        endedSlots.forEach(slot -> slot.setStatus(TimeSlotStatus.CLOSED));
        if (!endedSlots.isEmpty()) {
            timeSlotRepository.saveAll(endedSlots);
            log.info("Automatically closed {} ended lab slot(s)", endedSlots.size());
        }
        return endedSessions.size();
    }

    private Instant checkinCutoff() {
        SystemConfigResponse config = systemConfigService.getConfig();
        if (config == null || config.booking() == null || config.booking().checkinWindowMinutes() <= 0) {
            throw new IllegalStateException("Valid booking check-in configuration is required");
        }
        return Instant.now().minus(config.booking().checkinWindowMinutes(), ChronoUnit.MINUTES);
    }
}
