package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskEntity;
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
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    @EntityGraph(attributePaths = "assignedToStudent")
    List<TaskEntity> findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(Long milestoneId);

    @EntityGraph(attributePaths = "assignedToStudent")
    List<TaskEntity> findByMilestoneIdAndAssigneeIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(
            Long milestoneId,
            Long assigneeId
    );

    boolean existsByMilestoneIdAndAssigneeIdAndDeletedFalseAndActiveTrue(Long milestoneId, Long assigneeId);

    @Query("""
            SELECT COUNT(t) > 0
            FROM TaskEntity t
            WHERE t.milestoneId = :milestoneId
              AND t.deleted = false
              AND t.active = true
              AND t.status <> com.web.labportalbackend.research.enums.TaskStatus.DONE
            """)
    boolean existsIncompleteTaskByMilestoneId(@Param("milestoneId") Long milestoneId);

    @Query("""
            SELECT COUNT(t) > 0
            FROM TaskEntity t
            WHERE t.milestoneId = :milestoneId
              AND t.deleted = false
              AND t.active = true
              AND NOT EXISTS (
                    SELECT r.id
                    FROM ReportEntity r
                    WHERE r.status = com.web.labportalbackend.research.enums.ReportStatus.APPROVED
                      AND r.deleted = false
                      AND r.active = true
                      AND r.taskId = t.id
                      AND NOT EXISTS (
                            SELECT newer.id
                            FROM ReportEntity newer
                            WHERE newer.submissionScope = r.submissionScope
                              AND newer.version > r.version
                              AND newer.deleted = false
                              AND newer.active = true
                      )
              )
            """)
    boolean existsTaskWithoutApprovedReportByMilestoneId(@Param("milestoneId") Long milestoneId);

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query("""
            SELECT t
            FROM TaskEntity t
            JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE m.project.id = :projectId
              AND t.deleted = false
              AND t.active = true
            ORDER BY t.deadline ASC, t.createdAt ASC
            """)
    List<TaskEntity> findBoardTasksByProjectId(@Param("projectId") Long projectId);

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query("""
            SELECT t
            FROM TaskEntity t
            JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE m.project.id = :projectId
              AND t.assigneeId = :assigneeId
              AND t.deleted = false
              AND t.active = true
            ORDER BY t.deadline ASC, t.createdAt ASC
            """)
    List<TaskEntity> findAssignedBoardTasksByProjectId(
            @Param("projectId") Long projectId,
            @Param("assigneeId") Long assigneeId
    );

    @Query("""
            SELECT COUNT(t)
            FROM TaskEntity t
            JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE m.project.id = :projectId
            """)
    long countByProjectId(@Param("projectId") Long projectId);

    @Query("""
            SELECT COUNT(t)
            FROM TaskEntity t
            JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE m.project.id = :projectId
              AND t.status = com.web.labportalbackend.research.enums.TaskStatus.DONE
            """)
    long countDoneByProjectId(@Param("projectId") Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "assignedToStudent")
    @Query("""
            SELECT t
            FROM TaskEntity t
            WHERE t.id = :id
              AND t.deleted = false
              AND t.active = true
            """)
    Optional<TaskEntity> findByIdForUpdate(@Param("id") Long id);
}
