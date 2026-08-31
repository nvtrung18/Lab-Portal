package com.web.labportalbackend.face.repository;

import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceConsentLogRepository extends JpaRepository<FaceConsentLogEntity, Long> {
    Optional<FaceConsentLogEntity> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
