package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskCommentEntity, Long> {
}
