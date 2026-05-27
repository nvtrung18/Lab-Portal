package com.web.labportalbackend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PasswordResetVerifyResponse {
    private String resetToken;
    private String message;
}
