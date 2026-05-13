package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    @EntityGraph(attributePaths = {"lab", "leader", "members", "members.user"})
    List<GroupEntity> findByLabId(Long labId);
}
