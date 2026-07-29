package com.web.labportalbackend.research.dto.request;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.web.labportalbackend.research.enums.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * Canonical task status update payload. Unknown properties are captured locally so
 * the legacy status endpoint and global Jackson configuration remain unchanged.
 */
@Getter
public class PatchTaskStatusRequest {

    @NotNull(message = "Task status is required")
    private TaskStatus status;

    @Size(max = 4000, message = "Blocked reason must not exceed 4000 characters")
    private String blockedReason;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonSetter("status")
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @JsonSetter("blockedReason")
    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    @JsonAnySetter
    public void captureUnknownProperty(String name, JsonNode value) {
        unknownFields.add(name);
    }

    @JsonIgnore
    @AssertTrue(message = "Unknown task status fields are not allowed")
    @Schema(hidden = true)
    public boolean hasNoUnknownFields() {
        return unknownFields.isEmpty();
    }

    /**
     * Transport-level content rule. Semantic blank/all-invisible handling belongs
     * to TaskWorkflowService and is deliberately not duplicated here.
     */
    @JsonIgnore
    @AssertTrue(message = "Blocked reason contains disallowed control characters")
    @Schema(hidden = true)
    public boolean hasAllowedBlockedReasonCharacters() {
        if (blockedReason == null) {
            return true;
        }
        return blockedReason.codePoints().allMatch(cp ->
                Character.getType(cp) != Character.CONTROL
                        || cp == '\t' || cp == '\n' || cp == '\r');
    }
}
