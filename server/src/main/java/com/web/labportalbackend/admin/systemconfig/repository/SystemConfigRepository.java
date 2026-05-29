package com.web.labportalbackend.admin.systemconfig.repository;

import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, Long> {

    Optional<SystemConfigEntity> findByConfigKeyAndDeletedFalse(String configKey);
}
