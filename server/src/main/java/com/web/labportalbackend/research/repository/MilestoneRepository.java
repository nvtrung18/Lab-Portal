package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.MilestoneEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilestoneRepository extends JpaRepository<MilestoneEntity, Long> {

    @EntityGraph(attributePaths = "project")
    List<MilestoneEntity> findByProjectIdOrderByStartDateAsc(Long projectId);
}
