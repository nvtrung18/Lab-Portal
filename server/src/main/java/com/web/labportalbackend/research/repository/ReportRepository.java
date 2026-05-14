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

    @Query("SELECT MAX(r.version) FROM ReportEntity r WHERE r.taskId = :taskId")
    Optional<Integer> findMaxVersionByTaskId(@Param("taskId") Long taskId);

    List<ReportEntity> findByTaskIdOrderByVersionDesc(Long taskId);

    @Query("""
            SELECT COUNT(r)
            FROM ReportEntity r
            JOIN TaskEntity t ON t.id = r.taskId
            JOIN MilestoneEntity m ON m.id = t.milestoneId
            WHERE m.project.id = :projectId
            """)
    long countByProjectId(@Param("projectId") Long projectId);
}
