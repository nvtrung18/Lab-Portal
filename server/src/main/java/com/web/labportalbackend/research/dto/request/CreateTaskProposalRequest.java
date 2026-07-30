package com.web.labportalbackend.research.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class CreateTaskProposalRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Group ID is required")
    private Long groupId;

    private Long milestoneId;
    private Long parentTaskId;

    @NotBlank(message = "Task proposal title is required")
    @Size(max = 200, message = "Task proposal title must not exceed 200 characters")
    private String title;

    @Size(max = 4000, message = "Task proposal description must not exceed 4000 characters")
    private String description;

    private TaskPriority priority;
    private TaskType type;
    private LocalDate dueDate;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonAnySetter
    public void captureUnknownProperty(String name, JsonNode value) {
        unknownFields.add(name);
    }

    @JsonIgnore
    @AssertTrue(message = "Unknown task proposal fields are not allowed")
    @Schema(hidden = true)
    public boolean hasNoUnknownFields() {
        return unknownFields.isEmpty();
    }
}
