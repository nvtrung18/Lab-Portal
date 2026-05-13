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
}
