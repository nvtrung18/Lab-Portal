package com.web.labportalbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendCodeRequest {
    @Email
    @NotBlank
    private String email;
}
