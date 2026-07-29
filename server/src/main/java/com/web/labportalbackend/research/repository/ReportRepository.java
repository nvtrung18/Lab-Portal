package com.web.labportalbackend.research.repository;

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
}
