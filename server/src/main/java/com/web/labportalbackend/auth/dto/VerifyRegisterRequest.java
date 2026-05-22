package com.web.labportalbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyRegisterRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String code;
}
