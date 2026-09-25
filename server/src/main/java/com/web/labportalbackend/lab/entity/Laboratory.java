package com.web.labportalbackend.lab.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.LabStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Represents a physical laboratory in the system.
 * A laboratory can have a manager (User entity) assigned to it.
 */
@Entity
@Table(name = "laboratories", indexes = {
        @Index(name = "idx_lab_name", columnList = "labName", unique = true),
        @Index(name = "idx_lab_department", columnList = "department"),
        @Index(name = "idx_lab_status", columnList = "status"),
        @Index(name = "idx_lab_manager", columnList = "managerId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Laboratory extends BaseEntity {

    /**
     * Compatibility constructor for existing lab creation and persistence tests.
     * Self check-in policy fields intentionally start with their safe defaults.
     */
    public Laboratory(String labName, String description, String location, Integer capacity,
                      String department, LabStatus status, User manager) {
        this.labName = labName;
        this.description = description;
        this.location = location;
        this.capacity = capacity;
        this.department = department;
        this.status = status;
        this.manager = manager;
    }

    @Column(name = "lab_name", unique = true, nullable = false, length = 100)
    private String labName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "checkin_latitude", precision = 10, scale = 7)
    private BigDecimal checkinLatitude;

    @Column(name = "checkin_longitude", precision = 10, scale = 7)
    private BigDecimal checkinLongitude;

    @Column(name = "checkin_radius_meters")
    private Integer checkinRadiusMeters;

    @Column(name = "checkin_max_accuracy_meters")
    private Integer checkinMaxAccuracyMeters;

    @Column(name = "checkin_enabled", nullable = false)
    private Boolean checkinEnabled = false;

    @Column(name = "cleaning_rotation_round", nullable = false)
    private Integer cleaningRotationRound = 1;

    @Column(length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LabStatus status = LabStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manager_id", referencedColumnName = "id")
    private User manager;
}
