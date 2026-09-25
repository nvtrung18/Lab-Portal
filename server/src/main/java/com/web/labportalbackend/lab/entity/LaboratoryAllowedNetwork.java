package com.web.labportalbackend.lab.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An IP literal or CIDR network allowed to perform student self check-in for a laboratory.
 */
@Entity
@Table(name = "laboratory_allowed_networks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lab_allowed_network_value",
                columnNames = {"laboratory_id", "network_value", "deleted"})
}, indexes = {
        @Index(name = "idx_lab_allowed_network_lab_active", columnList = "laboratory_id, active, deleted")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LaboratoryAllowedNetwork extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    @Column(name = "network_value", nullable = false, length = 100)
    private String networkValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by", nullable = false)
    private User registeredBy;

    public void disable() {
        setActive(false);
        setDeleted(true);
    }
}
