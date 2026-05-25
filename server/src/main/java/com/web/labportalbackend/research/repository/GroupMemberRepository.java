package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupMemberEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(Long groupId, Long userId);

    @EntityGraph(attributePaths = {
            "group",
            "group.lab",
            "group.lab.manager",
            "group.topic",
            "group.topic.manager",
            "group.project",
            "group.project.manager",
            "group.leader",
            "group.members",
            "group.members.user"
    })
    List<GroupMemberEntity> findByUserIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(Long userId);

    @EntityGraph(attributePaths = {
            "group",
            "group.lab",
            "group.lab.manager",
            "group.topic",
            "group.topic.manager",
            "group.project",
            "group.project.manager",
            "group.leader",
            "group.members",
            "group.members.user"
    })
    List<GroupMemberEntity> findByUserIdAndGroupLabIdAndActiveTrueAndDeletedFalseAndGroupActiveTrueAndGroupDeletedFalse(
            Long userId,
            Long labId
    );

    @Query("""
            SELECT COUNT(gm) > 0
            FROM GroupMemberEntity gm
            JOIN gm.group g
            WHERE gm.user.id = :userId
              AND gm.active = true
              AND gm.deleted = false
              AND g.active = true
              AND g.deleted = false
              AND g.project.id = :projectId
            """)
    boolean existsActiveMemberByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

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
