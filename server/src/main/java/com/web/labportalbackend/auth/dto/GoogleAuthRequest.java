package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Google Identity Services credential request")
public class GoogleAuthRequest {

    @NotBlank(message = "Google credential is required")
    @Size(max = 8192, message = "Google credential is too large")
    @Schema(description = "Google Identity Services ID token returned in the credential field")
    private String credential;
}
