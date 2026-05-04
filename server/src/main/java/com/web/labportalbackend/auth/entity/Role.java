package com.web.labportalbackend.auth.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a security role in the system.
 * <p>
 * Roles are stored as DB records (not an enum column) so that:
 * <ul>
 *   <li>New roles can be added without code changes or redeployment</li>
 *   <li>Roles can carry metadata (description, permissions) in the future</li>
 *   <li>Role assignment is enforced by foreign-key constraints</li>
 * </ul>
 */
@Entity
@Table(name = "roles", indexes = {
        @Index(name = "idx_role_name", columnList = "name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    /**
     * Inverse side of User ↔ Role many-to-many.
     * Mapped by the "roles" field in User entity.
     */
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();

    // --- convenience constructor ---

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
