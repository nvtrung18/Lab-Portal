package com.web.labportalbackend.lab.dto.request;

import com.web.labportalbackend.common.enums.LabStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateLabStatusRequest {
    @NotNull(message = "Status is required")
    private LabStatus status;
}
