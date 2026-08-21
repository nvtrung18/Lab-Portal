package com.web.labportalbackend.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.web.labportalbackend.ai.enums.AiCapability;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiAssistantChatRequest {

    @NotBlank(message = "Chat input is required")
    @Size(max = 32768, message = "Chat input must not exceed 32768 characters")
    private String input;

    @NotNull(message = "Capability is required")
    private AiCapability capability;

    @Positive(message = "Resource ID must be positive")
    private Long resourceId;

    @Positive(message = "Parent resource ID must be positive")
    private Long parentResourceId;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonAnySetter
    public void captureUnknownProperty(String name, JsonNode value) {
        unknownFields.add(name);
    }

    @JsonIgnore
    @AssertTrue(message = "Unknown assistant chat fields are not allowed")
    @Schema(hidden = true)
    public boolean hasNoUnknownFields() {
        return unknownFields.isEmpty();
    }

    @JsonIgnore
    @AssertTrue(message = "Capability resource selection is invalid")
    @Schema(hidden = true)
    public boolean hasValidResourceSelection() {
        if (capability == null) {
            return true;
        }
        boolean globalResource = switch (capability.resourceType()) {
            case SYSTEM, AUDIT_LOG, SYSTEM_CONFIG -> true;
            default -> false;
        };
        if (globalResource != (resourceId == null)) {
            return false;
        }
        return capability.parentResourceType() == null
                ? parentResourceId == null
                : parentResourceId != null;
    }
}
