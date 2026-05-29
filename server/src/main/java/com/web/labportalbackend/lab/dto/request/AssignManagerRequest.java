package com.web.labportalbackend.lab.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignManagerRequest {
    @NotNull(message = "Manager user ID is required")
    private Long managerUserId;
}
