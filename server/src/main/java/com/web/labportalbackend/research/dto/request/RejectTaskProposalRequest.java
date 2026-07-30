package com.web.labportalbackend.research.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class RejectTaskProposalRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 4000, message = "Rejection reason must not exceed 4000 characters")
    private String reason;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonAnySetter
    public void captureUnknownProperty(String name, JsonNode value) {
        unknownFields.add(name);
    }

    @JsonIgnore
    @AssertTrue(message = "Unknown rejection fields are not allowed")
    @Schema(hidden = true)
    public boolean hasNoUnknownFields() {
        return unknownFields.isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "Rejection reason contains invalid control characters")
    @Schema(hidden = true)
    public boolean hasNoDisallowedControlCharacters() {
        if (reason == null) {
            return true;
        }
        return reason.codePoints().noneMatch(codePoint ->
                Character.isISOControl(codePoint)
                        && codePoint != '\t'
                        && codePoint != '\n'
                        && codePoint != '\r');
    }
}
