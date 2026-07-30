package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.MilestoneEntity;
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
}
