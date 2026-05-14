package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    @Query("""
            SELECT COUNT(gm) > 0
            FROM GroupMemberEntity gm
            JOIN ProjectEntity p ON p.group.id = gm.group.id
            JOIN MilestoneEntity m ON m.project.id = p.id
            JOIN TaskEntity t ON t.milestoneId = m.id
            JOIN ReportEntity r ON r.taskId = t.id
            WHERE r.id = :reportId
              AND gm.user.id = :userId
            """)
    boolean existsByReportIdAndUserId(@Param("reportId") Long reportId, @Param("userId") Long userId);
}
