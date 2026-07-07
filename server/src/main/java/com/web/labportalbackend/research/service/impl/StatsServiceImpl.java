package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.ResearchAttendanceDTO;
import com.web.labportalbackend.research.dto.StudentAttendanceDTO;
import com.web.labportalbackend.research.dto.response.ProjectStatsOverviewResponse;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.port.BookingStatsPort;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.StatsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private enum StatsScope {
        LAB,
        GROUP,
        USER
    }

    private final ProjectRepository projectRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;
    private final BookingStatsPort bookingStatsPort;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public ProjectStatsOverviewResponse getProjectStats(Long projectId) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        User currentUser = getCurrentUser();
        AccessScope accessScope = resolveAccessScope(currentUser, project);

        ResearchAttendanceDTO attendance = switch (accessScope.scope()) {
            case LAB -> bookingStatsPort.getAttendanceByProject(projectId);
            case GROUP -> bookingStatsPort.getAttendanceByGroup(projectId, accessScope.groupId());
            case USER -> bookingStatsPort.getAttendanceByStudent(projectId, currentUser.getId());
        };

        List<StudentAttendanceDTO> attendanceByStudent = switch (accessScope.scope()) {
            case LAB -> bookingStatsPort.getAttendanceByStudents(projectId);
            case GROUP -> bookingStatsPort.getAttendanceByStudents(projectId, accessScope.groupId());
            case USER -> List.of(StudentAttendanceDTO.builder()
                    .studentId(currentUser.getId())
                    .studentName(displayName(currentUser))
                    .attendanceCount(attendance.getAttendedSessions())
                    .expectedAttendanceCount(attendance.getTotalBookings())
                    .attendanceRate(attendance.getAttendanceRate())
                    .build());
        };

        TaskAggregate taskAggregate = getTaskAggregate(projectId, accessScope);
        CountAggregate milestoneAggregate = getMilestoneAggregate(projectId, accessScope);
        ReportAggregate reportAggregate = getReportAggregate(projectId, accessScope);
        long productCount = getProductCount(projectId, accessScope);
        double averageEvaluationScore = getAverageEvaluationScore(projectId, accessScope);

        ProjectStatsOverviewResponse.Overview overview = ProjectStatsOverviewResponse.Overview.builder()
                .memberCount(getMemberCount(projectId, accessScope))
                .milestoneCount(milestoneAggregate.total())
                .completedMilestoneCount(milestoneAggregate.completed())
                .taskCount(taskAggregate.total())
                .completedTaskCount(taskAggregate.done())
                .overdueTaskCount(taskAggregate.overdue())
                .taskCompletionRate(calculateRate(taskAggregate.total(), taskAggregate.done()))
                .reportCount(reportAggregate.total())
                .approvedReportCount(reportAggregate.approved())
                .productCount(productCount)
                .averageEvaluationScore(averageEvaluationScore)
                .attendanceCount(attendance.getAttendedSessions())
                .attendanceRate(attendance.getAttendanceRate())
                .build();

        return ProjectStatsOverviewResponse.builder()
                .projectId(project.getId())
                .projectTitle(project.getTitle())
                .scope(accessScope.responseScope())
                .scopeLabel(accessScope.responseScopeLabel())
                .scopeGroupId(accessScope.groupId())
                .overview(overview)
                .taskByStatus(getTaskByStatus(projectId, accessScope))
                .milestoneProgress(getMilestoneProgress(projectId, accessScope))
                .attendanceByStudent(attendanceByStudent)
                .groupProgress(getGroupProgress(projectId, accessScope))
                .build();
    }

    private AccessScope resolveAccessScope(User currentUser, ProjectEntity project) {
        Long projectId = project.getId();
        if (currentUser.hasRole("LAB_MANAGER")) {
            Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("LAB_MANAGER is not assigned to a laboratory"));
            if (!managedLab.getId().equals(project.getLab().getId())) {
                throw new AccessDeniedException("User cannot access stats for this project");
            }
            return new AccessScope(StatsScope.LAB, null, null, "PROJECT", "Tổng quan đề tài");
        }

        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only lab managers and project members can view project stats");
        }

        GroupRole role = groupMemberRepository.findActiveRoleByProjectIdAndUserId(projectId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("User is not a member of this project"));
        if (role == GroupRole.LEADER) {
            Long groupId = groupMemberRepository.findActiveGroupIdsByProjectIdAndUserId(projectId, currentUser.getId())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new AccessDeniedException("User is not a leader in this project"));
            return new AccessScope(StatsScope.GROUP, groupId, currentUser.getId(), "GROUP", "Tổng quan nhóm của tôi");
        }
        return new AccessScope(StatsScope.USER, null, currentUser.getId(), "ME", "Tổng quan cá nhân");
    }

    private long getMemberCount(Long projectId, AccessScope accessScope) {
        if (accessScope.scope() == StatsScope.USER) {
            return 1;
        }
        Query query = entityManager.createNativeQuery("""
                SELECT COUNT(DISTINCT gm.user_id)
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id OR g.project_id = p.id
                JOIN group_members gm ON gm.group_id = g.id
                WHERE p.id = :projectId
                  AND (:groupId IS NULL OR g.id = :groupId)
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                  AND gm.active = true AND gm.deleted = false
                """);
        query.setParameter("projectId", projectId);
        query.setParameter("groupId", accessScope.groupId());
        return toLong(query.getSingleResult());
    }

    private CountAggregate getMilestoneAggregate(Long projectId, AccessScope accessScope) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                    COUNT(DISTINCT m.id) AS total_count,
                    COALESCE(SUM(CASE WHEN m.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_count
                FROM milestones m
                WHERE m.project_id = :projectId
                  AND m.active = true AND m.deleted = false
                  AND (
                        :scope <> 'USER'
                        OR m.assigned_to_student_id = :userId
                        OR EXISTS (
                            SELECT 1 FROM tasks t
                            WHERE t.milestone_id = m.id
                              AND t.assignee_id = :userId
                              AND t.active = true AND t.deleted = false
                        )
                  )
                """);
        setScopeParams(query, projectId, accessScope);
        Object[] row = (Object[]) query.getSingleResult();
        return new CountAggregate(toLong(row[0]), toLong(row[1]));
    }

    private TaskAggregate getTaskAggregate(Long projectId, AccessScope accessScope) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                    COUNT(t.id) AS total_count,
                    COALESCE(SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END), 0) AS done_count,
                    COALESCE(SUM(CASE WHEN t.deadline IS NOT NULL
                        AND t.deadline < CURRENT_DATE
                        AND t.status NOT IN ('DONE', 'CANCELLED')
                        THEN 1 ELSE 0 END), 0) AS overdue_count
                FROM tasks t
                JOIN milestones m ON m.id = t.milestone_id
                WHERE m.project_id = :projectId
                  AND t.active = true AND t.deleted = false
                  AND m.active = true AND m.deleted = false
                  AND (:scope <> 'USER' OR t.assignee_id = :userId)
                  AND (:scope <> 'GROUP' OR EXISTS (
                        SELECT 1 FROM group_members gm
                        WHERE gm.group_id = :groupId
                          AND gm.user_id = t.assignee_id
                          AND gm.active = true AND gm.deleted = false
                  ))
                """);
        setScopeParams(query, projectId, accessScope);
        Object[] row = (Object[]) query.getSingleResult();
        return new TaskAggregate(toLong(row[0]), toLong(row[1]), toLong(row[2]));
    }

    private ReportAggregate getReportAggregate(Long projectId, AccessScope accessScope) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                    COUNT(r.id) AS total_count,
                    COALESCE(SUM(CASE WHEN r.status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved_count
                FROM reports r
                JOIN milestones m ON m.id = r.milestone_id
                WHERE m.project_id = :projectId
                  AND r.active = true AND r.deleted = false
                  AND m.active = true AND m.deleted = false
                  AND (:scope <> 'USER' OR r.submitted_by_id = :userId)
                  AND (:scope <> 'GROUP' OR r.group_id = :groupId OR EXISTS (
                        SELECT 1 FROM group_members gm
                        WHERE gm.group_id = :groupId
                          AND gm.user_id = r.submitted_by_id
                          AND gm.active = true AND gm.deleted = false
                  ))
                """);
        setScopeParams(query, projectId, accessScope);
        Object[] row = (Object[]) query.getSingleResult();
        return new ReportAggregate(toLong(row[0]), toLong(row[1]));
    }

    private long getProductCount(Long projectId, AccessScope accessScope) {
        Query query = entityManager.createNativeQuery("""
                SELECT COUNT(p.id)
                FROM products p
                WHERE p.project_id = :projectId
                  AND p.active = true AND p.deleted = false
                  AND (:scope <> 'USER' OR p.submitted_by_id = :userId)
                  AND (:scope <> 'GROUP' OR p.group_id = :groupId OR EXISTS (
                        SELECT 1 FROM group_members gm
                        WHERE gm.group_id = :groupId
                          AND gm.user_id = p.submitted_by_id
                          AND gm.active = true AND gm.deleted = false
                  ))
                """);
        setScopeParams(query, projectId, accessScope);
        return toLong(query.getSingleResult());
    }

    private double getAverageEvaluationScore(Long projectId, AccessScope accessScope) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(AVG(e.score), 0)
                FROM evaluations e
                WHERE e.project_id = :projectId
                  AND e.active = true AND e.deleted = false
                  AND (:scope <> 'USER' OR e.student_id = :userId)
                  AND (:scope <> 'GROUP' OR e.group_id = :groupId OR EXISTS (
                        SELECT 1 FROM group_members gm
                        WHERE gm.group_id = :groupId
                          AND gm.user_id = e.student_id
                          AND gm.active = true AND gm.deleted = false
                  ))
                """);
        setScopeParams(query, projectId, accessScope);
        return round(toDouble(query.getSingleResult()));
    }

    private Map<String, Long> getTaskByStatus(Long projectId, AccessScope accessScope) {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            stats.put(status.name(), 0L);
        }
        Query query = entityManager.createNativeQuery("""
                SELECT t.status, COUNT(t.id)
                FROM tasks t
                JOIN milestones m ON m.id = t.milestone_id
                WHERE m.project_id = :projectId
                  AND t.active = true AND t.deleted = false
                  AND m.active = true AND m.deleted = false
                  AND (:scope <> 'USER' OR t.assignee_id = :userId)
                  AND (:scope <> 'GROUP' OR EXISTS (
                        SELECT 1 FROM group_members gm
                        WHERE gm.group_id = :groupId
                          AND gm.user_id = t.assignee_id
                          AND gm.active = true AND gm.deleted = false
                  ))
                GROUP BY t.status
                """);
        setScopeParams(query, projectId, accessScope);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        for (Object[] row : rows) {
            stats.put((String) row[0], toLong(row[1]));
        }
        return stats;
    }

    private List<ProjectStatsOverviewResponse.MilestoneProgress> getMilestoneProgress(Long projectId, AccessScope accessScope) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                    m.id,
                    COALESCE(m.title, m.name) AS milestone_title,
                    m.status,
                    m.progress_percent,
                    COUNT(t.id) AS task_count,
                    COALESCE(SUM(CASE WHEN t.status = 'DONE' THEN 1 ELSE 0 END), 0) AS done_count
                FROM milestones m
                LEFT JOIN tasks t ON t.milestone_id = m.id
                    AND t.active = true
                    AND t.deleted = false
                    AND (:scope <> 'USER' OR t.assignee_id = :userId)
                    AND (:scope <> 'GROUP' OR EXISTS (
                        SELECT 1 FROM group_members gm
                        WHERE gm.group_id = :groupId
                          AND gm.user_id = t.assignee_id
                          AND gm.active = true AND gm.deleted = false
                    ))
                WHERE m.project_id = :projectId
                  AND m.active = true AND m.deleted = false
                  AND (:scope <> 'USER' OR m.assigned_to_student_id = :userId OR EXISTS (
                        SELECT 1 FROM tasks mt
                        WHERE mt.milestone_id = m.id
                          AND mt.assignee_id = :userId
                          AND mt.active = true AND mt.deleted = false
                  ))
                GROUP BY m.id, m.title, m.name, m.status, m.progress_percent, m.deadline, m.created_at
                ORDER BY m.deadline ASC, m.created_at ASC
                """);
        setScopeParams(query, projectId, accessScope);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<ProjectStatsOverviewResponse.MilestoneProgress> result = new ArrayList<>();
        for (Object[] row : rows) {
            long taskCount = toLong(row[4]);
            long doneCount = toLong(row[5]);
            double progress = taskCount > 0 ? calculateRate(taskCount, doneCount) : toDouble(row[3]);
            result.add(ProjectStatsOverviewResponse.MilestoneProgress.builder()
                    .milestoneId(toLongObject(row[0]))
                    .title((String) row[1])
                    .progressPercent(progress)
                    .status((String) row[2])
                    .build());
        }
        return result;
    }

    private List<ProjectStatsOverviewResponse.GroupProgress> getGroupProgress(Long projectId, AccessScope accessScope) {
        if (accessScope.scope() == StatsScope.USER) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery("""
                SELECT
                    g.id,
                    g.name,
                    (
                        SELECT COUNT(DISTINCT gm.user_id)
                        FROM group_members gm
                        WHERE gm.group_id = g.id
                          AND gm.active = true AND gm.deleted = false
                    ) AS member_count,
                    (
                        SELECT COUNT(t.id)
                        FROM milestones m
                        JOIN tasks t ON t.milestone_id = m.id
                        WHERE m.project_id = p.id
                          AND m.active = true AND m.deleted = false
                          AND t.active = true AND t.deleted = false
                          AND EXISTS (
                              SELECT 1 FROM group_members tgm
                              WHERE tgm.group_id = g.id
                                AND tgm.user_id = t.assignee_id
                                AND tgm.active = true AND tgm.deleted = false
                          )
                    ) AS task_count,
                    (
                        SELECT COUNT(t.id)
                        FROM milestones m
                        JOIN tasks t ON t.milestone_id = m.id
                        WHERE m.project_id = p.id
                          AND m.active = true AND m.deleted = false
                          AND t.active = true AND t.deleted = false
                          AND t.status = 'DONE'
                          AND EXISTS (
                              SELECT 1 FROM group_members tgm
                              WHERE tgm.group_id = g.id
                                AND tgm.user_id = t.assignee_id
                                AND tgm.active = true AND tgm.deleted = false
                          )
                    ) AS done_task_count,
                    (
                        SELECT COUNT(r.id)
                        FROM milestones m
                        JOIN reports r ON r.milestone_id = m.id
                        WHERE m.project_id = p.id
                          AND m.active = true AND m.deleted = false
                          AND r.active = true AND r.deleted = false
                          AND (r.group_id = g.id OR EXISTS (
                              SELECT 1 FROM group_members rgm
                              WHERE rgm.group_id = g.id
                                AND rgm.user_id = r.submitted_by_id
                                AND rgm.active = true AND rgm.deleted = false
                          ))
                    ) AS report_count,
                    (
                        SELECT COUNT(pdt.id)
                        FROM products pdt
                        WHERE pdt.project_id = p.id
                          AND pdt.active = true AND pdt.deleted = false
                          AND (pdt.group_id = g.id OR EXISTS (
                              SELECT 1 FROM group_members pgm
                              WHERE pgm.group_id = g.id
                                AND pgm.user_id = pdt.submitted_by_id
                                AND pgm.active = true AND pgm.deleted = false
                          ))
                    ) AS product_count,
                    (
                        SELECT COALESCE(AVG(e.score), 0)
                        FROM evaluations e
                        WHERE e.project_id = p.id
                          AND e.active = true AND e.deleted = false
                          AND (e.group_id = g.id OR EXISTS (
                              SELECT 1 FROM group_members egm
                              WHERE egm.group_id = g.id
                                AND egm.user_id = e.student_id
                                AND egm.active = true AND egm.deleted = false
                          ))
                    ) AS average_score
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id OR g.project_id = p.id
                WHERE p.id = :projectId
                  AND (:groupId IS NULL OR g.id = :groupId)
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                ORDER BY g.name ASC
                """);
        query.setParameter("projectId", projectId);
        query.setParameter("groupId", accessScope.groupId());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> ProjectStatsOverviewResponse.GroupProgress.builder()
                        .groupId(toLongObject(row[0]))
                        .groupName((String) row[1])
                        .memberCount(toLong(row[2]))
                        .taskCompletionRate(calculateRate(toLong(row[3]), toLong(row[4])))
                        .reportCount(toLong(row[5]))
                        .productCount(toLong(row[6]))
                        .averageEvaluationScore(round(toDouble(row[7])))
                        .build())
                .toList();
    }

    private void setScopeParams(Query query, Long projectId, AccessScope accessScope) {
        setParameterIfPresent(query, "projectId", projectId);
        setParameterIfPresent(query, "scope", accessScope.scope().name());
        setParameterIfPresent(query, "userId", accessScope.userId());
        setParameterIfPresent(query, "groupId", accessScope.groupId());
    }

    private void setParameterIfPresent(Query query, String name, Object value) {
        try {
            query.setParameter(name, value);
        } catch (IllegalArgumentException ignored) {
            // Some aggregate queries do not need every scope parameter.
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName();
    }

    private double calculateRate(long total, long completed) {
        if (total == 0) {
            return 0;
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Long toLongObject(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private double toDouble(Object value) {
        return value == null ? 0 : ((Number) value).doubleValue();
    }

    private record AccessScope(
            StatsScope scope,
            Long groupId,
            Long userId,
            String responseScope,
            String responseScopeLabel
    ) {
    }

    private record CountAggregate(long total, long completed) {
    }

    private record TaskAggregate(long total, long done, long overdue) {
    }

    private record ReportAggregate(long total, long approved) {
    }
}
