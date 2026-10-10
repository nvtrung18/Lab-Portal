package com.web.labportalbackend.auth.security;

import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortalAccessService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public boolean canEnter(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;

        boolean staff = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_LAB_MANAGER".equals(authority.getAuthority()));
        if (staff) return true;

        boolean student = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority()));
        if (!student) return false;

        return userRepository.findByUsername(authentication.getName())
                .map(user -> membershipRepository.existsByUserIdAndActiveTrueAndDeletedFalse(user.getId()))
                .orElse(false);
    }
}
