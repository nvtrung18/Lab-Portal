package com.web.labportalbackend.face.repository;

import com.web.labportalbackend.face.entity.FaceSecurityConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaceSecurityConfigRepository extends JpaRepository<FaceSecurityConfigEntity, Long> {
    Optional<FaceSecurityConfigEntity> findByConfigKeyAndActiveTrueAndDeletedFalse(String configKey);
}
