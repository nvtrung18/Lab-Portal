package com.web.labportalbackend.admin.systemconfig.repository;

import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, Long> {

    Optional<SystemConfigEntity> findByConfigKeyAndDeletedFalse(String configKey);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT config
            FROM SystemConfigEntity config
            WHERE config.configKey = :configKey
              AND config.deleted = false
            """)
    Optional<SystemConfigEntity> findByConfigKeyForStatusAuthorization(
            @Param("configKey") String configKey);
}
