package com.web.labportalbackend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RegisterVerifyCodeResponse {
    private String email;
    private String verificationToken;
    private String message;
}
