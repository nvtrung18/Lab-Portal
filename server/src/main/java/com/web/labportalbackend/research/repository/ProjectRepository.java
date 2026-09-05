package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.ai.service.AiResearchContext;
import com.web.labportalbackend.research.enums.ProjectStatus;
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
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    @Query("""
            SELECT DISTINCT new com.web.labportalbackend.ai.service.AiResearchToolCandidateResource(
                p.id, p.title, p.id)
            FROM ProjectEntity p JOIN p.lab l
            WHERE p.active = true AND p.deleted = false AND l.active = true AND l.deleted = false
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND (
                       EXISTS (SELECT gm.id FROM GroupMemberEntity gm JOIN gm.group g
                               WHERE gm.user.id = :actorId AND gm.active = true AND gm.deleted = false
                                 AND g.active = true AND g.deleted = false AND g.lab.id = l.id
                                 AND (g.project.id = p.id OR p.group.id = g.id))
                       OR EXISTS (SELECT t.id FROM TaskEntity t
                                  WHERE t.projectId = p.id AND t.assigneeId = :actorId
                                    AND t.active = true AND t.deleted = false))))
            ORDER BY p.id ASC
            """)
    List<com.web.labportalbackend.ai.service.AiResearchToolCandidateResource> findAiToolCandidateProjects(
            @Param("actorId") Long actorId,
            @Param("selectedRoleName") String selectedRoleName,
            org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT p
            FROM ProjectEntity p
            WHERE p.id = :id
              AND p.deleted = false
              AND p.active = true
            """)
    Optional<ProjectEntity> findByIdForStatusAuthorization(@Param("id") Long id);

    @EntityGraph(attributePaths = {"group", "topic", "manager", "createdBy"})
    List<ProjectEntity> findByGroupIdAndDeletedFalseAndActiveTrue(Long groupId);

    @EntityGraph(attributePaths = {"lab", "group", "topic", "manager", "createdBy"})
    List<ProjectEntity> findByLabIdAndDeletedFalseAndActiveTrue(Long labId);

    @EntityGraph(attributePaths = "group")
    List<ProjectEntity> findByGroupId(Long groupId);

    @EntityGraph(attributePaths = {"lab", "group", "group.lab", "topic", "manager", "createdBy"})
    Optional<ProjectEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);

    long countByGroupIdAndDeletedFalseAndActiveTrue(Long groupId);

    @Query("SELECT COUNT(p) FROM ProjectEntity p WHERE p.status = :status AND p.deleted = false AND p.active = true")
    long countActiveByStatus(@Param("status") ProjectStatus status);

    @Query("SELECT COUNT(p) FROM ProjectEntity p WHERE p.lab.id = :labId AND p.status = :status AND p.deleted = false AND p.active = true")
    long countActiveByLabIdAndStatus(
            @Param("labId") Long labId,
            @Param("status") ProjectStatus status
    );

    @Query("""
            SELECT new com.web.labportalbackend.ai.service.AiResearchContext$Project(
                p.id, p.code, p.title, p.status, p.startDate, p.endDate)
            FROM ProjectEntity p JOIN p.lab l
            WHERE p.id = :projectId AND l.id = :labId
              AND p.active = true AND p.deleted = false AND l.active = true AND l.deleted = false
              AND EXISTS (SELECT a.id FROM User a WHERE a.id = :actorId AND a.active = true
                          AND a.deleted = false AND a.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND (EXISTS (SELECT gm.id FROM GroupMemberEntity gm JOIN gm.group g
                              WHERE gm.user.id = :actorId AND gm.active = true AND gm.deleted = false
                                AND g.active = true AND g.deleted = false
                                AND g.lab.id = l.id
                                AND (g.project.id = p.id OR p.group.id = g.id)
                                AND (g.project IS NULL OR g.project.id = p.id)
                                AND (p.group IS NULL OR p.group.id = g.id))
                   OR EXISTS (SELECT t.id FROM TaskEntity t
                              JOIN MilestoneEntity m ON m.id = t.milestoneId
                              JOIN GroupEntity g ON g.id = t.groupId
                              WHERE t.projectId = p.id AND t.assigneeId = :actorId
                                AND t.active = true AND t.deleted = false
                                AND m.active = true AND m.deleted = false AND m.project.id = p.id AND m.group.id = g.id
                                AND g.active = true AND g.deleted = false AND g.lab.id = l.id
                                AND (g.project.id = p.id OR p.group.id = g.id)
                                AND (g.project IS NULL OR g.project.id = p.id)
                                AND (p.group IS NULL OR p.group.id = g.id)))))
            """)
    Optional<AiResearchContext.Project> findAiContextProject(@Param("actorId") Long actorId,
                                                               @Param("labId") Long labId,
                                                               @Param("projectId") Long projectId,
                                                               @Param("selectedRoleName") String selectedRoleName);
}
