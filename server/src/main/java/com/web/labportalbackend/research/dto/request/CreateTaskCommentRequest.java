package com.web.labportalbackend.research.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class CreateTaskCommentRequest {

    @NotBlank(message = "Task comment content is required")
    private String content;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonAnySetter
    public void captureUnknownProperty(String name, JsonNode value) {
        unknownFields.add(name);
    }
}
