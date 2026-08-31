package com.web.labportalbackend.face.repository;

import com.web.labportalbackend.face.entity.FaceCheckinLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceCheckinLogRepository extends JpaRepository<FaceCheckinLogEntity, Long> {
    List<FaceCheckinLogEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<FaceCheckinLogEntity> findByLabIdOrderByCreatedAtDescIdDesc(Long labId);
}
