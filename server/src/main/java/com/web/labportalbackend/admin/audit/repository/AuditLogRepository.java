package com.web.labportalbackend.admin.audit.repository;

import com.web.labportalbackend.admin.audit.entity.AuditLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Query("""
            SELECT CAST(a.createdAt AS LocalDate) AS day, a.module AS module, a.action AS action, COUNT(a) AS count
            FROM AuditLog a
            WHERE a.createdAt >= :cutoff
              AND a.deleted = false
              AND EXISTS (SELECT u.id FROM User u JOIN u.roles r
                          WHERE u.id = :actorId AND u.active = true AND u.deleted = false
                            AND u.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE
                            AND r.name = 'ADMIN')
            GROUP BY CAST(a.createdAt AS LocalDate), a.module, a.action
            ORDER BY CAST(a.createdAt AS LocalDate) DESC, a.module ASC, a.action ASC
            """)
    List<AiAuditSummaryBucket> findAiContextAuditBuckets(
            @Param("actorId") Long actorId, @Param("cutoff") Instant cutoff, Pageable pageable);
}
