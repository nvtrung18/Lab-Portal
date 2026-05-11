package com.web.labportalbackend.auth.service.impl;

import com.web.labportalbackend.auth.dto.UpdateProfileRequest;
import com.web.labportalbackend.auth.dto.UserProfileDTO;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserProfileDTO getCurrentUser() {
        User user = userRepository.findByUsername(getCurrentUsername())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
        return mapToUserProfileDTO(user);
    }

    @Override @Transactional
    public UserProfileDTO updateProfile(UpdateProfileRequest updateRequest) {
        User user = userRepository.findByUsername(getCurrentUsername())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
        user.setFullName(updateRequest.getFullName());
        user.setPhone(updateRequest.getPhone());
        User saved = userRepository.save(user);
        log.info("User profile updated: {}", saved.getUsername());
        return mapToUserProfileDTO(saved);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new IllegalStateException("No active authentication found");
        return auth.getName();
    }

    private UserProfileDTO mapToUserProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId()).email(user.getEmail()).username(user.getUsername())
                .fullName(user.getFullName()).phone(user.getPhone()).status(user.getStatus().name())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt()).updatedAt(user.getUpdatedAt()).build();
    }
}
