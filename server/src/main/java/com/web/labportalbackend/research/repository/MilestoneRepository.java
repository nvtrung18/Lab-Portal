package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.ai.service.AiResearchContext;
import org.springframework.data.domain.Pageable;
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
public interface MilestoneRepository extends JpaRepository<MilestoneEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT m
            FROM MilestoneEntity m
            WHERE m.id = :id
              AND m.deleted = false
              AND m.active = true
            """)
    Optional<MilestoneEntity> findByIdForProposalSubmission(@Param("id") Long id);

    @EntityGraph(attributePaths = {"project", "createdBy", "assignedToStudent"})
    List<MilestoneEntity> findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(Long projectId);

    @EntityGraph(attributePaths = {"project", "group", "createdBy", "assignedToStudent"})
    List<MilestoneEntity> findByGroupIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(Long groupId);

    @EntityGraph(attributePaths = {"project", "project.lab", "createdBy", "assignedToStudent"})
    Optional<MilestoneEntity> findByIdAndDeletedFalseAndActiveTrue(Long milestoneId);

    /**
     * Legacy timeline query retained for older task/report tests.
     */
    @EntityGraph(attributePaths = "project")
    List<MilestoneEntity> findByProjectIdOrderByStartDateAsc(Long projectId);

    @Query("""
            SELECT new com.web.labportalbackend.ai.service.AiResearchContext$Milestone(
                m.id, m.title, m.name, m.status, m.startDate, m.endDate, m.deadline, m.progressPercent)
            FROM MilestoneEntity m JOIN m.project p JOIN p.lab l LEFT JOIN m.group g
            WHERE p.id = :projectId AND m.active = true AND m.deleted = false
              AND p.active = true AND p.deleted = false AND l.active = true AND l.deleted = false
              AND (g IS NULL OR (g.active = true AND g.deleted = false AND g.lab.id = l.id
                                 AND (g.project.id = p.id OR p.group.id = g.id)
                                 AND (g.project IS NULL OR g.project.id = p.id)
                                 AND (p.group IS NULL OR p.group.id = g.id)))
              AND (:selectedGroupId IS NULL OR g.id = :selectedGroupId)
              AND (:selectedTaskId IS NULL OR EXISTS (SELECT t.id FROM TaskEntity t JOIN GroupEntity tg ON tg.id = t.groupId
                                                      WHERE t.id = :selectedTaskId AND t.milestoneId = m.id
                                                        AND t.projectId = p.id AND t.active = true AND t.deleted = false
                                                        AND tg.active = true AND tg.deleted = false AND tg.lab.id = l.id
                                                        AND (tg.project.id = p.id OR p.group.id = tg.id)
                                                        AND (tg.project IS NULL OR tg.project.id = p.id)
                                                        AND (p.group IS NULL OR p.group.id = tg.id)))
              AND (:selectedReportId IS NULL OR EXISTS (SELECT r.id FROM ReportEntity r
                                                        JOIN GroupEntity rg ON rg.id = r.groupId
                                                        JOIN MilestoneEntity rm ON rm.id = r.milestoneId
                                                        LEFT JOIN TaskEntity rt ON rt.id = r.taskId
                                                        WHERE r.id = :selectedReportId AND r.milestoneId = m.id
                                                          AND r.projectId = p.id AND r.active = true AND r.deleted = false
                                                          AND rg.active = true AND rg.deleted = false AND rg.lab.id = l.id
                                                          AND (rg.project.id = p.id OR p.group.id = rg.id)
                                                          AND (rg.project IS NULL OR rg.project.id = p.id)
                                                          AND (p.group IS NULL OR p.group.id = rg.id)
                                                          AND rm.active = true AND rm.deleted = false AND rm.project.id = p.id
                                                          AND rm.group.id = rg.id
                                                          AND (rt IS NULL OR (rt.active = true AND rt.deleted = false
                                                                              AND rt.projectId = p.id AND rt.groupId = rg.id
                                                                              AND rt.milestoneId = rm.id))))
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND (EXISTS (SELECT gm.id FROM GroupMemberEntity gm
                                                      WHERE gm.user.id = :actorId AND gm.group.id = g.id
                                                        AND gm.active = true AND gm.deleted = false)
                   OR m.assignedToStudent.id = :actorId)))
              AND EXISTS (SELECT a.id FROM User a WHERE a.id = :actorId AND a.active = true
                          AND a.deleted = false AND a.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
            ORDER BY CASE WHEN m.deadline IS NULL THEN 1 ELSE 0 END, m.deadline ASC, m.createdAt ASC, m.id ASC
            """)
    List<AiResearchContext.Milestone> findAiContextMilestones(@Param("actorId") Long actorId,
                                                                @Param("projectId") Long projectId,
                                                                @Param("selectedGroupId") Long selectedGroupId,
                                                                @Param("selectedTaskId") Long selectedTaskId,
                                                                @Param("selectedReportId") Long selectedReportId,
                                                                Pageable pageable, @Param("selectedRoleName") String selectedRoleName);
}
