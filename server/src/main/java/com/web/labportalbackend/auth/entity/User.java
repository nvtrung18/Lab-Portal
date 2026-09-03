package com.web.labportalbackend.auth.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * User account entity for authentication and authorization.
 * <p>
 * A user can hold multiple roles via a many-to-many relationship
 * with the {@link Role} entity (join table: {@code user_roles}).
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_google_subject", columnList = "google_subject", unique = true),
        @Index(name = "idx_user_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * Owning side of User ↔ Role many-to-many.
     * Eager-fetch roles because they are almost always needed (security checks).
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public User(String email, String username, String password, String fullName, String phone,
                UserStatus status, Set<Role> roles) {
        this.email = email;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.phone = phone;
        this.status = status;
        this.roles = roles;
    }

    // --- convenience methods ---

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public boolean hasRole(String roleName) {
        String normalizedRoleName = normalizeRoleName(roleName);
        return this.roles.stream()
                .map(Role::getName)
                .map(User::normalizeRoleName)
                .anyMatch(normalizedRoleName::equalsIgnoreCase);
    }

    private static String normalizeRoleName(String roleName) {
        if (roleName == null) {
            return "";
        }
        return roleName.startsWith("ROLE_") ? roleName.substring("ROLE_".length()) : roleName;
    }
}
