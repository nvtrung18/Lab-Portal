package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.auth.dto.UpdateProfileRequest;
import com.web.labportalbackend.auth.dto.UserProfileDTO;
import com.web.labportalbackend.auth.service.UserService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User profile management endpoints.
 * All endpoints require Bearer token authentication.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User profile management")
public class UserProfileController {

    private final UserService userService;

    /**
     * GET /api/users/me - Get current user's profile
     */
    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile of the currently authenticated user",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Response<UserProfileDTO>> getCurrentUser() {
        UserProfileDTO profile = userService.getCurrentUser();
        return ResponseEntity.ok(Response.ok("Current user profile retrieved", profile));
    }

    /**
     * PUT /api/users/me - Update current user's profile
     */
    @PutMapping("/me")
    @Operation(
            summary = "Update current user profile",
            description = "Update the profile of the currently authenticated user (fullName and phone only)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<Response<UserProfileDTO>> updateProfile(@Valid @RequestBody UpdateProfileRequest updateRequest) {
        UserProfileDTO updatedProfile = userService.updateProfile(updateRequest);
        return ResponseEntity.ok(Response.ok("User profile updated successfully", updatedProfile));
    }
}

