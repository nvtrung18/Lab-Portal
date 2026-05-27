package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Laboratory managed by current lab manager")
public class ManagedLabDTO {

    @Schema(description = "Laboratory ID", example = "1")
    private Long id;

    @Schema(description = "Laboratory name", example = "AI Research Lab")
    private String name;
}
