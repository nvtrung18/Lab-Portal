package com.web.labportalbackend.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
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
}
