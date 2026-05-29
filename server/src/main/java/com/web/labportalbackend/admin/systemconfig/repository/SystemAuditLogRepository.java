package com.web.labportalbackend.admin.systemconfig.repository;

import com.web.labportalbackend.admin.systemconfig.entity.SystemAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLogEntity, Long> {
}
