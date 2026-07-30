package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskActivityRepository extends JpaRepository<TaskActivityEntity, Long> {

    List<TaskActivityEntity> findByTaskIdOrderByCreatedAtDescIdDesc(Long taskId);
}
