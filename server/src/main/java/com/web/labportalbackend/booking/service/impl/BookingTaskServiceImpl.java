package com.web.labportalbackend.booking.service.impl;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${booking.task.no-show-grace-minutes:15}")
    private long noShowGraceMinutes;

    @Override
    @Scheduled(cron = "${booking.task.cron:0 * * * * *}")
    @Transactional
    public int processNoShows() {
        Instant cutoff = Instant.now().minus(noShowGraceMinutes, ChronoUnit.MINUTES);
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
}
