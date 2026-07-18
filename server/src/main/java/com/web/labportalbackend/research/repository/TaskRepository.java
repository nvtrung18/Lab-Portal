package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;

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

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query("""
            SELECT t
            FROM TaskEntity t
            JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE m.group.id = :groupId
              AND t.deleted = false
              AND t.active = true
            ORDER BY t.deadline ASC, t.createdAt ASC
            """)
    List<TaskEntity> findBoardTasksByGroupId(@Param("groupId") Long groupId);

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query("""
            SELECT t
            FROM TaskEntity t
            JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE m.group.id = :groupId
              AND t.assigneeId = :assigneeId
              AND t.deleted = false
              AND t.active = true
            ORDER BY t.deadline ASC, t.createdAt ASC
            """)
    List<TaskEntity> findAssignedBoardTasksByGroupId(
            @Param("groupId") Long groupId,
            @Param("assigneeId") Long assigneeId
    );

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query("""
            SELECT DISTINCT t
            FROM TaskEntity t
            WHERE t.projectId = :projectId
              AND EXISTS (SELECT m.id FROM MilestoneEntity m JOIN m.project p
                          WHERE m.id = t.milestoneId AND p.id = :projectId
                            AND p.active = true AND p.deleted = false)
              AND t.active = true
              AND t.deleted = false
              AND (t.groupId IS NULL OR t.groupId IN (
                    SELECT g.id FROM GroupEntity g LEFT JOIN g.project gp
                    WHERE g.active = true AND g.deleted = false
                      AND ((gp.id = :projectId AND gp.active = true AND gp.deleted = false AND gp.lab.id = g.lab.id)
                           OR EXISTS (SELECT p.id FROM ProjectEntity p
                                      WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                                        AND p.group.id = g.id AND p.lab.id = g.lab.id))
                  ))
              AND (:groupId IS NULL OR t.groupId = :groupId)
              AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId)
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:type IS NULL OR t.type = :type)
              AND (:includeBacklog = true OR t.status <> com.web.labportalbackend.research.enums.TaskStatus.BACKLOG)
              AND (:includeCancelled = true OR t.status <> com.web.labportalbackend.research.enums.TaskStatus.CANCELLED)
            ORDER BY CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, t.dueDate ASC, t.createdAt ASC, t.id ASC
            """)
    List<TaskEntity> findBoardTasksForManager(
            @Param("projectId") Long projectId,
            @Param("groupId") Long groupId,
            @Param("assigneeId") Long assigneeId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("type") TaskType type,
            @Param("includeBacklog") boolean includeBacklog,
            @Param("includeCancelled") boolean includeCancelled
    );

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query("""
            SELECT DISTINCT t
            FROM TaskEntity t
            WHERE t.projectId = :projectId
              AND EXISTS (SELECT m.id FROM MilestoneEntity m JOIN m.project p
                          WHERE m.id = t.milestoneId AND p.id = :projectId
                            AND p.active = true AND p.deleted = false)
              AND t.active = true
              AND t.deleted = false
              AND t.groupId IN (
                    SELECT g.id FROM GroupEntity g LEFT JOIN g.project gp
                    WHERE g.active = true AND g.deleted = false
                      AND ((gp.id = :projectId AND gp.active = true AND gp.deleted = false AND gp.lab.id = g.lab.id)
                           OR EXISTS (SELECT p.id FROM ProjectEntity p
                                      WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                                        AND p.group.id = g.id AND p.lab.id = g.lab.id))
                  )
              AND ((t.groupId IN :leaderGroupIds)
                   OR (t.groupId IN :memberGroupIds AND t.assigneeId = :currentUserId))
              AND (:groupId IS NULL OR t.groupId = :groupId)
              AND (:assigneeId IS NULL OR t.assigneeId = :assigneeId)
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:type IS NULL OR t.type = :type)
              AND (:includeBacklog = true OR t.status <> com.web.labportalbackend.research.enums.TaskStatus.BACKLOG)
              AND (:includeCancelled = true OR t.status <> com.web.labportalbackend.research.enums.TaskStatus.CANCELLED)
            ORDER BY CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, t.dueDate ASC, t.createdAt ASC, t.id ASC
            """)
    List<TaskEntity> findBoardTasksForStudent(
            @Param("projectId") Long projectId,
            @Param("leaderGroupIds") List<Long> leaderGroupIds,
            @Param("memberGroupIds") List<Long> memberGroupIds,
            @Param("currentUserId") Long currentUserId,
            @Param("groupId") Long groupId,
            @Param("assigneeId") Long assigneeId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("type") TaskType type,
            @Param("includeBacklog") boolean includeBacklog,
            @Param("includeCancelled") boolean includeCancelled
    );

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query(value = """
            SELECT DISTINCT t
            FROM TaskEntity t
            WHERE t.projectId = :projectId
              AND t.status = com.web.labportalbackend.research.enums.TaskStatus.BACKLOG
              AND EXISTS (SELECT m.id FROM MilestoneEntity m JOIN m.project p
                          WHERE m.id = t.milestoneId AND p.id = :projectId
                            AND p.active = true AND p.deleted = false)
              AND t.active = true
              AND t.deleted = false
              AND (t.groupId IS NULL OR t.groupId IN (
                    SELECT g.id FROM GroupEntity g LEFT JOIN g.project gp
                    WHERE g.active = true AND g.deleted = false
                      AND ((gp.id = :projectId AND gp.active = true AND gp.deleted = false AND gp.lab.id = g.lab.id)
                           OR EXISTS (SELECT p.id FROM ProjectEntity p
                                      WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                                        AND p.group.id = g.id AND p.lab.id = g.lab.id))
                  ))
            ORDER BY CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, t.dueDate ASC, t.createdAt ASC, t.id ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT t.id)
            FROM TaskEntity t
            WHERE t.projectId = :projectId
              AND t.status = com.web.labportalbackend.research.enums.TaskStatus.BACKLOG
              AND EXISTS (SELECT m.id FROM MilestoneEntity m JOIN m.project p
                          WHERE m.id = t.milestoneId AND p.id = :projectId
                            AND p.active = true AND p.deleted = false)
              AND t.active = true
              AND t.deleted = false
              AND (t.groupId IS NULL OR t.groupId IN (
                    SELECT g.id FROM GroupEntity g LEFT JOIN g.project gp
                    WHERE g.active = true AND g.deleted = false
                      AND ((gp.id = :projectId AND gp.active = true AND gp.deleted = false AND gp.lab.id = g.lab.id)
                           OR EXISTS (SELECT p.id FROM ProjectEntity p
                                      WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                                        AND p.group.id = g.id AND p.lab.id = g.lab.id))
                  ))
            """)
    Page<TaskEntity> findBacklogTasksForManager(
            @Param("projectId") Long projectId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "assignedToStudent")
    @Query(value = """
            SELECT DISTINCT t
            FROM TaskEntity t
            WHERE t.projectId = :projectId
              AND t.status = com.web.labportalbackend.research.enums.TaskStatus.BACKLOG
              AND EXISTS (SELECT m.id FROM MilestoneEntity m JOIN m.project p
                          WHERE m.id = t.milestoneId AND p.id = :projectId
                            AND p.active = true AND p.deleted = false)
              AND t.active = true
              AND t.deleted = false
              AND t.groupId IN (
                    SELECT g.id FROM GroupEntity g LEFT JOIN g.project gp
                    WHERE g.active = true AND g.deleted = false
                      AND ((gp.id = :projectId AND gp.active = true AND gp.deleted = false AND gp.lab.id = g.lab.id)
                           OR EXISTS (SELECT p.id FROM ProjectEntity p
                                      WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                                        AND p.group.id = g.id AND p.lab.id = g.lab.id))
                  )
              AND (t.groupId IN :leaderGroupIds
                   OR (t.groupId IN :memberGroupIds AND t.assigneeId = :currentUserId))
            ORDER BY CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, t.dueDate ASC, t.createdAt ASC, t.id ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT t.id)
            FROM TaskEntity t
            WHERE t.projectId = :projectId
              AND t.status = com.web.labportalbackend.research.enums.TaskStatus.BACKLOG
              AND EXISTS (SELECT m.id FROM MilestoneEntity m JOIN m.project p
                          WHERE m.id = t.milestoneId AND p.id = :projectId
                            AND p.active = true AND p.deleted = false)
              AND t.active = true
              AND t.deleted = false
              AND t.groupId IN (
                    SELECT g.id FROM GroupEntity g LEFT JOIN g.project gp
                    WHERE g.active = true AND g.deleted = false
                      AND ((gp.id = :projectId AND gp.active = true AND gp.deleted = false AND gp.lab.id = g.lab.id)
                           OR EXISTS (SELECT p.id FROM ProjectEntity p
                                      WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                                        AND p.group.id = g.id AND p.lab.id = g.lab.id))
                  )
              AND (t.groupId IN :leaderGroupIds
                   OR (t.groupId IN :memberGroupIds AND t.assigneeId = :currentUserId))
            """)
    Page<TaskEntity> findBacklogTasksForStudent(
            @Param("projectId") Long projectId,
            @Param("leaderGroupIds") List<Long> leaderGroupIds,
            @Param("memberGroupIds") List<Long> memberGroupIds,
            @Param("currentUserId") Long currentUserId,
            Pageable pageable
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

    @Query("""
            SELECT COUNT(t)
            FROM TaskEntity t
            WHERE t.milestoneId = :milestoneId
              AND t.assigneeId = :assigneeId
              AND t.deleted = false
              AND t.active = true
            """)
    int countByMilestoneIdAndAssigneeId(
            @Param("milestoneId") Long milestoneId,
            @Param("assigneeId") Long assigneeId
    );

    @Query("""
            SELECT COUNT(t)
            FROM TaskEntity t
            WHERE t.milestoneId = :milestoneId
              AND t.assigneeId = :assigneeId
              AND t.status = com.web.labportalbackend.research.enums.TaskStatus.DONE
              AND t.deleted = false
              AND t.active = true
            """)
    int countDoneByMilestoneIdAndAssigneeId(
            @Param("milestoneId") Long milestoneId,
            @Param("assigneeId") Long assigneeId
    );

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
