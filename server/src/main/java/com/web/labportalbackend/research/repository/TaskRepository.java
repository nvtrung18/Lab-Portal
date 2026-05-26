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
    @Query("SELECT t FROM TaskEntity t WHERE t.id = :id")
    Optional<TaskEntity> findByIdForUpdate(@Param("id") Long id);
}
