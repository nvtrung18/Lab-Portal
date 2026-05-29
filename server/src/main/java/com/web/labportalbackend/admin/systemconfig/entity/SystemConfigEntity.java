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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "system_configs",
        indexes = {
                @Index(name = "idx_system_configs_key_deleted", columnList = "config_key, deleted")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_configs_key", columnNames = "config_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class SystemConfigEntity extends BaseEntity {

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value_json", nullable = false, columnDefinition = "json")
    private String configValueJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;
}
