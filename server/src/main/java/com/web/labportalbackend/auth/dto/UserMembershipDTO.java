package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Current user's laboratory membership")
public class UserMembershipDTO {

    @Schema(description = "Membership ID", example = "1")
    private Long id;

    @Schema(description = "Laboratory ID", example = "2")
    private Long labId;

    @Schema(description = "Laboratory name", example = "AI Research Lab")
    private String labName;

    @Schema(description = "Membership role", example = "MEMBER")
    private String role;

    @Schema(description = "Membership status", example = "ACTIVE")
    private String status;

    @Schema(description = "Membership creation timestamp")
    private Instant joinedAt;
}
