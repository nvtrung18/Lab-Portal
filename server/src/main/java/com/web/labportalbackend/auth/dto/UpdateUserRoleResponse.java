package com.web.labportalbackend.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateUserRoleResponse {
    private String message;
    private UserRoleInfo user;

    @Getter
    @Builder
    public static class UserRoleInfo {
        private Long id;
        private String fullName;
        private String email;
        private String role;
        private Long managedLabId;
        private String managedLabName;
    }
}
