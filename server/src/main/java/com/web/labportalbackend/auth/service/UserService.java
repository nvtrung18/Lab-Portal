package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.auth.dto.UpdateProfileRequest;
import com.web.labportalbackend.auth.dto.UserProfileDTO;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * User profile management service.
 * Handles reading and updating user profile information.
 * Uses SecurityContext to retrieve the currently authenticated user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Get the currently authenticated user's profile from SecurityContext.
     *
     * @return User profile information as UserProfileDTO
     * @throws EntityNotFoundException if the current user is not found
     */
    public UserProfileDTO getCurrentUser() {
        String username = getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found: " + username));
        
        return mapToUserProfileDTO(user);
    }

    /**
     * Update the currently authenticated user's profile.
     * Only allows updates to fullName and phone.
     *
     * @param updateRequest Request containing fullName and phone
     * @return Updated user profile as UserProfileDTO
     * @throws EntityNotFoundException if the current user is not found
     */
    @Transactional
    public UserProfileDTO updateProfile(UpdateProfileRequest updateRequest) {
        String username = getCurrentUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Current user not found: " + username));

        // Update allowed fields
        user.setFullName(updateRequest.getFullName());
        user.setPhone(updateRequest.getPhone());

        User savedUser = userRepository.save(user);
        log.info("User profile updated: {}", savedUser.getUsername());

        return mapToUserProfileDTO(savedUser);
    }

    // ---- Internal methods ----

    /**
     * Retrieve the current username from SecurityContext.
     *
     * @return Username of authenticated user
     * @throws IllegalStateException if no authentication is found
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No active authentication found");
        }
        return authentication.getName();
    }

    /**
     * Map User entity to UserProfileDTO.
     */
    private UserProfileDTO mapToUserProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus().name())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

