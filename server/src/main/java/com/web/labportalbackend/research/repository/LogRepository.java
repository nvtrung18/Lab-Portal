package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.LogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<LogEntity, Long> {
    List<LogEntity> findAllByOrderByCreatedAtDesc();

    List<LogEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
