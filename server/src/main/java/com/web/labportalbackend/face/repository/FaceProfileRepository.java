package com.web.labportalbackend.face.repository;

import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceProfileRepository extends JpaRepository<FaceProfileEntity, Long> {
    Optional<FaceProfileEntity> findByUserIdAndDeletedFalse(Long userId);
    Optional<FaceProfileEntity> findByUserIdAndProfileStatusAndActiveTrueAndDeletedFalse(
            Long userId, FaceProfileStatus profileStatus);
}
