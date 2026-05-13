package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
}
