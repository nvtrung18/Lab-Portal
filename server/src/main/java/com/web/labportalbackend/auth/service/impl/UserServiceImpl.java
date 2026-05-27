package com.web.labportalbackend.auth.service.impl;

import com.web.labportalbackend.auth.dto.UpdateProfileRequest;
import com.web.labportalbackend.auth.dto.ManagedLabDTO;
import com.web.labportalbackend.auth.dto.UserMembershipDTO;
import com.web.labportalbackend.auth.dto.UserProfileDTO;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.service.UserService;
import com.web.labportalbackend.lab.entity.Membership;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
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
    private final MembershipRepository membershipRepository;
    private final LaboratoryRepository laboratoryRepository;

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
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(user.getId()).orElse(null);

        return UserProfileDTO.builder()
                .id(user.getId()).email(user.getEmail()).username(user.getUsername())
                .fullName(user.getFullName()).phone(user.getPhone()).status(user.getStatus().name())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .memberships(membershipRepository.findByUserIdAndDeletedFalse(user.getId()).stream()
                        .map(this::mapToMembershipDTO)
                        .toList())
                .managedLabId(managedLab != null ? managedLab.getId() : null)
                .managedLab(managedLab != null
                        ? ManagedLabDTO.builder().id(managedLab.getId()).name(managedLab.getLabName()).build()
                        : null)
                .createdAt(user.getCreatedAt()).updatedAt(user.getUpdatedAt()).build();
    }

    private UserMembershipDTO mapToMembershipDTO(Membership membership) {
        return UserMembershipDTO.builder()
                .id(membership.getId())
                .labId(membership.getLaboratory().getId())
                .labName(membership.getLaboratory().getLabName())
                .role(membership.getRole())
                .status(Boolean.TRUE.equals(membership.getActive()) ? "ACTIVE" : "INACTIVE")
                .joinedAt(membership.getCreatedAt())
                .build();
    }
}
