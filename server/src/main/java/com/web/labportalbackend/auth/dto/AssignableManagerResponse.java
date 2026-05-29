package com.web.labportalbackend.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssignableManagerResponse {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private Long managedLabId;
}
