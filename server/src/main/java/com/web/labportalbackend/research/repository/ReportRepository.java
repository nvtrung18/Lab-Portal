package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

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

    List<ReportEntity> findByMilestoneIdOrderByCreatedAtDescVersionDesc(Long milestoneId);

    List<ReportEntity> findByMilestoneIdAndSubmittedByIdOrderByCreatedAtDescVersionDesc(
            Long milestoneId,
            Long submittedById
    );

    List<ReportEntity> findByGroupIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDescVersionDesc(Long groupId);

    @Query("""
            SELECT r
            FROM ReportEntity r
            JOIN MilestoneEntity m ON m.id = r.milestoneId
            WHERE m.project.lab.id = :labId
              AND r.status = com.web.labportalbackend.research.enums.ReportStatus.LEADER_REVIEWED
              AND r.deleted = false
              AND r.active = true
            ORDER BY r.createdAt ASC
            """)
    List<ReportEntity> findPendingManagerReviewByLabId(@Param("labId") Long labId);

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
}
