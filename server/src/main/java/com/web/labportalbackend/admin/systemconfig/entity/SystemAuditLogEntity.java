package com.web.labportalbackend.admin.systemconfig.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_audit_logs", indexes = {
        @Index(name = "idx_system_audit_logs_module_action", columnList = "module, action"),
        @Index(name = "idx_system_audit_logs_actor", columnList = "actor_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SystemAuditLogEntity extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String action;

    @Column(nullable = false, length = 100)
    private String module;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;
}
