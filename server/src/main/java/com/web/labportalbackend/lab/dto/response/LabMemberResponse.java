package com.web.labportalbackend.lab.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Laboratory member information")
public class LabMemberResponse {

    @Schema(description = "Membership ID", example = "1")
    private Long id;

    @Schema(description = "User ID", example = "10")
    private Long userId;

    @Schema(description = "User full name", example = "Student 01")
    private String fullName;

    @Schema(description = "User email", example = "student01@labportal.com")
    private String email;

    @Schema(description = "Laboratory ID", example = "1")
    private Long labId;

    @Schema(description = "Laboratory name", example = "AI Research Lab")
    private String labName;

    @Schema(description = "Role inside laboratory", example = "MEMBER")
    private String role;

    @Schema(description = "Membership status", example = "ACTIVE")
    private String status;

    @Schema(description = "Joined timestamp")
    private Instant joinedAt;
}
