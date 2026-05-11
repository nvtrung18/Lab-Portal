package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.auth.dto.UpdateProfileRequest;
import com.web.labportalbackend.auth.dto.UserProfileDTO;

/**
 * Service interface for user profile management.
 */
public interface UserService {
    UserProfileDTO getCurrentUser();
    UserProfileDTO updateProfile(UpdateProfileRequest updateRequest);
}
