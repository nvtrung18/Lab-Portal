package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    @EntityGraph(attributePaths = {"lab", "topic", "leader", "members", "members.user"})
    List<GroupEntity> findByLabIdAndDeletedFalseAndActiveTrue(Long labId);

    @EntityGraph(attributePaths = {"lab", "topic", "leader", "members", "members.user"})
    List<GroupEntity> findByTopicIdAndDeletedFalseAndActiveTrue(Long topicId);

    @EntityGraph(attributePaths = {"lab", "topic", "leader", "members", "members.user"})
    Optional<GroupEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);

    long countByTopicIdAndDeletedFalseAndActiveTrue(Long topicId);
}
