package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, Long> {
    List<CommentEntity> findByReportIdOrderByCreatedAtAsc(Long reportId);
}
