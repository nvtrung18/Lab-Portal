package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ProjectEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    @EntityGraph(attributePaths = "group")
    List<ProjectEntity> findByGroupId(Long groupId);
}
