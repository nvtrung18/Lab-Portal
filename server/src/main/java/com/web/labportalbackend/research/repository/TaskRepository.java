package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.ai.service.AiResearchContext;
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

    @Query("""
            SELECT DISTINCT new com.web.labportalbackend.ai.service.AiResearchToolCandidateResource(
                t.id, t.title, t.projectId)
            FROM TaskEntity t JOIN ProjectEntity p ON p.id = t.projectId JOIN p.lab l
                 JOIN GroupEntity g ON g.id = t.groupId
            WHERE t.active = true AND t.deleted = false
              AND p.active = true AND p.deleted = false
              AND g.active = true AND g.deleted = false
              AND l.active = true AND l.deleted = false AND g.lab.id = l.id
              AND (g.project.id = p.id OR p.group.id = g.id)
              AND (g.project IS NULL OR g.project.id = p.id)
              AND (p.group IS NULL OR p.group.id = g.id)
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND (t.assigneeId = :actorId OR EXISTS (
                       SELECT gm.id FROM GroupMemberEntity gm
                       WHERE gm.group.id = g.id AND gm.user.id = :actorId
                         AND gm.active = true AND gm.deleted = false))))
            ORDER BY t.id ASC
            """)
    List<com.web.labportalbackend.ai.service.AiResearchToolCandidateResource> findAiToolCandidateTasks(
            @Param("actorId") Long actorId,
            @Param("selectedRoleName") String selectedRoleName,
            Pageable pageable);
    Optional<TaskEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT t
            FROM TaskEntity t
            WHERE t.id = :id
              AND t.deleted = false
              AND t.active = true
            """)
    Optional<TaskEntity> findByIdForProposalSubmission(@Param("id") Long id);

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
              AND EXISTS (SELECT p.id FROM ProjectEntity p
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false)
              AND (t.milestoneId IS NULL OR EXISTS (
                    SELECT m.id FROM MilestoneEntity m JOIN m.project p
                    WHERE m.id = t.milestoneId AND p.id = :projectId
                      AND m.active = true AND m.deleted = false
                      AND p.active = true AND p.deleted = false))
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
              AND EXISTS (SELECT p.id FROM ProjectEntity p
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false)
              AND (t.milestoneId IS NULL OR EXISTS (
                    SELECT m.id FROM MilestoneEntity m JOIN m.project p
                    WHERE m.id = t.milestoneId AND p.id = :projectId
                      AND m.active = true AND m.deleted = false
                      AND p.active = true AND p.deleted = false))
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
              AND EXISTS (SELECT p.id FROM ProjectEntity p
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false)
              AND (t.milestoneId IS NULL OR EXISTS (
                    SELECT m.id FROM MilestoneEntity m JOIN m.project p
                    WHERE m.id = t.milestoneId AND p.id = :projectId
                      AND m.active = true AND m.deleted = false
                      AND p.active = true AND p.deleted = false))
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
              AND EXISTS (SELECT p.id FROM ProjectEntity p
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false)
              AND (t.milestoneId IS NULL OR EXISTS (
                    SELECT m.id FROM MilestoneEntity m JOIN m.project p
                    WHERE m.id = t.milestoneId AND p.id = :projectId
                      AND m.active = true AND m.deleted = false
                      AND p.active = true AND p.deleted = false))
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
              AND EXISTS (SELECT p.id FROM ProjectEntity p
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false)
              AND (t.milestoneId IS NULL OR EXISTS (
                    SELECT m.id FROM MilestoneEntity m JOIN m.project p
                    WHERE m.id = t.milestoneId AND p.id = :projectId
                      AND m.active = true AND m.deleted = false
                      AND p.active = true AND p.deleted = false))
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
              AND EXISTS (SELECT p.id FROM ProjectEntity p
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false)
              AND (t.milestoneId IS NULL OR EXISTS (
                    SELECT m.id FROM MilestoneEntity m JOIN m.project p
                    WHERE m.id = t.milestoneId AND p.id = :projectId
                      AND m.active = true AND m.deleted = false
                      AND p.active = true AND p.deleted = false))
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

    @Query("""
            SELECT new com.web.labportalbackend.ai.service.AiResearchContext$Task(
                t.id, t.title, t.status, t.priority, t.type, t.dueDate, t.deadline, t.progressPercent,
                t.blockedReason,
                CASE WHEN t.deadline IS NOT NULL AND t.deadline < CURRENT_DATE
                          AND t.status NOT IN (com.web.labportalbackend.research.enums.TaskStatus.DONE,
                                               com.web.labportalbackend.research.enums.TaskStatus.CANCELLED)
                     THEN true ELSE false END)
            FROM TaskEntity t JOIN ProjectEntity p ON p.id = t.projectId JOIN p.lab l
            JOIN GroupEntity g ON g.id = t.groupId JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE t.projectId = :projectId AND t.active = true AND t.deleted = false
              AND p.active = true AND p.deleted = false AND l.active = true AND l.deleted = false
              AND g.active = true AND g.deleted = false AND g.lab.id = l.id
              AND (g.project.id = p.id OR p.group.id = g.id)
              AND (g.project IS NULL OR g.project.id = p.id)
              AND (p.group IS NULL OR p.group.id = g.id)
              AND m.active = true AND m.deleted = false AND m.project.id = p.id AND m.group.id = g.id
              AND (:selectedGroupId IS NULL OR g.id = :selectedGroupId)
              AND (:selectedTaskId IS NULL OR t.id = :selectedTaskId)
              AND (:selectedReportId IS NULL OR EXISTS (SELECT r.id FROM ReportEntity r
                                                        JOIN GroupEntity rg ON rg.id = r.groupId
                                                        JOIN MilestoneEntity rm ON rm.id = r.milestoneId
                                                        WHERE r.id = :selectedReportId AND r.taskId = t.id
                                                          AND r.projectId = p.id AND r.active = true AND r.deleted = false
                                                          AND rg.active = true AND rg.deleted = false AND rg.lab.id = l.id
                                                          AND (rg.project.id = p.id OR p.group.id = rg.id)
                                                          AND (rg.project IS NULL OR rg.project.id = p.id)
                                                          AND (p.group IS NULL OR p.group.id = rg.id)
                                                          AND rm.active = true AND rm.deleted = false AND rm.project.id = p.id
                                                          AND rm.group.id = rg.id
                                                          AND t.milestoneId = rm.id AND t.groupId = rg.id))
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND (t.assigneeId = :actorId
                   OR EXISTS (SELECT gm.id FROM GroupMemberEntity gm WHERE gm.group.id = g.id
                              AND gm.user.id = :actorId AND gm.active = true AND gm.deleted = false
                              AND (gm.role = com.web.labportalbackend.research.enums.GroupRole.LEADER
                                   OR gm.role = com.web.labportalbackend.research.enums.GroupRole.MEMBER)))))
              AND EXISTS (SELECT a.id FROM User a WHERE a.id = :actorId AND a.active = true
                          AND a.deleted = false AND a.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
            ORDER BY CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END, t.dueDate ASC, t.createdAt ASC, t.id ASC
            """)
    List<AiResearchContext.Task> findAiContextTasks(@Param("actorId") Long actorId,
                                                     @Param("projectId") Long projectId,
                                                     @Param("selectedGroupId") Long selectedGroupId,
                                                     @Param("selectedTaskId") Long selectedTaskId,
                                                     @Param("selectedReportId") Long selectedReportId,
                                                     org.springframework.data.domain.Pageable pageable,
                                                     @Param("selectedRoleName") String selectedRoleName);

    @Query("""
            SELECT COUNT(t) > 0 FROM TaskEntity t JOIN ProjectEntity p ON p.id = t.projectId
            JOIN p.lab l JOIN GroupEntity g ON g.id = t.groupId JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE t.id = :taskId AND t.projectId = :projectId AND t.active = true AND t.deleted = false
              AND p.active = true AND p.deleted = false AND l.active = true AND l.deleted = false
              AND g.active = true AND g.deleted = false AND g.lab.id = l.id
              AND (g.project.id = p.id OR p.group.id = g.id)
              AND (g.project IS NULL OR g.project.id = p.id)
              AND (p.group IS NULL OR p.group.id = g.id)
              AND m.active = true AND m.deleted = false AND m.project.id = p.id AND m.group.id = g.id
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND (t.assigneeId = :actorId
                   OR EXISTS (SELECT gm.id FROM GroupMemberEntity gm WHERE gm.group.id = g.id
                              AND gm.user.id = :actorId AND gm.active = true AND gm.deleted = false
                              AND (gm.role = com.web.labportalbackend.research.enums.GroupRole.LEADER
                                   OR gm.role = com.web.labportalbackend.research.enums.GroupRole.MEMBER)))))
              AND EXISTS (SELECT a.id FROM User a WHERE a.id = :actorId AND a.active = true
                          AND a.deleted = false AND a.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
            """)
    boolean existsAiContextTask(@Param("actorId") Long actorId, @Param("projectId") Long projectId,
                                @Param("taskId") Long taskId, @Param("selectedRoleName") String selectedRoleName);
}
