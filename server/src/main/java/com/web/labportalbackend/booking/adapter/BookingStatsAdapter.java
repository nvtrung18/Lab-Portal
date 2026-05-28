package com.web.labportalbackend.booking.adapter;

import com.web.labportalbackend.research.dto.ResearchAttendanceDTO;
import com.web.labportalbackend.research.dto.StudentAttendanceDTO;
import com.web.labportalbackend.research.port.BookingStatsPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class BookingStatsAdapter implements BookingStatsPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public ResearchAttendanceDTO getAttendanceByProject(Long projectId) {
        return getAttendance(projectId, null, null);
    }

    @Override
    public ResearchAttendanceDTO getAttendanceByGroup(Long projectId, Long groupId) {
        return getAttendance(projectId, groupId, null);
    }

    @Override
    public ResearchAttendanceDTO getAttendanceByStudent(Long projectId, Long studentId) {
        return getAttendance(projectId, null, studentId);
    }

    @Override
    public List<StudentAttendanceDTO> getAttendanceByStudents(Long projectId) {
        return getAttendanceByStudents(projectId, null);
    }

    @Override
    public List<StudentAttendanceDTO> getAttendanceByStudents(Long projectId, Long groupId) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                    u.id AS student_id,
                    COALESCE(u.full_name, u.username) AS student_name,
                    COALESCE(SUM(CASE WHEN b.status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') THEN 1 ELSE 0 END), 0) AS attended_sessions,
                    COUNT(b.id) AS total_bookings
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id OR g.project_id = p.id
                JOIN group_members gm ON gm.group_id = g.id
                JOIN users u ON u.id = gm.user_id
                LEFT JOIN bookings b ON b.user_id = gm.user_id
                    AND b.lab_id = g.lab_id
                    AND b.active = true
                    AND b.deleted = false
                WHERE p.id = :projectId
                  AND (:groupId IS NULL OR g.id = :groupId)
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                  AND gm.active = true AND gm.deleted = false
                GROUP BY u.id, u.full_name, u.username
                ORDER BY student_name ASC
                """);
        query.setParameter("projectId", projectId);
        query.setParameter("groupId", groupId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        return rows.stream()
                .map(row -> StudentAttendanceDTO.builder()
                        .studentId(toLongObject(row[0]))
                        .studentName((String) row[1])
                        .attendanceCount(toLong(row[2]))
                        .expectedAttendanceCount(toLong(row[3]))
                        .attendanceRate(calculateRate(toLong(row[3]), toLong(row[2])))
                        .build())
                .toList();
    }

    private ResearchAttendanceDTO getAttendance(Long projectId, Long groupId, Long studentId) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                    COUNT(b.id) AS total_bookings,
                    COALESCE(SUM(CASE WHEN b.status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') THEN 1 ELSE 0 END), 0) AS attended_sessions,
                    COALESCE(SUM(CASE WHEN b.status = 'NO_SHOW' THEN 1 ELSE 0 END), 0) AS no_show_sessions
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id OR g.project_id = p.id
                JOIN group_members gm ON gm.group_id = g.id
                LEFT JOIN bookings b ON b.user_id = gm.user_id
                    AND b.lab_id = g.lab_id
                    AND b.active = true
                    AND b.deleted = false
                WHERE p.id = :projectId
                  AND (:groupId IS NULL OR g.id = :groupId)
                  AND (:studentId IS NULL OR gm.user_id = :studentId)
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                  AND gm.active = true AND gm.deleted = false
                """);
        query.setParameter("projectId", projectId);
        query.setParameter("groupId", groupId);
        query.setParameter("studentId", studentId);
        Object[] row = (Object[]) query.getSingleResult();
        long totalBookings = toLong(row[0]);
        long attendedSessions = toLong(row[1]);
        return ResearchAttendanceDTO.builder()
                .totalBookings(totalBookings)
                .attendedSessions(attendedSessions)
                .noShowSessions(toLong(row[2]))
                .attendanceRate(calculateRate(totalBookings, attendedSessions))
                .build();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Long toLongObject(Object value) {
        return value == null ? null : ((Number) value).longValue();
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
}
