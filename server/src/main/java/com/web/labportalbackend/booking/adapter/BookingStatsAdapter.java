package com.web.labportalbackend.booking.adapter;

import com.web.labportalbackend.research.dto.ResearchAttendanceDTO;
import com.web.labportalbackend.research.port.BookingStatsPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

@Component
public class BookingStatsAdapter implements BookingStatsPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public ResearchAttendanceDTO getAttendanceByProject(Long projectId) {
        Query query = entityManager.createNativeQuery("""
                SELECT
                    COUNT(b.id) AS total_bookings,
                    COALESCE(SUM(CASE WHEN b.status IN ('CHECKED_IN', 'IN_PROGRESS', 'COMPLETED') THEN 1 ELSE 0 END), 0) AS attended_sessions,
                    COALESCE(SUM(CASE WHEN b.status = 'NO_SHOW' THEN 1 ELSE 0 END), 0) AS no_show_sessions
                FROM projects p
                JOIN research_groups g ON g.id = p.group_id
                JOIN group_members gm ON gm.group_id = g.id
                JOIN bookings b ON b.user_id = gm.user_id AND b.lab_id = g.lab_id
                WHERE p.id = :projectId
                  AND p.active = true AND p.deleted = false
                  AND g.active = true AND g.deleted = false
                  AND gm.active = true AND gm.deleted = false
                  AND b.active = true AND b.deleted = false
                """);
        query.setParameter("projectId", projectId);

        Object[] row = (Object[]) query.getSingleResult();
        return ResearchAttendanceDTO.builder()
                .totalBookings(toLong(row[0]))
                .attendedSessions(toLong(row[1]))
                .noShowSessions(toLong(row[2]))
                .build();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
