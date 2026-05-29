package com.web.labportalbackend.admin.dashboard.service.impl;

import com.web.labportalbackend.admin.dashboard.dto.AdminDashboardStatsResponse;
import com.web.labportalbackend.admin.dashboard.service.AdminDashboardService;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.ComplaintRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.CleaningStatus;
import com.web.labportalbackend.common.enums.ComplaintStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.repository.ApplicationRepository;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.enums.GroupStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.enums.ReportStatus;
import com.web.labportalbackend.research.repository.EvaluationRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProductRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final List<BookingStatus> COUNTED_TODAY_BOOKING_STATUSES = List.of(
            BookingStatus.PENDING_APPROVAL,
            BookingStatus.APPROVED,
            BookingStatus.PENDING,
            BookingStatus.CONFIRMED,
            BookingStatus.CHECKED_IN,
            BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED,
            BookingStatus.NO_SHOW
    );

    private static final List<CleaningStatus> COMPLETED_CLEANING_STATUSES = List.of(
            CleaningStatus.DONE,
            CleaningStatus.COMPLETED,
            CleaningStatus.CANCELLED
    );

    private static final List<ReportStatus> PENDING_MANAGER_REPORT_STATUSES = List.of(
            ReportStatus.LEADER_REVIEWED
    );

    private final UserRepository userRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final ApplicationRepository applicationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final ComplaintRepository complaintRepository;
    private final CleaningRepository cleaningRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final ReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final EvaluationRepository evaluationRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getStats() {
        DayRange today = resolveTodayRange();

        return new AdminDashboardStatsResponse(
                getUserStats(),
                getLabStats(),
                getOperationStats(today),
                getResearchStats()
        );
    }

    private AdminDashboardStatsResponse.UserStats getUserStats() {
        return new AdminDashboardStatsResponse.UserStats(
                userRepository.countRegisteredUsers(),
                userRepository.countByStatusAndNotDeleted(UserStatus.ACTIVE),
                userRepository.countByStatusAndNotDeleted(UserStatus.SUSPENDED),
                userRepository.countByStatusAndNotDeleted(UserStatus.PENDING_VERIFICATION),
                userRepository.countByRoleNameAndNotDeleted("STUDENT"),
                userRepository.countByRoleNameAndNotDeleted("LAB_MANAGER"),
                userRepository.countUnassignedManagers()
        );
    }

    private AdminDashboardStatsResponse.LabStats getLabStats() {
        return new AdminDashboardStatsResponse.LabStats(
                laboratoryRepository.countNotDeleted(),
                laboratoryRepository.countByStatusAndNotDeleted(LabStatus.AVAILABLE),
                laboratoryRepository.countByStatusAndNotDeleted(LabStatus.INACTIVE),
                laboratoryRepository.countWithoutManager()
        );
    }

    private AdminDashboardStatsResponse.OperationStats getOperationStats(DayRange today) {
        return new AdminDashboardStatsResponse.OperationStats(
                applicationRepository.countByStatusAndNotDeleted(ApplicationStatus.PENDING),
                timeSlotRepository.countActiveSlotsStartingBetween(today.startInclusive(), today.endExclusive()),
                bookingRepository.countActiveBookingsStartingBetweenAndStatusIn(
                        today.startInclusive(),
                        today.endExclusive(),
                        COUNTED_TODAY_BOOKING_STATUSES
                ),
                complaintRepository.countActiveByStatus(ComplaintStatus.PENDING),
                cleaningRepository.countActiveIncomplete(COMPLETED_CLEANING_STATUSES)
        );
    }

    private AdminDashboardStatsResponse.ResearchStats getResearchStats() {
        return new AdminDashboardStatsResponse.ResearchStats(
                projectRepository.countActiveByStatus(ProjectStatus.ONGOING),
                groupRepository.countActiveByStatus(GroupStatus.ACTIVE),
                reportRepository.countActiveByStatusIn(PENDING_MANAGER_REPORT_STATUSES),
                productRepository.countSubmittedProducts(),
                round(evaluationRepository.findAverageActiveTotalScore())
        );
    }

    private DayRange resolveTodayRange() {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        Instant start = today.atStartOfDay(zoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zoneId).toInstant();
        return new DayRange(start, end);
    }

    private double round(Double value) {
        return BigDecimal.valueOf(value == null ? 0 : value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record DayRange(Instant startInclusive, Instant endExclusive) {
    }
}
