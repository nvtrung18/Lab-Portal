package com.web.labportalbackend.research.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
public class PatchResearchTaskRequest {

    private Long groupId;
    private Long milestoneId;
    private Long parentTaskId;

    @Size(max = 200, message = "Task title must not exceed 200 characters")
    private String title;

    @Size(max = 4000, message = "Task description must not exceed 4000 characters")
    private String description;

    private Long assigneeId;
    private TaskPriority priority;
    private TaskType type;
    private LocalDate dueDate;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean groupIdPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean milestoneIdPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean parentTaskIdPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean titlePresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean descriptionPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean assigneeIdPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean priorityPresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean typePresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean dueDatePresent;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unknownFields = new LinkedHashSet<>();

    @JsonSetter("groupId")
    public void setGroupId(Long groupId) {
        this.groupIdPresent = true;
        this.groupId = groupId;
    }

    @JsonSetter("milestoneId")
    public void setMilestoneId(Long milestoneId) {
        this.milestoneIdPresent = true;
        this.milestoneId = milestoneId;
    }

    @JsonSetter("parentTaskId")
    public void setParentTaskId(Long parentTaskId) {
        this.parentTaskIdPresent = true;
        this.parentTaskId = parentTaskId;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.titlePresent = true;
        this.title = title;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.descriptionPresent = true;
        this.description = description;
    }

    @JsonSetter("assigneeId")
    public void setAssigneeId(Long assigneeId) {
        this.assigneeIdPresent = true;
        this.assigneeId = assigneeId;
    }

    @JsonSetter("priority")
    public void setPriority(TaskPriority priority) {
        this.priorityPresent = true;
        this.priority = priority;
    }

    @JsonSetter("type")
    public void setType(TaskType type) {
        this.typePresent = true;
        this.type = type;
    }

    @JsonSetter("dueDate")
    public void setDueDate(LocalDate dueDate) {
        this.dueDatePresent = true;
        this.dueDate = dueDate;
    }

    @JsonAnySetter
    public void captureUnknownProperty(String name, JsonNode value) {
        unknownFields.add(name);
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean hasAnyRecognizedField() {
        return groupIdPresent || milestoneIdPresent || parentTaskIdPresent || titlePresent
                || descriptionPresent || assigneeIdPresent || priorityPresent || typePresent || dueDatePresent;
    }
}
