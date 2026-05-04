package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.common.enums.ResearchStatus;
import com.web.labportalbackend.research.entity.ResearchProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResearchProjectRepository extends JpaRepository<ResearchProject, Long> {

    Optional<ResearchProject> findByProjectName(String projectName);

    List<ResearchProject> findByLabId(Long labId);

    List<ResearchProject> findByLeaderId(Long leaderId);

    List<ResearchProject> findByStatus(ResearchStatus status);

    List<ResearchProject> findByDomain(String domain);

    boolean existsByProjectName(String projectName);
}
