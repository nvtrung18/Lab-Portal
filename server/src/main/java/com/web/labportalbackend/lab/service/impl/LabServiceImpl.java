package com.web.labportalbackend.lab.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.dto.response.AssignableLabResponse;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.mapper.LabMapper;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.service.LabService;
import com.web.labportalbackend.common.enums.LabStatus;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.web.labportalbackend.lab.dto.response.LabDashboardStatsResponse;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.ComplaintRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.research.dto.StudentAttendanceDTO;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.CleaningStatus;
import com.web.labportalbackend.common.enums.ComplaintStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabServiceImpl implements LabService {

    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final MembershipRepository membershipRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final CleaningRepository cleaningRepository;
    private final ComplaintRepository complaintRepository;
    private final ProjectRepository projectRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public LabResponse createLab(CreateLabRequest request) {
        if (laboratoryRepository.existsByLabName(request.getLabName())) {
            throw new IllegalArgumentException("Laboratory name is already in use: " + request.getLabName());
        }
        Laboratory laboratory = LabMapper.toEntity(request);
        laboratory.setStatus(LabStatus.AVAILABLE);
        Laboratory savedLab = laboratoryRepository.save(laboratory);
        log.info("Laboratory created: {} (ID: {})", savedLab.getLabName(), savedLab.getId());
        auditLogService.logCurrentUser(
                AuditAction.CREATE_LAB,
                AuditModule.LAB,
                "LAB",
                savedLab.getId(),
                "Admin đã tạo PTN " + savedLab.getLabName() + "."
        );
        return LabMapper.toResponse(savedLab);
    }

    @Override
    @Transactional
    public LabResponse assignManager(Long labId, Long managerId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + managerId));
        if (!manager.hasRole("LAB_MANAGER")) {
            throw new IllegalArgumentException("User must have LAB_MANAGER role to be assigned as lab manager");
        }
        laboratoryRepository.findFirstByManagerIdAndDeletedFalse(managerId)
                .filter(existingLab -> !existingLab.getId().equals(labId))
                .ifPresent(existingLab -> {
                    throw new IllegalArgumentException("Lab manager is already assigned to another lab: " + existingLab.getLabName());
                });
        if (laboratory.getManager() != null && !laboratory.getManager().getId().equals(managerId)) {
            laboratory.setManager(null);
        }
        laboratory.setManager(manager);
        Laboratory updatedLab = laboratoryRepository.save(laboratory);
        log.info("Manager assigned to laboratory: {} -> User: {} (ID: {})",
                updatedLab.getLabName(), manager.getUsername(), manager.getId());
        auditLogService.logCurrentUser(
                AuditAction.ASSIGN_MANAGER,
                AuditModule.LAB,
                "LAB",
                updatedLab.getId(),
                "Admin đã gán " + displayName(manager) + " cho " + updatedLab.getLabName() + "."
        );
        return LabMapper.toResponse(updatedLab);
    }

    @Override
    @Transactional
    public LabResponse assignManagerPatch(Long labId, Long managerUserId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy PTN với ID: " + labId));
        
        User user = userRepository.findById(managerUserId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy người dùng với ID: " + managerUserId));

        if (!user.hasRole("LAB_MANAGER")) {
            throw new IllegalArgumentException("Người dùng phải có vai trò quản lý PTN.");
        }

        if (laboratory.getManager() != null) {
            throw new IllegalArgumentException("PTN này đã có quản lý.");
        }

        laboratoryRepository.findFirstByManagerIdAndDeletedFalse(managerUserId)
                .ifPresent(existingLab -> {
                    throw new IllegalArgumentException("Người dùng đã là quản lý của phòng thí nghiệm: " + existingLab.getLabName());
                });

        laboratory.setManager(user);
        Laboratory saved = laboratoryRepository.save(laboratory);

        auditLogService.logCurrentUser(
                AuditAction.ASSIGN_MANAGER,
                AuditModule.LAB,
                "LAB",
                saved.getId(),
                "Admin đã gán quản lý " + displayName(user) + " cho PTN " + saved.getLabName() + "."
        );

        return LabMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LabResponse updateStatus(Long labId, LabStatus status) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        laboratory.setStatus(status);
        Laboratory updatedLab = laboratoryRepository.save(laboratory);
        log.info("Laboratory status updated: {} -> {}", updatedLab.getLabName(), status);
        auditLogService.logCurrentUser(
                status == LabStatus.INACTIVE ? AuditAction.DEACTIVATE_LAB : AuditAction.UPDATE_LAB,
                AuditModule.LAB,
                "LAB",
                updatedLab.getId(),
                "Admin đã cập nhật trạng thái PTN " + updatedLab.getLabName() + " thành " + status + "."
        );
        return LabMapper.toResponse(updatedLab);
    }

    @Override
    @Transactional(readOnly = true)
    public LabResponse getLabById(Long labId) {
        Laboratory laboratory = laboratoryRepository.findById(labId)
                .orElseThrow(() -> new EntityNotFoundException("Laboratory not found with ID: " + labId));
        return LabMapper.toResponse(laboratory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LabResponse> getAllLabs() {
        return laboratoryRepository.findAll().stream()
                .filter(lab -> Boolean.TRUE.equals(lab.getActive()) && Boolean.FALSE.equals(lab.getDeleted()))
                .map(LabMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignableLabResponse> getAssignableLabs(String keyword, Boolean includeInactive) {
        boolean inclInactive = includeInactive != null && includeInactive;
        String searchKeyword = keyword != null ? keyword.trim().toLowerCase() : "";

        return laboratoryRepository.findAll().stream()
                .filter(lab -> Boolean.FALSE.equals(lab.getDeleted()))
                .filter(lab -> lab.getManager() == null)
                .filter(lab -> {
                    if (inclInactive) {
                        return true;
                    }
                    return lab.getStatus() == LabStatus.AVAILABLE;
                })
                .filter(lab -> {
                    if (searchKeyword.isEmpty()) {
                        return true;
                    }
                    return lab.getLabName().toLowerCase().contains(searchKeyword)
                            || (lab.getDepartment() != null && lab.getDepartment().toLowerCase().contains(searchKeyword));
                })
                .map(lab -> AssignableLabResponse.builder()
                        .id(lab.getId())
                        .name(lab.getLabName())
                        .department(lab.getDepartment())
                        .status(lab.getStatus().name())
                        .managerId(null)
                        .managerName(null)
                        .build())
                .toList();
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName();
    }

    @Override
    @Transactional(readOnly = true)
    public LabDashboardStatsResponse getLabDashboardStats(Long labId) {
        if (!laboratoryRepository.existsById(labId)) {
            throw new EntityNotFoundException("Laboratory not found with ID: " + labId);
        }

        // 1. memberCount
        long memberCount = membershipRepository.findByLaboratoryIdAndDeletedFalse(labId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                .count();

        // Resolving today range
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        Instant start = today.atStartOfDay(zoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        // 2. todaySlots
        long todaySlots = timeSlotRepository.countActiveSlotsStartingBetweenAndLabId(labId, start, end);

        // 3. todayBookings
        List<BookingStatus> statuses = List.of(
                BookingStatus.PENDING_APPROVAL,
                BookingStatus.APPROVED,
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED,
                BookingStatus.CHECKED_IN,
                BookingStatus.IN_PROGRESS,
                BookingStatus.COMPLETED,
                BookingStatus.NO_SHOW
        );
        long todayBookings = bookingRepository.countActiveBookingsStartingBetweenAndLabIdAndStatusIn(labId, start, end, statuses);

        // 4. pendingCleaningTasks
        List<CleaningStatus> completedStatuses = List.of(
                CleaningStatus.DONE,
                CleaningStatus.COMPLETED,
                CleaningStatus.CANCELLED
        );
        long pendingCleaningTasks = cleaningRepository.countActiveIncompleteByLabId(labId, completedStatuses);

        // 5. pendingComplaints
        long pendingComplaints = complaintRepository.countActiveByLabIdAndStatus(labId, ComplaintStatus.PENDING);

        // 6. activeResearchProjects
        long activeResearchProjects = projectRepository.countActiveByLabIdAndStatus(labId, ProjectStatus.ONGOING);

        // 7. Student attendance query
        Query query = entityManager.createNativeQuery("""
                SELECT
                    u.id AS student_id,
                    COALESCE(u.full_name, u.username) AS student_name,
                    COALESCE(SUM(CASE WHEN b.status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') THEN 1 ELSE 0 END), 0) AS attendance_count,
                    COUNT(b.id) AS expected_attendance_count
                FROM memberships m
                JOIN users u ON u.id = m.user_id
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                LEFT JOIN bookings b ON b.user_id = u.id
                    AND b.lab_id = m.lab_id
                    AND b.active = true
                    AND b.deleted = false
                WHERE m.lab_id = :labId
                  AND m.active = true
                  AND m.deleted = false
                  AND r.name = 'STUDENT'
                GROUP BY u.id, u.full_name, u.username
                ORDER BY student_name ASC
                """);
        query.setParameter("labId", labId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<StudentAttendanceDTO> attendanceByStudent = new ArrayList<>();
        long totalExpected = 0;
        long totalAttended = 0;

        for (Object[] row : rows) {
            Long studentId = ((Number) row[0]).longValue();
            String studentName = (String) row[1];
            long attendanceCount = ((Number) row[2]).longValue();
            long expectedAttendanceCount = ((Number) row[3]).longValue();
            double attendanceRate = 0.0;
            if (expectedAttendanceCount > 0) {
                attendanceRate = BigDecimal.valueOf(attendanceCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(expectedAttendanceCount), 2, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            totalExpected += expectedAttendanceCount;
            totalAttended += attendanceCount;

            attendanceByStudent.add(StudentAttendanceDTO.builder()
                    .studentId(studentId)
                    .studentName(studentName)
                    .attendanceCount(attendanceCount)
                    .expectedAttendanceCount(expectedAttendanceCount)
                    .attendanceRate(attendanceRate)
                    .build());
        }

        double overallAttendanceRate = 0.0;
        if (totalExpected > 0) {
            overallAttendanceRate = BigDecimal.valueOf(totalAttended)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalExpected), 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return LabDashboardStatsResponse.builder()
                .labId(labId)
                .memberCount(memberCount)
                .todaySlots(todaySlots)
                .todayBookings(todayBookings)
                .attendanceRate(overallAttendanceRate)
                .attendanceByStudent(attendanceByStudent)
                .pendingCleaningTasks(pendingCleaningTasks)
                .pendingComplaints(pendingComplaints)
                .activeResearchProjects(activeResearchProjects)
                .build();
    }
}

