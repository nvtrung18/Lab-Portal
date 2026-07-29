package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT gm
            FROM GroupMemberEntity gm
            WHERE gm.group.id = :groupId
              AND gm.user.id = :userId
              AND gm.active = true
              AND gm.deleted = false
            """)
    Optional<GroupMemberEntity> findActiveForStatusAuthorization(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

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
            LEFT JOIN ProjectEntity p ON p.group.id = g.id
            WHERE gm.user.id = :userId
              AND gm.active = true
              AND gm.deleted = false
              AND g.active = true
              AND g.deleted = false
              AND (g.project.id = :projectId OR p.id = :projectId)
            """)
    boolean existsActiveMemberByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT g.id
            FROM GroupMemberEntity gm
            JOIN gm.group g
            LEFT JOIN ProjectEntity p ON p.group.id = g.id
            WHERE gm.user.id = :userId
              AND gm.active = true
              AND gm.deleted = false
              AND g.active = true
              AND g.deleted = false
              AND (g.project.id = :projectId OR p.id = :projectId)
            """)
    List<Long> findActiveGroupIdsByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT DISTINCT g.id
            FROM GroupMemberEntity gm
            JOIN gm.group g
            LEFT JOIN g.project gp
            LEFT JOIN ProjectEntity p ON p.group.id = g.id
            WHERE gm.user.id = :userId
              AND gm.user.active = true
              AND gm.user.deleted = false
              AND gm.role = :role
              AND gm.active = true
              AND gm.deleted = false
              AND g.active = true
              AND g.deleted = false
              AND ((gp.id = :projectId AND gp.active = true AND gp.deleted = false AND gp.lab.id = g.lab.id)
                   OR (p.id = :projectId AND p.active = true AND p.deleted = false AND p.lab.id = g.lab.id))
            """)
    List<Long> findActiveGroupIdsByProjectIdAndUserIdAndRole(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId,
            @Param("role") GroupRole role
    );

    @Query("""
            SELECT gm.role
            FROM GroupMemberEntity gm
            JOIN gm.group g
            LEFT JOIN ProjectEntity p ON p.group.id = g.id
            WHERE gm.user.id = :userId
              AND gm.active = true
              AND gm.deleted = false
              AND g.active = true
              AND g.deleted = false
              AND (g.project.id = :projectId OR p.id = :projectId)
            """)
    Optional<GroupRole> findActiveRoleByProjectIdAndUserId(
            @Param("projectId") Long projectId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT gm.role
            FROM GroupMemberEntity gm
            JOIN gm.group g
            WHERE g.id = :groupId
              AND gm.user.id = :userId
              AND gm.active = true
              AND gm.deleted = false
              AND g.active = true
              AND g.deleted = false
            """)
    Optional<GroupRole> findActiveRoleByGroupIdAndUserId(
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT COUNT(gm) > 0
            FROM GroupMemberEntity gm
            JOIN gm.group g
            LEFT JOIN ProjectEntity p ON p.group.id = g.id
            JOIN MilestoneEntity m ON (m.project.id = g.project.id OR m.project.id = p.id)
            JOIN ReportEntity r ON r.milestoneId = m.id
            WHERE r.id = :reportId
              AND gm.user.id = :userId
              AND (g.project.id = m.project.id OR p.id = m.project.id)
            """)
    boolean existsByReportIdAndUserId(@Param("reportId") Long reportId, @Param("userId") Long userId);

    @Query("""
            SELECT COUNT(gm) > 0
            FROM GroupMemberEntity gm
            JOIN gm.group g
            LEFT JOIN ProjectEntity p ON p.group.id = g.id
            JOIN MilestoneEntity m ON (m.project.id = g.project.id OR m.project.id = p.id)
            JOIN ReportEntity r ON r.milestoneId = m.id
            WHERE r.id = :reportId
              AND gm.user.id = :userId
              AND (g.project.id = m.project.id OR p.id = m.project.id)
              AND gm.role = com.web.labportalbackend.research.enums.GroupRole.LEADER
              AND gm.active = true
              AND gm.deleted = false
            """)
    boolean existsLeaderByReportIdAndUserId(@Param("reportId") Long reportId, @Param("userId") Long userId);

    @Query("""
            SELECT gm.role
            FROM GroupMemberEntity gm
            JOIN gm.group g
            LEFT JOIN ProjectEntity p ON p.group.id = g.id
            JOIN MilestoneEntity m ON (m.project.id = g.project.id OR m.project.id = p.id)
            JOIN ReportEntity r ON r.milestoneId = m.id
            WHERE r.id = :reportId
              AND gm.user.id = :userId
              AND (g.project.id = m.project.id OR p.id = m.project.id)
              AND gm.active = true
              AND gm.deleted = false
            """)
    Optional<GroupRole> findActiveRoleByReportIdAndUserId(
            @Param("reportId") Long reportId,
            @Param("userId") Long userId
    );
}
