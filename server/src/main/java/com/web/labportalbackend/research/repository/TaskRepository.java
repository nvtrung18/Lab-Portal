package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findByMilestoneIdOrderByCreatedAtAsc(Long milestoneId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TaskEntity t WHERE t.id = :id")
    Optional<TaskEntity> findByIdForUpdate(@Param("id") Long id);
}
