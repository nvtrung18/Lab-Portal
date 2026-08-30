package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.ai.context.AiResearchReportContext;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT r
            FROM ReportEntity r
            WHERE r.taskId = :taskId
              AND r.status = com.web.labportalbackend.research.enums.ReportStatus.APPROVED
              AND r.deleted = false
              AND r.active = true
              AND NOT EXISTS (
                    SELECT newer.id
                    FROM ReportEntity newer
                    WHERE newer.submissionScope = r.submissionScope
                      AND newer.version > r.version
                      AND newer.deleted = false
                      AND newer.active = true
              )
            """)
    List<ReportEntity> findLatestApprovedForStatusAuthorization(@Param("taskId") Long taskId);

    @Query("""
            SELECT MAX(r.version)
            FROM ReportEntity r
            WHERE r.taskId = :taskId
              AND r.submittedById = :submittedById
              AND r.deleted = false
              AND r.active = true
            """)
    Optional<Integer> findMaxVersionByTaskIdAndSubmittedById(
            @Param("taskId") Long taskId,
            @Param("submittedById") Long submittedById
    );

    List<ReportEntity> findByTaskIdOrderByVersionDesc(Long taskId);

    Optional<ReportEntity> findTopByTaskIdAndSubmittedByIdAndDeletedFalseAndActiveTrueOrderByVersionDescCreatedAtDesc(
            Long taskId,
            Long submittedById
    );

    List<ReportEntity> findByMilestoneIdOrderByCreatedAtDescVersionDesc(Long milestoneId);

    List<ReportEntity> findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(
            Long milestoneId,
            Long submittedById
    );

    List<ReportEntity> findByGroupIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDescVersionDesc(Long groupId);

    List<ReportEntity> findByGroupIdAndSubmittedByIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDescVersionDesc(Long groupId, Long submittedById);

    @Query("""
            SELECT r
            FROM ReportEntity r
            JOIN MilestoneEntity m ON m.id = r.milestoneId
            LEFT JOIN TaskEntity t ON t.id = r.taskId
            WHERE m.group.id = :groupId
              AND r.deleted = false
              AND r.active = true
              AND m.deleted = false
              AND m.active = true
              AND (
                    r.taskId IS NULL
                    OR (
                        t.deleted = false
                        AND t.active = true
                        AND t.milestoneId = m.id
                    )
              )
            ORDER BY r.createdAt DESC, r.version DESC
            """)
    List<ReportEntity> findReportsByGroupScope(@Param("groupId") Long groupId);

    @Query("""
            SELECT r
            FROM ReportEntity r
            JOIN MilestoneEntity m ON m.id = r.milestoneId
            LEFT JOIN TaskEntity t ON t.id = r.taskId
            WHERE m.group.id = :groupId
              AND r.submittedById = :submittedById
              AND r.deleted = false
              AND r.active = true
              AND m.deleted = false
              AND m.active = true
              AND (
                    r.taskId IS NULL
                    OR (
                        t.deleted = false
                        AND t.active = true
                        AND t.milestoneId = m.id
                    )
              )
            ORDER BY r.createdAt DESC, r.version DESC
            """)
    List<ReportEntity> findOwnReportsByGroupScope(
            @Param("groupId") Long groupId,
            @Param("submittedById") Long submittedById
    );

    @Query("""
            SELECT r
            FROM ReportEntity r
            JOIN MilestoneEntity m ON m.id = r.milestoneId
            WHERE m.project.lab.id = :labId
              AND (r.status = com.web.labportalbackend.research.enums.ReportStatus.LEADER_REVIEWED
                   OR (:requireLeaderReview = false AND r.status = com.web.labportalbackend.research.enums.ReportStatus.SUBMITTED))
              AND r.deleted = false
              AND r.active = true
            ORDER BY r.createdAt ASC
            """)
    List<ReportEntity> findPendingManagerReviewByLabId(
            @Param("labId") Long labId,
            @Param("requireLeaderReview") boolean requireLeaderReview
    );

    boolean existsBySubmissionScopeAndVersionGreaterThan(String submissionScope, Integer version);

    @Query("""
            SELECT COUNT(r) > 0
            FROM ReportEntity r
            WHERE r.milestoneId = :milestoneId
              AND r.status = com.web.labportalbackend.research.enums.ReportStatus.APPROVED
              AND r.deleted = false
              AND r.active = true
            """)
    boolean existsApprovedByMilestoneId(@Param("milestoneId") Long milestoneId);

    @Query("""
            SELECT COUNT(r) > 0
            FROM ReportEntity r
            WHERE r.taskId = :taskId
              AND r.status = com.web.labportalbackend.research.enums.ReportStatus.APPROVED
              AND r.deleted = false
              AND r.active = true
              AND NOT EXISTS (
                    SELECT newer.id
                    FROM ReportEntity newer
                    WHERE newer.submissionScope = r.submissionScope
                      AND newer.version > r.version
                      AND newer.deleted = false
                      AND newer.active = true
              )
            """)
    boolean existsLatestApprovedByTaskId(@Param("taskId") Long taskId);

    @Query("""
            SELECT COUNT(r) > 0
            FROM ReportEntity r
            WHERE r.milestoneId = :milestoneId
              AND r.deleted = false
              AND r.active = true
              AND r.status <> com.web.labportalbackend.research.enums.ReportStatus.APPROVED
              AND NOT EXISTS (
                    SELECT newer.id
                    FROM ReportEntity newer
                    WHERE newer.submissionScope = r.submissionScope
                      AND newer.version > r.version
                      AND newer.deleted = false
                      AND newer.active = true
              )
            """)
    boolean existsLatestUnapprovedByMilestoneId(@Param("milestoneId") Long milestoneId);

    @Query("""
            SELECT COUNT(r)
            FROM ReportEntity r
            JOIN MilestoneEntity m ON m.id = r.milestoneId
            WHERE m.project.id = :projectId
            """)
    long countByProjectId(@Param("projectId") Long projectId);

    @Query("""
            SELECT COUNT(r)
            FROM ReportEntity r
            WHERE r.status IN :statuses
              AND r.deleted = false
              AND r.active = true
            """)
    long countActiveByStatusIn(@Param("statuses") List<ReportStatus> statuses);

    @Query("""
            SELECT r
            FROM ReportEntity r
            WHERE r.taskId IN :taskIds
              AND r.deleted = false
              AND r.active = true
            """)
    List<ReportEntity> findActiveReportsByTaskIds(@Param("taskIds") List<Long> taskIds);

    @Query("""
            SELECT COUNT(r) > 0 FROM ReportEntity r JOIN ProjectEntity p ON p.id = r.projectId
            JOIN p.lab l JOIN GroupEntity g ON g.id = r.groupId JOIN MilestoneEntity m ON m.id = r.milestoneId
            LEFT JOIN TaskEntity t ON t.id = r.taskId
            WHERE r.id = :reportId AND r.projectId = :projectId AND r.active = true AND r.deleted = false
              AND p.active = true AND p.deleted = false AND l.active = true AND l.deleted = false
              AND g.active = true AND g.deleted = false AND g.lab.id = l.id
              AND (g.project.id = p.id OR p.group.id = g.id)
              AND (g.project IS NULL OR g.project.id = p.id)
              AND (p.group IS NULL OR p.group.id = g.id)
              AND m.active = true AND m.deleted = false AND m.project.id = p.id
              AND m.group.id = g.id
              AND (t IS NULL OR (t.active = true AND t.deleted = false AND t.projectId = p.id
                                AND t.groupId = g.id AND t.milestoneId = m.id))
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND EXISTS (SELECT gm.id FROM GroupMemberEntity gm
                                                      WHERE gm.group.id = g.id AND gm.user.id = :actorId
                                                        AND gm.active = true AND gm.deleted = false
                                                        AND gm.role = com.web.labportalbackend.research.enums.GroupRole.LEADER
                                                        AND r.submittedById <> :actorId)))
              AND EXISTS (SELECT a.id FROM User a WHERE a.id = :actorId AND a.active = true
                          AND a.deleted = false AND a.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
            """)
    boolean existsAiContextReport(@Param("actorId") Long actorId, @Param("projectId") Long projectId,
                                  @Param("reportId") Long reportId, @Param("selectedRoleName") String selectedRoleName);

    @Query("""
            SELECT new com.web.labportalbackend.ai.context.AiResearchReportContext(
                r.id, r.projectId, r.groupId, r.milestoneId, r.taskId, r.version, r.title,
                r.contentDone, r.result, r.difficulty, r.nextPlan, r.selfAssessment, r.evidenceLink, r.status)
            FROM ReportEntity r JOIN ProjectEntity p ON p.id = r.projectId
            JOIN p.lab l JOIN GroupEntity g ON g.id = r.groupId JOIN MilestoneEntity m ON m.id = r.milestoneId
            LEFT JOIN TaskEntity t ON t.id = r.taskId
            WHERE r.id = :reportId AND r.projectId = :projectId AND r.active = true AND r.deleted = false
              AND p.active = true AND p.deleted = false AND l.active = true AND l.deleted = false
              AND g.active = true AND g.deleted = false AND g.lab.id = l.id
              AND (g.project.id = p.id OR p.group.id = g.id)
              AND (g.project IS NULL OR g.project.id = p.id)
              AND (p.group IS NULL OR p.group.id = g.id)
              AND m.active = true AND m.deleted = false AND m.project.id = p.id AND m.group.id = g.id
              AND (t IS NULL OR (t.active = true AND t.deleted = false AND t.projectId = p.id
                                AND t.groupId = g.id AND t.milestoneId = m.id))
              AND EXISTS (SELECT role.id FROM User roleActor JOIN roleActor.roles role
                          WHERE roleActor.id = :actorId AND role.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND EXISTS (SELECT gm.id FROM GroupMemberEntity gm
                                                      WHERE gm.group.id = g.id AND gm.user.id = :actorId
                                                        AND gm.active = true AND gm.deleted = false
                                                        AND gm.role = com.web.labportalbackend.research.enums.GroupRole.LEADER
                                                        AND r.submittedById <> :actorId)))
              AND EXISTS (SELECT actor.id FROM User actor WHERE actor.id = :actorId AND actor.active = true
                          AND actor.deleted = false AND actor.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
            """)
    Optional<AiResearchReportContext> findAiContextReport(@Param("actorId") Long actorId,
                                                          @Param("projectId") Long projectId,
                                                          @Param("reportId") Long reportId,
                                                          @Param("selectedRoleName") String selectedRoleName);
}
