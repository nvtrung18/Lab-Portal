package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.auth.dto.UpdateUserRolesRequest;
import com.web.labportalbackend.auth.dto.UserResponse;
import com.web.labportalbackend.auth.service.AuthService;
import com.web.labportalbackend.common.dto.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<Response<List<UserResponse>>> getAllUsers() {
        List<UserResponse> manageableUsers = authService.getAllUsers().stream()
                .filter(user -> !user.getRoles().contains("ADMIN"))
                .toList();
        return ResponseEntity.ok(Response.ok("Users retrieved", manageableUsers));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<Response<UserResponse>> updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest request) {
        return ResponseEntity.ok(Response.ok("User roles updated", authService.updateUserRoles(id, request.getRoles())));
    }

    @PutMapping("/{id}/ban")
    public ResponseEntity<Response<UserResponse>> banUser(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("User banned", authService.banUser(id)));
    }

    @PutMapping("/{id}/unban")
    public ResponseEntity<Response<UserResponse>> unbanUser(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("User unbanned", authService.unbanUser(id)));
    }
}
