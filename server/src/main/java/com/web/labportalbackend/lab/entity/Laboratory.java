package com.web.labportalbackend.lab.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.LabStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "lab_name", unique = true, nullable = false, length = 100)
    private String labName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false)
    private Integer capacity;

    @Column(length = 100)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LabStatus status = LabStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manager_id", referencedColumnName = "id")
    private User manager;
}
